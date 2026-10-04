package io.github.menglian18.skyworkbench.today

import io.github.menglian18.skyworkbench.util.DateKeys
import java.time.temporal.ChronoUnit

/**
 * 一条待办的"时间"：哪一天 + 几点几分。
 *
 * 1.1 改进第 2 条：时间不再是手打的文本框，改成"日期 / 时 / 分"来选，日期默认是今天。
 * 解析、格式化、越界处理这些规则都放在这里（纯数据，能测），界面只管显示。
 */
data class TodoSchedule(
    val dateKey: String,
    val hour: Int? = null,
    val minute: Int? = null,
) {
    /** 存进数据库的时间点，形如 "08:30"；没选时间就是 null */
    val timePoint: String? get() =
        if (hour != null && minute != null) "%02d:%02d".format(hour, minute) else null

    val hasTime: Boolean get() = timePoint != null

    fun withDate(newDateKey: String): TodoSchedule = copy(dateKey = newDateKey)

    /** 越界的时/分会被夹回合法范围，不会写出 "25:70" 这种东西 */
    fun withTime(hour: Int, minute: Int): TodoSchedule =
        copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59))

    fun clearTime(): TodoSchedule = copy(hour = null, minute = null)

    companion object {
        const val DEFAULT_HOUR = 9
        const val DEFAULT_MINUTE = 0

        /** 库里存的 "08:30" 读回来；读到乱七八糟的值就当作"没设时间"，不崩。 */
        fun from(dateKey: String, timePoint: String?): TodoSchedule {
            val text = timePoint?.trim().orEmpty()
            if (text.isEmpty()) return TodoSchedule(dateKey)
            val parts = text.split(':')
            if (parts.size < 2) return TodoSchedule(dateKey)
            val hour = parts[0].trim().toIntOrNull() ?: return TodoSchedule(dateKey)
            val minute = parts[1].trim().toIntOrNull() ?: return TodoSchedule(dateKey)
            if (hour !in 0..23 || minute !in 0..59) return TodoSchedule(dateKey)
            return TodoSchedule(dateKey, hour, minute)
        }

        /**
         * 日期必须是真实存在的 yyyy-MM-dd，否则退回 [fallback]。
         * 这样即使用户把系统日期改乱了、或旧数据里有脏值，也不会写进一条查不出来的记录。
         */
        fun safeDateKey(candidate: String?, fallback: String): String {
            val text = candidate?.trim().orEmpty()
            if (text.isEmpty()) return fallback
            return runCatching { DateKeys.of(DateKeys.parse(text)) }.getOrElse { fallback }
        }
    }
}

/**
 * 日期按钮上的短标签：今天 / 明天 / 昨天 / 10月9日。
 * 用户一眼就能确认"日期最新的是今日日期"这件事。
 */
fun dateLabel(dateKey: String, todayKey: String): String = runCatching {
    val date = DateKeys.parse(dateKey)
    val today = DateKeys.parse(todayKey)
    val short = "%d月%d日".format(date.monthValue, date.dayOfMonth)
    when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "今天 $short"
        1L -> "明天 $short"
        -1L -> "昨天 $short"
        else -> short
    }
}.getOrElse { dateKey }

/** 分可以选的档位：5 分钟一档（快捷用） */
val MINUTE_STEPS: List<Int> = (0..11).map { it * 5 }

/**
 * 时的加减：按 1 小时循环（23 点再往后是 0 点，0 点往前是 23 点）。
 */
fun shiftHour(hour: Int, forward: Boolean): Int =
    if (forward) (hour + 1 + 24) % 24 else (hour - 1 + 24) % 24

/**
 * 分的加减：在 [MINUTE_STEPS] 里找相邻档位，到头的绕回另一端。
 * 如果当前值不在档位上（例如编辑旧数据得到 37 分），就取相邻的档（→40 / →35）。
 */
fun shiftMinute(minute: Int, forward: Boolean): Int = if (forward) {
    MINUTE_STEPS.firstOrNull { it > minute } ?: MINUTE_STEPS.first()
} else {
    MINUTE_STEPS.lastOrNull { it < minute } ?: MINUTE_STEPS.last()
}

/**
 * 分的加减：**一分钟一档**（1.2 改进第 3 条：时间要精确到几时几分）。
 * 0 分往前是 59 分，59 分往后是 0 分。
 */
fun shiftMinuteExact(minute: Int, forward: Boolean): Int {
    val safe = minute.coerceIn(0, 59)
    return if (forward) (safe + 1) % 60 else (safe + 59) % 60
}
