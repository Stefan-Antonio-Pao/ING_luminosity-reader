package com.lumiread.core.ocr

import com.lumiread.core.Lang
import com.lumiread.core.llm.Conversation
import com.lumiread.core.llm.LlmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 直接控制 LLM 输出的桩(比 FakeLlmEngine 更精确,用于 JSON 解析矩阵)。 */
private class ScriptedLlm(private val output: String, private val throwError: Boolean = false) : LlmEngine {
    override suspend fun warmUp() {}
    override fun generate(prompt: String): Flow<String> = flow {
        if (throwError) error("scripted llm failure")
        // 模拟流式:按 20 字符分片。
        output.chunked(20).forEach { emit(it) }
    }
    override suspend fun startConversation(systemPrompt: String): Conversation =
        throw UnsupportedOperationException("not used")
    override suspend fun close() {}
}

class OcrCorrectionStageTest {

    private val raw = "The litt1e bear is loking for his mum."

    @Test fun valid_json_correction_accepted() = runTest {
        val llm = ScriptedLlm(
            """{"corrected_text": "The little bear is looking for his mum.",
               "changes": [{"from": "litt1e", "to": "little", "reason": "1/l"},
                           {"from": "loking", "to": "looking", "reason": "typo"}],
               "uncertain_parts": []}"""
        )
        val out = OcrCorrectionStage(llm).correct(raw, Lang.EN)
        assertTrue(out.corrected)
        assertEquals("The little bear is looking for his mum.", out.text)
    }

    @Test fun json_with_markdown_fence_still_parsed() = runTest {
        val llm = ScriptedLlm(
            "```json\n{\"corrected_text\": \"The little bear is looking for his mum.\", \"changes\": [], \"uncertain_parts\": [\"mum\"]}\n```"
        )
        val out = OcrCorrectionStage(llm).correct(raw, Lang.EN)
        assertTrue(out.corrected)
        assertEquals(listOf("mum"), out.uncertainParts)
    }

    @Test fun bad_json_degrades_to_raw_no_crash() = runTest {
        val llm = ScriptedLlm("Sure! Here is the corrected text: The little bear...")
        val out = OcrCorrectionStage(llm).correct(raw, Lang.EN)
        assertFalse(out.corrected)
        assertEquals(raw, out.text)
        assertEquals("bad_json", out.degradedReason)
    }

    @Test fun truncated_json_degrades_to_raw() = runTest {
        val llm = ScriptedLlm("""{"corrected_text": "The little bear is look""")
        val out = OcrCorrectionStage(llm).correct(raw, Lang.EN)
        assertFalse(out.corrected)
        assertEquals(raw, out.text)
    }

    @Test fun missing_corrected_text_key_degrades() = runTest {
        val llm = ScriptedLlm("""{"text": "wrong key", "changes": []}""")
        val out = OcrCorrectionStage(llm).correct(raw, Lang.EN)
        assertFalse(out.corrected)
        assertEquals("bad_json", out.degradedReason)
    }

    @Test fun llm_error_degrades_to_raw_no_crash() = runTest {
        val out = OcrCorrectionStage(ScriptedLlm("", throwError = true)).correct(raw, Lang.EN)
        assertFalse(out.corrected)
        assertEquals(raw, out.text)
        assertTrue(out.degradedReason!!.startsWith("llm_error"))
    }

    @Test fun validator_rejects_number_change_returns_raw() = runTest {
        val rawNum = "He found 3 apples."
        val llm = ScriptedLlm("""{"corrected_text": "He found 8 apples.", "changes": [], "uncertain_parts": []}""")
        val out = OcrCorrectionStage(llm).correct(rawNum, Lang.EN)
        assertFalse(out.corrected)
        assertEquals(rawNum, out.text)
        assertTrue(out.degradedReason!!.startsWith("protected_token_lost"))
    }

    @Test fun empty_input_passthrough() = runTest {
        val out = OcrCorrectionStage(ScriptedLlm("{}")).correct("  ", Lang.ZH)
        assertEquals("", out.text)
        assertFalse(out.corrected)
    }
}
