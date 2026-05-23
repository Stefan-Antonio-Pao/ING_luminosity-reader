package com.lumiread.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.core.OcrMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户设置持久化。
 *
 * 用 `androidx.datastore-preferences` 把"输出语言 / 年龄段"两个枚举存到磁盘,
 * App 启动时 `langFlow`/`ageFlow` 直接发射上次的值,UI 用 `collectAsState` 订阅 →
 * 用户改了立刻 [setLang]/[setAge] 写回。读写都在 Dispatchers.IO 里跑(DataStore 自己管),
 * UI 线程只感知 Flow 发射。
 *
 * 没值时给安全默认:[Lang.EN] 与 [AgeBand.PRESCHOOL]。
 * 枚举名跟代码同步;反序列化失败(理论上 enum 改名才会触发)也回退到默认,不让用户卡死在脏值上。
 */
private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

class SettingsRepository(private val context: Context) {
    private val keyLang     = stringPreferencesKey("lang")
    private val keyAge      = stringPreferencesKey("age")
    private val keyOcrMode  = stringPreferencesKey("ocr_mode")

    val langFlow: Flow<Lang> = context.userPrefs.data.map { prefs ->
        prefs[keyLang]?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
    }

    val ageFlow: Flow<AgeBand> = context.userPrefs.data.map { prefs ->
        prefs[keyAge]?.let { runCatching { AgeBand.valueOf(it) }.getOrNull() } ?: AgeBand.PRESCHOOL
    }

    /**
     * OCR 模式。默认 [OcrMode.OCR](独立 OCR);[OcrMode.MULTIMODAL] 为实验入口,延迟显著。
     */
    val ocrModeFlow: Flow<OcrMode> = context.userPrefs.data.map { prefs ->
        prefs[keyOcrMode]?.let { runCatching { OcrMode.valueOf(it) }.getOrNull() } ?: OcrMode.OCR
    }

    suspend fun setLang(lang: Lang) {
        context.userPrefs.edit { it[keyLang] = lang.name }
    }

    suspend fun setAge(age: AgeBand) {
        context.userPrefs.edit { it[keyAge] = age.name }
    }

    suspend fun setOcrMode(mode: OcrMode) {
        context.userPrefs.edit { it[keyOcrMode] = mode.name }
    }
}
