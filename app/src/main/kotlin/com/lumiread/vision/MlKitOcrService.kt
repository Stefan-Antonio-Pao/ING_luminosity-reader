package com.lumiread.vision

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lumiread.core.ImageInput
import com.lumiread.core.Lang
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult
import com.lumiread.core.vision.OcrService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * ML Kit 离线 OCR。同时跑拉丁 + 中文两个识别器(ML Kit 无"通吃"模型),
 * 再用 language-id 判定 OCR 出来的文本主语种。
 *
 * 路由策略:**两个识别器并发跑,谁产出的文本长度更长就用谁**。
 * 理由:中文识别器在纯拉丁页面常返回断字 / 乱码 / 空串,反之亦然——长度差异通常足以路由。
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
            .map { OcrLine(text = it.text, confidence = it.lineConfidence()) }

        Log.i(TAG, "OCR 取胜识别器=${if (picked === latinText) "latin" else "chinese"} "
                + "lines=${lines.size} lang=$detectedLang")

        OcrResult(lines = lines, detectedLang = detectedLang)
    }

    private fun pickBetter(a: Text?, b: Text?): Text? = when {
        a == null && b == null -> null
        a == null              -> b
        b == null              -> a
        a.text.length >= b.text.length -> a
        else -> b
    }

    /**
     * ML Kit `Text.Line` 没有可靠的整行置信度字段(`Symbol#getConfidence` 在中文路径上常为 null)。
     * 这里取所有 element 平均;全 null 时退回 1f,避免下游误判。
     */
    private fun Text.Line.lineConfidence(): Float {
        val confidences = elements.mapNotNull { it.confidence }
        return if (confidences.isEmpty()) 1f else confidences.average().toFloat()
    }

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
    }
}
