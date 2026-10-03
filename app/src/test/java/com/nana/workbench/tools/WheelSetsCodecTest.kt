package com.nana.workbench.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5 阶段的测试之一：转盘选项的保存与读取。
 */
class WheelSetsCodecTest {

    @Test
    fun `存了再读_内容一致`() {
        val sets = listOf(
            WheelSet("午饭吃什么", listOf("火锅", "烤肉")),
            WheelSet("周末去哪", listOf("宅家")),
        )
        val restored = WheelSetsCodec.decode(WheelSetsCodec.encode(sets))
        assertEquals(sets, restored)
    }

    @Test
    fun `特殊字符不会坏掉`() {
        val sets = listOf(WheelSet("带\"引号\"和|竖线", listOf("a|b", "c\"d", "换\n行")))
        val restored = WheelSetsCodec.decode(WheelSetsCodec.encode(sets))
        assertEquals("带\"引号\"和|竖线", restored[0].name)
        assertEquals(listOf("a|b", "c\"d", "换\n行"), restored[0].options)
    }

    @Test
    fun `从来没存过时给默认几套`() {
        assertEquals(WheelSetsCodec.defaults(), WheelSetsCodec.decode(null))
        assertEquals(WheelSetsCodec.defaults(), WheelSetsCodec.decode(""))
        assertEquals(WheelSetsCodec.defaults(), WheelSetsCodec.decode("   "))
    }

    @Test
    fun `读不出来时退回默认`() {
        assertEquals(WheelSetsCodec.defaults(), WheelSetsCodec.decode("这不是 json"))
    }

    @Test
    fun `用户把选项都删光时尊重用户_不冒出默认值`() {
        assertEquals(0, WheelSetsCodec.decode("[]").size)
    }

    @Test
    fun `没有选项的套会被丢掉`() {
        val text = """[{"name":"空的","options":[]},{"name":"有内容","options":["A"]}]"""
        val restored = WheelSetsCodec.decode(text)
        assertEquals(1, restored.size)
        assertEquals("有内容", restored[0].name)
    }

    @Test
    fun `没有名字的套会被丢掉`() {
        val text = """[{"name":"","options":["A"]}]"""
        assertEquals(0, WheelSetsCodec.decode(text).size)
    }

    @Test
    fun `默认值本身是合法的_能编码解码`() {
        val restored = WheelSetsCodec.decode(WheelSetsCodec.encode(WheelSetsCodec.defaults()))
        assertEquals(WheelSetsCodec.defaults(), restored)
        assertTrue(restored.size >= 2)
    }
}
