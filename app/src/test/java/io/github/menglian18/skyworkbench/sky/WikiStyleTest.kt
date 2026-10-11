package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0 新增：给 WIKI 正文注入的阅读样式。
 *
 * 这几条都是"错了也看不出来、但用户会觉得难看/看不懂"的地方，
 * 所以宁可一个字一个字地断言。
 */
class WikiStyleTest {

    private val css = WikiStyle.readerCss(
        background = "#0F1115",
        surface = "#171A21",
        text = "#E6E8EE",
        muted = "#9AA3B2",
        accent = "#BB9AF7",
    )

    @Test
    fun `颜色分量转成六位十六进制`() {
        assertEquals("#000000", WikiStyle.hex(0f, 0f, 0f))
        assertEquals("#FFFFFF", WikiStyle.hex(1f, 1f, 1f))
        assertEquals("#0F1115", WikiStyle.hex(0x0F / 255f, 0x11 / 255f, 0x15 / 255f))
    }

    @Test
    fun `颜色分量超出范围也不会出怪颜色`() {
        assertEquals("#000000", WikiStyle.hex(-1f, -0.5f, 0f))
        assertEquals("#FFFFFF", WikiStyle.hex(2f, 9f, 1f))
    }

    @Test
    fun `256 级都能原样转回来`() {
        for (value in 0..255) {
            val part = value / 255f
            val expected = String.format(java.util.Locale.ROOT, "#%02X%02X%02X", value, value, value)
            assertEquals(expected, WikiStyle.hex(part, part, part))
        }
    }

    @Test
    fun `样式里带上当前主题的颜色`() {
        assertTrue(css.contains("#0F1115"))
        assertTrue(css.contains("#171A21"))
        assertTrue(css.contains("#E6E8EE"))
        assertTrue(css.contains("#9AA3B2"))
        assertTrue(css.contains("#BB9AF7"))
    }

    @Test
    fun `图片和表格不许超出屏幕`() {
        // 手机屏窄，WIKI 上的兑换图动辄 500px、表格更宽，不压住就会左右乱跑
        assertTrue(css.contains("img,video,iframe{max-width:100%!important"))
        assertTrue(css.contains("table{max-width:100%"))
        // 图外面那层写死宽度的 div 也要压住，不然图片被裁掉一半
        assertTrue(css.contains("div,section{max-width:100%"))
    }

    @Test
    fun `藏掉网页的零件但不动正文`() {
        assertTrue(css.contains("#breadcrumb"))
        assertTrue(css.contains(".mw-editsection"))
        assertTrue(css.contains("#catlinks"))
        assertTrue(css.contains("display:none!important"))
        // 目录要留着（长页面靠它跳转），只是缩小
        assertFalse(WikiStyle.HIDDEN.contains("#toc"))
        assertTrue(css.contains("#toc,.toc{background:"))
    }

    @Test
    fun `注入脚本里的样式被转义过`() {
        // 样式里有引号和换行时，如果直接拼进 JS，脚本就断了 —— 表现是"样式没生效"，
        // 查起来很费劲，所以这里专门盯一下
        val script = WikiStyle.injectScript("a{content:\"x\"}\nb{}")
        assertTrue(script.contains("sky-reader-style"))
        assertTrue(script.contains("viewport"))
        assertTrue(script.contains("width=device-width"))
        assertTrue(script.contains("\\n")) // 换行被转义成 \n 两个字符
        assertFalse(script.contains("\nb{}")) // 没有真换行漏出去
        assertFalse(script.contains("{content:\"x\"}")) // 引号不是原样拼的
    }

    @Test
    fun `注入脚本是一整段能直接执行的表达式`() {
        val script = WikiStyle.injectScript(css)
        assertTrue(script.startsWith("(function(){"))
        assertTrue(script.endsWith("})()"))
        // 样式表只建一次（一个 id：先按 id 找，没有才建），重复注入不会越堆越多
        assertEquals(2, script.split("sky-reader-style").size - 1)
    }
}
