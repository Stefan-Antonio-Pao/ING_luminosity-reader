package com.lumiread.core.agent

import com.lumiread.core.AgeBand
import com.lumiread.core.ImageInput
import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrMode
import com.lumiread.core.OcrResult
import com.lumiread.core.OutputMode
import kotlinx.coroutines.flow.Flow

/**
 * v2.0.0 原生函数调用重构(任务书 §2 / RESEARCH_FC.md §7)的核心解耦接口。
 *
 * **设计动机 / 与任务书草图的诚实差异**:
 * 任务书 §2 给的草图是 `suspend fun generate(input: PageContext): SocraticResult`(一次性返回)。
 * 但本项目的 UI 真实契约是**流式 + 多轮**:`ChatSession.firstTurn/userTurn` 逐 token 推 `ChatEvent`、
 * 边出边写气泡、收完整段喂 TTS。强行套一次性签名会摧毁流式/多轮/TTS 体验 = 改 UI,违反任务书 §11。
 * 因此本接口落在**"每轮生成"这一可替换接缝**上:`ChatSession` 仍负责会话编排(OCR 处理、历史、
 * TTS、事件、多轮崩溃修复),只把"如何把本轮上下文变成一串助手文本"委托给一个 [SocraticEngine]。
 *
 * 这样:
 *  - [TwoStagePipelineEngine]:现有 OCR+文本→LLM 流式(降级脊梁),行为与 v1.x 完全一致。
 *  - `FunctionCallingEngine`(Stage 1 Step 3-4 接入):同一接口,内部跑手动工具调用 agent 循环。
 *  - `AgentOrchestrator`(Step 5 接入):同一接口,按模型/复杂度选 FC,任意失败回退 TwoStage。
 * 三者可互换而**不动 ChatSession 编排、不动 UI**。
 */
interface SocraticEngine {
    /**
     * 流式生成**一轮**助手回复。
     *
     * 实现自行决定回复怎么产出(纯文本 LLM / 工具调用循环),但必须:
     *  - 逐片 emit [TurnEvent.Chunk](UI 实时显示),
     *  - 结束时 emit 恰好一个 [TurnEvent.Done](带 served-by 路径与用到的工具,供日志/README 演示)。
     *  - 在 Flow 结束(正常或异常)前释放自己开的底层 `Conversation`(沿用 `.use` 模式,
     *    保证 native session 在调用方做 TTS 之前已关闭 —— 见 ChatSession 的多轮崩溃修复说明)。
     */
    fun generateTurn(req: TurnRequest): Flow<TurnEvent>
}

/**
 * 一轮生成所需的全部上下文。字段为两条路径的并集:
 *  - [composedPrompt] 供 [TwoStagePipelineEngine] 直接喂 LLM(已含历史前缀,等价旧 `buildPromptWithHistory`)。
 *  - 结构化字段([ocr]/[labels]/[ageBand]/[userText] 等)供 `FunctionCallingEngine` 自行决策与拼装
 *    (它要把 OCR/标签喂给 `classify_scene` 之类工具,而非只看一段拼好的文本)。
 * Step 1 只有 TwoStage 在用,结构化字段先携带、暂不消费;Step 3 FC 落地时直接取用,接口不再变。
 */
data class TurnRequest(
    /** 注入底层 `ConversationConfig.systemInstruction` 的系统提示(三档 persona + 语言/格式块)。 */
    val systemPrompt: String,
    /** 本轮最终喂给纯文本 LLM 的完整提示(历史前缀 + 本轮业务文本)。TwoStage 直接用。 */
    val composedPrompt: String,
    /** 本轮"业务文本"(SocraticPromptBuilder 输出或用户裸输入),不含历史。FC 可据此 + 结构化字段重拼。 */
    val userText: String,
    /** 本轮 OCR 结果;无图/多模态轮为 null。 */
    val ocr: OcrResult?,
    /** 本轮合并去重后的 Top-K 图像标签;无图/多模态轮为空。 */
    val labels: List<Label>,
    /** 仅 [OcrMode.MULTIMODAL] 下非空,走 `Conversation.sendUserMessage(text, images)` 多模态重载。 */
    val images: List<ImageInput>,
    val lang: Lang,
    val ageBand: AgeBand,
    val outputMode: OutputMode,
    val ocrMode: OcrMode,
    /** 首轮(刚拍照起会话)= true;后续 userTurn = false。FC 用它决定是否优先跑 classify_scene。 */
    val isFirstTurn: Boolean,
)

/** [SocraticEngine.generateTurn] 的流事件。 */
sealed interface TurnEvent {
    /** 助手回复的增量分片(可能多次)。 */
    data class Chunk(val text: String) : TurnEvent

    /**
     * 本轮生成结束(底层 Conversation 已 close)。
     * @param servedBy  实际服务本轮的引擎路径(演示 agentic 闭环 + 喂 README/DEV_LOG)。
     * @param usedTools 本轮真实触发并执行的原生函数名(snake_case),TwoStage 恒为空。
     */
    data class Done(
        val servedBy: EngineKind,
        val usedTools: List<String> = emptyList(),
    ) : TurnEvent
}

/** 服务一轮的引擎种类。用于 DEV_LOG / README 展示"哪条路径服务了这一轮"。 */
enum class EngineKind { TWO_STAGE, FUNCTION_CALLING }

/**
 * v2.0.0 Stage 3:一轮生成的可观测指标(任务书 §8 step 10)。
 * 用于演示 agentic 闭环 + 喂 README/DEV_LOG。由 ChatSession 计时后经回调上报给 :app 记日志。
 *
 * @param servedBy     实际服务本轮的引擎路径。
 * @param usedTools    本轮真实触发的原生函数(snake_case);TwoStage 恒空。
 * @param firstChunkMs 从本轮开始到首个助手分片的耗时(ms)——"首字延迟"代理(FC 手动模式 = 首段)。
 * @param totalGenMs   从本轮开始到生成结束(底层会话已 close、TTS 之前)的耗时(ms)。
 */
data class TurnMetrics(
    val servedBy: EngineKind,
    val usedTools: List<String>,
    val firstChunkMs: Long,
    val totalGenMs: Long,
)
