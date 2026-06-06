package com.lumiread.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * LumiRead v3.0.0 调色板。
 *
 * 数值逐一来自设计交付 `design-system/tokens.json`(单一事实源)。映射方式见
 * `handoff/compose-implementation-notes.md §4`。儿童(Kids)与家长(Parent)两套语义色:
 * Kids = 奶油纸页 + 深墨蓝 + 暖金;Parent = 冷静冷灰 + 墨蓝主色 + 暖金点缀。
 *
 * 与旧的 [LumiTokens] 插值系统并存:新页面读 [LocalLumiPalette],旧页面读 [LocalLumiTokens]。
 */
@Immutable
data class LumiPalette(
    val bg: Color,
    val bgElevated: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val textSoft: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryPressed: Color,
    val accent: Color,
    val border: Color,
    // 气泡(对话)
    val bubbleAi: Color,
    val bubbleUser: Color,
)

// ── 品牌色族(tokens.json#color.brand / paper / semantic),供装饰与渐变直接引用 ──
object LumiColors {
    // brand ink
    val Ink900 = Color(0xFF0F1B3D)
    val Ink700 = Color(0xFF1E2C5C)
    val Ink500 = Color(0xFF2E4488)
    val Ink300 = Color(0xFF6A7BB0)
    val Ink100 = Color(0xFFC9D2EA)
    // gold
    val Gold700 = Color(0xFFB57B1E)
    val Gold500 = Color(0xFFE8A33A)
    val Gold300 = Color(0xFFF4C97A)
    val Gold100 = Color(0xFFFBE9C2)
    // 主按钮渐变上端(components.md#primary-button)
    val GoldGradTop = Color(0xFFF2B752)
    val OnGold = Color(0xFF2A1E03) // 暖金按钮上的近黑棕字,AA 通过
    // paper
    val Paper1000 = Color(0xFFFFFDF6)
    val Paper900 = Color(0xFFFBF6E8)
    val Paper700 = Color(0xFFF3EBD3)
    val Paper500 = Color(0xFFE6DAB6)
    val Paper300 = Color(0xFFC8B98D)
    // semantic
    val Success = Color(0xFF2F8C5A)
    val SuccessSoft = Color(0xFFDDEFE2)
    val Warning = Color(0xFFC97A20)
    val WarningSoft = Color(0xFFFBE9C2)
    val Error = Color(0xFFB6422F)
    val ErrorSoft = Color(0xFFF6DCD4)
    // camera 暗底
    val CameraDark = Color(0xFF0B1020)
}

val KidsPalette = LumiPalette(
    bg = Color(0xFFFFFDF6),
    bgElevated = Color(0xFFFFFFFF),
    surface = Color(0xFFFBF6E8),
    surfaceAlt = Color(0xFFEEF1FB),
    text = Color(0xFF0F1B3D),
    textSoft = Color(0xFF2E4488),
    textMuted = Color(0xFF6A7BB0),
    primary = Color(0xFFE8A33A),
    primaryPressed = Color(0xFFB57B1E),
    accent = Color(0xFF1E2C5C),
    border = Color(0xFFE6DAB6),
    bubbleAi = Color(0xFFFFFFFF),
    bubbleUser = Color(0xFF1E2C5C),
)

val ParentPalette = LumiPalette(
    bg = Color(0xFFF7F8FB),
    bgElevated = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEEF1F7),
    text = Color(0xFF14193B),
    textSoft = Color(0xFF4A537A),
    textMuted = Color(0xFF8A92AE),
    primary = Color(0xFF1E2C5C),
    primaryPressed = Color(0xFF0F1B3D),
    accent = Color(0xFFE8A33A),
    border = Color(0xFFE3E7F0),
    bubbleAi = Color(0xFFFFFFFF),
    bubbleUser = Color(0xFF1E2C5C),
)

fun lumiPalette(mode: LumiMode): LumiPalette =
    if (mode == LumiMode.Child) KidsPalette else ParentPalette

val LocalLumiPalette = staticCompositionLocalOf { KidsPalette }
