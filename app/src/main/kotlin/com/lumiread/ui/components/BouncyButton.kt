package com.lumiread.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.lumiread.ui.motion.Motion
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * 全局**按下回弹**按钮(core UI overhaul component)。
 *
 * 行为:
 * - 按下 → `scale = 1 - tokens.bounceDepth`(儿童 0.92,家长 1 即不缩)
 * - 松手 → 经 [Motion.PressSpring] 弹簧回弹超调
 * - 三重反馈(设计规范):视觉缩放 + 触觉反馈,**仅儿童模式**触发
 *
 * **2026-05-25 bugfix**:原先用 `detectTapGestures + awaitRelease`,在 `verticalScroll`
 * 父容器下,手指轻微滑动就被滚动手势竞争抢走 → onTap 不触发 → 点击丢失/感觉很卡。
 * 改成 `Modifier.clickable` + `InteractionSource.collectIsPressedAsState`:
 * - `clickable` 内部走 Compose 标准手势协议,正确把"垂直拖动"让给父滚动,"短按"自己消化
 * - `interactionSource` 监听 press,驱动缩放动画
 * - `LaunchedEffect(pressed)` 触感反馈与 press 状态同步
 *
 * 用法:作为其它组件(BigCircleActionButton / SelectableIllustratedCard / ChunkySwitch)的
 * 复合基座。设计纪律(设计规范):scale 数值全部从 `tokens.bounceDepth` 派生。
 */
@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalLumiTokens.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f - tokens.bounceDepth else 1f,
        animationSpec = Motion.PressSpring,
        label = "bouncy-press-scale",
    )
    LaunchedEffect(pressed) {
        if (pressed && enabled && tokens.bounceDepth > 0f) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        content = content,
    )
}
