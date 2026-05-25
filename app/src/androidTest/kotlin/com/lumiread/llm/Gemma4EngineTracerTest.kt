package com.lumiread.llm

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 集成冒烟测试:在真机上跑一次 Gemma4Engine,断言"流非空 + 首词延迟 + 已知后端"。
 *
 * 跑法:
 * 1. 先 push 模型(用户必做):
 * adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.lumiread/files/
 * 2. ./gradlew :app:connectedDebugAndroidTest
 *
 * 断言策略(LLM 输出不确定,只验**结构性**属性,不验逐字):
 * - 流非空
 * - 首词延迟 ≤ 6000 ms(目标基线;PKJ110 不是 Pixel 8,**实测数字也写 Log**,
 * 由人工评估是否接受)
 * - `activeBackend` 已知(GPU 或 CPU)
 *
 * Log 输出格式:
 * SMOKE: first-token=Xms, backend=Y, out.len=Z, total=Tms
 */
@RunWith(AndroidJUnit4::class)
class Gemma4EngineTracerTest {

    @Test
    fun gemma_streams_nonEmpty_with_known_backend() = runBlocking {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = Gemma4Engine(ctx)

        val out = StringBuilder()
        val t0 = System.currentTimeMillis()
        var firstTokenMs: Long = -1L
        // 在 close 之前 snapshot 后端名,避免后续断言读到 "closed"。
        var snapshotBackend = "<not-captured>"

        try {
            // 固定英文 prompt,降低不确定性;避免触碰中文 OCR / 分句路径
            engine.generate(
                "You are a warm tutor. Ask a 5-year-old ONE short open question about a puppy in a park."
            ).collect { chunk ->
                if (firstTokenMs < 0) {
                    firstTokenMs = System.currentTimeMillis() - t0
                    snapshotBackend = engine.activeBackendName()
                }
                out.append(chunk)
            }
        } finally {
            engine.close()
        }

        val totalMs = System.currentTimeMillis() - t0
        val report = "first-token=${firstTokenMs}ms, backend=$snapshotBackend," +
            " out.len=${out.length}, total=${totalMs}ms"
        Log.i("TRACER", report)
        // System.out 也写一份,JUnit 报告能拿到
        println("TRACER: $report")
        println("TRACER OUTPUT: $out")

        // 结构性断言
        assertTrue("流应非空,实际 length=${out.length}", out.isNotEmpty())
        assertTrue("应记录到首词时间,实际=$firstTokenMs", firstTokenMs > 0)
        assertTrue(
            "首词应 ≤ 6 s(性能基线),实测 ${firstTokenMs}ms 后端=$snapshotBackend",
            firstTokenMs <= 6_000L,
        )
        assertTrue(
            "后端应为 GPU 或 CPU,实际=$snapshotBackend",
            snapshotBackend in setOf("GPU", "CPU"),
        )
    }
}
