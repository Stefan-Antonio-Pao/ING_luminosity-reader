package com.lumiread.vision

import android.util.Log
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.lumiread.core.ImageInput
import com.lumiread.core.Label
import com.lumiread.core.vision.ImageLabelService

/**
 * ML Kit 离线图像打标 —— 默认模型 400+ 标签。
 *
 * 置信度阈值取 0.5(略高于默认 0.5,保留宽泛但去掉噪声)。
 * 低风格化插画可能拿到"purple/round"而非具体物体——后续 可加自定义 TFLite 兜底。
 */
class MlKitImageLabelService : ImageLabelService {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    override suspend fun label(image: ImageInput, topK: Int): List<Label> {
        val input = InputImages.load(image)
        val raw = runCatching { labeler.process(input).await() }.getOrNull()
        if (raw == null) {
            Log.w(TAG, "image labeling 返回 null")
            return emptyList()
        }

        val labels = raw.sortedByDescending { it.confidence }
            .take(topK)
            .map { Label(name = it.text, confidence = it.confidence) }

        Log.i(TAG, "labels(top$topK)=${labels.joinToString { "${it.name}:${"%.2f".format(it.confidence)}" }}")
        return labels
    }

    companion object {
        private const val TAG = "MlKitImageLabelSvc"
    }
}
