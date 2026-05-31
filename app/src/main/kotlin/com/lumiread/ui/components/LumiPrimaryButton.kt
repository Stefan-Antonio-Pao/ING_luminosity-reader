package com.lumiread.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
 * 填充式主按钮(UI 改造任务书 §5.2 / §5.4,2026-05-25 步骤五)。
 *
 * 在双模式下统一行动按钮的外观与反馈:
 *  - **家长模式**:tokens 对齐 M3 light primary,视觉与原生 `Button(...)` 等价;无回弹
 *  - **儿童模式**:行动金 + Ink 文字 + 22dp 圆角 + 0.92 按下回弹 + 触感反馈
 *
 * 颜色 / 圆角 / 触控高度 / 字号 / 回弹深度全部从 [LocalLumiTokens] 派生(§10 红线:
 * 禁止硬编码视觉数值)。
 *
 * 用法:替换 CameraCaptureScreen / SettingsScreen / ChatScreen 等屏的原生 `Button(...) { Text(...) }`。
 *
 * @param enabled 禁用态走 38% alpha(对齐 M3 disabled),不接受点击
 * @param modifier 外层 Modifier;weight / fillMaxWidth 等布局约束在此叠加
 */
@Composable
fun LumiPrimaryButton(
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
            .background(tokens.primary)
            .defaultMinSize(minHeight = tokens.minTouch)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = tokens.onPrimary,
            fontSize = tokens.bodySp,
            fontFamily = tokens.fontFamily,
            fontWeight = tokens.fontWeight,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
