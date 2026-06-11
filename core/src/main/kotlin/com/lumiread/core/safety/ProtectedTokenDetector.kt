package com.lumiread.core.safety

/**
 * 受保护词检测(OCR_TECH_DOC §9/§11.1,轨道 A)。
 *
 * 从 OCR 原文里抽出"模型不许改"的 token:数字(含页码/日期/价格/电话)、英文专有名词
 * (句中大写词)、中文人名常见姓氏开头的 2~3 字词无法可靠识别 → 中文侧保守起见只保护
 * 数字与引号内内容。OcrCorrectionValidator 据此拒绝 Gemma 对这些 token 的任何修改。
 *
 * 纯确定性规则,无 LLM(OCR_TECH_DOC §13:能由 App 稳定完成的就由 App 完成)。
 */
object ProtectedTokenDetector {

    /**
     * 返回原文中所有受保护 token(原样子串,可重复)。
     *  - 数字串:连续的数字,可含 . : / - 分隔(日期/时间/价格/电话)。
     *  - 英文专有名词:**非句首**的首字母大写词(句首大写是普通语法,不保护)。
     *  - 引号内内容:成对引号「」『』“”"" 中的短内容(≤ 20 字符)。
     */
    fun detect(rawText: String): List<String> {
        val tokens = mutableListOf<String>()
        tokens += NUMBER_PATTERN.findAll(rawText).map { it.value }
        tokens += properNouns(rawText)
        tokens += quotedSpans(rawText)
        return tokens.distinct()
    }

    private fun properNouns(text: String): List<String> {
        val result = mutableListOf<String>()
        // 按句切开,句内除首词外的大写词视为专有名词。
        for (sentence in text.split('.', '!', '?', '。', '!', '?', '\n')) {
            val words = sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            for ((i, w) in words.withIndex()) {
                if (i == 0) continue
                val cleaned = w.trim(',', ';', ':', '"', '\'', ')', '(')
                if (cleaned.length >= 2 && cleaned[0].isUpperCase() && cleaned.drop(1).any { it.isLowerCase() }) {
                    result += cleaned
                }
            }
        }
        return result
    }

    private fun quotedSpans(text: String): List<String> {
        val result = mutableListOf<String>()
        for ((open, close) in QUOTE_PAIRS) {
            var idx = 0
            while (true) {
                val start = text.indexOf(open, idx)
                if (start < 0) break
                val end = text.indexOf(close, start + 1)
                if (end < 0) break
                val span = text.substring(start + 1, end)
                if (span.isNotBlank() && span.length <= 20) result += span
                idx = end + 1
            }
        }
        return result
    }

    // 只保护**独立**数字:嵌在字母词内的数字(litt1e / l0ok)正是 OCR l/1、O/0 混淆,
    // 是修正阶段该修的对象,不能保护。前后用字母否定环视排除。
    private val NUMBER_PATTERN = Regex("(?<![A-Za-z\\d])\\d+(?:[./:\\-]\\d+)*(?![A-Za-z\\d])")
    private val QUOTE_PAIRS = listOf('「' to '」', '『' to '』', '“' to '”')
}
