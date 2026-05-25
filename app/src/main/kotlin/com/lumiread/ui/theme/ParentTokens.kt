package com.lumiread.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 家长模式令牌(UI overhaul)。
 *
 * **= v1.1 改造前的简洁现状**,但用 M3 light 默认色显式建模 —— 逐屏接入
 * token 化按钮时,确保 `LumiPrimaryButton(parent)` 看起来与原生 `Button` 等价。
 *
 * later(2026-05-25)调整:`primary` 从中性灰 `#E7E0EC` 改为 M3 light primary
 * `#6750A4`,`onPrimary` 从墨黑改为白 —— 这样原有 Material `Button(...)` 替换为
 * `LumiPrimaryButton(...)` 后,**家长模式像素级一致**(填充色、文字色都对得上)。
 *
 * 副作用:`[ModeSwitchSection]` 过渡演示卡的"暖金药丸"在家长模式下颜色由灰变 M3 紫 ——
 * 这是更诚实的"家长模式 = 当前 M3 现状"表达,与本应用其它屏 M3 紫按钮一致。
 *
 * 其它字段仍保持 M3 中性基线;字号/尺寸保持 M3 baseline。
 */
val ParentTokens = LumiTokens(
    // 颜色族:M3 light 默认色(对齐 androidx.compose.material3 default ColorScheme)
    primary = Color(0xFF6750A4),       // M3 light primary
    onPrimary = Color(0xFFFFFFFF),     // M3 light onPrimary
    brand = Color(0xFFE7E0EC),         // M3 light surfaceVariant(承担"沉浸/标题块/助手气泡"角色,与 surfaceBg 拉开层次)
    onBrand = Color(0xFF49454F),       // M3 light onSurfaceVariant
    surfaceBg = Color(0xFFFFFBFE),     // M3 light surface
    ink = Color(0xFF1C1B1F),           // M3 light onSurface

    // 形状:M3 基线
    cornerLarge = 12.dp,
    cornerMedium = 8.dp,

    // 字号:M3 baseline
    titleSp = 22.sp,
    bodySp = 16.sp,

    // 尺寸:M3 触控基线
    minTouch = 48.dp,

    // 动效:不缩
    bounceDepth = 0f,

    // 装饰:无
    mascotAlpha = 0f,
    decorDensity = 0f,

    // 字体:系统默认 sans-serif,普通 weight —— 家长态等价 M3 默认排版
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
)
