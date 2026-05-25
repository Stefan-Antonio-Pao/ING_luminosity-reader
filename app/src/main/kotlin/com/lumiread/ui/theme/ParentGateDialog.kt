package com.lumiread.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumiread.R
import com.lumiread.ui.components.BouncyButton
import com.lumiread.ui.components.LumiOutlinedButton
import kotlin.math.abs
import kotlin.random.Random

/**
 * 家长门(part of the UI overhaul)。
 *
 * 出现时机:**儿童模式 → 家长模式**的切换动作触发(在 [ModeSwitchSection] 拦截 Radio 选择)。
 * 反向(家长 → 儿童)无需验证,直通。
 *
 * 验证方式:**"点击较大的数字"** —— 屏幕上并排显示两个 20~99 之间、差距 ≥ 10 的数字,
 * 用户必须点中较大的一个。设计规范明确"完全离线、不存密码",此设计:
 * - 题目随机,每次开门都不同(无法靠记忆位置作弊)
 * - 答错自动换题,防穷举
 * - 对成人显然 trivial、对幼儿(无数字概念)较难,符合"幼儿不易完成的小关卡"定位
 * - 完全本地,不涉及网络、密码、PIN 存储
 *
 * 视觉:对话框内的两个数字 tile 走 `tokens.primary`(行动金)+ 88dp 高度 + 48sp 大字 +
 * [BouncyButton] 回弹反馈 —— 与 LumiPrimaryButton 同语言,孩子点上去也有正反馈,不会觉得是"惩罚"。
 */
@Composable
fun ParentGateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalLumiTokens.current
    var challenge by remember { mutableStateOf(generateChallenge()) }
    var showError by remember { mutableStateOf(false) }

    fun onTilePick(picked: Int) {
        val correct = if (challenge.left > challenge.right) challenge.left else challenge.right
        if (picked == correct) {
            onConfirm()
        } else {
            showError = true
            challenge = generateChallenge()  // 重新出题防穷举
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(tokens.cornerLarge),
            color = tokens.surfaceBg,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.parent_gate_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.parent_gate_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.ink.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NumberTile(
                        value = challenge.left,
                        modifier = Modifier.weight(1f),
                        onClick = { onTilePick(challenge.left) },
                    )
                    NumberTile(
                        value = challenge.right,
                        modifier = Modifier.weight(1f),
                        onClick = { onTilePick(challenge.right) },
                    )
                }
                if (showError) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.parent_gate_wrong),
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LumiOutlinedButton(
                        onClick = onDismiss,
                        label = stringResource(R.string.parent_gate_cancel),
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberTile(
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalLumiTokens.current
    BouncyButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(tokens.cornerMedium))
            .background(tokens.primary)
            .defaultMinSize(minHeight = 88.dp)
            .padding(vertical = 16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                color = tokens.onPrimary,
                fontSize = 48.sp,
                fontFamily = tokens.fontFamily,
                fontWeight = tokens.fontWeight,
            )
        }
    }
}

private data class NumberChallenge(val left: Int, val right: Int)

/**
 * 生成一对 20~99 之间、差距 ≥ 10 的数字,左右随机摆放。
 * 范围避开 0~19 是因为太小的数字幼儿也可能识别;两位数差距 ≥ 10 保证视觉对比明显,
 * 对认识"位数 / 大小"的人(成人)瞬间可辨,对学龄前儿童难。
 */
private fun generateChallenge(): NumberChallenge {
    val a = Random.nextInt(20, 100)
    var b: Int
    do {
        b = Random.nextInt(20, 100)
    } while (abs(a - b) < 10)
    return if (Random.nextBoolean()) NumberChallenge(a, b) else NumberChallenge(b, a)
}
