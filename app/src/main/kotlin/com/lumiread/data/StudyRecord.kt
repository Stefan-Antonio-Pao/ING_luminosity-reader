package com.lumiread.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 「学习记录」数据层。
 *
 * 单文件容纳 [StudyRecord]/[StudyDao]/[LumiReadDb],避免 3 个小文件爆炸。
 *
 * 表 `study_session`:一条 = 用户从「开始第一轮 user↔assistant 完成」到「最后一轮完成」的一整段会话。
 *  - `startedAt`/`endedAt`:Unix ms;首轮 AssistantDone 时建行,后续每轮刷 `endedAt`,
 *    所以即使中途 App 被杀也不会漏算已完成轮(每轮 UPDATE 一次)。
 *  - `turnCount`:user↔assistant 轮数(只在 AssistantDone 时 +1)。
 *  - `contentSummary`:首轮的 OCR + labels 摘要;直聊空。≤ 80 字 UI 截断,DB 不截。
 *
 * `exportSchema = false`:目前不维护 schema 历史。
 * `@Insert/@Update` 非 suspend,在 [StudyStore] 里用 `withContext(IO)` 包,与
 * [SettingsRepository] 风格一致。
 */
@Entity(tableName = "study_session")
data class StudyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt:   Long,
    val lang:      String,
    val ageBand:   String,
    val turnCount: Int,
    val contentSummary: String,
)

@Dao
interface StudyDao {
    @Insert
    fun insert(record: StudyRecord): Long

    @Update
    fun update(record: StudyRecord)

    @Query("SELECT * FROM study_session WHERE id = :id")
    fun get(id: Long): StudyRecord?

    @Query("SELECT * FROM study_session ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<StudyRecord>>

    @Query("DELETE FROM study_session")
    fun clearAll()
}

@Database(entities = [StudyRecord::class], version = 1, exportSchema = false)
abstract class LumiReadDb : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        const val DB_NAME = "lumiread.db"
    }
}
