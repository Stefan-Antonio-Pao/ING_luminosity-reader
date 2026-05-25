package com.lumiread.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiread.AppGraph
import com.lumiread.R
import com.lumiread.ui.components.AppBackground
import com.lumiread.ui.components.Celebration
import com.lumiread.ui.components.ChunkySwitch
import com.lumiread.ui.components.LumiOutlinedButton
import com.lumiread.ui.components.LumiPrimaryButton
import com.lumiread.ui.theme.LocalLumiTokens
import com.lumiread.ui.theme.LumiMode
import com.lumiread.ui.theme.ModeSwitchSection
import com.lumiread.camera.CameraCaptureScreen
import com.lumiread.core.AgeBand
import com.lumiread.core.GemmaModel
import com.lumiread.core.ImageInput
import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrMode
import com.lumiread.core.OcrResult
import com.lumiread.core.OutputMode
import com.lumiread.core.pipeline.ChatEvent
import com.lumiread.core.pipeline.ChatSession
import com.lumiread.llm.ModelProvider
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 三屏:Capture / Chat / Settings(.5 把原 Reading 单轮屏改成多轮聊天屏)。
 *
 * .1 增量:
 * - 设置(lang/age)由 [AppGraph.settings] 通过 DataStore 持久化,UI 用 `collectAsState` 订阅
 * - 当前会话由 [AppGraph.chatStore] 序列化到 `filesDir/chat/current.json`,启动自动恢复
 * - 全面屏:Activity 已 `enableEdgeToEdge`,这里用 `statusBarsPadding/.imePadding/.navigationBarsPadding`
 * 把内容压在系统栏内,顶部不再被状态栏盖住
 *
 * 顶部贴一条醒目的"FAKE MODE"红色水印,防止 demo/评委把桩件
 * 输出误当真实功能。任何 Fake 实现没被替换掉,这条都会一直挂着。
 */
enum class Screen { CAPTURE, CHAT, SETTINGS, MY_LEARNING }

/**
 * 进相机的意图:
 * - [NEW] = 拍完开启一段新会话(用首批图启动 ChatSession)
 * - [ATTACH] = 拍完把图加进当前会话的"待发送"暂存,与下一条文本一起发出去
 */
enum class CaptureIntent { NEW, ATTACH }

/**
 * v1.1:界面语言三态。
 *
 * - [SYSTEM]:走系统语言(`LocaleListCompat.getEmptyLocaleList`)
 * - [ZH] / [EN]:显式钉住,与"输出语言"/"输出模式"完全解耦(设计规范)
 *
 * 状态实际持久化由 AppCompat 接手:API 33+ 走 `LocaleManager`,API 26-32 由
 * `AppLocalesMetadataHolderService` 元数据控制持久化(见 AndroidManifest)。
 */
enum class AppUiLang { SYSTEM, ZH, EN }

/** [GemmaModel] 的本地化显示名;:core 模块不依赖 Android,无法直接持有 R.string id。 */
@StringRes
fun GemmaModel.displayNameRes(): Int = when (this) {
    GemmaModel.E2B -> R.string.model_e2b_display_name
    GemmaModel.E4B -> R.string.model_e4b_display_name
}

/** 把 AppCompatDelegate 当前的 locale 列表归到三态之一,供 UI 回显使用。 */
private fun currentAppUiLang(): AppUiLang {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return AppUiLang.SYSTEM
    return when (locales.get(0)?.language) {
        "zh" -> AppUiLang.ZH
        "en" -> AppUiLang.EN
        else -> AppUiLang.SYSTEM
    }
}

@Composable
fun LumiReadApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.CAPTURE) }
    val lang by AppGraph.settings.langFlow.collectAsState(initial = Lang.EN)
    val age  by AppGraph.settings.ageFlow.collectAsState(initial = AgeBand.PRESCHOOL)
    // 注意:用户的"原始"OcrMode 给设置页回显;真正喂给 ChatSession 的是 effectiveOcrMode
    // —— 后者在 E2B 时被 SettingsRepository 强制成 OCR,防止 E2B + MULTIMODAL 崩(v1.1)。
    val ocrMode by AppGraph.settings.ocrModeFlow.collectAsState(initial = OcrMode.OCR)
    val effectiveOcrMode by AppGraph.settings.effectiveOcrModeFlow.collectAsState(initial = OcrMode.OCR)
    val selectedModel by AppGraph.settings.selectedModelFlow.collectAsState(initial = GemmaModel.E2B)
    val autoPlayTts by AppGraph.settings.autoPlayTtsFlow.collectAsState(initial = true)
    val outputMode by AppGraph.settings.outputModeFlow.collectAsState(initial = OutputMode.MONOLINGUAL)
    val studyRecords by AppGraph.studyStore.all.collectAsState(initial = emptyList())
    // captureIntent / settingsReturnTo:UI 路由短期态,不必跨进程恢复
    var captureIntent by remember { mutableStateOf(CaptureIntent.NEW) }
    var settingsReturnTo by remember { mutableStateOf(Screen.CAPTURE) }
    val chat = rememberChatState()
    val scope = rememberCoroutineScope()

    // 庆祝动效计数:递增即触发一轮星星
    // - 拍照"完成 (N)"提交时 +1
    // - 每次 assistant 回应结束(done=true,无 error)时 +1
    // 家长模式下 Celebration 内部检查 decorDensity=0,直接 return,不绘制
    var celebrationTick by remember { mutableIntStateOf(0) }
    var lastDoneAssistantSize by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            chat.messages.count { it is ChatRow.Assistant && it.done && it.error == null }
        }.collect { current ->
            if (current > lastDoneAssistantSize && lastDoneAssistantSize > 0) {
                celebrationTick++
            }
            lastDoneAssistantSize = current
        }
    }

    // 启动恢复:从磁盘读 current.json,有内容就跳到 CHAT 屏(用户更可能想接着聊)。
    // hasLoaded 故意 **不** rememberSaveable —— 配置变更(旋转)时 ChatState 会重建,我们要重新装回。
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasLoaded) return@LaunchedEffect
        val saved = AppGraph.chatStore.load()
        if (saved.isNotEmpty() && chat.messages.isEmpty()) {
            chat.messages.addAll(saved)
            // 只在用户没明确选别的屏(默认 CAPTURE)时才跳;尊重 rememberSaveable 恢复出的 SETTINGS/CHAT。
            if (screen == Screen.CAPTURE) screen = Screen.CHAT
        }
        hasLoaded = true
    }

    // 自动落盘:加载完成后才开始,跳过首个快照(== 刚加载好的内容,不必回写)。
    // snapshotFlow 自带去重,连续修改同一帧只会发一次。
    LaunchedEffect(hasLoaded) {
        if (!hasLoaded) return@LaunchedEffect
        snapshotFlow { chat.messages.toList() }
            .drop(1)
            .collect { runCatching { AppGraph.chatStore.save(it) } }
    }

    // 全面屏返回手势 / 系统返回键路由(2026-05-25 步骤 5b 用户反馈)。
    // - SETTINGS / MY_LEARNING:走父屏(无 finishAffinity,系统不会突然退出 App)
    // - CAPTURE:有进行中会话 → 回 CHAT;否则放行 → Activity 默认 finish 退出 App
    // - CHAT:不拦截,系统默认行为 = 退出 App(会话已自动持久化,下次启动恢复)
    val activity = LocalContext.current as? Activity
    BackHandler(enabled = when (screen) {
        Screen.CAPTURE -> chat.hasSession || chat.messages.isNotEmpty()
        Screen.CHAT -> false
        Screen.SETTINGS, Screen.MY_LEARNING -> true
    }) {
        when (screen) {
            Screen.CAPTURE -> screen = Screen.CHAT
            Screen.SETTINGS -> screen = settingsReturnTo
            Screen.MY_LEARNING -> screen = Screen.SETTINGS
            Screen.CHAT -> Unit  // not reached(enabled=false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()        // 顶部躲开状态栏 / 刘海(用户:"最上面不要放东西")
            .navigationBarsPadding()    // 底部躲开手势条
            .imePadding(),              // 键盘弹起时整体上推,聊天输入框始终可见
    ) {
        if (AppGraph.isFakeMode) FakeModeBanner()

        Box(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                Screen.CAPTURE -> CameraCaptureScreen(
                    onCaptured = { paths ->
                        celebrationTick++
                        when (captureIntent) {
                            CaptureIntent.NEW -> {
                                chat.startNewSession(scope, lang, age, effectiveOcrMode, autoPlayTts, outputMode, paths)
                                screen = Screen.CHAT
                            }
                            CaptureIntent.ATTACH -> {
                                chat.pendingPaths.addAll(paths)
                                screen = Screen.CHAT
                            }
                        }
                    },
                    onOpenSettings = {
                        settingsReturnTo = Screen.CAPTURE
                        screen = Screen.SETTINGS
                    },
                    onCancel = {
                        // 有进行中会话 → 回 CHAT;否则 CAPTURE 是首屏,退出 App
                        if (chat.hasSession || chat.messages.isNotEmpty()) {
                            screen = Screen.CHAT
                        } else {
                            activity?.finish()
                        }
                    },
                    // "直接开始对话":仅在 NEW intent(开新会话路径)下提供;
                    // ATTACH intent(中途加图)显示这个按钮会语义混乱。
                    onStartChatDirect = if (captureIntent == CaptureIntent.NEW) {
                        {
                            chat.startChatWithoutImages(scope, lang, age, effectiveOcrMode, autoPlayTts, outputMode)
                            screen = Screen.CHAT
                        }
                    } else null,
                )
                Screen.CHAT -> ChatScreen(
                    chat = chat,
                    lang = lang,
                    ageBand = age,
                    autoPlayTts = autoPlayTts,
                    onAddImages = {
                        captureIntent = CaptureIntent.ATTACH
                        screen = Screen.CAPTURE
                    },
                    onEndSession = {
                        chat.endSession()
                        captureIntent = CaptureIntent.NEW
                        screen = Screen.CAPTURE
                    },
                    onOpenSettings = {
                        settingsReturnTo = Screen.CHAT
                        screen = Screen.SETTINGS
                    },
                    onSend = { text -> chat.sendUserTurn(scope, lang, age, effectiveOcrMode, autoPlayTts, outputMode, text) },
                    onRemovePending = { path -> chat.removePendingImage(path) },
                    // v1.1:手动播放。AppGraph.tts(SherpaTtsEngine)内部 speakMutex 串行,
                    // 多次快点不会撞;cancellation 由 onPlay 调用方的 scope 决定。
                    onPlayAssistant = { text -> AppGraph.tts.speak(text, lang, age) },
                )
                Screen.SETTINGS -> SettingsScreen(
                    lang = lang,
                    ageBand = age,
                    ocrMode = ocrMode,
                    selectedModel = selectedModel,
                    autoPlayTts = autoPlayTts,
                    outputMode = outputMode,
                    onLang = { newLang -> scope.launch { AppGraph.settings.setLang(newLang) } },
                    onAge  = { newAge  -> scope.launch { AppGraph.settings.setAge(newAge)  } },
                    onOcrMode = { newMode -> scope.launch { AppGraph.settings.setOcrMode(newMode) } },
                    onAutoPlayTts = { v -> scope.launch { AppGraph.settings.setAutoPlayTts(v) } },
                    onOutputMode = { m -> scope.launch { AppGraph.settings.setOutputMode(m) } },
                    onSelectModel = { newModel ->
                        // 切换模型副作用很重:close 旧 Engine + 重 init 新模型(~10 s)。
                        // 必须先结束当前会话,否则进行中的 Conversation 与即将关闭的 Engine 撞死。
                        scope.launch {
                            chat.endSession()
                            AppGraph.settings.setSelectedModel(newModel)
                            // 之后:DataStore 发射 → AppGraph.startModelWatcher 收到 →
                            // Gemma4Engine.setActiveModel → 下次 warmUp 用新模型初始化
                        }
                    },
                    onBack = { screen = settingsReturnTo },
                    onOpenMyLearning = { screen = Screen.MY_LEARNING },
                )
                Screen.MY_LEARNING -> MyLearningScreen(
                    records = studyRecords,
                    onBack  = { screen = Screen.SETTINGS },
                    onClear = { scope.launch { AppGraph.studyStore.clearAll() } },
                )
            }

            // 庆祝层:非交互覆盖,家长模式自动 short-circuit
            Celebration(trigger = celebrationTick)
        }
    }
}

@Composable
private fun FakeModeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFB00020))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.fake_mode_banner),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* -------------------------------------------------------------------------- *
 * 聊天状态 + 一组操作函数
 * -------------------------------------------------------------------------- */

/**
 * UI 层的一行聊天记录。
 *
 * 用户气泡:本轮文本 + 附带图片路径 + OCR/标签**摘要字符串**(便于序列化到 JSON)。
 * ocrSummary 用 " / " 拼接每行 OCR 文本(截 160 字),labelsSummary 用 ", " 拼接标签名。
 * 持久化时只存摘要,不存原始 OcrResult/Label; 若需要回放原始置信度再说。
 * 助手气泡:增量累积 LLM 流式输出;done=true 表示本轮结束,error 非空表示本轮失败。
 */
sealed interface ChatRow {
    data class User(
        val text: String,
        val imagePaths: List<String>,
        val ocrSummary: String,
        val labelsSummary: String,
    ) : ChatRow

    data class Assistant(
        val text: String,
        val done: Boolean,
        val error: String?,
    ) : ChatRow
}

private fun summarizeOcr(ocr: List<OcrResult>): String =
    ocr.flatMap { it.lines }
        .joinToString(" / ") { it.text }
        .take(160)

private fun summarizeLabels(labels: List<Label>): String =
    labels.joinToString(", ") { it.name }

/**
 * Compose 范围内的聊天状态容器。
 *
 * 设计要点:
 * - [messages] / [pendingPaths] 用 SnapshotStateList,UI 自动观察增删
 * - [streaming] / [error] 是 mutableStateOf,UI 自动观察值变化
 * - [session] 故意不是 State:它只在事件回调里读(非 Composable 上下文),不需要触发重组
 * - [mutex] 串行化每轮请求(LiteRT-LM 单会话不可重入,详见 ChatSession 文档)
 */
class ChatState {
    val messages: androidx.compose.runtime.snapshots.SnapshotStateList<ChatRow> = mutableStateListOf()
    val pendingPaths: androidx.compose.runtime.snapshots.SnapshotStateList<String> = mutableStateListOf()
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    internal var session: ChatSession? = null
    internal val mutex = Mutex()

    /**
     * 当前"学习记录会话"在 Room 中的行 id。(下半)。
     * - 首轮 AssistantDone 时,若为 null → 调 [StudyStore.beginSession] 插行并接住 id
     * - 后续 AssistantDone → 调 [StudyStore.recordTurn] 刷 endedAt 与 turnCount
     * - 切「↻ 新会话」/ 开新会话 / 直聊起 → 一律置 null,下一轮会重起一条
     * 不放进 messages 序列化,因为 Room 行本身就是落盘的。
     */
    internal var studySessionId: Long? = null

    val hasSession: Boolean get() = session != null
}

@Composable
private fun rememberChatState(): ChatState = remember { ChatState() }

/**
 * 开新会话:关旧的、清状态、首批图入用户气泡,异步跑 [ChatSession.firstTurn]。
 *
 * 设计:消息列表先压两条占位(User 带图路径 + Assistant 空文本流位),协程里再按
 * ChatEvent 增量改写对应索引;这样用户拍完立刻看到自己气泡,助手气泡有"等待…"反馈。
 *
 * C3:paths 进流水线前过滤"文件不存在 / 长度 0"(cacheDir 被系统清理过)。
 * C9:pendingPaths 残留文件清理。
 */
private fun ChatState.startNewSession(
    scope: CoroutineScope,
    lang: Lang,
    age: AgeBand,
    ocrMode: OcrMode,
    autoPlayTts: Boolean,
    outputMode: OutputMode,
    paths: List<String>,
) {
    if (streaming) return  // C4 防抖:正在出文的时候忽略
    // 关旧会话,清状态
    runCatching { session?.close() }
    session = null
    studySessionId = null   // 新会话起一行新的学习记录
    messages.clear()
    pendingPaths.forEach { runCatching { File(it).delete() } }   // C9
    pendingPaths.clear()
    error = null

    val validPaths = paths.filter { runCatching { File(it).isFile && File(it).length() > 0 }.getOrDefault(false) }
    if (validPaths.isEmpty()) {
        // 文案来自 res/values{,-en}/strings.xml#error_images_cleared;此处用上下文取字符串
        // 因为该路径不在 Composable 内,无法直接 stringResource。读 AppGraph.appContext 即可。
        error = AppGraph.appContext.getString(R.string.error_images_cleared)
        return
    }
    val images = validPaths.map { ImageInput.Path(absolutePath = it) }
    val userIdx = messages.size
    messages += ChatRow.User(text = "", imagePaths = validPaths, ocrSummary = "", labelsSummary = "")
    val assistantIdx = messages.size
    messages += ChatRow.Assistant(text = "", done = false, error = null)
    streaming = true

    scope.launch {
        mutex.withLock {
            try {
                val newSession = AppGraph.pipeline.startChat(lang, age, ocrMode, autoPlayTts, outputMode)
                session = newSession
                runFlow(newSession.firstTurn(images), userIdx, assistantIdx, lang, age)
            } finally {
                streaming = false   // C2:cancellation 也走得到这里
            }
        }
    }
}

/**
 * 开无图会话:跳过 [ChatSession.firstTurn],只开 Conversation,让用户自己起话头。
 *
 * 用户体验:打开聊天屏 → 输入栏禁用直到引擎就绪(Gemma4 首次 init 可达 ~10 s)→
 * 引擎好了输入栏激活 → 用户输入并发送,走 [ChatSession.userTurn] 文本路径。
 */
private fun ChatState.startChatWithoutImages(
    scope: CoroutineScope,
    lang: Lang,
    age: AgeBand,
    ocrMode: OcrMode,
    autoPlayTts: Boolean,
    outputMode: OutputMode,
) {
    if (streaming) return  // C4
    runCatching { session?.close() }
    session = null
    studySessionId = null
    messages.clear()
    pendingPaths.forEach { runCatching { File(it).delete() } }   // C9
    pendingPaths.clear()
    error = null
    // 标记"准备中"。streaming = true 会让输入栏禁用、按钮变灰,直到引擎就绪。
    streaming = true

    scope.launch {
        try {
            mutex.withLock {
                try {
                    val newSession = AppGraph.pipeline.startChat(lang, age, ocrMode, autoPlayTts, outputMode)
                    session = newSession
                } catch (t: Throwable) {
                    error = t.message ?: t.javaClass.simpleName
                }
            }
        } finally {
            streaming = false   // C2
        }
    }
}

/**
 * 送下一轮:把暂存的 [pendingPaths] 与用户文本一起发出去。
 * 文本与图片至少一非空(UI 已通过 enabled 控制)。
 *
 * 若 [session] 为空(冷启动恢复了历史聊天但 LiteRT-LM Conversation 已死),按需重开一段。
 * 注意此时 **LLM 没有过往 KV cache**,只看到这一轮的内容 —— 这是当前阶段可接受的限制,
 * 真正的"会话归档/恢复"留给 。
 */
private fun ChatState.sendUserTurn(
    scope: CoroutineScope,
    lang: Lang,
    age: AgeBand,
    ocrMode: OcrMode,
    autoPlayTts: Boolean,
    outputMode: OutputMode,
    text: String,
) {
    if (streaming) return  // C4 防抖:双击发送只会出一轮
    val pending = pendingPaths.toList()
        .filter { runCatching { File(it).isFile && File(it).length() > 0 }.getOrDefault(false) }   // C3
    pendingPaths.clear()
    val images = pending.map { ImageInput.Path(absolutePath = it) }

    val userIdx = messages.size
    messages += ChatRow.User(text = text, imagePaths = pending, ocrSummary = "", labelsSummary = "")
    val assistantIdx = messages.size
    messages += ChatRow.Assistant(text = "", done = false, error = null)
    streaming = true

    scope.launch {
        try {
            mutex.withLock {
                val sess = session ?: try {
                    AppGraph.pipeline.startChat(lang, age, ocrMode, autoPlayTts, outputMode).also { session = it }
                } catch (t: Throwable) {
                    val msg = t.message ?: t.javaClass.simpleName
                    val a = messages.getOrNull(assistantIdx) as? ChatRow.Assistant   // C1
                    if (a != null) messages[assistantIdx] = a.copy(error = msg, done = true)
                    error = msg
                    return@withLock
                }
                runFlow(sess.userTurn(text, images), userIdx, assistantIdx, lang, age)
            }
        } finally {
            streaming = false   // C2
        }
    }
}

/**
 * 收集 ChatSession 发来的 ChatEvent,把流式增量写到对应 messages 行。
 *
 * C1:所有 `messages[idx] as ChatRow.X` 改成 `getOrNull(idx) as? ChatRow.X ?: return@collect`,
 * 避免用户在 streaming 中按「↻ 新会话」清空 messages 后,后到的 Flow 回调撞 ClassCastException
 * / IndexOutOfBoundsException。
 * C2:streaming = false 由调用方在 finally 内统一兜底;本函数内不再显式翻。
 * C8:学习记录写入用 runCatching 包,DB 异常不阻断对话。
 */
private suspend fun ChatState.runFlow(
    flow: Flow<ChatEvent>,
    userIdx: Int,
    assistantIdx: Int,
    lang: Lang,
    age: AgeBand,
) {
    flow.collect { ev ->
        when (ev) {
            is ChatEvent.TurnContext -> {
                val u = messages.getOrNull(userIdx) as? ChatRow.User ?: return@collect
                messages[userIdx] = u.copy(
                    ocrSummary    = summarizeOcr(ev.ocr),
                    labelsSummary = summarizeLabels(ev.labels),
                )
            }
            is ChatEvent.AssistantChunk -> {
                val a = messages.getOrNull(assistantIdx) as? ChatRow.Assistant ?: return@collect
                messages[assistantIdx] = a.copy(text = a.text + ev.text)
            }
            is ChatEvent.AssistantDone -> {
                val a = messages.getOrNull(assistantIdx) as? ChatRow.Assistant ?: return@collect
                val text = ev.fullText.ifEmpty { a.text }
                messages[assistantIdx] = a.copy(text = text, done = true)

                // (下半):学习记录 —— 首轮插行,后续每轮 UPDATE。
                // 失败分支(Failed)不计入;空文本助手回应仍记一轮(用户花了时间)。
                val u = messages.getOrNull(userIdx) as? ChatRow.User
                val summary = listOfNotNull(
                    u?.ocrSummary?.takeIf { it.isNotBlank() }?.let { "OCR: $it" },
                    u?.labelsSummary?.takeIf { it.isNotBlank() }?.let { "标签: $it" },
                ).joinToString(" | ")
                runCatching {   // C8:DB 异常不阻断对话
                    val sid = studySessionId
                    if (sid == null) {
                        studySessionId = AppGraph.studyStore.beginSession(lang, age, summary)
                    } else {
                        AppGraph.studyStore.recordTurn(sid)
                    }
                }
            }
            is ChatEvent.Failed -> {
                val msg = ev.error.message ?: ev.error.javaClass.simpleName
                val a = messages.getOrNull(assistantIdx) as? ChatRow.Assistant ?: return@collect
                messages[assistantIdx] = a.copy(error = msg, done = true)
                error = msg
            }
        }
    }
}

private fun ChatState.endSession() {
    runCatching { session?.close() }
    session = null
    studySessionId = null
    messages.clear()
    pendingPaths.forEach { runCatching { File(it).delete() } }   // C9
    pendingPaths.clear()
    streaming = false
    error = null
}

private fun ChatState.removePendingImage(path: String) {
    if (pendingPaths.remove(path)) {
        runCatching { File(path).delete() }
    }
}

/* -------------------------------------------------------------------------- *
 * Chat 屏
 * -------------------------------------------------------------------------- */

@Composable
private fun ChatScreen(
    chat: ChatState,
    lang: Lang,
    ageBand: AgeBand,
    /**
     * v1.1:控制助手气泡上播放按钮的文案。
     * - true → "🔁 再听一遍"(假设刚才自动播过)
     * - false → "▶ 播放"(本轮没自动播,需要孩子主动点)
     * 行为相同(都触发 TTS),只是 label 不同;中途切换设置只影响标签,不影响已发生的播放。
     */
    autoPlayTts: Boolean,
    onAddImages: () -> Unit,
    onEndSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: (String) -> Unit,
    onRemovePending: (String) -> Unit,
    /**
     * v1.1:手动播放回调。挂起到 TTS 完成,UI 据此切"播放中"态。
     * 复用 [AppGraph.tts] 单例(Mutex 串行,内部抢占已有播放)。
     */
    onPlayAssistant: suspend (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新消息(或最新助手气泡有增量)时,自动滚到底部
    LaunchedEffect(chat.messages.size, (chat.messages.lastOrNull() as? ChatRow.Assistant)?.text?.length) {
        if (chat.messages.isNotEmpty()) {
            listState.animateScrollToItem(chat.messages.size - 1)
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_title_template, lang.toString(), ageBand.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // 顶栏 TextButton 走 token primary 色,儿童模式自然落到行动金
                TextButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalLumiTokens.current.primary),
                ) { Text(stringResource(R.string.btn_settings)) }
                TextButton(
                    onClick = onEndSession,
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalLumiTokens.current.primary),
                ) { Text(stringResource(R.string.btn_new_session)) }
            }
            HorizontalDivider()

            // 消息列表
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chat.messages.size) { i ->
                    when (val row = chat.messages[i]) {
                        is ChatRow.User      -> UserBubble(row)
                        is ChatRow.Assistant -> AssistantBubble(
                            row = row,
                            autoPlayTts = autoPlayTts,
                            onPlay = { onPlayAssistant(row.text) },
                        )
                    }
                }
            }

            // 待发送图片暂存
            if (chat.pendingPaths.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("📎 ${chat.pendingPaths.size}", style = MaterialTheme.typography.bodySmall)
                    chat.pendingPaths.forEach { path ->
                        val tag = runCatching { File(path).nameWithoutExtension }
                            .getOrDefault("img")
                            .takeLast(6)
                        AssistChip(
                            onClick = { onRemovePending(path) },
                            label = { Text("📷$tag ×") },
                        )
                    }
                }
            }

            // 错误提示
            chat.error?.let {
                Text(
                    "⚠ $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            HorizontalDivider()

            // 输入栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LumiOutlinedButton(
                    onClick = onAddImages,
                    enabled = !chat.streaming,
                    label = "📷",
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(stringResource(
                            if (chat.streaming) R.string.chat_input_placeholder_replying
                            else R.string.chat_input_placeholder_idle
                        ))
                    },
                    enabled = !chat.streaming,
                    maxLines = 4,
                )

                LumiPrimaryButton(
                    onClick = {
                        val trimmed = input.trim()
                        if (trimmed.isNotEmpty() || chat.pendingPaths.isNotEmpty()) {
                            onSend(trimmed)
                            input = ""
                        }
                    },
                    enabled = !chat.streaming &&
                        (input.isNotBlank() || chat.pendingPaths.isNotEmpty()),
                    label = stringResource(R.string.btn_send),
                )
            }
        }
    }
}

@Composable
private fun UserBubble(row: ChatRow.User) {
    val tokens = LocalLumiTokens.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = tokens.primary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(
                        topStart = tokens.cornerLarge,
                        topEnd = tokens.cornerLarge,
                        bottomStart = tokens.cornerLarge,
                        bottomEnd = 4.dp,
                    ),
                )
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.imagePaths.isNotEmpty()) {
                    Text(
                        stringResource(R.string.bubble_n_images, row.imagePaths.size),
                        color = tokens.ink,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (row.ocrSummary.isNotBlank()) {
                    Text(
                        stringResource(R.string.bubble_ocr_prefix, row.ocrSummary),
                        color = tokens.ink,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (row.labelsSummary.isNotBlank()) {
                    Text(
                        stringResource(R.string.bubble_labels_prefix, row.labelsSummary),
                        color = tokens.ink,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (row.text.isNotEmpty()) {
                    Text(
                        row.text,
                        color = tokens.ink,
                        fontSize = tokens.bodySp,
                    )
                }
                if (row.imagePaths.isEmpty() && row.text.isEmpty() &&
                    row.ocrSummary.isBlank() && row.labelsSummary.isBlank()) {
                    Text(
                        stringResource(R.string.bubble_empty),
                        color = tokens.ink,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 助手气泡。
 *
 * v1.1:本轮回复完成(`row.done && row.error == null && row.text.isNotEmpty`)后,
 * 末尾追加一个播放按钮:
 * - [autoPlayTts] = true → "🔁 再听一遍"(假设已自动播过,孩子可重播)
 * - [autoPlayTts] = false → "▶ 播放"(没自动播,孩子主动决定何时听)
 *
 * 点击挂起调 [onPlay],期间按钮置灰显示"朗读中…";完成/取消/异常都解灰。
 * [onPlay] 内部由 SherpaTtsEngine 的 speakMutex 串行,无需在 UI 再加锁。
 */
@Composable
private fun AssistantBubble(
    row: ChatRow.Assistant,
    autoPlayTts: Boolean,
    onPlay: suspend () -> Unit,
) {
    val tokens = LocalLumiTokens.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = tokens.brand,
                    shape = RoundedCornerShape(
                        topStart = tokens.cornerLarge,
                        topEnd = tokens.cornerLarge,
                        bottomStart = 4.dp,
                        bottomEnd = tokens.cornerLarge,
                    ),
                )
                .padding(12.dp),
        ) {
            Column {
                when {
                    row.error != null ->
                        Text("⚠ ${row.error}", color = MaterialTheme.colorScheme.error)

                    row.text.isEmpty() && !row.done ->
                        Text(
                            stringResource(R.string.assistant_thinking),
                            color = tokens.onBrand,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                    else -> {
                        Text(
                            row.text,
                            color = tokens.onBrand,
                            fontSize = tokens.bodySp,
                        )
                        if (!row.done) {
                            Text("▌", color = tokens.onBrand, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // 播放按钮:仅当本轮成功结束且有文本时展示。儿童模式深蓝气泡上,文字色走 onBrand(白)。
                if (row.done && row.error == null && row.text.isNotEmpty()) {
                    val labelRes = when {
                        playing       -> R.string.btn_playing
                        autoPlayTts   -> R.string.btn_play_again
                        else          -> R.string.btn_play
                    }
                    TextButton(
                        onClick = {
                            if (playing) return@TextButton
                            scope.launch {
                                playing = true
                                try { onPlay() }
                                catch (ce: kotlinx.coroutines.CancellationException) { throw ce }
                                catch (_: Throwable) { /* TTS 失败静默,不打扰孩子 */ }
                                finally { playing = false }
                            }
                        },
                        enabled = !playing,
                        colors = ButtonDefaults.textButtonColors(contentColor = tokens.onBrand),
                    ) { Text(stringResource(labelRes)) }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- *
 * Settings 屏(DataStore 持久化已接入,改完即写回,App 重启自动恢复)
 * -------------------------------------------------------------------------- */

@Composable
private fun SettingsScreen(
    lang: Lang,
    ageBand: AgeBand,
    ocrMode: OcrMode,
    selectedModel: GemmaModel,
    autoPlayTts: Boolean,
    outputMode: OutputMode,
    onLang: (Lang) -> Unit,
    onAge: (AgeBand) -> Unit,
    onOcrMode: (OcrMode) -> Unit,
    onSelectModel: (GemmaModel) -> Unit,
    onAutoPlayTts: (Boolean) -> Unit,
    onOutputMode: (OutputMode) -> Unit,
    onBack: () -> Unit,
    onOpenMyLearning: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 已安装模型集合(filesystem 状态,不是 Flow)。开页时初始化,导入/删除后手动 refresh。
    var installed by remember { mutableStateOf(ModelProvider.installedModels(context)) }
    fun refreshInstalled() { installed = ModelProvider.installedModels(context) }

    // 切换模型确认弹窗:用户点 E2B↔E4B 时弹一次,确认后再调 onSelectModel。
    var pendingSwitchTo by remember { mutableStateOf<GemmaModel?>(null) }

    // 导入进度态:null = 空闲;Pair(bytesCopied, totalBytes) = 进行中。
    var importTarget by remember { mutableStateOf<GemmaModel?>(null) }
    var importProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    // SAF 文件选择器:用户从 HF 下好 .litertlm 后,点"导入"调起这个。
    // 拿到 Uri 后用 ContentResolver 流式拷贝到 getExternalFilesDir。
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val target = importTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) {
            importTarget = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importProgress = 0L to -1L
            importError = null
            val result = ModelProvider.importFromUri(context, target, uri) { copied, total ->
                importProgress = copied to total
            }
            result.onSuccess {
                refreshInstalled()
                importProgress = null
                importTarget = null
            }.onFailure { t ->
                importError = t.message ?: t.javaClass.simpleName
                importProgress = null
                importTarget = null
            }
        }
    }

    AppBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        // ---- 显示模式 section(UI overhaul)----
        // 内部直接读 SettingsRepository.lumiModeFlow,不入侵 SettingsScreen 的现有参数链。
        // 演示卡是当前唯一读取 LocalLumiTokens 的节点 —— 切换时观察其圆角/字号/色块平滑过渡。
        HorizontalDivider()
        Text(stringResource(R.string.settings_section_mode), style = MaterialTheme.typography.titleMedium)
        ModeSwitchSection()

        // ---- 儿童模式守门(UI overhaul补丁,2026-05-25)----
        // 儿童模式下设置页只保留"显示模式"section(上面那段);完整配置仅家长模式可见。
        // 与家长门(ModeSwitchSection 内部 ParentGateDialog)形成两层防护:
        // 1) 儿童态根本看不到完整设置项(降低误触面)
        // 2) 想看到 → 必须先过家长门(防孩子误进)
        val currentMode by AppGraph.settings.lumiModeFlow.collectAsState(initial = LumiMode.Child)
        if (currentMode == LumiMode.Parent) {

        // ---- 界面语言 section(v1.1)----
        // 三态(跟随系统/中文/English)与"输出语言"完全独立 —— 设计规范反复强调
        HorizontalDivider()
        Text(stringResource(R.string.settings_section_ui_language), style = MaterialTheme.typography.titleMedium)
        UiLanguageSection()

        // ---- 模型选择 section(v1.1 2026-05-25)----
        HorizontalDivider()
        Text(stringResource(R.string.settings_section_model), style = MaterialTheme.typography.titleMedium)
        GemmaModel.entries.forEach { model ->
            ModelCard(
                model = model,
                isSelected = model == selectedModel,
                isInstalled = model in installed,
                onSelect = {
                    // 同模型 no-op;不同模型才弹确认
                    if (model != selectedModel) pendingSwitchTo = model
                },
                onOpenHf = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.hfModelPageUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                },
                onImport = {
                    importTarget = model
                    importError = null
                    // ".litertlm" 不在标准 MIME 表里,用 "*/*" 让用户自己选;
                    // 落地后 ModelProvider.locate 会按文件大小校验
                    importLauncher.launch(arrayOf("*/*"))
                },
                onDelete = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ModelProvider.delete(context, model) }
                        refreshInstalled()
                    }
                },
                isImporting = importTarget == model && importProgress != null,
                importProgress = if (importTarget == model) importProgress else null,
            )
        }
        importError?.let {
            Text(stringResource(R.string.settings_import_failed, it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()

        Text(stringResource(R.string.settings_output_lang_value, lang.toString()))
        LumiPrimaryButton(
            onClick = { onLang(if (lang == Lang.EN) Lang.ZH else Lang.EN) },
            label = stringResource(R.string.settings_btn_toggle_lang),
        )

        // ---- 输出模式 section(v1.1,2026-05-25)----
        // 与输出语言相邻但独立显示。RadioButton + 提示文字。
        HorizontalDivider()
        Text(stringResource(R.string.settings_section_output_mode), style = MaterialTheme.typography.titleMedium)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            OutputMode.entries.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = option == outputMode,
                        onClick = { if (option != outputMode) onOutputMode(option) },
                    )
                    Text(stringResource(
                        when (option) {
                            OutputMode.MONOLINGUAL -> R.string.output_mode_monolingual
                            OutputMode.BILINGUAL   -> R.string.output_mode_bilingual
                        }
                    ))
                }
            }
        }
        Text(
            stringResource(R.string.settings_output_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(stringResource(R.string.settings_age_value, ageBand.toString()))
        LumiPrimaryButton(
            onClick = {
                onAge(when (ageBand) {
                    AgeBand.TODDLER       -> AgeBand.PRESCHOOL
                    AgeBand.PRESCHOOL     -> AgeBand.PREADOLESCENT
                    AgeBand.PREADOLESCENT -> AgeBand.TODDLER
                })
            },
            label = stringResource(R.string.settings_btn_cycle_age),
        )

        // OCR 模式切换( + v1.1 联动 selectedModel)
        // ocrMode 显示的是用户**原始**偏好;effective 由 SettingsRepository 强制(E2B → OCR)
        val multimodalAllowed = selectedModel.supportsMultimodal
        val ocrModeText = stringResource(
            when (ocrMode) {
                OcrMode.OCR        -> R.string.ocr_mode_ocr
                OcrMode.MULTIMODAL -> R.string.ocr_mode_multimodal
            }
        )
        val ocrLabelRes = if (!multimodalAllowed && ocrMode == OcrMode.MULTIMODAL)
            R.string.settings_ocr_mode_label_with_warning
        else
            R.string.settings_ocr_mode_label
        Text(stringResource(ocrLabelRes, ocrModeText))
        LumiPrimaryButton(
            onClick = {
                onOcrMode(if (ocrMode == OcrMode.OCR) OcrMode.MULTIMODAL else OcrMode.OCR)
            },
            enabled = multimodalAllowed,
            label = stringResource(R.string.settings_btn_toggle_ocr_mode),
        )

        if (!multimodalAllowed) {
            Text(
                stringResource(R.string.settings_e2b_no_multimodal, stringResource(selectedModel.displayNameRes())),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (ocrMode == OcrMode.MULTIMODAL) {
            Text(
                stringResource(R.string.settings_multimodal_warn),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---- 朗读 section(v1.1,2026-05-25)----
        // 自动/手动开关。与三个语言概念正交,互不影响。
        HorizontalDivider()
        Text(stringResource(R.string.settings_section_tts), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_auto_play_tts),
                modifier = Modifier.weight(1f),
            )
            ChunkySwitch(
                checked = autoPlayTts,
                onCheckedChange = onAutoPlayTts,
            )
        }
        Text(
            stringResource(R.string.settings_auto_play_tts_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LumiOutlinedButton(onClick = onOpenMyLearning, label = stringResource(R.string.btn_my_learning))

        Text(
            stringResource(R.string.settings_footer_note),
            style = MaterialTheme.typography.bodySmall,
        )

        }  // end if (currentMode == LumiMode.Parent)

        // 返回按钮在两种模式下都显示,否则儿童态进入设置就出不去了
        LumiOutlinedButton(onClick = onBack, label = stringResource(R.string.btn_back))
    }
    }

    // 切换模型确认弹窗
    pendingSwitchTo?.let { target ->
        val installedOk = target in installed
        val targetDisplay = stringResource(target.displayNameRes())
        AlertDialog(
            onDismissRequest = { pendingSwitchTo = null },
            title = { Text(stringResource(R.string.model_switch_dialog_title, targetDisplay)) },
            text = {
                Text(
                    if (installedOk) {
                        stringResource(R.string.model_switch_dialog_confirm)
                    } else {
                        stringResource(R.string.model_switch_dialog_not_installed, target.fileName)
                    }
                )
            },
            confirmButton = {
                if (installedOk) {
                    TextButton(onClick = {
                        onSelectModel(target)
                        pendingSwitchTo = null
                    }) { Text(stringResource(R.string.btn_confirm)) }
                } else {
                    TextButton(onClick = { pendingSwitchTo = null }) { Text(stringResource(R.string.btn_ok)) }
                }
            },
            dismissButton = if (installedOk) {
                { TextButton(onClick = { pendingSwitchTo = null }) { Text(stringResource(R.string.btn_cancel)) } }
            } else null,
        )
    }
}

/**
 * 界面语言三选一(v1.1)。
 *
 * 当前态从 [AppCompatDelegate.getApplicationLocales] 读;切换时同步调
 * [AppCompatDelegate.setApplicationLocales],API 33+ 由 LocaleManager 接,
 * API 26-32 由 AppLocalesMetadataHolderService 接(见 Manifest)。
 * 切换后 AppCompat 会触发 Activity recreate,Compose 重组后自动回显新值。
 *
 * 注意:此 section 与 lang/ocrMode/selectedModel 完全无关 —— 三概念正交。
 */
@Composable
private fun UiLanguageSection() {
    // **2026-05-25 bugfix**:原先直接 `val current = currentAppUiLang`,问题是该函数读
    // `AppCompatDelegate.getApplicationLocales` 不被 Compose 观察,且:
    // - 点 SYSTEM 时若系统语言与之前显式钉死的语言一致,AppCompat 不会触发 Activity recreate
    // → Composable 不重组 → Radio 选中态不更新
    // - API 33+ `LocaleManager` 写入有微秒级延迟,即时读回可能拿到旧值
    // 修法:本地 `mutableStateOf` 做即时回显真源,初值从 AppCompat 读;点击时:
    // 1) 先更新本地 state(Radio 立刻翻)
    // 2) 再调 AppCompat 持久化(可能触发 recreate,新组合重新 init 本地 state)
    var current by remember { mutableStateOf(currentAppUiLang()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppUiLang.entries.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = option == current,
                    onClick = {
                        if (option == current) return@RadioButton
                        current = option
                        val locales = when (option) {
                            AppUiLang.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                            AppUiLang.ZH     -> LocaleListCompat.forLanguageTags("zh-CN")
                            AppUiLang.EN     -> LocaleListCompat.forLanguageTags("en-US")
                        }
                        AppCompatDelegate.setApplicationLocales(locales)
                    },
                )
                Text(stringResource(
                    when (option) {
                        AppUiLang.SYSTEM -> R.string.settings_ui_lang_follow_system
                        AppUiLang.ZH     -> R.string.settings_ui_lang_zh
                        AppUiLang.EN     -> R.string.settings_ui_lang_en
                    }
                ))
            }
        }
    }
}

/**
 * 单个模型卡片(设置页 v1.1)。
 *
 * 显示模型名 + 状态徽章(已安装 / 未安装),提供 4 个动作:
 * - 选中(Radio):仅当已安装且非当前时可点
 * - 打开 HF 下载页:浏览器 Intent
 * - 导入已下载文件:SAF
 * - 删除:已安装 + 非当前选中(当前选中不允许删,防止把脚下踩没)
 */
@Composable
private fun ModelCard(
    model: GemmaModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    onSelect: () -> Unit,
    onOpenHf: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    isImporting: Boolean,
    importProgress: Pair<Long, Long>?,
) {
    val tokens = LocalLumiTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cornerLarge))
            .background(tokens.surfaceBg)
            .border(1.dp, tokens.primary.copy(alpha = 0.25f), RoundedCornerShape(tokens.cornerLarge))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    enabled = isInstalled && !isSelected,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(model.displayNameRes()), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            if (isInstalled) R.string.model_status_installed
                            else R.string.model_status_missing
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInstalled)
                            tokens.primary
                        else
                            MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (isImporting) {
                val (copied, total) = importProgress ?: (0L to -1L)
                val copiedMb = (copied / 1_000_000).toInt()
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (copied.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.import_progress_with_total, copiedMb, (total / 1_000_000).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.import_progress_no_total, copiedMb),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LumiOutlinedButton(onClick = onOpenHf, label = stringResource(R.string.btn_open_hf))
                    LumiOutlinedButton(onClick = onImport, label = stringResource(R.string.btn_import_file))
                    if (isInstalled && !isSelected) {
                        LumiOutlinedButton(onClick = onDelete, label = stringResource(R.string.btn_delete))
                    }
                }
            }
        }
    }
}
