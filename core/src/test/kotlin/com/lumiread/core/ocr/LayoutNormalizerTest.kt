package com.lumiread.core.ocr

import com.lumiread.core.Lang
import com.lumiread.core.OcrBox
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult
import com.lumiread.core.PageRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutNormalizerTest {

    private fun line(
        text: String,
        left: Int, top: Int, right: Int, bottom: Int,
        conf: Float = 0.95f,
        angle: Float? = 0f,
    ) = OcrLine(text, conf, box = OcrBox(left, top, right, bottom), angle = angle)

    @Test fun single_page_sorted_by_y_then_x() {
        // 乱序输入:第二行在前。
        val ocr = OcrResult(
            lines = listOf(
                line("second line", 100, 300, 500, 350),
                line("first line", 100, 100, 500, 150),
                line("third line", 100, 500, 500, 550),
            ),
            detectedLang = Lang.EN, imageWidth = 800, imageHeight = 1000,
        )
        val out = LayoutNormalizer.normalize(ocr)
        assertEquals(listOf("first line", "second line", "third line"), out.orderedLines.map { it.text })
        assertEquals(LayoutNormalizer.LayoutType.SINGLE_PAGE, out.layoutType)
    }

    @Test fun two_page_spread_reads_left_page_first() {
        // 左页 x∈[50,350],右页 x∈[850,1150],书脊空隙 500px ≥ 1200*0.12。
        val ocr = OcrResult(
            lines = listOf(
                line("right A", 850, 100, 1150, 150),
                line("left A", 50, 100, 350, 150),
                line("right B", 850, 300, 1150, 350),
                line("left B", 50, 300, 350, 350),
            ),
            detectedLang = Lang.EN, imageWidth = 1200, imageHeight = 800,
        )
        val out = LayoutNormalizer.normalize(ocr)
        assertEquals(LayoutNormalizer.LayoutType.TWO_PAGE_SPREAD, out.layoutType)
        assertEquals(listOf("left A", "left B", "right A", "right B"), out.orderedLines.map { it.text })
        assertTrue(out.orderedLines.take(2).all { it.pageRegion == PageRegion.LEFT_PAGE })
        assertTrue(out.orderedLines.drop(2).all { it.pageRegion == PageRegion.RIGHT_PAGE })
    }

    @Test fun page_number_at_bottom_excluded_from_text() {
        val ocr = OcrResult(
            lines = listOf(
                line("The bear sleeps.", 100, 400, 700, 460),
                line("12", 380, 940, 420, 980),  // 底部 12% 区域的纯数字
            ),
            detectedLang = Lang.EN, imageWidth = 800, imageHeight = 1000,
        )
        val out = LayoutNormalizer.normalize(ocr)
        assertEquals(listOf("The bear sleeps."), out.orderedLines.map { it.text })
        assertFalse(out.plainText.contains("12"))
        assertTrue(out.warnings.any { it.startsWith("page_numbers_excluded") })
    }

    @Test fun mid_page_number_like_text_is_kept() {
        // 正文中间的数字(如 "3 little pigs" 的页中数字)不是页码,不能丢。
        val ocr = OcrResult(
            lines = listOf(
                line("He found", 100, 400, 400, 450),
                line("3", 420, 400, 450, 450),
                line("apples", 470, 400, 700, 450),
            ),
            detectedLang = Lang.EN, imageWidth = 800, imageHeight = 1000,
        )
        val out = LayoutNormalizer.normalize(ocr)
        assertTrue(out.plainText.contains("3"))
    }

    @Test fun skewed_decorative_line_dropped() {
        val ocr = OcrResult(
            lines = listOf(
                line("Real text here", 100, 100, 600, 160),
                line("WHOOSH", 200, 300, 500, 420, angle = 45f), // 倾斜装饰字
            ),
            detectedLang = Lang.EN, imageWidth = 800, imageHeight = 1000,
        )
        val out = LayoutNormalizer.normalize(ocr)
        assertEquals(listOf("Real text here"), out.orderedLines.map { it.text })
    }

    @Test fun no_geometry_passthrough_keeps_original_order() {
        // 旧调用点 / Fake:box 全 null → 原序透传,不丢内容。
        val ocr = OcrResult(
            lines = listOf(OcrLine("第一行", 0.9f), OcrLine("第二行", 0.9f)),
            detectedLang = Lang.ZH,
        )
        val out = LayoutNormalizer.normalize(ocr)
        assertEquals(listOf("第一行", "第二行"), out.orderedLines.map { it.text })
        assertEquals(LayoutNormalizer.LayoutType.UNKNOWN, out.layoutType)
    }

    @Test fun low_confidence_line_dropped_only_when_confidence_available() {
        val lines = listOf(
            line("good line", 100, 100, 500, 150, conf = 0.95f),
            line("garbage~~", 100, 300, 500, 350, conf = 0.1f),
        )
        val withConf = LayoutNormalizer.normalize(
            OcrResult(lines, Lang.EN, 800, 1000, confidenceAvailable = true)
        )
        assertEquals(listOf("good line"), withConf.orderedLines.map { it.text })

        // 置信度不可用(全 0 场景):不能按 0 砍行。
        val noConf = LayoutNormalizer.normalize(
            OcrResult(lines.map { it.copy(confidence = 0f) }, Lang.EN, 800, 1000, confidenceAvailable = false)
        )
        assertEquals(2, noConf.orderedLines.size)
    }
}
