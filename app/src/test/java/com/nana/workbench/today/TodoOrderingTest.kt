package com.nana.workbench.today

import com.nana.workbench.data.db.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M3 阶段的测试之一：今日计划的显示顺序。
 */
class TodoOrderingTest {

    private fun todo(
        id: Long,
        title: String,
        priority: Int = 0,
        timePoint: String? = null,
        sortOrder: Int = 0,
    ) = TodoEntity(
        id = id,
        title = title,
        priority = priority,
        timePoint = timePoint,
        done = false,
        originalDate = "2026-10-09",
        dueDate = "2026-10-09",
        sortOrder = sortOrder,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `优先级高的排前面`() {
        val sorted = TodoOrdering.sortForDisplay(
            listOf(
                todo(1, "普通"),
                todo(2, "紧急", priority = 2),
                todo(3, "重要", priority = 1),
            ),
        )
        assertEquals(listOf("紧急", "重要", "普通"), sorted.map { it.title })
    }

    @Test
    fun `有具体时间点的排在没时间的前面`() {
        val sorted = TodoOrdering.sortForDisplay(
            listOf(
                todo(1, "没时间"),
                todo(2, "下午三点", timePoint = "15:00"),
            ),
        )
        assertEquals(listOf("下午三点", "没时间"), sorted.map { it.title })
    }

    @Test
    fun `时间早的排前面`() {
        val sorted = TodoOrdering.sortForDisplay(
            listOf(
                todo(1, "晚上", timePoint = "20:00"),
                todo(2, "早上", timePoint = "08:00"),
                todo(3, "中午", timePoint = "12:30"),
            ),
        )
        assertEquals(listOf("早上", "中午", "晚上"), sorted.map { it.title })
    }

    @Test
    fun `同优先级同时间时按手工顺序`() {
        val sorted = TodoOrdering.sortForDisplay(
            listOf(
                todo(1, "第三", sortOrder = 3),
                todo(2, "第一", sortOrder = 1),
                todo(3, "第二", sortOrder = 2),
            ),
        )
        assertEquals(listOf("第一", "第二", "第三"), sorted.map { it.title })
    }

    @Test
    fun `优先级压过时间点`() {
        val sorted = TodoOrdering.sortForDisplay(
            listOf(
                todo(1, "早上但普通", timePoint = "08:00"),
                todo(2, "没时间但紧急", priority = 2),
            ),
        )
        assertEquals(listOf("没时间但紧急", "早上但普通"), sorted.map { it.title })
    }

    @Test
    fun `空清单不报错`() {
        assertEquals(0, TodoOrdering.sortForDisplay(emptyList()).size)
    }
}
