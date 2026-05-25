package com.lumiread.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumiread.R

/**
 * 儿童模式令牌(UI overhaul,2026-05-25)。
 *
 * 配色 / 形状 / 尺寸全部取自 UI overhaul 设计稿真值;字体族(ZCOOL KuaiLe)的许可、下载、
 * 致谢都已记入 THIRD_PARTY_NOTICES.md。
 *
 * 配色 —— **暖金 + 沉静深蓝**,承接 App 图标"夜空发光的星照着翻开的书"意象。
 * 纪律:深蓝管沉静、暖金管点亮、浅底保通透;每屏一个主导面 + 暖金焦点,避免七彩平均堆砌。
 *
 * 这些值在 ChildAgeBandTokens 中按年龄段微调(主按钮触控目标、回弹幅度、装饰密度);
 * [LumiTheme] 在 mode == Child 时把它们注入 [LocalLumiTokens]。
 */
val ChildTokens = LumiTokens(
    // 颜色族(§5.2)
    primary = Color(0xFFF6C445),       // 行动金:按钮填充(略加饱和保文字可读)
    onPrimary = Color(0xFF102A5C),     // 墨色 Ink:暖金底上的文字
    brand = Color(0xFF0A57ED),         // 主品牌蓝:沉浸背景 / 标题块
    onBrand = Color(0xFFFFFFFF),       // 卡面白:主品牌蓝上的文字
    surfaceBg = Color(0xFFFFF7EC),     // Cream:内容浅底
    ink = Color(0xFF102A5C),           // 墨色 Ink:正文

    // 形状(§5.3)杜绝尖角
    cornerLarge = 28.dp,                // 卡片 24–32dp,取中位
    cornerMedium = 20.dp,               // 按钮圆角(胶囊感)

    // 字号(§5.3)圆润大字
    titleSp = 30.sp,                    // 标题
    bodySp = 20.sp,                     // 正文 ≥18sp

    // 尺寸(§5.3)
    minTouch = 72.dp,                   // 主按钮 ≥72dp(Toddler 档在 ChildAgeBandTokens 中放大到 ≥88dp)

    // 动效预算
    bounceDepth = 0.08f,                // 按下缩到 0.92

    // 装饰开关
    mascotAlpha = 1f,                   // 吉祥物可见
    decorDensity = 1f,                  // 背景装饰开启

    // 字体(2026-05-25)
    // ZCOOL KuaiLe / 站酷快乐体:SIL OFL 1.1,圆胖儿童 Comic 风 Heavy weight,
    // 单字体覆盖 Latin + Simplified Chinese,无需 fallback 链。
    // 来源:https://github.com/google/fonts/tree/main/ofl/zcoolkuaile
    // 文件:res/font/zcool_kuaile.ttf
    // 致谢见 THIRD_PARTY_NOTICES.md
    fontFamily = FontFamily(Font(R.font.zcool_kuaile, FontWeight.Black)),
    fontWeight = FontWeight.Black,
)
