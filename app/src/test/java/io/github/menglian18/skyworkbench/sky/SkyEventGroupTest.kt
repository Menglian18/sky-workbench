package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.3.0 新增：活动分成"进行中 / 即将开始 / 已结束"三堆。
 */
class SkyEventGroupTest {

    private val today = "2026-10-09"

    private fun event(name: String, start: String, end: String, id: Long = 0) = SkyEventEntity(
        id = id,
        name = name,
        startDate = start,
        endDate = end,
        fetchedAt = 0L,
    )

    @Test
    fun `分三堆`() {
        val buckets = SkyEventGroup.split(
            listOf(
                event("进行中", "2026-10-02", "2026-10-18", 1),
                event("还没开始", "2026-10-20", "2026-10-30", 2),
                event("已经结束", "2026-09-01", "2026-09-20", 3),
            ),
            today,
        )
        assertEquals(listOf("进行中"), buckets.ongoing.map { it.name })
        assertEquals(listOf("还没开始"), buckets.upcoming.map { it.name })
        assertEquals(listOf("已经结束"), buckets.past.map { it.name })
    }

    @Test
    fun `结束时间未公布的算进行中`() {
        val buckets = SkyEventGroup.split(listOf(event("长期活动", "2026-09-01", "", 1)), today)
        assertEquals(1, buckets.ongoing.size)
        assertEquals(0, buckets.past.size)
    }

    @Test
    fun `边界_今天开始和今天结束都算进行中`() {
        val buckets = SkyEventGroup.split(
            listOf(
                event("今天开始", today, "2026-10-20", 1),
                event("今天结束", "2026-10-01", today, 2),
            ),
            today,
        )
        assertEquals(2, buckets.ongoing.size)
    }

    @Test
    fun `进行中的最近的排前面`() {
        val buckets = SkyEventGroup.split(
            listOf(
                event("早开始的", "2026-09-01", "2026-10-20", 1),
                event("晚开始的", "2026-10-05", "2026-10-20", 2),
            ),
            today,
        )
        assertEquals(listOf("晚开始的", "早开始的"), buckets.ongoing.map { it.name })
    }

    @Test
    fun `已结束的刚结束的排前面`() {
        val buckets = SkyEventGroup.split(
            listOf(
                event("很久以前", "2026-01-01", "2026-01-10", 1),
                event("上周", "2026-09-20", "2026-09-30", 2),
            ),
            today,
        )
        assertEquals(listOf("上周", "很久以前"), buckets.past.map { it.name })
    }

    @Test
    fun `空列表不报错`() {
        assertTrue(SkyEventGroup.split(emptyList(), today).isEmpty)
    }


    @Test
    fun `占位结束时间的活动算进行中`() {
        val buckets = SkyEventGroup.split(listOf(event("某活动", "2026-09-01", "2099-12-31", 1)), today)
        assertEquals(1, buckets.ongoing.size)
        assertEquals(0, buckets.past.size)
    }
}
