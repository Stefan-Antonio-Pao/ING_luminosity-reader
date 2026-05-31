package com.lumiread.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumiread.core.AgeBand
import com.lumiread.core.GemmaModel
import com.lumiread.core.Lang
import com.lumiread.core.OcrMode
import com.lumiread.core.OutputMode
import com.lumiread.ui.theme.LumiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 用户设置持久化(CLAUDE.md §2.5–2.6 + Phase 5 衔接 + v1.1 双模型架构)。
 *
 * 用 `androidx.datastore-preferences` 把"输出语言 / 年龄段 / OCR 模式 / 选中的 Gemma 模型"四个
 * 枚举存到磁盘,App 启动时各 Flow 直接发射上次的值,UI 用 `collectAsState` 订阅 →
 * 用户改了立刻 setXxx 写回。读写都在 Dispatchers.IO 里跑(DataStore 自己管),UI 线程只感知 Flow 发射。
 *
 * 没值时给安全默认:[Lang.EN] / [AgeBand.PRESCHOOL] / [OcrMode.OCR] / [GemmaModel.E2B]。
 *
 * **v1.1(2026-05-25)新增**:
 *  - [selectedModelFlow]:用户选的 Gemma 模型(E2B / E4B)。默认 E2B(原行为)
 *  - [effectiveOcrModeFlow]:派生流 —— 当选中模型不支持多模态时强制返回 [OcrMode.OCR],
 *    避免 E2B + MULTIMODAL 的崩溃组合。**UI 与 ChatSession 都用这个**,不读裸 [ocrModeFlow]
 */
private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

class SettingsRepository(private val context: Context) {
    private val keyLang          = stringPreferencesKey("lang")
    private val keyAge           = stringPreferencesKey("age")
    private val keyOcrMode       = stringPreferencesKey("ocr_mode")
    private val keySelectedModel = stringPreferencesKey("selected_model")
    private val keyAutoPlayTts   = booleanPreferencesKey("auto_play_tts")
    private val keyOutputMode    = stringPreferencesKey("output_mode")
    private val keyLumiMode      = stringPreferencesKey("lumi_mode")

    val langFlow: Flow<Lang> = context.userPrefs.data.map { prefs ->
        prefs[keyLang]?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
    }

    val ageFlow: Flow<AgeBand> = context.userPrefs.data.map { prefs ->
        prefs[keyAge]?.let { runCatching { AgeBand.valueOf(it) }.getOrNull() } ?: AgeBand.PRESCHOOL
    }

    /**
     * 用户**原始**的 OCR 模式选择(可能与生效模式不一致,见 [effectiveOcrModeFlow])。
     * 仅在设置页 UI **回显**与"模型支持多模态时是否启用 MULTIMODAL"判断时使用。
     */
    val ocrModeFlow: Flow<OcrMode> = context.userPrefs.data.map { prefs ->
        prefs[keyOcrMode]?.let { runCatching { OcrMode.valueOf(it) }.getOrNull() } ?: OcrMode.OCR
    }

    /**
     * 选中的 Gemma 模型(v1.1 2026-05-25)。默认 [GemmaModel.E2B]。
     *
     * **AppGraph 监听此流**:发生变化 → `Gemma4Engine.switchActiveModel(it)` close 旧引擎、重 init。
     */
    val selectedModelFlow: Flow<GemmaModel> = context.userPrefs.data.map { prefs ->
        prefs[keySelectedModel]?.let { runCatching { GemmaModel.valueOf(it) }.getOrNull() }
            ?: GemmaModel.E2B
    }

    /**
     * **生效**的 OCR 模式(v1.1 2026-05-25)。
     *
     * 派生规则:
     *  - 选中模型 [GemmaModel.supportsMultimodal] = true → 用 [ocrModeFlow] 原值
     *  - 否则 → 强制 [OcrMode.OCR]
     *
     * 这样用户在 E4B 下选过 MULTIMODAL,切回 E2B 不会崩(自动回 OCR);再切回 E4B 时,
     * 之前的 MULTIMODAL 偏好会自动恢复(因为 DataStore 里的 [ocrModeFlow] 没被改动)。
     *
     * **ChatSession 与 UI 都订阅这个**。设置页的 OCR 模式切换按钮判断 enabled 时,直接看 selectedModel.supportsMultimodal。
     */
    val effectiveOcrModeFlow: Flow<OcrMode> = combine(selectedModelFlow, ocrModeFlow) { model, mode ->
        if (model.supportsMultimodal) mode else OcrMode.OCR
    }

    /**
     * 自动朗读开关(v1.1 步骤三,2026-05-25)。默认 true(保留 Phase 4 起的既有行为)。
     *
     * - true:LLM 回复结束后,ChatSession 自动调 [com.lumiread.core.tts.TtsEngine.speak] 朗读
     * - false:不自动朗读;UI 在每条助手气泡旁显示手动播放按钮,由孩子自己点
     *
     * 与"输出语言 / 输出模式 / 界面语言"三概念完全正交,互不联动(任务书 v1.1 §2)。
     */
    val autoPlayTtsFlow: Flow<Boolean> = context.userPrefs.data.map { prefs ->
        prefs[keyAutoPlayTts] ?: true
    }

    /**
     * 输出模式(v1.1 步骤四,2026-05-25)。默认 [OutputMode.MONOLINGUAL]。
     *
     *  - [OutputMode.MONOLINGUAL]:Gemma 仅用 [langFlow] 指定的那一种语言输出(v1.0 行为)
     *  - [OutputMode.BILINGUAL]:中英成对输出,主语种 = [langFlow],副语种 = 另一个
     *
     * 与 [langFlow] / [ocrModeFlow] / [selectedModelFlow] / [autoPlayTtsFlow] / 界面语言完全正交,
     * 互不联动(任务书 v1.1 §2 反复强调)。
     */
    val outputModeFlow: Flow<OutputMode> = context.userPrefs.data.map { prefs ->
        prefs[keyOutputMode]?.let { runCatching { OutputMode.valueOf(it) }.getOrNull() }
            ?: OutputMode.MONOLINGUAL
    }

    /**
     * 双模式 UI 开关(UI 改造任务书 §3.1,2026-05-25)。默认 [LumiMode.Child]。
     *
     *  - [LumiMode.Child]:卡通皮肤(步骤三起逐步落地)
     *  - [LumiMode.Parent]:= v1.1 改造前的简洁风(步骤一回归校验基准)
     *
     * 由 [com.lumiread.ui.theme.LumiTheme] 订阅。**纯表现层**,与 v1.1 全部业务设置完全正交:
     * 切换 LumiMode 不影响 Lang / AppUiLang / OutputMode / OcrMode / GemmaModel / autoPlayTts。
     */
    val lumiModeFlow: Flow<LumiMode> = context.userPrefs.data.map { prefs ->
        prefs[keyLumiMode]?.let { runCatching { LumiMode.valueOf(it) }.getOrNull() }
            ?: LumiMode.Child
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

    suspend fun setSelectedModel(model: GemmaModel) {
        context.userPrefs.edit { it[keySelectedModel] = model.name }
    }

    suspend fun setAutoPlayTts(enabled: Boolean) {
        context.userPrefs.edit { it[keyAutoPlayTts] = enabled }
    }

    suspend fun setOutputMode(mode: OutputMode) {
        context.userPrefs.edit { it[keyOutputMode] = mode.name }
    }

    suspend fun setLumiMode(mode: LumiMode) {
        context.userPrefs.edit { it[keyLumiMode] = mode.name }
    }
}
