package com.lumiread.core.tools

import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.core.data.DictEntry

/**
 * `lookup_word` 原生函数的**年龄自适应格式化体**(任务书 §4 / §6 双层表达)。
 *
 * 这是"把 v1.3.0 的年龄段差异写进原生函数"的关键:同一个词条,工具根据当前 [AgeBand] 产出
 * **不同深度**的结构化结果(不仅靠系统提示词):
 *  - Toddler:极简,只留最核心一句 + 例句作为"试着说说看"的玩法;
 *  - Preschool:简单释义(1~2 句)+ 例句;
 *  - Preadolescent:较丰富的完整释义 + 例句。
 *
 * 查不到词([entry] = null)时**绝不编造**:返回 status=not_found + 一句适龄、适语言的"和你一起看看"
 * 兜底,交给模型自然处理(CLAUDE.md §C5 / 任务书 §0.5)。
 *
 * 纯 JVM、可单测。返回 `Map<String,String>`(LiteRT-LM 会转成 JSON 回灌给模型)。
 */
object WordExplainer {

    fun explain(
        entry: DictEntry?,
        term: String,
        ageBand: AgeBand,
        lang: Lang,
    ): Map<String, String> {
        if (entry == null) {
            return mapOf(
                "term" to term,
                "status" to "not_found",
                "note" to notFoundNote(term, lang),
            )
        }

        val shaped = when (ageBand) {
            // Toddler 只保留第一句,最简。
            AgeBand.TODDLER -> firstSentence(entry.definition)
            // Preschool 取前两句。
            AgeBand.PRESCHOOL -> firstSentences(entry.definition, 2)
            // Preadolescent 给完整释义。
            AgeBand.PREADOLESCENT -> entry.definition.trim()
        }

        val result = linkedMapOf(
            "term" to entry.term,
            "status" to "found",
            "definition" to shaped,
            // 把目标年龄段一并回灌,让模型据此用词(双层表达:工具已裁深度,提示词再控语气)。
            "audience" to ageBand.name.lowercase(),
        )
        // 例句:三档都附(Toddler 当作"跟读/玩法"),有就给。
        entry.example?.takeIf { it.isNotBlank() }?.let { result["example"] = it.trim() }
        return result
    }

    private fun notFoundNote(term: String, lang: Lang): String = when (lang) {
        Lang.ZH -> "我的小词典里还没有「$term」这个词,我们可以一起看看图、猜猜它的意思。"
        Lang.EN -> "My little dictionary doesn't have \"$term\" yet — let's look at the picture and figure it out together."
    }

    /** 取第一句(到第一个句末标点为止;没有就整段)。中英标点都认。 */
    private fun firstSentence(text: String): String {
        val t = text.trim()
        val end = t.indexOfFirst { it == '.' || it == '。' || it == '!' || it == '!' || it == '?' || it == '?' }
        return if (end >= 0) t.substring(0, end + 1).trim() else t
    }

    /** 取前 [n] 句。 */
    private fun firstSentences(text: String, n: Int): String {
        val t = text.trim()
        var count = 0
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '.' || c == '。' || c == '!' || c == '!' || c == '?' || c == '?') {
                count++
                if (count >= n) return t.substring(0, i + 1).trim()
            }
            i++
        }
        return t
    }
}
