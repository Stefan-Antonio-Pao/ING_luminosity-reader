package com.lumiread.core.vision

import com.lumiread.core.ImageInput
import com.lumiread.core.Label

/**
 * 假 ImageLabel:返回固定 5 个标签。
 * **运行时水印**(CLAUDE.md §C5):第一个标签名带 [FAKE] 前缀。
 */
class FakeImageLabelService(
    private val canned: List<Label> = listOf(
        Label("[FAKE] dog",   0.93f),
        Label("ball",         0.81f),
        Label("garden",       0.74f),
        Label("sunshine",     0.66f),
        Label("child",        0.55f),
    ),
) : ImageLabelService {
    override suspend fun label(image: ImageInput, topK: Int): List<Label> =
        canned.take(topK)
}
