package com.lumiread.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumiread.R
import com.lumiread.ui.theme.LocalLumiPalette
import com.lumiread.ui.theme.LocalMode
import com.lumiread.ui.theme.LocalTier
import com.lumiread.ui.theme.LumiColors
import com.lumiread.ui.theme.LumiMode

/**
 * 单色图标:把 `res/drawable/ic_lumi_*` vector 用 [tint] 重新着色绘制。
 * 装饰性图标传 `contentDescription = null` 让 TalkBack 跳过(accessibility §4)。
 */
@Composable
fun LumiIcon(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalLumiPalette.current.text,
    size: Dp = 24.dp,
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .then(if (contentDescription == null) Modifier.semantics { } else Modifier),
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * 屏幕级背景。Kids = 奶油纸页渐变 (`bg_kids_paper`) + [PaperDecor] 星屑光点;
 * Parent = 冷静中性底 (`bg_parent_neutral`),无装饰。装饰密度随 [LocalTier]。
 */
@Composable
fun LumiScreenBackground(
    modifier: Modifier = Modifier,
    decor: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val mode = LocalMode.current
    val palette = LocalLumiPalette.current
    Box(modifier = modifier.fillMaxSize().background(palette.bg)) {
        Image(
            painter = painterResource(
                if (mode == LumiMode.Child) R.drawable.bg_kids_paper else R.drawable.bg_parent_neutral
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (decor && mode == LumiMode.Child) PaperDecor()
        content()
    }
}

/**
 * 固定位置的纸张装饰(星屑 + 光点)。**不用随机位置**(避免重组跳动,components §7)。
 * 整体不透明度 = [LocalTier].decorDensity(toddler 1.0 → preadolescent 0.35)。
 */
@Composable
fun PaperDecor(modifier: Modifier = Modifier) {
    val density = LocalTier.current.decorDensity
    if (density <= 0.01f) return
    Box(modifier = modifier.fillMaxSize().alpha(density)) {
        DecorImage(R.drawable.decor_dot, 14.dp, 28.dp, 110.dp)
        DecorImage(R.drawable.decor_dot, 10.dp, 320.dp, 70.dp)
        DecorImage(R.drawable.decor_dot, 12.dp, 300.dp, 480.dp)
        DecorImage(R.drawable.decor_dot, 9.dp, 40.dp, 560.dp)
        DecorImage(R.drawable.decor_star_burst, 22.dp, 52.dp, 210.dp)
        DecorImage(R.drawable.decor_star_burst, 16.dp, 330.dp, 240.dp)
        DecorImage(R.drawable.decor_star_burst, 18.dp, 300.dp, 660.dp)
    }
}

@Composable
private fun BoxScope.DecorImage(resId: Int, sz: Dp, x: Dp, y: Dp) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.offset(x = x, y = y).size(sz),
    )
}

/**
 * "魔法书框"容器:纸色底 + 内描边 + 四角金色 L 形(components#paper-frame)。
 * 用于首页主视觉、preview 包图、dialog 顶部缩略图。
 */
@Composable
fun PaperFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LumiColors.Paper700),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LumiColors.Paper1000),
        ) { content() }
        // 四角金色 L
        CornerL(Alignment.TopStart, 0f)
        CornerL(Alignment.TopEnd, 90f)
        CornerL(Alignment.BottomEnd, 180f)
        CornerL(Alignment.BottomStart, 270f)
    }
}

@Composable
private fun BoxScope.CornerL(align: Alignment, rotation: Float) {
    Image(
        painter = painterResource(R.drawable.decor_corner_l),
        contentDescription = null,
        modifier = Modifier
            .align(align)
            .padding(6.dp)
            .size(28.dp)
            .then(Modifier),
    )
    // 注:decor_corner_l 本身是左上朝向;此处不旋转(四角用同一图近似),保持简单稳定。
}

/** 半透明深色胶囊提示(camera 顶部 / 下载提示),前缀 ✦(components#capture-hint)。 */
@Composable
fun CaptureHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = "✦ $text", color = Color.White)
    }
}

/** "离线 / 本地运行"肯定式药丸(accessibility §10:不报红,肯定句)。 */
@Composable
fun OfflinePill(text: String, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(LumiColors.SuccessSoft)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = LumiColors.Success)
    }
}
