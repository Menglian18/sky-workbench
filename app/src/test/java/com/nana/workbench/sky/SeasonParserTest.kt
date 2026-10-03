package com.nana.workbench.sky

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M4 阶段的测试之一：季节列表解析（WIKI 语义查询返回的 JSON）。
 */
class SeasonParserTest {

    private val realJson = """
        {
          "query": {
            "results": {
              "圣岛季": {
                "printouts": {
                  "开启时间": ["2026-07-17 08:00"],
                  "结束时间": ["2026-10-02 23:59"]
                }
              },
              "归巢季": {
                "printouts": {
                  "开启时间": ["2026-04-23 08:00"],
                  "结束时间": ["2026-07-08 23:59"]
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `解析出季节与起止日期`() {
        val list = SeasonParser.parse(realJson, 999L)
        assertEquals(2, list.size)
        // 按开始时间排序
        assertEquals("归巢季", list[0].name)
        assertEquals("2026-04-23", list[0].startDate)
        assertEquals("2026-07-08", list[0].endDate)
        assertEquals("圣岛季", list[1].name)
        assertEquals(999L, list[1].fetchedAt)
    }

    @Test
    fun `缺结束时间的条目会被跳过`() {
        val json = """{"query":{"results":{"坏数据":{"printouts":{"开启时间":["2026-01-01 08:00"]}}}}}"""
        assertEquals(0, SeasonParser.parse(json, 1L).size)
    }

    @Test
    fun `没有结果时返回空`() {
        assertEquals(0, SeasonParser.parse("""{"query":{"results":{}}}""", 1L).size)
    }

    @Test
    fun `不是 JSON 也不崩`() {
        assertEquals(0, SeasonParser.parse("这不是 json", 1L).size)
        assertEquals(0, SeasonParser.parse("", 1L).size)
    }
}
