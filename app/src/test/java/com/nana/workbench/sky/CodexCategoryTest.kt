package com.nana.workbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 改进 · C3（图鉴按游戏分类）对应的测试。
 */
class CodexCategoryTest {

    @Test
    fun `认得出的英文分类会翻成中文`() {
        assertEquals("斗篷", CodexCategory.labelOf("Cape"))
        assertEquals("发型", CodexCategory.labelOf("Hair"))
        assertEquals("面具", CodexCategory.labelOf("Mask"))
        assertEquals("乐器乐谱", CodexCategory.labelOf("Music"))
    }

    @Test
    fun `大小写和空格都不影响识别`() {
        assertEquals(CodexCategory.CAPE, CodexCategory.fromType(" cape "))
        assertEquals(CodexCategory.HAIR, CodexCategory.fromType("HAIR"))
    }

    @Test
    fun `认不出的分类归到其他占位_不崩`() {
        assertEquals(CodexCategory.SPECIAL, CodexCategory.fromType("Whatever"))
        assertEquals(CodexCategory.SPECIAL, CodexCategory.fromType(""))
    }

    @Test
    fun `光翼加成的几种写法都认得`() {
        // 1.2 第 5 条：光翼加成不能被漏掉
        assertEquals(CodexCategory.WING_BUFF, CodexCategory.fromType("WingBuff"))
        assertEquals(CodexCategory.WING_BUFF, CodexCategory.fromType("Wing Buff"))
        assertEquals(CodexCategory.WING_BUFF, CodexCategory.fromType("wingbuff"))
        assertEquals(true, CodexCategory.WING_BUFF.showInCodex)
    }

    @Test
    fun `占位和任务物品不放进图鉴`() {
        assertFalse(CodexCategory.SPECIAL.showInCodex)
        assertFalse(CodexCategory.QUEST.showInCodex)
        assertTrue(CodexCategory.CAPE.showInCodex)
    }

    @Test
    fun `展示顺序里不该出现被隐藏的分类_且没有重复`() {
        val shown = CodexCategory.shownInCodex
        assertFalse(shown.any { it == CodexCategory.SPECIAL })
        assertEquals(shown.size, shown.map { it.order }.toSet().size)
        assertEquals(shown.sortedBy { it.order }, shown)
    }

    @Test
    fun `分组时按分类顺序排_空分类不出现`() {
        val items = listOf(
            "Hair" to "发型甲",
            "Cape" to "斗篷乙",
            "Hair" to "发型丙",
            "Whatever" to "乱七八糟",
        )
        val groups = CodexCategory.group(items, typeOf = { it.first }, nameOf = { it.second })
        assertEquals(listOf(CodexCategory.CAPE, CodexCategory.HAIR), groups.map { it.first })
        assertEquals(listOf("斗篷乙"), groups[0].second.map { it.second })
        assertEquals(listOf("发型丙", "发型甲"), groups[1].second.map { it.second })
    }

    @Test
    fun `组内的东西会被排序`() {
        val items = listOf("Cape" to "乙", "Cape" to "甲")
        val groups = CodexCategory.group(items, typeOf = { it.first }, nameOf = { it.second })
        assertEquals(listOf("甲", "乙"), groups.single().second.map { it.second })
    }

    @Test
    fun `空清单分组结果是空`() {
        assertEquals(0, CodexCategory.group(emptyList<Pair<String, String>>(), { it.first }, { it.second }).size)
    }
}
