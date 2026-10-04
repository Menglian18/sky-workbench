package io.github.menglian18.skyworkbench.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * M5 阶段的测试之一：骰子 / 随机抽 / 转盘。
 * 传固定种子，所以结果可复现，能精确断言。
 */
class RandomPickerTest {

    @Test
    fun `骰子结果落在范围内`() {
        val picker = RandomPicker(Random(42))
        repeat(200) {
            val value = picker.rollDice(6)
            assertTrue("点数 $value 越界", value in 1..6)
        }
    }

    @Test
    fun `骰子面数为一时只有一种结果`() {
        val picker = RandomPicker(Random(1))
        repeat(10) { assertEquals(1, picker.rollDice(1)) }
    }

    @Test
    fun `骰子面数非法时按一面处理`() {
        val picker = RandomPicker(Random(1))
        assertEquals(1, picker.rollDice(0))
        assertEquals(1, picker.rollDice(-5))
    }

    @Test
    fun `同样的种子结果一样`() {
        val first = RandomPicker(Random(7)).rollDice(100)
        val second = RandomPicker(Random(7)).rollDice(100)
        assertEquals(first, second)
    }

    @Test
    fun `随机抽一个只从非空选项里抽`() {
        val picker = RandomPicker(Random(3))
        val options = listOf("火锅", "", "烤肉")
        repeat(50) {
            val picked = picker.pickOne(options)
            assertTrue("抽到了空选项", picked == "火锅" || picked == "烤肉")
        }
    }

    @Test
    fun `选项全空时返回空`() {
        val picker = RandomPicker(Random(1))
        assertNull(picker.pickOne(listOf("", "   ")))
        assertNull(picker.pickOne(emptyList()))
    }

    @Test
    fun `不重复抽_抽过的不会再抽到`() {
        val picker = RandomPicker(Random(11))
        val options = listOf("A", "B", "C")
        val used = mutableSetOf<Int>()
        repeat(3) {
            val result = picker.pickOneWithoutRepeat(options, used)
            assertTrue(result != null)
            used += result!!.first
        }
        assertEquals(setOf(0, 1, 2), used)
    }

    @Test
    fun `不重复抽_抽完一轮后自动重来`() {
        val picker = RandomPicker(Random(5))
        val options = listOf("A", "B")
        val result = picker.pickOneWithoutRepeat(options, usedIndexes = setOf(0, 1))
        assertTrue("抽完一轮后应能重新开始", result != null)
        assertEquals("返回的下标与值必须对应", options[result!!.first], result.second)
        assertTrue("必须从选项里取", result.second in options)
    }

    @Test
    fun `不重复抽_空选项返回空`() {
        val picker = RandomPicker(Random(1))
        assertNull(picker.pickOneWithoutRepeat(emptyList(), emptySet()))
        assertNull(picker.pickOneWithoutRepeat(listOf("", " "), emptySet()))
    }
}
