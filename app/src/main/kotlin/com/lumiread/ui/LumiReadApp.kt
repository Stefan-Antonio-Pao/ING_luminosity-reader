package com.lumiread.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiread.AppGraph
import com.lumiread.R
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
import com.lumiread.ui.screens.CelebrateScreen
import com.lumiread.ui.screens.DialogScreen
import com.lumiread.ui.screens.KidsHomeScreen
import com.lumiread.ui.screens.ParentGateScreen
import com.lumiread.ui.screens.ParentHomeScreen
import com.lumiread.ui.screens.ParentSettingsScreen
import com.lumiread.ui.screens.PrivacyScreen
import com.lumiread.ui.screens.StoryScreen
import com.lumiread.ui.screens.ThinkingScreen
import com.lumiread.ui.theme.LumiMode
import com.lumiread.ui.theme.LumiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * LumiRead v3.0.0 导航宿主。
 *
 * 完整拍读链:KIDS_HOME → CAMERA → (preview+crop) → CELEBRATE → THINKING → DIALOG;
 * 故事模式 STORY;家长区 PARENT_GATE → PARENT_HOME → PARENT_SETTINGS → PRIVACY。
 *
 * 关键约束(screen-mapping / compose-notes):
 *  - 启动一律回 Kids(不持久化 parent mode);tier 仍由 DataStore 持久化。
 *  - 主题 mode 跟随**当前所在区域**(家长屏=Parent 调色板,其余=Kids),不再读 lumiModeFlow。
 *  - 进家长必须过数字门禁(PARENT_GATE),不可绕过;退出家长直接回 KIDS_HOME。
 *
 * 推理链路完全复用既有 [ChatState]/[ChatSession],未改 :core 任何签名。
 */
enum class Screen {
    KIDS_HOME, CAMERA, CELEBRATE, THINKING, DIALOG, STORY,
    PARENT_GATE, PARENT_HOME, PARENT_SETTINGS, PRIVACY,
}

/** [GemmaModel] 的本地化显示名;:core 不依赖 Android,无法直接持有 R.string id。 */
@StringRes
fun GemmaModel.displayNameRes(): Int = when (this) {
    GemmaModel.E2B -> R.string.model_e2b_display_name
    GemmaModel.E4B -> R.string.model_e4b_display_name
}

@Composable
fun LumiReadApp() {
    // rememberSaveable:跨 Activity.recreate()(切换界面语言会触发 recreate)保留当前屏 ——
    // 修复"切换系统/界面语言后跳回首页"的 bug,语言切换后应停留在设置页。
    // 真正的冷启动(从启动器全新打开)无 savedInstanceState → 回退到初值 KIDS_HOME,
    // 仍满足"启动一律回 Kids、不持久化 parent mode"(saveable 只存实例态,不落盘)。
    var screen by rememberSaveable { mutableStateOf(Screen.KIDS_HOME) }
    val lang by AppGraph.settings.langFlow.collectAsState(initial = Lang.EN)
    val age by AppGraph.settings.ageFlow.collectAsState(initial = AgeBand.PRESCHOOL)
    val effectiveOcrMode by AppGraph.settings.effectiveOcrModeFlow.collectAsState(initial = OcrMode.OCR)
    val selectedModel by AppGraph.settings.selectedModelFlow.collectAsState(initial = GemmaModel.E2B)
    val autoPlayTts by AppGraph.settings.autoPlayTtsFlow.collectAsState(initial = true)
    val outputMode by AppGraph.settings.outputModeFlow.collectAsState(initial = OutputMode.MONOLINGUAL)
    val studyRecords by AppGraph.studyStore.all.collectAsState(initial = emptyList())

    val chat = rememberChatState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 相机来源:从 KIDS_HOME(绘本)还是 STORY(物品)进入,决定取消时返回哪屏。
    var cameraOrigin by remember { mutableStateOf(Screen.KIDS_HOME) }

    // 首次启动引导(并入 PR #1 onboarding):无模型时弹下载引导。
    var showOnboarding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (ModelProvider.installedModels(context).isEmpty()) showOnboarding = true
    }

    // 主题 mode 跟随区域:家长屏 → Parent 调色板,其余 → Kids。
    val inParent = screen == Screen.PARENT_HOME || screen == Screen.PARENT_SETTINGS || screen == Screen.PRIVACY
    val mode = if (inParent) LumiMode.Parent else LumiMode.Child

    // 启动恢复:有历史会话则装回 messages(停在首页,首页给"继续上次"chip),不自动跳转。
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasLoaded) return@LaunchedEffect
        val saved = AppGraph.chatStore.load()
        if (saved.isNotEmpty() && chat.messages.isEmpty()) chat.messages.addAll(saved)
        hasLoaded = true
    }
    LaunchedEffect(hasLoaded) {
        if (!hasLoaded) return@LaunchedEffect
        snapshotFlow { chat.messages.toList() }.drop(1).collect { runCatching { AppGraph.chatStore.save(it) } }
    }

    // THINKING → DIALOG:监听首个 token / 完成 / 失败到达即跳转(screen-mapping 状态机)。
    LaunchedEffect(screen) {
        if (screen != Screen.THINKING) return@LaunchedEffect
        snapshotFlow {
            val a = chat.messages.lastOrNull() as? ChatRow.Assistant
            Triple(a?.text?.isNotEmpty() ?: false, a?.done ?: false, a?.error != null)
        }.collect { (hasText, done, failed) ->
            if (hasText || done || failed) { screen = Screen.DIALOG }
        }
    }

    BackHandler(enabled = screen != Screen.KIDS_HOME) {
        screen = when (screen) {
            Screen.CAMERA -> cameraOrigin
            Screen.CELEBRATE, Screen.THINKING, Screen.DIALOG, Screen.STORY -> Screen.KIDS_HOME
            Screen.PARENT_GATE, Screen.PARENT_HOME -> Screen.KIDS_HOME
            Screen.PARENT_SETTINGS -> Screen.PARENT_HOME
            Screen.PRIVACY -> Screen.PARENT_SETTINGS
            Screen.KIDS_HOME -> Screen.KIDS_HOME
        }
    }

    LumiTheme(mode = mode, ageBand = age) {
        Box(Modifier.fillMaxSize()) {
            if (AppGraph.isFakeMode) FakeModeBanner()
            when (screen) {
                Screen.KIDS_HOME -> KidsHomeScreen(
                    hasSession = chat.hasSession || chat.messages.isNotEmpty(),
                    onTakePhoto = { cameraOrigin = Screen.KIDS_HOME; screen = Screen.CAMERA },
                    onStory = { screen = Screen.STORY },
                    onContinue = { screen = Screen.DIALOG },
                    onParent = { screen = Screen.PARENT_GATE },
                )
                Screen.CAMERA -> CameraCaptureScreen(
                    onCaptured = { paths ->
                        chat.startNewSession(scope, lang, age, effectiveOcrMode, autoPlayTts, outputMode, paths)
                        screen = Screen.CELEBRATE
                    },
                    onOpenSettings = { screen = Screen.PARENT_GATE },
                    onCancel = { screen = cameraOrigin },
                )
                Screen.CELEBRATE -> {
                    CelebrateScreen()
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(1100)
                        if (screen == Screen.CELEBRATE) screen = Screen.THINKING
                    }
                }
                Screen.THINKING -> ThinkingScreen()
                Screen.DIALOG -> DialogScreen(
                    messages = chat.messages,
                    streaming = chat.streaming,
                    error = chat.error,
                    onSend = { text -> chat.sendUserTurn(scope, lang, age, effectiveOcrMode, autoPlayTts, outputMode, text) },
                    onPlay = { text -> AppGraph.tts.speak(text, lang, age) },
                    onRetake = { chat.endSession(); cameraOrigin = Screen.KIDS_HOME; screen = Screen.CAMERA },
                    onNewPage = { chat.endSession(); cameraOrigin = Screen.KIDS_HOME; screen = Screen.CAMERA },
                    onBack = { screen = Screen.KIDS_HOME },
                )
                Screen.STORY -> StoryScreen(
                    lang = lang,
                    age = age,
                    onSnapObject = { cameraOrigin = Screen.STORY; screen = Screen.CAMERA },
                    onStartStory = { opening ->
                        chat.startStory(scope, lang, age, effectiveOcrMode, autoPlayTts, outputMode, opening)
                        screen = Screen.THINKING
                    },
                    onBack = { screen = Screen.KIDS_HOME },
                )
                Screen.PARENT_GATE -> ParentGateScreen(
                    onPass = { screen = Screen.PARENT_HOME },
                    onBack = { screen = Screen.KIDS_HOME },
                )
                Screen.PARENT_HOME -> ParentHomeScreen(
                    records = studyRecords,
                    onOpenSettings = { screen = Screen.PARENT_SETTINGS },
                    onExit = { screen = Screen.KIDS_HOME },
                    onClear = { scope.launch { AppGraph.studyStore.clearAll() } },
                )
                Screen.PARENT_SETTINGS -> ParentSettingsScreen(
                    lang = lang,
                    ageBand = age,
                    outputMode = outputMode,
                    autoPlayTts = autoPlayTts,
                    selectedModel = selectedModel,
                    onTier = { newAge -> scope.launch { AppGraph.settings.setAge(newAge) } },
                    onToggleLang = { scope.launch { AppGraph.settings.setLang(if (lang == Lang.EN) Lang.ZH else Lang.EN) } },
                    onBilingual = { on -> scope.launch { AppGraph.settings.setOutputMode(if (on) OutputMode.BILINGUAL else OutputMode.MONOLINGUAL) } },
                    onAutoTts = { v -> scope.launch { AppGraph.settings.setAutoPlayTts(v) } },
                    onSelectModel = { newModel ->
                        scope.launch {
                            chat.endSession()
                            AppGraph.settings.setSelectedModel(newModel)
                        }
                    },
                    onOpenPrivacy = { screen = Screen.PRIVACY },
                    onBack = { screen = Screen.PARENT_HOME },
                )
                Screen.PRIVACY -> PrivacyScreen(onBack = { screen = Screen.PARENT_SETTINGS })
            }

            // 首次启动引导弹窗(无模型时):去 HF 下载 / 去设置导入。首启 onboarding 直达设置
            // (此时还没有模型可保护,无需经家长门)。
            if (showOnboarding) {
                AlertDialog(
                    onDismissRequest = { showOnboarding = false },
                    title = { Text(stringResource(R.string.onboarding_title)) },
                    text = { Text(stringResource(R.string.onboarding_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showOnboarding = false
                            screen = Screen.PARENT_SETTINGS
                        }) { Text(stringResource(R.string.onboarding_btn_settings)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showOnboarding = false
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(GemmaModel.E2B.hfModelPageUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text(stringResource(R.string.onboarding_btn_open_hf)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun FakeModeBanner() {
    Box(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFB00020)).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.fake_mode_banner), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/* -------------------------------------------------------------------------- *
 * 聊天状态 + 操作函数(沿用既有实现,未改动推理链路语义;新增 startStory)。
 * -------------------------------------------------------------------------- */

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
    ocr.flatMap { it.lines }.joinToString(" / ") { it.text }.take(160)

private fun summarizeLabels(labels: List<Label>): String =
    labels.joinToString(", ") { it.name }

class ChatState {
    val messages: androidx.compose.runtime.snapshots.SnapshotStateList<ChatRow> = androidx.compose.runtime.mutableStateListOf()
    val pendingPaths: androidx.compose.runtime.snapshots.SnapshotStateList<String> = androidx.compose.runtime.mutableStateListOf()
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    internal var session: ChatSession? = null
    internal val mutex = Mutex()
    internal var studySessionId: Long? = null

    val hasSession: Boolean get() = session != null
}

@Composable
private fun rememberChatState(): ChatState = remember { ChatState() }

private fun ChatState.startNewSession(
    scope: CoroutineScope,
    lang: Lang,
    age: AgeBand,
    ocrMode: OcrMode,
    autoPlayTts: Boolean,
    outputMode: OutputMode,
    paths: List<String>,
) {
    if (streaming) return
    runCatching { session?.close() }
    session = null
    studySessionId = null
    messages.clear()
    pendingPaths.forEach { runCatching { File(it).delete() } }
    pendingPaths.clear()
    error = null

    val validPaths = paths.filter { runCatching { File(it).isFile && File(it).length() > 0 }.getOrDefault(false) }
    if (validPaths.isEmpty()) {
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
                streaming = false
            }
        }
    }
}

/**
 * 故事模式起头(Phase 6,用户授权的 app 层补全)。开一段无图会话,把 [opening] 作为首轮
 * 用户文本发给真实模型链路(走 ChatSession.userTurn)。场景区分(BOOK_PAGE/OBJECT/STORY)
 * 仍由 :core 的 classifyScene 工具在生成时动态决定,**未改 core 签名**。
 */
private fun ChatState.startStory(
    scope: CoroutineScope,
    lang: Lang,
    age: AgeBand,
    ocrMode: OcrMode,
    autoPlayTts: Boolean,
    outputMode: OutputMode,
    opening: String,
) {
    if (streaming) return
    runCatching { session?.close() }
    session = null
    studySessionId = null
    messages.clear()
    pendingPaths.forEach { runCatching { File(it).delete() } }
    pendingPaths.clear()
    error = null

    val userIdx = messages.size
    messages += ChatRow.User(text = opening, imagePaths = emptyList(), ocrSummary = "", labelsSummary = "")
    val assistantIdx = messages.size
    messages += ChatRow.Assistant(text = "", done = false, error = null)
    streaming = true

    scope.launch {
        mutex.withLock {
            try {
                val sess = AppGraph.pipeline.startChat(lang, age, ocrMode, autoPlayTts, outputMode)
                session = sess
                runFlow(sess.userTurn(opening, emptyList()), userIdx, assistantIdx, lang, age)
            } catch (t: Throwable) {
                val a = messages.getOrNull(assistantIdx) as? ChatRow.Assistant
                val msg = t.message ?: t.javaClass.simpleName
                if (a != null) messages[assistantIdx] = a.copy(error = msg, done = true)
                error = msg
            } finally {
                streaming = false
            }
        }
    }
}

private fun ChatState.sendUserTurn(
    scope: CoroutineScope,
    lang: Lang,
    age: AgeBand,
    ocrMode: OcrMode,
    autoPlayTts: Boolean,
    outputMode: OutputMode,
    text: String,
) {
    if (streaming) return
    val pending = pendingPaths.toList()
        .filter { runCatching { File(it).isFile && File(it).length() > 0 }.getOrDefault(false) }
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
                    val a = messages.getOrNull(assistantIdx) as? ChatRow.Assistant
                    if (a != null) messages[assistantIdx] = a.copy(error = msg, done = true)
                    error = msg
                    return@withLock
                }
                runFlow(sess.userTurn(text, images), userIdx, assistantIdx, lang, age)
            }
        } finally {
            streaming = false
        }
    }
}

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
                    ocrSummary = summarizeOcr(ev.ocr),
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

                val u = messages.getOrNull(userIdx) as? ChatRow.User
                val summary = listOfNotNull(
                    u?.ocrSummary?.takeIf { it.isNotBlank() }?.let { "OCR: $it" },
                    u?.labelsSummary?.takeIf { it.isNotBlank() }?.let { "标签: $it" },
                ).joinToString(" | ")
                runCatching {
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
    pendingPaths.forEach { runCatching { File(it).delete() } }
    pendingPaths.clear()
    streaming = false
    error = null
}
