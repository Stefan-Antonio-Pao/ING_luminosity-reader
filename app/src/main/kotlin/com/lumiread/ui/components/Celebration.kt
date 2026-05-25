package com.lumiread.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.lumiread.ui.motion.Motion
import com.lumiread.ui.theme.LocalLumiTokens
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 庆祝动效覆盖层(part of the UI overhaul)。
 *
 * 触发器(`trigger`)递增即触发一轮:N 颗星从屏幕中心向外飞,走 [Motion.PlayfulSpring]
 * (HighBouncy / StiffnessLow)—— 过冲明显、慢回弹,符合"庆祝/强调"语义。
 *
 * 视觉:
 * - 颜色用 `tokens.primary`(儿童态行动金 / 家长态 M3 紫),与按钮焦点色一致
 * - 透明度同步 `tokens.decorDensity`:家长 = 0(完全不可见,即便误触发也不打扰),儿童 = 1
 * - 8 颗五角星,随机角度 ±15° 抖动,随机大小 16–28dp
 *
 * 性能:不挡触摸(`Modifier.fillMaxSize` 的 Box 本身不接收事件;Canvas 也是被动绘制)。
 * 单次触发约 800ms 后所有 Animatable 静止,无持续 recompose 成本。
 *
 * 用法:`Celebration(trigger = captureCount)` —— 父屏维护一个 Int,需要庆祝时 `count++`。
 */
@Composable
fun Celebration(trigger: Int, particleCount: Int = 8) {
    val tokens = LocalLumiTokens.current
    if (tokens.decorDensity <= 0f) return  // 家长模式短路,Canvas 都不绘制

    val particles = remember(particleCount) {
        List(particleCount) { i ->
            Particle(
                angle = (i.toFloat() / particleCount) * 2f * Math.PI.toFloat() +
                    Random.nextFloat() * 0.3f - 0.15f,
                sizeDp = 16f + Random.nextFloat() * 12f,
                rotation = Random.nextFloat() * 360f,
                progress = Animatable(0f),
            )
        }
    }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        particles.forEach { p ->
            // 同时启动而不顺序启动:8 颗星齐发,氛围更"砰"
            launchParticle(p)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadius = minOf(size.width, size.height) * 0.42f

            particles.forEach { p ->
                val t = p.progress.value
                if (t <= 0f) return@forEach
                val r = maxRadius * t
                val x = cx + cos(p.angle) * r
                val y = cy + sin(p.angle) * r
                val alpha = (1f - t).coerceIn(0f, 1f) * tokens.decorDensity
                rotate(p.rotation + t * 180f, pivot = Offset(x, y)) {
                    drawStar(
                        center = Offset(x, y),
                        radius = p.sizeDp.dp.toPx(),
                        color = tokens.primary.copy(alpha = alpha),
                    )
                }
            }
        }
    }
}

private data class Particle(
    val angle: Float,
    val sizeDp: Float,
    val rotation: Float,
    val progress: Animatable<Float, *>,
)

private suspend fun launchParticle(p: Particle) {
    p.progress.snapTo(0f)
    p.progress.animateTo(targetValue = 1f, animationSpec = Motion.playfulSpring())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color,
    points: Int = 5,
) {
    val outer = radius
    val inner = radius * 0.45f
    val path = Path()
    val angleStep = Math.PI.toFloat() / points
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val theta = -Math.PI.toFloat() / 2f + i * angleStep
        val x = center.x + cos(theta) * r
        val y = center.y + sin(theta) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}
