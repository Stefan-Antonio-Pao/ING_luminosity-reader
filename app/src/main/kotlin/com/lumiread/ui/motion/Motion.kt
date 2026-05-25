package com.lumiread.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 命名弹簧预设(part of the UI overhaul)。
 *
 * 核心理念:**主要交互一律用 spring,禁止线性 tween**。
 * 预备 → 挤压拉伸 → 超调回弹 是儿童感的来源。
 *
 * - [PressSpring](MediumBouncy / StiffnessMedium):按下回弹,响应快、回弹明显但不过冲
 * - [PopInSpring](LowBouncy / StiffnessMediumLow):柔和登场,首帧从小放大轻微超调
 * - [PlayfulSpring](HighBouncy / StiffnessLow):庆祝/强调,过冲明显、慢回弹
 *
 * Float 型预设已经实例化(给 `animateFloatAsState` / `Animatable<Float>` 直接用)。
 * Dp / TextUnit 等其它类型需要时调用泛型工厂([pressSpring] 等)就地构造。
 *
 * 家长模式默认 `bounceDepth = 0`、`decorDensity = 0`,组件读到 0 自然不动 ——
 * **本文件无任何模式判断**,弹簧参数对两种模式都生效,只是儿童模式才有可见振幅。
 */
object Motion {
    val PressSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val PopInSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val PlayfulSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessLow,
    )

    fun <T> pressSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> popInSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> playfulSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessLow,
    )
}
