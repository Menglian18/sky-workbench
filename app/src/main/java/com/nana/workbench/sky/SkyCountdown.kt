package com.nana.workbench.sky

import com.nana.workbench.data.db.SkySeasonEntity
import com.nana.workbench.data.db.SkyTravelingSpiritEntity
import com.nana.workbench.util.DateKeys
import java.time.temporal.ChronoUnit

/** 复刻的"正在进行"与"下一次要来"。 */
data class SkyCountdownPicks(
    val ongoing: SkyTravelingSpiritEntity?,
    val next: SkyTravelingSpiritEntity?,
    /** 与"正在进行"同一天开始的一共有几位（集体复刻会有好几位） */
    val ongoingCount: Int = 0,
    /** 与"下一次"同一天开始的一共有几位 */
    val nextCount: Int = 0,
) {
    /** 是不是集体复刻（同期不止一位先祖） */
    val ongoingIsCollective: Boolean get() = ongoingCount > 1
    val nextIsCollective: Boolean get() = nextCount > 1
}

/**
 * 光遇倒计时的计算（纯函数，便于在电脑上测）。
 *
 * 日期都是 `yyyy-MM-dd`，字符串比较就等于日期比较。
 */
object SkyCountdown {

    /** 到 [endDate] 还剩几天（今天到期算 0，已经过去算负数）。 */
    fun daysLeft(endDate: String, todayKey: String): Long =
        ChronoUnit.DAYS.between(DateKeys.parse(todayKey), DateKeys.parse(endDate))

    /**
     * 从复刻列表里挑出：
     *  - 正在进行中的（开始日 ≤ 今天 ≤ 结束日），有多条时取开始最晚的
     *  - 下一次要来的（开始日 > 今天），取开始最早的
     *
     * 同时统计"同一天开始的还有几位"——集体复刻是一次来好几位，
     * 界面上必须说清是"几位先祖"，不能只显示其中一位的名字。
     */
    fun pick(events: List<SkyTravelingSpiritEntity>, todayKey: String): SkyCountdownPicks {
        val ongoing = events
            .filter { it.startDate <= todayKey && todayKey <= it.endDate }
            .maxByOrNull { it.startDate }
        val next = events
            .filter { it.startDate > todayKey }
            .minByOrNull { it.startDate }

        return SkyCountdownPicks(
            ongoing = ongoing,
            next = next,
            ongoingCount = ongoing?.let { picked -> events.count { it.startDate == picked.startDate } } ?: 0,
            nextCount = next?.let { picked -> events.count { it.startDate == picked.startDate } } ?: 0,
        )
    }

    /** 当前季节（结束日 ≥ 今天里开始最晚的那个）。 */
    fun currentSeason(seasons: List<SkySeasonEntity>, todayKey: String): SkySeasonEntity? =
        seasons
            .filter { it.endDate >= todayKey }
            .maxByOrNull { it.startDate }

    /** 季节还剩几天。 */
    fun seasonDaysLeft(season: SkySeasonEntity?, todayKey: String): Long? =
        season?.let { daysLeft(it.endDate, todayKey) }
}
