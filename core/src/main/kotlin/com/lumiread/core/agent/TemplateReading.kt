package com.lumiread.core.agent

import com.lumiread.core.AgeBand
import com.lumiread.core.Lang

/**
 * Level 2 模板化伴读(OCR_TECH_DOC §14 四层 fallback)。
 *
 * 当 FC(Level 0)与 TwoStage(Level 1)都失败(模型未加载/推理崩溃/超时)时,
 * 用确定性模板保住核心体验:有 OCR 文本就念出来 + 一个适龄问题;没有文本就走
 * 图像观察引导(Level 3 文案)。**纯确定性,绝不依赖 LLM,绝不崩。**
 */
object TemplateReading {

    /** 生成本轮模板回复。[ocrText] 为空白时输出图像观察引导。 */
    fun message(lang: Lang, ageBand: AgeBand, ocrText: String?): String {
        val text = ocrText?.trim().orEmpty()
        return if (text.isNotEmpty()) withText(lang, ageBand, text) else imageOnly(lang)
    }

    private fun withText(lang: Lang, ageBand: AgeBand, text: String): String = when (lang) {
        Lang.ZH -> buildString {
            appendLine("这一页写着:")
            appendLine(text)
            append(
                when (ageBand) {
                    AgeBand.TODDLER -> "宝贝,你看到图上有什么呀?"
                    AgeBand.PRESCHOOL -> "你觉得接下来会发生什么呢?"
                    AgeBand.PREADOLESCENT -> "你觉得这一页最有意思的地方是什么?"
                }
            )
        }
        Lang.EN -> buildString {
            appendLine("This page says:")
            appendLine(text)
            append(
                when (ageBand) {
                    AgeBand.TODDLER -> "What do you see in the picture?"
                    AgeBand.PRESCHOOL -> "What do you think happens next?"
                    AgeBand.PREADOLESCENT -> "What do you find most interesting on this page?"
                }
            )
        }
    }

    private fun imageOnly(lang: Lang): String = when (lang) {
        Lang.ZH -> "我们先一起看看图上有什么吧!你能找到你最喜欢的东西吗?"
        Lang.EN -> "Let's look at the picture together first! Can you find your favorite thing in it?"
    }
}
