package com.lumiread.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.lumiread.core.Lang
import com.lumiread.core.data.DictDbContract
import com.lumiread.core.data.DictEntry
import com.lumiread.core.data.OfflineDictionary
import java.io.File

/**
 * 随包 SQLite 离线词典(轨道 A,FACTS#F14):WordNet 3.1(EN)+ CC-CEDICT(ZH)。
 *
 * asset 内 SQLite 不能直接打开(压缩流)→ 首次查询时把 [DictDbContract.ASSET_PATH] 复制到
 * `noBackupFilesDir/dict/`(词典是静态资源,不该进云备份),之后 OPEN_READONLY 打开缓存复用。
 * 复制带版本戳文件名(asset 大小作指纹),升级 APK 换词典后自动重新复制。
 *
 * **绝不崩**(任务书 §1.3 第 4 条):复制失败/DB 损坏/查询异常 → 返回 null,
 * `lookup_word` 走 WordExplainer 的 not_found 兜底(模型给谨慎解释),与词典缺失同路径。
 *
 * SQL 与词条规范化共用 [DictDbContract] —— JVM 硬验证单测跑的就是同一条查询。
 */
class SqliteOfflineDictionary(private val context: Context) : OfflineDictionary {

    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var broken = false
    private val lock = Any()

    override fun lookup(term: String, lang: Lang): DictEntry? {
        val database = openOrNull() ?: return null
        val normalized = DictDbContract.normalizeTerm(term, lang)
        if (normalized.isEmpty()) return null
        return try {
            database.rawQuery(DictDbContract.QUERY, arrayOf(normalized, DictDbContract.langCode(lang))).use { c ->
                if (c.moveToFirst()) {
                    DictEntry(
                        term = c.getString(0),
                        definition = c.getString(1),
                        example = if (c.isNull(2)) null else c.getString(2),
                    )
                } else null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "词典查询异常(降级 not_found):term=$normalized", t)
            null
        }
    }

    private fun openOrNull(): SQLiteDatabase? {
        db?.let { return it }
        if (broken) return null
        synchronized(lock) {
            db?.let { return it }
            if (broken) return null
            return try {
                val file = ensureDbFile()
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    .also {
                        db = it
                        Log.i(TAG, "词典 DB 已打开:${file.name},${file.length() / 1024} KB")
                    }
            } catch (t: Throwable) {
                Log.e(TAG, "词典 DB 打开失败(lookup_word 将走 not_found 兜底)", t)
                broken = true
                null
            }
        }
    }

    /** 从 asset 复制到 noBackupFilesDir(带 asset 长度指纹,APK 升级换词典自动刷新)。 */
    private fun ensureDbFile(): File {
        val dir = File(context.noBackupFilesDir, "dict").apply { mkdirs() }
        val assetLen = context.assets.openFd(DictDbContract.ASSET_PATH).use { it.length }
        val target = File(dir, "lumi_dict_$assetLen.db")
        if (target.isFile && target.length() == assetLen) return target
        // 清掉旧版本词典文件。
        dir.listFiles()?.filter { it.name != target.name }?.forEach { it.delete() }
        val tmp = File(dir, "${target.name}.tmp")
        context.assets.open(DictDbContract.ASSET_PATH).use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("词典 DB 复制改名失败")
        }
        return target
    }

    private companion object {
        const val TAG = "SqliteOfflineDictionary"
    }
}
