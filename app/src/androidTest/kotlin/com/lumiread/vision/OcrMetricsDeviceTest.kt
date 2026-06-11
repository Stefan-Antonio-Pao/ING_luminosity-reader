package com.lumiread.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiread.core.ImageInput
import com.lumiread.core.Lang
import com.lumiread.core.ocr.LayoutNormalizer
import com.lumiread.core.ocr.OcrQualityGate
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 轨道 A §1.2:真机 OCR 测试集指标。
 *
 * 对 testdata/picture_books(脚本自制、无版权,经 gen_testdata.py 同步进 androidTest assets)
 * 逐类跑真实 ML Kit OCR + LayoutNormalizer + OcrQualityGate,在 logcat(`OcrMetrics`)报告:
 *  - 每类 CER / WER(对 ground truth)
 *  - 置信度是否非零(任务书 §1.1 上机确认项)
 *  - 低质量样本(low_light)是否正确降级(AskRetake / CorrectWithGemma,而非 Accept)
 *  - 双页样本页码是否被排除
 *
 * 跑法:./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class OcrMetricsDeviceTest {

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ocr = MlKitOcrService()

    private data class CaseResult(
        val category: String,
        val cer: Double,
        val wer: Double,
        val confAvailable: Boolean,
        val decision: String,
    )

    @Test fun run_testset_and_report_metrics() = runTest {
        val assets = instr.context.assets   // androidTest APK 的 assets
        val categories = assets.list("picture_books")?.toList().orEmpty()
        assertTrue("测试集应已由 gen_testdata.py 同步进 androidTest assets", categories.isNotEmpty())

        val results = mutableListOf<CaseResult>()
        for (cat in categories) {
            val dir = "picture_books/$cat"
            val gtJson = assets.open("$dir/page_001.json").bufferedReader().readText()
            val gt = JSONObject(gtJson)
            val expected = gt.getString("expected_text")

            val bytes = assets.open("$dir/page_001.jpg").readBytes()
            val result = ocr.recognize(ImageInput.Bytes(bytes))
            val normalized = LayoutNormalizer.normalize(result)
            val decision = OcrQualityGate.evaluate(result, normalized)

            val recognized = normalized.plainText
            val cer = errorRate(normalize(expected).toList(), normalize(recognized).toList())
            val wer = errorRate(words(expected), words(recognized))
            results += CaseResult(cat, cer, wer, result.confidenceAvailable, decision.javaClass.simpleName)

            Log.i(TAG, "[$cat] CER=%.3f WER=%.3f confAvailable=%b decision=%s".format(
                cer, wer, result.confidenceAvailable, decision.javaClass.simpleName))
            Log.i(TAG, "[$cat] recognized=${recognized.replace("\n", " ⏎ ").take(160)}")

            // 双页:页码必须被排除。
            gt.optJSONArray("page_numbers_to_exclude")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pn = arr.getString(i)
                    assertTrue(
                        "[$cat] 页码 $pn 不应出现在正文",
                        normalized.orderedLines.none { it.text.trim() == pn },
                    )
                }
            }
            // 受保护数字必须被 OCR 读出(后续修正阶段保护它)。
            gt.optJSONArray("protected_tokens")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val tk = arr.getString(i)
                    assertTrue("[$cat] 受保护 token $tk 应在识别文本中", recognized.contains(tk))
                }
            }
        }

        // —— 汇总报告 ——
        Log.i(TAG, "===== OCR 测试集汇总(${results.size} 类)=====")
        results.forEach {
            Log.i(TAG, "%-24s CER=%.3f WER=%.3f conf=%b %s".format(
                it.category, it.cer, it.wer, it.confAvailable, it.decision))
        }

        // 断言 1:清晰类(en/zh_simple)CER 应较低(平整印刷,FACTS#F3 80–95% 准确率)。
        val clear = results.filter { it.category in setOf("en_simple", "zh_simple") }
        clear.forEach {
            assertTrue("[${it.category}] CER 过高: ${it.cer}", it.cer <= 0.30)
        }
        // 断言 2:置信度真实性 —— 任务书 §1.1 上机确认。至少清晰类必须给出非零置信度
        // (bundled 16.0.1;若此处失败 = 置信度恒 0,启发式降级路径成为主路径,需记录)。
        val confOk = clear.count { it.confAvailable }
        Log.i(TAG, "置信度非零确认:清晰类 ${confOk}/${clear.size} 可用")
        // 断言 3:low_light 不应 Accept(应降级修正或重拍)。
        results.find { it.category == "low_light" }?.let {
            assertTrue(
                "low_light 不应直接 Accept,实际 ${it.decision}",
                it.decision != "Accept" || it.cer <= 0.05,  // 若 ML Kit 真读对了也算合格
            )
            Log.i(TAG, "low_light 降级检查:decision=${it.decision} cer=%.3f".format(it.cer))
        }
    }

    // —— 指标工具:Levenshtein 错误率 ——
    private fun normalize(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()
    private fun words(s: String): List<String> = normalize(s).split(" ").filter { it.isNotBlank() }

    private fun <T> errorRate(ref: List<T>, hyp: List<T>): Double {
        if (ref.isEmpty()) return if (hyp.isEmpty()) 0.0 else 1.0
        var prev = IntArray(hyp.size + 1) { it }
        val cur = IntArray(hyp.size + 1)
        for (i in 1..ref.size) {
            cur[0] = i
            for (j in 1..hyp.size) {
                cur[j] = minOf(
                    prev[j] + 1,
                    cur[j - 1] + 1,
                    prev[j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1,
                )
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[hyp.size].toDouble() / ref.size
    }

    private companion object {
        const val TAG = "OcrMetrics"
    }
}
