package com.lumiread.core.ocr

import com.lumiread.core.OcrResult

/**
 * OCR 质量门控(OCR_TECH_DOC §8,轨道 A)。
 *
 * **静默校准修订(2026-06-11,用户验收反馈)**:原方案低置信度(<0.65)主动弹"重拍提示",
 * 真机上中文识别器自报置信度普遍偏低,几乎轮轮触发、体验打断。改为**全程静默**:
 *  - averageConfidence ≥ 0.88 → [Decision.Accept](直接伴读)
 *  - 0.40 ≤ avg < 0.88        → [Decision.CorrectWithGemma](静默保守修正,孩子无感知)
 *  - avg < 0.40               → [Decision.ImageOnlyGuidance](文字不可信:静默丢弃文本、
 *    改聊画面 —— 系统提示原则 6 本来就指示模型"文字乱就聊图",自然不突兀)
 *  - 无文字                    → [Decision.ImageOnlyGuidance]
 * **不再有任何主动重拍打断**;模型若觉得需要,可按系统提示自行温和建议(语言层面,非 App 弹话)。
 *
 * **置信度真实约束**(任务书 §1.1 ⚠):`Text.Line.getConfidence()` 在非捆绑模型/旧 Play 服务
 * 下恒 0。[OcrResult.confidenceAvailable] = false 时**自动退回启发式信号**:
 * 行数量、文本长度、language-id 是否成功 —— 不得想当然。
 */
object OcrQualityGate {

    sealed class Decision {
        /** 质量好,直接进伴读。 */
        data object Accept : Decision()
        /** 中低,进 Gemma 静默保守修正(OcrCorrectionStage)。 */
        data class CorrectWithGemma(val reason: String) : Decision()
        /** 文字不可信或没有文字:静默丢文本,改走图画观察(prompt 已有空文本路径)。 */
        data class ImageOnlyGuidance(val reason: String) : Decision()
    }

    fun evaluate(ocr: OcrResult, normalized: LayoutNormalizer.NormalizedPageText): Decision {
        val text = normalized.plainText.trim()
        if (text.isEmpty()) return Decision.ImageOnlyGuidance("no_text")

        return if (ocr.confidenceAvailable) {
            evaluateByConfidence(normalized)
        } else {
            evaluateByHeuristics(ocr, normalized)
        }
    }

    private fun evaluateByConfidence(normalized: LayoutNormalizer.NormalizedPageText): Decision {
        val lines = normalized.orderedLines
        val totalLen = lines.sumOf { it.text.length }.coerceAtLeast(1)
        val avg = lines.sumOf { (it.confidence * it.text.length).toDouble() }.toFloat() / totalLen
        // EPS 吸收 Float→Double→Float 往返的精度损失(0.88f 加权平均可能算出 0.8799999…)。
        return when {
            avg + EPS >= ACCEPT_THRESHOLD -> Decision.Accept
            avg + EPS >= UNUSABLE_THRESHOLD -> Decision.CorrectWithGemma("avg_confidence=%.3f".format(avg))
            else -> Decision.ImageOnlyGuidance("avg_confidence=%.3f".format(avg))
        }
    }

    /**
     * 置信度不可用时的启发式(任务书 §1.1 列举的信号):
     *  - 文本太短且行碎(平均行长 < 2)→ 大概率是噪声 → 静默丢文本聊画面
     *  - 其余 → CorrectWithGemma(没有把握说"好",静默保守修正一轮最安全)
     */
    private fun evaluateByHeuristics(
        ocr: OcrResult,
        normalized: LayoutNormalizer.NormalizedPageText,
    ): Decision {
        val lines = normalized.orderedLines
        val text = normalized.plainText
        val avgLineLen = text.length.toFloat() / lines.size.coerceAtLeast(1)

        if (lines.size >= 3 && avgLineLen < 2f) {
            return Decision.ImageOnlyGuidance("heuristic:fragmented_lines avgLen=%.1f".format(avgLineLen))
        }
        return Decision.CorrectWithGemma("heuristic:confidence_unavailable")
    }

    const val ACCEPT_THRESHOLD = 0.88f
    /** 低于此值连"保守修正"都不可救(乱码喂模型只会引诱编造)→ 静默丢文本聊画面。 */
    const val UNUSABLE_THRESHOLD = 0.40f
    private const val EPS = 1e-4f
}
