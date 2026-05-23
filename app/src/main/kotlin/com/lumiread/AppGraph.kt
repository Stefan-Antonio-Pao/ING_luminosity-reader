package com.lumiread

import android.app.Application
import com.lumiread.core.llm.FakeLlmEngine
import com.lumiread.core.llm.LlmEngine
import com.lumiread.core.pipeline.Pipeline
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

/**
 * 手写依赖注入容器。
 *
 * 默认绑定:
 *  - OCR / 图像打标 → ML Kit
 *  - LLM → `Gemma4Engine`(LiteRT-LM 0.12.0)
 *  - TTS → `SherpaTtsEngine`(sherpa-onnx 1.13.2 + vits-melo-tts-zh_en)
 *
 * `Gemma4Engine` / `SherpaTtsEngine` 都需要 `Context`,故 `AppGraph` 由"无参 object"改为
 * "`init(application)` 后可用"形态;`MainActivity.onCreate` 第一行必须调 [init]。
 *
 * `_llm` / `_tts` 用 `lateinit` + 私有 setter,外部只读;[isFakeMode] 把"还没 init"也算 Fake,
 * 这样万一忘了调 [init],UI 上的红色水印能直接暴露问题。
 */
object AppGraph {
    val ocr: OcrService           = MlKitOcrService()
    val labels: ImageLabelService = MlKitImageLabelService()

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

    val pipeline: Pipeline by lazy { Pipeline(ocr, labels, llm, tts) }

    /** `MainActivity.onCreate` 第一行调用。重复调用是 no-op。 */
    fun init(application: Application) {
        val ctx = application.applicationContext
        if (!::_llm.isInitialized)      _llm      = Gemma4Engine(ctx)
        if (!::_tts.isInitialized)      _tts      = SherpaTtsEngine(ctx)
        if (!::_settings.isInitialized) _settings = SettingsRepository(ctx)
        if (!::_chatStore.isInitialized) _chatStore = ChatStore(ctx)
        if (!::_studyStore.isInitialized) _studyStore = StudyStore(ctx)
    }

    /**
     * 当前是否还有任一 Fake → 控制红色 "FAKE MODE" 横幅。
     *
     * 默认 LLM/TTS 都为真实实现 → false → 水印不显示。
     * 未 init 也算 Fake,避免"忘了调 init 但 UI 看起来正常"的静默故障。
     */
    val isFakeMode: Boolean
        get() = !::_llm.isInitialized
            || !::_tts.isInitialized
            || _llm is FakeLlmEngine
            || _tts is FakeTtsEngine
}
