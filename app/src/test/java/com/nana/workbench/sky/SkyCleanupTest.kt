package com.nana.workbench.sky

import com.nana.workbench.data.db.SkySeasonEntity
import com.nana.workbench.data.db.SkyTravelingSpiritEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 改进 · D1（清空行为）对应的测试。
 *
 * 重点：**正在进行的复刻绝不能被清掉** —— 这正是用户报的问题。
 */
class SkyCleanupTest {

    private val today = "2026-10-09"

    private fun spirit(
        id: Long,
        endDate: String,
        manuallyEdited: Boolean = false,
        name: String = "先祖$id",
    ) = SkyTravelingSpiritEntity(
        id = id,
        spiritName = name,
        startDate = "2026-01-01",
        endDate = endDate,
        fetchedAt = 1L,
        manuallyEdited = manuallyEdited,
    )

    private fun season(id: Long, endDate: String, manuallyEdited: Boolean = false) = SkySeasonEntity(
        id = id,
        name = "季节$id",
        startDate = "2026-01-01",
        endDate = endDate,
        fetchedAt = 1L,
        manuallyEdited = manuallyEdited,
    )

    @Test
    fun `正在进行的复刻不算过期`() {
        assertFalse(SkyCleanup.isExpired("2026-10-20", today))
        assertFalse(SkyCleanup.isExpired(today, today))
    }

    @Test
    fun `刚刚结束的还留着_给别人回看`() {
        assertFalse("昨天刚结束不该清", SkyCleanup.isExpired("2026-10-08", today))
        assertFalse("7 天前结束的还在保留期内", SkyCleanup.isExpired("2026-10-02", today))
    }

    @Test
    fun `结束很久的才算过期`() {
        assertTrue(SkyCleanup.isExpired("2026-10-01", today))
        assertTrue(SkyCleanup.isExpired("2025-01-01", today))
    }

    @Test
    fun `保留天数可以调`() {
        assertFalse(SkyCleanup.isExpired("2026-10-02", today, keepDays = 30))
        assertTrue(SkyCleanup.isExpired("2026-08-01", today, keepDays = 30))
    }

    @Test
    fun `日期读不懂的一律保留_拿不准就不删`() {
        assertFalse(SkyCleanup.isExpired("", today))
        assertFalse(SkyCleanup.isExpired("不是日期", today))
        assertFalse(SkyCleanup.isExpired("2026-13-45", today))
    }

    @Test
    fun `今天的日期读不懂时也不删`() {
        assertFalse(SkyCleanup.isExpired("2026-10-01", ""))
    }

    @Test
    fun `挑出来的只有过期的那些`() {
        val items = listOf(
            spirit(1, "2026-10-20"),
            spirit(2, "2026-09-01"),
            spirit(3, "2026-10-08"),
        )
        assertEquals(listOf(2L), SkyCleanup.expiredSpirits(items, today).map { it.id })
    }

    @Test
    fun `手动改过的一条都不清`() {
        val items = listOf(
            spirit(1, "2020-01-01", manuallyEdited = true),
            spirit(2, "2020-01-01"),
        )
        assertEquals(listOf(2L), SkyCleanup.expiredSpirits(items, today).map { it.id })
    }

    @Test
    fun `季节用同一套规则`() {
        val seasons = listOf(
            season(1, "2026-10-30"),
            season(2, "2026-01-01"),
            season(3, "2025-01-01", manuallyEdited = true),
        )
        assertEquals(listOf(2L), SkyCleanup.expiredSeasons(seasons, today).map { it.id })
    }

    @Test
    fun `空清单不报错`() {
        assertEquals(0, SkyCleanup.expiredSpirits(emptyList(), today).size)
        assertEquals(0, SkyCleanup.expiredSeasons(emptyList(), today).size)
    }
}
