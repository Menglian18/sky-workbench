package io.github.menglian18.skyworkbench.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 改进 · 第 2 条（时间改成"日期 / 时 / 分"）对应的测试。
 *
 * 重点保证三件事：
 *  1. 时和分永远补成两位（"8:5" 要变成 "08:05"）；
 *  2. 库里读到脏值不能崩，也不能写进一条查不出来的记录；
 *  3. 不可能出现 25 点、70 分这种越界时间。
 */
class TodoScheduleTest {

    @Test
    fun `没选时间就是空的`() {
        val schedule = TodoSchedule(dateKey = "2026-10-09")
        assertNull(schedule.timePoint)
        assertFalse(schedule.hasTime)
    }

    @Test
    fun `时和分会补成两位`() {
        assertEquals("08:30", TodoSchedule("2026-10-09", 8, 30).timePoint)
        assertEquals("09:05", TodoSchedule("2026-10-09", 9, 5).timePoint)
        assertEquals("00:00", TodoSchedule("2026-10-09", 0, 0).timePoint)
        assertEquals("23:59", TodoSchedule("2026-10-09", 23, 59).timePoint)
    }

    @Test
    fun `设置时间时越界会被夹回合法范围`() {
        assertEquals("23:59", TodoSchedule("2026-10-09").withTime(25, 70).timePoint)
        assertEquals("00:00", TodoSchedule("2026-10-09").withTime(-3, -9).timePoint)
    }

    @Test
    fun `可以清掉时间`() {
        val withTime = TodoSchedule("2026-10-09", 8, 30)
        assertTrue(withTime.hasTime)
        assertFalse(withTime.clearTime().hasTime)
        assertNull(withTime.clearTime().timePoint)
    }

    @Test
    fun `改日期不影响已选的时间`() {
        val moved = TodoSchedule("2026-10-09", 8, 30).withDate("2026-10-20")
        assertEquals("2026-10-20", moved.dateKey)
        assertEquals("08:30", moved.timePoint)
    }

    @Test
    fun `从库里读回正常的时间`() {
        val schedule = TodoSchedule.from("2026-10-09", "08:30")
        assertEquals(8, schedule.hour)
        assertEquals(30, schedule.minute)
        assertEquals("08:30", schedule.timePoint)
    }

    @Test
    fun `仓库里的单位数时间也能读回来`() {
        assertEquals("08:05", TodoSchedule.from("2026-10-09", "8:5").timePoint)
    }

    @Test
    fun `空的或只有空格的时间当作没设`() {
        assertNull(TodoSchedule.from("2026-10-09", null).timePoint)
        assertNull(TodoSchedule.from("2026-10-09", "").timePoint)
        assertNull(TodoSchedule.from("2026-10-09", "   ").timePoint)
    }

    @Test
    fun `乱七八糟的时间不会崩_当成没设`() {
        listOf("abc", "12", "12:", ":", "25:00", "12:99", "-1:30", "8点30").forEach { junk ->
            val schedule = TodoSchedule.from("2026-10-09", junk)
            assertNull("这个值应该被当成没设时间：$junk", schedule.timePoint)
            assertEquals("日期不该被改", "2026-10-09", schedule.dateKey)
        }
    }

    @Test
    fun `日期不合法时退回今天`() {
        assertEquals("2026-10-09", TodoSchedule.safeDateKey(null, "2026-10-09"))
        assertEquals("2026-10-09", TodoSchedule.safeDateKey("", "2026-10-09"))
        assertEquals("2026-10-09", TodoSchedule.safeDateKey("   ", "2026-10-09"))
        assertEquals("2026-10-09", TodoSchedule.safeDateKey("2026-13-45", "2026-10-09"))
        assertEquals("2026-10-09", TodoSchedule.safeDateKey("2026-02-30", "2026-10-09"))
        assertEquals("2026-10-09", TodoSchedule.safeDateKey("明天", "2026-10-09"))
    }

    @Test
    fun `合法日期会原样保留`() {
        assertEquals("2026-02-28", TodoSchedule.safeDateKey("2026-02-28", "2026-10-09"))
        assertEquals("2024-02-29", TodoSchedule.safeDateKey("2024-02-29", "2026-10-09"))
    }

    @Test
    fun `日期标签认得今天明天昨天`() {
        val today = "2026-10-09"
        assertEquals("今天 10月9日", dateLabel(today, today))
        assertEquals("明天 10月10日", dateLabel("2026-10-10", today))
        assertEquals("昨天 10月8日", dateLabel("2026-10-08", today))
    }

    @Test
    fun `更远的日期只显示月日`() {
        assertEquals("10月20日", dateLabel("2026-10-20", "2026-10-09"))
        assertEquals("11月1日", dateLabel("2026-11-01", "2026-10-09"))
        assertEquals("9月30日", dateLabel("2026-09-30", "2026-10-09"))
    }

    @Test
    fun `日期标签遇到坏值也不会崩`() {
        assertEquals("不是日期", dateLabel("不是日期", "2026-10-09"))
    }

    @Test
    fun `时的加减是循环的`() {
        assertEquals(10, shiftHour(9, forward = true))
        assertEquals(8, shiftHour(9, forward = false))
        assertEquals(0, shiftHour(23, forward = true))
        assertEquals(23, shiftHour(0, forward = false))
    }

    @Test
    fun `分的加减按五分鐘一档`() {
        assertEquals(35, shiftMinute(30, forward = true))
        assertEquals(25, shiftMinute(30, forward = false))
        assertEquals(5, shiftMinute(0, forward = true))
        assertEquals(55, shiftMinute(0, forward = false))
    }

    @Test
    fun `分到头会绕回另一端`() {
        // 55 分再往后 → 0 分；0 分再往前 → 55 分
        assertEquals(0, shiftMinute(55, forward = true))
        assertEquals(55, shiftMinute(0, forward = false))
    }

    @Test
    fun `分不在档位上时取相邻的档`() {
        // 编辑旧数据可能得到 37 分
        assertEquals(40, shiftMinute(37, forward = true))
        assertEquals(35, shiftMinute(37, forward = false))
        assertEquals(5, shiftMinute(1, forward = true))
        // 59 分不在档位上，往后绕回 0、往前取 55
        assertEquals(0, shiftMinute(59, forward = true))
        assertEquals(55, shiftMinute(59, forward = false))
    }

    @Test
    fun `每一档都在合法范围内`() {
        assertTrue(MINUTE_STEPS.all { it in 0..59 })
        assertEquals(12, MINUTE_STEPS.size)
        assertEquals(0, MINUTE_STEPS.first())
        assertEquals(55, MINUTE_STEPS.last())
    }

    @Test
    fun `精确到分的加减是一分钟一档`() {
        assertEquals(31, shiftMinuteExact(30, forward = true))
        assertEquals(29, shiftMinuteExact(30, forward = false))
        assertEquals(38, shiftMinuteExact(37, forward = true))
        assertEquals(36, shiftMinuteExact(37, forward = false))
    }

    @Test
    fun `精确到分时 0 和 59 会绕回`() {
        assertEquals(0, shiftMinuteExact(59, forward = true))
        assertEquals(59, shiftMinuteExact(0, forward = false))
    }

    @Test
    fun `精确到分时越界值会被夹回来`() {
        assertEquals(0, shiftMinuteExact(60, forward = true))
        assertEquals(59, shiftMinuteExact(-1, forward = false))
        // 999 先被夹到 59，再往前退一分钟 = 58
        assertEquals(58, shiftMinuteExact(999, forward = false))
        assertEquals(0, shiftMinuteExact(999, forward = true))
    }

    @Test
    fun `每一分钟都能选到`() {
        // 从 0 一直往后加，应该能把 60 个分钟值全走一遍（1.2 第 3 条要求精确到分）
        val seen = mutableSetOf<Int>()
        var minute = 0
        repeat(60) {
            seen += minute
            minute = shiftMinuteExact(minute, forward = true)
        }
        assertEquals(60, seen.size)
        assertEquals(0, minute)
    }
}
