package com.lumiread.core.tts

import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import kotlinx.coroutines.delay

/**
 * 假 TTS:把"播报"打到回调里,不发声。
 *
 * **运行时水印**:每次"播报"前打 [FAKE_PREFIX]。
 * 真实路径出现 Fake 走 = 立即失败,不许静默降级。
 */
class FakeTtsEngine(
    private val sink: (String) -> Unit = { println(it) },
) : TtsEngine {

    @Volatile private var stopped = false

    override suspend fun speak(text: String, lang: Lang, ageBand: AgeBand) {
        stopped = false
        sink("$FAKE_PREFIX lang=$lang ageBand=$ageBand text=\"$text\"")
        // 模拟按年龄段不同语速的"播报时长"
        val durationMs = when (ageBand) {
            AgeBand.TODDLER       -> text.length * 90L
            AgeBand.PRESCHOOL     -> text.length * 70L
            AgeBand.PREADOLESCENT -> text.length * 55L
        }
        // 拆成几段,以便 stop 能中断
        val step = 100L
        var elapsed = 0L
        while (elapsed < durationMs && !stopped) {
            delay(step.coerceAtMost(durationMs - elapsed))
            elapsed += step
        }
    }

    override suspend fun stop() {
        stopped = true
    }

    companion object {
        const val FAKE_PREFIX = "[FAKE TTS]"
    }
}
