package com.lumiread.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * 吉祥物占位视图(part of the UI overhaul)。
 *
 * **当前版本是占位**,真正的 Lottie 待机/反应/庆祝动画推迟到后续版本:
 * - 设计纪律明令禁止"凭记忆写 Lottie/字体坐标与许可"
 * - 设计规范强调 Lottie 动画文件**各有许可,须核对可商用并致谢**
 * - will一次性核对许可、下载文件、入 THIRD_PARTY_NOTICES.md
 *
 * 当前实现:几何抽象星形(★ Unicode glyph)+ 圆底,**原创且与任何 IP 无关**
 * (设计纪律:不得复刻爆款幼儿 IP / 抄某 App 视觉资产)。
 *
 * 可见性:
 * - 整体 alpha = `tokens.mascotAlpha`(家长 0 → 完全隐形;儿童 1 → 完全可见)
 * - 双模式之间会随 [LumiTheme] 的 playfulness 在 450ms 内淡入淡出
 */
@Composable
fun MascotView(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val tokens = LocalLumiTokens.current
    if (tokens.mascotAlpha <= 0.001f) return  // 家长模式直接跳过绘制

    Box(
        modifier = modifier
            .graphicsLayer { alpha = tokens.mascotAlpha }
            .size(size)
            .clip(CircleShape)
            .background(tokens.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "★",
            color = tokens.onPrimary,
            fontSize = tokens.titleSp,
        )
    }
}
