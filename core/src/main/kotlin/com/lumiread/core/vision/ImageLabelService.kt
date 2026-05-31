package com.lumiread.core.vision

import com.lumiread.core.ImageInput
import com.lumiread.core.Label

/**
 * 离线图像打标抽象。
 *
 * Android 端用 ML Kit Image Labeling 默认模型,400+ 标签(FACTS.md#F3)。
 * 取前 3–5 标签拼进 Gemma 提示,给模型场景上下文。
 */
interface ImageLabelService {
    suspend fun label(image: ImageInput, topK: Int = 5): List<Label>
}
