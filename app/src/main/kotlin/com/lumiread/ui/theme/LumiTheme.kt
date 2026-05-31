package com.lumiread.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lumiread.core.AgeBand
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.unit.lerp as unitLerp

/**
 * 双模式主题入口(UI 改造任务书 §3.3)。
 *
 * 步骤六(2026-05-25)字体:儿童模式统一使用 **ZCOOL KuaiLe / 站酷快乐体**
 * (SIL OFL 1.1,圆胖 Comic 风 Heavy weight,单字体覆盖 Latin + Simplified Chinese)。
 *
 * **2026-05-25 简化修订**:原本通过 `Typeface.CustomFallbackBuilder` 把 Luckiest Guy(Latin)
 * 与 ZCOOL KuaiLe(CJK)串成 fallback 链。用户要求统一字体 → 删除 Luckiest Guy 路径,
 * 全部走 ZCOOL KuaiLe(它自身含 Latin 字形,虽然 Latin 风格不及 Luckiest Guy 浓烈,
 * 但整体视觉一致性更高,且少一个字体下载/许可)。
 *
 * **字体全覆盖修复**:在儿童模式下用 token 字体重建整套 Typography 后传给
 * `MaterialTheme(typography = ...)`,所有 `style = MaterialTheme.typography.X` 的 Text 自动卡通。
 *
 *  - `playfulness = 0` ⇔ [LumiMode.Parent]
 *  - `playfulness = 1` ⇔ [LumiMode.Child]
 *  - 切换 mode 时 `animateFloatAsState` 用 450ms `FastOutSlowInEasing` 在 0↔1 之间平滑过渡
 *  - 数值字段(Dp/TextUnit/Float/Color)用 `lerp` 同时插值
 *  - 字体不可 lerp,在中点(playfulness > 0.5)硬翻一次
 */
@Composable
fun LumiTheme(mode: LumiMode, ageBand: AgeBand, content: @Composable () -> Unit) {
    val childTokens = childTokensFor(ageBand)
    val childTypography = remember {
        buildChildTypography(ChildTokens.fontFamily, ChildTokens.fontWeight)
    }
    val parentTypography = remember { Typography() }

    val playfulness by animateFloatAsState(
        targetValue = if (mode == LumiMode.Child) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "playfulness",
    )
    val tokens = LumiTokens(
        // 颜色族 —— primary / onPrimary / ink 三档恒定(任务书 §7:行动金恒定),仅 brand / surfaceBg 变
        primary      = colorLerp(ParentTokens.primary, childTokens.primary, playfulness),
        onPrimary    = colorLerp(ParentTokens.onPrimary, childTokens.onPrimary, playfulness),
        brand        = colorLerp(ParentTokens.brand, childTokens.brand, playfulness),
        onBrand      = colorLerp(ParentTokens.onBrand, childTokens.onBrand, playfulness),
        surfaceBg    = colorLerp(ParentTokens.surfaceBg, childTokens.surfaceBg, playfulness),
        ink          = colorLerp(ParentTokens.ink, childTokens.ink, playfulness),
        // 形状
        cornerLarge  = unitLerp(ParentTokens.cornerLarge, childTokens.cornerLarge, playfulness),
        cornerMedium = unitLerp(ParentTokens.cornerMedium, childTokens.cornerMedium, playfulness),
        // 字号
        titleSp      = unitLerp(ParentTokens.titleSp, childTokens.titleSp, playfulness),
        bodySp       = unitLerp(ParentTokens.bodySp, childTokens.bodySp, playfulness),
        // 尺寸
        minTouch     = unitLerp(ParentTokens.minTouch, childTokens.minTouch, playfulness),
        // 动效预算 / 装饰开关
        bounceDepth  = lerpFloat(ParentTokens.bounceDepth, childTokens.bounceDepth, playfulness),
        mascotAlpha  = lerpFloat(ParentTokens.mascotAlpha, childTokens.mascotAlpha, playfulness),
        decorDensity = lerpFloat(ParentTokens.decorDensity, childTokens.decorDensity, playfulness),
        // 字体不可 lerp,playfulness > 0.5 阈值切换(过渡中点硬翻一次)
        fontFamily   = if (playfulness > 0.5f) childTokens.fontFamily else ParentTokens.fontFamily,
        fontWeight   = if (playfulness > 0.5f) childTokens.fontWeight else ParentTokens.fontWeight,
    )
    val effectiveTypography = if (playfulness > 0.5f) childTypography else parentTypography
    CompositionLocalProvider(LocalLumiTokens provides tokens) {
        MaterialTheme(typography = effectiveTypography, content = content)
    }
}

/**
 * 把默认 Material3 Typography 的 15 个 TextStyle 全部改写为儿童字体 + Black 字重。
 * 这样 `style = MaterialTheme.typography.X` 的所有 Text 在儿童模式下自动卡通,
 * 不需要逐处加 `fontFamily = tokens.fontFamily`。
 *
 * 注意:字号(fontSize / lineHeight / letterSpacing)保留 Material3 默认 ——
 * 我们要的只是"字形换皮",不是"字号也变",字号变化由 tokens.titleSp / bodySp 单独管理。
 */
private fun buildChildTypography(family: FontFamily, weight: FontWeight): Typography {
    val base = Typography()
    return Typography(
        displayLarge   = base.displayLarge.copy(fontFamily = family, fontWeight = weight),
        displayMedium  = base.displayMedium.copy(fontFamily = family, fontWeight = weight),
        displaySmall   = base.displaySmall.copy(fontFamily = family, fontWeight = weight),
        headlineLarge  = base.headlineLarge.copy(fontFamily = family, fontWeight = weight),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = weight),
        headlineSmall  = base.headlineSmall.copy(fontFamily = family, fontWeight = weight),
        titleLarge     = base.titleLarge.copy(fontFamily = family, fontWeight = weight),
        titleMedium    = base.titleMedium.copy(fontFamily = family, fontWeight = weight),
        titleSmall     = base.titleSmall.copy(fontFamily = family, fontWeight = weight),
        bodyLarge      = base.bodyLarge.copy(fontFamily = family, fontWeight = weight),
        bodyMedium     = base.bodyMedium.copy(fontFamily = family, fontWeight = weight),
        bodySmall      = base.bodySmall.copy(fontFamily = family, fontWeight = weight),
        labelLarge     = base.labelLarge.copy(fontFamily = family, fontWeight = weight),
        labelMedium    = base.labelMedium.copy(fontFamily = family, fontWeight = weight),
        labelSmall     = base.labelSmall.copy(fontFamily = family, fontWeight = weight),
    )
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t
