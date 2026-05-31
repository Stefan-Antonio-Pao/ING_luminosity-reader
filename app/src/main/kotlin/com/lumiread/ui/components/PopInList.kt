package com.lumiread.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumiread.ui.motion.Motion
import com.lumiread.ui.theme.LocalLumiTokens
import kotlinx.coroutines.delay

/**
 * 错峰登场列表 + 子项工具(UI 改造任务书 §5.4,2026-05-25 步骤四)。
 *
 * 任务书 §5.4:"登场 Pop-in 错峰蹦出"。子项按 [staggerMs] 间隔依次出现:
 *  - scale: 0.6 → 1(走 [Motion.PopInSpring] 超调回弹)
 *  - alpha: 0 → 1(220ms 线性补,纯透明度无需弹簧)
 *
 * **家长模式策略**:`tokens.mascotAlpha == 0f` ⇔ 家长模式 → **跳过 staggered 动画**,
 * 子项一次性出现。这是任务书 §4 "家长模式近乎无动效"的体现:用 mascotAlpha 作 mode 信号,
 * 避免再引一个独立的 token。
 *
 * **两个 API**:
 *  - [PopInList]:便利封装,直接传 items 列表 + itemContent
 *  - [PopInItem]:暴露的子项原语,任意布局(Row / Box / FlowRow)中按 index 错峰登场
 */
@Composable
fun <T> PopInList(
    items: List<T>,
    modifier: Modifier = Modifier,
    staggerMs: Int = 80,
    spacing: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items.forEachIndexed { index, item ->
            PopInItem(index = index, staggerMs = staggerMs) {
                itemContent(item)
            }
        }
    }
}

@Composable
fun PopInItem(
    index: Int,
    staggerMs: Int = 80,
    content: @Composable () -> Unit,
) {
    val tokens = LocalLumiTokens.current
    val animated = tokens.mascotAlpha > 0f

    if (!animated) {
        content()
        return
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * staggerMs).toLong())
        shown = true
    }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = Motion.PopInSpring,
        label = "pop-in-scale",
    )
    val alphaAnim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "pop-in-alpha",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = alphaAnim
        },
    ) { content() }
}
