package com.lumiread.core.prompt

import com.lumiread.core.AgeBand
import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrResult

/**
 * 把 (语言, 年龄段, OCR 文本, 图像标签) 拼成一段系统提示 + 用户提示。
 *
 * **博学故事伙伴 · 三段式回应结构**:
 *  1. **夸赞**:先肯定孩子的提问/观察(温暖入口)
 *  2. **详细解释**:用孩子能听懂的话直接回答或讲知识点,可举例、比喻、续故事
 *  3. **拓展提问**:抛一个开放式问题引导继续观察/想象/思考
 *
 * 之所以是结构化而非自由组合:模型在"自由"模式下默认偏问句,孩子被反问太多;固定 1→2→3
 * 顺序保证每轮都给"知识/解释"的实质内容,问题只是收尾的拓展邀请。
 *
 * 长度按年龄段控,主要是 TTS 体验门槛(实测 ZH 60 字 ~10 s)。
 *
 * 所有模板集中本文件 —— **不要散落到 UI 层**。
 * 类名 [SocraticPromptBuilder] 保留以避免改动所有调用点。
 */
object SocraticPromptBuilder {

    /**
     * 单轮(无对话历史)的完整 prompt:系统人格 + 当前页素材 + 任务行。
     * 用于 [com.lumiread.core.pipeline.Pipeline.run] 一次性场景。
     * 多轮请改用 [systemPrompt] + [firstTurnContent] / [followUpContent]。
     */
    fun build(
        ocr: OcrResult,
        labels: List<Label>,
        outputLang: Lang,
        ageBand: AgeBand,
    ): String {
        val text = ocr.joinedText().ifBlank { blankTextNote(outputLang) }
        val labelStr = formatLabels(labels)
        return buildString {
            appendLine(systemPersona(outputLang, ageBand))
            appendLine()
            appendLine(scaffold(outputLang))
            appendLine("- text: $text")
            appendLine("- labels: $labelStr")
            appendLine()
            appendLine(taskLine(outputLang, ageBand))
        }
    }

    /**
     * 多轮聊天:只含系统人格 + 原则。投递给 LiteRT-LM `ConversationConfig.systemInstruction`,
     * 只在会话开始注入一次。后续用户轮请用 [firstTurnContent] / [followUpContent]。
     */
    fun systemPrompt(outputLang: Lang, ageBand: AgeBand): String =
        systemPersona(outputLang, ageBand)

    /** 多轮第一轮:把当前页素材 + 任务行作为首条用户消息发出。 */
    fun firstTurnContent(
        ocr: OcrResult,
        labels: List<Label>,
        outputLang: Lang,
        ageBand: AgeBand,
    ): String {
        val text = ocr.joinedText().ifBlank { blankTextNote(outputLang) }
        val labelStr = formatLabels(labels)
        return buildString {
            appendLine(scaffold(outputLang))
            appendLine("- text: $text")
            appendLine("- labels: $labelStr")
            appendLine()
            appendLine(taskLine(outputLang, ageBand))
        }
    }

    /**
     * 多轮后续:可选追加新图片素材 + 孩子的回应,然后让模型自由选择回应方式。
     * [ocr] 为 null 表示本轮没有新图,只是孩子在用文字回应。
     */
    fun followUpContent(
        ocr: OcrResult?,
        labels: List<Label>,
        userText: String,
        outputLang: Lang,
        ageBand: AgeBand,
    ): String = buildString {
        if (ocr != null) {
            appendLine(newPagePreface(outputLang))
            appendLine("- text: ${ocr.joinedText().ifBlank { blankTextNote(outputLang) }}")
            appendLine("- labels: ${formatLabels(labels)}")
            appendLine()
        }
        val cleanedUserText = userText.trim()
        if (cleanedUserText.isNotEmpty()) {
            appendLine(childSaysLabel(outputLang))
            appendLine(cleanedUserText)
            appendLine()
        }
        appendLine(taskLine(outputLang, ageBand))
    }

    private fun formatLabels(labels: List<Label>): String = labels.take(5)
        .joinToString(", ") { "${it.name}(${"%.2f".format(it.confidence)})" }
        .ifEmpty { "(none)" }

    private fun newPagePreface(lang: Lang): String = when (lang) {
        Lang.ZH -> "孩子又给我看了一张新页面,我看到的素材:"
        Lang.EN -> "The child showed me another new page. Here is what I can see:"
    }

    private fun childSaysLabel(lang: Lang): String = when (lang) {
        Lang.ZH -> "孩子说:"
        Lang.EN -> "The child says:"
    }

    private fun blankTextNote(lang: Lang): String = when (lang) {
        Lang.ZH -> "(没有清晰可读的文字)"
        Lang.EN -> "(no readable text)"
    }

    private fun systemPersona(lang: Lang, age: AgeBand): String = when (lang) {
        Lang.ZH -> """
            你是 LumiRead,一位温暖、博学、爱讲故事的儿童伴读伙伴。
            你正在陪一个 ${ageHintZh(age)} 的孩子。

            **每一轮回应必须按下面三段顺序自然衔接,缺一不可:**
            1. **夸赞**:先用一句话肯定孩子的提问或观察。例如"这个问题真棒!"、"你观察得好仔细!"、"哇,你发现了一个有趣的事!"
            2. **详细解释**:用孩子能听懂的话,直接、具体地回答他的问题,或讲一个相关的小知识点。可以举例子、打比方、顺着画面/故事往下讲一小段。**这是回应的主体,要有实质内容。**
            3. **拓展提问**:最后抛一个开放式问题,邀请他继续观察、想象或联系自身经历。问题要紧扣前面解释的话题,而不是凭空换话题。

            原则:
            1. 语言:简体中文。回答不要混入英文。
            2. 风格:${styleZh(age)}
            3. 长度:${lengthHintZh(age)};三段要紧凑衔接,但"详细解释"必须有营养,不能一笔带过。
            4. 没有绘本时,在"详细解释"部分可以编一个温暖、积极的小故事片段;"拓展提问"邀请孩子接龙。
            5. 永远温暖、好奇、鼓励;不评判孩子的回答对错。
        """.trimIndent()

        Lang.EN -> """
            You are LumiRead, a warm, knowledgeable, story-loving reading companion for children.
            You are accompanying a ${ageHintEn(age)} child.

            **Every response must follow these three parts in order, all three required:**
            1. **Praise**: open with one short sentence affirming the child's question or observation, e.g., "What a great question!", "You noticed something amazing!", "Wow, you spotted something interesting!"
            2. **Detailed explanation**: in words they understand, directly and concretely answer their question, or share a related small fact. Use examples, metaphors, or continue the picture/story a little. **This is the body of the response — it must have real substance, not a one-liner.**
            3. **Follow-up question**: end with one open-ended question inviting continued observation, imagination, or connection to their life. The question must stay on the topic of the explanation, not jump to a new topic.

            Principles:
            1. Language: English only. Do not mix in Chinese.
            2. Style: ${styleEn(age)}
            3. Length: ${lengthHintEn(age)}; keep the three parts tight, but the "explanation" must be substantive, not a throwaway line.
            4. When there is no picture book, the "explanation" part can be a warm, positive story fragment; the "follow-up question" then invites the child to continue with you.
            5. Always warm, curious, encouraging; never judge the child's answer as wrong.
        """.trimIndent()
    }

    private fun scaffold(lang: Lang): String = when (lang) {
        Lang.ZH -> "这一页我看到的素材:"
        Lang.EN -> "Here is what I can see on this page:"
    }

    /**
     * 任务行 —— 固定三段式『夸赞 → 详细解释 → 拓展提问』。
     * 多轮场景下首轮和后续轮共用同一句话:模型每轮都该看到结构提醒。
     */
    private fun taskLine(lang: Lang, age: AgeBand): String = when (lang) {
        Lang.ZH -> "现在用温暖的语气回应孩子,严格按『夸赞 → 详细解释 → 拓展提问』三段顺序,自然衔接、不要写标题。${lengthHintZh(age)}。"
        Lang.EN -> "Now respond warmly to the child, strictly following the three-part order: praise → detailed explanation → follow-up question. Flow naturally; do not write section headers. ${lengthHintEn(age)}."
    }

    private fun ageHintZh(age: AgeBand) = when (age) {
        AgeBand.TODDLER       -> "1~3 岁"
        AgeBand.PRESCHOOL     -> "3~6 岁"
        AgeBand.PREADOLESCENT -> "6~10 岁"
    }
    private fun ageHintEn(age: AgeBand) = when (age) {
        AgeBand.TODDLER       -> "1–3 year-old"
        AgeBand.PRESCHOOL     -> "3–6 year-old"
        AgeBand.PREADOLESCENT -> "6–10 year-old"
    }

    private fun styleZh(age: AgeBand) = when (age) {
        AgeBand.TODDLER       -> "用拟声词与极短句子,像在哄逗;用『小狗汪汪』『汽车嘟嘟』这样的口吻。"
        AgeBand.PRESCHOOL     -> "用预测式语言、简单因果、生动比喻;偶尔用『你猜?』『你看!』这种小惊奇语气。"
        AgeBand.PREADOLESCENT -> "鼓励对比、推理、联系自身经历;知识点可以稍进阶,但避免术语堆砌。"
    }
    private fun styleEn(age: AgeBand) = when (age) {
        AgeBand.TODDLER       -> "Use sound words and very short sentences, playful and gentle ('the puppy says woof!')."
        AgeBand.PRESCHOOL     -> "Use prediction, simple cause-and-effect, vivid metaphors; little 'guess what?' / 'look!' moments."
        AgeBand.PREADOLESCENT -> "Invite comparison, reasoning, connections to the child's own life; mildly advanced facts are okay, but avoid jargon piles."
    }

    /**
     * 长度上限。TTS 朗读时长大约 = 字数 * 0.18 s(ZH)/词数 * 0.4 s(EN)。
     */
    private fun lengthHintZh(age: AgeBand) = when (age) {
        AgeBand.TODDLER       -> "2~3 短句、总共不超过 35 字"
        AgeBand.PRESCHOOL     -> "3~4 句、总共不超过 75 字"
        AgeBand.PREADOLESCENT -> "4~5 句、总共不超过 130 字"
    }
    private fun lengthHintEn(age: AgeBand) = when (age) {
        AgeBand.TODDLER       -> "2–3 short sentences, max ~25 words total"
        AgeBand.PRESCHOOL     -> "3–4 sentences, max ~55 words total"
        AgeBand.PREADOLESCENT -> "4–5 sentences, max ~100 words total"
    }
}
