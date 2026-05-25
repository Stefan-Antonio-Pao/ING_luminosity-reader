package com.lumiread.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumiread.ui.motion.Motion
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * 可选中的图文卡(part of the UI overhaul)。
 *
 * 适合"年龄段选择 / 输出语言 / 输出模式"等需要一组互斥选项 + 图示的设置项,
 * wire up设置页时复用。
 *
 * 状态:
 * - **未选中**:背景 = `tokens.surfaceBg`,边框 0dp
 * - **选中**:背景 = `tokens.primary` 18% 半透明覆层,边框 = `tokens.primary` 3dp
 *
 * 选中态切换走 [Motion.PressSpring] 的 Dp / Color 弹簧,**家长模式因 bounceDepth=0
 * BouncyButton 不缩,但选中态颜色仍然平滑过渡** —— 这是有意为之:选中反馈在两种
 * 模式下都需要可见,只是儿童模式额外有按下缩放。
 */
@Composable
fun SelectableIllustratedCard(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalLumiTokens.current

    val bgColor by animateColorAsState(
        targetValue = if (selected) tokens.primary.copy(alpha = 0.18f) else tokens.surfaceBg,
        animationSpec = Motion.pressSpring(),
        label = "sel-card-bg",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = Motion.pressSpring(),
        label = "sel-card-border",
    )

    BouncyButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cornerLarge))
            .background(bgColor)
            .border(borderWidth, tokens.primary, RoundedCornerShape(tokens.cornerLarge))
            .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        Text(
            text = label,
            color = tokens.ink,
            fontSize = tokens.bodySp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}
