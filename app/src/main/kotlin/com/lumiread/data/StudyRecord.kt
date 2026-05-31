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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Phase 5(下半)——「学习记录」数据层。
 *
 * 单文件容纳 [StudyRecord]/[StudyDao]/[LumiReadDb],避免 3 个小文件爆炸。
 *
 * 表 `study_session`:一条 = 用户从「开始第一轮 user↔assistant 完成」到「最后一轮完成」的一整段会话。
 *  - `startedAt`/`endedAt`:Unix ms;首轮 AssistantDone 时建行,后续每轮刷 `endedAt`,
 *    所以即使中途 App 被杀也不会漏算已完成轮(每轮 UPDATE 一次)。
 *  - `turnCount`:user↔assistant 轮数(只在 AssistantDone 时 +1)。
 *  - `contentSummary`:首轮的 OCR + labels 摘要;直聊空。≤ 80 字 UI 截断,DB 不截。
 *
 * `exportSchema = false`:hackathon 阶段不维护 schema 历史,Phase 6 上 Play 前再开。
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

/**
 * v2.0.0 Step 7a——`save_learning_record` 原生函数写入的「单词掌握」记录。
 *
 * 与 [StudyRecord](会话级时长/轮数)正交:这条记一个**词**及孩子是否掌握,含年龄段上下文(任务书 §4)。
 * 由 Gemma 4 在对话中判断孩子掌握了某词时调用 `save_learning_record(word, mastered)` 触发写入。
 *
 * **注**:本期不改「我的学习」UI(§11 UI 冻结),故这些词记录已落库但暂不在 UI 展示;
 * 数据已可被将来的 UI/导出消费。
 */
@Entity(tableName = "word_record")
data class WordRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word:      String,
    val mastered:  Boolean,
    val lang:      String,
    val ageBand:   String,
    val createdAt: Long,
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

    // ---- v2.0.0 Step 7a:单词掌握记录 ----
    @Insert
    fun insertWord(record: WordRecord): Long

    @Query("SELECT * FROM word_record ORDER BY createdAt DESC")
    fun observeWords(): Flow<List<WordRecord>>

    @Query("DELETE FROM word_record")
    fun clearWords()
}

@Database(entities = [StudyRecord::class, WordRecord::class], version = 2, exportSchema = false)
abstract class LumiReadDb : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        const val DB_NAME = "lumiread.db"

        /**
         * v1→v2:新增 `word_record` 表(save_learning_record 用)。**非破坏性**——
         * 只 CREATE 新表,保留用户已有的 `study_session` 学习历史。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `word_record` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`word` TEXT NOT NULL, " +
                        "`mastered` INTEGER NOT NULL, " +
                        "`lang` TEXT NOT NULL, " +
                        "`ageBand` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
