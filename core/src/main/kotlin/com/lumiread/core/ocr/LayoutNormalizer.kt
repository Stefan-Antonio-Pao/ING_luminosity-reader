package com.lumiread.core.ocr

import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult
import com.lumiread.core.PageRegion

/**
 * 绘本版面排序(OCR_TECH_DOC §7,轨道 A)。
 *
 * 儿童绘本 OCR 的最大问题之一不是错字而是**读错顺序**:双页展开先右后左、页码混进正文、
 * 装饰字干扰。本类把 ML Kit 的原始行序重排为"自然阅读顺序",并过滤明显非正文内容。
 *
 * 规则(全部确定性,纯 JVM 可单测):
 *  1. 有几何信息时:按 x 中心分布判断是否双页展开(两簇且簇间有明显空隙)。
 *  2. 双页先左页后右页;页内先按 y 排,y 接近(同一文本行)再按 x 排。
 *  3. 过滤:过斜(|angle| > [MAX_ANGLE_DEG])、置信度极低(< [MIN_LINE_CONFIDENCE],仅当置信度可用)、
 *     纯装饰性超短行(单字符且非 CJK)。
 *  4. 页码检测(短纯数字、位于页面底部 12% 或顶部 6% 区域)→ 标记 PAGE_NUMBER,不进正文。
 *  5. 顶部大字标记 TOP_TITLE(保留进正文,但带标记)。
 *  6. 无几何信息(旧调用点 / Fake / box 全 null)→ 原序透传,layoutType=UNKNOWN,不丢内容。
 */
object LayoutNormalizer {

    enum class LayoutType { SINGLE_PAGE, TWO_PAGE_SPREAD, TEXT_LIGHT_IMAGE_HEAVY, UNKNOWN }

    data class NormalizedPageText(
        val orderedLines: List<OcrLine>,
        val plainText: String,
        val layoutType: LayoutType,
        val warnings: List<String>,
    )

    fun normalize(result: OcrResult): NormalizedPageText {
        val warnings = mutableListOf<String>()
        val withGeometry = result.lines.filter { it.box != null }

        // 无几何信息 → 原序透传(不丢内容),只做页码样式过滤。
        if (withGeometry.size < result.lines.size / 2.0 || withGeometry.isEmpty()) {
            if (result.lines.isNotEmpty()) warnings += "no_geometry_passthrough"
            val kept = result.lines.filterNot { isPageNumberText(it.text) }
            return NormalizedPageText(
                orderedLines = kept,
                plainText = kept.joinToString("\n") { it.text },
                layoutType = LayoutType.UNKNOWN,
                warnings = warnings,
            )
        }

        val pageH = effectiveHeight(result, withGeometry)
        val pageW = effectiveWidth(result, withGeometry)

        // 3. 过滤非正文。
        val filtered = withGeometry.filter { line ->
            val tooSkewed = line.angle != null && kotlin.math.abs(line.angle) > MAX_ANGLE_DEG
            val tooLowConf = result.confidenceAvailable && line.confidence < MIN_LINE_CONFIDENCE
            // 装饰性超短行 = 单个非字母数字字符(如 "~" "*");单个数字/字母/汉字是正文,不能丢。
            val decorative = line.text.length == 1 && !line.text.single().isLetterOrDigit()
            if (tooSkewed) warnings += "skewed_line_dropped:${line.text.take(12)}"
            if (tooLowConf) warnings += "low_conf_line_dropped:${line.text.take(12)}"
            !tooSkewed && !tooLowConf && !decorative
        }
        if (filtered.size < withGeometry.size) warnings += "filtered=${withGeometry.size - filtered.size}"
        if (filtered.isEmpty()) {
            return NormalizedPageText(emptyList(), "", LayoutType.TEXT_LIGHT_IMAGE_HEAVY, warnings)
        }

        // 4./5. 区域标记。
        val tagged = filtered.map { line ->
            val box = line.box!!
            val region = when {
                isPageNumberText(line.text) &&
                    (box.centerY > pageH * 0.88f || box.centerY < pageH * 0.06f) -> PageRegion.PAGE_NUMBER
                box.centerY < pageH * 0.15f && box.height > pageH * 0.05f -> PageRegion.TOP_TITLE
                else -> PageRegion.BODY_TEXT
            }
            line.copy(pageRegion = region)
        }
        val pageNumbers = tagged.filter { it.pageRegion == PageRegion.PAGE_NUMBER }
        if (pageNumbers.isNotEmpty()) warnings += "page_numbers_excluded=${pageNumbers.map { it.text }}"
        val content = tagged.filter { it.pageRegion != PageRegion.PAGE_NUMBER }
        if (content.isEmpty()) {
            return NormalizedPageText(emptyList(), "", LayoutType.TEXT_LIGHT_IMAGE_HEAVY, warnings)
        }

        // 1. 双页判定:x 中心两簇且簇间空隙 ≥ 页宽 12%。
        val split = detectSpreadSplit(content, pageW)
        val layoutType: LayoutType
        val ordered: List<OcrLine>
        if (split != null) {
            layoutType = LayoutType.TWO_PAGE_SPREAD
            val (left, right) = content.partition { it.box!!.centerX < split }
            ordered = sortReadingOrder(left.map { it.copy(pageRegion = pageRegionFor(it, PageRegion.LEFT_PAGE)) }) +
                sortReadingOrder(right.map { it.copy(pageRegion = pageRegionFor(it, PageRegion.RIGHT_PAGE)) })
        } else {
            layoutType = if (content.size <= 2) LayoutType.TEXT_LIGHT_IMAGE_HEAVY else LayoutType.SINGLE_PAGE
            ordered = sortReadingOrder(content)
        }

        return NormalizedPageText(
            orderedLines = ordered,
            plainText = ordered.joinToString("\n") { it.text },
            layoutType = layoutType,
            warnings = warnings,
        )
    }

    /** 标题等已有的精细标记优先,普通正文行继承左右页标记。 */
    private fun pageRegionFor(line: OcrLine, side: PageRegion): PageRegion =
        if (line.pageRegion == PageRegion.TOP_TITLE) PageRegion.TOP_TITLE else side

    /** 页内阅读顺序:先按 y,y 差小于行高中位数一半视为同一行,再按 x。 */
    private fun sortReadingOrder(lines: List<OcrLine>): List<OcrLine> {
        if (lines.size <= 1) return lines
        val medianH = lines.mapNotNull { it.box?.height }.sorted().let { it[it.size / 2] }.coerceAtLeast(1)
        val rowTolerance = medianH / 2f
        return lines.sortedWith(
            Comparator { a, b ->
                val ya = a.box!!.centerY; val yb = b.box!!.centerY
                if (kotlin.math.abs(ya - yb) <= rowTolerance) a.box!!.left.compareTo(b.box!!.left)
                else ya.compareTo(yb)
            }
        )
    }

    /**
     * 双页展开检测:把 x 中心从小到大排,找最大相邻空隙;空隙 ≥ 页宽 [SPREAD_GAP_RATIO]
     * 且两侧都有至少 2 行 → 认为是书脊,返回分割 x。
     */
    private fun detectSpreadSplit(lines: List<OcrLine>, pageW: Float): Float? {
        if (lines.size < 4 || pageW <= 0f) return null
        val centers = lines.map { it.box!!.centerX }.sorted()
        var bestGap = 0f
        var bestMid = 0f
        var leftCount = 0
        for (i in 0 until centers.size - 1) {
            val gap = centers[i + 1] - centers[i]
            if (gap > bestGap) {
                bestGap = gap; bestMid = (centers[i + 1] + centers[i]) / 2f; leftCount = i + 1
            }
        }
        val rightCount = centers.size - leftCount
        return if (bestGap >= pageW * SPREAD_GAP_RATIO && leftCount >= 2 && rightCount >= 2) bestMid else null
    }

    /** 页码样式:1~3 位纯数字(可带 - 包围,如 "- 12 -")。 */
    private fun isPageNumberText(text: String): Boolean {
        val t = text.trim().trim('-', '–', ' ')
        return t.length in 1..3 && t.all { it.isDigit() }
    }

    private fun effectiveHeight(result: OcrResult, lines: List<OcrLine>): Float =
        if (result.imageHeight > 0) result.imageHeight.toFloat()
        else lines.maxOf { it.box!!.bottom }.toFloat()

    private fun effectiveWidth(result: OcrResult, lines: List<OcrLine>): Float =
        if (result.imageWidth > 0) result.imageWidth.toFloat()
        else lines.maxOf { it.box!!.right }.toFloat()

    private const val MAX_ANGLE_DEG = 30f
    private const val MIN_LINE_CONFIDENCE = 0.30f
    private const val SPREAD_GAP_RATIO = 0.12f
}
