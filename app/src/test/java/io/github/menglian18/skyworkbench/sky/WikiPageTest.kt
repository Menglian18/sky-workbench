package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0 新增：点开看详细介绍时拼出来的 WIKI 地址。
 *
 * 期望值不是拍脑袋写的 —— 是拿 WIKI 自己返回的 `fullurl` 对过的
 * （`action=query&prop=info&inprop=url`）。这种中文页面名只要编码差一个字符，
 * 服务器就给 404，用户看到的就是"打不开"。
 */
class WikiPageTest {

    @Test
    fun `中文页面名编码成网址`() {
        assertEquals(
            "2026%E5%B9%B4%E7%A7%8B%E5%AE%B5%E8%8A%82",
            WikiPage.encode("2026年秋宵节"),
        )
        assertEquals("%E7%8F%8D%E7%8F%A0%E5%AD%A3", WikiPage.encode("珍珠季"))
    }

    @Test
    fun `书名号这类符号也能编码`() {
        assertEquals(
            "2026%E5%B9%B4%E3%80%8A%E6%88%91%E7%9A%84%E4%B8%96%E7%95%8C%E3%80%8B%E8%81%94%E5%8A%A8",
            WikiPage.encode("2026年《我的世界》联动"),
        )
    }

    @Test
    fun `空格用百分号20而不是加号`() {
        // 页面名里的空格必须是 %20：`+` 只有表单参数才代表空格，放在页面名里
        // 服务器会当成字面上的加号，于是页面"不存在"
        assertEquals("A%20B", WikiPage.encode("A B"))
        assertFalse(WikiPage.encode("A B").contains("+"))
    }

    @Test
    fun `正文地址带 action等于render`() {
        assertEquals(
            "https://wiki.biligame.com/sky/index.php?title=%E7%8F%8D%E7%8F%A0%E5%AD%A3&action=render",
            WikiPage.readerUrl("珍珠季"),
        )
    }

    @Test
    fun `网页地址和 WIKI 自己给的地址一致`() {
        assertEquals(
            "https://wiki.biligame.com/sky/2026%E5%B9%B4%E7%A7%8B%E5%AE%B5%E8%8A%82",
            WikiPage.pageUrl("2026年秋宵节"),
        )
    }

    @Test
    fun `名字两边的空格不影响地址`() {
        assertEquals(WikiPage.pageUrl("珍珠季"), WikiPage.pageUrl("  珍珠季 "))
        assertEquals(WikiPage.readerUrl("珍珠季"), WikiPage.readerUrl("珍珠季\n"))
    }

    @Test
    fun `空名字不算能查的名字`() {
        assertFalse(WikiPage.isValidTitle(""))
        assertFalse(WikiPage.isValidTitle("   "))
        assertTrue(WikiPage.isValidTitle("珍珠季"))
    }
}
