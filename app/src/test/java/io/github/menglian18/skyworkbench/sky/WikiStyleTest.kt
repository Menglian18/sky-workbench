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
        assertTrue(WikiStyle.HIDDEN.contains("#breadcrumb"))
        assertTrue(WikiStyle.HIDDEN.contains(".mw-editsection"))
        assertTrue(WikiStyle.HIDDEN.contains("#catlinks"))
        assertTrue(css.contains("display:none!important"))
    }

    @Test
    fun `1_4_1 要藏的那四样一个都不能漏`() {
        // 下面这些 class/id 是**侦察真实页面结构**得到的（见 WikiNoise 的注释）
        assertTrue("面包屑和「短刷编历」都在 .crumb 里", WikiStyle.HIDDEN.contains(".crumb"))
        assertTrue("MediaWiki 的目录", WikiStyle.HIDDEN.contains("#toc"))
        assertTrue("节日信息模板里给电脑看的那份拷贝", WikiStyle.HIDDEN.contains(".hidden-xs"))
        assertTrue("模板留下的两个空壳", WikiStyle.HIDDEN.contains("#noscrolltoc"))
        assertTrue(WikiStyle.HIDDEN.contains("#nohovertoc"))
        assertTrue(css.contains("display:none!important"))
    }

    @Test
    fun `清理脚本跟 Kotlin 里的规则是同一份`() {
        val script = WikiStyle.cleanupScript()
        val patterns =
            WikiNoise.blockPatterns() + WikiNoise.noisePatterns() + WikiNoise.pastPatterns()
        assertTrue(patterns.isNotEmpty())
        for (pattern in patterns) {
            // 脚本里的正则必须和电脑上测过的那份逐字一致（差一个字就白测了）
            val quoted = org.json.JSONObject.quote(pattern)
            assertTrue("脚本里少了这条规则：$pattern", script.contains(quoted))
        }
        for (marker in WikiNoise.stationLinks()) {
            assertTrue("脚本里少了站务链接标记：$marker", script.contains(marker))
        }
        for (alt in WikiNoise.decorImageAlts()) {
            assertTrue("脚本里少了装饰图标记：$alt", script.contains(alt))
        }
    }

    @Test
    fun `收拾空壳时不动表格单元格`() {
        val script = WikiStyle.cleanupScript()
        // 删空的 td/th 会让整张表错位，所以只收拾这几种容器
        assertTrue(script.contains("querySelectorAll('div,p,span,li,ul,ol,section')"))
        assertFalse(script.contains("'div,p,span,li,td,th'"))
        assertTrue(script.contains("createTreeWalker"))
        assertTrue(script.contains("closest('table,ul,ol,div')"))
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
