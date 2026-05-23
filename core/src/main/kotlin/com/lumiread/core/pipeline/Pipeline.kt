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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow

/**
 * 流水线事件。Reading 屏要把每一段都展示给用户(多页 OCR / 合并标签 / LLM 流式)。
 * OCR/Labels 必须独立可见,以便 UI 透明展示识别结果。
 */
sealed interface PipelineEvent {
    /** 单页 OCR 完成。[index] 0-based,[of] 总页数。 */
    data class OcrPage(val index: Int, val of: Int, val result: OcrResult) : PipelineEvent
    /** 跨页合并、去重后的 Top-K 标签。 */
    data class Labels(val list: List<Label>) : PipelineEvent
    /** LLM 流式分片。 */
    data class LlmChunk(val text: String) : PipelineEvent
    data object Done                       : PipelineEvent
    data class Failed(val error: Throwable) : PipelineEvent
}

/**
 * 编排:Capture(N 张)→ OCR(每页)→ Label(跨页合并)→ Compose Prompt → LLM(流式) → TTS。
 *
 * 多张支持:同一次伴读会话允许拍多张(比如绘本的左右两页 / 翻页阅读),
 * Pipeline 把所有页面的 OCR 文本拼成一段塞给 LLM,标签做 union + 去重 + Top-K。
 *
 * 核心解耦点 —— Pipeline 不依赖任何 Android UI 类,可在 JVM 单测里跑。
 */
class Pipeline(
    private val ocr: OcrService,
    private val labels: ImageLabelService,
    private val llm: LlmEngine,
    private val tts: TtsEngine,
) {
    /**
     * 跑一次完整循环。事件顺序:多个 OcrPage → Labels → 多个 LlmChunk → Done。
     * 任一阶段抛错就发 [PipelineEvent.Failed] 并终止。
     */
    fun run(
        images: List<ImageInput>,
        outputLang: Lang,
        ageBand: AgeBand,
    ): Flow<PipelineEvent> = flow {
        require(images.isNotEmpty()) { "Pipeline.run 至少需要一张图片" }
        runCatching {
            val ocrPerPage = mutableListOf<OcrResult>()
            images.forEachIndexed { i, img ->
                val result = ocr.recognize(img)
                ocrPerPage += result
                emit(PipelineEvent.OcrPage(index = i, of = images.size, result = result))
            }

            val merged = mergeLabels(images.map { labels.label(it, topK = 5) }, topK = 5)
            emit(PipelineEvent.Labels(merged))

            val combinedOcr = mergeOcr(ocrPerPage)
            val prompt = SocraticPromptBuilder.build(combinedOcr, merged, outputLang, ageBand)
            llm.generate(prompt).collect { chunk -> emit(PipelineEvent.LlmChunk(chunk)) }

            emit(PipelineEvent.Done)
        }.onFailure { e ->
            emit(PipelineEvent.Failed(e))
        }
    }

    /** 单图便捷重载。 */
    fun run(image: ImageInput, outputLang: Lang, ageBand: AgeBand): Flow<PipelineEvent> =
        run(listOf(image), outputLang, ageBand)

    /**
     * 与 [run] 类似但同时驱动 TTS:**累积全部 LLM 文本,Done 时一次性合成播放**。
     *
     * 设计取舍:曾尝试"按句切分边出边播"以降低首声延迟,但实测发现:
     *  1. VITS 每次 `generateWithCallback` 在内部独立计算韵律,句间衔接出现回零重起,听感破碎
     *  2. CPU provider 上 EN 段单次合成本身 ~28 s,流式切句节省的几秒钟相对 TTS 总时长可忽略
     *  3. 短句喂 VITS 上下文不足,韵律抖动更明显
     * 因此回到最朴素方案:LLM 全跑完 → 整段合成 → 播放。UI 文字仍按 `LlmChunk` 实时显示,
     * 不受 TTS 影响;`Done` 在 TTS 播放完成后发出。
     */
    fun runAndSpeak(
        images: List<ImageInput>,
        outputLang: Lang,
        ageBand: AgeBand,
    ): Flow<PipelineEvent> = channelFlow {
        val buf = StringBuilder()
        run(images, outputLang, ageBand).collect { ev ->
            when (ev) {
                is PipelineEvent.LlmChunk -> {
                    send(ev)
                    buf.append(ev.text)
                }
                is PipelineEvent.Done -> {
                    val fullText = buf.toString().trim()
                    if (fullText.isNotEmpty()) {
                        runCatching { tts.speak(fullText, outputLang, ageBand) }
                    }
                    send(ev)
                }
                else -> send(ev)
            }
        }
    }

    /**
     * 启动一段多轮伴读对话。
     *
     * 不在这里 createConversation —— ChatSession 改为每轮一条新的 Conversation
     * (LiteRT-LM 0.12 native session 锁约束,详见 ChatSession 文档)。这里只缓存 systemPrompt,
     * ChatSession 拿到 [llm] 后自行管理 per-turn Conversation 生命周期。
     */
    fun startChat(outputLang: Lang, ageBand: AgeBand, ocrMode: OcrMode): ChatSession {
        val sys = SocraticPromptBuilder.systemPrompt(outputLang, ageBand)
        return ChatSession(
            ocr = ocr,
            labels = labels,
            llm = llm,
            systemPrompt = sys,
            tts = tts,
            lang = outputLang,
            ageBand = ageBand,
            ocrMode = ocrMode,
        )
    }
}
