package io.github.menglian18.skyworkbench.today

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.1 改进 · 第 3 条（拖拽排序）对应的测试。
 */
class ReorderTest {

    @Test
    fun `往后拖`() {
        val moved = listOf("A", "B", "C", "D").moveItem(from = 0, to = 2)
        assertEquals(listOf("B", "C", "A", "D"), moved)
    }

    @Test
    fun `往前拖`() {
        val moved = listOf("A", "B", "C", "D").moveItem(from = 3, to = 1)
        assertEquals(listOf("A", "D", "B", "C"), moved)
    }

    @Test
    fun `拖到原位等于没动`() {
        val items = listOf("A", "B", "C")
        assertEquals(items, items.moveItem(1, 1))
    }

    @Test
    fun `拖到第一个和最后一个`() {
        assertEquals(listOf("C", "A", "B"), listOf("A", "B", "C").moveItem(2, 0))
        assertEquals(listOf("A", "B", "C"), listOf("A", "C", "B").moveItem(1, 2))
    }

    @Test
    fun `下标越界会被夹回来_不崩`() {
        val items = listOf("A", "B", "C")
        assertEquals(listOf("C", "A", "B"), items.moveItem(9, 0))
        assertEquals(listOf("B", "C", "A"), items.moveItem(0, 99))
        assertEquals(listOf("A", "B", "C"), items.moveItem(-5, -5))
    }

    @Test
    fun `只有一条或没有时不报错`() {
        assertEquals(listOf("A"), listOf("A").moveItem(0, 0))
        assertEquals(emptyList<String>(), emptyList<String>().moveItem(0, 1))
    }

    @Test
    fun `挪动不会丢元素也不会多出元素`() {
        val items = (1..7).toList()
        val moved = items.moveItem(0, 5)
        assertEquals(items.size, moved.size)
        assertEquals(items.toSet(), moved.toSet())
    }

    @Test
    fun `同一个象限内从 1 开始编号`() {
        val result = renumberWithinGroups(listOf(11L to 3, 12L to 3, 13L to 3))
        assertEquals(listOf(11L to 1, 12L to 2, 13L to 3), result)
    }

    @Test
    fun `不同象限各编各的`() {
        val result = renumberWithinGroups(
            listOf(1L to 3, 2L to 3, 3L to 2, 4L to 1, 5L to 1, 6L to 0),
        )
        assertEquals(
            listOf(1L to 1, 2L to 2, 3L to 1, 4L to 1, 5L to 2, 6L to 1),
            result,
        )
    }

    @Test
    fun `编号跟着拖后的顺序走`() {
        // 原来是 A B C，把 C 拖到最前：C 该拿 1，A 拿 2，B 拿 3
        val ids = listOf(100L to "A", 101L to "B", 102L to "C")
        val moved = ids.moveItem(from = 2, to = 0)
        val result = renumberWithinGroups(moved.map { it.first to 3 })
        assertEquals(listOf(102L to 1, 100L to 2, 101L to 3), result)
    }

    @Test
    fun `空清单不报错`() {
        assertEquals(emptyList<Pair<Long, Int>>(), renumberWithinGroups(emptyList()))
    }

    @Test
    fun `混排时每组都从 1 连续编号`() {
        val input = listOf(1L to 2, 2L to 2, 3L to 3, 4L to 3, 5L to 3)
        val result = renumberWithinGroups(input)
        assertEquals(1, result.filter { it.first in listOf(1L, 2L) }.minOf { it.second })
        assertEquals(1, result.filter { it.first in listOf(3L, 4L, 5L) }.minOf { it.second })
        assertEquals(listOf(1, 2), result.filter { it.first in listOf(1L, 2L) }.map { it.second })
        assertEquals(listOf(1, 2, 3), result.filter { it.first in listOf(3L, 4L, 5L) }.map { it.second })
    }
}
