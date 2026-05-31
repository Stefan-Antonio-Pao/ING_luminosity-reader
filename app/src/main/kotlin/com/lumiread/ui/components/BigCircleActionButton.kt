package com.lumiread.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * 超大圆形主行动按钮(UI 改造任务书 §6.2 / §5.4,2026-05-25 步骤四)。
 *
 * 步骤五在首页"拍绘本"位置接入。当前只作为组件存在,演示画在 [ModeSwitchSection] 内。
 *
 * 视觉:
 *  - 直径 = `tokens.minTouch × sizeFactor`(默认 1.6 倍,儿童 ≈ 115dp,家长 ≈ 77dp)
 *  - 填充 = `tokens.primary`(儿童 = 行动金,家长 = M3 默认浅紫)
 *  - 文字 = `tokens.onPrimary` + `tokens.titleSp`
 *
 * 动效:
 *  - **轻柔呼吸**(`rememberInfiniteTransition` 1.8s 周期,峰值 1.04)—— 仅儿童模式启用,
 *    家长模式幅度自动为 0(由 `tokens.bounceDepth > 0f` 判定),克制即"阅读时安静"原则
 *  - **按下回弹**:复用 [BouncyButton] 基座,bounceDepth 同步驱动
 *
 * 任务书 §5.4 提示:**主 CTA 用 InfiniteTransition 轻柔呼吸,克制别全屏乱跳**。这里振幅 4%
 * 即可让按钮"活着",不抢注意力。
 */
@Composable
fun BigCircleActionButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    sizeFactor: Float = 1.6f,
) {
    val tokens = LocalLumiTokens.current
    val size = tokens.minTouch * sizeFactor

    val transition = rememberInfiniteTransition(label = "big-circle-breath")
    val breathTarget = if (tokens.bounceDepth > 0f) 1.04f else 1f
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = breathTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "big-circle-breath-scale",
    )

    BouncyButton(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = breath
                scaleY = breath
            }
            .size(size)
            .clip(CircleShape)
            .background(tokens.primary),
    ) {
        Text(
            text = label,
            color = tokens.onPrimary,
            fontSize = tokens.titleSp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
