package com.nana.workbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.2 改进 · 第 5 条对应的测试：表情同款不同等级合并。
 */
class EmoteGroupingTest {

    @Test
    fun `去掉英文的等级后缀`() {
        assertEquals("Wave", EmoteGrouping.baseName("Wave Level 2"))
        assertEquals("Wave", EmoteGrouping.baseName("Wave Lv.3"))
        assertEquals("Wave", EmoteGrouping.baseName("Wave lv 4"))
    }

    @Test
    fun `去掉中文的等级后缀`() {
        assertEquals("挥手", EmoteGrouping.baseName("挥手（等级2）"))
        assertEquals("挥手", EmoteGrouping.baseName("挥手 等级 3"))
        assertEquals("挥手", EmoteGrouping.baseName("挥手 第4级"))
        assertEquals("挥手", EmoteGrouping.baseName("挥手（2）"))
    }

    @Test
    fun `没有等级的名字原样保留`() {
        assertEquals("Butterfly", EmoteGrouping.baseName("Butterfly"))
        assertEquals("鞠躬", EmoteGrouping.baseName("鞠躬"))
    }

    @Test
    fun `能读出等级数字`() {
        assertEquals(2, EmoteGrouping.levelOf("Wave Level 2"))
        assertEquals(3, EmoteGrouping.levelOf("Wave Lv.3"))
        assertEquals(4, EmoteGrouping.levelOf("挥手（等级4）"))
        assertNull(EmoteGrouping.levelOf("Wave"))
        assertNull(EmoteGrouping.levelOf(""))
    }

    @Test
    fun `同一个表情的各级会合成一组`() {
        val items = listOf(
            "Wave Level 1",
            "Wave Level 2",
            "Wave Level 3",
            "Bow Level 1",
        )
        val groups = EmoteGrouping.group(items) { it }
        assertEquals(2, groups.size)
        val wave = groups.first { it.first == "Wave" }
        assertEquals(3, wave.second.size)
        assertEquals(listOf("Wave Level 1", "Wave Level 2", "Wave Level 3"), wave.second)
    }

    @Test
    fun `组内按等级从小到大排`() {
        val items = listOf("挥手（等级3）", "挥手（等级1）", "挥手（等级2）")
        val groups = EmoteGrouping.group(items) { it }
        assertEquals(listOf("挥手（等级1）", "挥手（等级2）", "挥手（等级3）"), groups.single().second)
    }

    @Test
    fun `没有等级的排在组内最后`() {
        val items = listOf("Wave", "Wave Level 1")
        val groups = EmoteGrouping.group(items) { it }
        assertEquals(listOf("Wave Level 1", "Wave"), groups.single().second)
    }

    @Test
    fun `空清单不报错`() {
        assertEquals(0, EmoteGrouping.group(emptyList<String>()) { it }.size)
    }

    @Test
    fun `只出现一次的表情也算一组`() {
        val groups = EmoteGrouping.group(listOf("鞠躬")) { it }
        assertEquals(1, groups.size)
        assertEquals("鞠躬", groups[0].first)
    }

    @Test
    fun `组名里不会有等级字样`() {
        val groups = EmoteGrouping.group(listOf("Wave Level 1", "Wave Level 2")) { it }
        groups.forEach { (base, _) ->
            assertEquals(false, base.contains("Level", ignoreCase = true))
        }
    }
}
