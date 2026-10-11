package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `没有结果时返回空`() {
        assertEquals(0, SeasonParser.parse("""{"query":{"results":{}}}""", 1L).size)
    }

    @Test
    fun `不是 JSON 也不崩`() {
        assertEquals(0, SeasonParser.parse("这不是 json", 1L).size)
        assertEquals(0, SeasonParser.parse("", 1L).size)
    }


    // ── 1.3.0：用户反馈"季节的时间不对" ─────────────────────────────────────────
    //
    // 注意：这里**故意改掉了**旧行为。以前"缺少结束时间的季节会被跳过"，
    // 结果是刚公布的季节（WIKI 上还没写结束时间）在界面上整块消失；
    // 现在改成保留、把结束时间记成空串 = "官方未公布"。

    @Test
    fun `缺少结束时间的季节不再被丢掉_而是记成官方未公布`() {
        val json = """{"query":{"results":{"珍珠季":{"printouts":{"开启时间":["2026-10-22 08:00"]}}}}}"""
        val list = SeasonParser.parse(json, 1L)
        assertEquals(1, list.size)
        assertEquals("珍珠季", list[0].name)
        assertEquals("2026-10-22", list[0].startDate)
        assertEquals("", list[0].endDate)
    }

    @Test
    fun `占位结束时间_2099年那种_当成未公布`() {
        // WIKI 上没公布结束时间的季节，模板会填 2099-12-31（真实数据就是这样），
        // 照抄的话界面会显示"还剩 3 万多天"
        val json = """{"query":{"results":{"珍珠季":{"printouts":{"开启时间":["2026-10-22 08:00"],"结束时间":["2099-12-31 23:59"]}}}}}"""
        val list = SeasonParser.parse(json, 1L)
        assertEquals(1, list.size)
        assertEquals("", list[0].endDate)
        assertTrue(SeasonParser.isPlaceholder("2099-12-31"))
        assertTrue(!SeasonParser.isPlaceholder("2026-10-22"))
    }

    @Test
    fun `结束时间归一化的各种脏数据`() {
        assertEquals("2026-08-01", SeasonParser.normalizeEnd("2026-08-01 23:59", "2026-05-01"))
        assertEquals("", SeasonParser.normalizeEnd(null, "2026-05-01"))
        assertEquals("", SeasonParser.normalizeEnd("", "2026-05-01"))
        assertEquals("", SeasonParser.normalizeEnd("看不懂", "2026-05-01"))
        assertEquals("", SeasonParser.normalizeEnd("2099-01-01 00:00", "2026-05-01"))
        assertEquals("早于开始时间也是脏数据", "", SeasonParser.normalizeEnd("2026-01-01 00:00", "2026-05-01"))
    }


    @Test
    fun `effectiveEnd_把没公布的收拾成空串`() {
        assertEquals("", SeasonParser.effectiveEnd(""))
        assertEquals("", SeasonParser.effectiveEnd("2099-12-31"))
        assertEquals("", SeasonParser.effectiveEnd("看不懂"))
        assertEquals("2026-10-14", SeasonParser.effectiveEnd("2026-10-14"))
    }
}
