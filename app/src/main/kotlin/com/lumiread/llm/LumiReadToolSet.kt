package com.lumiread.llm

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.core.data.OfflineDictionary
import com.lumiread.core.tools.SceneClassifier
import com.lumiread.core.tools.WordExplainer

/**
 * v2.0.0 Step 3:LumiRead 的原生函数工具集(任务书 §3、§4)。
 *
 * **真·原生函数调用**:实现 LiteRT-LM 的 `ToolSet`,方法用 `@Tool`/`@ToolParam` 标注,库反射生成
 * OpenAPI schema 声明给 Gemma 4,由模型的原生工具 token 触发 —— **不是字符串解析假装**(§0.5 / §11)。
 * 签名严格依据 `docs/FACTS.md#F2.6`(已对 0.12.0 真实 JAR + :spike 探针核实)。
 *
 * **薄工具(Google AI Edge Gallery `Function_Calling_Guide` 范式)**:方法体只做最小事——查词典/分类/
 * 触发朗读回调,真正逻辑在 :core 的纯函数([SceneClassifier]/[WordExplainer]),便于 JVM 单测且 UI 零耦合。
 *
 * **年龄自适应(双层表达,§6)**:本工具集按当前 [ageBand]/[lang] 构造(每轮由 FunctionCallingEngine 新建),
 * `lookup_word` 据此产出不同深度的释义(不仅靠提示词)。
 *
 * **均 ≤2 参数**(铁律,RESEARCH_FC §2:参数越多通过率越差,4 参≈0%)。
 *
 * **手动模式**:`automaticToolCalling = false` 下库不自动执行;Step 4 的 FunctionCallingEngine 按
 * `ToolCall.name` 调 [dispatch] 执行,再把结果经 `Content.ToolResponse` 回灌。本类的 `@Tool` 方法
 * 仍是 schema 来源(注册用),且非 suspend(反射安全)。
 *
 * @param onReadAloud `read_aloud` 触发的朗读回调(薄:不在工具里阻塞做 TTS,交回调异步处理,避免卡住 agent 循环)
 * @param onToolUsed  每次工具被执行时回调工具名(snake_case),供 FunctionCallingEngine 汇总 usedTools
 */
class LumiReadToolSet(
    private val ageBand: AgeBand,
    private val lang: Lang,
    private val dict: OfflineDictionary,
    private val onReadAloud: (String) -> Unit,
    private val onToolUsed: (String) -> Unit,
) : ToolSet {

    @Tool(
        description = "Decide whether the photo shows a storybook page or a real-world object. " +
            "Call this first when a new photo is shown so you know whether to tell a story or explain the object.",
    )
    fun classifyScene(
        @ToolParam(description = "Comma-separated image labels detected in the photo (may be empty)")
        imageLabels: String,
        @ToolParam(description = "Text recognized in the photo (may be empty)")
        ocrText: String,
    ): Map<String, String> {
        onToolUsed(TOOL_CLASSIFY_SCENE)
        return mapOf("scene" to SceneClassifier.classify(imageLabels, ocrText))
    }

    @Tool(
        description = "Look up a child-friendly definition of a single word from the on-device dictionary. " +
            "Use it when the child asks what a word means or when a word is likely unfamiliar.",
    )
    fun lookupWord(
        @ToolParam(description = "The single word or short term to define")
        term: String,
    ): Map<String, String> {
        onToolUsed(TOOL_LOOKUP_WORD)
        val cleaned = term.trim()
        val entry = dict.lookup(cleaned, lang)
        return WordExplainer.explain(entry, cleaned, ageBand, lang)
    }

    @Tool(
        description = "Read a short piece of text aloud to the child with the on-device voice. " +
            "Use sparingly, e.g. to sound out a word or a short phrase.",
    )
    fun readAloud(
        @ToolParam(description = "The text to speak aloud")
        text: String,
    ): Map<String, String> {
        onToolUsed(TOOL_READ_ALOUD)
        onReadAloud(text)
        return mapOf("status" to "queued")
    }
    // 注:v2.0.0 曾加第 4 个工具 save_learning_record,真机实测(Step 7a)4 工具让 E2B/E4B 工具调用
    // 基本全回退(RESEARCH_FC §2:工具越多模型选择越难)。已移除该工具,恢复 3 工具以保 FC 稳定。
    // 单词记录的 Room 基础设施(WordRecord/StudyStore.saveWordRecord)停放备用,见 DEV_LOG Step 7a 回退。

    /**
     * 手动模式分发(Step 4 FunctionCallingEngine 调用):据 `ToolCall.name` 找到对应工具执行。
     *
     * 名称匹配用 [canon](去下划线/连字符 + 小写),对 `convertCamelToSnakeCaseInToolDescription`
     * 开/关都健壮:`classify_scene` / `classifyScene` 都能命中。
     * 参数值从 `ToolCall.arguments`(`Map<String, Any?>`,值可空,F2.6)按 camel/snake 双名取。
     */
    fun dispatch(name: String, args: Map<String, Any?>): Map<String, *> = when (canon(name)) {
        canon(TOOL_CLASSIFY_SCENE) -> classifyScene(
            imageLabels = arg(args, "imageLabels", "image_labels"),
            ocrText = arg(args, "ocrText", "ocr_text"),
        )
        canon(TOOL_LOOKUP_WORD) -> lookupWord(term = arg(args, "term"))
        canon(TOOL_READ_ALOUD) -> readAloud(text = arg(args, "text"))
        else -> mapOf("error" to "unknown_tool", "name" to name)
    }

    /** 工具是否可被分发(用于"该调却没调/调了不存在的工具"校验)。 */
    fun isKnownTool(name: String): Boolean = canon(name) in KNOWN_CANON

    private fun arg(args: Map<String, Any?>, vararg keys: String): String {
        for (k in keys) args[k]?.let { return it.toString() }
        return ""
    }

    companion object {
        const val TOOL_CLASSIFY_SCENE = "classify_scene"
        const val TOOL_LOOKUP_WORD = "lookup_word"
        const val TOOL_READ_ALOUD = "read_aloud"

        /** 去掉下划线/连字符并小写,统一 camelCase 与 snake_case 两种命名。 */
        private fun canon(name: String): String =
            name.lowercase().filter { it.isLetterOrDigit() }

        private val KNOWN_CANON: Set<String> =
            setOf(TOOL_CLASSIFY_SCENE, TOOL_LOOKUP_WORD, TOOL_READ_ALOUD).map { canon(it) }.toSet()
    }
}
