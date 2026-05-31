package com.lumiread.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumiread.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * 拍照后的手动裁切确认页(CLAUDE.md §3.5)。
 *
 * 全屏显示拍到的原图(EXIF 旋转后),叠一个可拖矩形;九区命中:
 *  - 4 个角(左上/右上/左下/右下)
 *  - 4 条边(上/下/左/右)
 *  - 矩形内部 → 整体平移
 *
 * 状态用图片像素坐标存,避免屏幕换算误差。点"确认"才裁剪落盘,返回新路径;
 * 点"重拍"什么也不写,调用方负责把原图删掉。
 */
@Composable
fun CropConfirmScreen(
    srcPath: String,
    onConfirm: (croppedPath: String) -> Unit,
    onRetake: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val bitmap = remember(srcPath) { decodeAndOrientForDisplay(srcPath) }
    DisposableEffect(srcPath) {
        onDispose { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()
    val imgBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    // 初始裁切框:中央 90%×70%。单位 = 图片像素坐标。
    var crop by remember(srcPath) {
        mutableStateOf(initialCrop(bmpW, bmpH))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerW = with(density) { maxWidth.toPx() }
            val containerH = with(density) { maxHeight.toPx() }

            // fit-center:同比缩放,可能上下/左右有黑边
            val scale = min(containerW / bmpW, containerH / bmpH)
            val displayW = bmpW * scale
            val displayH = bmpH * scale
            val offsetX = (containerW - displayW) / 2f
            val offsetY = (containerH - displayH) / 2f

            // 触点 → 图片像素坐标
            fun viewToImage(viewX: Float, viewY: Float): Offset =
                Offset((viewX - offsetX) / scale, (viewY - offsetY) / scale)

            // 命中阈值(图片像素单位):角点 6%、边带 4% 的短边
            val shortEdge = min(bmpW, bmpH)
            val cornerGrabPx = shortEdge * 0.06f
            val edgeGrabPx = shortEdge * 0.04f
            val minCropPx = shortEdge * 0.10f

            Image(
                bitmap = imgBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            var dragMode by remember(srcPath) { mutableStateOf(DragMode.NONE) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(srcPath, bmpW, bmpH, scale, offsetX, offsetY) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val p = viewToImage(startOffset.x, startOffset.y)
                                dragMode = hitTest(p, crop, cornerGrabPx, edgeGrabPx)
                            },
                            onDragEnd = { dragMode = DragMode.NONE },
                            onDragCancel = { dragMode = DragMode.NONE },
                        ) { change, dragAmount ->
                            if (dragMode == DragMode.NONE) return@detectDragGestures
                            change.consume()
                            // 视图像素增量 → 图片像素增量
                            val dx = dragAmount.x / scale
                            val dy = dragAmount.y / scale
                            crop = applyDrag(crop, dragMode, dx, dy, bmpW, bmpH, minCropPx)
                        }
                    },
            ) {
                val cornerHandleLen = with(density) { 16.dp.toPx() }
                val cornerHandleW = with(density) { 3.dp.toPx() }
                val edgeHandleLen = with(density) { 24.dp.toPx() }
                val strokeW = with(density) { 2.dp.toPx() }

                // 裁切框在视图坐标系的位置
                val l = offsetX + crop.left * scale
                val t = offsetY + crop.top * scale
                val r = offsetX + crop.right * scale
                val b = offsetY + crop.bottom * scale

                // 1) 四条压暗带(裁切框外区域)
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(color = dim, topLeft = Offset.Zero, size = Size(size.width, t))
                drawRect(color = dim, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
                drawRect(color = dim, topLeft = Offset(0f, t), size = Size(l, b - t))
                drawRect(color = dim, topLeft = Offset(r, t), size = Size(size.width - r, b - t))

                // 2) 裁切框描边
                drawRect(
                    color = Color.White,
                    topLeft = Offset(l, t),
                    size = Size(r - l, b - t),
                    style = Stroke(width = strokeW),
                )

                // 3) 四角 L 形把手
                drawCornerHandle(l, t, +1f, +1f, cornerHandleLen, cornerHandleW)
                drawCornerHandle(r, t, -1f, +1f, cornerHandleLen, cornerHandleW)
                drawCornerHandle(l, b, +1f, -1f, cornerHandleLen, cornerHandleW)
                drawCornerHandle(r, b, -1f, -1f, cornerHandleLen, cornerHandleW)

                // 4) 四边中点短杠
                val midX = (l + r) / 2f
                val midY = (t + b) / 2f
                drawRect(
                    color = Color.White,
                    topLeft = Offset(midX - edgeHandleLen / 2f, t - cornerHandleW / 2f),
                    size = Size(edgeHandleLen, cornerHandleW),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(midX - edgeHandleLen / 2f, b - cornerHandleW / 2f),
                    size = Size(edgeHandleLen, cornerHandleW),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(l - cornerHandleW / 2f, midY - edgeHandleLen / 2f),
                    size = Size(cornerHandleW, edgeHandleLen),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(r - cornerHandleW / 2f, midY - edgeHandleLen / 2f),
                    size = Size(cornerHandleW, edgeHandleLen),
                )
            }
        }

        // 底部按钮条
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onRetake,
            ) { Text(stringResource(R.string.btn_retake)) }

            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val left = crop.left.toInt().coerceIn(0, bitmap.width)
                    val top = crop.top.toInt().coerceIn(0, bitmap.height)
                    val right = crop.right.toInt().coerceIn(left + 1, bitmap.width)
                    val bottom = crop.bottom.toInt().coerceIn(top + 1, bitmap.height)
                    val outPath = cropBitmapToCache(
                        src = bitmap,
                        left = left,
                        top = top,
                        width = right - left,
                        height = bottom - top,
                        context = context,
                    )
                    onConfirm(outPath)
                },
            ) { Text(stringResource(R.string.btn_confirm_crop)) }
        }
    }
}

private data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

private enum class DragMode {
    LEFT_TOP, TOP, RIGHT_TOP,
    LEFT, INSIDE, RIGHT,
    LEFT_BOTTOM, BOTTOM, RIGHT_BOTTOM,
    NONE,
}

private fun initialCrop(bmpW: Float, bmpH: Float): CropRect = CropRect(
    left = bmpW * 0.05f,
    top = bmpH * 0.15f,
    right = bmpW * 0.95f,
    bottom = bmpH * 0.85f,
)

/**
 * 命中测试:先看 4 角(优先级最高,避免被边/内部抢走),再看 4 边窄带,再看内部。
 * 输入 [p] 是触点的图片像素坐标。
 */
private fun hitTest(
    p: Offset,
    crop: CropRect,
    cornerGrab: Float,
    edgeGrab: Float,
): DragMode {
    val nearLeft = kotlin.math.abs(p.x - crop.left) <= cornerGrab
    val nearRight = kotlin.math.abs(p.x - crop.right) <= cornerGrab
    val nearTop = kotlin.math.abs(p.y - crop.top) <= cornerGrab
    val nearBottom = kotlin.math.abs(p.y - crop.bottom) <= cornerGrab

    if (nearLeft && nearTop) return DragMode.LEFT_TOP
    if (nearRight && nearTop) return DragMode.RIGHT_TOP
    if (nearLeft && nearBottom) return DragMode.LEFT_BOTTOM
    if (nearRight && nearBottom) return DragMode.RIGHT_BOTTOM

    val insideY = p.y in (crop.top - edgeGrab)..(crop.bottom + edgeGrab)
    val insideX = p.x in (crop.left - edgeGrab)..(crop.right + edgeGrab)
    if (insideY && kotlin.math.abs(p.x - crop.left) <= edgeGrab) return DragMode.LEFT
    if (insideY && kotlin.math.abs(p.x - crop.right) <= edgeGrab) return DragMode.RIGHT
    if (insideX && kotlin.math.abs(p.y - crop.top) <= edgeGrab) return DragMode.TOP
    if (insideX && kotlin.math.abs(p.y - crop.bottom) <= edgeGrab) return DragMode.BOTTOM

    if (p.x in crop.left..crop.right && p.y in crop.top..crop.bottom) return DragMode.INSIDE
    return DragMode.NONE
}

/**
 * 按拖动模式更新裁切框。clamp 到 [0, bmpW] × [0, bmpH],并保持最小尺寸 [minSize]。
 */
private fun applyDrag(
    crop: CropRect,
    mode: DragMode,
    dx: Float,
    dy: Float,
    bmpW: Float,
    bmpH: Float,
    minSize: Float,
): CropRect {
    var l = crop.left
    var t = crop.top
    var r = crop.right
    var b = crop.bottom

    when (mode) {
        DragMode.LEFT_TOP -> { l += dx; t += dy }
        DragMode.TOP -> { t += dy }
        DragMode.RIGHT_TOP -> { r += dx; t += dy }
        DragMode.LEFT -> { l += dx }
        DragMode.RIGHT -> { r += dx }
        DragMode.LEFT_BOTTOM -> { l += dx; b += dy }
        DragMode.BOTTOM -> { b += dy }
        DragMode.RIGHT_BOTTOM -> { r += dx; b += dy }
        DragMode.INSIDE -> {
            val w = r - l
            val h = b - t
            l = (l + dx).coerceIn(0f, bmpW - w)
            t = (t + dy).coerceIn(0f, bmpH - h)
            r = l + w
            b = t + h
            return CropRect(l, t, r, b)
        }
        DragMode.NONE -> return crop
    }

    // 边/角的 clamp:不出图,且保持最小尺寸
    l = l.coerceIn(0f, r - minSize)
    t = t.coerceIn(0f, b - minSize)
    r = r.coerceIn(l + minSize, bmpW)
    b = b.coerceIn(t + minSize, bmpH)
    return CropRect(l, t, r, b)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerHandle(
    x: Float,
    y: Float,
    dirX: Float,
    dirY: Float,
    len: Float,
    width: Float,
) {
    // 水平短杠:从角点沿 dirX 延伸 len
    val hx = if (dirX > 0) x else x - len
    val hy = y - width / 2f
    drawRect(
        color = Color.White,
        topLeft = Offset(hx, hy),
        size = Size(len, width),
    )
    // 垂直短杠
    val vx = x - width / 2f
    val vy = if (dirY > 0) y else y - len
    drawRect(
        color = Color.White,
        topLeft = Offset(vx, vy),
        size = Size(width, len),
    )
}

private fun cropBitmapToCache(
    src: Bitmap,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    context: Context,
): String {
    val cropped = Bitmap.createBitmap(src, left, top, width, height)
    val out = File(context.cacheDir, "crop-${System.currentTimeMillis()}.jpg")
    FileOutputStream(out).use { fos ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 92, fos)
    }
    // createBitmap 在不变换时会返回原 src 视图,这里子矩形必然产生新 bitmap,可放心 recycle。
    if (cropped !== src) cropped.recycle()
    return out.absolutePath
}

private fun decodeAndOrientForDisplay(path: String): Bitmap {
    val raw = BitmapFactory.decodeFile(path)
        ?: error("BitmapFactory.decodeFile returned null: $path")
    val rot = readExifRotationDegrees(path)
    if (rot == 0) return raw
    val matrix = Matrix().apply { postRotate(rot.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (rotated !== raw) raw.recycle()
    return rotated
}

private fun readExifRotationDegrees(path: String): Int =
    when (ExifInterface(path).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

