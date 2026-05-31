package com.lumiread.core.prompt

import com.lumiread.core.AgeBand
import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrResult
import com.lumiread.core.OutputMode

/**
 * 把 (语言, 年龄段, OCR 文本, 图像标签) 拼成一段系统提示 + 用户提示。
 *
 * **CLAUDE.md §6 2026-05-24 二次修订**:在"博学故事伙伴"基础上锁定三段式回应结构。
 *
 * 一次修订(2026-05-24 早):从"纯苏格拉底导师"放松为"每轮 1~2 件事自由组合",但模型偏向
 * 选"提问"动作,孩子反复被问、体验疲劳。
 *
 * 二次修订(2026-05-24 晚):用户要求每轮固定三段式 ——
 *  1. **夸赞**:先肯定孩子的提问/观察(温暖入口)
 *  2. **详细解释**:用孩子能听懂的话直接回答或讲知识点,可举例、比喻、续故事
 *  3. **拓展提问**:抛一个开放式问题引导继续观察/想象/思考
 *
 * 之所以是结构化而非自由组合:模型在"自由"模式下默认偏问句,孩子被反问太多;固定 1→2→3
 * 顺序保证每轮都给"知识/解释"的实质内容,问题只是收尾的拓展邀请。
 *
 * 长度按年龄段控,主要是 TTS 体验门槛(FACTS#F4 实测 ZH 60 字 ~10 s)。三段式比一次修订更
 * 密一些,所以上限略放宽;但仍要紧凑,避免长篇大论。
 *
 * 所有模板集中本文件,Phase 6 调优入口 —— **不要散落到 UI 层**。
 * 类名 [SocraticPromptBuilder] 保留以避免改动所有调用点。
 */
object SocraticPromptBuilder {

    /**
     * 单轮(无对话历史)的完整 prompt:系统人格 + 当前页素材 + 任务行。
     * 用于 [com.lumiread.core.pipeline.Pipeline.run] 一次性场景。
     * 多轮请改用 [systemPrompt] + [firstTurnContent] / [followUpContent]。
     *
     * [outputMode] 默认 [OutputMode.MONOLINGUAL],保持与 v1.0 调用点的二进制兼容。
     */
    fun build(
        ocr: OcrResult,
        labels: List<Label>,
        outputLang: Lang,
        ageBand: AgeBand,
        outputMode: OutputMode = OutputMode.MONOLINGUAL,
    ): String {
        val text = ocr.joinedText().ifBlank { blankTextNote(outputLang) }
        val labelStr = formatLabels(labels)
        return buildString {
            appendLine(systemPersona(outputLang, ageBand, outputMode))
            appendLine()
            appendLine(scaffold(outputLang))
            appendLine("- text: $text")
            appendLine("- labels: $labelStr")
            appendLine()
            appendLine(taskLine(outputLang, ageBand, outputMode))
        }
    }

    /**
     * 多轮聊天:只含系统人格 + 原则。投递给 LiteRT-LM `ConversationConfig.systemInstruction`,
     * 只在会话开始注入一次。后续用户轮请用 [firstTurnContent] / [followUpContent]。
     */
    fun systemPrompt(
        outputLang: Lang,
        ageBand: AgeBand,
        outputMode: OutputMode = OutputMode.MONOLINGUAL,
    ): String = systemPersona(outputLang, ageBand, outputMode)

    /**
     * v2.0.0 Step 4:**仅函数调用路径**附加的工具使用说明块(任务书 §5)。
     *
     * 追加在系统提示之后(只在 [com.lumiread.core.agent] 的 FunctionCallingEngine 路径用;TwoStage 不加,
     * 它没注册工具)。要点:声明可用工具、何时调用、**仅在有帮助时才调**(避免简单场景的工具税与翻车),
     * 调完工具后仍按三段式给最终回答。工具名用 snake_case(与 LiteRT-LM 暴露给模型的一致)。
     */
    fun toolUsageBlock(lang: Lang): String = when (lang) {
        Lang.ZH -> """

            ——你还可以使用这些本地小工具(仅在确实有帮助时才用,简单场景直接回答即可):
            - classify_scene:当孩子刚给你看一张**新图片**时,先用它判断这是"绘本页(book)"还是"眼前物品(object)"。**若返回 object:不要讲绘本故事**,改成用孩子能懂的话讲解这个物品(它是什么、有什么有趣之处),仍按年龄段与三段式;若返回 book,就正常伴读。
            - lookup_word:当出现孩子可能不懂的词时,用它查一个适龄的小解释。
            - read_aloud:偶尔用它把一个词或短语读出来给孩子听。
            用完工具后,仍然按『夸赞 → 详细解释 → 拓展提问』三段式,用工具结果把回答讲得更准更生动。
        """.trimIndent()

        Lang.EN -> """

            — You also have these small on-device tools (use them only when genuinely helpful; for simple cases just answer directly):
            - classify_scene: when the child shows you a **new picture**, call it first to decide whether it is a storybook page ("book") or a real-world object ("object"). **If it returns object: do NOT tell a storybook story** — instead explain that object in words the child understands (what it is, what's interesting about it), still age-appropriate and in three parts; if it returns book, do normal reading companionship.
            - lookup_word: when a word may be unfamiliar to the child, call it for an age-appropriate little explanation.
            - read_aloud: occasionally use it to sound out a word or short phrase for the child.
            After using a tool, still answer in the three parts (praise → detailed explanation → follow-up question), using the tool result to make your answer more accurate and vivid.
        """.trimIndent()
    }

    /** 多轮第一轮:把当前页素材 + 任务行作为首条用户消息发出。 */
    fun firstTurnContent(
        ocr: OcrResult,
        labels: List<Label>,
        outputLang: Lang,
        ageBand: AgeBand,
        outputMode: OutputMode = OutputMode.MONOLINGUAL,
    ): String {
        val text = ocr.joinedText().ifBlank { blankTextNote(outputLang) }
        val labelStr = formatLabels(labels)
        return buildString {
            appendLine(scaffold(outputLang))
            appendLine("- text: $text")
            appendLine("- labels: $labelStr")
            appendLine()
            appendLine(taskLine(outputLang, ageBand, outputMode))
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
        outputMode: OutputMode = OutputMode.MONOLINGUAL,
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
        appendLine(taskLine(outputLang, ageBand, outputMode))
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

    private fun systemPersona(lang: Lang, age: AgeBand, mode: OutputMode): String = when (lang) {
        Lang.ZH -> """
            你是 LumiRead,一位温暖、博学、爱讲故事的儿童伴读伙伴。
            你正在陪一个 ${ageHintZh(age)} 的孩子。

            **每一轮回应必须按下面三段顺序自然衔接,缺一不可:**
            1. **夸赞**:先用一句话肯定孩子的提问或观察。例如"这个问题真棒!"、"你观察得好仔细!"、"哇,你发现了一个有趣的事!"
            2. **详细解释**:用孩子能听懂的话,直接、具体地回答他的问题,或讲一个相关的小知识点。可以举例子、打比方、顺着画面/故事往下讲一小段。**这是回应的主体,要有实质内容。**
            3. **拓展提问**:最后抛一个开放式问题,邀请他继续观察、想象或联系自身经历。问题要紧扣前面解释的话题,而不是凭空换话题。

            原则:
            1. ${languageRuleZh(mode)}
            2. 风格:${styleZh(age)}
            3. 长度:${lengthHintZh(age)};三段要紧凑衔接,但"详细解释"必须有营养,不能一笔带过。${bilingualLengthNoteZh(mode)}
            4. 看到的若是眼前的物品(不是绘本),就讲解这个物品——它是什么、有什么有趣之处;没有绘本、孩子直接和你聊天时,可在"详细解释"里编一个温暖、积极的小故事片段,"拓展提问"邀请孩子接龙。
            5. 永远温暖、好奇、鼓励;不评判孩子的回答对错。
            6. 我看到的"文字"来自拍照识别,可能有错别字或乱码。请结合图像标签**默默纠正**明显的识别错误、跳过读不通的乱码;但**绝不要编造**原文或画面里没有的内容。文字乱到没把握时,就改聊画面,或温和地请孩子把书放平再拍一张。
        """.trimIndent()

        Lang.EN -> """
            You are LumiRead, a warm, knowledgeable, story-loving reading companion for children.
            You are accompanying a ${ageHintEn(age)} child.

            **Every response must follow these three parts in order, all three required:**
            1. **Praise**: open with one short sentence affirming the child's question or observation, e.g., "What a great question!", "You noticed something amazing!", "Wow, you spotted something interesting!"
            2. **Detailed explanation**: in words they understand, directly and concretely answer their question, or share a related small fact. Use examples, metaphors, or continue the picture/story a little. **This is the body of the response — it must have real substance, not a one-liner.**
            3. **Follow-up question**: end with one open-ended question inviting continued observation, imagination, or connection to their life. The question must stay on the topic of the explanation, not jump to a new topic.

            Principles:
            1. ${languageRuleEn(mode)}
            2. Style: ${styleEn(age)}
            3. Length: ${lengthHintEn(age)}; keep the three parts tight, but the "explanation" must be substantive, not a throwaway line.${bilingualLengthNoteEn(mode)}
            4. If what you see is a real-world object (not a storybook), explain that object — what it is and what's interesting about it. When there is no book and the child is just chatting, the "explanation" can be a warm, positive story fragment, and the "follow-up question" invites the child to continue.
            5. Always warm, curious, encouraging; never judge the child's answer as wrong.
            6. The "text" I see comes from photo recognition and may contain typos or garbled bits. Use the image labels to **silently correct** obvious recognition errors and skip unreadable gibberish; but **never fabricate** content not supported by the text or picture. If the text is too garbled to trust, talk about the picture instead, or gently ask the child to lay the book flat and retake the photo.
        """.trimIndent()
    }

    /**
     * v1.1 步骤四:语言规则。单语保持 v1.0 行为;双语下要求"中英成对、分行清晰、主语言在前"。
     *
     * 任务书 §6 步骤四要求"同一句先中文后英文成对输出";UI 上希望主语种由 [Lang] 决定(更对称、
     * 教学价值更高),所以这里 [Lang.ZH] 时主语种=中文,[Lang.EN] 时主语种=英文。两者都"主在前、副在后"。
     */
    private fun languageRuleZh(mode: OutputMode): String = when (mode) {
        OutputMode.MONOLINGUAL -> "语言:简体中文。回答不要混入英文。"
        OutputMode.BILINGUAL   -> """
            语言:中英双语成对输出。每讲完一句中文,**紧接着**用一个**独立的新行**给出对应的英文翻译,**不要把中英写在同一行**;然后空一行再继续下一句。三段(夸赞 / 详细解释 / 拓展提问)各自都要按此中英成对呈现。例如:
            这个问题真棒!
            What a great question!

            小狗在公园里跑步,因为公园有好多它喜欢的草地味道。
            The puppy is running in the park, because the park has many grassy smells it loves.

            你猜小狗最喜欢公园的哪个角落?
            Can you guess which corner of the park the puppy likes best?
        """.trimIndent()
    }
    private fun languageRuleEn(mode: OutputMode): String = when (mode) {
        OutputMode.MONOLINGUAL -> "Language: English only. Do not mix in Chinese."
        OutputMode.BILINGUAL   -> """
            Language: bilingual English + Chinese, paired. After each English sentence, **immediately** give the matching Chinese translation on a **separate new line** — never put both on the same line. Then leave one blank line before the next sentence. All three parts (praise / explanation / follow-up question) must follow this paired format. Example:
            What a great question!
            这个问题真棒!

            The puppy is running in the park, because the park has many grassy smells it loves.
            小狗在公园里跑步,因为公园有好多它喜欢的草地味道。

            Can you guess which corner of the park the puppy likes best?
            你猜小狗最喜欢公园的哪个角落?
        """.trimIndent()
    }

    /** 双语模式下,长度上限按"每种语言独立"应用,避免两侧都被腰斩。 */
    private fun bilingualLengthNoteZh(mode: OutputMode): String = when (mode) {
        OutputMode.MONOLINGUAL -> ""
        OutputMode.BILINGUAL   -> " 双语模式下,长度上限**只约束中文侧**,英文翻译按需要自然写,不必硬压字数。"
    }
    private fun bilingualLengthNoteEn(mode: OutputMode): String = when (mode) {
        OutputMode.MONOLINGUAL -> ""
        OutputMode.BILINGUAL   -> " In bilingual mode, the length limit **applies only to the English side**; let the Chinese translation flow naturally without trying to match the cap."
    }

    private fun scaffold(lang: Lang): String = when (lang) {
        Lang.ZH -> "这一页我看到的素材:"
        Lang.EN -> "Here is what I can see on this page:"
    }

    /**
     * 任务行 —— 二次修订:固定三段式『夸赞 → 详细解释 → 拓展提问』,不再让模型自由组合。
     * 多轮场景下首轮和后续轮共用同一句话:模型每轮都该看到结构提醒。
     *
     * v1.1 步骤四:双语模式追加"中英成对、分行清晰、主语言在前"提醒,作为系统提示的二次强调
     * (单次注入容易被对话历史稀释,任务行每轮注入更稳)。
     */
    private fun taskLine(lang: Lang, age: AgeBand, mode: OutputMode): String = when (lang) {
        Lang.ZH -> "现在用温暖的语气回应孩子,严格按『夸赞 → 详细解释 → 拓展提问』三段顺序,自然衔接、不要写标题。${lengthHintZh(age)}。${taskLineBilingualHintZh(mode)}"
        Lang.EN -> "Now respond warmly to the child, strictly following the three-part order: praise → detailed explanation → follow-up question. Flow naturally; do not write section headers. ${lengthHintEn(age)}.${taskLineBilingualHintEn(mode)}"
    }

    private fun taskLineBilingualHintZh(mode: OutputMode): String = when (mode) {
        OutputMode.MONOLINGUAL -> ""
        OutputMode.BILINGUAL   -> " 双语模式:每说一句中文,紧接着另起一行写对应英文翻译,三段都按此中英成对呈现。"
    }
    private fun taskLineBilingualHintEn(mode: OutputMode): String = when (mode) {
        OutputMode.MONOLINGUAL -> ""
        OutputMode.BILINGUAL   -> " Bilingual mode: after each English sentence, put its Chinese translation on a new line; all three parts must be presented as English+Chinese pairs."
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
     * 三段式比之前自由组合的版本需要更多字数,这里相应放宽。
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
