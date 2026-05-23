package com.lumiread.vision

import android.graphics.BitmapFactory
import android.media.ExifInterface   // 框架自带,无需额外依赖
import com.google.mlkit.vision.common.InputImage
import com.lumiread.core.ImageInput

/**
 * 把 core 抽象的 [ImageInput] 拍成 ML Kit 吃的 [InputImage]。
 *
 * 来源:ML Kit 官方文档 https://developers.google.com/ml-kit/vision/image-labeling/android,
 * 以及 https://developer.android.com/reference/com/google/mlkit/vision/common/InputImage。
 * InputImage.fromBitmap 要 0/90/180/270 度的 rotation;fromFilePath 自动读 EXIF。
 * 这里我们用 Bitmap 路径以便对相同 Bitmap 复用两个识别器。
 */
internal object InputImages {
    /** 注意:返回值需要调用方在用完后释放 Bitmap(让 GC 来,通常无需手动 recycle)。 */
    fun load(image: ImageInput): InputImage = when (image) {
        is ImageInput.Path -> {
            val bmp = BitmapFactory.decodeFile(image.absolutePath)
                ?: error("BitmapFactory.decodeFile returned null: ${image.absolutePath}")
            val rotation = readExifRotationDegrees(image.absolutePath)
            InputImage.fromBitmap(bmp, rotation)
        }
        is ImageInput.Bytes -> {
            val bmp = BitmapFactory.decodeByteArray(image.data, 0, image.data.size)
                ?: error("BitmapFactory.decodeByteArray returned null")
            InputImage.fromBitmap(bmp, 0)
        }
    }

    private fun readExifRotationDegrees(path: String): Int =
        when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
