package com.nana.workbench.sky

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.2.1 改进 · 第 1 条对应的测试：图鉴里的物品名字**一定有**。
 *
 * 之前因为把物品中文名那列清空、回落又没生效，图鉴里名字一度全没了。
 * 现在拼成"中文部位名 · 英文名"，并且保证任何情况下都不会是空字符串。
 */
class CodexEntryTest {

    private fun entry(
        name: String = "",
        nameZh: String = "",
        partZh: String = "",
    ) = CodexEntry(
        guid = "g",
        name = name,
        nameZh = nameZh,
        partZh = partZh,
        type = "Cape",
        icon = "",
    )

    @Test
    fun `中文部位名和英文名拼在一起_中英都有`() {
        assertEquals("斗篷 · Cape", entry(name = "Cape", partZh = "斗篷").displayName)
    }

    @Test
    fun `有真中文名时优先用真中文名`() {
        assertEquals("白鸟斗篷 · Cape", entry(name = "Cape", nameZh = "白鸟斗篷", partZh = "斗篷").displayName)
    }

    @Test
    fun `只有英文名时就用英文名`() {
        assertEquals("Cape", entry(name = "Cape").displayName)
    }

    @Test
    fun `只有中文名时就用中文名`() {
        assertEquals("斗篷", entry(partZh = "斗篷").displayName)
    }

    @Test
    fun `中英文一样时不重复`() {
        assertEquals("Cape", entry(name = "Cape", nameZh = "Cape").displayName)
    }

    @Test
    fun `什么都没有时兜底成"未知物品"_绝不留空`() {
        assertEquals("未知物品", entry().displayName)
        assertEquals("未知物品", entry(name = "   ", partZh = "  ").displayName)
    }

    @Test
    fun `前后空格会被去掉`() {
        assertEquals("斗篷 · Cape", entry(name = "  Cape ", partZh = " 斗篷 ").displayName)
    }

    @Test
    fun `每一件物品都会有一个非空名字`() {
        val entries = listOf(
            entry(name = "Cape", partZh = "斗篷"),
            entry(name = "Hair"),
            entry(partZh = "面具"),
            entry(),
        )
        entries.forEach { assertEquals(false, it.displayName.isBlank()) }
    }
}
