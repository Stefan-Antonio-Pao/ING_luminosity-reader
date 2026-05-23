package com.lumiread.core

import com.lumiread.core.llm.FakeLlmEngine
import com.lumiread.core.pipeline.Pipeline
import com.lumiread.core.pipeline.PipelineEvent
import com.lumiread.core.prompt.SocraticPromptBuilder
import com.lumiread.core.tts.FakeTtsEngine
import com.lumiread.core.vision.FakeImageLabelService
import com.lumiread.core.vision.FakeOcrService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineTest {

    private val image = ImageInput.Path("/dev/null/page.jpg")

    @Test fun fake_pipeline_emits_ordered_events_single_page() = runTest {
        val pipeline = Pipeline(
            ocr = FakeOcrService(),
            labels = FakeImageLabelService(),
            llm = FakeLlmEngine(chunkDelayMs = 0L),
            tts = FakeTtsEngine { /* swallow */ },
        )
        val events = pipeline.run(image, Lang.EN, AgeBand.PRESCHOOL).toList()

        // 顺序:OcrPage(0/1) → Labels → 多个 LlmChunk → Done
        val firstOcr = events.first() as? PipelineEvent.OcrPage
        assertTrue("第一个事件应是 OcrPage", firstOcr != null)
        assertEquals(0, firstOcr!!.index)
        assertEquals(1, firstOcr.of)
        assertTrue("第二个事件应是 Labels", events[1] is PipelineEvent.Labels)
        assertEquals("最后一个事件应是 Done", PipelineEvent.Done, events.last())

        val llmText = events.filterIsInstance<PipelineEvent.LlmChunk>()
            .joinToString("") { it.text }
        assertTrue("LLM 流应非空", llmText.isNotEmpty())
        assertTrue("LLM 流应带 Fake 水印", llmText.contains(FakeLlmEngine.FAKE_PREFIX))
    }

    @Test fun fake_pipeline_handles_multi_page() = runTest {
        val pipeline = Pipeline(
            ocr = FakeOcrService(),
            labels = FakeImageLabelService(),
            llm = FakeLlmEngine(chunkDelayMs = 0L),
            tts = FakeTtsEngine { /* swallow */ },
        )
        val images = listOf(
            ImageInput.Path("/dev/null/page1.jpg"),
            ImageInput.Path("/dev/null/page2.jpg"),
            ImageInput.Path("/dev/null/page3.jpg"),
        )
        val events = pipeline.run(images, Lang.EN, AgeBand.PRESCHOOL).toList()

        val ocrPages = events.filterIsInstance<PipelineEvent.OcrPage>()
        assertEquals("应有 3 个 OcrPage 事件", 3, ocrPages.size)
        ocrPages.forEachIndexed { i, ev ->
            assertEquals(i, ev.index)
            assertEquals(3, ev.of)
        }
        assertEquals("最后一个事件应是 Done", PipelineEvent.Done, events.last())
    }

    @Test fun prompt_builder_zh_includes_zh_persona_and_age_hint() {
        val ocr = OcrResult(
            lines = listOf(OcrLine("小狗在公园里玩。", 0.95f)),
            detectedLang = Lang.ZH,
        )
        val labels = listOf(Label("dog", 0.9f), Label("park", 0.8f))
        val prompt = SocraticPromptBuilder.build(ocr, labels, Lang.ZH, AgeBand.TODDLER)
        assertTrue(prompt.contains("简体中文"))
        assertTrue(prompt.contains("1~3 岁"))
        assertTrue(prompt.contains("小狗在公园里玩"))
    }

    @Test fun prompt_builder_en_includes_en_persona_and_age_hint() {
        val ocr = OcrResult(
            lines = listOf(OcrLine("The puppy plays.", 0.9f)),
            detectedLang = Lang.EN,
        )
        val labels = listOf(Label("dog", 0.9f), Label("park", 0.8f))
        val prompt = SocraticPromptBuilder.build(ocr, labels, Lang.EN, AgeBand.PREADOLESCENT)
        assertTrue(prompt.contains("Language: English"))
        assertTrue(prompt.contains("6–10 year-old"))
        assertTrue(prompt.contains("The puppy plays"))
    }
}
