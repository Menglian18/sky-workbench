package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M4 阶段的测试之一：复刻页解析。
 * 测试数据是**从真实页面上抄下来的一行**，不是编的。
 */
class TravelingSpiritParserTest {

    private val realRow = "{{复刻项|id=252|复刻类型=常规复刻|同类序号=201|开始时间=2026-10-01|" +
        "结束时间=2026-10-05|常规复刻次数=4|先祖中文名=放松日浴者|所属季节=圣岛季|所属地图=云野|" +
        "装扮=面饰、发饰、斗篷、大型家具}}"

    private val realCollectiveRow = "{{复刻项|id=251|复刻类型=集体复刻|同类序号=12|开始时间=2026-09-24|" +
        "结束时间=2026-10-14|常规复刻次数=0|集体复刻次数=1|先祖中文名=采药人|所属季节=九色鹿季|" +
        "所属地图=禁阁|装扮=发型、裤子、小型家具}}"

    @Test
    fun `解析一行真实数据`() {
        val list = TravelingSpiritParser.parse(realRow, fetchedAt = 1000L)
        assertEquals(1, list.size)
        val item = list[0]
        assertEquals("放松日浴者", item.spiritName)
        assertEquals("常规复刻", item.type)
        assertEquals("2026-10-01", item.startDate)
        assertEquals("2026-10-05", item.endDate)
        assertEquals("面饰、发饰、斗篷、大型家具", item.items)
        assertEquals(1000L, item.fetchedAt)
        assertEquals(false, item.manuallyEdited)
    }

    @Test
    fun `解析多行并保留各自类型`() {
        val list = TravelingSpiritParser.parse(realRow + "\n" + realCollectiveRow, 1L)
        assertEquals(2, list.size)
        assertEquals("常规复刻", list[0].type)
        assertEquals("集体复刻", list[1].type)
        assertEquals("采药人", list[1].spiritName)
    }

    @Test
    fun `注释会被丢掉`() {
        val withComment = "{{复刻项|开始时间=2026-10-01|结束时间=2026-10-05|" +
            "先祖中文名=测试者<!-- 这是备注 -->|装扮=斗篷}}"
        val list = TravelingSpiritParser.parse(withComment, 1L)
        assertEquals(1, list.size)
        assertEquals("测试者", list[0].spiritName)
    }

    @Test
    fun `缺开始时间就不采纳`() {
        val broken = "{{复刻项|结束时间=2026-10-05|先祖中文名=缺时间的}}"
        assertEquals(0, TravelingSpiritParser.parse(broken, 1L).size)
    }

    @Test
    fun `缺先祖名就不采纳`() {
        val broken = "{{复刻项|开始时间=2026-10-01|结束时间=2026-10-05|装扮=斗篷}}"
        assertEquals(0, TravelingSpiritParser.parse(broken, 1L).size)
    }

    @Test
    fun `日期格式不对就不采纳`() {
        val dotted = "{{复刻项|开始时间=2026.10.01|结束时间=2026.10.05|先祖中文名=点号日期}}"
        assertEquals(0, TravelingSpiritParser.parse(dotted, 1L).size)
    }

    @Test
    fun `只有乱码文本时不报错`() {
        assertEquals(0, TravelingSpiritParser.parse("这页什么都没有", 1L).size)
        assertEquals(0, TravelingSpiritParser.parse("", 1L).size)
    }

    @Test
    fun `没有装扮字段时物品为空串`() {
        val noItems = "{{复刻项|开始时间=2026-10-01|结束时间=2026-10-05|先祖中文名=无物品}}"
        val list = TravelingSpiritParser.parse(noItems, 1L)
        assertEquals(1, list.size)
        assertEquals("", list[0].items)
    }

    @Test
    fun `日期归一化`() {
        assertEquals("2026-10-01", TravelingSpiritParser.normalizeDate("2026-10-01"))
        assertEquals("2026-10-01", TravelingSpiritParser.normalizeDate(" 2026-10-01 "))
        assertNull(TravelingSpiritParser.normalizeDate("2026.10.01"))
        assertNull(TravelingSpiritParser.normalizeDate("10月1日"))
        assertNull(TravelingSpiritParser.normalizeDate(null))
    }

    @Test
    fun `来源会写清楚`() {
        val list = TravelingSpiritParser.parse(realRow, 1L)
        assertEquals(TravelingSpiritParser.SOURCE, list[0].source)
    }
}
