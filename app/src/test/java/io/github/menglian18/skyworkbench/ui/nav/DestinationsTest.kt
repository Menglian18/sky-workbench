package io.github.menglian18.skyworkbench.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1 阶段的有效测试：锁住"底部五个格子 + 设置齿轮"这个导航契约（PRD §2.1）。
 * 任何人不小心把格子改成 6 个、改了名字、或者让两个页面路径重复，这个测试都会红。
 */
class DestinationsTest {

    @Test
    fun `底部正好五个格子`() {
        assertEquals(5, Routes.tabs.size)
        assertEquals(5, TabDestination.entries.size)
    }

    @Test
    fun `五个格子的名字与顺序符合PRD`() {
        assertEquals(
            listOf("首页", "今日", "娱乐", "学习", "备忘"),
            Routes.tabs.map { it.label },
        )
    }

    @Test
    fun `页面路径互不重复`() {
        val routes = Routes.all
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `页面路径都非空`() {
        Routes.all.forEach { assertTrue("路径不能为空", it.isNotBlank()) }
    }

    @Test
    fun `设置不是底部格子`() {
        assertFalse(Routes.tabs.any { it.route == Routes.SETTINGS })
        assertTrue(Routes.all.contains(Routes.SETTINGS))
        assertEquals(6, Routes.all.size)
    }

    @Test
    fun `打开应用默认进首页`() {
        assertEquals(TabDestination.HOME, TabDestination.default)
    }

    @Test
    fun `按路径能找到对应格子`() {
        Routes.tabs.forEach { tab ->
            assertEquals(tab, TabDestination.fromRoute(tab.route))
        }
    }

    @Test
    fun `未知路径不匹配任何格子`() {
        assertNull(TabDestination.fromRoute("不存在的页面"))
        assertNull(TabDestination.fromRoute(null))
    }
}
