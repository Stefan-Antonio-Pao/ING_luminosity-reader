package com.lumiread.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lumiread.R
import com.lumiread.ui.ChatRow
import com.lumiread.ui.components.LumiIcon
import com.lumiread.ui.components.LumiIconButton
import com.lumiread.ui.components.LumiScreenBackground
import com.lumiread.ui.components.OfflinePill
import com.lumiread.ui.components.PromptChip
import com.lumiread.ui.components.ReadingBubble
import com.lumiread.ui.components.TtsPlayButton
import com.lumiread.ui.components.UserMessageBubble
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalTier
import kotlinx.coroutines.launch

/**
 * 阅读对话屏(screen 07)。展示 AI 回答 + 用户追问;底部文本输入 + 麦克风 + 发送。
 * 复用现有 ChatState 链路:[onSend] 走 ChatSession.userTurn;[onPlay] 走 SherpaTtsEngine。
 *
 * 麦克风:用系统 [RecognizerIntent](无需额外依赖);不可用时禁用并提示,不崩溃(Phase 7)。
 * TTS 不可用走 inline,不弹错误页。
 */
@Composable
fun DialogScreen(
    messages: List<ChatRow>,
    streaming: Boolean,
    error: String?,
    onSend: (String) -> Unit,
    onPlay: suspend (String) -> Unit,
    onRetake: () -> Unit,
    onNewPage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    val tier = LocalTier.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var micNote by remember { mutableStateOf<String?>(null) }
    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) input = if (input.isBlank()) spoken else "$input $spoken"
        }
    }
    fun launchMic() {
        if (!micAvailable) { micNote = context.getString(R.string.lr_mic_unavailable); return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        runCatching { micLauncher.launch(intent) }
            .onFailure { micNote = context.getString(R.string.lr_mic_unavailable) }
    }

    LaunchedEffect(messages.size, (messages.lastOrNull() as? ChatRow.Assistant)?.text?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LumiScreenBackground(modifier = modifier, decor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            LumiTopBar(
                title = stringResource(R.string.lr_dialog_title),
                onBack = onBack,
                trailing = {
                    LumiIconButton(R.drawable.ic_lumi_camera, stringResource(R.string.lr_dialog_new_page), onNewPage)
                },
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages.size) { i ->
                    when (val row = messages[i]) {
                        is ChatRow.User -> if (row.text.isNotBlank()) UserMessageBubble(row.text)
                        is ChatRow.Assistant -> {
                            if (row.error != null) {
                                // 失败:友好提示,不吓人(toddler 不朗读错误)
                                ReadingBubble(text = stringResource(R.string.lr_err_ocr_b))
                            } else {
                                ReadingBubble(
                                    text = row.text,
                                    streaming = !row.done,
                                    footer = {
                                        if (row.done && row.text.isNotEmpty()) {
                                            TtsFooter(text = row.text, onPlay = onPlay)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            micNote?.let {
                Text(
                    it,
                    color = palette.textMuted,
                    fontSize = tier.fsCaption,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // 底部输入栏:麦克风 + 文本框 + 发送
            HorizontalDivider(color = palette.border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LumiIconButton(
                    resId = R.drawable.ic_lumi_mic,
                    contentDescription = stringResource(R.string.lr_dialog_mic_cd),
                    onClick = { launchMic() },
                    enabled = !streaming,
                )
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    enabled = !streaming,
                    placeholder = {
                        Text(stringResource(tierRes(tier, R.string.lr_dialog_input_toddler, R.string.lr_dialog_input_preschool, R.string.lr_dialog_input_pre)))
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.bgElevated,
                        unfocusedContainerColor = palette.bgElevated,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { submit(input, streaming) { onSend(it); input = "" } },
                    ),
                )
                LumiIconButton(
                    resId = R.drawable.ic_lumi_arrow,
                    contentDescription = stringResource(R.string.lr_dialog_send_cd),
                    onClick = { submit(input, streaming) { onSend(it); input = "" } },
                    enabled = !streaming && input.isNotBlank(),
                    tint = palette.primary,
                )
            }
        }
    }
}

private inline fun submit(text: String, streaming: Boolean, send: (String) -> Unit) {
    val t = text.trim()
    if (!streaming && t.isNotEmpty()) send(t)
}

@Composable
private fun TtsFooter(text: String, onPlay: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TtsPlayButton(
            playing = playing,
            onToggle = {
                if (playing) return@TtsPlayButton
                scope.launch {
                    playing = true
                    try { onPlay(text) }
                    catch (ce: kotlinx.coroutines.CancellationException) { throw ce }
                    catch (_: Throwable) { /* TTS 失败静默,文字仍可读 */ }
                    finally { playing = false }
                }
            },
            playLabel = stringResource(R.string.lr_dialog_tts_play),
            pauseLabel = stringResource(R.string.lr_dialog_tts_pause),
        )
    }
}
