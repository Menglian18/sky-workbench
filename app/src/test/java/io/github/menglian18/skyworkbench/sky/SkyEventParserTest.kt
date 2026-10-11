package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.3.0 新增：活动列表解析（WIKI「分类:节日活动」的语义查询结果）。
 */
class SkyEventParserTest {

    private val realJson = """
        {
          "query": {
            "results": {
              "2026年秋宵节": {
                "printouts": {
                  "开启时间": ["2026-09-24 00:00"],
                  "结束时间": ["2026-10-14 23:59"]
                }
              },
              "2026年我的世界联动": {
                "printouts": {
                  "开启时间": ["2026-10-02 00:00"],
                  "结束时间": ["2026-10-18 23:59"]
                }
              },
              "还没公布结束时间的活动": {
                "printouts": {
                  "开启时间": ["2026-11-01 00:00"]
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `解析出活动与起止日期`() {
        val list = SkyEventParser.parse(realJson, 777L)
        assertEquals(3, list.size)
        assertEquals("2026年秋宵节", list[0].name)
        assertEquals("2026-09-24", list[0].startDate)
        assertEquals("2026-10-14", list[0].endDate)
        assertEquals(777L, list[0].fetchedAt)
        assertEquals("2026年我的世界联动", list[1].name)
    }

    @Test
    fun `结束时间没公布的活动照样留下_记成空串`() {
        val list = SkyEventParser.parse(realJson, 1L)
        val last = list.last()
        assertEquals("还没公布结束时间的活动", last.name)
        assertEquals("", last.endDate)
    }

    @Test
    fun `占位结束时间也当未公布`() {
        val json = """{"query":{"results":{"某活动":{"printouts":{"开启时间":["2026-05-01 00:00"],"结束时间":["2099-12-31 23:59"]}}}}}"""
        assertEquals("", SkyEventParser.parse(json, 1L)[0].endDate)
    }

    @Test
    fun `没有开启时间的条目跳过`() {
        val json = """{"query":{"results":{"坏数据":{"printouts":{"结束时间":["2026-01-01 00:00"]}}}}}"""
        assertEquals(0, SkyEventParser.parse(json, 1L).size)
    }

    @Test
    fun `没有结果时返回空_不是JSON也不崩`() {
        assertEquals(0, SkyEventParser.parse("""{"query":{"results":{}}}""", 1L).size)
        assertEquals(0, SkyEventParser.parse("这不是 json", 1L).size)
        assertEquals(0, SkyEventParser.parse("", 1L).size)
    }
}
