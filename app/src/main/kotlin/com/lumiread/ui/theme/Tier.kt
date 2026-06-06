package com.lumiread.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumiread.core.AgeBand

/**
 * LumiRead v3.0.0 年龄段视觉变体(Tier)。
 *
 * 数值逐一来自 `design-system/tokens.json#tier` 与 `design-system/age-band-variants.md §1`。
 * 同一套 Composable + 读取 [LocalTier] 实现差异,**不为不同 tier 写不同 Composable**。
 *
 * 与 core 的 [AgeBand] 一一对应,通过 [tierFor] 映射。
 */
enum class Tier(
    val fsDisplay: TextUnit,
    val fsTitle: TextUnit,
    val fsBody: TextUnit,
    val fsCaption: TextUnit,
    val btnHeight: Dp,
    val btnFontSize: TextUnit,
    val radius: Dp,
    val cardPad: Dp,
    val lineHeight: Float,
    val animAmp: Float,
    val animDurScale: Float,
    val decorDensity: Float,
    val iconRatio: Float,
    /** 拍照大按钮直径(age-band-variants §5) */
    val captureDiameter: Dp,
    /** 是否显示 prompt chips(toddler 隐藏) */
    val showChips: Boolean,
    /** 故事开头 chips 数量 */
    val storyChipCount: Int,
    /** AI 回答正文软上限字符数(中文计) */
    val answerCharCap: Int,
    /** 是否显示历史用户提问气泡(仅 preadolescent) */
    val showUserHistory: Boolean,
) {
    TODDLER(
        fsDisplay = 34.sp, fsTitle = 26.sp, fsBody = 20.sp, fsCaption = 16.sp,
        btnHeight = 72.dp, btnFontSize = 22.sp, radius = 36.dp, cardPad = 24.dp,
        lineHeight = 1.5f, animAmp = 1.15f, animDurScale = 1.20f, decorDensity = 1.0f,
        iconRatio = 1.40f, captureDiameter = 96.dp,
        showChips = false, storyChipCount = 2, answerCharCap = 30, showUserHistory = false,
    ),
    PRESCHOOL(
        fsDisplay = 30.sp, fsTitle = 22.sp, fsBody = 17.sp, fsCaption = 14.sp,
        btnHeight = 60.dp, btnFontSize = 18.sp, radius = 28.dp, cardPad = 20.dp,
        lineHeight = 1.5f, animAmp = 1.00f, animDurScale = 1.00f, decorDensity = 0.7f,
        iconRatio = 1.15f, captureDiameter = 88.dp,
        showChips = true, storyChipCount = 4, answerCharCap = 80, showUserHistory = false,
    ),
    PREADOLESCENT(
        fsDisplay = 26.sp, fsTitle = 19.sp, fsBody = 15.sp, fsCaption = 13.sp,
        btnHeight = 52.dp, btnFontSize = 16.sp, radius = 22.dp, cardPad = 16.dp,
        lineHeight = 1.55f, animAmp = 0.85f, animDurScale = 0.85f, decorDensity = 0.35f,
        iconRatio = 1.00f, captureDiameter = 80.dp,
        showChips = true, storyChipCount = 5, answerCharCap = 150, showUserHistory = true,
    );
}

fun tierFor(ageBand: AgeBand): Tier = when (ageBand) {
    AgeBand.TODDLER -> Tier.TODDLER
    AgeBand.PRESCHOOL -> Tier.PRESCHOOL
    AgeBand.PREADOLESCENT -> Tier.PREADOLESCENT
}

val LocalTier = staticCompositionLocalOf { Tier.PRESCHOOL }
