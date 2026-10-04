package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.data.db.SkyTravelingSpiritEntity
import io.github.menglian18.skyworkbench.util.DateKeys
import java.time.temporal.ChronoUnit

/**
 * 清理规则（1.1 改进 · D1：清空行为）。
 *
 * 用户反馈"无论哪个清空都像把 App 初始化了"。查下来问题是：
 * "一键清理"原本会把**抓来的复刻/季节数据全部删掉**（连正在复刻的也删），
 * 看起来就像数据没了。正确的做法是只清**早就结束、留着也没用**的那些。
 *
 * 规则写在这里（纯函数、可测）：
 *  - 只清"抓来的"（`manuallyEdited = false`）；用户手动改过的一条都不动；
 *  - 结束日已经过去超过 [KEEP_DAYS] 天才算过期；
 *  - 正在进行的、还没结束的、日期读不懂的 —— 一律**保留**（拿不准就不删）。
 */
object SkyCleanup {

    /** 结束后还留几天再清（留一点，方便回头看刚刚结束的那次） */
    const val KEEP_DAYS = 7L

    /** 这个结束日是不是"早就过去了" */
    fun isExpired(endDate: String, todayKey: String, keepDays: Long = KEEP_DAYS): Boolean {
        val end = runCatching { DateKeys.parse(endDate) }.getOrNull() ?: return false
        val today = runCatching { DateKeys.parse(todayKey) }.getOrNull() ?: return false
        return ChronoUnit.DAYS.between(end, today) > keepDays
    }

    /** 该清掉的复刻条目 */
    fun expiredSpirits(
        items: List<SkyTravelingSpiritEntity>,
        todayKey: String,
        keepDays: Long = KEEP_DAYS,
    ): List<SkyTravelingSpiritEntity> =
        items.filter { !it.manuallyEdited && isExpired(it.endDate, todayKey, keepDays) }

    /** 该清掉的季节 */
    fun expiredSeasons(
        items: List<SkySeasonEntity>,
        todayKey: String,
        keepDays: Long = KEEP_DAYS,
    ): List<SkySeasonEntity> =
        items.filter { !it.manuallyEdited && isExpired(it.endDate, todayKey, keepDays) }
}
