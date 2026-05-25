package com.lumiread.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lumiread.core.GemmaModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 模型文件定位与管理。
 *
 * v1.1(2026-05-25)双模型重构:从单一文件常量改为按 [GemmaModel] 参数化。
 *
 * 文件位置统一在 `getExternalFilesDir(null)`(应用专属外部目录):
 * 1. 应用卸载自动清理,无需 storage 权限(Android 10+ scoped storage 友好)
 * 2. 容量足够装 6+ GB(E2B 2.59 + E4B 3.66)
 * 3. adb push 路径稳定:`adb push <file> /sdcard/Android/data/com.lumiread/files/`
 *
 * 三种安装方式(全部落到同一目录,App 不区分):
 * - **adb push**(评委/开发):命令行直推到 [targetDir]
 * - **HF 跳转 + SAF 导入**(普通用户):浏览器接受 Gemma 许可 → 下载到手机 → 用 [importFromUri] 拷贝
 * - 未来 `EXTERNAL_DOWNLOAD`(自建托管 URL,当前未实现)留接口位
 */
object ModelProvider {
    private const val TAG = "ModelProvider"

    /** 文件大小校验容差:小于 90% 视为传输中断/损坏(adb push / SAF 拷贝都可能中断)。 */
    private const val SIZE_TOLERANCE_RATIO = 0.9

    /** 模型文件存放目录。所有模型共用此目录,文件名靠 [GemmaModel.fileName] 区分。 */
    fun targetDir(context: Context): File =
        context.getExternalFilesDir(null)
            ?: error("getExternalFilesDir(null) 返回 null —— 外部存储不可用?")

    /**
     * 返回指定模型文件的绝对路径,或失败原因。
     *
     * 失败分两种,UI 文案不同:
     * - [ModelMissingException]:文件不存在 → 引导用户去 HF 下载或 adb push
     * - [ModelCorruptedException]:文件大小异常 → 引导用户重传(可能传输中断)
     */
    fun locate(context: Context, model: GemmaModel): Result<String> {
        val dir = runCatching { targetDir(context) }.getOrElse { return Result.failure(it) }
        val f = File(dir, model.fileName)
        if (!f.exists()) {
            return Result.failure(
                ModelMissingException(
                    "未找到 ${model.fileName}。请去设置页选择模型并导入,或执行:\n" +
                        "  adb push <local-path>/${model.fileName} ${dir.absolutePath}/",
                )
            )
        }
        val minBytes = (model.expectedSizeBytes * SIZE_TOLERANCE_RATIO).toLong()
        if (f.length() < minBytes) {
            return Result.failure(
                ModelCorruptedException(
                    "${model.fileName} 大小异常:${f.length()} bytes,期望约 ${model.expectedSizeBytes / 1_000_000_000.0} GB。可能传输中断,请重传。"
                )
            )
        }
        return Result.success(f.absolutePath)
    }

    /** 检测某模型是否已就位(文件存在且大小通过容差)。UI 状态徽章用此判断。 */
    fun isAvailable(context: Context, model: GemmaModel): Boolean =
        locate(context, model).isSuccess

    /** 列出当前已安装的模型集合。设置页"模型卡片"渲染用。 */
    fun installedModels(context: Context): Set<GemmaModel> =
        GemmaModel.entries.filterTo(mutableSetOf()) { isAvailable(context, it) }

    /**
     * 删除指定模型的本地文件。
     *
     * 用户从设置页"删除"按钮触发。注意 **AppGraph 上层需要先 close 引擎**,否则正在使用的模型
     * 文件可能仍被 mmap;尤其 currentModel == model 时,UI 应强制结束会话再调本函数。
     *
     * @return 文件不存在时返回 false(无操作);删除失败返回 false 并打日志;成功返回 true。
     */
    fun delete(context: Context, model: GemmaModel): Boolean {
        val dir = runCatching { targetDir(context) }.getOrElse { return false }
        val f = File(dir, model.fileName)
        if (!f.exists()) return false
        val ok = runCatching { f.delete() }.getOrDefault(false)
        if (!ok) Log.w(TAG, "删除失败:${f.absolutePath}(可能正在被 mmap)")
        else Log.i(TAG, "已删除 ${model.fileName}")
        return ok
    }

    /**
     * 从 SAF Uri 拷贝模型文件到目标目录。用户在 HF 下载完成后,通过系统文件选择器选中文件,
     * 我们用 `ContentResolver.openInputStream(src)` 流式读 + `FileOutputStream` 写。
     *
     * 进度通过 [onProgress]([bytesCopied], [totalBytes]) 回调;totalBytes 可能为 -1 表示未知大小。
     *
     * **不做**校验文件名是否与 [model.fileName] 匹配 —— 用户可能从浏览器另存为别的名字;只信任他们
     * "我下载的是 E4B 文件,导入到 E4B 槽"的选择,落地后大小校验由 [locate] 把关。
     *
     * 失败处理:中途任何异常都会删除部分写入的目标文件,避免下次启动被识别为损坏。
     */
    suspend fun importFromUri(
        context: Context,
        model: GemmaModel,
        src: Uri,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<String> = withContext(Dispatchers.IO) {
        val dir = runCatching { targetDir(context) }.getOrElse { return@withContext Result.failure(it) }
        val target = File(dir, model.fileName)
        val tmp = File(dir, "${model.fileName}.importing")
        // 估算总大小:ContentResolver 的 query 可能没大小列,fallback -1
        val totalBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(src, "r")?.use { it.length }
        }.getOrNull() ?: -1L

        runCatching {
            context.contentResolver.openInputStream(src)
                ?: error("ContentResolver 无法打开输入流:$src")
        }.fold(
            onSuccess = { input ->
                try {
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(1 shl 16)   // 64 KB
                        var copied = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            copied += n
                            onProgress(copied, totalBytes)
                        }
                        out.fd.sync()
                    }
                    if (target.exists()) target.delete()
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        return@withContext Result.failure(
                            IllegalStateException("无法把临时文件重命名为 ${model.fileName}")
                        )
                    }
                    // 二次校验:落地后查一遍大小
                    val verify = locate(context, model)
                    verify.fold(
                        onSuccess = { Result.success(it) },
                        onFailure = {
                            target.delete()
                            Result.failure(it)
                        }
                    )
                } catch (t: Throwable) {
                    tmp.delete()
                    Result.failure(t)
                } finally {
                    runCatching { input.close() }
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
}

class ModelMissingException(message: String) : RuntimeException(message)
class ModelCorruptedException(message: String) : RuntimeException(message)
