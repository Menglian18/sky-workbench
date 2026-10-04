package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.data.db.SkyTravelingSpiritEntity
import org.junit.Assert.assertEquals
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
}
