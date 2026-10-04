package io.github.menglian18.skyworkbench.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3 阶段的测试之一：数字表格的"比上次涨跌"和"近 30 天趋势"。
 */
class NumberStatsTest {

    private val today = "2026-10-09"

    @Test
    fun `没有记录时不报错`() {
        val stats = NumberStatsCalculator.compute(emptyList(), today)
        assertNull(stats.latest)
        assertNull(stats.previous)
        assertNull(stats.delta)
        assertTrue(stats.trend.isEmpty())
        assertFalse(stats.hasTrend)
        assertEquals(0, stats.totalCount)
    }

    @Test
    fun `只有一笔时没有涨跌`() {
        val stats = NumberStatsCalculator.compute(listOf(NumberPoint("2026-10-09", 62.4)), today)
        assertEquals(62.4, stats.latest!!.value, 1e-9)
        assertNull("只有一笔时不该有涨跌", stats.delta)
        assertFalse(stats.hasTrend)
    }

    @Test
    fun `两笔时算出跌了多少`() {
        val stats = NumberStatsCalculator.compute(
            listOf(NumberPoint("2026-10-08", 62.7), NumberPoint("2026-10-09", 62.4)),
            today,
        )
        assertEquals(62.4, stats.latest!!.value, 1e-9)
        assertEquals(62.7, stats.previous!!.value, 1e-9)
        assertEquals(-0.3, stats.delta!!, 1e-9)
    }

    @Test
    fun `两笔时算出涨了多少`() {
        val stats = NumberStatsCalculator.compute(
            listOf(NumberPoint("2026-10-08", 100.0), NumberPoint("2026-10-09", 128.5)),
            today,
        )
        assertEquals(28.5, stats.delta!!, 1e-9)
    }

    @Test
    fun `顺序打乱也能取到正确的最新一笔`() {
        val stats = NumberStatsCalculator.compute(
            listOf(
                NumberPoint("2026-10-01", 60.0),
                NumberPoint("2026-10-09", 62.4),
                NumberPoint("2026-10-05", 61.5),
            ),
            today,
        )
        assertEquals("2026-10-09", stats.latest!!.date)
        assertEquals("2026-10-05", stats.previous!!.date)
    }

    @Test
    fun `趋势只保留近三十天`() {
        val stats = NumberStatsCalculator.compute(
            listOf(
                NumberPoint("2026-08-01", 70.0),   // 太老，排除
                NumberPoint("2026-09-10", 65.0),   // 30 天窗口的第一天（10-09 往回 29 天）
                NumberPoint("2026-10-09", 62.4),
            ),
            today,
        )
        assertEquals(listOf("2026-09-10", "2026-10-09"), stats.trend.map { it.date })
        assertEquals("总数应包含窗口外的记录", 3, stats.totalCount)
    }

    @Test
    fun `趋势按时间从早到晚`() {
        val stats = NumberStatsCalculator.compute(
            listOf(
                NumberPoint("2026-10-09", 3.0),
                NumberPoint("2026-10-07", 1.0),
                NumberPoint("2026-10-08", 2.0),
            ),
            today,
        )
        assertEquals(listOf(1.0, 2.0, 3.0), stats.trend.map { it.value })
        assertTrue(stats.hasTrend)
    }

    @Test
    fun `可以自定义窗口天数`() {
        val records = listOf(
            NumberPoint("2026-10-01", 1.0),
            NumberPoint("2026-10-09", 2.0),
        )
        // 窗口 10 天：从 10-09 往回数 9 天 = 09-30，两条都落在窗口内
        assertEquals(2, NumberStatsCalculator.compute(records, today, trendDays = 10).trend.size)
        // 窗口 7 天：从 10-09 往回数 6 天 = 10-03，10-01 那条落在窗口外
        assertEquals(1, NumberStatsCalculator.compute(records, today, trendDays = 7).trend.size)
        // 窗口 3 天：只剩最新一条
        assertEquals(1, NumberStatsCalculator.compute(records, today, trendDays = 3).trend.size)
    }

    @Test
    fun `同一天多笔时取最后一条当最新`() {
        val stats = NumberStatsCalculator.compute(
            listOf(
                NumberPoint("2026-10-09", 62.0),
                NumberPoint("2026-10-09", 63.0),
            ),
            today,
        )
        assertEquals(63.0, stats.latest!!.value, 1e-9)
        assertEquals(1.0, stats.delta!!, 1e-9)
    }
}
