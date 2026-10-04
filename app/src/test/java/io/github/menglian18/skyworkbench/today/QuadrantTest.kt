package io.github.menglian18.skyworkbench.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 改进 · 第 3 条（四象限）对应的测试。
 */
class QuadrantTest {

    @Test
    fun `四格齐全且编码不重复`() {
        assertEquals(4, Quadrant.ordered.size)
        assertEquals(
            "编码不能重复",
            4,
            Quadrant.ordered.map { it.code }.toSet().size,
        )
        assertEquals(listOf(3, 2, 1, 0), Quadrant.ordered.map { it.code })
    }

    @Test
    fun `编码能原样读回来`() {
        Quadrant.ordered.forEach { quadrant ->
            assertEquals(quadrant, Quadrant.fromCode(quadrant.code))
        }
    }

    @Test
    fun `认不出的编码当作都不_不崩`() {
        listOf(-1, 4, 99, Int.MIN_VALUE, Int.MAX_VALUE).forEach { weird ->
            assertEquals(Quadrant.ELIMINATE, Quadrant.fromCode(weird))
        }
    }

    @Test
    fun `每一格都有说法和建议`() {
        Quadrant.ordered.forEach { quadrant ->
            assertTrue("标题不能为空", quadrant.title.isNotBlank())
            assertTrue("短标题不能为空", quadrant.shortTitle.isNotBlank())
            assertTrue("建议不能为空", quadrant.advice.isNotBlank())
        }
    }

    @Test
    fun `待办的象限由 priority 决定`() {
        assertEquals(Quadrant.DO_FIRST, quadrantOf(3))
        assertEquals(Quadrant.SCHEDULE, quadrantOf(2))
        assertEquals(Quadrant.DELEGATE, quadrantOf(1))
        assertEquals(Quadrant.ELIMINATE, quadrantOf(0))
        assertEquals(Quadrant.ELIMINATE, quadrantOf(77))
    }

    @Test
    fun `重要和紧急是两条独立的轴`() {
        // 第 1 位 = 重要，第 0 位 = 紧急
        assertTrue(Quadrant.DO_FIRST.code and 2 != 0)
        assertTrue(Quadrant.DO_FIRST.code and 1 != 0)
        assertTrue(Quadrant.SCHEDULE.code and 2 != 0)
        assertEquals(0, Quadrant.SCHEDULE.code and 1)
        assertEquals(0, Quadrant.DELEGATE.code and 2)
        assertTrue(Quadrant.DELEGATE.code and 1 != 0)
        assertEquals(0, Quadrant.ELIMINATE.code)
    }

    @Test
    fun `旧版优先级会被换成正确的象限`() {
        // 旧版 2 = 紧急 → 现在归"重要且紧急"
        assertEquals(Quadrant.DO_FIRST.code, legacyPriorityToQuadrantCode(2))
        // 旧版 1 = 重要 → 现在归"重要不紧急"
        assertEquals(Quadrant.SCHEDULE.code, legacyPriorityToQuadrantCode(1))
        // 旧版 0 = 普通 → 归"都不"
        assertEquals(Quadrant.ELIMINATE.code, legacyPriorityToQuadrantCode(0))
    }

    @Test
    fun `旧版没见过的值也归到都不`() {
        assertEquals(Quadrant.ELIMINATE.code, legacyPriorityToQuadrantCode(-5))
        assertEquals(Quadrant.ELIMINATE.code, legacyPriorityToQuadrantCode(9))
    }

    @Test
    fun `换算后的编码都是合法的四象限编码`() {
        listOf(-1, 0, 1, 2, 3, 99).forEach { legacy ->
            val code = legacyPriorityToQuadrantCode(legacy)
            assertNotEquals("不该换出非法编码", null, Quadrant.ordered.firstOrNull { it.code == code })
        }
    }
}
