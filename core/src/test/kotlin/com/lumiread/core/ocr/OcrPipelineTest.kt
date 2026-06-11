package com.lumiread.core.ocr

import com.lumiread.core.Lang
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult
import com.lumiread.core.llm.Conversation
import com.lumiread.core.llm.LlmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPipelineTest {

    private fun page(text: String, conf: Float) =
        OcrResult(listOf(OcrLine(text, conf)), detectedLang = Lang.EN)

    @Test fun high_quality_proceeds_without_correction() = runTest {
        val out = OcrPipeline(correction = null)
            .prepare(listOf(page("The bear sleeps in the cave.", 0.95f)), Lang.EN)
        assertTrue(out is OcrPipeline.Outcome.Proceed)
        out as OcrPipeline.Outcome.Proceed
        assertEquals("The bear sleeps in the cave.", out.ocrForPrompt.joinedText())
        assertFalse(out.corrected)
        assertEquals(null, out.note)
    }

    @Test fun unusable_quality_silently_goes_image_only() = runTest {
        // 静默校准:不可信文本不打断,静默丢文本改聊画面。
        val out = OcrPipeline(correction = null)
            .prepare(listOf(page("Th~ l1tt buar", 0.3f)), Lang.EN)
        assertTrue(out is OcrPipeline.Outcome.ImageOnly)
    }

    @Test fun empty_text_goes_image_only() = runTest {
        val out = OcrPipeline(correction = null)
            .prepare(listOf(OcrResult(emptyList(), null)), Lang.EN)
        assertTrue(out is OcrPipeline.Outcome.ImageOnly)
    }

    @Test fun mid_quality_runs_correction_and_uses_validated_text() = runTest {
        val llm = object : LlmEngine {
            override suspend fun warmUp() {}
            override fun generate(prompt: String): Flow<String> = flowOf(
                """{"corrected_text": "The little bear is looking for his mum.",
                    "changes": [{"from": "litt1e", "to": "little", "reason": "1/l"}],
                    "uncertain_parts": []}"""
            )
            override suspend fun startConversation(systemPrompt: String): Conversation =
                throw UnsupportedOperationException()
            override suspend fun close() {}
        }
        val out = OcrPipeline(OcrCorrectionStage(llm))
            .prepare(listOf(page("The litt1e bear is loking for his mum.", 0.75f)), Lang.EN)
        assertTrue(out is OcrPipeline.Outcome.Proceed)
        out as OcrPipeline.Outcome.Proceed
        assertTrue(out.corrected)
        assertEquals("The little bear is looking for his mum.", out.ocrForPrompt.joinedText())
    }

    @Test fun mid_quality_without_correction_passes_through_with_note() = runTest {
        val out = OcrPipeline(correction = null)
            .prepare(listOf(page("The litt1e bear is loking for his mum.", 0.75f)), Lang.EN)
        assertTrue(out is OcrPipeline.Outcome.Proceed)
        out as OcrPipeline.Outcome.Proceed
        assertFalse(out.corrected)
        assertEquals("correction_unavailable_passthrough", out.note)
    }

    @Test fun bad_page_silently_dropped_good_page_still_used() = runTest {
        // 静默校准:不可信页文本剔除,可信页正常携带 —— 不连坐、不打断。
        val out = OcrPipeline(correction = null).prepare(
            listOf(page("Good readable text here.", 0.95f), page("~~ ## ~~", 0.2f)),
            Lang.EN,
        )
        assertTrue(out is OcrPipeline.Outcome.Proceed)
        out as OcrPipeline.Outcome.Proceed
        assertEquals("Good readable text here.", out.ocrForPrompt.joinedText())
    }
}
