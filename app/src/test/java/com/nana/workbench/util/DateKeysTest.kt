package com.nana.workbench.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * M3 阶段的测试之一：日期键的互转与"今天"的判定（必须与凌晨 4 点日界一致）。
 */
class DateKeysTest {

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun `互转不丢信息`() {
        val date = LocalDate.of(2026, 10, 9)
        assertEquals("2026-10-09", DateKeys.of(date))
        assertEquals(date, DateKeys.parse("2026-10-09"))
    }

    @Test
    fun `个位数月份日期会补零`() {
        assertEquals("2026-01-05", DateKeys.of(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `字符串排序等于日期排序`() {
        val keys = listOf("2026-10-09", "2026-01-05", "2026-10-08").sorted()
        assertEquals(listOf("2026-01-05", "2026-10-08", "2026-10-09"), keys)
    }

    @Test
    fun `凌晨四点前算昨天`() {
        assertEquals("2026-10-08", DateKeys.todayKey(millisOf(2026, 10, 9, 3, 59)))
        assertEquals("2026-10-08", DateKeys.todayKey(millisOf(2026, 10, 9, 0, 0)))
    }

    @Test
    fun `凌晨四点起算今天`() {
        assertEquals("2026-10-09", DateKeys.todayKey(millisOf(2026, 10, 9, 4, 0)))
        assertEquals("2026-10-09", DateKeys.todayKey(millisOf(2026, 10, 9, 23, 59)))
    }

    @Test
    fun `跨年也算对`() {
        assertEquals("2025-12-31", DateKeys.todayKey(millisOf(2026, 1, 1, 2, 0)))
        assertEquals("2026-01-01", DateKeys.todayKey(millisOf(2026, 1, 1, 4, 0)))
    }
}
