package com.lumiread.core.ocr

import com.lumiread.core.safety.ProtectedTokenDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedTokenDetectorTest {

    @Test fun detects_numbers_dates_phones() {
        val tokens = ProtectedTokenDetector.detect("Page 12, born 2018/05/06, call 138-0000")
        assertTrue(tokens.contains("12"))
        assertTrue(tokens.contains("2018/05/06"))
        assertTrue(tokens.contains("138-0000"))
    }

    @Test fun detects_mid_sentence_proper_nouns_not_sentence_start() {
        val tokens = ProtectedTokenDetector.detect("The bear met Lily in London. Big trees everywhere.")
        assertTrue(tokens.contains("Lily"))
        assertTrue(tokens.contains("London"))
        assertFalse("句首 The 不该保护", tokens.contains("The"))
        assertFalse("句首 Big 不该保护", tokens.contains("Big"))
    }

    @Test fun detects_quoted_names_zh() {
        val tokens = ProtectedTokenDetector.detect("小熊对「乐乐」说:你好呀。")
        assertTrue(tokens.contains("乐乐"))
    }
}

class OcrCorrectionValidatorTest {

    @Test fun accepts_conservative_typo_fix() {
        val r = OcrCorrectionValidator.validate(
            rawText = "The litt1e bear is loking for his mum.",
            correctedText = "The little bear is looking for his mum.",
            declaredChanges = listOf(
                OcrCorrectionValidator.OcrChange("litt1e", "little"),
                OcrCorrectionValidator.OcrChange("loking", "looking"),
            ),
        )
        assertTrue("保守修正应通过: ${r.reason}", r.accepted)
        assertEquals("The little bear is looking for his mum.", r.safeText)
    }

    @Test fun rejects_changed_number() {
        val r = OcrCorrectionValidator.validate(
            rawText = "He found 3 apples on page 12.",
            correctedText = "He found 5 apples on page 12.",
        )
        assertFalse(r.accepted)
        assertEquals("He found 3 apples on page 12.", r.safeText)
        assertTrue(r.reason!!.startsWith("protected_token_lost"))
    }

    @Test fun rejects_changed_name() {
        val r = OcrCorrectionValidator.validate(
            rawText = "The bear met Lily in the forest.",
            correctedText = "The bear met Lucy in the forest.",
        )
        assertFalse(r.accepted)
        assertTrue(r.reason!!.contains("Lily"))
    }

    @Test fun rejects_rewrite_with_high_change_ratio() {
        val r = OcrCorrectionValidator.validate(
            rawText = "The bear sleeps in the cave all winter long.",
            correctedText = "A fluffy brown animal rests inside a rocky den during cold months.",
        )
        assertFalse(r.accepted)
    }

    @Test fun rejects_fabricated_expansion() {
        val r = OcrCorrectionValidator.validate(
            rawText = "The bear sleeps.",
            correctedText = "The bear sleeps. Then a rabbit comes and they play together in the forest happily.",
        )
        assertFalse(r.accepted)
        assertEquals("length_growth", r.reason)
    }

    @Test fun rejects_hallucinated_change_declaration() {
        val r = OcrCorrectionValidator.validate(
            rawText = "The bear sleeps in winter.",
            correctedText = "The bear sleeps in winter!",
            declaredChanges = listOf(OcrCorrectionValidator.OcrChange("summmer", "winter")),
        )
        assertFalse(r.accepted)
        assertTrue(r.reason!!.startsWith("hallucinated_change_from"))
    }

    @Test fun empty_correction_falls_back_to_raw() {
        val r = OcrCorrectionValidator.validate("原文在这里。", "")
        assertFalse(r.accepted)
        assertEquals("原文在这里。", r.safeText)
    }

    @Test fun zh_conservative_fix_accepted() {
        val r = OcrCorrectionValidator.validate(
            rawText = "小熊在森材里玩。",
            correctedText = "小熊在森林里玩。",
        )
        assertTrue("中文单字修正应通过: ${r.reason}", r.accepted)
    }
}
