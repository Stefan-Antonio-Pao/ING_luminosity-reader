package com.lumiread.core.ocr

import com.lumiread.core.Lang
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OCR 校准管线编排(轨道 A):LayoutNormalizer → OcrQualityGate → OcrCorrectionStage → Validator。
 *
 * 这是 ChatSession 消费的唯一入口 —— "接通而非摆设"(任务书 §1.1):每轮带图的 OCR 结果
 * 必须流经这里再进伴读 prompt。
 *
 * **App 控制的确定性流程,不是 LLM 工具链**:
 *  - 每页独立 normalize(几何坐标不跨照片混用),按页序拼接;
 *  - 质量决策取全场最保守(任一页 AskRetake → 整轮 AskRetake);
 *  - 仅 CorrectWithGemma 档触发一次 Gemma 结构化修正(带超时,失败/超时保留原文);
 *  - 任何路径都不抛(除 cancellation),App 绝不崩。
 *
 * @param correction null = 无修正能力(JVM 单测/Fake 模式),CorrectWithGemma 档退化为原文直通。
 */
class OcrPipeline(
    private val correction: OcrCorrectionStage?,
    private val correctionTimeoutMs: Long = DEFAULT_CORRECTION_TIMEOUT_MS,
) {

    /** 一轮的最终 OCR 决策(全程静默,质量信号只进日志,不打断孩子)。 */
    sealed class Outcome {
        /** 文本可用(可能已修正)。[ocrForPrompt] 是下游唯一应消费的 OCR。 */
        data class Proceed(
            val ocrForPrompt: OcrResult,
            val uncertainParts: List<String>,
            val corrected: Boolean,
            val note: String?,
        ) : Outcome()

        /** 无可读文字 / 文字不可信 → 静默丢文本,改聊画面(prompt 已有空文本路径)。 */
        data class ImageOnly(val reason: String) : Outcome()
    }

    suspend fun prepare(pages: List<OcrResult>, outputLang: Lang): Outcome {
        if (pages.isEmpty()) return Outcome.ImageOnly("no_pages")

        // 每页独立 normalize + gate。
        val normalized = pages.map { LayoutNormalizer.normalize(it) }
        val decisions = pages.zip(normalized).map { (page, norm) -> OcrQualityGate.evaluate(page, norm) }

        // 静默校准(2026-06-11):不可信页的文本**静默剔除**(不进 prompt、不提示),
        // 可信页正常携带 —— 单页不可信不连坐整轮。
        val usableTexts = normalized.zip(decisions)
            .filter { (_, d) -> d !is OcrQualityGate.Decision.ImageOnlyGuidance }
            .map { (norm, _) -> norm.plainText }
            .filter { it.isNotBlank() }
        val combinedText = usableTexts.joinToString("\n")
        if (combinedText.isBlank()) {
            val reason = decisions.filterIsInstance<OcrQualityGate.Decision.ImageOnlyGuidance>()
                .firstOrNull()?.reason ?: "no_text_all_pages"
            return Outcome.ImageOnly(reason)
        }

        val detectedLang = pages.mapNotNull { it.detectedLang }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val confidenceAvailable = pages.all { it.confidenceAvailable }
        val avgConf = pages.flatMap { it.lines }.let { lines ->
            if (lines.isEmpty()) 0f
            else {
                val totalLen = lines.sumOf { it.text.length }.coerceAtLeast(1)
                lines.sumOf { (it.confidence * it.text.length).toDouble() }.toFloat() / totalLen
            }
        }

        fun asResult(text: String) = OcrResult(
            lines = listOf(OcrLine(text, avgConf)),
            detectedLang = detectedLang,
            confidenceAvailable = confidenceAvailable,
        )

        val needsCorrection = decisions.any { it is OcrQualityGate.Decision.CorrectWithGemma }
        if (!needsCorrection || correction == null) {
            val note = if (needsCorrection) "correction_unavailable_passthrough" else null
            return Outcome.Proceed(asResult(combinedText), emptyList(), corrected = false, note = note)
        }

        // CorrectWithGemma:一次结构化修正,带超时;失败/超时保留原文(绝不崩)。
        val correctionLang = detectedLang ?: outputLang
        val result = withTimeoutOrNull(correctionTimeoutMs) {
            correction.correct(combinedText, correctionLang)
        }
        return if (result == null) {
            Outcome.Proceed(asResult(combinedText), emptyList(), corrected = false, note = "correction_timeout")
        } else {
            Outcome.Proceed(
                ocrForPrompt = asResult(result.text),
                uncertainParts = result.uncertainParts,
                corrected = result.corrected,
                note = result.degradedReason,
            )
        }
    }

    companion object {
        /**
         * 修正超时:端侧 E2B CPU 一轮生成实测 ~16 s(DEV_LOG 2026-05-31),修正文本短、
         * 30 s 给 2 倍余量;超时保留原文,不拖垮伴读首响。
         */
        const val DEFAULT_CORRECTION_TIMEOUT_MS = 30_000L
    }
}
