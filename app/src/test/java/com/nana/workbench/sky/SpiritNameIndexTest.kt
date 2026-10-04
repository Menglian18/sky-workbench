package com.nana.workbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.2 改进 · 第 1 条（兑换树看不到）对应的测试：先祖名字的查找。
 */
class SpiritNameIndexTest {

    private val names = mapOf(
        "guid-1" to "放松日浴者",
        "guid-2" to "Butterfly Charmer",
        "guid-3" to "指路蜡烛匠",
    )

    @Test
    fun `能按中文名精确找到`() {
        val index = SpiritNameIndex.build(names)
        assertEquals("guid-1", SpiritNameIndex.find("放松日浴者", index))
    }

    @Test
    fun `能按英文名找到_大小写和空格都不影响`() {
        val index = SpiritNameIndex.build(names)
        assertEquals("guid-2", SpiritNameIndex.find("butterflycharmer", index))
        assertEquals("guid-2", SpiritNameIndex.find("  Butterfly   Charmer  ", index))
    }

    @Test
    fun `带括号后缀的写法也能对上`() {
        val index = SpiritNameIndex.build(names)
        assertEquals("guid-1", SpiritNameIndex.find("放松日浴者（复刻）", index))
        assertEquals("guid-1", SpiritNameIndex.find("放松日浴者 (Traveling)", index))
        assertEquals("guid-1", SpiritNameIndex.find("放松日浴者（2026 复刻）", index))
    }

    @Test
    fun `名字里带标点也能对上`() {
        val index = SpiritNameIndex.build(mapOf("g" to "Nesting Challenges: Workshop"))
        assertEquals("g", SpiritNameIndex.find("Nesting Challenges - Workshop", index))
        assertEquals("g", SpiritNameIndex.find("nestingchallengesworkshop", index))
    }

    @Test
    fun `一边包含另一边时也能对上`() {
        val index = SpiritNameIndex.build(mapOf("guid-1" to "放松日浴者"))
        assertEquals("guid-1", SpiritNameIndex.find("日浴者", index))
        assertEquals("guid-1", SpiritNameIndex.find("放松日浴者先祖", index))
    }

    @Test
    fun `太短的词不乱配`() {
        val index = SpiritNameIndex.build(mapOf("guid-1" to "放松日浴者"))
        assertNull("一个字不该乱配", SpiritNameIndex.find("者", index))
    }

    @Test
    fun `查不到就返回 null`() {
        val index = SpiritNameIndex.build(names)
        assertNull(SpiritNameIndex.find("完全没有这个人", index))
        assertNull(SpiritNameIndex.find("", index))
        assertNull(SpiritNameIndex.find("   ", index))
    }

    @Test
    fun `空索引不报错`() {
        assertNull(SpiritNameIndex.find("放松日浴者", emptyMap()))
    }

    @Test
    fun `空名字不会被建进索引`() {
        val index = SpiritNameIndex.build(mapOf("guid-1" to "", "guid-2" to "   "))
        assertEquals(0, index.size)
    }

    @Test
    fun `同一个名字只认第一个_不会来回跳`() {
        val index = SpiritNameIndex.build(mapOf("a" to "重名", "b" to "重名"))
        assertEquals(1, index.size)
        assertEquals("a", SpiritNameIndex.find("重名", index))
    }

    @Test
    fun `同一位先祖的中文名和英文名都能查到`() {
        // 真实场景：一位先祖同时有中文名和英文名，两个名字都要能对上同一个 guid。
        // （用单个名字的写法会互相覆盖，所以这里用 buildNames。）
        val index = SpiritNameIndex.buildNames(
            mapOf("guid-1" to listOf("放松日浴者", "Relaxed Daydreamer")),
        )
        assertEquals("guid-1", SpiritNameIndex.find("放松日浴者", index))
        assertEquals("guid-1", SpiritNameIndex.find("Relaxed Daydreamer", index))
        assertEquals(2, index.size)
    }

    @Test
    fun `名字列表里的空格和空串会被忽略`() {
        val index = SpiritNameIndex.buildNames(mapOf("guid-1" to listOf("  ", "", "放松日浴者")))
        assertEquals(1, index.size)
        assertEquals("guid-1", SpiritNameIndex.find("放松日浴者", index))
    }
    @Test
    fun `同名不同括号的先祖要各归各的`() {
        // 1.2.3 更新：这四位“远古…记忆”只靠括号区分
        val index = SpiritNameIndex.buildNames(
            mapOf(
                "guid-a" to listOf("远古黑暗记忆（暗花）"),
                "guid-b" to listOf("远古黑暗记忆（冥龙）"),
            ),
        )
        assertEquals("guid-a", SpiritNameIndex.find("远古黑暗记忆（暗花）", index))
        assertEquals("guid-b", SpiritNameIndex.find("远古黑暗记忆（冥龙）", index))
    }

    @Test
    fun `括号写成半角也能对上`() {
        val index = SpiritNameIndex.buildNames(mapOf("guid-a" to listOf("远古黑暗记忆（暗花）")))
        assertEquals("guid-a", SpiritNameIndex.find("远古黑暗记忆(暗花)", index))
    }

    @Test
    fun `wiki抳来的中文名能补上原本没中文名的先祖`() {
        // 模拟：某先祖原本只有英文名，现在把 wiki 的官方中文名也加进同一个 guid
        val index = SpiritNameIndex.buildNames(
            mapOf("guid-x" to listOf("", "Boogie Kid", "踏舞孩童")),
        )
        assertEquals("guid-x", SpiritNameIndex.find("踏舞孩童", index))
        assertEquals("guid-x", SpiritNameIndex.find("Boogie Kid", index))
    }
}
