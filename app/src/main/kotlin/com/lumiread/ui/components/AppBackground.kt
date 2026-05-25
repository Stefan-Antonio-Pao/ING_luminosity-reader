package com.lumiread.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lumiread.ui.theme.LocalLumiTokens

/**
 * App 级背景容器(part of the UI overhaul)。
 *
 * this iteration只**铺底色** `tokens.surfaceBg`(儿童 Cream / 家长白)。
 *
 * 设计规范提到"内容浅底 Cream + 卡面白 + 柔和阴影",§6.2 提到首页"柔和渐变背景"。
 * 渐变 / 星光装饰由 `tokens.decorDensity` 控制密度,推迟到后续版本(吉祥物 / Lottie / 庆祝动效一并)
 * —— 此处先把入口留好,避免逐屏接入时再来回改导入。
 *
 * 用法(接入后):
 * ```
 * AppBackground {
 * LumiReadApp
 * }
 * ```
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalLumiTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.surfaceBg),
        content = content,
    )
}
