package com.lumiread.core.pipeline

import com.lumiread.core.AgeBand
import com.lumiread.core.ImageInput
import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrMode
import com.lumiread.core.OcrResult
import com.lumiread.core.llm.LlmEngine
import com.lumiread.core.prompt.SocraticPromptBuilder
import com.lumiread.core.tts.TtsEngine
import com.lumiread.core.vision.ImageLabelService
import com.lumiread.core.vision.OcrService
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * 多轮聊天会话编排。
 *
 * **每轮一条全新 Conversation + Prompt 内嵌历史**。
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
    private val llm: LlmEngine,
    private val systemPrompt: String,
    private val tts: TtsEngine,
    private val lang: Lang,
    private val ageBand: AgeBand,
    private val ocrMode: OcrMode,
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
                    )
                    streamAssistant(userText = userText, images = emptyList())
                }
                OcrMode.MULTIMODAL -> {
                    // 跳过 ML Kit。UI 仍发 TurnContext(空) 以便清空"思考中"占位。
                    send(ChatEvent.TurnContext(emptyList(), emptyList()))
                    val userText = SocraticPromptBuilder.firstTurnContent(
                        ocr = OcrResult(emptyList(), null),
                        labels = emptyList(),
                        outputLang = lang,
                        ageBand = ageBand,
                    )
                    streamAssistant(userText = userText, images = images)
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
                    )
                    streamAssistant(userText = userText, images = emptyList())
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
                    )
                    streamAssistant(userText = userText, images = images)
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
    ) {
        val prompt = buildPromptWithHistory(userText)
        val buf = StringBuilder()

        // 用 .use 让 conv 在 TTS 之前就 close,**立刻**释放 native session 句柄。
        // 否则 TTS 播放期间(10–30 s)native session 一直挂着,下一轮 createConversation
        // 可能撞上未完成清理的句柄。
        llm.startConversation(systemPrompt).use { conv ->
            val flow = if (images.isEmpty()) {
                conv.sendUserMessage(prompt)
            } else {
                conv.sendUserMessage(prompt, images)
            }
            flow.collect { chunk ->
                buf.append(chunk)
                send(ChatEvent.AssistantChunk(chunk))
            }
        }
        // conv 已 close,native session 已释放。后续 TTS 播 10+ 秒也不再占住 LiteRT-LM。

        val full = buf.toString().trim()
        if (full.isNotEmpty()) {
            try {
                tts.speak(full, lang, ageBand)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce  // cancellation 必须透传,不能被吞
            } catch (_: Throwable) {
                // TTS 失败不影响对话流程,继续 emit AssistantDone 让 UI 解锁。
            }
        }
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
        // 每图独立 runCatching,一张图损坏不连坐整轮。
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
