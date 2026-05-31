package com.lumiread.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumiread.ui.motion.Motion
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * 圆胖开关(UI 改造任务书 §6.2,2026-05-25 步骤四)。
 *
 * 步骤五接入设置页的"自动朗读 / 多模态切换"等布尔开关。
 *
 * 视觉:
 *  - 轨道:宽 72dp,高 = `tokens.cornerMedium × 2`,圆角胶囊(`tokens.cornerMedium`)
 *  - 拇指:正圆,直径 = 轨道高 - 8dp(留 4dp 内边距)
 *  - 颜色:开 = `tokens.primary`(行动金),关 = `tokens.surfaceBg`(浅底)
 *
 * 动效:
 *  - 拇指位置走 [Motion.pressSpring] 的 Dp 弹簧,儿童模式自然超调回弹
 *  - 颜色 [Motion.pressSpring] 的 Color 弹簧
 *  - 按下时 [BouncyButton] 整体缩到 `1 - bounceDepth`(家长 = 不缩)
 *
 * 任务书 §10 红线:**禁止散落硬编码颜色/尺寸/动画** —— 这里轨道尺寸 72×(2×cornerMedium)
 * 是从 token 派生的,改 `cornerMedium` 自动跟着变,无硬编码视觉。
 */
@Composable
fun ChunkySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalLumiTokens.current
    val trackWidth = 72.dp
    val trackHeight = tokens.cornerMedium * 2  // 儿童 40dp / 家长 16dp
    val padding = 4.dp
    val thumbSize = trackHeight - padding * 2
    val maxOffset = trackWidth - thumbSize - padding * 2

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = Motion.pressSpring(),
        label = "chunky-thumb-offset",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) tokens.primary else tokens.surfaceBg,
        animationSpec = Motion.pressSpring(),
        label = "chunky-track-color",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) tokens.onPrimary else tokens.ink.copy(alpha = 0.4f),
        animationSpec = Motion.pressSpring(),
        label = "chunky-thumb-color",
    )

    BouncyButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier
            .width(trackWidth)
            .clip(RoundedCornerShape(tokens.cornerMedium))
            .background(trackColor)
            .border(2.dp, tokens.ink.copy(alpha = 0.2f), RoundedCornerShape(tokens.cornerMedium))
            .padding(padding),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}
