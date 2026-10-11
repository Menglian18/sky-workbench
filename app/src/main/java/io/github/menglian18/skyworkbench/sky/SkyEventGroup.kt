package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkyEventEntity

/** 活动分成的几堆：进行中 / 即将开始 / 已结束。 */
data class SkyEventBuckets(
    val ongoing: List<SkyEventEntity> = emptyList(),
    val upcoming: List<SkyEventEntity> = emptyList(),
    val past: List<SkyEventEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = ongoing.isEmpty() && upcoming.isEmpty() && past.isEmpty()

    /**
     * 「活动」页实际显示的那些（1.4.0 用户要求：**已经结束的就不要了**）。
     *
     * [past] 仍然照旧算出来 —— 它是「清空早就没用的数据」那套规则要用的，
     * 只是不再往界面上摆。
     */
    val shown: List<SkyEventEntity> get() = ongoing + upcoming

    val hasShown: Boolean get() = shown.isNotEmpty()
}

/**
 * 把抓来的活动分成三堆（纯函数，便于在电脑上测）。
 *
 * 规则：
 *  - **进行中**：开始日 ≤ 今天，且（结束时间未公布 或 结束日 ≥ 今天）；最近的排前面
 *  - **即将开始**：开始日 > 今天；先开始的排前面
 *  - **已结束**：结束日 < 今天；刚结束的排前面
 */
object SkyEventGroup {

    fun split(events: List<SkyEventEntity>, todayKey: String): SkyEventBuckets {
        // 结束时间一律走 effectiveEnd："官方还没公布"（含 WIKI 占位值 2099）算没有结束时间
        val ongoing = events
            .filter {
                val end = SeasonParser.effectiveEnd(it.endDate)
                it.startDate <= todayKey && (end.isEmpty() || end >= todayKey)
            }
            .sortedWith(compareByDescending<SkyEventEntity> { it.startDate }.thenBy { it.name })
        val upcoming = events
            .filter { it.startDate > todayKey }
            .sortedWith(compareBy<SkyEventEntity> { it.startDate }.thenBy { it.name })
        val past = events
            .filter {
                val end = SeasonParser.effectiveEnd(it.endDate)
                end.isNotEmpty() && end < todayKey
            }
            .sortedWith(
                compareByDescending<SkyEventEntity> { SeasonParser.effectiveEnd(it.endDate) }
                    .thenBy { it.name },
            )
        return SkyEventBuckets(ongoing = ongoing, upcoming = upcoming, past = past)
    }
}
