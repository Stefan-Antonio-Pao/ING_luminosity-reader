package com.lumiread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiread.R
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import com.lumiread.data.StoryRepository
import com.lumiread.ui.components.LumiIcon
import com.lumiread.ui.components.LumiPrimaryButton
import com.lumiread.ui.components.LumiScreenBackground
import com.lumiread.ui.components.OfflinePill
import com.lumiread.ui.components.PromptChip
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalMode
import com.lumiread.ui.theme.LocalTier
import com.lumiread.ui.theme.LumiColors
import com.lumiread.ui.theme.Tier
import com.lumiread.ui.theme.displayFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 无书故事模式(screen 08)。三入口:1) 拍一样东西 2) 选本地模型生成的开头 chips 3) 自由输入。
 * chips 由 [StoryRepository] 用本地模型生成(loading / ready / fallback 三态);chip 文案不硬编码在此。
 */
@Composable
fun StoryScreen(
    lang: Lang,
    age: AgeBand,
    onSnapObject: () -> Unit,
    onStartStory: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    val tier = LocalTier.current
    val mode = LocalMode.current

    var refreshKey by remember { mutableIntStateOf(0) }
    // 三态:null = loading;非空 = ready/fallback
    var chips by remember(lang, age) { mutableStateOf(StoryRepository.cached(lang, age)) }
    var usedFallback by remember(lang, age) { mutableStateOf(false) }
    val fallback = listOf(
        stringResource(R.string.lr_story_fallback_1),
        stringResource(R.string.lr_story_fallback_2),
        stringResource(R.string.lr_story_fallback_3),
        stringResource(R.string.lr_story_fallback_4),
        stringResource(R.string.lr_story_fallback_5),
    )

    LaunchedEffect(lang, age, refreshKey) {
        if (chips != null && refreshKey == 0) return@LaunchedEffect
        chips = null
        usedFallback = false
        val generated = withContext(Dispatchers.IO) { StoryRepository.generate(lang, age, tier.storyChipCount) }
        if (generated.isNotEmpty()) {
            chips = generated
        } else {
            chips = fallback.take(tier.storyChipCount)
            usedFallback = true
        }
    }

    var input by remember { mutableStateOf("") }

    LumiScreenBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            LumiTopBar(
                title = stringResource(tierRes(tier, R.string.lr_story_title_toddler, R.string.lr_story_title_preschool, R.string.lr_story_title_pre)),
                onBack = onBack,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(if (tier == Tier.TODDLER) R.string.lr_story_sub_toddler else R.string.lr_story_sub_others),
                    color = palette.textSoft,
                    fontSize = tier.fsBody,
                )

                // 入口 1:拍一样东西
                StoryEntryCard(
                    iconRes = R.drawable.ic_lumi_camera,
                    title = stringResource(R.string.lr_story_object_title),
                    subtitle = stringResource(tierRes(tier, R.string.lr_story_object_sub_toddler, R.string.lr_story_object_sub_preschool, R.string.lr_story_object_sub_pre)),
                    onClick = onSnapObject,
                )

                // 入口 2:本地模型生成的开头 chips
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (chips == null) R.string.lr_story_chips_loading else R.string.lr_story_chips_label),
                        modifier = Modifier.weight(1f),
                        color = palette.textSoft,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (chips != null) {
                        PromptChip(
                            text = stringResource(R.string.lr_story_chips_refresh),
                            onClick = {
                                StoryRepository.invalidate(lang, age)
                                refreshKey++
                            },
                        )
                    }
                }
                val current = chips
                if (current == null) {
                    // loading skeleton(避免布局跳动)
                    OfflinePill(stringResource(R.string.lr_story_chips_offline))
                    repeat(tier.storyChipCount) { ChipSkeleton() }
                } else {
                    current.forEach { opener ->
                        PromptChip(
                            text = opener,
                            onClick = { onStartStory(opener) },
                            gold = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 入口 3:自由输入(toddler 也允许,但占位更简单)
                Text(
                    stringResource(R.string.lr_story_input_label),
                    color = palette.textSoft,
                    fontWeight = FontWeight.SemiBold,
                )
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(tierRes(tier, R.string.lr_story_input_toddler, R.string.lr_story_input_preschool, R.string.lr_story_input_pre)))
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.bgElevated,
                        unfocusedContainerColor = palette.bgElevated,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                LumiPrimaryButton(
                    onClick = { val t = input.trim(); if (t.isNotEmpty()) onStartStory(t) },
                    label = stringResource(R.string.lr_story_start),
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.lr_story_footer),
                    color = palette.textMuted,
                    fontSize = tier.fsCaption,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun StoryEntryCard(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    val tier = LocalTier.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tier.radius))
            .background(palette.bgElevated)
            .border(1.dp, palette.border, RoundedCornerShape(tier.radius))
            .clickable(onClick = onClick)
            .padding(tier.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LumiIcon(iconRes, null, tint = LumiColors.Gold700, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = tier.fsBody)
            Text(subtitle, color = palette.textMuted, fontSize = tier.fsCaption)
        }
        LumiIcon(R.drawable.ic_lumi_arrow, null, tint = palette.textMuted, size = 22.dp)
    }
}

@Composable
private fun ChipSkeleton() {
    val palette = LocalLumiPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surfaceAlt),
    )
}
