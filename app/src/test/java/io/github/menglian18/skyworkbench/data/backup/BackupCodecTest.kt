package io.github.menglian18.skyworkbench.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import io.github.menglian18.skyworkbench.data.db.AppStateEntity
import io.github.menglian18.skyworkbench.data.db.MemoEntity
import io.github.menglian18.skyworkbench.data.db.NumberRecordEntity
import io.github.menglian18.skyworkbench.data.db.NumberTableEntity
import io.github.menglian18.skyworkbench.data.db.SkyAccountEntity
import io.github.menglian18.skyworkbench.data.db.SkyEventEntity
import io.github.menglian18.skyworkbench.data.db.SkyHoldingEntity
import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.data.db.SkyTravelingSpiritEntity
import io.github.menglian18.skyworkbench.data.db.SkyWishEntity
import io.github.menglian18.skyworkbench.data.db.StudyCardEntity
import io.github.menglian18.skyworkbench.data.db.StudyReviewLogEntity
import io.github.menglian18.skyworkbench.data.db.TodoEntity

/**
 * M6 阶段的测试之一：备份文件的读写。
 *
 * 重点测"导出再导入，内容一模一样"—— 换手机时数据不能少、不能变。
 */
class BackupCodecTest {

    private fun fullSnapshot() = BackupSnapshot(
        exportedAt = 1_760_000_000_000L,
        todos = listOf(
            TodoEntity(
                id = 1, title = "买牛奶", note = "两盒", priority = 2, timePoint = "08:30",
                done = true, originalDate = "2026-10-08", dueDate = "2026-10-09",
                postponedCount = 2, doneAt = 123L, sortOrder = 3, createdAt = 10L, updatedAt = 20L,
            ),
            TodoEntity(
                id = 2, title = "没时间的", originalDate = "2026-10-09", dueDate = "2026-10-09",
                createdAt = 30L, updatedAt = 40L,
            ),
        ),
        studyCards = listOf(
            StudyCardEntity(
                id = 5, front = "apple", back = "苹果", note = "n.", phase = 2,
                stability = 10.964, difficulty = 2.1176, reps = 3, lapses = 1,
                lastReviewedAt = 500L, dueAt = 600L, createdAt = 100L, updatedAt = 200L,
            ),
        ),
        studyLogs = listOf(
            StudyReviewLogEntity(id = 1, cardId = 5, rating = 3, reviewedAt = 500L, elapsedDays = 2.0, scheduledDays = 11),
        ),
        memos = listOf(
            MemoEntity(id = 3, title = "快递", content = "SF123456", category = "快递", pinned = true, sortOrder = 1, createdAt = 1L, updatedAt = 2L),
        ),
        numberTables = listOf(
            NumberTableEntity(id = 1, name = "体重", unit = "kg", decimals = 1, allowMultiplePerDay = false, sortOrder = 0),
        ),
        numberRecords = listOf(
            NumberRecordEntity(id = 9, tableId = 1, date = "2026-10-09", value = 62.4, note = "早上", createdAt = 5L, updatedAt = 6L),
        ),
        skyAccounts = listOf(
            SkyAccountEntity(id = 1, nickname = "小号1", loginMethod = "华为", server = "国服", binding = "已绑", note = "主玩", password = "abc123", sortOrder = 0),
        ),
        skyWishes = listOf(
            SkyWishEntity(id = 2, itemGuid = "abc", itemName = "斗篷", spiritName = "指路蜡烛匠", costCandle = 60, costHeart = 3, costAscended = 0, owned = true, obtained = false, sortOrder = 0),
        ),
        skyHolding = SkyHoldingEntity(candles = 120, hearts = 8, ascended = 2),
        skySpirits = listOf(
            SkyTravelingSpiritEntity(
                id = 1, spiritName = "放松日浴者", type = "常规复刻", startDate = "2026-10-01",
                endDate = "2026-10-05", items = "面饰、发饰", source = "WIKI", fetchedAt = 7L, manuallyEdited = true,
            ),
        ),
        skySeasons = listOf(
            SkySeasonEntity(id = 1, name = "圣岛季", startDate = "2026-07-17", endDate = "2026-10-02", fetchedAt = 8L),
        ),
        appState = listOf(AppStateEntity(key = "sky_last_fetch", value = "12345")),
    )

    @Test
    fun `导出再导入_内容完全一致`() {
        val original = fullSnapshot()
        val restored = BackupCodec.decode(BackupCodec.encode(original))
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `逐张表都对得上`() {
        val restored = BackupCodec.decode(BackupCodec.encode(fullSnapshot()))!!
        assertEquals(2, restored.todos.size)
        assertEquals("买牛奶", restored.todos[0].title)
        assertEquals("08:30", restored.todos[0].timePoint)
        assertEquals(true, restored.todos[0].done)
        assertEquals(2, restored.todos[0].postponedCount)
        assertEquals(1, restored.studyCards.size)
        assertEquals(10.964, restored.studyCards[0].stability, 1e-9)
        assertEquals(1, restored.studyLogs.size)
        assertEquals(1, restored.memos.size)
        assertEquals(1, restored.numberRecords.size)
        assertEquals(62.4, restored.numberRecords[0].value, 1e-9)
        assertEquals(1, restored.skyAccounts.size)
        assertEquals(1, restored.skyWishes.size)
        assertEquals(120, restored.skyHolding?.candles)
        assertEquals(1, restored.skySpirits.size)
        assertEquals(true, restored.skySpirits[0].manuallyEdited)
        assertEquals(1, restored.skySeasons.size)
        assertEquals("12345", restored.appState[0].value)
    }

    @Test
    fun `可选字段为空时也一致`() {
        val snapshot = BackupSnapshot(
            exportedAt = 1L,
            todos = listOf(
                TodoEntity(id = 1, title = "没有时间点和备注", originalDate = "2026-10-09", dueDate = "2026-10-09", createdAt = 1L, updatedAt = 1L),
            ),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(snapshot))!!
        assertNull(restored.todos[0].timePoint)
        assertNull(restored.todos[0].doneAt)
        assertEquals(snapshot, restored)
    }

    @Test
    fun `空备份也能往返`() {
        val snapshot = BackupSnapshot(exportedAt = 1L)
        val restored = BackupCodec.decode(BackupCodec.encode(snapshot))!!
        assertEquals(0, restored.todos.size)
        assertNull(restored.skyHolding)
        assertEquals(snapshot, restored)
    }

    @Test
    fun `读不出来的内容返回 null 而不是崩掉`() {
        assertNull(BackupCodec.decode("这不是 json"))
        assertNull(BackupCodec.decode(""))
    }

    @Test
    fun `版本号会被写进去`() {
        val text = BackupCodec.encode(fullSnapshot())
        val restored = BackupCodec.decode(text)!!
        assertEquals(BackupCodec.CURRENT_VERSION, restored.version)
    }

    @Test
    fun `摘要数字与内容一致`() {
        val summary = fullSnapshot().summary()
        assertEquals(2, summary.todos)
        assertEquals(1, summary.studyCards)
        assertEquals(1, summary.memos)
        assertEquals(1, summary.numberTables)
        assertEquals(1, summary.numberRecords)
        assertEquals(1, summary.skyAccounts)
        assertEquals(1, summary.skyWishes)
        assertEquals(1, summary.skySpirits)
        assertEquals(1, summary.skySeasons)
        assertNotNull(summary.describe())
    }

    @Test
    fun `建议的文件名带日期`() {
        assertEquals("光遇工作台备份-2026-10-09.json", BackupCodec.suggestedFileName("2026-10-09"))
    }

    @Test
    fun `中文和换行不会坏掉`() {
        val snapshot = BackupSnapshot(
            exportedAt = 1L,
            memos = listOf(
                MemoEntity(id = 1, title = "多行内容", content = "第一行\n第二行\t带制表符", category = "其他", createdAt = 1L, updatedAt = 1L),
            ),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(snapshot))!!
        assertEquals("第一行\n第二行\t带制表符", restored.memos[0].content)
    }


    @Test
    fun `活动也会一起导出再导入`() {
        val snapshot = BackupSnapshot(
            exportedAt = 1L,
            skyEvents = listOf(
                SkyEventEntity(
                    id = 7,
                    name = "2026年秋宵节",
                    startDate = "2026-09-24",
                    endDate = "2026-10-14",
                    fetchedAt = 5L,
                ),
                SkyEventEntity(
                    id = 8,
                    name = "还没公布结束时间的活动",
                    startDate = "2026-11-01",
                    endDate = "",
                    fetchedAt = 5L,
                ),
            ),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(snapshot))!!
        assertEquals(2, restored.skyEvents.size)
        assertEquals("2026年秋宵节", restored.skyEvents[0].name)
        assertEquals("2026-10-14", restored.skyEvents[0].endDate)
        assertEquals("", restored.skyEvents[1].endDate)
        assertEquals(2, restored.summary().skyEvents)
        assertTrue(
            "导入后的摘要要提到活动",
            restored.summary().describe().contains("活动 2 个"),
        )
    }
}
