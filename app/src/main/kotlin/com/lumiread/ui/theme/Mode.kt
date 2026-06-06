package com.lumiread.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前 UI 模式的 CompositionLocal。[LumiMode] 枚举本身定义在 `LumiMode.kt`。
 * 新 v3 页面用它来在 Kids / Parent 视觉之间分支(配色由 [LocalLumiPalette] 给出,
 * 此处仅用于少数需要直接判断模式的逻辑,如装饰开关、字体家族选择)。
 */
val LocalMode = staticCompositionLocalOf { LumiMode.Child }
