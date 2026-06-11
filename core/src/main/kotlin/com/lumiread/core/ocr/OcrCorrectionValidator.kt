package com.lumiread.core.ocr

import com.lumiread.core.safety.ProtectedTokenDetector

/**
 * OCR 修正校验器(OCR_TECH_DOC §11.1,轨道 A)——"Validator 防止乱改"。
 *
 * 对 Gemma 的修正结果做确定性把关,**任一规则不过 → 整体退回 OCR 原文**(保守优先,
 * 儿童产品里可靠 > 聪明):
 *  1. 长度膨胀:修正文不得比原文长超过 [MAX_GROWTH_RATIO](反编造新内容)。
 *  2. 长度坍缩:修正文不得比原文短超过 [MAX_SHRINK_RATIO](反吞句)。
 *  3. 保护 token:原文中的数字/专有名词/引号内容必须原样保留在修正文里。
 *  4. 修改比例:词级 diff 改动比例 > [MAX_CHANGE_RATIO] → 模型大概率在重写,退回。
 *  5. changes 一致性:模型声明的每条 change.from 必须真实存在于原文(反幻觉 diff)。
 */
object OcrCorrectionValidator {

    data class OcrChange(val from: String, val to: String, val reason: String? = null)

    data class ValidationResult(
        val accepted: Boolean,
        /** accepted=false 时的退回原因(日志/指标用)。 */
        val reason: String?,
        /** 最终安全文本:accepted ? correctedText : rawText。下游永远用这个。 */
        val safeText: String,
    )

    fun validate(
        rawText: String,
        correctedText: String,
        declaredChanges: List<OcrChange> = emptyList(),
    ): ValidationResult {
        val raw = rawText.trim()
        val corrected = correctedText.trim()

        if (corrected.isEmpty()) return reject(raw, "empty_correction")
        if (corrected == raw) return ValidationResult(true, null, raw)

        // 1./2. 长度护栏。
        if (corrected.length > raw.length * MAX_GROWTH_RATIO) return reject(raw, "length_growth")
        if (corrected.length < raw.length * MAX_SHRINK_RATIO) return reject(raw, "length_shrink")

        // 3. 保护 token 必须原样保留。
        for (token in ProtectedTokenDetector.detect(raw)) {
            if (!corrected.contains(token)) return reject(raw, "protected_token_lost:$token")
        }

        // 4. 词级修改比例。
        val changeRatio = wordChangeRatio(raw, corrected)
        if (changeRatio > MAX_CHANGE_RATIO) {
            return reject(raw, "change_ratio=%.2f".format(changeRatio))
        }

        // 5. 声明的 changes 必须真实(from 在原文中存在)——反"幻觉 diff"。
        for (c in declaredChanges) {
            if (c.from.isNotBlank() && !raw.contains(c.from)) {
                return reject(raw, "hallucinated_change_from:${c.from.take(20)}")
            }
        }

        return ValidationResult(true, null, corrected)
    }

    private fun reject(rawText: String, reason: String) =
        ValidationResult(false, reason, rawText)

    /** 词级(中文按字)简易 diff 比例:1 - LCS/max。 */
    private fun wordChangeRatio(a: String, b: String): Float {
        val ta = tokenize(a)
        val tb = tokenize(b)
        if (ta.isEmpty() || tb.isEmpty()) return 1f
        val lcs = lcsLength(ta, tb)
        return 1f - lcs.toFloat() / maxOf(ta.size, tb.size)
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch.code in 0x4E00..0x9FFF -> {        // CJK 按字
                    if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
                    tokens += ch.toString()
                }
                ch.isWhitespace() -> {
                    if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
                }
                else -> sb.append(ch.lowercaseChar())
            }
        }
        if (sb.isNotEmpty()) tokens += sb.toString()
        return tokens
    }

    private fun lcsLength(a: List<String>, b: List<String>): Int {
        // 滚动数组 LCS;绘本页文本短(≤ 数百 token),O(n*m) 足够。
        var prev = IntArray(b.size + 1)
        var cur = IntArray(b.size + 1)
        for (i in a.indices) {
            for (j in b.indices) {
                cur[j + 1] = if (a[i] == b[j]) prev[j] + 1 else maxOf(prev[j + 1], cur[j])
            }
            val t = prev; prev = cur; cur = t
            cur.fill(0)
        }
        return prev[b.size]
    }

    private const val MAX_GROWTH_RATIO = 1.3f
    private const val MAX_SHRINK_RATIO = 0.6f
    private const val MAX_CHANGE_RATIO = 0.35f
}
