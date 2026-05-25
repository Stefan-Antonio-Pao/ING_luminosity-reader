package com.lumiread.core.llm

import kotlinx.coroutines.flow.Flow

/**
 * LLM 推理引擎抽象。
 *
 * 真实实现 [com.lumiread.core.llm.Gemma4Engine] 居于 :app 模块,依赖 LiteRT-LM
 * 。本接口故意保持 Android-free,以便在 JVM 单测里用 [FakeLlmEngine]
 * 跑通整条管线。
 */
interface LlmEngine {
    /**
     * 预热 —— 加载权重、建会话。耗时较长(LiteRT-LM 初始化可达 ~10 s),
     * 必须放在非主线程,且只调用一次。
     */
    suspend fun warmUp()

    /**
     * 单轮流式生成(无对话历史,每次新会话)。
     * 用于 [com.lumiread.core.pipeline.Pipeline.run] 这种一次性场景。
     * 多轮聊天请走 [startConversation]。
     */
    fun generate(prompt: String): Flow<String>

    /**
     * 启动一段多轮对话。[systemPrompt] 注入到底层 `ConversationConfig.systemInstruction`,
     * 只在会话开始时投递一次,后续 `sendUserMessage` 仅追加用户内容。
     * 调用方负责 `close` 返回的 [Conversation],否则底层 KV cache 不会释放。
     */
    suspend fun startConversation(systemPrompt: String): Conversation

    /** 释放底层资源(LiteRT-LM Engine.close)。 */
    suspend fun close()
}
