package com.lumiread.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 圆角刻度,来自 `design-system/tokens.json#shape.radius`。
 * 卡片/按钮的实际圆角随 [Tier.radius] 变化;这里是固定语义刻度,供装饰、药丸、缩略图等使用。
 */
object LumiRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val xxl = 36.dp
    val pill = 999.dp
}

/** spacing 刻度,来自 tokens.json#spacing。 */
object LumiSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s7 = 32.dp
    val s8 = 40.dp
    val s9 = 48.dp
    val s10 = 64.dp
}
