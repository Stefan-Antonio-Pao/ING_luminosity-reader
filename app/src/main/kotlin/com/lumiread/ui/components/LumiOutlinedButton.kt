package com.lumiread.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * 描边辅助按钮(part of the UI overhaul)。
 *
 * 与 [LumiPrimaryButton] 成对:主操作走 Primary、次操作走 Outlined。
 * - **家长模式**:1dp tokens.primary 描边 + 透明底,视觉与原生 `OutlinedButton(...)` 等价
 * - **儿童模式**:同样 1dp 描边但圆角更大、字号更胖、按下回弹 —— 按下手感与 Primary 一致
 *
 * 设计纪律(设计规范):描边色 / 文字色全部读 `tokens.primary`,不硬编码。
 * 描边宽度暂用 1dp(M3 默认),如儿童模式需更胖描边,日后追加 `borderWidth` token。
 */
@Composable
fun LumiOutlinedButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = LocalLumiTokens.current
    BouncyButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(tokens.cornerMedium))
            .border(1.dp, tokens.primary, RoundedCornerShape(tokens.cornerMedium))
            .defaultMinSize(minHeight = tokens.minTouch)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = tokens.primary,
            fontSize = tokens.bodySp,
            fontFamily = tokens.fontFamily,
            fontWeight = tokens.fontWeight,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
