package com.lumiread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiread.R
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.data.StudyRecord
import com.lumiread.ui.components.AppBackground
import com.lumiread.ui.components.PlayfulCard
import com.lumiread.ui.theme.LocalLumiTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * (下半)「我的学习」页 + UI 改造步骤 5d(2026-05-25)。
 *
 * 来源:[com.lumiread.data.StudyStore.all] 由 Room 实时推送,Composable 用
 * `collectAsState(emptyList)` 订阅 → 改动立即重绘。
 *
 * 双模式视觉(步骤 5d):
 * - 整屏:[AppBackground] 铺 `surfaceBg`
 * - 聚合卡:[PlayfulCard] 大圆角(儿童 28dp / 家长 12dp),内含"📚"标记
 * - 记录卡:token 驱动 Box(`surfaceBg` + 浅描边),儿童态自然更圆胖
 * - 顶栏返回 / 底部清空:TextButton 文字色对齐 `tokens.primary`
 *
 * 不做的事:不按周/月分组;不画时间序列图;星星贴纸 / 庆祝动画推迟到步骤 6
 * 吉祥物 / Lottie 一并(设计规范)。
 */
@Composable
fun MyLearningScreen(
    records: List<StudyRecord>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val tokens = LocalLumiTokens.current
    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = tokens.primary),
                ) { Text(stringResource(R.string.btn_back)) }
                Text(
                    stringResource(R.string.my_learning_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
            }
            HorizontalDivider()

            // 聚合卡
            SummaryCard(records)

            // 列表
            if (records.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.my_learning_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(records.size) { i ->
                        RecordCard(records[i])
                    }
                }

                // 清空记录(本期单次点击直接清, 加二次确认)
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = tokens.primary),
                    ) {
                        Text(stringResource(R.string.my_learning_clear), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(records: List<StudyRecord>) {
    val tokens = LocalLumiTokens.current
    val totalSessions = records.size
    val totalDurationMs = records.sumOf { (it.endedAt - it.startedAt).coerceAtLeast(0) }
    val zhCount = records.count { it.lang == Lang.ZH.name }
    val enCount = records.count { it.lang == Lang.EN.name }

    PlayfulCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            stringResource(R.string.my_learning_summary_header),
            fontWeight = FontWeight.SemiBold,
            color = tokens.ink,
            fontSize = tokens.titleSp,
        )
        Text(
            stringResource(R.string.my_learning_summary_totals, totalSessions, formatDuration(totalDurationMs)),
            color = tokens.ink,
            fontSize = tokens.bodySp,
        )
        Text(
            stringResource(R.string.my_learning_summary_langs, zhCount, enCount),
            color = tokens.ink,
            fontSize = tokens.bodySp,
        )
    }
}

@Composable
private fun RecordCard(rec: StudyRecord) {
    val tokens = LocalLumiTokens.current
    val durationMs = (rec.endedAt - rec.startedAt).coerceAtLeast(0)
    val started = DateFmt.format(Date(rec.startedAt))
    val langLabel = runCatching { Lang.valueOf(rec.lang).name }.getOrDefault(rec.lang)
    val ageLabel  = runCatching { AgeBand.valueOf(rec.ageBand).name }.getOrDefault(rec.ageBand)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cornerLarge))
            .background(tokens.surfaceBg)
            .border(1.dp, tokens.primary.copy(alpha = 0.20f), RoundedCornerShape(tokens.cornerLarge))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    started,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(durationMs),
                    color = tokens.ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.my_learning_record_meta, langLabel, ageLabel, rec.turnCount),
                color = tokens.ink,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                rec.contentSummary.takeIf { it.isNotBlank() }
                    ?.let { it.take(80) + if (it.length > 80) "…" else "" }
                    ?: stringResource(R.string.my_learning_no_image_session),
                color = tokens.ink,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val DateFmt: SimpleDateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}
