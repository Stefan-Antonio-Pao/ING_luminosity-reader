package com.lumiread.core.ocr

import com.lumiread.core.Lang
import com.lumiread.core.llm.LlmEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Gemma OCR 保守修正阶段(OCR_TECH_DOC §9/§15.1,轨道 A)。
 *
 * **结构化生成,不是工具调用**(任务书 §1.1:OCR 修正不做工具链):App 用固定 prompt 要求模型
 * 输出 `{corrected_text, changes, uncertain_parts}` JSON,防御式解析后交 [OcrCorrectionValidator]
 * 确定性把关。任一环节失败 → 原文原样返回(`degraded=true`),**绝不崩、绝不编**。
 *
 * 失败矩阵(全部降级保留原文):
 *  - LLM 抛错/超时(调用方控制超时)→ degraded("llm_error")
 *  - 输出不是 JSON / JSON 缺 corrected_text → degraded("bad_json")
 *  - Validator 拒绝(改数字/改名/重写/膨胀)→ correctedText=原文,rejectedReason 记录
 *
 * JSON 解析依赖 FACTS#F13(kotlinx-serialization-json 1.11.0,运行时树解析)。
 */
class OcrCorrectionStage(private val llm: LlmEngine) {

    data class CorrectedPageText(
        /** 下游(伴读 prompt / TTS)唯一应消费的安全文本。 */
        val text: String,
        /** Gemma 标记的不确定片段(家长透明度/不自动朗读判断用)。 */
        val uncertainParts: List<String>,
        /** 修正是否真实生效(false = 原文透传,可能因降级或 Validator 拒绝)。 */
        val corrected: Boolean,
        /** 降级/拒绝原因,null = 正常。 */
        val degradedReason: String?,
    )

    /**
     * 跑一轮保守修正。[rawText] 来自 LayoutNormalizer 的 plainText;[lang] 用于提示语言提示
     * (修正本身保持原文语言,不翻译)。
     *
     * 调用方(ChatSession)负责放在质量门控的 CorrectWithGemma 分支、并自行决定超时。
     */
    suspend fun correct(rawText: String, lang: Lang): CorrectedPageText {
        val raw = rawText.trim()
        if (raw.isEmpty()) return passthrough(raw, "empty_input")

        val output = StringBuilder()
        try {
            llm.generate(buildPrompt(raw, lang)).collect { output.append(it) }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            return passthrough(raw, "llm_error:${t.javaClass.simpleName}")
        }

        val parsed = parseCorrectionJson(output.toString())
            ?: return passthrough(raw, "bad_json")

        val validation = OcrCorrectionValidator.validate(
            rawText = raw,
            correctedText = parsed.correctedText,
            declaredChanges = parsed.changes,
        )
        return CorrectedPageText(
            text = validation.safeText,
            uncertainParts = parsed.uncertainParts,
            corrected = validation.accepted,
            degradedReason = validation.reason,
        )
    }

    private fun passthrough(raw: String, reason: String) =
        CorrectedPageText(text = raw, uncertainParts = emptyList(), corrected = false, degradedReason = reason)

    private data class ParsedCorrection(
        val correctedText: String,
        val changes: List<OcrCorrectionValidator.OcrChange>,
        val uncertainParts: List<String>,
    )

    /**
     * 防御式解析(任务书 §2.1 已知 bad_json 风险):
     *  - 模型常在 JSON 外裹 markdown 代码栅栏或前后缀文字 → 先抽取首个 '{'..最后 '}' 的子串;
     *  - 任何解析异常 → null(调用方降级);
     *  - changes/uncertain_parts 缺失或形状不对 → 按空处理,不影响 corrected_text 主路径。
     */
    private fun parseCorrectionJson(output: String): ParsedCorrection? {
        val start = output.indexOf('{')
        val end = output.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val jsonText = output.substring(start, end + 1)

        val root = try {
            lenientJson.parseToJsonElement(jsonText).jsonObject
        } catch (_: Exception) {
            return null
        }

        val correctedText = try {
            root["corrected_text"]?.jsonPrimitive?.content
        } catch (_: Exception) { null } ?: return null

        val changes = try {
            root["changes"]?.jsonArray?.mapNotNull { el ->
                val obj = el.jsonObject
                val from = obj["from"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val to = obj["to"]?.jsonPrimitive?.content ?: return@mapNotNull null
                OcrCorrectionValidator.OcrChange(from, to, obj["reason"]?.jsonPrimitive?.content)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val uncertain = try {
            root["uncertain_parts"]?.jsonArray?.mapNotNull { el ->
                try { el.jsonPrimitive.content.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        return ParsedCorrection(correctedText, changes, uncertain)
    }

    /** OCR_TECH_DOC §15.1 模板(固定英文指令——结构化输出更稳;原文语言由内容自带)。 */
    private fun buildPrompt(rawText: String, lang: Lang): String = buildString {
        appendLine("You are an OCR post-correction engine for children's picture books.")
        appendLine()
        appendLine("Correct only obvious OCR errors (like l/1, O/0, rn/m confusions, broken words, obvious typos).")
        appendLine("Do not rewrite, summarize, translate, or add new content.")
        appendLine("Keep the original meaning, language and sentence order.")
        appendLine("Protect numbers, page numbers, names, dates, titles, units, and quoted text — never change them.")
        appendLine("If unsure about a part, keep the original and add it to uncertain_parts.")
        appendLine()
        appendLine("Return JSON only, exactly this shape:")
        appendLine("""{"corrected_text": "...", "changes": [{"from": "...", "to": "...", "reason": "..."}], "uncertain_parts": ["..."]}""")
        appendLine()
        val langHint = when (lang) {
            Lang.ZH -> "The text is mainly Chinese."
            Lang.EN -> "The text is mainly English."
        }
        appendLine(langHint)
        appendLine()
        appendLine("OCR text:")
        append(rawText)
    }

    private companion object {
        val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }
    }
}
