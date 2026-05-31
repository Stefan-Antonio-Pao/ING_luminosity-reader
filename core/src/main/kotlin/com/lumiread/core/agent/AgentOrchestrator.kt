package com.lumiread.core.agent

import com.lumiread.core.GemmaModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * v2.0.0(任务书 §2/§5):模型策略 + 降级脊梁。实现 [SocraticEngine],对上层(ChatSession)透明。
 *
 * **模型策略 + 复杂度门控(Step 5,任务书 §5)**——[shouldTryPrimary]:
 *  - **多模态轮**(`req.images` 非空):跳过 FC(FunctionCallingEngine 不处理图片),直接 TwoStage 多模态直喂。
 *  - **E4B**:工具**常开**(只要是文本/OCR 轮就试 FC)。
 *  - **E2B**:**复杂度门控**——仅当场景复杂([isComplex]:OCR 字母多 / 标签多)才试 FC;简单轮直接 TwoStage
 *    (省工具税与翻车风险,且保流式打字体验)。
 * 当前模型由注入的 [currentModel] 动态读取(用户运行时切 E2B/E4B 即时生效)。
 *
 * **缓冲式降级(关键)**:FC 路径手动模式整段产出,先把 [primary] 的所有 Chunk **缓冲**,只有
 * FC **完整成功**(无异常、有内容)才把缓冲回放给下游;一旦 FC 抛异常(校验不过/静默丢/超时/未收敛
 * /多模态不支持……任意 [FunctionCallingException] 或其它 Throwable),**丢弃缓冲**、改跑 [fallback]
 * (TwoStage)。这样下游(UI)永远只看到一条干净路径的输出,不会出现"FC 半截 + TwoStage 重来"的拼接。
 *
 * **App 绝不崩**:primary 的任何失败都被收住并降级;只有 [CancellationException] 透传(结构化取消)。
 *
 * @param currentModel 动态读取当前模型(E2B/E4B),决定工具是否常开。
 * @param onFallback   降级时回调(原因 Throwable;正常门控跳过 primary 时为 null),供 :app 记日志/埋点。
 */
class AgentOrchestrator(
    private val primary: SocraticEngine,
    private val fallback: SocraticEngine,
    private val currentModel: () -> GemmaModel,
    private val onFallback: (Throwable?) -> Unit = {},
) : SocraticEngine {

    /** 是否对本轮尝试 FC(primary)。见类注释的模型策略 + 复杂度门控。 */
    private fun shouldTryPrimary(req: TurnRequest): Boolean {
        if (req.images.isNotEmpty()) return false           // 多模态轮 → TwoStage 直喂(FC 不处理图片)
        return when (currentModel()) {
            GemmaModel.E4B -> true                           // 工具常开
            GemmaModel.E2B -> isComplex(req)                 // 复杂度门控
        }
    }

    /**
     * 廉价复杂度判定(性能护栏,任务书 §5):场景复杂才让 E2B 付工具税。
     * 信号:OCR 有成段文字(字母数 ≥ [COMPLEX_OCR_MIN_LETTERS])或图像标签较多(≥ [COMPLEX_MIN_LABELS])。
     * "含生词"信号待 Step 7 真实词典接入后可再加。
     */
    private fun isComplex(req: TurnRequest): Boolean {
        val ocrLetters = req.ocr?.joinedText()?.count { it.isLetter() } ?: 0
        return ocrLetters >= COMPLEX_OCR_MIN_LETTERS || req.labels.size >= COMPLEX_MIN_LABELS
    }

    override fun generateTurn(req: TurnRequest): Flow<TurnEvent> = flow {
        if (shouldTryPrimary(req)) {
            val buffered = ArrayList<TurnEvent.Chunk>()
            var done: TurnEvent.Done? = null
            val outcome = runCatching {
                primary.generateTurn(req).collect { ev ->
                    when (ev) {
                        is TurnEvent.Chunk -> buffered += ev
                        is TurnEvent.Done -> done = ev
                    }
                }
            }
            val error = outcome.exceptionOrNull()
            if (error is CancellationException) throw error   // 结构化取消必须透传(CLAUDE.md §C2)

            if (error == null && buffered.isNotEmpty()) {
                buffered.forEach { emit(it) }
                emit(done ?: TurnEvent.Done(EngineKind.FUNCTION_CALLING))
                return@flow
            }
            // FC 失败或空产出 → 丢弃缓冲,降级。
            onFallback(error)
        }
        fallback.generateTurn(req).collect { emit(it) }
    }

    companion object {
        /** E2B 复杂度门控:OCR 字母数达到此值视为"有成段文字"。 */
        private const val COMPLEX_OCR_MIN_LETTERS = 6
        /** E2B 复杂度门控:图像标签数达到此值视为"场景较复杂"。 */
        private const val COMPLEX_MIN_LABELS = 3
    }
}
