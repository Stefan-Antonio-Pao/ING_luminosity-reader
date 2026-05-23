package com.lumiread.core.vision

import com.lumiread.core.ImageInput
import com.lumiread.core.Lang
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult

/**
 * 假 OCR:返回一段固定的"绘本文字",用于跑通 UI。
 * **运行时水印**:返回结果第一行带 [FAKE LINE] 前缀。
 */
class FakeOcrService(
    private val cannedText: String = "[FAKE LINE] The puppy plays in the sunny park.",
    private val confidence: Float = 0.95f,
    private val lang: Lang = Lang.EN,
) : OcrService {
    override suspend fun recognize(image: ImageInput): OcrResult =
        OcrResult(
            lines = listOf(OcrLine(cannedText, confidence)),
            detectedLang = lang,
        )
}
