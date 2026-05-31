package com.lumiread.llm

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.tool
import com.lumiread.core.agent.EngineKind
import com.lumiread.core.agent.SocraticEngine
import com.lumiread.core.agent.TurnEvent
import com.lumiread.core.agent.TurnRequest
import com.lumiread.core.data.OfflineDictionary
import com.lumiread.core.prompt.SocraticPromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * v2.0.0 Step 4(评分核心):Gemma 4 **原生函数调用** 引擎,实现 [SocraticEngine](任务书 §3)。
 *
 * **真·原生 FC**:用 LiteRT-LM 手动工具模式(`automaticToolCalling = false`,FACTS#F2.6)——
 * 同步 `sendMessage` 取 `Message.toolCalls`(由 Gemma 4 原生工具 token 触发,**非字符串解析**),
 * 我们 dispatch [LumiReadToolSet] 真实执行 Kotlin,再用 `Content.ToolResponse` 回灌,模型据此产出最终回答。
 *
 * **手动模式不逐 token 流**(已核实库行为):工具调用 / 文本整条 Message 一次性返回。因此 FC 路径的
 * 最终回答按行整体 emit(不是逐字打字效果);TTS 仍由 ChatSession 收完整段后朗读,不受影响。
 *
 * **可靠性硬规则(RESEARCH_FC §reliability / 任务书 §3)——任一触发即抛异常,交 AgentOrchestrator 降级**:
 *  - 轮次上限 [maxRounds](2–3,严于库默认 5);超限仍要调工具 → [NotConvergedException]。
 *  - 校验:工具名未知 → [InvalidToolCallException];最终文本空 → [EmptyAnswerException]。
 *  - 静默丢调用:原始文本含 `<|tool_call|>` 形状但 `toolCalls` 为空 → [SilentToolDropException]。
 *  - 超时看门狗:每个同步回合用 async 竞速 + [withTimeoutOrNull]([perRoundTimeoutMs])——
 *    阻塞的 native `sendMessage` 无法被普通 withTimeout 取消,故放到 async 里 await(可挂起点),
 *    超时则放手(abandon)并抛 [ToolTurnTimeoutException]。
 *
 * **多模态/图片轮不在 FC 范围**:`req.images` 非空(MULTIMODAL)时直接抛 [UnsupportedTurnException] →
 * 降级到 TwoStage(它原生支持多模态)。FC 专注 OCR/文本轮。
 *
 * 失败语义:**任何**问题都以异常冒出,由 [com.lumiread.core.agent.AgentOrchestrator] 缓冲式回退到
 * TwoStage —— 本引擎绝不自行降级、也绝不让 App 崩。
 */
class FunctionCallingEngine(
    private val provider: Gemma4Engine,
    private val dict: OfflineDictionary,
    private val onReadAloud: (String) -> Unit,
    private val maxRounds: Int = DEFAULT_MAX_ROUNDS,
    private val perRoundTimeoutMs: Long = DEFAULT_PER_ROUND_TIMEOUT_MS,
) : SocraticEngine {

    override fun generateTurn(req: TurnRequest): Flow<TurnEvent> = flow {
        if (req.images.isNotEmpty()) {
            // 多模态轮交给 TwoStage(FC 手动模式 + 图片 + 工具未验证,稳妥起见降级)。
            throw UnsupportedTurnException("multimodal turn not handled by FC")
        }

        val usedTools = mutableListOf<String>()
        val toolSet = LumiReadToolSet(
            ageBand = req.ageBand,
            lang = req.lang,
            dict = dict,
            onReadAloud = onReadAloud,
            onToolUsed = { name -> usedTools += name },
        )
        // 系统提示 = 共用三档 persona(§6)+ 仅 FC 路径的工具使用说明(§5)。
        val systemPrompt = req.systemPrompt + "\n" + SocraticPromptBuilder.toolUsageBlock(req.lang)

        provider.createToolConversation(systemPrompt, listOf(tool(toolSet))).use { conv ->
            var msg: Message = sendWatched { conv.send(req.composedPrompt) }
            var round = 0
            while (msg.toolCalls.isNotEmpty() && round < maxRounds) {
                val responses: List<Content> = msg.toolCalls.map { call ->
                    if (!toolSet.isKnownTool(call.name)) {
                        throw InvalidToolCallException("unknown tool '${call.name}'")
                    }
                    val result = toolSet.dispatch(call.name, call.arguments)
                    Log.i(TAG, "工具执行:${call.name} args=${call.arguments} -> $result")
                    Content.ToolResponse(call.name, result)
                }
                val toolMsg = Message.tool(Contents.of(*responses.toTypedArray()))
                msg = sendWatched { conv.send(toolMsg) }
                round++
            }

            val finalText = textOf(msg)
            // 静默丢调用检测(RESEARCH_FC §4):看着像工具调用却没解析出来 → 判失败降级。
            if (msg.toolCalls.isEmpty() && finalText.contains(TOOL_CALL_MARKER)) {
                throw SilentToolDropException()
            }
            // 仍想调工具但已到轮次上限 → 未收敛,降级。
            if (msg.toolCalls.isNotEmpty()) throw NotConvergedException(maxRounds)
            if (finalText.isBlank()) throw EmptyAnswerException()

            Log.i(TAG, "FC 完成,rounds=$round,usedTools=${usedTools.distinct()},len=${finalText.length}")
            // 手动模式整段返回:按行 emit(不逐字)。ChatSession 收完整段再 TTS。
            finalText.lineSequence().forEach { line ->
                emit(TurnEvent.Chunk(if (line.isEmpty()) "\n" else line + "\n"))
            }
        }
        emit(TurnEvent.Done(servedBy = EngineKind.FUNCTION_CALLING, usedTools = usedTools.distinct()))
    }.flowOn(Dispatchers.IO)

    /**
     * 同步 send 的超时看门狗:阻塞的 native `sendMessage` 无法被普通 `withTimeout` 取消
     * (它不是挂起函数、没有取消检查点)。放进 `async` 后 `await()` 是挂起点,可被超时取消;
     * 超时则 cancel(放手让 native 线程自己结束)并抛 [ToolTurnTimeoutException] → 上层降级。
     */
    private suspend fun sendWatched(block: () -> Message): Message = coroutineScope {
        val deferred = async(Dispatchers.IO) { block() }
        withTimeoutOrNull(perRoundTimeoutMs) { deferred.await() }
            ?: run {
                deferred.cancel()
                throw ToolTurnTimeoutException(perRoundTimeoutMs)
            }
    }

    private fun textOf(msg: Message): String = msg.contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString("") { it.text }
        .trim()

    companion object {
        private const val TAG = "FunctionCallingEngine"
        /** 轮次上限:严于库默认 RECURRING_TOOL_CALL_LIMIT=5,控延迟与电量(任务书 §3)。 */
        private const val DEFAULT_MAX_ROUNDS = 3
        /**
         * 单回合挂起上限(**hang 天花板,非延迟目标**)。
         *
         * 2026-05-31 真机调参:E4B 在 CPU 后端单轮生成 ~15-25s(RESEARCH_FC 实测 GPU p95 已 26.4s,
         * CPU 更慢),20s 会把"慢但正常"误判挂起 → 频繁错误降级 + 双倍延迟(实测 72s)。放宽到 60s:
         * 给慢生成 2-3 倍余量,只抓真正永不返回的 hang(#2202)。GPU 设备会远快于此,不受影响。
         */
        private const val DEFAULT_PER_ROUND_TIMEOUT_MS = 60_000L
        /** Gemma 4 原生工具调用 token 形状,用于静默丢调用检测。 */
        private const val TOOL_CALL_MARKER = "<|tool_call|>"
    }
}

/** FC 失败族:任一冒出即触发 AgentOrchestrator 降级到 TwoStage。 */
sealed class FunctionCallingException(message: String) : Exception(message)

/** 多模态/图片轮不由 FC 处理(降级)。 */
class UnsupportedTurnException(message: String) : FunctionCallingException(message)

/** 模型请求了未注册的工具。 */
class InvalidToolCallException(message: String) : FunctionCallingException(message)

/** 到达轮次上限仍要求调用工具,未收敛。 */
class NotConvergedException(rounds: Int) : FunctionCallingException("not converged within $rounds rounds")

/** 静默丢调用:文本含 <|tool_call|> 但未解析出 toolCalls。 */
class SilentToolDropException : FunctionCallingException("silent tool-call drop detected")

/** 最终回答为空。 */
class EmptyAnswerException : FunctionCallingException("empty final answer")

/** 某回合超过看门狗上限,判挂起。 */
class ToolTurnTimeoutException(timeoutMs: Long) : FunctionCallingException("tool turn exceeded ${timeoutMs}ms")
