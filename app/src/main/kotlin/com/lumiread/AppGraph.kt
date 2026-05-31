package com.lumiread

import android.app.Application
import android.util.Log
import com.lumiread.core.agent.AgentOrchestrator
import com.lumiread.core.agent.SocraticEngine
import com.lumiread.core.agent.TwoStagePipelineEngine
import com.lumiread.core.data.EmptyOfflineDictionary
import com.lumiread.core.llm.FakeLlmEngine
import com.lumiread.core.llm.LlmEngine
import com.lumiread.core.pipeline.Pipeline
import com.lumiread.llm.FunctionCallingEngine
import com.lumiread.core.tts.FakeTtsEngine
import com.lumiread.core.tts.TtsEngine
import com.lumiread.core.vision.ImageLabelService
import com.lumiread.core.vision.OcrService
import com.lumiread.data.ChatStore
import com.lumiread.data.SettingsRepository
import com.lumiread.data.StudyStore
import com.lumiread.llm.Gemma4Engine
import com.lumiread.tts.SherpaTtsEngine
import com.lumiread.vision.MlKitImageLabelService
import com.lumiread.vision.MlKitOcrService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 手写依赖注入容器。
 *
 * 进度:
 * - Phase 1:全 Fake。
 * - Phase 2:OCR + Label 切到 ML Kit;LLM/TTS 仍 Fake。
 * - Phase 3:LLM 切到 `Gemma4Engine`(LiteRT-LM 0.12.0);TTS 仍 Fake。
 * - **Phase 4(当前):TTS 切到 `SherpaTtsEngine`(sherpa-onnx 1.13.2 + vits-melo-tts-zh_en),
 *   两者都为真 → `isFakeMode = false` → 红色水印消失(CLAUDE.md §C5 兑现)。**
 *
 * `Gemma4Engine` / `SherpaTtsEngine` 都需要 `Context`,故 `AppGraph` 由"无参 object"改为
 * "`init(application)` 后可用"形态;`MainActivity.onCreate` 第一行必须调 [init]。
 *
 * `_llm` / `_tts` 用 `lateinit` + 私有 setter,外部只读;[isFakeMode] 把"还没 init"也算 Fake,
 * 这样万一忘了调 [init],UI 上的红色水印能直接暴露问题(CLAUDE.md §C5)。
 */
object AppGraph {
    private const val TAG = "AppGraph"

    val ocr: OcrService           = MlKitOcrService()
    val labels: ImageLabelService = MlKitImageLabelService()

    /**
     * Application context(v1.1 步骤二):
     * 给非 Composable 路径取 string 资源用(如 ChatState.startNewSession 在 Flow 回调里报错)。
     * `init(application)` 一次后稳定可读。
     */
    private lateinit var _appContext: android.content.Context
    val appContext: android.content.Context get() = _appContext

    private lateinit var _llm: LlmEngine
    val llm: LlmEngine get() = _llm

    private lateinit var _tts: TtsEngine
    val tts: TtsEngine get() = _tts

    private lateinit var _settings: SettingsRepository
    val settings: SettingsRepository get() = _settings

    private lateinit var _chatStore: ChatStore
    val chatStore: ChatStore get() = _chatStore

    private lateinit var _studyStore: StudyStore
    val studyStore: StudyStore get() = _studyStore

    val pipeline: Pipeline by lazy {
        Pipeline(
            ocr, labels, llm, tts, buildSocraticEngine(),
            onMetrics = { m ->
                // v2.0.0 Stage 3:每轮 served-by / 工具 / 延迟落日志(演示 agentic 闭环 + 喂 README)。
                Log.i(
                    "TurnMetrics",
                    "served-by=${m.servedBy} tools=${m.usedTools} firstChunk=${m.firstChunkMs}ms totalGen=${m.totalGenMs}ms",
                )
            },
        )
    }

    /**
     * v2.0.0 Step 5:构建每轮生成引擎。
     *  - 真实 LLM([Gemma4Engine]):`AgentOrchestrator`(primary = [FunctionCallingEngine] 原生函数调用,
     *    fallback = [TwoStagePipelineEngine]),策略 = **E4B 工具常开 / E2B 复杂度门控 / 多模态轮跳过 FC**
     *    (currentModel 动态读 `gemma.activeModel()`,用户切模型即时生效)。
     *  - Fake 模式 / 非 Gemma4Engine:直接 TwoStage(无工具),不引入 FC。
     *
     * 词典暂用 [EmptyOfflineDictionary](Step 7 换真实 WordNet/CC-CEDICT);`read_aloud` 暂只记日志
     * (最终回答由 ChatSession autoPlay 朗读,避免重复朗读)。
     */
    private fun buildSocraticEngine(): SocraticEngine {
        val gemma = _llm as? Gemma4Engine ?: return TwoStagePipelineEngine(_llm)
        val fc = FunctionCallingEngine(
            provider = gemma,
            dict = EmptyOfflineDictionary,
            onReadAloud = { text -> Log.i(TAG, "read_aloud 请求(仅记录,不重复朗读):$text") },
        )
        val twoStage = TwoStagePipelineEngine(_llm)
        return AgentOrchestrator(
            primary = fc,
            fallback = twoStage,
            currentModel = { gemma.activeModel() },
            onFallback = { t -> Log.w(TAG, "FC 降级到 TwoStage(原因见异常,null=门控跳过)", t) },
        )
    }

    /**
     * 应用级协程作用域(v1.1 2026-05-25)。
     *
     * 仅用于跨组件的"长期监听"协程,例如订阅 [SettingsRepository.selectedModelFlow] 把
     * 模型切换事件转发给 [Gemma4Engine.setActiveModel]。UI 自己的协程仍走各自的 viewModelScope。
     *
     * SupervisorJob:任一子任务失败不会带垮整个 scope。
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var modelWatcherJob: Job? = null

    /** `MainActivity.onCreate` 第一行调用。重复调用是 no-op。 */
    fun init(application: Application) {
        val ctx = application.applicationContext
        if (!::_appContext.isInitialized) _appContext = ctx
        if (!::_llm.isInitialized)      _llm      = Gemma4Engine(ctx)
        if (!::_tts.isInitialized)      _tts      = SherpaTtsEngine(ctx)
        if (!::_settings.isInitialized) _settings = SettingsRepository(ctx)
        if (!::_chatStore.isInitialized) _chatStore = ChatStore(ctx)
        if (!::_studyStore.isInitialized) _studyStore = StudyStore(ctx)

        startModelWatcher()
    }

    /**
     * 启动"选中模型"监听协程(v1.1 2026-05-25):
     *  - 第一次发射:把 DataStore 里的持久值同步给 [Gemma4Engine.setActiveModel],
     *    确保引擎 warmUp 用的是用户上次选的模型(而不是 Engine 默认的 E2B)。
     *  - 之后每次发射(用户在设置页切模型):同样调 setActiveModel,
     *    它内部会 close 旧 engine + 重置状态,下次 warmUp 用新模型初始化。
     *
     * **调用约束**:[Gemma4Engine.setActiveModel] 文档要求"上层先确保没进行中的对话"——
     * 这一职责由 UI(切换前先 `chat.endSession()`)承担,这里不再保护。
     *
     * 多次调用 [init] 时已 launch 过的 watcher 不再重启(modelWatcherJob != null)。
     */
    private fun startModelWatcher() {
        if (modelWatcherJob != null) return
        val engine = _llm as? Gemma4Engine ?: run {
            Log.w(TAG, "LLM 不是 Gemma4Engine,跳过 selectedModel 监听")
            return
        }
        modelWatcherJob = appScope.launch {
            // collect 第一次发射 = DataStore 持久值 → 同步给 Engine.currentModel(防止
            // 用户上次选了 E4B 但 Engine 默认初始化为 E2B);之后每次用户改设置都会发射。
            // distinctUntilChanged 避免 DataStore 偶发重复发射触发不必要的 close+重 init。
            try {
                _settings.selectedModelFlow.distinctUntilChanged().collect { model ->
                    Log.i(TAG, "selectedModelFlow 发射:$model,调 engine.setActiveModel")
                    engine.setActiveModel(model)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "模型监听协程异常退出", t)
            }
        }
    }

    /**
     * 当前是否还有任一 Fake → 控制红色 "FAKE MODE" 横幅(CLAUDE.md §C5)。
     *
     * Phase 4 完成后:LLM 真 + TTS 真 → false → 水印消失。
     * 未 init 也算 Fake,避免"忘了调 init 但 UI 看起来正常"的静默故障。
     */
    val isFakeMode: Boolean
        get() = !::_llm.isInitialized
            || !::_tts.isInitialized
            || _llm is FakeLlmEngine
            || _tts is FakeTtsEngine
}
