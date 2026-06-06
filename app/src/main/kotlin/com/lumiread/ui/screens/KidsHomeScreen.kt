package com.lumiread.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumiread.R
import com.lumiread.ui.components.LumiIcon
import com.lumiread.ui.components.LumiIconButton
import com.lumiread.ui.components.LumiPrimaryButton
import com.lumiread.ui.components.LumiSecondaryButton
import com.lumiread.ui.components.LumiScreenBackground
import com.lumiread.ui.components.PaperFrame
import com.lumiread.ui.components.PromptChip
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalMode
import com.lumiread.ui.theme.LocalTier
import com.lumiread.ui.theme.LumiColors
import com.lumiread.ui.theme.displayFamily

/**
 * 儿童首页(screen 02)。v3:奶油纸页底 + 星屑装饰 + 魔法书框主视觉 + 暖金主按钮。
 * 文案/字号/装饰密度随 [LocalTier]。每屏唯一主操作 = 拍这一页。
 */
@Composable
fun KidsHomeScreen(
    hasSession: Boolean,
    onTakePhoto: () -> Unit,
    onStory: () -> Unit,
    onContinue: () -> Unit,
    onParent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    val tier = LocalTier.current
    val mode = LocalMode.current
    LumiScreenBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 顶部:品牌药丸 + 家长入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(LumiColors.Gold100)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.lr_brand_pill),
                        color = LumiColors.Gold700,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(tierRes(tier, R.string.lr_home_age_toddler, R.string.lr_home_age_preschool, R.string.lr_home_age_pre)),
                    color = palette.textMuted,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(0.dp))

            // 主视觉:魔法书框包开书插画
            PaperFrame(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1.05f),
            ) {
                Image(
                    painter = painterResource(R.drawable.illu_open_book),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Text(
                stringResource(tierRes(tier, R.string.lr_home_title_toddler, R.string.lr_home_title_preschool, R.string.lr_home_title_pre)),
                color = palette.text,
                fontFamily = displayFamily(mode),
                fontWeight = FontWeight.Bold,
                fontSize = tier.fsDisplay,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(tierRes(tier, R.string.lr_home_sub_toddler, R.string.lr_home_sub_preschool, R.string.lr_home_sub_pre)),
                color = palette.textSoft,
                fontSize = tier.fsBody,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            LumiPrimaryButton(
                onClick = onTakePhoto,
                label = stringResource(tierRes(tier, R.string.lr_home_cta_toddler, R.string.lr_home_cta_preschool, R.string.lr_home_cta_pre)),
                modifier = Modifier.fillMaxWidth(),
            )

            // 续读(仅当有进行中的会话);toddler 仍可显示但保持简单
            if (hasSession) {
                PromptChip(
                    text = stringResource(R.string.lr_home_continue),
                    onClick = onContinue,
                    gold = true,
                )
            }

            // 次操作:没有绘本(故事) + 家长。Toddler 也允许故事入口(单一辅操作)。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LumiSecondaryButton(
                    onClick = onStory,
                    label = stringResource(R.string.lr_home_no_book),
                    modifier = Modifier.weight(1f),
                )
                LumiSecondaryButton(
                    onClick = onParent,
                    label = stringResource(R.string.lr_home_parent),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
