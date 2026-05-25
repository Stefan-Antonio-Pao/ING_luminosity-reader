package com.lumiread.core

/** 输出语言。绑在设置项里,由用户独立切换。 */
enum class Lang { ZH, EN }

/** 三个年龄段。 */
enum class AgeBand { TODDLER, PRESCHOOL, PREADOLESCENT }

/**
 * 多模态 / 独立 OCR 二选一。 用户授权解锁。
 *
 * - [OCR]:跑 ML Kit 离线 OCR + 标签,把文本拼进 prompt 喂 Gemma 纯文本。**默认**,延迟可控。
 * - [MULTIMODAL]:跳过 ML Kit,把图直接喂 Gemma 多模态。端侧延迟显著(10s+ 首字),作为实验入口。
 */
enum class OcrMode { OCR, MULTIMODAL }

/**
 * 输出模式 —— 单语 / 中英双语(v1.1,2026-05-25)。
 *
 * 与 [Lang] / [OcrMode] / [GemmaModel] / 界面语言完全正交,各自独立持久化、互不联动。
 *
 * - [MONOLINGUAL]:**默认**。Gemma 仅用 [Lang] 指定的那一种语言输出,行为与 v1.0 一致。
 * - [BILINGUAL]:Gemma 同一句先用主语言(=当前 [Lang])再用另一语言成对输出,中英分行清晰呈现;
 * `vits-melo-tts-zh_en` 原生支持中英混读,无需切换 TTS 模型。
 *
 * 长度上限在 [com.lumiread.core.prompt.SocraticPromptBuilder] 中按"每种语言独立"应用,
 * 避免双语模式下两侧都被腰斩。
 */
enum class OutputMode { MONOLINGUAL, BILINGUAL }

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
 * Gemma 模型选项(v1.1 2026-05-25 双模型架构)。
 *
 * **为什么需要枚举**:E2B 是纯文本(2.59 GB),配 `EngineConfig.visionBackend` 会在多模态调用时
 * SIGSEGV(已知崩溃路径);E4B 是原生多模态(3.66 GB)。两者并存,用户在设置页选择。
 *
 * 字段语义:
 * - [fileName]:`getExternalFilesDir(null)` 下的文件名(adb push / SAF 导入目标)
 * - [expectedSizeBytes]:校验下载完整性的下限(实际允许 ±10% 容差)
 * - [supportsMultimodal]:决定 `Gemma4Engine` 是否给 `EngineConfig.visionBackend` 传非 null Backend;
 * 也决定设置页"OCR 模式"按钮是否可点(false → 强制 OcrMode=OCR)
 * - [hfModelPageUrl]:浏览器 Intent 跳转目标(用户在 HF 接受 Gemma 许可后下载)
 * - [displayName]:UI 显示用,内含大小与能力提示
 *
 * v1.1 双模型重构。
 */
enum class GemmaModel(
    val fileName: String,
    val expectedSizeBytes: Long,
    val supportsMultimodal: Boolean,
    val hfModelPageUrl: String,
    val displayName: String,
) {
    E2B(
        fileName = "gemma-4-E2B-it.litertlm",
        expectedSizeBytes = 2_590_000_000L,
        supportsMultimodal = false,
        hfModelPageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
        displayName = "Gemma 4 E2B(纯文本,2.59 GB)",
    ),
    E4B(
        fileName = "gemma-4-E4B-it.litertlm",
        expectedSizeBytes = 3_660_000_000L,
        supportsMultimodal = true,
        hfModelPageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
        displayName = "Gemma 4 E4B(多模态,3.66 GB)",
    ),
}

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
