package com.lumiread.core

/** 输出语言。绑在设置项里,由用户独立切换(与系统语言解耦)。 */
enum class Lang { ZH, EN }

/** 三个年龄段。影响 prompt 写法、TTS 语速、UI 用词。 */
enum class AgeBand { TODDLER, PRESCHOOL, PREADOLESCENT }

/**
 * 多模态 / 独立 OCR 二选一。
 *
 *  - [OCR]:跑 ML Kit 离线 OCR + 标签,把文本拼进 prompt 喂 Gemma 纯文本。**默认**,延迟可控。
 *  - [MULTIMODAL]:跳过 ML Kit,把图直接喂 Gemma 多模态。端侧延迟显著(10s+ 首字),作为实验入口。
 */
enum class OcrMode { OCR, MULTIMODAL }

/** OCR 单元 —— 文本 + 0~1 置信度。 */
data class OcrLine(val text: String, val confidence: Float)

/** OCR 全量结果。 */
data class OcrResult(
    val lines: List<OcrLine>,
    /** language-id 推断的主语种,可能与 [Lang] 不同(原文与输出语言可分离)。 */
    val detectedLang: Lang?,
) {
    fun joinedText(): String = lines.joinToString(" ") { it.text }.trim()
}

/** 图像标签 —— 名称 + 0~1 置信度。 */
data class Label(val name: String, val confidence: Float)

/**
 * 抽象图像输入。不依赖 android.graphics —— core 模块要保持 Android-free。
 * Android 端实现把 Bitmap / ImageProxy 包成 [Bytes]。
 */
sealed interface ImageInput {
    /** 完整文件路径,适合 ML Kit 与 LiteRT-LM 都吃路径的场景。 */
    data class Path(val absolutePath: String) : ImageInput

    /** 内存里的解码字节(如 JPEG/PNG)。 */
    data class Bytes(val data: ByteArray, val mimeType: String = "image/jpeg") : ImageInput {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }
}
