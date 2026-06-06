package com.lumiread

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.lumiread.ui.LumiReadApp

/**
 * App 入口。Phase 3 起:`onCreate` 第一件事调用 [AppGraph.init],把 [Application]
 * 注入 [com.lumiread.llm.Gemma4Engine](LiteRT-LM 需 Context 拿 `getExternalFilesDir`)。
 *
 * 不在这里触发 `warmUp()` —— Gemma 初始化可达 ~10 s,放 `onCreate` 会阻塞冷启动。
 * 首次 `generate()` 时由 Pipeline 异步触发,UI 侧已有"等待中"占位。
 *
 * Phase 5.1:`enableEdgeToEdge()` 必须放在 `super.onCreate` 之前(官方文档要求),
 * 让窗口绘到系统栏底下,顶部状态栏不再被状态栏盖住;具体内边距由 LumiReadApp 用
 * `statusBarsPadding()` / `navigationBarsPadding()` / `imePadding()` 在 Compose 侧消化。
 *
 * v1.1 步骤二:继承 [AppCompatActivity] 而非 [ComponentActivity],
 * 让 `AppCompatDelegate.setApplicationLocales` 在 API 26-32 也能通过
 * `AppLocalesMetadataHolderService`(见 Manifest)落地。AppCompatActivity 本身仍是
 * ComponentActivity 子类,setContent / Compose 栈不受影响。
 * 主题切到 `Theme.AppCompat.DayNight.NoActionBar`(在 Manifest)—— 全 Compose UI,
 * 不用系统 ActionBar / View,所以 AppCompat 主题只起"提供窗口背景 + DayNight 配置"的作用。
 *
 * UI 改造步骤一(2026-05-25):用 [LumiTheme] 取代原私有 `LumiReadTheme`。
 * 当前是骨架版,只按 mode 选 tokens + 套 MaterialTheme,**视觉与改造前完全一致**。
 * 步骤二会引入 playfulness 动画;步骤三起 ChildTokens 才会落地卡通值。
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(application)
        setContent {
            // v3.0.0:主题 mode 跟随导航(家长区=Parent 调色板),由 LumiReadApp 内部用 LumiTheme 包裹,
            // 因此这里不再外套一层 LumiTheme;每个屏自带 LumiScreenBackground 铺底。
            LumiReadApp()
        }
    }
}
