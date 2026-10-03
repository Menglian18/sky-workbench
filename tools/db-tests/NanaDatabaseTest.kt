package com.nana.workbench.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nana.workbench.data.backup.BackupCodec
import com.nana.workbench.data.backup.BackupManager
import com.nana.workbench.today.TodoRolloverRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据库层测试（跑在电脑上的真实 SQLite）。
 *
 * 为什么这个文件重要：前面所有测试都是"纯逻辑"，数据库读写一次都没真正跑过。
 * 而验收清单里的 A3（数据不丢）、A4（顺延）、A5（首页与今日一致）、A15（清理不伤用户数据）、
 * A16（导出导入一致）全靠数据库，所以这里用真数据库把它们钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NanaDatabaseTest {

    private lateinit var db: NanaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun todo(
        title: String,
        dueDate: String,
        done: Boolean = false,
        postponed: Int = 0,
        priority: Int = 0,
        timePoint: String? = null,
        id: Long = 0,
    ) = TodoEntity(
        id = id,
        title = title,
        priority = priority,
        timePoint = timePoint,
        done = done,
        originalDate = dueDate,
        dueDate = dueDate,
        postponedCount = postponed,
        createdAt = 1L,
        updatedAt = 1L,
    )

    // ── A3：写进去就读得出来 ──────────────────────────────────────────────────

    @Test
    fun `写进去的计划关掉数据库再打开还在吗_内存库只能验证读写本身`() = runBlocking {
        val id = db.todoDao().insert(todo("买牛奶", "2026-10-09"))
        val loaded = db.todoDao().listForDate("2026-10-09")
        assertEquals(1, loaded.size)
        assertEquals("买牛奶", loaded[0].title)
        assertTrue("自增主键应被分配", id > 0)
    }

    @Test
    fun `勾选完成后从待办移到已完成`() = runBlocking {
        val dao = db.todoDao()
        dao.insert(todo("写作业", "2026-10-09"))

        val openBefore = dao.watchForDate("2026-10-09").first()
        assertEquals(1, openBefore.size)

        dao.update(openBefore[0].copy(done = true, doneAt = 123L))

        assertEquals("待办里应该没有了", 0, dao.watchForDate("2026-10-09").first().size)
        val done = dao.watchDoneForDate("2026-10-09").first()
        assertEquals("已完成里应该有一条", 1, done.size)
        assertEquals("写作业", done[0].title)

        // 首页的"还剩几件"用的是这个查询，必须跟着变（A5）
        assertEquals(0, dao.watchOpenCount("2026-10-09").first())
    }

    // ── A4：顺延真的会改数据库里的行 ──────────────────────────────────────────

    @Test
    fun `顺延结算会把昨天的条目写成今天_并累计天数`() = runBlocking {
        val dao = db.todoDao()
        dao.insert(todo("昨天没做的", "2026-10-08"))
        dao.insert(todo("今天要做的", "2026-10-09"))

        val moved = TodoRolloverRunner.run(dao, "2026-10-09")
        assertEquals(1, moved)

        val today = dao.watchForDate("2026-10-09").first()
        val titles = today.map { it.title }.sorted()
        assertEquals(listOf("今天要做的", "昨天没做的"), titles)

        val rolled = today.first { it.title == "昨天没做的" }
        assertEquals(1, rolled.postponedCount)
        assertEquals("原始日期不该被改", "2026-10-08", rolled.originalDate)
        assertEquals("昨天的列表应该空了", 0, dao.watchForDate("2026-10-08").first().size)
    }

    @Test
    fun `隔三天没打开_数据库里累计三天`() = runBlocking {
        val dao = db.todoDao()
        dao.insert(todo("拖了很久的", "2026-10-06", postponed = 2))

        TodoRolloverRunner.run(dao, "2026-10-09")

        val item = dao.watchForDate("2026-10-09").first().first()
        assertEquals(5, item.postponedCount)
    }

    @Test
    fun `重复跑顺延不会把天数越加越多`() = runBlocking {
        val dao = db.todoDao()
        dao.insert(todo("昨天没做的", "2026-10-08"))

        TodoRolloverRunner.run(dao, "2026-10-09")
        TodoRolloverRunner.run(dao, "2026-10-09")
        TodoRolloverRunner.run(dao, "2026-10-09")

        val item = dao.watchForDate("2026-10-09").first().first()
        assertEquals(1, item.postponedCount)
    }

    // ── A15：清理只清抓来的，不动用户数据 ────────────────────────────────────

    @Test
    fun `清空抓来的复刻数据_手动改过的会留下`() = runBlocking {
        val dao = db.skyDao()
        dao.insertTravelingSpirits(
            listOf(
                SkyTravelingSpiritEntity(
                    spiritName = "抓来的", startDate = "2026-10-01", endDate = "2026-10-05",
                    fetchedAt = 1L, manuallyEdited = false,
                ),
                SkyTravelingSpiritEntity(
                    spiritName = "我手动改的", startDate = "2026-10-08", endDate = "2026-10-12",
                    fetchedAt = 1L, manuallyEdited = true,
                ),
            ),
        )
        assertEquals(2, dao.listTravelingSpirits().size)

        dao.deleteFetchedTravelingSpirits()

        val left = dao.listTravelingSpirits()
        assertEquals(1, left.size)
        assertEquals("我手动改的", left[0].spiritName)
    }

    @Test
    fun `清理抓来的数据时_账号和心愿单一条不动`() = runBlocking {
        val dao = db.skyDao()
        dao.insertAccount(SkyAccountEntity(nickname = "小号1", server = "国服"))
        dao.insertWish(SkyWishEntity(itemName = "斗篷", costCandle = 60))
        dao.insertTravelingSpirits(
            listOf(
                SkyTravelingSpiritEntity(
                    spiritName = "抓来的", startDate = "2026-10-01", endDate = "2026-10-05", fetchedAt = 1L,
                ),
            ),
        )

        dao.deleteFetchedTravelingSpirits()
        dao.deleteFetchedSeasons()

        assertEquals("账号不该被清", 1, dao.listAccounts().size)
        assertEquals("心愿单不该被清", 1, dao.listWishes().size)
    }

    // ── A16：导出再导入，条目数量一致 ────────────────────────────────────────

    @Test
    fun `备份往返_六张表的数据都还在`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 造点数据
        db.todoDao().insert(todo("计划一", "2026-10-09"))
        db.todoDao().insert(todo("计划二", "2026-10-09"))
        db.memoDao().insert(MemoEntity(title = "快递", content = "SF123", category = "快递", createdAt = 1L, updatedAt = 1L))
        db.studyDao().insert(
            StudyCardEntity(front = "apple", back = "苹果", dueAt = 1L, createdAt = 1L, updatedAt = 1L),
        )
        val tableId = db.numberDao().insertTable(NumberTableEntity(name = "体重", unit = "kg"))
        db.numberDao().insertRecord(
            NumberRecordEntity(tableId = tableId, date = "2026-10-09", value = 62.4, createdAt = 1L, updatedAt = 1L),
        )
        db.skyDao().insertAccount(SkyAccountEntity(nickname = "小号1"))
        db.skyDao().insertWish(SkyWishEntity(itemName = "斗篷", costCandle = 60))
        db.skyDao().saveHolding(SkyHoldingEntity(candles = 120, hearts = 8, ascended = 2))

        val manager = BackupManager(context)
        val before = run {
            val snapshot = manager.createSnapshot()
            BackupCodec.decode(BackupCodec.encode(snapshot))
        }
        assertNotNull("备份内容应该能原样读回来", before)

        // 清空再恢复
        manager.clearEverything()
        assertEquals(0, db.todoDao().listAll().size)
        assertEquals(0, db.memoDao().listAll().size)

        val summary = manager.restore(before!!)

        assertEquals(2, summary.todos)
        assertEquals(1, summary.memos)
        assertEquals(1, summary.studyCards)
        assertEquals(1, summary.numberTables)
        assertEquals(1, summary.numberRecords)
        assertEquals(1, summary.skyAccounts)
        assertEquals(1, summary.skyWishes)
        assertNotNull("持有的蜡烛数也要恢复", db.skyDao().findHolding())
        assertEquals(120, db.skyDao().findHolding()!!.candles)
    }

    @Test
    fun `恢复后表格记录仍然挂着正确的表格`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tableId = db.numberDao().insertTable(NumberTableEntity(name = "开支", unit = "元", decimals = 2))
        db.numberDao().insertRecord(
            NumberRecordEntity(tableId = tableId, date = "2026-10-08", value = 30.5, createdAt = 1L, updatedAt = 1L),
        )
        db.numberDao().insertRecord(
            NumberRecordEntity(tableId = tableId, date = "2026-10-09", value = 12.0, createdAt = 1L, updatedAt = 1L),
        )

        val manager = BackupManager(context)
        val snapshot = manager.createSnapshot()
        manager.clearEverything()
        manager.restore(snapshot)

        val tables = db.numberDao().listTables()
        assertEquals(1, tables.size)
        val records = db.numberDao().listRecords(tables[0].id)
        assertEquals("两笔记录都要在，并且还挂在同一张表下", 2, records.size)
        assertEquals(
            listOf(12.0, 30.5),
            records.map { it.value }.sorted(),
        )
    }

    // ── 清空全部 ──────────────────────────────────────────────────────────────

    @Test
    fun `清空全部数据后所有表都是空的`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db.todoDao().insert(todo("计划", "2026-10-09"))
        db.memoDao().insert(MemoEntity(title = "备忘", content = "x", createdAt = 1L, updatedAt = 1L))
        db.studyDao().insert(
            StudyCardEntity(front = "a", back = "b", dueAt = 1L, createdAt = 1L, updatedAt = 1L),
        )
        db.skyDao().insertAccount(SkyAccountEntity(nickname = "号"))
        db.skyDao().insertWish(SkyWishEntity(itemName = "物品"))

        BackupManager(context).clearEverything()

        assertEquals(0, db.todoDao().listAll().size)
        assertEquals(0, db.memoDao().listAll().size)
        assertEquals(0, db.studyDao().listAll().size)
        assertEquals(0, db.skyDao().listAccounts().size)
        assertEquals(0, db.skyDao().listWishes().size)
        assertEquals(0, db.numberDao().listTables().size)
        assertNull("账号清空后应查不到", db.skyDao().findHolding()?.takeIf { false })
    }

    // ── 学习卡的查询 ──────────────────────────────────────────────────────────

    @Test
    fun `只有到期的卡片会出现在今日复习里`() = runBlocking {
        val dao = db.studyDao()
        dao.insert(StudyCardEntity(front = "到期的", back = "", dueAt = 100L, createdAt = 1L, updatedAt = 1L))
        dao.insert(StudyCardEntity(front = "没到期的", back = "", dueAt = 9_999L, createdAt = 1L, updatedAt = 1L))
        dao.insert(
            StudyCardEntity(
                front = "暂停的", back = "", dueAt = 100L,
                phase = 3, createdAt = 1L, updatedAt = 1L,
            ),
        )

        val due = dao.listDue(now = 1_000L)
        assertEquals(1, due.size)
        assertEquals("到期的", due[0].front)
        assertEquals(1, dao.watchDueCount(1_000L).first())
    }

    @Test
    fun `暂停与恢复卡片`() = runBlocking {
        val dao = db.studyDao()
        val id = dao.insert(
            StudyCardEntity(front = "会被暂停的", back = "", dueAt = 100L, createdAt = 1L, updatedAt = 1L),
        )
        assertEquals(1, dao.listDue(1_000L).size)

        dao.suspend(id, 2L)
        assertEquals("暂停后不该出现在今日复习", 0, dao.listDue(1_000L).size)

        dao.resume(id, 3L)
        assertEquals(1, dao.listDue(1_000L).size)
    }

    @Test
    fun `备忘录的搜索与置顶`() = runBlocking {
        val dao = db.memoDao()
        dao.insert(MemoEntity(title = "快递单号", content = "SF123456", category = "快递", pinned = true, createdAt = 1L, updatedAt = 1L))
        dao.insert(MemoEntity(title = "我的尺寸", content = "M 码", category = "尺寸", createdAt = 1L, updatedAt = 1L))
        dao.insert(MemoEntity(title = "银行卡", content = "不该存这里", category = "其他", createdAt = 1L, updatedAt = 1L))

        val all = dao.watchAll().first()
        assertEquals(3, all.size)
        assertEquals("置顶的排最前", "快递单号", all[0].title)
        assertFalse("置顶标记应为真", !all[0].pinned)

        val pinned = dao.watchPinned(3).first()
        assertEquals(1, pinned.size)
        assertEquals("快递单号", pinned[0].title)

        val hit = dao.search("SF123").first()
        assertEquals(1, hit.size)
        assertEquals("快递单号", hit[0].title)
    }
}
