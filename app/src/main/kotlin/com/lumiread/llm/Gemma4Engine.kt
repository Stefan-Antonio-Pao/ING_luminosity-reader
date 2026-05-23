package com.lumiread.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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
 * LiteRT-LM 0.12.0 上的 Gemma 4 E2B 推理引擎。
 *
 * 关键事实(直接决定本类的形状):
 *  - `Engine.initialize()` 同步 void,可达 ~10 s(OpenCL kernel 编译)→ 必须 `Dispatchers.IO`
 *  - `sendMessageAsync(String): Flow<Message>`(注意是 `Flow<Message>` 不是 `Flow<String>`),
 *    本类负责抽取 `msg.contents.contents.filterIsInstance<Content.Text>().joinToString { it.text }`
 *    再以 `Flow<String>` 形式吐给上层
 *  - 默认 GPU 后端(实测 ~52 tok/s),初始化失败回退 CPU(~4–5 tok/s)
 *  - `Engine` 是 AutoCloseable,App 进程内全局单例;`Conversation` 一次伴读一个,完后 close
 *  - 不在引擎层做错误重试 —— 让 `core.pipeline.Pipeline.runCatching` 收尾
 *
 * 故意不实现的:
 *  - cancellation(`cancelProcess()` 留待后续)
 *  - SamplerConfig 调温
 *
 * 多模态 `sendUserMessage(text, images)` 重载用于 `OcrMode.MULTIMODAL`;默认仍走独立 OCR。
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

    /** 当前激活的推理后端名(GPU / CPU),供诊断使用。 */
    fun activeBackendName(): String = activeBackend

    override suspend fun warmUp() = withContext(Dispatchers.IO) {
        if (engine?.isInitialized() == true) return@withContext
        initMutex.withLock {
            // double-checked locking:进 lock 后再看一次,避免并发 warmUp 重复 init
            if (engine?.isInitialized() == true) return@withLock

            val modelPath = ModelProvider.locate(context).getOrThrow()
            Log.i(TAG, "Gemma4Engine 准备初始化,modelPath=$modelPath")
            val t0 = System.currentTimeMillis()

            engine = tryInitialize(modelPath, Backend.GPU(), "GPU")
                ?: tryInitialize(modelPath, Backend.CPU(numOfThreads = 4), "CPU")
                ?: error("Gemma4Engine 初始化失败:GPU 与 CPU 后端都不可用")

            Log.i(TAG, "Gemma4Engine 初始化完成,后端=$activeBackend,耗时=${System.currentTimeMillis() - t0}ms")
        }
    }

    private fun tryInitialize(modelPath: String, backend: Backend, name: String): Engine? = try {
        val cfg = EngineConfig(modelPath = modelPath, backend = backend)
        val e = Engine(cfg)
        e.initialize()
        if (!e.isInitialized()) {
            Log.w(TAG, "Engine.isInitialized() 在 $name 后端 init 后仍为 false,关闭重试")
            e.close()
            null
        } else {
            activeBackend = name
            e
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Gemma4Engine 用 $name 后端初始化失败:${t.message}", t)
        null
    }

    override fun generate(prompt: String): Flow<String> = flow {
        warmUp()
        val e = engine ?: error("Gemma4Engine 未初始化")
        // 每次 generate 用一个新 Conversation,避免历史累计撑爆 ctx-window(2048 token)。
        // 系统提示已经由 SocraticPromptBuilder 拼进 prompt 文本,这里不传 ConversationConfig.systemInstruction。
        e.createConversation().use { conv ->
            conv.sendMessageAsync(prompt).collect { msg ->
                // sendMessageAsync 返回 Flow<Message>,文本在 msg.contents.contents 的 Content.Text 子类里
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
            // 多轮串行化:
            //  1) 用 convMutex 串行化 createConversation —— 防 native 层 create/destroy 重叠
            //  2) 与上一条 Conversation.close() 之间至少留 MIN_CONV_QUIESCENCE_MS 空隙,
            //     给 liblitertlm_jni.so 时间收完前一条 session 句柄。
            convMutex.withLock {
                val lastClose = lastConvCloseAtMs.get()
                if (lastClose != 0L) {
                    val elapsed = System.currentTimeMillis() - lastClose
                    val wait = MIN_CONV_QUIESCENCE_MS - elapsed
                    if (wait > 0) delay(wait)
                }
                val cfg = ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                // createConversation(ConversationConfig?):
                // systemInstruction 只在会话开始注入一次,后续 sendMessageAsync 仅追加用户内容。
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
         * 上一条 Conversation.close() 与下一条 createConversation() 之间的最小空隙。
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
     * 多模态一轮(OcrMode.MULTIMODAL)。
     *
     * LiteRT-LM 0.12.0 多模态 API:
     *  - `Content.ImageFile(String absolutePath)` / `Content.ImageBytes(byte[] bytes)`
     *  - `Contents.Companion.of(Content...)` varargs
     *  - `Conversation.sendMessageAsync(Contents, Map): Flow<Message>`(Map 有 $default,可省略)
     *
     * 实现选择:
     *  - [ImageInput.Path] → 优先 `Content.ImageFile`(让 native 自己 mmap);路径无效就回退读字节
     *  - [ImageInput.Bytes] → `Content.ImageBytes`(已在内存)
     *  - 文本永远放在 Content 列表首位(LiteRT-LM 文档示例约定:先 text 后 image)
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
                            // 路径已被清理 → 跳过这张(文件路径竞态的 Gemma 侧兜底)
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
            // 给 Gemma4Engine.startConversation 算 quiescence 用 —— 即使 conv.close() 抛错,
            // 也要记下尝试 close 的时刻,否则下一轮 createConversation 会立刻撞上去。
            lastConvCloseAtMs.set(System.currentTimeMillis())
        }
    }
}
