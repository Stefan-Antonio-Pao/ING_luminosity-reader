package com.lumiread.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiread.AppGraph
import com.lumiread.camera.CameraCaptureScreen
import com.lumiread.core.AgeBand
import com.lumiread.core.ImageInput
import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrMode
import com.lumiread.core.OcrResult
import com.lumiread.core.pipeline.ChatEvent
import com.lumiread.core.pipeline.ChatSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 三屏:Capture / Chat / Settings(多轮聊天屏)。
 *
 *  - 设置(lang/age)由 [AppGraph.settings] 通过 DataStore 持久化,UI 用 `collectAsState` 订阅
 *  - 当前会话由 [AppGraph.chatStore] 序列化到 `filesDir/chat/current.json`,启动自动恢复
 *  - 全面屏:Activity 已 `enableEdgeToEdge`,这里用 `statusBarsPadding/.imePadding/.navigationBarsPadding`
 *    把内容压在系统栏内,顶部不再被状态栏盖住
 *
 * 顶部贴一条醒目的"FAKE MODE"红色水印,防止演示时把桩件输出误当真实功能。
 * 任何 Fake 实现没被替换掉,这条都会一直挂着。
 */
enum class Screen { CAPTURE, CHAT, SETTINGS, MY_LEARNING }

/**
 * 进相机的意图:
 *  - [NEW]    = 拍完开启一段新会话(用首批图启动 ChatSession)
 *  - [ATTACH] = 拍完把图加进当前会话的"待发送"暂存,与下一条文本一起发出去
 */
enum class CaptureIntent { NEW, ATTACH }

@Composable
fun LumiReadApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.CAPTURE) }
    val lang by AppGraph.settings.langFlow.collectAsState(initial = Lang.EN)
    val age  by AppGraph.settings.ageFlow.collectAsState(initial = AgeBand.PRESCHOOL)
    val ocrMode by AppGraph.settings.ocrModeFlow.collectAsState(initial = OcrMode.OCR)
    val studyRecords by AppGraph.studyStore.all.collectAsState(initial = emptyList())
    // captureIntent / settingsReturnTo:UI 路由短期态,不必跨进程恢复
    var captureIntent by remember { mutableStateOf(CaptureIntent.NEW) }
    var settingsReturnTo by remember { mutableStateOf(Screen.CAPTURE) }
    val chat = rememberChatState()
    val scope = rememberCoroutineScope()

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
                        when (captureIntent) {
                            CaptureIntent.NEW -> {
                                chat.startNewSession(scope, lang, age, ocrMode, paths)
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
                        // 若已有进行中的会话,允许返回 CHAT;否则停在原地(Capture 是首屏)
                        if (chat.hasSession || chat.messages.isNotEmpty()) screen = Screen.CHAT
                    },
                    // "直接开始对话":仅在 NEW intent(开新会话路径)下提供;
                    // ATTACH intent(中途加图)显示这个按钮会语义混乱。
                    onStartChatDirect = if (captureIntent == CaptureIntent.NEW) {
                        {
                            chat.startChatWithoutImages(scope, lang, age, ocrMode)
                            screen = Screen.CHAT
                        }
                    } else null,
                )
                Screen.CHAT -> ChatScreen(
                    chat = chat,
                    lang = lang,
                    ageBand = age,
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
                    onSend = { text -> chat.sendUserTurn(scope, lang, age, ocrMode, text) },
                    onRemovePending = { path -> chat.removePendingImage(path) },
                )
                Screen.SETTINGS -> SettingsScreen(
                    lang = lang,
                    ageBand = age,
                    ocrMode = ocrMode,
                    onLang = { newLang -> scope.launch { AppGraph.settings.setLang(newLang) } },
                    onAge  = { newAge  -> scope.launch { AppGraph.settings.setAge(newAge)  } },
                    onOcrMode = { newMode -> scope.launch { AppGraph.settings.setOcrMode(newMode) } },
                    onBack = { screen = settingsReturnTo },
                    onOpenMyLearning = { screen = Screen.MY_LEARNING },
                )
                Screen.MY_LEARNING -> MyLearningScreen(
                    records = studyRecords,
                    onBack  = { screen = Screen.SETTINGS },
                    onClear = { scope.launch { AppGraph.studyStore.clearAll() } },
                )
            }
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
            text = "FAKE MODE · 演示桩件 · 非真实推理",
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
 *   ocrSummary 用 " / " 拼接每行 OCR 文本(截 160 字),labelsSummary 用 ", " 拼接标签名。
 *   持久化时只存摘要,不存原始 OcrResult/Label。
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
 *  - [messages] / [pendingPaths] 用 SnapshotStateList,UI 自动观察增删
 *  - [streaming] / [error] 是 mutableStateOf,UI 自动观察值变化
 *  - [session] 故意不是 State:它只在事件回调里读(非 Composable 上下文),不需要触发重组
 *  - [mutex] 串行化每轮请求(LiteRT-LM 单会话不可重入,详见 ChatSession 文档)
 */
class ChatState {
    val messages: androidx.compose.runtime.snapshots.SnapshotStateList<ChatRow> = mutableStateListOf()
    val pendingPaths: androidx.compose.runtime.snapshots.SnapshotStateList<String> = mutableStateListOf()
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    internal var session: ChatSession? = null
    internal val mutex = Mutex()

    /**
     * 当前"学习记录会话"在 Room 中的行 id。
     *  - 首轮 AssistantDone 时,若为 null → 调 [StudyStore.beginSession] 插行并接住 id
     *  - 后续 AssistantDone → 调 [StudyStore.recordTurn] 刷 endedAt 与 turnCount
     *  - 切「↻ 新会话」/ 开新会话 / 直聊起 → 一律置 null,下一轮会重起一条
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
        error = "图片已被系统清理,请重拍。"
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
                val newSession = AppGraph.pipeline.startChat(lang, age, ocrMode)
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
                    val newSession = AppGraph.pipeline.startChat(lang, age, ocrMode)
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
 * 注意此时 **LLM 没有过往 KV cache**,只看到这一轮的内容 —— 这是当前阶段可接受的限制。
 */
private fun ChatState.sendUserTurn(
    scope: CoroutineScope,
    lang: Lang,
    age: AgeBand,
    ocrMode: OcrMode,
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
                    AppGraph.pipeline.startChat(lang, age, ocrMode).also { session = it }
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
 *      避免用户在 streaming 中按「↻ 新会话」清空 messages 后,后到的 Flow 回调撞 ClassCastException
 *      / IndexOutOfBoundsException。
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

                // 学习记录 —— 首轮插行,后续每轮 UPDATE。
                // 失败分支(Failed)不计入;空文本助手回应仍记一轮(用户花了时间)。
                val u = messages.getOrNull(userIdx) as? ChatRow.User
                val summary = listOfNotNull(
                    u?.ocrSummary?.takeIf { it.isNotBlank() }?.let { "OCR: $it" },
                    u?.labelsSummary?.takeIf { it.isNotBlank() }?.let { "标签: $it" },
                ).joinToString(" | ")
                runCatching {   // DB 异常不阻断对话
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
    onAddImages: () -> Unit,
    onEndSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: (String) -> Unit,
    onRemovePending: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新消息(或最新助手气泡有增量)时,自动滚到底部
    LaunchedEffect(chat.messages.size, (chat.messages.lastOrNull() as? ChatRow.Assistant)?.text?.length) {
        if (chat.messages.isNotEmpty()) {
            listState.animateScrollToItem(chat.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "💬 LumiRead · $lang · $ageBand",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) { Text("⚙ 设置") }
            TextButton(onClick = onEndSession) { Text("↻ 新会话") }
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
                    is ChatRow.Assistant -> AssistantBubble(row)
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
            OutlinedButton(
                onClick = onAddImages,
                enabled = !chat.streaming,
            ) { Text("📷") }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (chat.streaming) "对方正在回复…" else "说点什么…") },
                enabled = !chat.streaming,
                maxLines = 4,
            )

            Button(
                onClick = {
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty() || chat.pendingPaths.isNotEmpty()) {
                        onSend(trimmed)
                        input = ""
                    }
                },
                enabled = !chat.streaming &&
                    (input.isNotBlank() || chat.pendingPaths.isNotEmpty()),
            ) { Text("发送") }
        }
    }
}

@Composable
private fun UserBubble(row: ChatRow.User) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (row.imagePaths.isNotEmpty()) {
                    Text(
                        "📷 ${row.imagePaths.size} 张图",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (row.ocrSummary.isNotBlank()) {
                    Text(
                        "OCR: ${row.ocrSummary}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (row.labelsSummary.isNotBlank()) {
                    Text(
                        "标签: ${row.labelsSummary}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (row.text.isNotEmpty()) {
                    Text(row.text, style = MaterialTheme.typography.bodyLarge)
                }
                if (row.imagePaths.isEmpty() && row.text.isEmpty() &&
                    row.ocrSummary.isBlank() && row.labelsSummary.isBlank()) {
                    Text("(空消息)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(row: ChatRow.Assistant) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when {
                    row.error != null ->
                        Text("⚠ ${row.error}", color = MaterialTheme.colorScheme.error)

                    row.text.isEmpty() && !row.done ->
                        Text("(LumiRead 正在思考…)", style = MaterialTheme.typography.bodyMedium)

                    else -> {
                        Text(row.text, style = MaterialTheme.typography.bodyLarge)
                        if (!row.done) {
                            Text("▌", style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
    onLang: (Lang) -> Unit,
    onAge: (AgeBand) -> Unit,
    onOcrMode: (OcrMode) -> Unit,
    onBack: () -> Unit,
    onOpenMyLearning: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("LumiRead · 设置", style = MaterialTheme.typography.headlineSmall)

        Text("输出语言: $lang")
        Button(onClick = { onLang(if (lang == Lang.EN) Lang.ZH else Lang.EN) }) {
            Text("切换输出语言 (EN ↔ ZH)")
        }

        Text("年龄段: $ageBand")
        Button(onClick = {
            onAge(when (ageBand) {
                AgeBand.TODDLER       -> AgeBand.PRESCHOOL
                AgeBand.PRESCHOOL     -> AgeBand.PREADOLESCENT
                AgeBand.PREADOLESCENT -> AgeBand.TODDLER
            })
        }) { Text("循环年龄段") }

        // OCR 模式切换
        Text(
            "OCR 模式: " + when (ocrMode) {
                OcrMode.OCR        -> "独立 OCR(推荐)"
                OcrMode.MULTIMODAL -> "原生多模态(实验)"
            }
        )
        Button(onClick = {
            onOcrMode(if (ocrMode == OcrMode.OCR) OcrMode.MULTIMODAL else OcrMode.OCR)
        }) { Text("切换 OCR ↔ 多模态") }

        if (ocrMode == OcrMode.MULTIMODAL) {
            Text(
                "(实验)端侧多模态延迟显著,可能 10s+ 才出首字。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedButton(onClick = onOpenMyLearning) { Text("📚 我的学习") }

        OutlinedButton(onClick = onBack) { Text("← 返回") }

        Text(
            "(语言/年龄段/OCR 模式已接 DataStore,改完立刻写回,重启自动恢复。" +
                "切换在下一次新会话或下一轮发送时生效。)",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
