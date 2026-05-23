package com.lumiread.core.pipeline

import com.lumiread.core.Label
import com.lumiread.core.Lang
import com.lumiread.core.OcrLine
import com.lumiread.core.OcrResult

/**
 * 多页 OCR / 标签合并辅助。从 [Pipeline] 提出来,以便 [ChatSession] 也能复用。
 * `internal` 可见性:只在 :core 的 pipeline 包内使用,不暴露给上层 :app。
 */

/** 把多页 OCR 平摊成一份;detectedLang 取众数(并列时取第一个非空)。 */
internal fun mergeOcr(pages: List<OcrResult>): OcrResult {
    val allLines: List<OcrLine> = pages.flatMap { it.lines }
    val detectedLang: Lang? = pages.mapNotNull { it.detectedLang }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return OcrResult(lines = allLines, detectedLang = detectedLang)
}

/** Union by name,同名取最高置信度,按置信度降序取前 K。 */
internal fun mergeLabels(perPage: List<List<Label>>, topK: Int): List<Label> {
    val byName = mutableMapOf<String, Float>()
    perPage.flatten().forEach { lab ->
        val prev = byName[lab.name] ?: 0f
        if (lab.confidence > prev) byName[lab.name] = lab.confidence
    }
    return byName.entries
        .map { Label(it.key, it.value) }
        .sortedByDescending { it.confidence }
        .take(topK)
}
