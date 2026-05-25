package com.lumiread.data

import android.content.Context
import androidx.room.Room
import com.lumiread.core.AgeBand
import com.lumiread.core.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 「学习记录」业务封装。把 Room 的 [StudyDao] 包成 UI 直接消费的形态。
 *
 * 调用点(详见 LumiReadApp.kt 的 `ChatEvent.AssistantDone` 分支):
 * - 首轮:[beginSession] 插一行,返回新 `id` → `ChatState.studySessionId` 持有它。
 * - 后续每轮:[recordTurn] 拿到 `id`,UPDATE `endedAt` + `turnCount + 1`。
 * - 切「↻ 新会话」或冷启动:`ChatState.studySessionId` 置 null,下一条 user 触发 [beginSession] 起新行。
 *
 * `endSession` 不存在——最后一轮 AssistantDone 已写好 `endedAt`,行本身就完结了。
 * 这样实现的好处:进程被系统杀也不会留下「行存在但 `endedAt < startedAt`」的脏行。
 */
class StudyStore(context: Context) {
    private val db: LumiReadDb = Room
        .databaseBuilder(context.applicationContext, LumiReadDb::class.java, LumiReadDb.DB_NAME)
        .build()
    private val dao: StudyDao = db.studyDao()

    /** UI 侧用 `collectAsState` 订阅。Room 会在 INSERT/UPDATE/DELETE 后自动推送新快照。 */
    val all: Flow<List<StudyRecord>> = dao.observeAll()

    /** 首轮 AssistantDone 调用。返回新行 id。 */
    suspend fun beginSession(lang: Lang, age: AgeBand, contentSummary: String): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.insert(
                StudyRecord(
                    startedAt = now,
                    endedAt   = now,
                    lang      = lang.name,
                    ageBand   = age.name,
                    turnCount = 1,
                    contentSummary = contentSummary,
                )
            )
        }

    /** 后续每轮 AssistantDone 调用。行不存在(被清空)则 no-op,不抛。 */
    suspend fun recordTurn(id: Long): Unit = withContext(Dispatchers.IO) {
        val rec = dao.get(id) ?: return@withContext
        dao.update(
            rec.copy(
                endedAt   = System.currentTimeMillis(),
                turnCount = rec.turnCount + 1,
            )
        )
    }

    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
