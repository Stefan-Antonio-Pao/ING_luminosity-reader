package com.lumiread.llm

import android.content.Context
import java.io.File

/**
 * Gemma 4 模型文件定位。
 *
 * 当前只实现 `ADB_PUSHED` 策略:由用户手动把 ~2.59 GB 的
 * `gemma-4-E2B-it.litertlm` push 到应用专属外部目录:
 *
 *   `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.lumiread/files/`
 *
 * 选 `getExternalFilesDir(null)` 而不是 `filesDir` 的原因:
 *   1. 应用专属、卸载自动清理,无需 storage 权限(Android 10+ scoped storage 友好)
 *   2. 容量更大,2.59 GB 不会塞爆内存盘
 *   3. adb push 路径稳定,易复现
 */
object ModelProvider {
    private const val FILE_NAME = "gemma-4-E2B-it.litertlm"

    /** 期望文件大小约 2.59 GB,允许 ±10% 容差(Hugging Face 镜像可能微差)。 */
    private const val EXPECTED_SIZE_BYTES = 2_590_000_000L

    /**
     * 返回模型文件的绝对路径,或失败原因。
     *
     * 失败分两种,UI 文案不同:
     *  - [ModelMissingException]:文件不存在 → 引导用户 adb push
     *  - [ModelCorruptedException]:文件大小异常 → 引导用户重 push(可能传输中断)
     */
    fun locate(context: Context): Result<String> {
        val dir = context.getExternalFilesDir(null)
            ?: return Result.failure(
                IllegalStateException("getExternalFilesDir(null) 返回 null —— 外部存储不可用?")
            )
        val f = File(dir, FILE_NAME)
        if (!f.exists()) {
            return Result.failure(
                ModelMissingException(
                    "未找到 $FILE_NAME。请执行:\n" +
                        "  adb push <local-path>/$FILE_NAME ${dir.absolutePath}/",
                )
            )
        }
        // 容差 10%:HF 镜像或不同量化分支可能微差,但小于 90% 几乎肯定是没传完
        if (f.length() < EXPECTED_SIZE_BYTES * 9 / 10) {
            return Result.failure(
                ModelCorruptedException(
                    "$FILE_NAME 大小异常:${f.length()} bytes,期望约 2.59 GB。可能 adb push 中断,请重 push。"
                )
            )
        }
        return Result.success(f.absolutePath)
    }
}

class ModelMissingException(message: String) : RuntimeException(message)
class ModelCorruptedException(message: String) : RuntimeException(message)
