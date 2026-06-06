package com.lumiread.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumiread.R

/**
 * LumiRead v3.0.0 字体家族。
 *
 * 设计交付要求 Fraunces / Nunito / Inter / Noto Sans SC / Noto Serif SC 五套变体字体
 * (`compose-implementation-notes.md §5`)。但本工程 `res/font/` 仅有 `zcool_kuaile.ttf`。
 * 防幻觉铁律1:**不凭空捏造字体文件**。降级映射:
 *  - 儿童标题(kidsDisplay):站酷快乐体(圆胖,含 Latin+简中),近似 Fraunces 的友好感。
 *  - 其余(正文/家长):[FontFamily.Default](系统 sans,内含中文 fallback),近似 Nunito/Inter。
 * 若后续放入真实变体字体,只改此处即可。
 */
object LumiFonts {
    val KidsDisplay = FontFamily(Font(R.font.zcool_kuaile, FontWeight.Normal))
    val KidsBody = FontFamily.Default
    val ParentDisplay = FontFamily.Default
    val ParentBody = FontFamily.Default
}

/** 根据 mode 选 display 字体家族。 */
fun displayFamily(mode: LumiMode): FontFamily =
    if (mode == LumiMode.Child) LumiFonts.KidsDisplay else LumiFonts.ParentDisplay

fun bodyFamily(mode: LumiMode): FontFamily =
    if (mode == LumiMode.Child) LumiFonts.KidsBody else LumiFonts.ParentBody

private val lh = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** 标题/Display 文本样式,字号随 [Tier],字体随 [LumiMode]。 */
@Composable
fun lumiDisplayStyle(tier: Tier, mode: LumiMode): TextStyle = TextStyle(
    fontFamily = displayFamily(mode),
    fontWeight = FontWeight.Bold,
    fontSize = tier.fsDisplay,
    lineHeight = tier.fsDisplay * 1.15f,
    letterSpacing = (-0.01).em,
    lineHeightStyle = lh,
)

@Composable
fun lumiTitleStyle(tier: Tier, mode: LumiMode): TextStyle = TextStyle(
    fontFamily = displayFamily(mode),
    fontWeight = FontWeight.Bold,
    fontSize = tier.fsTitle,
    lineHeight = tier.fsTitle * 1.2f,
    lineHeightStyle = lh,
)

@Composable
fun lumiBodyStyle(tier: Tier, mode: LumiMode): TextStyle = TextStyle(
    fontFamily = bodyFamily(mode),
    fontWeight = FontWeight.Medium,
    fontSize = tier.fsBody,
    lineHeight = tier.fsBody * tier.lineHeight,
    lineHeightStyle = lh,
)

@Composable
fun lumiCaptionStyle(tier: Tier, mode: LumiMode): TextStyle = TextStyle(
    fontFamily = bodyFamily(mode),
    fontWeight = FontWeight.SemiBold,
    fontSize = tier.fsCaption,
    lineHeightStyle = lh,
)

/** 家长页 section 小标签:11sp 700 大写字距(tokens.json#typography.parent.section)。 */
val ParentSectionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 0.12.em,
)

/** 家长统计大数字:28sp 700 tabular。 */
val ParentStatStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    letterSpacing = (-0.02).em,
)
