package com.lumiread.core.tools

/**
 * `classify_scene` 原生函数的**确定性执行体**(任务书 §4)。
 *
 * 在手动工具调用模式里,模型决定"何时调用 classify_scene",但**返回什么由本类的启发式决定**
 * (不是模型猜)。承接 v1.3.0 的"绘本 / 物品"判定:判 "book" → 走伴读;判 "object" → 解释眼前物品。
 *
 * 纯 JVM、无副作用、可单测(黄金样本思路,CLAUDE.md §B3)。判定与年龄段无关(一本书不会因孩子
 * 年龄而变成物品),年龄自适应体现在 `lookup_word` / `read_aloud` 等其它工具。
 *
 * 启发式(故意简单、可解释):
 *  1. 图像标签含"书/纸/文字"类信号([BOOK_LABEL_HINTS]) → "book"(最强信号,直接定);
 *  2. 否则看 OCR 是否像**成段自然语言**——按**字母数**([letterCount],排除数字/标点/空白)≥
 *     [BOOK_OCR_MIN_LETTERS]。绘本页有成句文字;物品上的注册号/品牌(如飞机 "AUA 00000…")字母少、
 *     数字多,不算;
 *  3. 都不满足 → "object"。
 *
 * **Step 5 修正(检查点① 发现)**:旧版按"总可读字符 ≥6"判,飞机涂装被 OCR 成 "AUA。 00000 000000000"
 * (字符多但**字母仅 3 个**、其余是 0)被误判 book。改成数**字母**而非总字符:数字/标点不计入"成段文字"。
 */
object SceneClassifier {

    /**
     * OCR 里**字母**(含中文,排除数字/标点/空白)达到这个数即认为"有成段文字" → 倾向绘本。
     * 取 6:中文密度高("从前有一只小狗"=7 字母已成句);用字母数而非总字符,避开数字串型物品文字(注册号/型号)。
     */
    private const val BOOK_OCR_MIN_LETTERS = 6

    /** 图像标签里出现这些(小写子串)视为绘本/印刷品信号。 */
    private val BOOK_LABEL_HINTS = listOf(
        "book", "paper", "text", "font", "document", "page",
        "poster", "newspaper", "magazine", "menu", "handwriting", "calligraphy",
        "publication", "letter", "writing", "comic",
    )

    /**
     * @param imageLabels 逗号分隔的 ML Kit 图像标签(工具入参,可空串)
     * @param ocrText     OCR 文本(工具入参,可空串)
     * @return "book" 或 "object"
     */
    fun classify(imageLabels: String, ocrText: String): String {
        // 1) 印刷品/书类标签是最强信号。
        val labelsLower = imageLabels.lowercase()
        if (BOOK_LABEL_HINTS.any { hint -> labelsLower.contains(hint) }) return SCENE_BOOK

        // 2) 是否有成段自然语言:数字母(含 CJK),排除数字/标点/空白。
        val letterCount = ocrText.count { it.isLetter() }
        return if (letterCount >= BOOK_OCR_MIN_LETTERS) SCENE_BOOK else SCENE_OBJECT
    }

    const val SCENE_BOOK = "book"
    const val SCENE_OBJECT = "object"
}
