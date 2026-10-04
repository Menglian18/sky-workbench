package io.github.menglian18.skyworkbench.util

import java.time.LocalDate

/**
 * 日期与字符串的互转。全 App 只此一处，避免有的地方写 `yyyy-MM-dd`、有的地方写别的格式。
 * （用 ISO 格式还有个好处：字符串排序就等于日期排序。）
 */
object DateKeys {

    fun of(date: LocalDate): String = date.toString()

    fun parse(key: String): LocalDate = LocalDate.parse(key)

    /** 今天（按凌晨 4 点日界算）。 */
    fun todayKey(nowMillis: Long = System.currentTimeMillis()): String =
        of(LogicalDate.of(java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nowMillis),
            java.time.ZoneId.systemDefault(),
        )))
}
