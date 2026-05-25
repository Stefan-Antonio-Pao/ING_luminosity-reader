package com.lumiread.core.model

/**
 * 决定 `.litertlm` 权重文件从哪来。
 *
 * - [Strategy.EXTERNAL_DOWNLOAD]:默认。首启检测应用私有外部目录,缺则从 [downloadUrl] 流式下载,
 * 带进度、断点续传、SHA-256 校验。
 * - [Strategy.ADB_PUSHED]:评委兜底。要求文件已被 `adb push` 到应用私有外部目录。
 * - [Strategy.BUNDLED_ASSET]:警告。把 2.59 GB 塞 assets/。仅当确保演示设备空间充足时用。
 *
 * 真实 Android 端实现位于 :app(需要 Context)。
 */
interface ModelProvider {
    enum class Strategy { EXTERNAL_DOWNLOAD, ADB_PUSHED, BUNDLED_ASSET }

    val strategy: Strategy

    /** SHA-256 期望值,用于校验。来自 设计规范#A8 —— 由托管方提供。 */
    val expectedSha256: String?

    /** 仅在 [Strategy.EXTERNAL_DOWNLOAD] 下使用。 */
    val downloadUrl: String?

    /**
     * 阻塞地拿到本地文件绝对路径;若未就位则触发下载/检查。
     * @return 真实文件路径(可直接传给 LiteRT-LM 的 `EngineConfig.modelPath`)。
     */
    suspend fun ensureLocal(progress: (bytesRead: Long, totalBytes: Long) -> Unit): String
}
