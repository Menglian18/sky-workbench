package com.nana.workbench.memo

import com.nana.workbench.util.DateKeys

/** 一条数字记录（只保留统计与画图需要的字段）。 */
data class NumberPoint(val date: String, val value: Double)

/** 数字表格的统计结果。 */
data class NumberStats(
    /** 最新一次记录 */
    val latest: NumberPoint?,
    /** 上一次记录 */
    val previous: NumberPoint?,
    /** 最新一次比上一次变化了多少（没有上一次时为 null） */
    val delta: Double?,
    /** 近 N 天，按日期从早到晚 */
    val trend: List<NumberPoint>,
    /** 一共记了多少笔 */
    val totalCount: Int,
) {
    val hasTrend: Boolean get() = trend.size >= 2
}

/**
 * 数字表格的"比上次涨跌"和"近 30 天趋势"计算。
 *
 * 写成纯函数（不碰数据库、不碰界面），所以在电脑上就能测。
 */
object NumberStatsCalculator {

    const val TREND_DAYS = 30

    fun compute(
        records: List<NumberPoint>,
        todayKey: String,
        trendDays: Int = TREND_DAYS,
    ): NumberStats {
        // 日期是 yyyy-MM-dd，字符串排序就等于日期排序
        val sorted = records.sortedWith(compareBy({ it.date }, { it.value }))
        val latest = sorted.lastOrNull()
        val previous = if (sorted.size >= 2) sorted[sorted.size - 2] else null

        val fromKey = DateKeys.of(DateKeys.parse(todayKey).minusDays((trendDays - 1).toLong()))
        val trend = sorted.filter { it.date >= fromKey }

        return NumberStats(
            latest = latest,
            previous = previous,
            delta = if (latest != null && previous != null) latest.value - previous.value else null,
            trend = trend,
            totalCount = sorted.size,
        )
    }
}
