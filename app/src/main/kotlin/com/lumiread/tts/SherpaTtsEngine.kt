package com.lumiread.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.core.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * sherpa-onnx 1.13.2 + vits-melo-tts-zh_en 上的中英 TTS 引擎。
 *
 * 关键事实(直接决定本类的形状):
 *  - `OfflineTts(assetManager, config)` 同步构造读 ~170 MB ONNX,耗时 1–3 s → Dispatchers.IO + 单例
 *  - `generateWithCallback(text, sid, speed, callback)` 边合成边回调每个 chunk(PCM FloatArray);
 *    回调返回 1 继续、0 停止 → 配合 `AudioTrack(MODE_STREAM, PCM_FLOAT)` 边收边播,显著降低首声延迟
 *  - **sampleRate 必须 init 后即从 `OfflineTts.sampleRate()` 取真值再建 AudioTrack**。
 *    melo-tts-zh_en 真实采样率是 **44100 Hz**(HuggingFace 模型卡 + JNI `sampleRate()` 双重确认)。
 *    若写死 22050,AudioTrack 把 44100 样本按 22050 播 → 听感"诡异半速慢放"。
 *    修复:warmUp() 末尾从引擎读出真值并缓存到 [sampleRate]。
 *  - 不可重入:并发调两次 `generate*` 会破坏内部状态 → 全程 Mutex 串行化
 *  - JNI 资源:进程退出由 `finalize()` 兜底;不主动 `free()`
 *
 * 语速:**永远 1.0f**。
 * 实测:`speed != 1.0` 在 VITS-melo 上声学畸变明显(尤其 0.85 慢速场景出现"诡异慢速"),
 * 且 LLM 已按年龄段缩短/简化句式,无需再在合成器侧二次缩放。
 * VITS 训练语速本身已偏柔,直接 1.0 听感最自然。**`ageBand` 参数保留但本类不使用**,
 * 留给后续若换 TTS 引擎时复用。
 *
 * `lang` 当前不映射到 sid:melo-tts-zh_en 是中英混读单模型(sid=0)。
 */
class SherpaTtsEngine(private val context: Context) : TtsEngine {

    private val initMutex = Mutex()
    private val speakMutex = Mutex()

    @Volatile private var tts: OfflineTts? = null
    // 真实采样率,warmUp() 末尾从 OfflineTts.sampleRate() 读取。<=0 表示尚未初始化。
    @Volatile private var sampleRate: Int = 0

    @Volatile private var stopped = false

    /** speak() 期间的当前 AudioTrack。回调线程通过它写 PCM。 */
    @Volatile private var currentTrack: AudioTrack? = null

    /**
     * sherpa-onnx generateWithCallback 的流式回调。**必须**以方法引用
     * `this::ttsStreamCallback` 形式传入,而非 lambda `{ samples -> ... }`。
     *
     * 2026-05-24 PKJ110 Android 16 实测:lambda 会被 D8 desugar 成
     * `ExternalSyntheticLambdaN` 的 `int invoke(FloatArray)`(原始 int 返回);
     * 但 sherpa-onnx JNI 侧查找 `Function1<FloatArray, Integer>.invoke([F)Ljava/lang/Integer;`
     * (Kotlin 泛型擦除时 Int 装箱成 Integer)→ NoSuchMethodError → SIGABRT。
     * 方法引用 `this::method` 会生成 KFunction-style 实现,自动桥接装箱返回值,与 JNI 期望匹配。
     *
     * 参考:官方 Android demo `SherpaOnnxTts/MainActivity.kt` 同样用 `this::callback`。
     */
    private fun ttsStreamCallback(samples: FloatArray): Int {
        if (stopped) return 0
        currentTrack?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        return 1
    }

    override suspend fun warmUp() = withContext(Dispatchers.IO) {
        if (tts != null) return@withContext
        initMutex.withLock {
            if (tts != null) return@withLock
            val paths = TtsModelProvider.locate(context).getOrThrow()
            Log.i(TAG, "SherpaTtsEngine 准备初始化,modelOnnx=${paths.modelOnnx}")
            val t0 = System.currentTimeMillis()
            val engine = OfflineTts(
                assetManager = null,
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model    = paths.modelOnnx,
                            lexicon  = paths.lexicon,
                            tokens   = paths.tokens,
                            // dictDir / dataDir 留默认 "",melo-tts-zh_en 基础调用不需要
                            // (来源:官方 Android demo SherpaOnnxTts/MainActivity.kt 范例)。
                            // 若 TtsModelProvider 报告 dictDir 非空,在此覆盖。
                            dictDir  = paths.dictDir,
                        ),
                        numThreads = 2,
                        provider   = "cpu",
                    ),
                    // ruleFsts: 数字/日期/电话/多音字归一化,提升朗读自然度;留空则跳过。
                    ruleFsts = paths.ruleFsts,
                ),
            )
            // 必须在 build AudioTrack **之前**拿到真实采样率,否则会出现半速/倍速畸变。
            // OfflineTts.sampleRate() 是 JNI 直查模型 meta,无需先 generate。
            sampleRate = engine.sampleRate()
            tts = engine
            Log.i(TAG, "SherpaTtsEngine 初始化完成,采样率=${sampleRate}Hz,耗时=${System.currentTimeMillis() - t0}ms")
        }
    }

    override suspend fun speak(text: String, lang: Lang, ageBand: AgeBand) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        warmUp()
        val engine = tts ?: error("SherpaTtsEngine 未初始化")
        val rate = sampleRate
        check(rate > 0) { "sampleRate 未在 warmUp 中初始化(=$rate)" }
        stopped = false
        // speed 永远 1.0:见类顶部文档。`ageBand` 参数保留但本类不再消费。
        val speed = 1.0f

        speakMutex.withLock {
            val bufSize = AudioTrack.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(8192)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            currentTrack = track
            try {
                track.play()
                val audio: GeneratedAudio = engine.generateWithCallback(
                    text     = text,
                    sid      = 0,
                    speed    = speed,
                    callback = this@SherpaTtsEngine::ttsStreamCallback,
                )
                if (audio.sampleRate != rate) {
                    // 真到了这一步只能说明模型在不同调用间返回不同采样率,几乎不可能。留个告警。
                    Log.w(TAG, "采样率漂移!warmUp=${rate}Hz vs generated=${audio.sampleRate}Hz")
                }
            } finally {
                currentTrack = null
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    override suspend fun stop() {
        stopped = true
    }

    companion object {
        private const val TAG = "SherpaTtsEngine"
    }
}
