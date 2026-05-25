package com.lumiread.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumiread.core.AgeBand

/**
 * 儿童模式年龄段微调(part of the UI overhaul)。
 *
 * **仅儿童模式生效**;家长模式 LumiTheme 忽略 ageBand 参数,值不变化。
 *
 * 设计纪律:
 * - **暖金 Action Gold 恒定为行动色** —— `primary` / `onPrimary` 三档相同
 * - **字体、装饰开关、墨色 ink 也恒定** —— fontFamily / mascotAlpha / decorDensity / ink 不变
 * - 仅主导面 / 氛围色 `brand` / `surfaceBg` 与"体感"维度(尺寸 / 字号 / 圆角 / 动效幅度)随龄变化
 *
 * 设计语言:
 * - **Toddler(1.5~3 岁)**:暖金奶白主导、最大触控 88dp、字号 36/22sp、bounce 0.12 最夸张
 * - **Preschool(3~5 岁)**:天空蓝品牌、72dp / 30/20sp / bounce 0.08 —— 与现有 ChildTokens 一致
 * - **Preadolescent(6~10 岁)**:深靛蓝收敛、60dp / 26/18sp / bounce 0.05
 *
 * 切换 ageBand 时令牌瞬时跳变(playfulness=1 时 lerp 输出 = childTokens 直接值);
 * mode 切换的 450ms 平滑过渡机制不受影响。
 */
fun childTokensFor(ageBand: AgeBand): LumiTokens = when (ageBand) {
    AgeBand.TODDLER -> ChildTokens.copy(
        // 暖橙金主导:呼应"暖金奶白为主"的环境氛围
        brand     = Color(0xFFFFA82E),   // 暖橙金:Toddler 氛围
        onBrand   = Color(0xFF102A5C),   // 暖底仍走墨色文字
        surfaceBg = Color(0xFFFFF5DC),   // 更奶白的内容浅底
        // 形状更圆胖
        cornerLarge  = 32.dp,
        cornerMedium = 24.dp,
        // 字号最大
        titleSp = 36.sp,
        bodySp  = 22.sp,
        // 触控最大
        minTouch = 88.dp,
        // 动效最夸张
        bounceDepth = 0.12f,
    )
    AgeBand.PRESCHOOL -> ChildTokens  // 现状即 Preschool 基线(天空蓝)
    AgeBand.PREADOLESCENT -> ChildTokens.copy(
        // 深靛蓝主导:略收敛、贴近"小学生稳重"气质
        brand     = Color(0xFF1E3A8A),   // 靛蓝
        surfaceBg = Color(0xFFF1F4FA),   // 略带蓝调的极浅底
        // 形状略收
        cornerLarge  = 24.dp,
        cornerMedium = 16.dp,
        // 字号略收
        titleSp = 26.sp,
        bodySp  = 18.sp,
        // 触控略收
        minTouch = 60.dp,
        // 动效收敛
        bounceDepth = 0.05f,
    )
}
