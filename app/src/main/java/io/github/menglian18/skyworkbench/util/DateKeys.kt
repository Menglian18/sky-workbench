package io.github.menglian18.skyworkbench.util

import java.time.LocalDate

/**
 * 日期与字符串的互转。全 App 只此一处，避免有的地方写 `yyyy-MM-dd`、有的地方写别的格式。
 * （用 ISO 格式还有个好处：字符串排序就等于日期排序。）
 */
object DateKeys {

    fun of(date: LocalDate): String = date.toString()

    fun parse(key: String): LocalDate = LocalDate.parse(key)

    /**
     * 读得懂就返回日期，读不懂（空串、`2026-13-45` 这种）返回 null —— **绝不抛异常**。
     *
     * 凡是"数据来自网络、可能缺字段"的地方都要用这个，不要直接用 [parse]。
     * 闪退的教训：曾经拿"结束时间未公布"（空串）去 [parse]，首页一打开就崩。
     */
    fun parseOrNull(key: String?): LocalDate? {
        val text = key?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    /** 今天（按凌晨 4 点日界算）。 */
    fun todayKey(nowMillis: Long = System.currentTimeMillis()): String =
        of(LogicalDate.of(java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nowMillis),
            java.time.ZoneId.systemDefault(),
        )))
}
