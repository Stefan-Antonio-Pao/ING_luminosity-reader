package com.lumiread.core.agent

import com.lumiread.core.llm.LlmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 降级脊梁(任务书 §2 / §5):现有"OCR+标签→纯文本提示→LLM 流式"管线,实现 [SocraticEngine]。
 *
 * v2.0.0 Step 1:把 `ChatSession.streamAssistant` 里"开 Conversation → 流式收文本"这段**可替换块**
 * 原样搬到这里;`ChatSession` 只把已拼好的 [TurnRequest.composedPrompt] / [TurnRequest.images] 递进来。
 * **行为与 v1.x 完全一致**——不引入任何新逻辑,纯结构抽取。后续 `FunctionCallingEngine` 走另一条
 * 实现,失败时 `AgentOrchestrator` 回退到本类。
 *
 * 多轮崩溃修复(2026-05-24,见 memory/project_litertlm_native_crash)在此**保持不变**:
 * 用 `.use` 让 `Conversation` 在 Flow 结束(即调用方做 TTS)**之前**就 close,立刻释放 native session 句柄。
 * `Conversation` 由注入的 [llm](真实为 :app 的 `Gemma4Engine`,内部已加 convMutex + 200ms quiescence)创建。
 */
class TwoStagePipelineEngine(
    private val llm: LlmEngine,
) : SocraticEngine {

    override fun generateTurn(req: TurnRequest): Flow<TurnEvent> = flow {
        // 关键:.use 保证 conv 在本 flow 完成/抛错前 close —— 与旧 ChatSession.streamAssistant 同序,
        // native session 在上层 TTS(10–30 s)开始前已释放,避免下一轮 createConversation 撞句柄。
        llm.startConversation(req.systemPrompt).use { conv ->
            val stream = if (req.images.isEmpty()) {
                conv.sendUserMessage(req.composedPrompt)
            } else {
                conv.sendUserMessage(req.composedPrompt, req.images)
            }
            stream.collect { chunk -> emit(TurnEvent.Chunk(chunk)) }
        }
        emit(TurnEvent.Done(servedBy = EngineKind.TWO_STAGE))
    }
}
