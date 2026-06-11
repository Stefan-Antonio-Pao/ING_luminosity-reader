package com.lumiread.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiread.core.Lang
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **lookup_word ↔ 词典硬验证门 · 上机部分**(任务书 §1.3 第 3 条):
 * 在真机上确认词库 DB 已打包进 APK、能从 asset 复制加载、caterpillar/毛毛虫 查询命中真实词条。
 *
 * 跑法:./gradlew :app:connectedDebugAndroidTest
 * (走的是与生产 lookup_word 完全相同的 SqliteOfflineDictionary —— 不是平行实现。)
 */
@RunWith(AndroidJUnit4::class)
class SqliteOfflineDictionaryDeviceTest {

    private val dict = SqliteOfflineDictionary(
        InstrumentationRegistry.getInstrumentation().targetContext
    )

    @Test fun caterpillar_hits_wordnet_on_device() {
        val entry = dict.lookup("caterpillar", Lang.EN)
        assertTrue("caterpillar 必须在真机命中 WordNet", entry != null)
        assertTrue(
            "释义应为 WordNet 真实词条,实际: ${entry!!.definition}",
            entry.definition.contains("larva") && entry.definition.contains("butterfly"),
        )
        Log.i("DictHardVerify", "caterpillar (EN) -> ${entry.definition}")
    }

    @Test fun maomaochong_hits_cedict_on_device() {
        val entry = dict.lookup("毛毛虫", Lang.ZH)
        assertTrue("毛毛虫 必须在真机命中 CC-CEDICT", entry != null)
        assertTrue(
            "释义应为 CC-CEDICT 真实词条,实际: ${entry!!.definition}",
            entry.definition.contains("caterpillar"),
        )
        Log.i("DictHardVerify", "毛毛虫 (ZH) -> ${entry.definition}")
    }

    @Test fun miss_returns_null_gracefully_on_device() {
        assertNull(dict.lookup("zzzz-not-a-word-zzzz", Lang.EN))
    }
}
