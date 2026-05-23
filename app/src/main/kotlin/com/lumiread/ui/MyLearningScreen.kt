package com.lumiread.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.data.StudyRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的学习」页。
 *
 * 来源:[com.lumiread.data.StudyStore.all] 由 Room 实时推送,Composable 用
 * `collectAsState(emptyList())` 订阅 → 改动立即重绘。
 *
 * 布局:
 *  - 顶栏:← 返回 / "📚 我的学习"
 *  - 聚合卡:总会话 / 总时长 / 中文数 / 英文数
 *  - LazyColumn:每条 Card 展示开始时刻、时长、Lang+Age+轮数、内容摘要(≤80 字)
 *  - 空态文案
 *  - 底部小字"清空记录"(单次点击直接清)
 *
 * 不做的事:不按周/月分组,不画时间序列图。
 */
@Composable
fun MyLearningScreen(
    records: List<StudyRecord>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                "📚 我的学习",
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
                    "还没有学习记录 —— 开一段伴读试试吧。",
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

            // 清空记录(单次点击直接清)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClear) {
                    Text("清空记录", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(records: List<StudyRecord>) {
    val totalSessions = records.size
    val totalDurationMs = records.sumOf { (it.endedAt - it.startedAt).coerceAtLeast(0) }
    val zhCount = records.count { it.lang == Lang.ZH.name }
    val enCount = records.count { it.lang == Lang.EN.name }

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("汇总", fontWeight = FontWeight.SemiBold)
            Text("总会话: $totalSessions  ·  总时长: ${formatDuration(totalDurationMs)}")
            Text("中文: $zhCount  ·  英文: $enCount")
        }
    }
}

@Composable
private fun RecordCard(rec: StudyRecord) {
    val durationMs = (rec.endedAt - rec.startedAt).coerceAtLeast(0)
    val started = DateFmt.format(Date(rec.startedAt))
    val langLabel = runCatching { Lang.valueOf(rec.lang).name }.getOrDefault(rec.lang)
    val ageLabel  = runCatching { AgeBand.valueOf(rec.ageBand).name }.getOrDefault(rec.ageBand)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    started,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "$langLabel · $ageLabel · ${rec.turnCount} 轮",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                rec.contentSummary.takeIf { it.isNotBlank() }
                    ?.let { it.take(80) + if (it.length > 80) "…" else "" }
                    ?: "(无图直聊)",
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
