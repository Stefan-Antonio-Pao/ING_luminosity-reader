package com.lumiread.core

/** 输出语言。绑在设置项里,由用户独立切换(CLAUDE.md §2.5)。 */
enum class Lang { ZH, EN }

/** 三个年龄段(CLAUDE.md §2.6)。 */
enum class AgeBand { TODDLER, PRESCHOOL, PREADOLESCENT }

/**
 * 多模态 / 独立 OCR 二选一。Phase 6 用户授权解锁(CLAUDE.md §2.4 2026-05-24 修订)。
 *
 *  - [OCR]:跑 ML Kit 离线 OCR + 标签,把文本拼进 prompt 喂 Gemma 纯文本。**默认**,延迟可控。
 *  - [MULTIMODAL]:跳过 ML Kit,把图直接喂 Gemma 多模态。端侧延迟显著(10s+ 首字),作为实验入口。
 */
enum class OcrMode { OCR, MULTIMODAL }

/**
 * 输出模式 —— 单语 / 中英双语(v1.1 步骤四,2026-05-25)。
 *
 * 与 [Lang] / [OcrMode] / [GemmaModel] / 界面语言完全正交,各自独立持久化、互不联动。
 *
 *  - [MONOLINGUAL]:**默认**。Gemma 仅用 [Lang] 指定的那一种语言输出,行为与 v1.0 一致。
 *  - [BILINGUAL]:Gemma 同一句先用主语言(=当前 [Lang])再用另一语言成对输出,中英分行清晰呈现;
 *    `vits-melo-tts-zh_en` 原生支持中英混读,无需切换 TTS 模型。
 *
 *  长度上限在 [com.lumiread.core.prompt.SocraticPromptBuilder] 中按"每种语言独立"应用,
 *  避免双语模式下两侧都被腰斩。
 */
enum class OutputMode { MONOLINGUAL, BILINGUAL }

/**
 * 纯 Kotlin 几何类型(:core 不能引用 android.graphics,见 FACTS#F12 第 5 条)。
 * :app 的 MlKitOcrService 负责从 `Rect`/`Point[]` 映射过来。
 */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class OcrPoint(val x: Int, val y: Int)

/** 绘本版面区域标记(OCR_TECH_DOC §6),由 LayoutNormalizer 推断。 */
enum class PageRegion { LEFT_PAGE, RIGHT_PAGE, TOP_TITLE, BODY_TEXT, SPEECH_BUBBLE, CAPTION, PAGE_NUMBER, UNKNOWN }

/**
 * OCR 单元 —— 富结构(轨道 A,2026-06-11)。
 *
 * 新字段全部带默认值,保持既有调用点(`OcrLine(text, confidence)`)与 JVM 单测二进制级兼容。
 * 字段语义对应 ML Kit `Text.Line` 真实 API(FACTS#F12,javap 核对 2026-06-11):
 * boundingBox/cornerPoints 可空 → 这里用 null/empty 表达;angle/recognizedLanguage 同理。
 */
data class OcrLine(
    val text: String,
    /** 行级置信度 0~1。⚠ 注意 [OcrResult.confidenceAvailable]:false 时本值不可信(ML Kit 返回了 0)。 */
    val confidence: Float,
    val box: OcrBox? = null,
    val cornerPoints: List<OcrPoint> = emptyList(),
    val angle: Float? = null,
    /** ML Kit 行级识别语种(BCP-47,如 "zh"/"en"/"und"),与全局 language-id 路由互补。 */
    val recognizedLanguage: String? = null,
    val pageRegion: PageRegion = PageRegion.UNKNOWN,
)

/** OCR 全量结果(轨道 A 起携带图像尺寸与置信度可用性,新字段带默认值保兼容)。 */
data class OcrResult(
    val lines: List<OcrLine>,
    /** language-id 推断的主语种,可能与 [Lang] 不同(原文与输出语言可分离)。 */
    val detectedLang: Lang?,
    /** 原图尺寸(px)。0 = 未知(旧调用点/Fake),LayoutNormalizer 此时退化为相对坐标推断。 */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    /**
     * 置信度是否真实可用。任务书 §1.1 警示:非捆绑模型/旧 Play 服务下 `getConfidence()` 恒 0。
     * MlKitOcrService 在所有行置信度都≈0 时置 false;OcrQualityGate 据此退回启发式信号,
     * **不得想当然把 0 当"很差"或把缺省 1f 当"很好"**。
     */
    val confidenceAvailable: Boolean = true,
) {
    fun joinedText(): String = lines.joinToString(" ") { it.text }.trim()

    /** 行置信度均值(按文本长度加权,长行话语权更大);无行返回 0。 */
    fun averageConfidence(): Float {
        if (lines.isEmpty()) return 0f
        val totalLen = lines.sumOf { it.text.length }.coerceAtLeast(1)
        return lines.sumOf { (it.confidence * it.text.length).toDouble() }.toFloat() / totalLen
    }
}

/** 图像标签 —— 名称 + 0~1 置信度。 */
data class Label(val name: String, val confidence: Float)

/**
 * Gemma 模型选项(v1.1 2026-05-25 双模型架构)。
 *
 * **为什么需要枚举**:E2B 是纯文本(2.59 GB),配 `EngineConfig.visionBackend` 会在多模态调用时
 * SIGSEGV(crash.txt 已记录);E4B 是原生多模态(3.66 GB)。两者并存,用户在设置页选择。
 *
 * 字段语义:
 *  - [fileName]:`getExternalFilesDir(null)` 下的文件名(adb push / SAF 导入目标)
 *  - [expectedSizeBytes]:校验下载完整性的下限(实际允许 ±10% 容差)
 *  - [supportsMultimodal]:决定 `Gemma4Engine` 是否给 `EngineConfig.visionBackend` 传非 null Backend;
 *    也决定设置页"OCR 模式"按钮是否可点(false → 强制 OcrMode=OCR)
 *  - [hfModelPageUrl]:浏览器 Intent 跳转目标(用户在 HF 接受 Gemma 许可后下载)
 *  - [displayName]:UI 显示用,内含大小与能力提示
 *
 * 见 FACTS#F1 / F1.E4B / F2.5。
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
