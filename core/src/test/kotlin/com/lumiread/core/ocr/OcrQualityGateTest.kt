package com.lumiread.core.ocr

import com.lumiread.core.Lang
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrQualityGateTest {

    private fun result(lines: List<OcrLine>, confAvailable: Boolean = true, lang: Lang? = Lang.EN) =
        OcrResult(lines, detectedLang = lang, confidenceAvailable = confAvailable)

    private fun gate(ocr: OcrResult): OcrQualityGate.Decision =
        OcrQualityGate.evaluate(ocr, LayoutNormalizer.normalize(ocr))

    @Test fun high_confidence_accepts() {
        val d = gate(result(listOf(OcrLine("The little bear is looking for his mum.", 0.96f))))
        assertTrue(d is OcrQualityGate.Decision.Accept)
    }

    @Test fun mid_confidence_goes_to_correction() {
        val d = gate(result(listOf(OcrLine("The litt1e bear is loking for his mum.", 0.75f))))
        assertTrue(d is OcrQualityGate.Decision.CorrectWithGemma)
    }

    @Test fun very_low_confidence_silently_drops_text_to_image_only() {
        // 静默校准:不可信文本不弹提示,静默丢弃改聊画面。
        val d = gate(result(listOf(OcrLine("Th~ l1tt buar 15 lo0k", 0.30f))))
        assertTrue(d is OcrQualityGate.Decision.ImageOnlyGuidance)
    }

    @Test fun low_but_salvageable_confidence_goes_to_silent_correction() {
        // 0.40~0.65 旧方案是"重拍打断",静默方案改为保守修正(孩子无感知)。
        val d = gate(result(listOf(OcrLine("The litt1e bear is loking", 0.55f))))
        assertTrue(d is OcrQualityGate.Decision.CorrectWithGemma)
    }

    @Test fun empty_text_goes_image_only() {
        val d = gate(result(emptyList()))
        assertTrue(d is OcrQualityGate.Decision.ImageOnlyGuidance)
    }

    @Test fun boundary_088_accepts() {
        val d = gate(result(listOf(OcrLine("Boundary test text here.", 0.88f))))
        assertTrue(d is OcrQualityGate.Decision.Accept)
    }

    @Test fun boundary_065_corrects() {
        val d = gate(result(listOf(OcrLine("Boundary test text here.", 0.65f))))
        assertTrue(d is OcrQualityGate.Decision.CorrectWithGemma)
    }

    @Test fun confidence_unavailable_uses_heuristics_not_zero() {
        // 全 0 置信度 + confidenceAvailable=false:不能按数值判 AskRetake,
        // 正常成句文本应走保守修正。
        val d = gate(
            result(
                listOf(OcrLine("The little bear is looking for his mum.", 0f)),
                confAvailable = false,
            )
        )
        assertTrue("应走启发式 CorrectWithGemma,实际 $d", d is OcrQualityGate.Decision.CorrectWithGemma)
    }

    @Test fun confidence_unavailable_fragmented_lines_silently_image_only() {
        val d = gate(
            result(
                listOf(OcrLine("a", 0f), OcrLine("~", 0f), OcrLine("b", 0f), OcrLine("c", 0f)),
                confAvailable = false,
                lang = null,
            )
        )
        assertTrue("碎行启发式应静默 ImageOnly,实际 $d", d is OcrQualityGate.Decision.ImageOnlyGuidance)
    }
}
