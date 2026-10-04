package io.github.menglian18.skyworkbench.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M5 阶段的测试之一：休息铃声的倒计时显示。
 */
class TimerFormatTest {

    @Test
    fun `分秒格式`() {
        assertEquals("0:00", TimerFormat.remaining(0))
        assertEquals("0:09", TimerFormat.remaining(9))
        assertEquals("1:00", TimerFormat.remaining(60))
        assertEquals("5:00", TimerFormat.remaining(300))
        assertEquals("10:00", TimerFormat.remaining(600))
        assertEquals("15:30", TimerFormat.remaining(930))
        assertEquals("59:59", TimerFormat.remaining(3599))
    }

    @Test
    fun `超过一小时显示小时`() {
        assertEquals("1:00:00", TimerFormat.remaining(3600))
        assertEquals("1:01:05", TimerFormat.remaining(3665))
        assertEquals("2:00:00", TimerFormat.remaining(7200))
    }

    @Test
    fun `负数按零处理`() {
        assertEquals("0:00", TimerFormat.remaining(-10))
    }

    @Test
    fun `进度从一到零`() {
        assertEquals(1f, TimerFormat.progress(300, 300), 1e-6f)
        assertEquals(0.5f, TimerFormat.progress(300, 150), 1e-6f)
        assertEquals(0f, TimerFormat.progress(300, 0), 1e-6f)
    }

    @Test
    fun `总时间为零时进度为零_不除零`() {
        assertEquals(0f, TimerFormat.progress(0, 0), 1e-6f)
        assertEquals(0f, TimerFormat.progress(0, 100), 1e-6f)
    }

    @Test
    fun `进度被夹在零到一之间`() {
        assertEquals(1f, TimerFormat.progress(300, 999), 1e-6f)
        assertEquals(0f, TimerFormat.progress(300, -50), 1e-6f)
    }
}
