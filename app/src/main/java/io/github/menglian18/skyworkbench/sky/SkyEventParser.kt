package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkyEventEntity
import org.json.JSONObject

/**
 * 解析 WIKI「分类:节日活动」的语义查询结果（1.3.0 新增"活动查看"）。
 *
 * 形状和季节那条查询一样：
 * ```json
 * {"query":{"results":{"2026年秋宵节":{"printouts":{"开启时间":["2026-09-24 00:00"],"结束时间":["2026-10-14 23:59"]}}}}}
 * ```
 *
 * 结束时间的"官方还没公布"处理与季节共用一套（[SeasonParser.normalizeEnd]）：
 * 同一个 WIKI、同一个模板毛病，规则只写一遍。
 */
object SkyEventParser {

    fun parse(json: String, fetchedAt: Long): List<SkyEventEntity> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val results = root.optJSONObject("query")?.optJSONObject("results") ?: return emptyList()
        val output = mutableListOf<SkyEventEntity>()
        for (name in results.keys()) {
            val printouts = results.optJSONObject(name)?.optJSONObject("printouts") ?: continue
            val start = firstValue(printouts, "开启时间") ?: continue
            val startDate = start.take(10)
            if (startDate.length != 10) continue
            output += SkyEventEntity(
                name = name,
                startDate = startDate,
                endDate = SeasonParser.normalizeEnd(firstValue(printouts, "结束时间"), startDate),
                fetchedAt = fetchedAt,
                manuallyEdited = false,
            )
        }
        return output.sortedBy { it.startDate }
    }

    private fun firstValue(source: JSONObject, key: String): String? {
        val array = source.optJSONArray(key) ?: return null
        if (array.length() == 0) return null
        return array.optString(0).takeIf { it.isNotBlank() }
    }
}
