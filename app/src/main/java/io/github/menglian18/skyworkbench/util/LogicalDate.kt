package io.github.menglian18.skyworkbench.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 日界规则：**凌晨 4 点算新一天**（PRD 附录 C 第 2 条）。
 *
 * 凌晨 4 点之前都还算"昨天"——这样熬夜到半夜记的事情不会串到第二天。
 * 全 App 只有这一处实现，今日计划 / 首页 / 数字表格都必须调用它，不许各写一套。
 */
object LogicalDate {

    /** 一天的起点：凌晨 4 点。 */
    const val DAY_START_HOUR: Long = 4

    /** 这个"时刻"算哪一天。 */
    fun of(moment: LocalDateTime): LocalDate = moment.minusHours(DAY_START_HOUR).toLocalDate()

    /** 某一天从哪个时刻开始（用于计算"今天还剩多久"）。 */
    fun startOf(logicalDate: LocalDate): LocalDateTime =
        logicalDate.atTime(DAY_START_HOUR.toInt(), 0)

    /** 这个时刻所属那一天的起点。 */
    fun startOfDayOf(moment: LocalDateTime): LocalDateTime = startOf(of(moment))
}
