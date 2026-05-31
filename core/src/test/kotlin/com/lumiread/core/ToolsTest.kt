package com.lumiread.core

import com.lumiread.core.data.DictEntry
import com.lumiread.core.data.EmptyOfflineDictionary
import com.lumiread.core.tools.SceneClassifier
import com.lumiread.core.tools.WordExplainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.0.0 Step 3:原生函数执行体的纯逻辑单测(CLAUDE.md §B3 黄金样本思路)。
 * 工具的 LiteRT-LM 绑定(LumiReadToolSet)在 :app;此处覆盖 :core 的确定性逻辑。
 */
class ToolsTest {

    // ---- SceneClassifier ----

    @Test fun classify_long_ocr_is_book() {
        assertEquals(SceneClassifier.SCENE_BOOK, SceneClassifier.classify("toy, plastic", "从前有一只小狗"))
    }

    @Test fun classify_print_label_is_book_even_without_ocr() {
        assertEquals(SceneClassifier.SCENE_BOOK, SceneClassifier.classify("book, paper, font", ""))
    }

    @Test fun classify_object_when_no_text_no_print_labels() {
        assertEquals(SceneClassifier.SCENE_OBJECT, SceneClassifier.classify("apple, fruit, red", ""))
    }

    @Test fun classify_short_ocr_falls_back_to_labels() {
        // 3 个字母 < 阈值 6 → object(标签无印刷信号)
        assertEquals(SceneClassifier.SCENE_OBJECT, SceneClassifier.classify("cup, mug", "abc"))
    }

    @Test fun classify_object_with_incidental_digits_is_object() {
        // 检查点① 回归:飞机注册号被 OCR 成 "AUA。 00000 000000000" —— 字母仅 3 个(AUA),其余是数字。
        // 旧版按总字符 ≥6 误判 book;新版按字母数 → object。
        assertEquals(
            SceneClassifier.SCENE_OBJECT,
            SceneClassifier.classify("Airplane, Aviation, Vehicle, Aircraft, Airliner", "AUA。 00000 000000000"),
        )
    }

    // ---- WordExplainer 年龄自适应 ----

    private val multiSentence = DictEntry(
        term = "puppy",
        definition = "A puppy is a young dog. It is small and playful. People often keep puppies as pets.",
        example = "The puppy wagged its tail.",
    )

    @Test fun explain_toddler_keeps_only_first_sentence() {
        val r = WordExplainer.explain(multiSentence, "puppy", AgeBand.TODDLER, Lang.EN)
        assertEquals("found", r["status"])
        assertEquals("A puppy is a young dog.", r["definition"])
        assertEquals("toddler", r["audience"])
        assertTrue("toddler 也带例句", r.containsKey("example"))
    }

    @Test fun explain_preschool_keeps_two_sentences() {
        val r = WordExplainer.explain(multiSentence, "puppy", AgeBand.PRESCHOOL, Lang.EN)
        assertEquals("A puppy is a young dog. It is small and playful.", r["definition"])
    }

    @Test fun explain_preadolescent_keeps_full_definition() {
        val r = WordExplainer.explain(multiSentence, "puppy", AgeBand.PREADOLESCENT, Lang.EN)
        assertEquals(multiSentence.definition, r["definition"])
    }

    @Test fun explain_not_found_never_fabricates() {
        val r = WordExplainer.explain(null, "wug", AgeBand.PRESCHOOL, Lang.EN)
        assertEquals("not_found", r["status"])
        assertNull("查不到不得给 definition", r["definition"])
        assertTrue("应有适语言兜底 note", r["note"]?.contains("wug") == true)
    }

    @Test fun empty_dictionary_returns_null() {
        assertNull(EmptyOfflineDictionary.lookup("anything", Lang.ZH))
        assertNull(EmptyOfflineDictionary.lookup("任意", Lang.EN))
    }
}
