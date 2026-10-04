package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 1.2.2 改进 · 第 2 条对应的测试：图鉴里**只要中文、不要英文**。
 *
 * 名字在生成数据时就翻成中文了（tools/translate_items.py），界面只管取用；
 * 这里钉住三件事：
 *  1. 有中文名就用中文名；
 *  2. 没有中文名时退回中文部位名；
 *  3. **任何情况下都不显示英文**（没有中文名就显示"未知物品"）。
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

    private val ascii = Regex("[A-Za-z]")

    @Test
    fun `有中文名就显示中文名`() {
        assertEquals("白鸟斗篷", entry(name = "White Bird Cape", nameZh = "白鸟斗篷").displayName)
    }

    @Test
    fun `没有中文名时退回中文部位名`() {
        assertEquals("斗篷", entry(name = "Some Cape", partZh = "斗篷").displayName)
    }

    @Test
    fun `英文一律不显示_只有英文名时显示未知物品`() {
        val onlyEnglish = entry(name = "White Bird Cape")
        assertEquals("未知物品", onlyEnglish.displayName)
        assertFalse("绝不能把英文名漏出来", ascii.containsMatchIn(onlyEnglish.displayName))
    }

    @Test
    fun `中英文都在时也只显示中文`() {
        val shown = entry(name = "Butterfly Hair", nameZh = "蝴蝶发型", partZh = "发型").displayName
        assertEquals("蝴蝶发型", shown)
        assertFalse(ascii.containsMatchIn(shown))
    }

    @Test
    fun `前后空格会被去掉`() {
        assertEquals("斗篷", entry(nameZh = "  斗篷  ").displayName)
    }

    @Test
    fun `什么都没有时也不会是空`() {
        assertEquals("未知物品", entry().displayName)
        assertEquals("未知物品", entry(name = "   ", nameZh = "  ", partZh = " ").displayName)
    }

    @Test
    fun `每一件物品显示出来都不含英文字母`() {
        val entries = listOf(
            entry(name = "Cape", nameZh = "斗篷"),
            entry(name = "Hair", partZh = "发型"),
            entry(name = "Mask"),
            entry(),
        )
        entries.forEach { item ->
            assertFalse(
                "不该出现英文：${item.displayName}",
                ascii.containsMatchIn(item.displayName),
            )
        }
    }
}
