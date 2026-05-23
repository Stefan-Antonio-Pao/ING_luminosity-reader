package com.lumiread.core.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 假 LLM:按词切分一段固定 reply,模拟流式输出。
 *
 * **运行时水印**:每段输出前会带 [FAKE_PREFIX] 标识,
 * 防止录演示视频或给评委看时把桩件输出误当成真实功能。
 *
 * 多轮:[startConversation] 返回的 [FakeConversation] 会按轮数切不同回复。
 */
class FakeLlmEngine(
    /** 控制每个分片之间的人为延迟,默认 30ms。 */
    private val chunkDelayMs: Long = 30L,
    /** 注入一段固定回复;不传则按 prompt 自动产生短句。 */
    private val cannedReply: String? = null,
) : LlmEngine {

    override suspend fun warmUp() {
        // no-op
    }

    override fun generate(prompt: String): Flow<String> = flow {
        val reply = cannedReply ?: defaultReply(prompt)
        emit("$FAKE_PREFIX ")
        reply.split(' ').forEach { token ->
            emit("$token ")
            delay(chunkDelayMs)
        }
    }

    override suspend fun startConversation(systemPrompt: String): Conversation =
        FakeConversation(chunkDelayMs, cannedReply)

    override suspend fun close() {
        // no-op
    }

    private fun defaultReply(prompt: String): String =
        "What do you see on this page? Can you point to it?"

    companion object {
        const val FAKE_PREFIX = "[FAKE LLM]"
    }
}

/**
 * 假对话:按轮数切不同回复,模拟多轮上下文感知。第 1 轮抛初始观察问题,
 * 后续轮"装作"在跟进。仍带 [FakeLlmEngine.FAKE_PREFIX] 水印。
 */
private class FakeConversation(
    private val chunkDelayMs: Long,
    private val cannedReply: String?,
) : Conversation {

    private var turn = 0

    override fun sendUserMessage(text: String): Flow<String> = flow {
        turn += 1
        val reply = cannedReply ?: defaultReply(turn)
        emit("${FakeLlmEngine.FAKE_PREFIX} ")
        reply.split(' ').forEach { token ->
            emit("$token ")
            delay(chunkDelayMs)
        }
    }

    override fun close() {
        // no-op
    }

    private fun defaultReply(turn: Int): String = when {
        turn == 1 -> "What do you see on this page? Can you point to it?"
        turn == 2 -> "Interesting! What do you think happens next?"
        else -> "Why do you think so? Have you seen this before?"
    }
}
