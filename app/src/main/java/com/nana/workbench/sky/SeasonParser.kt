package com.nana.workbench.sky

import com.nana.workbench.data.db.SkySeasonEntity
import org.json.JSONObject

/**
 * 解析 WIKI 的"季节列表"查询结果（语义查询返回的 JSON）。
 *
 * 形状大致是：
 * ```json
 * {"query":{"results":{"圣岛季":{"printouts":{"开启时间":["2026-07-17 08:00"],"结束时间":["2026-10-02 23:59"]}}}}}
 * ```
 */
object SeasonParser {

    fun parse(json: String, fetchedAt: Long): List<SkySeasonEntity> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val results = root.optJSONObject("query")?.optJSONObject("results") ?: return emptyList()
        val output = mutableListOf<SkySeasonEntity>()
        for (name in results.keys()) {
            val printouts = results.optJSONObject(name)?.optJSONObject("printouts") ?: continue
            val start = firstValue(printouts, "开启时间") ?: continue
            val end = firstValue(printouts, "结束时间") ?: continue
            val startDate = start.take(10)
            val endDate = end.take(10)
            if (startDate.length != 10 || endDate.length != 10) continue
            output += SkySeasonEntity(
                name = name,
                startDate = startDate,
                endDate = endDate,
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
