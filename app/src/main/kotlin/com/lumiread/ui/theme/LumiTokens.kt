package com.lumiread.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * 设计令牌(UI 改造任务书 §3.2 / §3.3 / §5.2 / §5.3)。
 *
 * 同一套组件,在 [LumiMode.Parent] 与 [LumiMode.Child] 下读取不同令牌值,
 * 由 [LumiTheme] 通过 [LocalLumiTokens] 注入并跨 mode 做 `playfulness` lerp。
 * **禁止在组件内硬编码颜色 / 尺寸 / 动画**。
 *
 * 步骤三(2026-05-25)字段扩展:把任务书 §5.2 "深蓝管沉静、暖金管点亮、浅底保通透"的色族
 * 完整建模 —— [primary]/[onPrimary] 管"行动按钮",[brand]/[onBrand] 管"沉浸背景/标题",
 * [surfaceBg]/[ink] 管"内容浅底与正文"。
 *
 * 字段说明:
 *  - 颜色族(§5.2)
 *    - [primary]:**行动主色**(按钮填充)。家长=中性灰白 / 儿童=行动金 `#F6C445`
 *    - [onPrimary]:primary 上文字色
 *    - [brand]:**沉浸/品牌色**(背景、标题块)。家长=白 / 儿童=主品牌蓝 `#0A57ED`
 *    - [onBrand]:brand 上文字色
 *    - [surfaceBg]:**内容浅底**。家长=白 / 儿童=Cream `#FFF7EC`
 *    - [ink]:**正文墨色**。家长=墨黑 / 儿童=墨色 Ink `#102A5C`
 *  - 形状(§5.3)
 *    - [cornerLarge]:卡片圆角。家长=12 / 儿童=28(24–32dp 中位)
 *    - [cornerMedium]:按钮圆角。家长=8 / 儿童=20
 *  - 字号(§5.3)
 *    - [titleSp]:标题。家长=22 / 儿童=30
 *    - [bodySp]:正文。家长=16 / 儿童=20(§5.3 正文 ≥18sp)
 *  - 尺寸(§5.3)
 *    - [minTouch]:触控最小高度。家长=48 基线 / 儿童=72(§5.3 主按钮 ≥72dp;Toddler 档步骤七再放大)
 *  - 动效预算(§5.4,实际由 BouncyButton 在步骤四读取)
 *    - [bounceDepth]:按下回弹深度。家长=0(不缩)/ 儿童=0.08(缩到 0.92)
 *  - 装饰开关(步骤六落地)
 *    - [mascotAlpha]:吉祥物可见度。家长=0 / 儿童=1
 *    - [decorDensity]:背景装饰密度。家长=0 / 儿童=1
 *
 * 注:**字体族**(任务书 §5.3 拉丁 Fredoka/Baloo 2、中文 站酷快乐体/得意黑)许可需 `[MUST-VERIFY]`,
 * 推迟到步骤五"逐屏套用"前与 Lottie 一并核对、下载、记入 THIRD_PARTY_NOTICES.md(任务书 §10 红线)。
 * 当前阶段所有字号字段仍使用系统默认 FontFamily。
 */
@Immutable
data class LumiTokens(
    // 颜色族
    val primary: Color,
    val onPrimary: Color,
    val brand: Color,
    val onBrand: Color,
    val surfaceBg: Color,
    val ink: Color,
    // 形状
    val cornerLarge: Dp,
    val cornerMedium: Dp,
    // 字号
    val titleSp: TextUnit,
    val bodySp: TextUnit,
    // 尺寸
    val minTouch: Dp,
    // 动效预算
    val bounceDepth: Float,
    // 装饰开关
    val mascotAlpha: Float,
    val decorDensity: Float,
    // 字体(步骤六,2026-05-25)
    // 家长 = FontFamily.Default(系统默认 sans-serif);儿童 = Luckiest Guy(Apache 2.0,Comic-sans 风,单一 Black weight)
    // FontFamily 不可 lerp,LumiTheme 按 playfulness > 0.5 阈值切换
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
)

/**
 * 通过 [LumiTheme] 注入的令牌。**未在 LumiTheme 内调用时直接读取会抛错**(开发期早爆,防止漏接 theme)。
 */
val LocalLumiTokens = staticCompositionLocalOf<LumiTokens> {
    error("LumiTheme 未提供 LumiTokens — 请确保 UI 居于 LumiTheme { ... } 之内")
}
