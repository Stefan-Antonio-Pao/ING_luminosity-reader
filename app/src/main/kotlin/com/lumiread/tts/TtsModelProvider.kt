package com.lumiread.tts

import android.content.Context
import java.io.File

/**
 * TTS 模型文件定位。
 *
 * 走 ADB_PUSHED:由用户手动把 `vits-melo-tts-zh_en` 的文件**平铺**推到应用专属外部目录:
 *
 *   adb push <local>/vits-melo-tts-zh_en/model.onnx \
 *            <local>/vits-melo-tts-zh_en/lexicon.txt \
 *            <local>/vits-melo-tts-zh_en/tokens.txt \
 *            <local>/vits-melo-tts-zh_en/date.fst \
 *            <local>/vits-melo-tts-zh_en/number.fst \
 *            <local>/vits-melo-tts-zh_en/phone.fst \
 *            <local>/vits-melo-tts-zh_en/new_heteronym.fst \
 *            /sdcard/Android/data/com.lumiread/files/
 *
 * **为什么平铺(Android 11+ FUSE 权限)**:
 * Android 11+ scoped storage 下,`adb shell mkdir` 创建的子目录由 `shell` UID 拥有,
 * 而 `/sdcard/Android/data/<pkg>/files/` 本体由应用 UID 拥有。FUSE 权限层会拒绝
 * 应用进程枚举 shell-拥有的子目录(虽然 posix `drwxrws---` 看似 OK)。
 * 表现:`File("…/files/vits-melo-tts-zh_en/model.onnx").exists()` 返回 false,即便文件已 push。
 * 而 `files/` 根目录由应用 UID 拥有,push 进去的文件可读。
 * 故平铺,与 Gemma 同一目录共存。
 *
 * 注意:`connectedDebugAndroidTest` 重装 APK 会清空 `/sdcard/Android/data/<pkg>/files/`,
 * 每次跑测试前需重新 push 模型(LLM + TTS 两套)。
 *
 * 必需文件(三件,缺一报 TtsModelMissingException):
 *   files/
 *     ├── model.onnx     (~170 MB,VITS 主模型)
 *     ├── lexicon.txt    (~6.84 MB,中英混合发音词典)
 *     ├── tokens.txt     (~655 B,token → id 映射)
 *     ├── gemma-4-E2B-it.litertlm  (LLM,与本期共存)
 *     └── date.fst, number.fst, phone.fst, new_heteronym.fst  (可选 ruleFsts)
 *
 * 不再支持 jieba `dict/` 子目录(同样的子目录权限问题)。melo-tts-zh_en 基础调用只需
 * model/lexicon/tokens 三件即可发声(官方 Android demo `SherpaOnnxTts/MainActivity.kt` 范例)。
 *
 * 事实来源:
 *  - 官方 CLI 例子(`k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html`):
 *    melo-tts-zh_en 基础调用只需 model/lexicon/tokens 三件,dict-dir 与 rule-fsts 是进阶可选
 *  - 官方 Android demo(`SherpaOnnxTts/MainActivity.kt`):melo-tts 范例里 dictDir = "" 与 ruleFsts = ""
 */
object TtsModelProvider {

    /** model.onnx ~170 MB,允许 ±10% 容差。小于 90% 几乎肯定是 adb push 中断。 */
    private const val EXPECTED_MODEL_SIZE_BYTES = 170_000_000L

    /** 可选的 ruleFsts 列表,按 sherpa-onnx 约定逗号拼接。文件不存在则跳过。 */
    private val OPTIONAL_RULE_FSTS = listOf("date.fst", "number.fst", "phone.fst", "new_heteronym.fst")

    fun locate(context: Context): Result<TtsModelPaths> {
        val ext = context.getExternalFilesDir(null)
            ?: return Result.failure(
                IllegalStateException("getExternalFilesDir(null) 返回 null —— 外部存储不可用?")
            )
        val model = File(ext, "model.onnx")
        val lexicon = File(ext, "lexicon.txt")
        val tokens = File(ext, "tokens.txt")

        val missing = listOf(
            "model.onnx" to model,
            "lexicon.txt" to lexicon,
            "tokens.txt" to tokens,
        ).filterNot { it.second.exists() }

        if (missing.isNotEmpty()) {
            return Result.failure(
                TtsModelMissingException(
                    "缺少 TTS 模型文件:${missing.joinToString { it.first }}。请执行(平铺到 files/ 根目录):\n" +
                        "  adb push <local>/vits-melo-tts-zh_en/{model.onnx,lexicon.txt,tokens.txt,date.fst,number.fst,phone.fst,new_heteronym.fst} ${ext.absolutePath}/"
                )
            )
        }

        if (model.length() < EXPECTED_MODEL_SIZE_BYTES * 9 / 10) {
            return Result.failure(
                TtsModelCorruptedException(
                    "model.onnx 大小异常:${model.length()} bytes,期望约 170 MB。可能 adb push 中断,请重 push。"
                )
            )
        }

        // 可选 ruleFsts(.fst 文本归一化,平铺在 files/ 根)。文件不存在则空字符串,
        // sherpa-onnx 会按默认行为走(不做归一化)。
        // dictDir 留空:子目录方案在 Android 11+ FUSE 下不可靠,且 melo-tts-zh_en 基础调用不需要。
        val ruleFstsPath = OPTIONAL_RULE_FSTS
            .map { File(ext, it) }
            .filter { it.exists() }
            .joinToString(",") { it.absolutePath }

        return Result.success(
            TtsModelPaths(
                modelOnnx = model.absolutePath,
                lexicon   = lexicon.absolutePath,
                tokens    = tokens.absolutePath,
                dictDir   = "",
                ruleFsts  = ruleFstsPath,
            )
        )
    }
}

data class TtsModelPaths(
    val modelOnnx: String,
    val lexicon: String,
    val tokens: String,
    val dictDir: String,   // "" = 不用 jieba 高级分词
    val ruleFsts: String,  // 逗号分隔的 .fst 路径列表,"" = 不做文本归一化
)

class TtsModelMissingException(message: String) : RuntimeException(message)
class TtsModelCorruptedException(message: String) : RuntimeException(message)
