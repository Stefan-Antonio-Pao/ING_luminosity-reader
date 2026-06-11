package com.lumiread.core.data

import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.core.tools.WordExplainer
import java.io.File
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **lookup_word ↔ 词典硬验证门**(任务书 §1.3,用户特别要求)。
 *
 * 直接打开**随包同一个** SQLite 文件(app/src/main/assets/dict/lumi_dict.db),
 * 用与真机完全相同的 [DictDbContract.QUERY] + [DictDbContract.normalizeTerm],
 * 断言 `caterpillar`(英,WordNet)与 `毛毛虫`(中,CC-CEDICT)返回**真实词条**——
 * 不是 stub、不是模型臆造。
 *
 * DB 由 `python scripts/build_dict.py` 生成;不在仓库时跳过(assumeTrue)而非误报失败,
 * 但 CI/验收前必须先跑构建脚本。
 */
class DictDbHardVerificationTest {

    private val dbFile = File("../app/src/main/assets/dict/lumi_dict.db")

    private fun query(term: String, lang: Lang): DictEntry? {
        assumeTrue("词典 DB 未构建,先跑 scripts/build_dict.py", dbFile.isFile)
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.prepareStatement(DictDbContract.QUERY).use { st ->
                st.setString(1, DictDbContract.normalizeTerm(term, lang))
                st.setString(2, DictDbContract.langCode(lang))
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return DictEntry(rs.getString(1), rs.getString(2), rs.getString(3))
                }
            }
        }
    }

    @Test fun caterpillar_returns_real_wordnet_entry() {
        val entry = query("caterpillar", Lang.EN)
        assertTrue("caterpillar 必须命中 WordNet", entry != null)
        // WordNet 3.1 data.noun 真实 gloss(build_dict.py 自检同源):
        // "a wormlike and often brightly colored and hairy or spiny larva of a butterfly or moth"
        assertTrue(
            "释义应来自 WordNet 真实词条,实际: ${entry!!.definition}",
            entry.definition.contains("larva") && entry.definition.contains("butterfly"),
        )
    }

    @Test fun maomaochong_returns_real_cedict_entry() {
        val entry = query("毛毛虫", Lang.ZH)
        assertTrue("毛毛虫 必须命中 CC-CEDICT", entry != null)
        // CC-CEDICT 真实词条:毛毛蟲 毛毛虫 [mao2 mao5 chong2] /caterpillar/
        assertTrue(
            "释义应来自 CC-CEDICT 真实词条,实际: ${entry!!.definition}",
            entry.definition.contains("caterpillar"),
        )
    }

    @Test fun case_insensitive_english_lookup() {
        // 真机上孩子词来自 LLM 参数,可能大写开头;normalizeTerm 必须吸收。
        val entry = query("Caterpillar", Lang.EN)
        assertTrue(entry != null)
    }

    @Test fun miss_returns_null_not_crash() {
        assertEquals(null, query("zzzz-not-a-word-zzzz", Lang.EN))
        assertEquals(null, query("不存在的词条组合啊", Lang.ZH))
    }

    @Test fun word_explainer_formats_real_entry_for_preschool() {
        // 端到端(JVM 侧):真实词条 → WordExplainer 年龄裁剪 → lookup_word 返回形状。
        val entry = query("caterpillar", Lang.EN)
        assumeTrue(entry != null)
        val result = WordExplainer.explain(entry, "caterpillar", AgeBand.PRESCHOOL, Lang.EN)
        assertEquals("found", result["status"])
        assertTrue(result["definition"]!!.contains("larva"))
    }
}
