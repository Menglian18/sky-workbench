package io.github.menglian18.skyworkbench.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3 阶段的测试之一："没做完自动跟到明天"（PRD §5.1 / 验收 A4）。
 */
class TodoRolloverTest {

    private val today = "2026-10-09"

    @Test
    fun `昨天没做完的会挪到今天`() {
        val updates = TodoRollover.decide(
            listOf(RolloverItem(id = 1, dueDate = "2026-10-08", postponedCount = 0)),
            today,
        )
        assertEquals(1, updates.size)
        assertEquals("2026-10-09", updates[0].dueDate)
        assertEquals(1, updates[0].postponedCount)
    }

    @Test
    fun `今天的不动`() {
        val updates = TodoRollover.decide(
            listOf(RolloverItem(id = 1, dueDate = today, postponedCount = 0)),
            today,
        )
        assertTrue("今天的不该被改", updates.isEmpty())
    }

    @Test
    fun `以后的不动`() {
        val updates = TodoRollover.decide(
            listOf(RolloverItem(id = 1, dueDate = "2026-10-20", postponedCount = 0)),
            today,
        )
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `隔了三天没打开_一次累加三天`() {
        val updates = TodoRollover.decide(
            listOf(RolloverItem(id = 7, dueDate = "2026-10-06", postponedCount = 2)),
            today,
        )
        assertEquals(1, updates.size)
        assertEquals("2026-10-09", updates[0].dueDate)
        assertEquals("天数应按实际过了几天累加", 5, updates[0].postponedCount)
    }

    @Test
    fun `已经在昨天挪过一次_再隔一天只再加一`() {
        val first = TodoRollover.decide(
            listOf(RolloverItem(id = 1, dueDate = "2026-10-08", postponedCount = 0)),
            today,
        )
        assertEquals(1, first[0].postponedCount)

        val second = TodoRollover.decide(
            listOf(RolloverItem(id = 1, dueDate = first[0].dueDate, postponedCount = first[0].postponedCount)),
            "2026-10-10",
        )
        assertEquals("2026-10-10", second[0].dueDate)
        // 这个字段是"累计被顺延了几天"，所以在原有 1 天的基础上再加 1 天，累计为 2
        assertEquals("在原有 1 天基础上再加 1 天", 2, second[0].postponedCount)
    }

    @Test
    fun `跨月与跨年都对`() {
        assertEquals(
            "2026-10-01",
            TodoRollover.decide(listOf(RolloverItem(1, "2026-09-30", 0)), "2026-10-01")[0].dueDate,
        )
        assertEquals(
            "2026-01-01",
            TodoRollover.decide(listOf(RolloverItem(1, "2025-12-31", 0)), "2026-01-01")[0].dueDate,
        )
    }

    @Test
    fun `混合清单只有该挪的会被挪`() {
        val updates = TodoRollover.decide(
            listOf(
                RolloverItem(1, "2026-10-07", 0),
                RolloverItem(2, today, 3),
                RolloverItem(3, "2026-10-20", 0),
                RolloverItem(4, "2026-10-08", 1),
            ),
            today,
        )
        assertEquals(listOf(1L, 4L), updates.map { it.id })
        assertEquals(listOf(2, 2), updates.map { it.postponedCount })
    }

    @Test
    fun `空清单不报错`() {
        assertTrue(TodoRollover.decide(emptyList(), today).isEmpty())
    }
}
