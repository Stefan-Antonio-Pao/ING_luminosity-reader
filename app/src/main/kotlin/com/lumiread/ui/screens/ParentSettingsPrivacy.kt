package com.lumiread.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.lumiread.R
import com.lumiread.core.AgeBand
import com.lumiread.core.GemmaModel
import com.lumiread.core.Lang
import com.lumiread.core.OutputMode
import com.lumiread.llm.ModelProvider
import com.lumiread.ui.components.LumiIcon
import com.lumiread.ui.components.LumiScreenBackground
import com.lumiread.ui.components.LumiSecondaryButton
import com.lumiread.ui.components.LumiSwitch
import com.lumiread.ui.components.OfflinePill
import com.lumiread.ui.components.ParentSectionHeader
import com.lumiread.ui.components.PromptChip
import com.lumiread.ui.components.SegmentedSelector
import com.lumiread.ui.components.SettingsRow
import com.lumiread.ui.displayNameRes
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LumiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 家长设置(screen 11)。v3 冷静风。接真实 DataStore 设置:年龄段/输出语言/双语/自动朗读/模型。
 * UI 语言走 AppCompat locale(切换触发 recreate)。无账号/上传/云端/订阅。
 */
@Composable
fun ParentSettingsScreen(
    lang: Lang,
    ageBand: AgeBand,
    outputMode: OutputMode,
    autoPlayTts: Boolean,
    selectedModel: GemmaModel,
    onTier: (AgeBand) -> Unit,
    onToggleLang: () -> Unit,
    onBilingual: (Boolean) -> Unit,
    onAutoTts: (Boolean) -> Unit,
    onSelectModel: (GemmaModel) -> Unit,
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installed by remember { mutableStateOf(ModelProvider.installedModels(context)) }
    fun refresh() { installed = ModelProvider.installedModels(context) }
    var importTarget by remember { mutableStateOf<GemmaModel?>(null) }
    var importProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = importTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) { importTarget = null; return@rememberLauncherForActivityResult }
        scope.launch {
            importProgress = 0L to -1L
            ModelProvider.importFromUri(context, target, uri) { c, t -> importProgress = c to t }
                .onSuccess { refresh() }
            importProgress = null; importTarget = null
        }
    }

    LumiScreenBackground(modifier = modifier, decor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            LumiTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // ── 孩子 ──
                ParentSectionHeader(stringResource(R.string.lr_set_section_child))
                SectionCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.lr_set_tier_label), color = palette.text, fontWeight = FontWeight.SemiBold)
                        SegmentedSelector(
                            options = listOf(
                                AgeBand.TODDLER to stringResource(R.string.lr_set_tier_toddler),
                                AgeBand.PRESCHOOL to stringResource(R.string.lr_set_tier_preschool),
                                AgeBand.PREADOLESCENT to stringResource(R.string.lr_set_tier_pre),
                            ),
                            selected = ageBand,
                            onSelect = onTier,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider(color = palette.border)
                    SettingsRow(
                        title = stringResource(R.string.lr_set_out_lang),
                        subtitle = if (lang == Lang.ZH) "中文" else "English",
                        onClick = onToggleLang,
                        trailing = { PromptChip(text = if (lang == Lang.ZH) "ZH" else "EN", onClick = onToggleLang) },
                    )
                    HorizontalDivider(color = palette.border)
                    SettingsRow(
                        title = stringResource(R.string.lr_set_bilingual),
                        subtitle = stringResource(R.string.lr_set_bilingual_desc),
                        trailing = {
                            LumiSwitch(
                                checked = outputMode == OutputMode.BILINGUAL,
                                onCheckedChange = { onBilingual(it) },
                            )
                        },
                    )
                }

                // ── 阅读体验 ──
                ParentSectionHeader(stringResource(R.string.lr_set_section_exp))
                SectionCard {
                    SettingsRow(
                        title = stringResource(R.string.lr_set_auto_tts),
                        subtitle = stringResource(R.string.lr_set_auto_tts_desc),
                        trailing = { LumiSwitch(checked = autoPlayTts, onCheckedChange = onAutoTts) },
                    )
                }

                // ── 模型与运行 ──
                ParentSectionHeader(stringResource(R.string.lr_set_model_section))
                SectionCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GemmaModel.entries.forEach { model ->
                            val isInstalled = model in installed
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(model.displayNameRes()), color = palette.text, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(if (isInstalled) R.string.model_status_installed else R.string.model_status_missing),
                                        color = if (isInstalled) LumiColors.Success else LumiColors.Error,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (isInstalled && model != selectedModel) {
                                    PromptChip(text = stringResource(R.string.btn_confirm), onClick = { onSelectModel(model) })
                                } else if (model == selectedModel) {
                                    PromptChip(text = "✓", onClick = {}, gold = true)
                                }
                            }
                            if (importTarget == model && importProgress != null) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PromptChip(text = stringResource(R.string.btn_open_hf), onClick = {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(model.hfModelPageUrl))
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    })
                                    PromptChip(text = stringResource(R.string.btn_import_file), onClick = {
                                        importTarget = model
                                        importLauncher.launch(arrayOf("*/*"))
                                    })
                                }
                            }
                            HorizontalDivider(color = palette.border)
                        }
                    }
                }

                // ── 应用 ──
                ParentSectionHeader(stringResource(R.string.lr_set_section_app))
                SectionCard {
                    UiLanguageRow()
                    HorizontalDivider(color = palette.border)
                    SettingsRow(
                        title = stringResource(R.string.lr_set_privacy),
                        subtitle = stringResource(R.string.lr_set_privacy_desc),
                        onClick = onOpenPrivacy,
                        trailing = { LumiIcon(R.drawable.ic_lumi_arrow, null, tint = palette.textMuted, size = 20.dp) },
                    )
                    HorizontalDivider(color = palette.border)
                    SettingsRow(
                        title = stringResource(R.string.lr_set_version),
                        subtitle = stringResource(R.string.lr_set_version_value),
                    )
                }

                OfflinePill(stringResource(R.string.lr_offline_pill), modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    val palette = LocalLumiPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp)),
    ) { content() }
}

/** UI 界面语言三态(跟随系统 / 中文 / English),走 AppCompat locale,切换触发 recreate。 */
@Composable
private fun UiLanguageRow() {
    val palette = LocalLumiPalette.current
    val systemLabel = stringResource(R.string.settings_ui_lang_follow_system)
    fun current(): String =
        when (AppCompatDelegate.getApplicationLocales().get(0)?.language) {
            "zh" -> "中文"; "en" -> "English"; else -> systemLabel
        }
    var label by remember { mutableStateOf(current()) }
    SettingsRow(
        title = stringResource(R.string.lr_set_ui_lang),
        subtitle = label,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PromptChip(text = "系统", onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()); label = current()
                })
                PromptChip(text = "中", onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")); label = current()
                })
                PromptChip(text = "EN", onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-US")); label = current()
                })
            }
        },
    )
}

/**
 * 隐私说明(screen 12)。强调本地运行、数据在本设备。无账号/上传/同步/订阅。
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    LumiScreenBackground(modifier = modifier, decor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            LumiTopBar(title = stringResource(R.string.lr_privacy_title), onBack = onBack)
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LumiColors.SuccessSoft),
                    contentAlignment = Alignment.Center,
                ) { LumiIcon(R.drawable.ic_lumi_shield, null, tint = LumiColors.Success, size = 36.dp) }
                Text(stringResource(R.string.lr_privacy_hero), color = palette.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(stringResource(R.string.lr_privacy_hero_body), color = palette.textSoft)
                PrivacyRow(R.drawable.ic_lumi_close, R.string.lr_privacy_r1_t, R.string.lr_privacy_r1_b)
                PrivacyRow(R.drawable.ic_lumi_lock, R.string.lr_privacy_r2_t, R.string.lr_privacy_r2_b)
                PrivacyRow(R.drawable.ic_lumi_wifi_off, R.string.lr_privacy_r3_t, R.string.lr_privacy_r3_b)
                PrivacyRow(R.drawable.ic_lumi_check, R.string.lr_privacy_r4_t, R.string.lr_privacy_r4_b)
                OfflinePill(stringResource(R.string.lr_offline_pill))
                LumiSecondaryButton(onClick = onBack, label = stringResource(R.string.lr_err_home), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun PrivacyRow(iconRes: Int, titleRes: Int, bodyRes: Int) {
    val palette = LocalLumiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LumiIcon(iconRes, null, tint = LumiColors.Gold700, size = 28.dp)
        Column {
            Text(stringResource(titleRes), color = palette.text, fontWeight = FontWeight.Bold)
            Text(stringResource(bodyRes), color = palette.textMuted, fontSize = 13.sp)
        }
    }
}
