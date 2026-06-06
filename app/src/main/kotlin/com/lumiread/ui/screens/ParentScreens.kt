package com.lumiread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumiread.R
import com.lumiread.core.Lang
import com.lumiread.data.StudyRecord
import com.lumiread.ui.components.LumiPrimaryButton
import com.lumiread.ui.components.LumiScreenBackground
import com.lumiread.ui.components.LumiSecondaryButton
import com.lumiread.ui.components.ParentSectionHeader
import com.lumiread.ui.components.StatCard
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LumiColors
import kotlin.random.Random

/**
 * 家长门禁(screen 09)。完全离线:展示 4 个数字,点最大的进入。错误重出题。
 * 数字按钮 cd 读出数字本身(accessibility §4)。
 */
@Composable
fun ParentGateScreen(
    onPass: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    var challenge by remember { mutableStateOf(makeChallenge()) }
    var wrong by remember { mutableStateOf(false) }
    var passed by remember { mutableStateOf(false) }

    LaunchedEffect(passed) {
        if (passed) {
            kotlinx.coroutines.delay(520)
            onPass()
        }
    }

    LumiScreenBackground(modifier = modifier, decor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.lr_gate_title),
                color = palette.text,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
            )
            Text(
                stringResource(if (passed) R.string.lr_gate_right else if (wrong) R.string.lr_gate_wrong else R.string.lr_gate_body),
                color = if (passed) LumiColors.Success else if (wrong) LumiColors.Error else palette.textSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            // 2x2 网格
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (rowIdx in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (colIdx in 0 until 2) {
                            val n = challenge.numbers[rowIdx * 2 + colIdx]
                            NumberTile(
                                number = n,
                                enabled = !passed,
                                onClick = {
                                    if (n == challenge.answer) {
                                        passed = true
                                    } else {
                                        wrong = true
                                        challenge = makeChallenge()
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.lr_gate_privacy),
                color = palette.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            LumiSecondaryButton(
                onClick = onBack,
                label = stringResource(R.string.lr_err_home),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun NumberTile(number: Int, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    val cd = stringResource(R.string.lr_gate_number_cd, number)
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 36.sp)
    }
}

private data class GateChallenge(val numbers: List<Int>, val answer: Int)

private fun makeChallenge(): GateChallenge {
    val set = sortedSetOf<Int>()
    while (set.size < 4) set.add(Random.nextInt(11, 99))
    val nums = set.toList().shuffled()
    return GateChallenge(nums, nums.max())
}

/**
 * 家长首页 / 我的学习(screen 10)。冷静现代:本周 hero + 统计卡 + 最近会话 + 隐私卡。
 * 数据来自真实 [StudyRecord]。无任何账号/上传/云端/订阅措辞。
 */
@Composable
fun ParentHomeScreen(
    records: List<StudyRecord>,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    val totalSessions = records.size
    val totalTurns = records.sumOf { it.turnCount }
    val totalMinutes = records.sumOf { ((it.endedAt - it.startedAt).coerceAtLeast(0)) / 60000 }
    val zh = records.count { it.lang == Lang.ZH.name }
    val topLang = if (records.isEmpty()) "—" else if (zh >= records.size - zh) "中文" else "EN"
    val hours = String.format("%.1f", totalMinutes / 60.0)

    LumiScreenBackground(modifier = modifier, decor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    ParentSectionHeader(stringResource(R.string.lr_parent_mode_label))
                    Text(
                        stringResource(R.string.lr_parent_title),
                        color = palette.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                }
                Text(
                    stringResource(R.string.lr_parent_exit),
                    color = palette.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onExit).padding(8.dp),
                )
            }

            // hero:本周阅读
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(LumiColors.Ink700, LumiColors.Ink900)))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.lr_parent_week_label), color = LumiColors.Gold300, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.lr_parent_week_unit, hours, totalSessions),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    )
                }
            }

            // 统计卡 2x2
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("$totalSessions", stringResource(R.string.lr_parent_stat_sessions), modifier = Modifier.weight(1f))
                StatCard("$totalTurns", stringResource(R.string.lr_parent_stat_questions), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(topLang, stringResource(R.string.lr_parent_stat_lang), modifier = Modifier.weight(1f))
                StatCard("$totalMinutes", stringResource(R.string.lr_parent_stat_minutes), modifier = Modifier.weight(1f))
            }

            ParentSectionHeader(stringResource(R.string.lr_parent_recent_label))
            if (records.isEmpty()) {
                Text(stringResource(R.string.lr_parent_empty), color = palette.textMuted)
            } else {
                records.take(8).forEach { r -> RecentRow(r) }
            }

            // 隐私卡(肯定句,无云端/账号/订阅)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LumiColors.SuccessSoft)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.lr_parent_privacy_card), color = LumiColors.Ink700)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                LumiPrimaryButton(
                    onClick = onOpenSettings,
                    label = stringResource(R.string.lr_tab_settings),
                    modifier = Modifier.weight(1f),
                )
                LumiSecondaryButton(
                    onClick = onClear,
                    label = stringResource(R.string.lr_parent_clear),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecentRow(r: StudyRecord) {
    val palette = LocalLumiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val summary = r.contentSummary.ifBlank { stringResource(R.string.my_learning_no_image_session) }
            Text(summary.take(40), color = palette.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${if (r.lang == Lang.ZH.name) "中文" else "EN"} · ${r.turnCount} 轮",
                color = palette.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}
