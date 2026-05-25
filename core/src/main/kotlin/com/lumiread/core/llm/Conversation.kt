package com.lumiread.core.llm

import com.lumiread.core.ImageInput
import kotlinx.coroutines.flow.Flow

/**
 * 单轮对话句柄。
 *
 * 重构(2026-05-24):LiteRT-LM 0.12 native 层"一个 Engine 一个活跃 session"的硬约束
 * 让"长期复用同一个 Conversation 跑多轮"不可行。`com.lumiread.core.pipeline.ChatSession` 已改为
 * **每轮开一个新 Conversation,收完即 close**,把历史拼进 prompt 文本。所以这个接口本质上
 * 退化为"一次性"句柄;[close] 必须在每轮 finally 里调用。
 *
 * 实现:
 * - Gemma4Conversation(:app 模块,Gemma4Engine 内部)—— 包一层 LiteRT-LM `Conversation`
 * - FakeConversation(:core 模块,FakeLlmEngine 内部)—— 跨轮变体回复
 *
 * 不允许跨线程并发调用 [sendUserMessage]。
 */
interface Conversation : AutoCloseable {
    /**
     * 纯文本一轮。流式返回助手的增量回复(逐 token 文本)。
     */
    fun sendUserMessage(text: String): Flow<String>

    /**
     * 多模态一轮:文本 + 图片(用于 OcrMode.MULTIMODAL)。
     *
     * 默认实现回退到纯文本 —— [FakeLlmEngine] 与早期不支持多模态的 LlmEngine 版本可以直接继承,
     * 不会破坏现有流程。真正的多模态由 [com.lumiread.core.llm.Gemma4Engine] 的 Gemma4Conversation 实现。
     */
    fun sendUserMessage(text: String, images: List<ImageInput>): Flow<String> =
        sendUserMessage(text)
}
