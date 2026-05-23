package com.lumiread.core.tts

import com.lumiread.core.AgeBand
import com.lumiread.core.Lang

/**
 * 文本转语音引擎抽象。
 *
 * 真实实现 [com.lumiread.core.tts.SherpaTtsEngine] 居于 :app,基于 sherpa-onnx。
 * 系统 TextToSpeech 作兜底。本接口保持 Android-free。
 */
interface TtsEngine {
    /**
     * 预热(加载模型、初始化资源)。默认 no-op;真实实现按需重载。
     * 首次构造 sherpa-onnx OfflineTts 读 ~170 MB ONNX,耗时 1–3 s,必须放 IO 线程提前预热。
     */
    suspend fun warmUp() {}

    /**
     * 念出一段文字。
     * @param lang 输出语言(独立于系统语言)。
     * @param ageBand 影响语速、音高、句间停顿。
     */
    suspend fun speak(text: String, lang: Lang, ageBand: AgeBand)

    /** 打断当前正在播放的语音。 */
    suspend fun stop()
}
