package io.github.menglian18.skyworkbench.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1 阶段的有效测试：锁住"六套主题 + 字体三档"的定义（PRD §5.3 / 《开发计划》§5.1）。
 */
class ThemeTest {

    @Test
    fun `正好六套主题`() {
        assertEquals(6, SkyPalettes.all.size)
    }

    @Test
    fun `六套主题的名字与顺序符合开发计划`() {
        assertEquals(
            listOf("深夜黑", "清爽白", "护眼绿", "粉紫", "光遇星空", "像素复古"),
            SkyPalettes.all.map { it.displayName },
        )
    }

    @Test
    fun `主题的编号互不重复且非空`() {
        val ids = SkyPalettes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `所有颜色都是不透明的八位色值`() {
        SkyPalettes.all.forEach { p ->
            val colors = mapOf(
                "background" to p.background, "surface" to p.surface, "primary" to p.primary,
                "accent" to p.accent, "onBackground" to p.onBackground,
                "onSurfaceVariant" to p.onSurfaceVariant, "onPrimary" to p.onPrimary,
            )
            colors.forEach { (name, value) ->
                assertTrue("${p.id}.$name 超出范围: $value", value in 0xFF000000L..0xFFFFFFFFL)
                assertEquals("${p.id}.$name 必须不透明", 0xFFL, (value ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun `主色不能和背景色一样_否则看不见`() {
        SkyPalettes.all.forEach { p ->
            assertNotEquals("${p.id} 的主色和背景色相同", p.background, p.primary)
            assertNotEquals("${p.id} 的文字色和背景色相同", p.background, p.onBackground)
        }
    }

    @Test
    fun `按编号取主题_取不到就用默认`() {
        SkyPalettes.all.forEach { p ->
            assertEquals(p, SkyPalettes.byId(p.id))
        }
        assertEquals(SkyPalettes.default, SkyPalettes.byId(null))
        assertEquals(SkyPalettes.default, SkyPalettes.byId("不存在的主题"))
    }

    @Test
    fun `默认主题是深夜黑`() {
        assertEquals("midnight", SkyPalettes.default.id)
    }

    @Test
    fun `字体正好三档_且越往后越大`() {
        assertEquals(3, FontScale.entries.size)
        assertEquals(listOf("标准", "大", "特大"), FontScale.entries.map { it.displayName })
        assertEquals(1.0f, FontScale.STANDARD.factor, 0.0001f)
        assertTrue(FontScale.LARGE.factor > FontScale.STANDARD.factor)
        assertTrue(FontScale.XLARGE.factor > FontScale.LARGE.factor)
    }

    @Test
    fun `按编号取字体档位_取不到就用标准`() {
        assertEquals(FontScale.LARGE, FontScale.byId("large"))
        assertEquals(FontScale.default, FontScale.byId(null))
        assertEquals(FontScale.default, FontScale.byId("不存在的档位"))
    }
}
