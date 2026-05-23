package com.lumiread.core.vision

import com.lumiread.core.ImageInput
import com.lumiread.core.OcrResult

/**
 * 离线 OCR 服务抽象。
 *
 * Android 端用 ML Kit 拉丁 + 中文 + language-id 路由。
 * core 模块保持 Android-free,因此真实实现位于 :app。
 */
interface OcrService {
    suspend fun recognize(image: ImageInput): OcrResult
}
