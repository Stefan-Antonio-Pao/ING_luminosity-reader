package com.lumiread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lumiread.ui.LumiReadApp

/**
 * App 入口。`onCreate` 第一件事调用 [AppGraph.init],把 [Application]
 * 注入 [com.lumiread.llm.Gemma4Engine](LiteRT-LM 需 Context 拿 `getExternalFilesDir`)。
 *
 * 不在这里触发 `warmUp()` —— Gemma 初始化可达 ~10 s,放 `onCreate` 会阻塞冷启动。
 * 首次 `generate()` 时由 Pipeline 异步触发,UI 侧已有"等待中"占位。
 *
 * `enableEdgeToEdge()` 必须放在 `super.onCreate` 之前(官方文档要求),
 * 让窗口绘到系统栏底下,顶部状态栏不再压住聊天头部。具体内边距由 LumiReadApp 用
 * `statusBarsPadding()` / `navigationBarsPadding()` / `imePadding()` 在 Compose 侧消化。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(application)
        setContent {
            LumiReadTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LumiReadApp()
                }
            }
        }
    }
}

@Composable
private fun LumiReadTheme(content: @Composable () -> Unit) {
    // 暂用 Material3 默认配色。
    MaterialTheme(content = content)
}
