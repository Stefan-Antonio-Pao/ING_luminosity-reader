package com.lumiread.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiread.R
import com.lumiread.core.AgeBand
import com.lumiread.ui.components.LumiIconButton
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalMode
import com.lumiread.ui.theme.LocalTier
import com.lumiread.ui.theme.Tier
import com.lumiread.ui.theme.displayFamily

/** 按当前 [Tier] 选三档文案之一的 res id。统一三段式年龄差异入口。 */
@StringRes
fun tierRes(tier: Tier, @StringRes toddler: Int, @StringRes preschool: Int, @StringRes pre: Int): Int =
    when (tier) {
        Tier.TODDLER -> toddler
        Tier.PRESCHOOL -> preschool
        Tier.PREADOLESCENT -> pre
    }

fun AgeBand.toTier(): Tier = when (this) {
    AgeBand.TODDLER -> Tier.TODDLER
    AgeBand.PRESCHOOL -> Tier.PRESCHOOL
    AgeBand.PREADOLESCENT -> Tier.PREADOLESCENT
}

/**
 * 通用顶栏:左侧返回 IconButton(可选)+ 居中标题。标题被 TalkBack 读出(accessibility §4)。
 */
@Composable
fun LumiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backCd: String = "返回",
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    val mode = LocalMode.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            LumiIconButton(R.drawable.ic_lumi_back, backCd, onBack)
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = palette.text,
            fontFamily = displayFamily(mode),
            fontWeight = FontWeight.Bold,
            fontSize = LocalTier.current.fsTitle,
        )
        if (trailing != null) trailing() else Spacer(Modifier.size(48.dp))
    }
}
