package io.github.menglian18.skyworkbench.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 改进 · 第 1 条（今日待办：点"完成"没添加）对应的测试。
 *
 * 规则："完成"也要把已经打好的内容存下去；但只有空格的内容不能存成一条空记录。
 */
class TodoDraftTest {

    @Test
    fun `打了字就算能存_哪怕没点添加并继续`() {
        assertTrue(TodoDraft.from("买牛奶").isSavable)
    }

    @Test
    fun `没打字不能存`() {
        assertFalse(TodoDraft.from("").isSavable)
    }

    @Test
    fun `只有空格不能存`() {
        assertFalse(TodoDraft.from("   ").isSavable)
        assertFalse(TodoDraft.from("\t\n ").isSavable)
    }

    @Test
    fun `标题前后空格会被去掉`() {
        assertEquals("买牛奶", TodoDraft.from("  买牛奶  ").cleanTitle)
    }

    @Test
    fun `备注前后空格也会去掉`() {
        assertEquals("两盒", TodoDraft.from("买牛奶", note = "  两盒 ").cleanNote)
    }

    @Test
    fun `时间点为空要存成 null 而不是空串`() {
        assertNull(TodoDraft.from("事", timePoint = "").cleanTimePoint)
        assertNull(TodoDraft.from("事", timePoint = "   ").cleanTimePoint)
        assertNull(TodoDraft.from("事", timePoint = null).cleanTimePoint)
        assertEquals("08:30", TodoDraft.from("事", timePoint = " 08:30 ").cleanTimePoint)
    }

    @Test
    fun `日期为空也存成 null`() {
        assertNull(TodoDraft.from("事").cleanDateKey)
        assertNull(TodoDraft(dateKey = "  ").cleanDateKey)
        assertEquals("2026-10-09", TodoDraft(dateKey = "2026-10-09").cleanDateKey)
    }

    @Test
    fun `只有时间没标题_依然不能存`() {
        assertFalse(TodoDraft.from("", timePoint = "08:30").isSavable)
    }

    @Test
    fun `默认优先级是普通`() {
        assertEquals(0, TodoDraft.from("事").priority)
    }
}
