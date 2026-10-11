package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.data.db.SkyTravelingSpiritEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3/M4 阶段的测试：光遇倒计时（复刻进行中/下一次、季节剩余）。
 */
class SkyCountdownTest {

    private val today = "2026-10-09"

    private fun spirit(
        name: String,
        start: String,
        end: String,
        id: Long = 0,
    ) = SkyTravelingSpiritEntity(
        id = id,
        spiritName = name,
        startDate = start,
        endDate = end,
        fetchedAt = 0L,
    )

    @Test
    fun `还剩几天`() {
        assertEquals(3L, SkyCountdown.daysLeft("2026-10-12", today))
        assertEquals(0L, SkyCountdown.daysLeft(today, today))
        assertEquals(-2L, SkyCountdown.daysLeft("2026-10-07", today))
    }

    @Test
    fun `正在进行中的复刻会被挑出来`() {
        val picks = SkyCountdown.pick(
            listOf(spirit("采药人", "2026-10-08", "2026-10-14")),
            today,
        )
        assertEquals("采药人", picks.ongoing?.spiritName)
        assertEquals(5L, SkyCountdown.daysLeft(picks.ongoing!!.endDate, today))
        assertNull(picks.next)
    }

    @Test
    fun `结束了的不算进行中_而是已经过去`() {
        val picks = SkyCountdown.pick(
            listOf(spirit("旧先祖", "2026-09-20", "2026-09-30")),
            today,
        )
        assertNull(picks.ongoing)
        assertNull("已经过去的也不该算下一次", picks.next)
    }

    @Test
    fun `还没开始的算下一次`() {
        val picks = SkyCountdown.pick(
            listOf(spirit("下一个", "2026-10-20", "2026-10-26")),
            today,
        )
        assertNull(picks.ongoing)
        assertEquals("下一个", picks.next?.spiritName)
    }

    @Test
    fun `多条进行中时取开始最晚的`() {
        val picks = SkyCountdown.pick(
            listOf(
                spirit("老的", "2026-10-01", "2026-10-15", id = 1),
                spirit("新的", "2026-10-08", "2026-10-20", id = 2),
            ),
            today,
        )
        assertEquals("新的", picks.ongoing?.spiritName)
    }

    @Test
    fun `多条未来的取最近的`() {
        val picks = SkyCountdown.pick(
            listOf(
                spirit("远的", "2026-12-01", "2026-12-07", id = 1),
                spirit("近的", "2026-10-20", "2026-10-26", id = 2),
            ),
            today,
        )
        assertEquals("近的", picks.next?.spiritName)
    }

    @Test
    fun `边界_今天正好开始算进行中`() {
        val picks = SkyCountdown.pick(listOf(spirit("今天开始", today, "2026-10-15")), today)
        assertEquals("今天开始", picks.ongoing?.spiritName)
    }

    @Test
    fun `边界_今天正好结束也算进行中`() {
        val picks = SkyCountdown.pick(listOf(spirit("今天最后一天", "2026-10-01", today)), today)
        assertEquals("今天最后一天", picks.ongoing?.spiritName)
        assertEquals(0L, SkyCountdown.daysLeft(picks.ongoing!!.endDate, today))
    }

    @Test
    fun `空列表不报错`() {
        val picks = SkyCountdown.pick(emptyList(), today)
        assertNull(picks.ongoing)
        assertNull(picks.next)
        assertEquals(0, picks.ongoingCount)
    }

    @Test
    fun `集体复刻_同一批会数出好几位`() {
        // 真实数据里，一次集体复刻是同一天来好几位先祖
        val picks = SkyCountdown.pick(
            listOf(
                spirit("采药人", "2026-09-24", "2026-10-14", id = 1),
                spirit("猎手", "2026-09-24", "2026-10-14", id = 2),
                spirit("公主", "2026-09-24", "2026-10-14", id = 3),
                spirit("领主", "2026-09-24", "2026-10-14", id = 4),
            ),
            today,
        )
        assertEquals(4, picks.ongoingCount)
        assertTrue("同期不止一位就该算集体复刻", picks.ongoingIsCollective)
    }

    @Test
    fun `单人来复刻时只算一位`() {
        val picks = SkyCountdown.pick(
            listOf(spirit("放松日浴者", "2026-10-08", "2026-10-12")),
            today,
        )
        assertEquals(1, picks.ongoingCount)
        assertTrue(!picks.ongoingIsCollective)
    }

    @Test
    fun `下一次也会数出同期几位`() {
        val picks = SkyCountdown.pick(
            listOf(
                spirit("甲", "2026-10-20", "2026-10-26", id = 1),
                spirit("乙", "2026-10-20", "2026-10-26", id = 2),
            ),
            today,
        )
        assertEquals(2, picks.nextCount)
        assertTrue(picks.nextIsCollective)
    }

    @Test
    fun `当前季节取结束日还没到的里面开始最晚的`() {
        val seasons = listOf(
            SkySeasonEntity(id = 1, name = "旧季", startDate = "2026-06-01", endDate = "2026-08-01", fetchedAt = 0L),
            SkySeasonEntity(id = 2, name = "当前季", startDate = "2026-09-01", endDate = "2026-11-01", fetchedAt = 0L),
        )
        val current = SkyCountdown.currentSeason(seasons, today)
        assertEquals("当前季", current?.name)
        assertEquals(23L, SkyCountdown.seasonDaysLeft(current, today))
    }

    @Test
    fun `季节都结束了就返回空`() {
        val seasons = listOf(
            SkySeasonEntity(id = 1, name = "旧季", startDate = "2026-06-01", endDate = "2026-08-01", fetchedAt = 0L),
        )
        assertNull(SkyCountdown.currentSeason(seasons, today))
        assertNull(SkyCountdown.seasonDaysLeft(null, today))
    }


    // ── 1.3.0：集体复刻要每位先祖各自的照片与兑换树 ──────────────────────────────

    @Test
    fun `集体复刻会带出同期全部先祖_供界面一位一位显示`() {
        val batch = listOf(
            spirit("采药人", "2026-09-24", "2026-10-14", id = 1),
            spirit("猎手", "2026-09-24", "2026-10-14", id = 2),
            spirit("公主", "2026-09-24", "2026-10-14", id = 3),
            spirit("领主", "2026-09-24", "2026-10-14", id = 4),
        )
        val picks = SkyCountdown.pick(batch, today)
        assertEquals(4, picks.ongoingList.size)
        assertEquals(
            setOf("采药人", "猎手", "公主", "领主"),
            picks.ongoingList.map { it.spiritName }.toSet(),
        )
    }

    @Test
    fun `单人复刻时列表里只有他自己`() {
        val picks = SkyCountdown.pick(listOf(spirit("放松日浴者", "2026-10-08", "2026-10-12")), today)
        assertEquals(1, picks.ongoingList.size)
        assertEquals("放松日浴者", picks.ongoingList[0].spiritName)
    }

    @Test
    fun `下一次是集体复刻时也带出全部名单`() {
        val picks = SkyCountdown.pick(
            listOf(
                spirit("甲", "2026-10-20", "2026-10-26", id = 1),
                spirit("乙", "2026-10-20", "2026-10-26", id = 2),
            ),
            today,
        )
        assertEquals(2, picks.nextList.size)
        assertEquals(setOf("甲", "乙"), picks.nextList.map { it.spiritName }.toSet())
    }

    @Test
    fun `没有复刻时列表是空的_界面不会崩`() {
        val picks = SkyCountdown.pick(emptyList(), today)
        assertTrue(picks.ongoingList.isEmpty())
        assertTrue(picks.nextList.isEmpty())
    }

    // ── 1.3.0：季节"时间不对"的三条规则 ────────────────────────────────────────

    @Test
    fun `季节_结束时间官方未公布时不显示天数`() {
        val season = SkySeasonEntity(id = 1, name = "珍珠季", startDate = "2026-10-01", endDate = "", fetchedAt = 0L)
        val status = SkyCountdown.seasonStatus(season, today)
        assertFalse(status.notStarted)
        assertNull("未公布结束时间就不该算天数", status.daysLeft)
        assertFalse(status.hasKnownEnd)
    }

    @Test
    fun `季节_还没开始时说还有几天开始`() {
        val seasons = listOf(
            SkySeasonEntity(id = 1, name = "致梵高季", startDate = "2026-07-17", endDate = "2026-10-02", fetchedAt = 0L),
            SkySeasonEntity(id = 2, name = "珍珠季", startDate = "2026-10-22", endDate = "", fetchedAt = 0L),
        )
        val status = SkyCountdown.seasonStatus(SkyCountdown.currentSeason(seasons, today), today)
        assertEquals("珍珠季", status.season?.name)
        assertTrue(status.notStarted)
        assertEquals(13L, status.daysUntilStart)
        assertNull(status.daysLeft)
    }

    @Test
    fun `季节_回归测试_两个季节中间不会显示成还剩三万多天`() {
        // 真实数据：致梵高季 10-02 结束、珍珠季 10-22 开始（结束日是 WIKI 占位值）。
        // 今天 10-09 正好夹在中间 —— 以前会把珍珠季当成当季，显示"还剩 3 万多天"。
        val seasons = listOf(
            SkySeasonEntity(id = 1, name = "致梵高季", startDate = "2026-07-17", endDate = "2026-10-02", fetchedAt = 0L),
            SkySeasonEntity(id = 2, name = "珍珠季", startDate = "2026-10-22", endDate = "", fetchedAt = 0L),
        )
        val status = SkyCountdown.seasonStatus(SkyCountdown.currentSeason(seasons, today), today)
        assertNull("未公布结束时间就不该算天数", status.daysLeft)
        assertTrue("应该只是'还有几天开始'", (status.daysUntilStart ?: 0L) < 30L)
    }

    @Test
    fun `季节_进行中且结束时间已公布时照旧显示还剩几天`() {
        val season = SkySeasonEntity(id = 1, name = "当季", startDate = "2026-09-01", endDate = "2026-10-20", fetchedAt = 0L)
        val status = SkyCountdown.seasonStatus(season, today)
        assertFalse(status.notStarted)
        assertEquals(11L, status.daysLeft)
        assertNull(status.daysUntilStart)
        assertTrue(status.hasKnownEnd)
    }


    @Test
    fun `季节_数据库里还存着旧的占位结束时间_也不会显示成三万多天`() {
        // 装过旧版的人，数据库里存的就是 WIKI 那个占位值 2099-12-31
        // （升级后一小时内还没重新抓，所以界面必须自己也能挡住）
        val season = SkySeasonEntity(
            id = 1, name = "珍珠季", startDate = "2026-10-22",
            endDate = "2099-12-31", fetchedAt = 0L,
        )
        val status = SkyCountdown.seasonStatus(season, today)
        assertTrue(status.notStarted)
        assertNull(status.daysLeft)
        assertEquals("官方未公布", status.endLabel)
    }

    @Test
    fun `季节_已经开始但结束日是占位值_算进行中且未公布`() {
        val season = SkySeasonEntity(
            id = 1, name = "某季", startDate = "2026-10-01",
            endDate = "2099-12-31", fetchedAt = 0L,
        )
        val status = SkyCountdown.seasonStatus(season, today)
        assertFalse(status.notStarted)
        assertNull(status.daysLeft)
        assertEquals("官方未公布", status.endLabel)
    }


    // ── 1.3.1：算天数绝不抛异常（闪退就是这么来的）────────────────────────────

    @Test
    fun `结束时间是空串时_算天数不抛异常而是 0`() {
        assertEquals(0L, SkyCountdown.daysLeft("", today))
        assertNull(SkyCountdown.daysLeftOrNull("", today))
    }

    @Test
    fun `日期读不懂时_算天数也不抛异常`() {
        assertEquals(0L, SkyCountdown.daysLeft("看不懂", today))
        assertEquals(0L, SkyCountdown.daysLeft("2026-13-45", today))
        assertNull(SkyCountdown.daysLeftOrNull("2026-13-45", today))
        assertNull(SkyCountdown.daysLeftOrNull("2026-10-20", "不是今天"))
    }

    @Test
    fun `回归_首页那句当季剩余在结束时间未公布时不再崩`() {
        // 闪退的最小场景：库里是这个季节（结束时间未公布），首页要显示还剩几天
        val season = SkySeasonEntity(
            id = 1, name = "珍珠季", startDate = "2026-10-22",
            endDate = "", fetchedAt = 0L,
        )
        val status = SkyCountdown.seasonStatus(season, today)
        assertEquals(13L, status.daysUntilStart)
        assertNull(status.daysLeft)
        assertEquals("官方未公布", status.endLabel)
        // 这一句就是首页原来崩掉的那行
        assertEquals(0L, SkyCountdown.daysLeft(season.endDate, today))
    }
}
