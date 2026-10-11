package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.data.db.SkyTravelingSpiritEntity
import io.github.menglian18.skyworkbench.util.DateKeys
import java.time.temporal.ChronoUnit

/** 复刻的"正在进行"与"下一次要来"。 */
data class SkyCountdownPicks(
    val ongoing: SkyTravelingSpiritEntity?,
    val next: SkyTravelingSpiritEntity?,
    /** 与"正在进行"同一天开始的一共有几位（集体复刻会有好几位） */
    val ongoingCount: Int = 0,
    /** 与"下一次"同一天开始的一共有几位 */
    val nextCount: Int = 0,
    /**
     * 与"正在进行"同期的**全部**先祖（1.3.0：集体复刻要一位一位显示照片和兑换树，
     * 以前只画被挑中的那一位）。
     */
    val ongoingSpirits: List<SkyTravelingSpiritEntity> = emptyList(),
    /** 与"下一次"同期的全部先祖 */
    val nextSpirits: List<SkyTravelingSpiritEntity> = emptyList(),
) {
    /** 是不是集体复刻（同期不止一位先祖） */
    val ongoingIsCollective: Boolean get() = ongoingCount > 1
    val nextIsCollective: Boolean get() = nextCount > 1

    /** 界面要逐位画出来的那批（单人复刻时就是他自己，兼容老调用） */
    val ongoingList: List<SkyTravelingSpiritEntity>
        get() = ongoingSpirits.ifEmpty { listOfNotNull(ongoing) }

    val nextList: List<SkyTravelingSpiritEntity>
        get() = nextSpirits.ifEmpty { listOfNotNull(next) }
}

/**
 * 季节在界面上的状态（1.3.0 新增）。
 *
 * 以前只有一句"还剩 N 天"，遇到下面两种真实情况就出错：
 *  - 官方**还没公布结束时间**（WIKI 上写的是占位值 2099-12-31）→ 会显示"还剩 3 万多天"
 *  - 今天正好在**两个季节中间** → 会把还没开始的下个季节当成"当季"
 */
data class SeasonStatus(
    val season: SkySeasonEntity?,
    /** 还没到开始日 */
    val notStarted: Boolean = false,
    /** 剩余天数；null = 结束时间官方还没公布 */
    val daysLeft: Long? = null,
    /** 还有几天开始（只有 notStarted 为真时才有值） */
    val daysUntilStart: Long? = null,
    /** 日期行要显示的结束时间：具体日期，或者「官方未公布」 */
    val endLabel: String = "",
) {
    /** 结束时间是不是官方已经公布了 */
    val hasKnownEnd: Boolean get() = season != null && !notStarted && daysLeft != null
}

/**
 * 光遇倒计时的计算（纯函数，便于在电脑上测）。
 *
 * 日期都是 `yyyy-MM-dd`，字符串比较就等于日期比较。
 */
object SkyCountdown {

    /**
     * 到 [endDate] 还剩几天（今天到期算 0，已经过去算负数）。
     *
     * **绝不抛异常**：日期读不懂（空串、脏数据）时返回 0。
     * 需要区分"读不懂"的调用方请用 [daysLeftOrNull]。
     * （教训：这里原来直接 `LocalDate.parse`，遇到"结束时间官方未公布"的空串就抛异常，
     *   首页一打开就闪退 —— 宁可显示 0，也不能让 App 崩。）
     */
    fun daysLeft(endDate: String, todayKey: String): Long = daysLeftOrNull(endDate, todayKey) ?: 0L

    /** 同上，但日期读不懂时返回 null（界面据此不显示天数）。 */
    fun daysLeftOrNull(endDate: String, todayKey: String): Long? {
        val end = DateKeys.parseOrNull(endDate) ?: return null
        val today = DateKeys.parseOrNull(todayKey) ?: return null
        return ChronoUnit.DAYS.between(today, end)
    }

    /**
     * 从复刻列表里挑出：
     *  - 正在进行中的（开始日 ≤ 今天 ≤ 结束日），有多条时取开始最晚的
     *  - 下一次要来的（开始日 > 今天），取开始最早的
     *
     * 同时把"同期那几位"一起带出来——集体复刻是一次来好几位，
     * 界面要一位一位显示照片与兑换树（1.3.0），不能只显示其中一位。
     */
    fun pick(events: List<SkyTravelingSpiritEntity>, todayKey: String): SkyCountdownPicks {
        val ongoingPick = events
            .filter { it.startDate <= todayKey && todayKey <= it.endDate }
            .maxByOrNull { it.startDate }
        val nextPick = events
            .filter { it.startDate > todayKey }
            .minByOrNull { it.startDate }

        val ongoingSpirits = sameStart(events, ongoingPick)
        val nextSpirits = sameStart(events, nextPick)

        return SkyCountdownPicks(
            ongoing = ongoingSpirits.firstOrNull() ?: ongoingPick,
            next = nextSpirits.firstOrNull() ?: nextPick,
            ongoingCount = ongoingSpirits.size,
            nextCount = nextSpirits.size,
            ongoingSpirits = ongoingSpirits,
            nextSpirits = nextSpirits,
        )
    }

    /** 同一天开始的那一批，按名字排（界面上顺序固定，测试也好写） */
    private fun sameStart(
        events: List<SkyTravelingSpiritEntity>,
        picked: SkyTravelingSpiritEntity?,
    ): List<SkyTravelingSpiritEntity> =
        picked?.let { p -> events.filter { it.startDate == p.startDate }.sortedBy { it.spiritName } }
            ?: emptyList()

    /**
     * 当前季节（1.3.0 修正）：
     *  - **正在进行**：开始日 ≤ 今天，且（结束日没到 或 结束时间还没公布）
     *  - 今天正好在**两个季节中间**：返回下一个即将开始的，界面会说"还有 N 天开始"
     *
     * 以前的写法只看"结束日 ≥ 今天"，于是把还没开始的下个季节当成当季，
     * 再配上占位结束日 2099，算出 3 万多天。
     */
    fun currentSeason(seasons: List<SkySeasonEntity>, todayKey: String): SkySeasonEntity? {
        val ongoing = seasons
            .filter {
                val end = endOf(it)
                it.startDate <= todayKey && (end.isEmpty() || end >= todayKey)
            }
            .maxByOrNull { it.startDate }
        if (ongoing != null) return ongoing
        return seasons.filter { it.startDate > todayKey }.minByOrNull { it.startDate }
    }

    /** 季节的结束时间（"官方还没公布"统一成空串，见 [SeasonParser.effectiveEnd]） */
    private fun endOf(season: SkySeasonEntity): String = SeasonParser.effectiveEnd(season.endDate)

    /** 季节还剩几天（结束时间官方未公布时返回 null，界面据此不显示天数）。 */
    fun seasonDaysLeft(season: SkySeasonEntity?, todayKey: String): Long? {
        if (season == null) return null
        val end = endOf(season)
        return if (end.isEmpty()) null else daysLeft(end, todayKey)
    }

    /** 季节在界面上要显示的全部信息。 */
    fun seasonStatus(season: SkySeasonEntity?, todayKey: String): SeasonStatus {
        if (season == null) return SeasonStatus(null)
        val notStarted = season.startDate > todayKey
        return SeasonStatus(
            season = season,
            notStarted = notStarted,
            daysLeft = if (notStarted) null else seasonDaysLeft(season, todayKey),
            daysUntilStart = if (notStarted) daysLeft(season.startDate, todayKey) else null,
            endLabel = endOf(season).ifBlank { "官方未公布" },
        )
    }
}
