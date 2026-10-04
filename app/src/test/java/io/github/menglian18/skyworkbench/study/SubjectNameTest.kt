package io.github.menglian18.skyworkbench.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.1 改进 · B 模块（学科 + 任务）对应的测试：学科名与任务内容的整理规则。
 */
class SubjectNameTest {

    @Test
    fun `学科名会去掉前后空格`() {
        assertEquals("数学", normalizeSubject("  数学  "))
    }

    @Test
    fun `中间连续空白会压成一个空格`() {
        assertEquals("大学 英语", normalizeSubject("大学    英语"))
        assertEquals("A B", normalizeSubject("A\t\nB"))
    }

    @Test
    fun `空的和只有空格的不接受`() {
        assertNull(normalizeSubject(""))
        assertNull(normalizeSubject("   "))
        assertNull(normalizeSubject("\t\n"))
    }

    @Test
    fun `太长的学科名会截断`() {
        val long = "字".repeat(50)
        val name = normalizeSubject(long)
        assertEquals(SUBJECT_MAX_LENGTH, name?.length)
    }

    @Test
    fun `中文和符号都原样保留`() {
        assertEquals("数学（上册）", normalizeSubject("数学（上册）"))
        assertEquals("C++", normalizeSubject("C++"))
        assertEquals("物理-力学", normalizeSubject("物理-力学"))
    }

    @Test
    fun `任务内容会去掉前后空格`() {
        assertEquals("做完第三章", normalizeTaskTitle("  做完第三章 "))
    }

    @Test
    fun `空任务不接受`() {
        assertNull(normalizeTaskTitle(""))
        assertNull(normalizeTaskTitle("   "))
    }

    @Test
    fun `学科合并会去重并排序`() {
        assertEquals(
            listOf("数学", "英语", "语文"),
            mergeSubjects(listOf("语文", "数学"), listOf("英语", "数学")),
        )
    }

    @Test
    fun `刚建好还没加任务的学科也要显示出来`() {
        // 数据库里还什么都没有，但用户刚点了"新建学科"
        assertEquals(listOf("物理"), mergeSubjects(emptyList(), listOf("物理")))
    }

    @Test
    fun `合并时空白与重复会被清掉`() {
        assertEquals(
            listOf("数学", "英语"),
            mergeSubjects(listOf("数学", "  ", "数学"), listOf("英语", " 英语 ")),
        )
    }

    @Test
    fun `两边都空时结果是空`() {
        assertEquals(emptyList<String>(), mergeSubjects(emptyList(), emptyList()))
    }
}
