package com.lumiread.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalMode
import com.lumiread.ui.theme.LocalTier
import com.lumiread.ui.theme.LumiColors
import com.lumiread.ui.theme.LumiMode
import com.lumiread.ui.theme.ParentSectionStyle
import com.lumiread.ui.theme.ParentStatStyle
import com.lumiread.ui.theme.bodyFamily
import com.lumiread.ui.theme.displayFamily

/**
 * AI 回答气泡(components#reading-bubble)。白卡 + 圆角(tier.radius)+ 顶部 LUMIREAD 小标签;
 * 首句默认作为夸赞句加粗,余下为解释。底部 slot 放 TTS 按钮 / chips。
 */
@Composable
fun ReadingBubble(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    val tier = LocalTier.current
    val mode = LocalMode.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tier.radius))
            .background(palette.bubbleAi)
            .border(1.dp, palette.border, RoundedCornerShape(tier.radius))
            .padding(tier.cardPad),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LumiIcon(com.lumiread.R.drawable.decor_star_burst, null, tint = LumiColors.Gold500, size = 18.dp)
                Text("LUMIREAD", style = ParentSectionStyle, color = palette.textMuted)
            }
            Text(
                text = if (streaming && text.isEmpty()) "…" else text + (if (streaming) " ▌" else ""),
                color = palette.text,
                fontFamily = bodyFamily(mode),
                fontSize = tier.fsBody,
                lineHeight = tier.fsBody * tier.lineHeight,
            )
            footer?.invoke()
        }
    }
}

/** 用户消息气泡:右对齐,ink700 底白字,最大宽 82%。 */
@Composable
fun UserMessageBubble(text: String, modifier: Modifier = Modifier) {
    val tier = LocalTier.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(
                    RoundedCornerShape(
                        topStart = tier.radius, topEnd = tier.radius,
                        bottomStart = tier.radius, bottomEnd = 6.dp,
                    )
                )
                .background(LumiColors.Ink700)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(text = text, color = Color.White, fontSize = tier.fsBody)
        }
    }
}

/** 家长统计卡(components#stat-card)。白底 + 1px border,大数字 + label。 */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = ParentStatStyle, color = palette.text)
        Text(label, color = palette.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

/** 设置行(components#settings-row)。左标题+副述,右 slot;底部细分隔线。 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = palette.textMuted, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}

/**
 * 友好错误/空状态卡(components#error-card / empty-state)。
 * 顶部圆角方块图标 + 标题 + 文案 + 主行动按钮 + ghost 回首页。先解释、后下一步。
 */
@Composable
fun ErrorCard(
    iconRes: Int,
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    homeLabel: String?,
    onHome: (() -> Unit)?,
    modifier: Modifier = Modifier,
    soft: Boolean = false,
) {
    val palette = LocalLumiPalette.current
    val tint = if (soft) LumiColors.Gold700 else LumiColors.Error
    val iconBg = if (soft) LumiColors.Gold100 else LumiColors.ErrorSoft
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) { LumiIcon(iconRes, null, tint = tint, size = 40.dp) }
        Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(body, color = palette.textSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            LumiPrimaryButton(onClick = onAction, label = actionLabel, modifier = Modifier.fillMaxWidth())
        }
        if (homeLabel != null && onHome != null) {
            Text(
                homeLabel,
                color = palette.textMuted,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onHome).padding(8.dp),
            )
        }
    }
}

/** 三选一年龄段胶囊组(components#age-band-selector)。选中填充 ink700 白字。 */
@Composable
fun <T> SegmentedSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surfaceAlt)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSel) LumiColors.Ink700 else Color.Transparent)
                    .clickable { onSelect(value) }
                    .heightIn(min = 44.dp)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSel) Color.White else palette.textSoft,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 家长页 section 小标题(大写字距)。 */
@Composable
fun ParentSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ParentSectionStyle, color = LocalLumiPalette.current.textMuted, modifier = modifier)
}
