package com.nana.workbench.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * M2 阶段的测试之一：凌晨 4 点日界（PRD 附录 C 第 2 条 / 《开发计划》§4.2）。
 * 这是最容易算错、且错了很难被发现的地方，所以边界值全部钉死。
 */
class LogicalDateTest {

    @Test
    fun `凌晨四点前算前一天`() {
        assertEquals(LocalDate.of(2026, 10, 8), LogicalDate.of(LocalDateTime.of(2026, 10, 9, 0, 0)))
        assertEquals(LocalDate.of(2026, 10, 8), LogicalDate.of(LocalDateTime.of(2026, 10, 9, 3, 59, 59)))
    }

    @Test
    fun `正好四点算当天`() {
        assertEquals(LocalDate.of(2026, 10, 9), LogicalDate.of(LocalDateTime.of(2026, 10, 9, 4, 0)))
        assertEquals(LocalDate.of(2026, 10, 9), LogicalDate.of(LocalDateTime.of(2026, 10, 9, 4, 0, 1)))
    }

    @Test
    fun `白天到午夜都算当天`() {
        assertEquals(LocalDate.of(2026, 10, 9), LogicalDate.of(LocalDateTime.of(2026, 10, 9, 12, 0)))
        assertEquals(LocalDate.of(2026, 10, 9), LogicalDate.of(LocalDateTime.of(2026, 10, 9, 23, 59, 59)))
    }

    @Test
    fun `跨月时也对`() {
        assertEquals(LocalDate.of(2026, 9, 30), LogicalDate.of(LocalDateTime.of(2026, 10, 1, 2, 0)))
        assertEquals(LocalDate.of(2026, 10, 1), LogicalDate.of(LocalDateTime.of(2026, 10, 1, 4, 0)))
    }

    @Test
    fun `跨年时也对`() {
        assertEquals(LocalDate.of(2025, 12, 31), LogicalDate.of(LocalDateTime.of(2026, 1, 1, 1, 30)))
        assertEquals(LocalDate.of(2026, 1, 1), LogicalDate.of(LocalDateTime.of(2026, 1, 1, 4, 0)))
    }

    @Test
    fun `一天的起点是四点整`() {
        assertEquals(LocalDateTime.of(2026, 10, 9, 4, 0), LogicalDate.startOf(LocalDate.of(2026, 10, 9)))
    }

    @Test
    fun `取某时刻所属那天的起点`() {
        assertEquals(
            LocalDateTime.of(2026, 10, 8, 4, 0),
            LogicalDate.startOfDayOf(LocalDateTime.of(2026, 10, 9, 2, 30)),
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 9, 4, 0),
            LogicalDate.startOfDayOf(LocalDateTime.of(2026, 10, 9, 22, 30)),
        )
    }

    @Test
    fun `深夜连续两点到五点会跨到新的一天`() {
        // 熬夜场景：2:00 记的事算昨天，5:00 记的事算今天
        val at2 = LogicalDate.of(LocalDateTime.of(2026, 10, 9, 2, 0))
        val at5 = LogicalDate.of(LocalDateTime.of(2026, 10, 9, 5, 0))
        assertEquals(LocalDate.of(2026, 10, 8), at2)
        assertEquals(LocalDate.of(2026, 10, 9), at5)
        assertEquals(1L, at5.toEpochDay() - at2.toEpochDay())
    }
}
