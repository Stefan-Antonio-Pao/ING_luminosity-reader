package com.lumiread.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 动效时长 / 缓动,来自 `design-system/tokens.json#motion` 与 `handoff/motion-spec.md`。
 *
 * 规则(tokens.json#motion.rules):
 *  - 一切动效尊重 reduced-motion;关键状态仍由文字/颜色/形状传达。
 *  - 儿童模式幅度 × [Tier.animAmp];时长 × [Tier.animDurScale]。
 *  - 家长模式动效一律 standard easing、≤ 240ms。
 */
object LumiMotion {
    const val FAST = 140
    const val MED = 240
    const val SLOW = 420
    const val CELEBRATE = 720

    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Emphasis: Easing = CubicBezierEasing(0.3f, 0.7f, 0.4f, 1.0f)
    val Spring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
}

/** 当前是否应降低动效。真机/系统"移除动画"开启时为 true。 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * 读取系统"动画时长缩放"判断 reduced-motion。当用户在开发者选项/无障碍里把
 * 动画关到 0 时返回 true。供 [com.lumiread.ui.theme.LumiTheme] 注入 [LocalReducedMotion]。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}

/** 按 reduced-motion 与 tier 计算实际动画时长(ms)。 */
fun motionDuration(base: Int, tier: Tier, reduced: Boolean): Int =
    if (reduced) 0 else (base * tier.animDurScale).toInt()
