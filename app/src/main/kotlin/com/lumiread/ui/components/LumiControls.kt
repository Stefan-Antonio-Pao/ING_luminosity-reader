package com.lumiread.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalMode
import com.lumiread.ui.theme.LocalReducedMotion
import com.lumiread.ui.theme.LocalTier
import com.lumiread.ui.theme.LumiColors
import com.lumiread.ui.theme.LumiMode
import com.lumiread.ui.theme.LumiMotion
import com.lumiread.ui.theme.displayFamily

/** 按下回弹 Modifier:幅度随 tier.animAmp,reduced-motion 时不缩放。 */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val tier = LocalTier.current
    val depth = if (reduced) 0f else 0.06f * tier.animAmp
    val target = if (pressed) 1f - depth else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(LumiMotion.FAST),
        label = "pressScale",
    )
    return this.scale(scale)
}

/**
 * 主行动按钮(components#primary-button)。暖金线性渐变 + 近黑棕字,高度=tier.btnHeight,
 * 圆角=tier.radius。每屏唯一主操作。家长模式用 palette.primary 实色(更克制)。
 */
@Composable
fun LumiPrimaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tier = LocalTier.current
    val mode = LocalMode.current
    val palette = LocalLumiPalette.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(tier.radius)
    val bg = if (mode == LumiMode.Child) {
        Modifier.background(Brush.verticalGradient(listOf(LumiColors.GoldGradTop, LumiColors.Gold500)), shape)
    } else {
        Modifier.background(palette.primary, shape)
    }
    val textColor = if (mode == LumiMode.Child) LumiColors.OnGold else Color.White
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = tier.btnHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(interaction)
            .clip(shape)
            .then(bg)
            .androidClickable(enabled, interaction, onClick, Role.Button)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = displayFamily(mode),
            fontWeight = FontWeight.Bold,
            fontSize = tier.btnFontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/** 次要按钮(components#secondary-button):白底 + ink100 描边 + ink700 字,略矮。 */
@Composable
fun LumiSecondaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tier = LocalTier.current
    val mode = LocalMode.current
    val palette = LocalLumiPalette.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(tier.radius)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = (tier.btnHeight.value - 12).dp)
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(interaction)
            .clip(shape)
            .background(palette.bgElevated, shape)
            .border(2.dp, LumiColors.Ink100, shape)
            .androidClickable(enabled, interaction, onClick, Role.Button)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = palette.textSoft,
            fontFamily = displayFamily(mode),
            fontWeight = FontWeight.SemiBold,
            fontSize = tier.btnFontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/** 圆形图标按钮,命中区 ≥44dp(toddler 56dp)。暗底场景传 [onDark]=true 反白。 */
@Composable
fun LumiIconButton(
    resId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDark: Boolean = false,
    tint: Color? = null,
) {
    val tier = LocalTier.current
    val palette = LocalLumiPalette.current
    val interaction = remember { MutableInteractionSource() }
    val hit = if (tier == com.lumiread.ui.theme.Tier.TODDLER) 56.dp else 48.dp
    val iconTint = tint ?: if (onDark) Color.White else palette.text
    Box(
        modifier = modifier
            .size(hit)
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(if (onDark) Color.Black.copy(alpha = 0.4f) else palette.surfaceAlt)
            .androidClickable(enabled, interaction, onClick, Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        LumiIcon(resId = resId, contentDescription = contentDescription, tint = iconTint, size = 24.dp * tier.iconRatio)
    }
}

/** 圆形大拍照按钮(camera),直径=tier.captureDiameter(toddler 96 / preschool 88 / pre 80)。 */
@Composable
fun LumiCaptureButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tier = LocalTier.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(tier.captureDiameter)
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(LumiColors.Gold500)
            .border(5.dp, Color.White, CircleShape)
            .androidClickable(enabled, interaction, onClick, Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        LumiIcon(
            resId = com.lumiread.R.drawable.ic_lumi_camera,
            contentDescription = contentDescription,
            tint = LumiColors.OnGold,
            size = 36.dp,
        )
    }
}

/** Prompt chip(components#prompt-chip):白底 + ink100 描边药丸;[gold]=true 金色变体。 */
@Composable
fun PromptChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = LocalLumiPalette.current
    val interaction = remember { MutableInteractionSource() }
    val border = if (gold) LumiColors.Gold300 else LumiColors.Ink100
    val bg = if (gold) LumiColors.Gold100 else palette.bgElevated
    val fg = if (gold) LumiColors.Gold700 else palette.textSoft
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(interaction)
            .clip(shape)
            .background(bg, shape)
            .border(1.5.dp, border, shape)
            .androidClickable(enabled, interaction, onClick, Role.Button)
            .sizeIn(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

/** 朴素开关(components,family settings-row 右侧)。 */
@Composable
fun LumiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    val interaction = remember { MutableInteractionSource() }
    val trackOn = palette.primary
    val trackOff = LumiColors.Ink100
    val align by animateFloatAsState(if (checked) 1f else 0f, tween(LumiMotion.FAST), label = "switch")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) trackOn else trackOff)
            .androidClickable(true, interaction, { onCheckedChange(!checked) }, Role.Switch)
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .align(if (align > 0.5f) Alignment.CenterEnd else Alignment.CenterStart)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** 朗读播放药丸(components#tts-button)。播放中显示 pause + 文案切换。 */
@Composable
fun TtsPlayButton(
    playing: Boolean,
    onToggle: () -> Unit,
    playLabel: String,
    pauseLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalLumiPalette.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(interaction)
            .clip(shape)
            .background(palette.bgElevated, shape)
            .border(1.5.dp, LumiColors.Gold300, shape)
            .androidClickable(enabled, interaction, onToggle, Role.Button)
            .height(44.dp)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(LumiColors.Gold500),
            contentAlignment = Alignment.Center,
        ) {
            LumiIcon(
                resId = if (playing) com.lumiread.R.drawable.ic_lumi_pause else com.lumiread.R.drawable.ic_lumi_play,
                contentDescription = null,
                tint = Color.White,
                size = 18.dp,
            )
        }
        Text(
            text = if (playing) pauseLabel else playLabel,
            color = LumiColors.Gold700,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * clickable 封装:统一无水波纹(用回弹替代涟漪),并设置语义 Role。
 * 命名带 android 前缀避免与 foundation.clickable 混淆。
 */
private fun Modifier.androidClickable(
    enabled: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    role: Role,
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    role = role,
    onClick = onClick,
)
