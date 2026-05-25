package com.lumiread.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.lumiread.core.GemmaModel
import com.lumiread.core.ImageInput
import com.lumiread.core.llm.Conversation
import com.lumiread.core.llm.LlmEngine
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.google.ai.edge.litertlm.Conversation as LiteRtConversation

/**
 * LiteRT-LM 0.12.0 上的 Gemma 4 推理引擎。
 *
 * 签名来源:LiteRT-LM 0.12.0 AAR(javap -p classes.jar 反汇编核对),2026-05-25。
 *
 * 关键事实(直接决定本类的形状):
 * - `Engine.initialize` 同步 void,可达 ~10 s(OpenCL kernel 编译)→ 必须 `Dispatchers.IO`
 * - `sendMessageAsync(String): Flow<Message>`,本类负责抽取 `Content.Text` 串成 `Flow<String>`
 * - 默认 GPU 后端,初始化失败回退 CPU
 * - `Engine` 是 AutoCloseable,App 进程内全局单例;`Conversation` 一次伴读一个,完后 close
 *
 * **v1.1(2026-05-25)双模型重构**:
 * - 持 [currentModel] 状态(从 SettingsRepository 注入)
 * - `EngineConfig.visionBackend = if (currentModel.supportsMultimodal) backend else null`
 * → 修复 E2B + 多模态时 native null deref 崩溃
 * - [setActiveModel] 切换模型:close 旧 engine + 重置状态,下次 [warmUp] 用新模型初始化
 *
 * 故意不实现的:
 * - cancellation( 接 `cancelProcess`)
 * - SamplerConfig 调温( 调优时再开)
 */
class Gemma4Engine(private val context: Context) : LlmEngine {

    private val initMutex = Mutex()

    /**
     * 串行化 createConversation —— 防多个 Conversation 在 native 层同时创建/销毁。
     * 与 [initMutex] 分开,避免 warmUp 慢 init 期间阻塞别的 startConversation。
     */
    private val convMutex = Mutex()

    /**
     * 上一条 Conversation close 的时间戳(epoch ms);0 = 从未 close 过。
     * 用于在 [startConversation] 内施加最小空隙,见 [MIN_CONV_QUIESCENCE_MS]。
     */
    private val lastConvCloseAtMs = AtomicLong(0L)

    @Volatile private var engine: Engine? = null
    @Volatile private var activeBackend: String = "uninit"

    /**
     * 当前已加载/将加载的模型。AppGraph 启动时根据 SettingsRepository 设置初值;
     * 用户切换模型时调 [setActiveModel] 改这里 + close 旧引擎。
     *
     * 默认 [GemmaModel.E2B](与设置默认值一致,避免不一致)。
     */
    @Volatile private var currentModel: GemmaModel = GemmaModel.E2B

    /** 集成冒烟测试 Log 报告用。 */
    fun activeBackendName(): String = activeBackend

    fun activeModel(): GemmaModel = currentModel

    /**
     * 切换当前模型(v1.1 2026-05-25)。
     *
     * 副作用:close 当前 engine + 清空缓存,下次 [warmUp] 用新模型 + 新 EngineConfig 初始化。
     * 若传入与 [currentModel] 相同的值,no-op。
     *
     * **调用约束**:上层(AppGraph 监听协程 / UI 切模型按钮)应**先确保没有进行中的对话**,
     * 否则会破坏未完成的 Conversation。本函数本身在 [initMutex] 内串行化,不会与 [warmUp] 并发。
     */
    suspend fun setActiveModel(model: GemmaModel) = withContext(Dispatchers.IO) {
        if (model == currentModel && engine?.isInitialized() == true) return@withContext
        initMutex.withLock {
            if (model == currentModel && engine?.isInitialized() == true) return@withLock
            Log.i(TAG, "切换模型:$currentModel → $model")
            engine?.close()
            engine = null
            activeBackend = "uninit"
            currentModel = model
        }
    }

    override suspend fun warmUp() = withContext(Dispatchers.IO) {
        if (engine?.isInitialized() == true) return@withContext
        initMutex.withLock {
            // double-checked locking:进 lock 后再看一次,避免并发 warmUp 重复 init
            if (engine?.isInitialized() == true) return@withLock

            val model = currentModel
            val modelPath = ModelProvider.locate(context, model).getOrThrow()
            Log.i(TAG, "Gemma4Engine 准备初始化,model=$model,modelPath=$modelPath")
            val t0 = System.currentTimeMillis()

            engine = tryInitialize(modelPath, Backend.GPU(), "GPU", model)
                ?: tryInitialize(modelPath, Backend.CPU(numOfThreads = 4), "CPU", model)
                ?: error("Gemma4Engine 初始化失败:GPU 与 CPU 后端都不可用(model=$model)")

            Log.i(TAG, "Gemma4Engine 初始化完成,model=$model,后端=$activeBackend,耗时=${System.currentTimeMillis() - t0}ms")
        }
    }

    /**
     * 用单一后端尝试 init。多模态模型 ([GemmaModel.supportsMultimodal] = true)同步配置
     * `visionBackend = backend`( 官方多模态示例);否则 null(纯文本模型,E2B)。
     *
     * `audioBackend` 暂时全部置 null —— 本期不接入音频路径。
     */
    private fun tryInitialize(
        modelPath: String,
        backend: Backend,
        name: String,
        model: GemmaModel,
    ): Engine? = try {
        val cfg = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = if (model.supportsMultimodal) backend else null,
        )
        val e = Engine(cfg)
        e.initialize()
        if (!e.isInitialized()) {
            Log.w(TAG, "Engine.isInitialized() 在 $name 后端 init 后仍为 false(model=$model),关闭重试")
            e.close()
            null
        } else {
            activeBackend = name
            e
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Gemma4Engine 用 $name 后端初始化失败(model=$model):${t.message}", t)
        null
    }

    override fun generate(prompt: String): Flow<String> = flow {
        warmUp()
        val e = engine ?: error("Gemma4Engine 未初始化")
        // 每次 generate 用一个新 Conversation,避免历史累计撑爆 ctx-window(:实测 2048)。
        // 系统提示已经由 SocraticPromptBuilder 拼进 prompt 文本,这里不传 ConversationConfig.systemInstruction。
        e.createConversation().use { conv ->
            conv.sendMessageAsync(prompt).collect { msg ->
                val text = msg.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                if (text.isNotEmpty()) emit(text)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun startConversation(systemPrompt: String): Conversation =
        withContext(Dispatchers.IO) {
            warmUp()
            val e = engine ?: error("Gemma4Engine 未初始化")
            // 2026-05-24 多轮崩溃修复:
            // 1) 用 convMutex 串行化 createConversation —— 防 native 层 create/destroy 重叠
            // 2) 与上一条 Conversation.close 之间至少留 MIN_CONV_QUIESCENCE_MS 空隙,
            // 给 liblitertlm_jni.so 时间收完前一条 session 句柄。
            convMutex.withLock {
                val lastClose = lastConvCloseAtMs.get()
                if (lastClose != 0L) {
                    val elapsed = System.currentTimeMillis() - lastClose
                    val wait = MIN_CONV_QUIESCENCE_MS - elapsed
                    if (wait > 0) delay(wait)
                }
                val cfg = ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                Gemma4Conversation(e.createConversation(cfg), lastConvCloseAtMs)
            }
        }

    override suspend fun close() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            engine?.close()
            engine = null
            activeBackend = "closed"
        }
    }

    companion object {
        private const val TAG = "Gemma4Engine"

        /**
         * 上一条 Conversation.close 与下一条 createConversation 之间的最小空隙。
         * 200 ms 是个工程经验值:足够让 native 句柄析构/资源回收完成,又不会让用户感知到额外延迟。
         */
        private const val MIN_CONV_QUIESCENCE_MS = 200L
    }
}

/**
 * LiteRT-LM `Conversation` 的薄包装。
 *
 * 单会话内 sendUserMessage 必须串行(LiteRT-LM 不支持单会话并发);
 * 上层 ChatSession 自然串行(用户得等上一轮回复完才能发下一轮)。
 *
 * 不要把 LiteRT-LM 的 `Conversation` 直接暴露给 :core —— :core 必须保持 Android-free。
 */
private class Gemma4Conversation(
    private val conv: LiteRtConversation,
    private val lastConvCloseAtMs: AtomicLong,
) : Conversation {

    override fun sendUserMessage(text: String): Flow<String> = flow {
        conv.sendMessageAsync(text).collect { msg ->
            val piece = msg.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            if (piece.isNotEmpty()) emit(piece)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 多模态一轮(OcrMode.MULTIMODAL, 2026-05-24 / v1.1 2026-05-25)。
     *
     * 签名核对:javap -p classes.jar(0.12.0 AAR),2026-05-24:
     * - `Content.ImageFile(String absolutePath)` / `Content.ImageBytes(byte[] bytes)`
     * - `Contents.Companion.of(Content...)` varargs
     * - `Conversation.sendMessageAsync(Contents, Map): Flow<Message>`
     *
     * **v1.1 修复**:崩溃根因不在这里,在引擎初始化时 `EngineConfig.visionBackend` 未配置。
     * 现在只有 [GemmaModel.supportsMultimodal] = true 的模型(E4B)走得到这里时,引擎的
     * visionBackend 已被设置为非 null,native 视觉路径可达 → 不再 SIGSEGV。
     */
    override fun sendUserMessage(text: String, images: List<ImageInput>): Flow<String> = flow {
        val parts = buildList<Content> {
            add(Content.Text(text))
            for (img in images) {
                when (img) {
                    is ImageInput.Path -> {
                        val f = File(img.absolutePath)
                        if (f.isFile && f.length() > 0) {
                            add(Content.ImageFile(img.absolutePath))
                        } else {
                            // 路径已被清理 → 跳过这张(C3 文件路径竞态的 Gemma 侧兜底)
                        }
                    }
                    is ImageInput.Bytes -> add(Content.ImageBytes(img.data))
                }
            }
        }
        val contents = Contents.of(*parts.toTypedArray())
        conv.sendMessageAsync(contents).collect { msg ->
            val piece = msg.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            if (piece.isNotEmpty()) emit(piece)
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        try {
            conv.close()
        } finally {
            // 给 Gemma4Engine.startConversation 算 quiescence 用 —— 即使 conv.close 抛错,
            // 也要记下尝试 close 的时刻,否则下一轮 createConversation 会立刻撞上去。
            lastConvCloseAtMs.set(System.currentTimeMillis())
        }
    }
}
