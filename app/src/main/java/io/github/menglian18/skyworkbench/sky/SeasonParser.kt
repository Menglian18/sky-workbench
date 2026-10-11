package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.util.DateKeys
import org.json.JSONObject

/**
 * 解析 WIKI 的"季节列表"查询结果（语义查询返回的 JSON）。
 *
 * 形状大致是：
 * ```json
 * {"query":{"results":{"圣岛季":{"printouts":{"开启时间":["2026-07-17 08:00"],"结束时间":["2026-10-02 23:59"]}}}}}
 * ```
 *
 * 1.3.0 修正（用户反馈"季节的时间不对"）：
 *  - WIKI 上**还没公布结束时间**的季节，模板会给一个占位值 `2099-12-31`。
 *    照抄就会显示成"还剩 3 万多天"。现在把占位值统一存成**空串**（= 官方未公布）。
 *  - 缺少结束时间的季节**不再被丢掉**：刚公布的季节本来就没写结束时间，
 *    丢掉它会让"下个季节"整块消失。
 */
object SeasonParser {

    /** 年份大于等于这个值，就当成 WIKI 的占位值（官方还没公布结束时间）。 */
    const val PLACEHOLDER_YEAR = 2099

    fun parse(json: String, fetchedAt: Long): List<SkySeasonEntity> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val results = root.optJSONObject("query")?.optJSONObject("results") ?: return emptyList()
        val output = mutableListOf<SkySeasonEntity>()
        for (name in results.keys()) {
            val printouts = results.optJSONObject(name)?.optJSONObject("printouts") ?: continue
            val start = firstValue(printouts, "开启时间") ?: continue
            val startDate = start.take(10)
            // 必须真的是一个能读懂的日子（`2026-13-45` 这种正则能过，但拿去解析会炸）
            if (DateKeys.parseOrNull(startDate) == null) continue
            output += SkySeasonEntity(
                name = name,
                startDate = startDate,
                endDate = normalizeEnd(firstValue(printouts, "结束时间"), startDate),
                fetchedAt = fetchedAt,
                manuallyEdited = false,
            )
        }
        return output.sortedBy { it.startDate }
    }

    /**
     * 结束时间归一化。下面几种情况一律当"官方未公布"（空串）：
     *  - 根本没有这个字段 / 是空的
     *  - 格式读不懂（不是 `yyyy-MM-dd`）
     *  - 是 WIKI 的占位值（2099 年那种）
     *  - 比开始时间还早（明显是脏数据）
     */
    fun normalizeEnd(rawEnd: String?, startDate: String): String {
        val end = rawEnd?.take(10) ?: return ""
        if (DateKeys.parseOrNull(end) == null) return ""
        if (isPlaceholder(end)) return ""
        if (end < startDate) return ""
        return end
    }

    /**
     * 把"官方还没公布结束时间"统一成空串。
     *
     * 除了空串和读不懂的，**年份 ≥ 2099 的占位值也算未公布**：
     * 装过旧版的人，数据库里可能还存着 WIKI 的占位值（比如 2099-12-31），
     * 不挡这一道的话，升级后一小时内（有抓取限速）界面还是"还剩 3 万多天"。
     */
    fun effectiveEnd(endDate: String): String =
        if (endDate.isBlank() || endDate.length < 10 || isPlaceholder(endDate)) "" else endDate

    /** 是不是 WIKI 的占位值（年份 ≥ 2099）。 */
    fun isPlaceholder(dateKey: String): Boolean =
        (dateKey.take(4).toIntOrNull() ?: 0) >= PLACEHOLDER_YEAR

    private fun firstValue(source: JSONObject, key: String): String? {
        val array = source.optJSONArray(key) ?: return null
        if (array.length() == 0) return null
        return array.optString(0).takeIf { it.isNotBlank() }
    }
}
