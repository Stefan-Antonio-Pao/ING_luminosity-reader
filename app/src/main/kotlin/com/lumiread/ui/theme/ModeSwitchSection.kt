package com.lumiread.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lumiread.AppGraph
import com.lumiread.R
import kotlinx.coroutines.launch

/**
 * 双模式切换 UI(UI 改造任务书 §3.1 / §6.1)。
 *
 * 入口位置(步骤六前):设置页顶部"清晰入口"(任务书 §6.1 家长模式视角)。
 *
 * **2026-05-25 步骤八**:接入家长门(§6.3)。儿童 → 家长方向弹 [ParentGateDialog]
 * 做"点击较大数字"轻量验证;家长 → 儿童无需验证直通。
 */
@Composable
fun ModeSwitchSection() {
    val mode by AppGraph.settings.lumiModeFlow.collectAsState(initial = LumiMode.Child)
    val scope = rememberCoroutineScope()
    var gateOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LumiMode.entries.forEach { option ->
            val labelRes = when (option) {
                LumiMode.Child  -> R.string.mode_child
                LumiMode.Parent -> R.string.mode_parent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = mode == option,
                        role = Role.RadioButton,
                        onClick = {
                            if (mode == option) return@selectable
                            // 儿童 → 家长走家长门(§6.3);家长 → 儿童直通
                            if (mode == LumiMode.Child && option == LumiMode.Parent) {
                                gateOpen = true
                            } else {
                                scope.launch { AppGraph.settings.setLumiMode(option) }
                            }
                        },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == option, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (gateOpen) {
        ParentGateDialog(
            onConfirm = {
                gateOpen = false
                scope.launch { AppGraph.settings.setLumiMode(LumiMode.Parent) }
            },
            onDismiss = { gateOpen = false },
        )
    }
}
