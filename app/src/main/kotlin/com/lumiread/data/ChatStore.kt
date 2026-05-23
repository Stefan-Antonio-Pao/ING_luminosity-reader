package com.lumiread.data

import android.content.Context
import android.util.Log
import com.lumiread.ui.ChatRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 当前会话本地持久化(用户要求:重启后聊天不丢)。
 *
 * 存储:`context.filesDir/chat/current.json`,JSON 数组,每行一个 [ChatRow]。
 * 用 `org.json`(Android 内置)避免引新依赖。本类只管"最近一段对话",历史归档不在范围内。
 *
 * 一致性:写入走"先写 `.tmp`、再 `rename`"原子语义,避免应用半路被杀留下半截 JSON。
 * 并发:全程 [Mutex] 串行化,UI 多次连续触发 [save] 不会交错。
 *
 * 不持久化:
 *  - LiteRT-LM `Conversation` 的 KV cache(进程内对象,本就不能跨重启)→ 重启后用户再发一条,
 *    [com.lumiread.ui.ChatState] 会按需重开 ChatSession,LLM 没有上下文 KV 但消息文本仍在 UI。
 *  - 用户图片路径仍存,但 cacheDir 可能已被系统清掉 → UI 仅显示文件名。
 */
class ChatStore(context: Context) {
    private val dir  = File(context.filesDir, "chat").apply { mkdirs() }
    private val file = File(dir, "current.json")
    private val mutex = Mutex()

    suspend fun load(): List<ChatRow> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock emptyList<ChatRow>()
            runCatching {
                val arr = JSONArray(file.readText(Charsets.UTF_8))
                val out = ArrayList<ChatRow>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    when (o.optString("role")) {
                        "user" -> out += ChatRow.User(
                            text          = o.optString("text", ""),
                            imagePaths    = o.optJSONArray("imagePaths")?.toStringList().orEmpty(),
                            ocrSummary    = o.optString("ocrSummary", ""),
                            labelsSummary = o.optString("labelsSummary", ""),
                        )
                        "assistant" -> out += ChatRow.Assistant(
                            text  = o.optString("text", ""),
                            done  = o.optBoolean("done", true),
                            error = o.optString("error", "").takeIf { it.isNotEmpty() },
                        )
                    }
                }
                out
            }.onFailure {
                Log.w(TAG, "ChatStore.load 读取失败,丢弃旧文件: ${it.message}")
                runCatching { file.delete() }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun save(messages: List<ChatRow>): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val arr = JSONArray()
            messages.forEach { row ->
                arr.put(when (row) {
                    is ChatRow.User -> JSONObject().apply {
                        put("role", "user")
                        put("text", row.text)
                        put("imagePaths", JSONArray(row.imagePaths))
                        put("ocrSummary", row.ocrSummary)
                        put("labelsSummary", row.labelsSummary)
                    }
                    is ChatRow.Assistant -> JSONObject().apply {
                        put("role", "assistant")
                        put("text", row.text)
                        put("done", row.done)
                        row.error?.let { put("error", it) }
                    }
                })
            }
            val tmp = File(dir, "current.json.tmp")
            tmp.writeText(arr.toString(), Charsets.UTF_8)
            // rename 是原子的(同分区);若目标存在先删,Windows JVM 在 Android 上不是问题。
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { file.delete() }
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    companion object {
        private const val TAG = "ChatStore"
    }
}
