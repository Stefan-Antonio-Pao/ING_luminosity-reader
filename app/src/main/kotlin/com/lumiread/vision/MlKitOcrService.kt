package com.lumiread.vision

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lumiread.core.ImageInput
import com.lumiread.core.Lang
import com.lumiread.core.OcrBox
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrPoint
import com.lumiread.core.OcrResult
import com.lumiread.core.vision.OcrService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * ML Kit 离线 OCR。同时跑拉丁 + 中文两个识别器(无"通吃"模型,见 FACTS#F3),
 * 再用 language-id 判定 OCR 出来的文本主语种。
 *
 * 路由策略:**两个识别器并发跑,谁产出的文本长度更长就用谁**。
 * 理由:中文识别器在纯拉丁页面常返回断字 / 乱码 / 空串,反之亦然——长度差异通常足以路由。
 * 极端边界(都很短、都很长且语种混排)留到 Phase 6 调,目前 demo 够用。
 *
 * 依赖坐标来源:FACTS.md#F3,2026-05-23 核对。
 */
class MlKitOcrService : OcrService {

    private val latin    = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chinese  = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val langId   = LanguageIdentification.getClient()

    override suspend fun recognize(image: ImageInput): OcrResult = coroutineScope {
        val input = InputImages.load(image)

        val latinDef   = async { runCatching { latin.process(input).await() }.getOrNull() }
        val chineseDef = async { runCatching { chinese.process(input).await() }.getOrNull() }

        val latinText   = latinDef.await()
        val chineseText = chineseDef.await()

        val picked = pickBetter(latinText, chineseText)
        if (picked == null || picked.text.isBlank()) {
            Log.i(TAG, "OCR 返回空文本")
            return@coroutineScope OcrResult(lines = emptyList(), detectedLang = null)
        }

        val detectedLang = identifyLang(picked.text)
        val lines = picked.textBlocks
            .flatMap { it.lines }
            .map { it.toRichOcrLine() }

        // 置信度真实性(任务书 §1.1 ⚠ / FACTS#F12):非捆绑模型/旧 Play 服务下 getConfidence()
        // 恒 0。所有行都 ≈0 时标记不可用,让 OcrQualityGate 退回启发式信号,不污染下游判断。
        val confidenceAvailable = lines.any { it.confidence > CONFIDENCE_EPSILON }
        if (!confidenceAvailable && lines.isNotEmpty()) {
            Log.w(TAG, "OCR 置信度全为 0 —— 标记 confidenceAvailable=false,质量门控将走启发式")
        }

        Log.i(TAG, "OCR 取胜识别器=${if (picked === latinText) "latin" else "chinese"} "
                + "lines=${lines.size} lang=$detectedLang confAvailable=$confidenceAvailable")

        OcrResult(
            lines = lines,
            detectedLang = detectedLang,
            imageWidth = input.width,
            imageHeight = input.height,
            confidenceAvailable = confidenceAvailable,
        )
    }

    private fun pickBetter(a: Text?, b: Text?): Text? = when {
        a == null && b == null -> null
        a == null              -> b
        b == null              -> a
        a.text.length >= b.text.length -> a
        else -> b
    }

    /**
     * 富结构映射(轨道 A,FACTS#F12 javap 核对 2026-06-11):
     * `Text.Line.getConfidence()` 直接存在(基本 float,0~1);`getBoundingBox()`/`getCornerPoints()`
     * 可空必须判空;`getAngle()` 基本 float;`getRecognizedLanguage()` 返回 BCP-47("und"=未知)。
     *
     * 旧实现"全 null 退回 1f"会污染质量门控(把'置信度缺失'伪装成'非常好'),已移除——
     * 置信度恒 0 的真实场景交由 [OcrResult.confidenceAvailable] 显式表达。
     */
    private fun Text.Line.toRichOcrLine(): OcrLine = OcrLine(
        text = text,
        confidence = confidence,
        box = boundingBox?.let { OcrBox(it.left, it.top, it.right, it.bottom) },
        cornerPoints = cornerPoints?.map { OcrPoint(it.x, it.y) } ?: emptyList(),
        angle = angle,
        recognizedLanguage = recognizedLanguage.takeIf { it.isNotBlank() && it != "und" },
    )

    private suspend fun identifyLang(text: String): Lang? {
        val tag = runCatching { langId.identifyLanguage(text).await() }.getOrNull() ?: return null
        return when {
            tag.startsWith("zh") -> Lang.ZH
            tag.startsWith("en") -> Lang.EN
            tag == "und"         -> null
            else                 -> null
        }
    }

    companion object {
        private const val TAG = "MlKitOcrService"
        /** 行置信度低于此值视为"等于 0"(非捆绑模型/旧 Play 服务的全 0 场景判定)。 */
        private const val CONFIDENCE_EPSILON = 0.001f
    }
}
