package com.lumiread.data

import android.util.Log
import com.lumiread.AppGraph
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout

/**
 * 故事模式"开头 chips"的本地模型生成(Phase 6)。
 *
 * 设计要求:首次进入由**本地模型**生成,不在 Composable 硬编码;支持"换一组";
 * loading / ready / fallback 三态;模型不可用时回退到 strings 文案,不崩溃。
 *
 * 安全:复用 [com.lumiread.core.llm.LlmEngine.generate] 契约(与 Pipeline 同一条安全调用方式,
 * 一次一条 Conversation),**不改 core 签名**。只在无活动对话、串行调用;withTimeout 防卡死;
 * 任何异常 → 返回空,调用方回退到资源文案。生成结果按 (lang, age) 在内存缓存,"换一组"清缓存重生成。
 */
object StoryRepository {

    private data class Key(val lang: Lang, val age: AgeBand)

    private val cache = mutableMapOf<Key, List<String>>()

    /** 返回缓存(若有);否则 null,调用方据此决定是否进入 loading 态去 [generate]。 */
    fun cached(lang: Lang, age: AgeBand): List<String>? = cache[Key(lang, age)]

    fun invalidate(lang: Lang, age: AgeBand) {
        cache.remove(Key(lang, age))
    }

    /**
     * 生成 [count] 个故事开头(每个 1 句)。成功返回非空列表并缓存;失败/超时返回空列表。
     * 必须在 IO/默认调度上调用(内部 generate 已 flowOn(IO))。
     */
    suspend fun generate(lang: Lang, age: AgeBand, count: Int): List<String> {
        cache[Key(lang, age)]?.let { return it }
        val result = runCatching {
            withTimeout(75_000) {
                val prompt = buildPrompt(lang, age, count)
                val full = AppGraph.llm.generate(prompt).toList().joinToString("")
                parseOpenings(full, count)
            }
        }.getOrElse {
            Log.w("StoryRepository", "opening generation failed: ${it.message}")
            emptyList()
        }
        if (result.isNotEmpty()) cache[Key(lang, age)] = result
        return result
    }

    private fun buildPrompt(lang: Lang, age: AgeBand, count: Int): String {
        val level = when (age) {
            AgeBand.TODDLER -> if (lang == Lang.ZH) "1.5–3 岁幼儿,极短、拟声、温暖" else "toddlers, very short, playful, warm"
            AgeBand.PRESCHOOL -> if (lang == Lang.ZH) "3–6 岁学龄前,简单生动" else "preschoolers, simple and vivid"
            AgeBand.PREADOLESCENT -> if (lang == Lang.ZH) "6–10 岁,稍有想象力与悬念" else "older children, a touch of wonder and suspense"
        }
        return if (lang == Lang.ZH) {
            "你是儿童故事伙伴。请为$level 的孩子,写 $count 个**不同的**故事开头," +
                "每个只写一句话,温暖有趣、适合朗读。每行一个,用数字编号(1. 2. ...),不要解释。"
        } else {
            "You are a children's story companion. Write $count DIFFERENT one-sentence story openers for $level. " +
                "Each on its own numbered line (1. 2. ...), warm and read-aloud friendly. No explanations."
        }
    }

    /** 解析模型输出:按行拆,去掉编号/项目符号,过滤太短/太长,取前 count 条。 */
    private fun parseOpenings(raw: String, count: Int): List<String> =
        raw.lines()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .map { it.replace(Regex("^\\d+[.、)]\\s*"), "").trim() }
            .filter { it.length in 6..120 }
            .distinct()
            .take(count)
}
