package com.lumiread.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * 通用大圆角卡片(part of the UI overhaul)。
 *
 * 形状 = `tokens.cornerLarge`(儿童 28dp / 家长 12dp);
 * 背景 = `tokens.surfaceBg`(儿童 Cream / 家长白);
 * 默认内边距 16dp(可覆盖)。
 *
 * **不**绘制阴影:设计规范提到卡面"白+柔和阴影",阴影留到正式接入屏幕时,
 * 与各屏布局节奏一并调,避免单组件先做阴影后整屏视觉打架。
 */
@Composable
fun PlayfulCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalLumiTokens.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.cornerLarge))
            .background(tokens.surfaceBg)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
