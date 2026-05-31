package com.lumiread.ui.theme

/**
 * 双模式开关(UI 改造任务书 §3.1)。
 *
 *  - [Child]:儿童模式 / 卡通皮肤。默认值。
 *  - [Parent]:家长模式 / 现有简洁风(= v1.1 改造前的现状,逐屏比对必须一致)。
 *
 * 通过 [com.lumiread.data.SettingsRepository.lumiModeFlow] 持久化(DataStore key="lumi_mode"),
 * 由 [LumiTheme] 订阅并据此选定 [LumiTokens]。
 *
 * 本枚举与 v1.1 的 Lang / AppUiLang / OutputMode / OcrMode / GemmaModel / autoPlayTts 完全正交,
 * 互不耦合 —— 切换 LumiMode 不影响任何业务逻辑。
 */
enum class LumiMode { Child, Parent }
