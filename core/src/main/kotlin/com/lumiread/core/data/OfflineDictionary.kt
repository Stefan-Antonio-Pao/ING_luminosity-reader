package com.lumiread.core.data

import com.lumiread.core.Lang

/**
 * 离线词典查询结果。来源:Step 7 起的随包 WordNet(英) / CC-CEDICT(中英)SQLite。
 *
 * @param term       规范化后的词条
 * @param definition 释义(未按年龄段裁剪的原文;年龄自适应交给 [com.lumiread.core.tools.WordExplainer])
 * @param example    可选例句
 */
data class DictEntry(
    val term: String,
    val definition: String,
    val example: String? = null,
)

/**
 * 离线词典抽象(任务书 §4)。core 接口保持 Android-free —— 真实 SQLite 实现(WordNet + CC-CEDICT)
 * 在 Step 7 落到 :app(用 Android `SQLiteDatabase`/Room),本接口让 `lookup_word` 工具与年龄自适应
 * 逻辑现在就能被 JVM 单测覆盖(用 [FakeOfflineDictionary])。
 *
 * **完全离线、无网络**(CLAUDE.md 离线原则)。
 */
interface OfflineDictionary {
    /**
     * 查一个词。[lang] = 当前输出语言,决定查英文库(WordNet)还是中英库(CC-CEDICT)。
     * 查不到返回 null —— 调用方([WordExplainer])据此走"没把握"兜底,**绝不编造释义**。
     *
     * **故意非 suspend**:本地 SQLite 查询是快速阻塞操作;且 LiteRT-LM `@Tool` 反射方法不能带
     * 隐藏 `Continuation` 参数(suspend 会破坏 schema 生成)。调用方(FunctionCallingEngine)在
     * `Dispatchers.IO` 上调,阻塞可接受。
     */
    fun lookup(term: String, lang: Lang): DictEntry?
}

/**
 * 空词典:Step 3 的诚实占位(真实 SQLite 词典在 Step 7 接入)。
 *
 * **不是 Fake-冒充-真实**:它老老实实对任何词返回 null,`lookup_word` 工具会走"没把握、和孩子一起看"
 * 的兜底文案,而非编造定义(CLAUDE.md §C5 / 任务书 §0.5"不许造假")。Step 7 用真实词典替换本实现。
 */
object EmptyOfflineDictionary : OfflineDictionary {
    override fun lookup(term: String, lang: Lang): DictEntry? = null
}
