package com.lumiread.core.pipeline

import com.lumiread.core.AgeBand
import com.lumiread.core.ImageInput
import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrMode
import com.lumiread.core.OcrResult
import com.lumiread.core.OutputMode
import com.lumiread.core.agent.EngineKind
import com.lumiread.core.agent.SocraticEngine
import com.lumiread.core.agent.TurnEvent
import com.lumiread.core.agent.TurnMetrics
import com.lumiread.core.agent.TurnRequest
import com.lumiread.core.prompt.SocraticPromptBuilder
import com.lumiread.core.tts.TtsEngine
import com.lumiread.core.vision.ImageLabelService
import com.lumiread.core.vision.OcrService
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * 多轮聊天会话编排(CLAUDE.md §4 解耦原则)。
 *
 * Phase 6 重构(2026-05-24):**每轮一条全新 Conversation + Prompt 内嵌历史**。
 *
 * 背景:LiteRT-LM 0.12 原生层一个 `Engine` 只允许一个活跃 session,复用同一个 `Conversation`
 * 连续 `sendMessageAsync` 第二次会抛 `only one session is supported at a time`(AAR 反编译
 * 与 stripped `.so` 字符串均证实)。即使 0.12 暴露了 `Engine.createConversation` 的多实例
 * 形态,实际 native session 句柄并未在第一轮收完后立刻释放。
 *
 * 修复:`ChatSession` 不再持有长期 Conversation,改成:
 *  - 持有 [llm] + [systemPrompt] + [history]
 *  - 每轮 [firstTurn]/[userTurn] **开新** Conversation → 把 history 拼进用户消息文本 → 收完即 close
 *  - close 在 finally 内,保证 cancellation/异常也释放 native session
 *
 * 代价:LiteRT-LM 内部 KV cache 不能跨轮复用 → 每轮要全量重新算前缀的 attention。对 5–10 轮
 * 短对话(每轮 ≤ 200 token)在 2048 token 上下文里完全够用;`MAX_HISTORY=8` 超出时滚动丢最旧两轮。
 *
 * OCR/MULTIMODAL 分支由 [ocrMode] 决定:
 *  - [OcrMode.OCR]:跑 ML Kit,把 OCR 文本 + 标签拼进 prompt
 *  - [OcrMode.MULTIMODAL]:跳过 ML Kit,把图直接喂给 LlmEngine 的多模态重载
 *
 * 串行约束:同一 ChatSession 上**不允许并发**调 [firstTurn] / [userTurn](LiteRT-LM 单 Engine
 * 同一时刻仍只能跑一条 session)。上层 UI 通过 mutex + 禁用发送按钮自然串行。
 *
 * 生命周期:用 [Pipeline.startChat] 取实例,用完 [close] 清 history 即可(不再有底层 Conversation 要关)。
 */
class ChatSession internal constructor(
    private val ocr: OcrService,
    private val labels: ImageLabelService,
    /**
     * v2.0.0(2026-05-31):每轮生成委托给可替换的 [SocraticEngine](原为直接持 `LlmEngine`)。
     * ChatSession 只管会话编排(OCR/历史/TTS/事件/崩溃修复),"如何把上下文变成助手文本"交给引擎。
     * Step 1 注入 [com.lumiread.core.agent.TwoStagePipelineEngine](行为同 v1.x);Step 5 起换 `AgentOrchestrator`。
     */
    private val engine: SocraticEngine,
    private val systemPrompt: String,
    private val tts: TtsEngine,
    private val lang: Lang,
    private val ageBand: AgeBand,
    private val ocrMode: OcrMode,
    /**
     * v1.1 步骤三(2026-05-25):是否在每轮 LLM 结束后自动朗读。
     *  - true:沿用 Phase 4 起的行为,[streamAssistant] 等 TTS 播完再发 [ChatEvent.AssistantDone]
     *  - false:跳过 TTS,LLM 一收完立即发 AssistantDone;UI 侧由"手动播放按钮"调 [TtsEngine.speak]
     *
     * 这是会话级配置(由 [Pipeline.startChat] 在创建时定);用户中途改设置只影响下一次新会话。
     * 与三个语言概念(界面/输出/输出模式)完全正交。
     */
    private val autoPlayTts: Boolean,
    /**
     * v1.1 步骤四(2026-05-25):输出模式(单语 / 中英双语)。会话级冻结。
     *  - [OutputMode.MONOLINGUAL]:Gemma 仅用 [lang] 输出
     *  - [OutputMode.BILINGUAL]:Gemma 主语种=[lang],副语种=另一个,分行成对呈现
     *
     * 影响 [SocraticPromptBuilder.systemPrompt] / [firstTurnContent] / [followUpContent]
     * 的 prompt 生成。TTS 不需要分支:vits-melo-tts-zh_en 原生支持中英混读,直接喂整段即可。
     */
    private val outputMode: OutputMode,
    /**
     * v2.0.0 Stage 3:每轮生成结束(生成完、TTS 前)上报一次可观测指标(served-by / 用到的工具 /
     * 首字延迟 / 生成总耗时)。core 保持 Android-free,具体落地(记日志)由 :app 注入。默认 no-op。
     */
    private val onMetrics: (TurnMetrics) -> Unit = {},
) : AutoCloseable {

    private data class HistoryEntry(val user: String, val assistant: String)

    private val history: MutableList<HistoryEntry> = mutableListOf()

    /**
     * 首轮:用户刚拍了首批图片。
     *  - OCR 模式:跑 ML Kit → emit TurnContext → 文本 prompt
     *  - MULTIMODAL 模式:emit TurnContext(空) → 图直接喂 LlmEngine
     *
     * @throws IllegalArgumentException 如果 images 为空
     */
    fun firstTurn(images: List<ImageInput>): Flow<ChatEvent> = channelFlow {
        require(images.isNotEmpty()) { "firstTurn 至少需要一张图片" }
        runCatching {
            when (ocrMode) {
                OcrMode.OCR -> {
                    val (ocrResults, mergedLabels) = processImages(images)
                    send(ChatEvent.TurnContext(ocrResults, mergedLabels))
                    val combinedOcr = mergeOcr(ocrResults)
                    val userText = SocraticPromptBuilder.firstTurnContent(
                        ocr = combinedOcr,
                        labels = mergedLabels,
                        outputLang = lang,
                        ageBand = ageBand,
                        outputMode = outputMode,
                    )
                    streamAssistant(
                        userText = userText,
                        images = emptyList(),
                        ocr = combinedOcr,
                        labels = mergedLabels,
                        isFirstTurn = true,
                    )
                }
                OcrMode.MULTIMODAL -> {
                    // 跳过 ML Kit。UI 仍发 TurnContext(空) 以便清空"思考中"占位。
                    send(ChatEvent.TurnContext(emptyList(), emptyList()))
                    val userText = SocraticPromptBuilder.firstTurnContent(
                        ocr = OcrResult(emptyList(), null),
                        labels = emptyList(),
                        outputLang = lang,
                        ageBand = ageBand,
                        outputMode = outputMode,
                    )
                    streamAssistant(
                        userText = userText,
                        images = images,
                        ocr = null,
                        labels = emptyList(),
                        isFirstTurn = true,
                    )
                }
            }
        }.onFailure { send(ChatEvent.Failed(it)) }
    }

    /**
     * 后续:用户的下一轮输入。[text] 和 [images] 至少一个非空。
     */
    fun userTurn(text: String, images: List<ImageInput>): Flow<ChatEvent> = channelFlow {
        require(text.isNotBlank() || images.isNotEmpty()) {
            "userTurn 至少需要文本或图片"
        }
        runCatching {
            when (ocrMode) {
                OcrMode.OCR -> {
                    val combinedOcr: OcrResult?
                    val mergedLabels: List<Label>
                    if (images.isNotEmpty()) {
                        val (ocrResults, lbs) = processImages(images)
                        send(ChatEvent.TurnContext(ocrResults, lbs))
                        combinedOcr = mergeOcr(ocrResults)
                        mergedLabels = lbs
                    } else {
                        combinedOcr = null
                        mergedLabels = emptyList()
                    }
                    val userText = SocraticPromptBuilder.followUpContent(
                        ocr = combinedOcr,
                        labels = mergedLabels,
                        userText = text,
                        outputLang = lang,
                        ageBand = ageBand,
                        outputMode = outputMode,
                    )
                    streamAssistant(
                        userText = userText,
                        images = emptyList(),
                        ocr = combinedOcr,
                        labels = mergedLabels,
                        isFirstTurn = false,
                    )
                }
                OcrMode.MULTIMODAL -> {
                    if (images.isNotEmpty()) {
                        send(ChatEvent.TurnContext(emptyList(), emptyList()))
                    }
                    val userText = SocraticPromptBuilder.followUpContent(
                        ocr = null,
                        labels = emptyList(),
                        userText = text,
                        outputLang = lang,
                        ageBand = ageBand,
                        outputMode = outputMode,
                    )
                    streamAssistant(
                        userText = userText,
                        images = images,
                        ocr = null,
                        labels = emptyList(),
                        isFirstTurn = false,
                    )
                }
            }
        }.onFailure { send(ChatEvent.Failed(it)) }
    }

    /**
     * 每轮的核心:开新 Conversation → 拼历史 → 跑流式 → close。
     *
     * 历史会被压成可读文本拼到 [userText] 前面;LLM 看到的内容形如:
     * ```
     * 【对话历史】
     * 用户: ...
     * 助手: ...
     * (more)
     *
     * 【本轮】
     * <userText>
     * ```
     *
     * [images] 仅在 MULTIMODAL 模式下非空,会通过 [com.lumiread.core.llm.Conversation.sendUserMessage]
     * 的多模态重载塞给底层。
     */
    private suspend fun ProducerScope<ChatEvent>.streamAssistant(
        userText: String,
        images: List<ImageInput>,
        ocr: OcrResult?,
        labels: List<Label>,
        isFirstTurn: Boolean,
    ) {
        val prompt = buildPromptWithHistory(userText)
        val buf = StringBuilder()

        // v2.0.0 Step 1:把"开 Conversation → 流式收文本"这段委托给注入的 [engine]。
        // 多轮崩溃修复(2026-05-24)随之搬进引擎:[TwoStagePipelineEngine] 仍用 .use 让 conv 在
        // 本轮生成 Flow 结束(即下面 TTS 之前)就 close,立刻释放 native session 句柄。旧实现
        // 把 tts.speak() 与 conv.close() 同放一处导致 TTS 播放期间(10–30 s)native session 一直挂着,
        // 下一轮 createConversation 撞句柄 —— PKJ110 tombstones #11/#12/#13 SIGSEGV in
        // liblitertlm_jni.so 共享 frame 0x6634c4。此处仍**先收完 engine 流(conv 已 close)再 TTS**,顺序不变。
        val req = TurnRequest(
            systemPrompt = systemPrompt,
            composedPrompt = prompt,
            userText = userText,
            ocr = ocr,
            labels = labels,
            images = images,
            lang = lang,
            ageBand = ageBand,
            outputMode = outputMode,
            ocrMode = ocrMode,
            isFirstTurn = isFirstTurn,
        )
        var servedBy = EngineKind.TWO_STAGE
        var usedTools: List<String> = emptyList()
        // Stage 3:计时(served-by / 首字延迟 / 生成总耗时)。
        val t0 = System.currentTimeMillis()
        var firstChunkMs = -1L
        engine.generateTurn(req).collect { ev ->
            when (ev) {
                is TurnEvent.Chunk -> {
                    if (firstChunkMs < 0) firstChunkMs = System.currentTimeMillis() - t0
                    buf.append(ev.text)
                    send(ChatEvent.AssistantChunk(ev.text))
                }
                is TurnEvent.Done -> {
                    servedBy = ev.servedBy
                    usedTools = ev.usedTools
                }
            }
        }
        // engine 流已结束,底层 conv 已 close,native session 已释放。后续 TTS 播 10+ 秒也不再占住 LiteRT-LM。
        onMetrics(
            TurnMetrics(
                servedBy = servedBy,
                usedTools = usedTools,
                firstChunkMs = firstChunkMs.coerceAtLeast(0),
                totalGenMs = System.currentTimeMillis() - t0,
            )
        )

        val full = buf.toString().trim()
        if (full.isNotEmpty() && autoPlayTts) {
            try {
                tts.speak(full, lang, ageBand)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce  // CLAUDE.md §C2:cancellation 必须透传,不能被吞
            } catch (_: Throwable) {
                // TTS 失败不影响对话流程,继续 emit AssistantDone 让 UI 解锁。
            }
        }
        // autoPlayTts=false 时直接跳过 TTS,LLM 一收完立即解锁 UI(用户可点手动播放按钮)。
        // 记入历史。userText 取本轮"业务文本"(用户裸输入或首轮 SocraticPromptBuilder 输出),
        // 不把整段历史拼好的 prompt 再压回去 —— 否则下一轮 history 会指数爆炸。
        history += HistoryEntry(user = userText, assistant = full)
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
        send(ChatEvent.AssistantDone(full))
    }

    private fun buildPromptWithHistory(currentUser: String): String {
        if (history.isEmpty()) return currentUser
        return buildString {
            appendLine(historyHeader(lang))
            history.forEach { (u, a) ->
                appendLine("${userTag(lang)}: $u")
                appendLine("${assistantTag(lang)}: $a")
            }
            appendLine()
            appendLine(currentTurnHeader(lang))
            append(currentUser)
        }
    }

    private suspend fun processImages(images: List<ImageInput>): Pair<List<OcrResult>, List<Label>> {
        // C6:每图独立 runCatching,一张图损坏不连坐整轮。
        val ocrResults = images.mapNotNull { img -> runCatching { ocr.recognize(img) }.getOrNull() }
        val perPageLabels = images.map { img ->
            runCatching { labels.label(img, topK = 5) }.getOrDefault(emptyList())
        }
        return ocrResults to mergeLabels(perPageLabels, topK = 5)
    }

    override fun close() {
        history.clear()
    }

    companion object {
        /** 多轮历史最多保留几轮(超出滚动丢最旧)。2048 ctx-window 下 8 轮约占一半,留余地。 */
        private const val MAX_HISTORY = 8

        private fun historyHeader(lang: Lang): String = when (lang) {
            Lang.ZH -> "【对话历史】"
            Lang.EN -> "[Conversation history]"
        }
        private fun currentTurnHeader(lang: Lang): String = when (lang) {
            Lang.ZH -> "【本轮】"
            Lang.EN -> "[Current turn]"
        }
        private fun userTag(lang: Lang): String = when (lang) {
            Lang.ZH -> "用户"; Lang.EN -> "User"
        }
        private fun assistantTag(lang: Lang): String = when (lang) {
            Lang.ZH -> "助手"; Lang.EN -> "Assistant"
        }
    }
}

/**
 * 聊天事件流。UI 据此渲染用户气泡(TurnContext) / 助手气泡(AssistantChunk / AssistantDone) /
 * 错误条(Failed)。
 */
sealed interface ChatEvent {
    /**
     * 本轮用户消息附带的 OCR/标签结果。UI 可在用户气泡里展示"识别到的文字"作为透明度。
     * 仅当本轮带了图片时发出;MULTIMODAL 模式发出空列表(用于清"思考中"占位)。
     */
    data class TurnContext(val ocr: List<OcrResult>, val labels: List<Label>) : ChatEvent

    /** 助手回复的增量分片(可能多次)。 */
    data class AssistantChunk(val text: String) : ChatEvent

    /**
     * 助手回复结束。[fullText] 是本轮的完整回复(已 trim);TTS 已播完。
     * UI 收到这个事件后应允许用户发下一轮。
     */
    data class AssistantDone(val fullText: String) : ChatEvent

    /** 任一阶段失败。可继续用于下一轮(本轮 Conversation 已在 finally 内 close)。 */
    data class Failed(val error: Throwable) : ChatEvent
}
