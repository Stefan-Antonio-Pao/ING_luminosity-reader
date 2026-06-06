package com.lumiread.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumiread.R
import com.lumiread.ui.components.LumiIcon
import com.lumiread.ui.components.LumiScreenBackground
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalMode
import com.lumiread.ui.theme.LocalReducedMotion
import com.lumiread.ui.theme.LocalTier
import com.lumiread.ui.theme.LumiColors
import com.lumiread.ui.theme.displayFamily

/**
 * 拍到啦 celebrate 过渡屏(screen 05)。中心 check + 标题,1100ms 后由调用方 nav 到 thinking。
 * reduced-motion 下静态展示(无缩放/无彩屑),其余有轻微 pop。
 */
@Composable
fun CelebrateScreen(modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    val tier = LocalTier.current
    val mode = LocalMode.current
    val reduced = LocalReducedMotion.current
    val scale = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "celebrate")
        val s by t.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "pulse",
        )
        s
    }
    LumiScreenBackground(modifier = modifier, decor = !reduced) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.illu_celebration_check),
                contentDescription = null,
                modifier = Modifier.size(140.dp).scale(scale),
            )
            Text(
                stringResource(tierRes(tier, R.string.lr_celebrate_toddler, R.string.lr_celebrate_preschool, R.string.lr_celebrate_pre)),
                color = palette.text,
                fontFamily = displayFamily(mode),
                fontWeight = FontWeight.Bold,
                fontSize = tier.fsDisplay,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.lr_celebrate_subtitle),
                color = palette.textSoft,
                fontSize = tier.fsBody,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * AI 阅读中 thinking 过渡屏(screen 06)。纯过渡:调用方监听首个 token,到达即 nav 到 dialog。
 * 三点呼吸动画;reduced-motion 下退化为静态文字 + 静态光晕。
 */
@Composable
fun ThinkingScreen(modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    val tier = LocalTier.current
    val mode = LocalMode.current
    val reduced = LocalReducedMotion.current
    LumiScreenBackground(modifier = modifier, decor = false) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.illu_thinking_glow),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Text(
                stringResource(tierRes(tier, R.string.lr_thinking_toddler, R.string.lr_thinking_preschool, R.string.lr_thinking_pre)),
                color = palette.text,
                fontFamily = displayFamily(mode),
                fontWeight = FontWeight.Bold,
                fontSize = tier.fsTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (!reduced) ThinkingDots() else Text("…", color = palette.textSoft, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.lr_thinking_privacy),
                color = palette.textMuted,
                fontSize = tier.fsCaption,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(720, delayMillis = i * 160, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(a)
                    .clip(CircleShape)
                    .background(LumiColors.Gold500),
            )
        }
    }
}
