package io.github.menglian18.skyworkbench.sky

import io.github.menglian18.skyworkbench.data.db.SkyTravelingSpiritEntity
import io.github.menglian18.skyworkbench.util.DateKeys

/**
 * 解析 B站光遇WIKI 复刻页里的 `{{复刻项|...}}`。
 *
 * 页面里每一行长这样（字段是固定的）：
 * ```
 * {{复刻项|id=252|复刻类型=常规复刻|同类序号=201|开始时间=2026-10-01|结束时间=2026-10-05|
 *   常规复刻次数=4|先祖中文名=放松日浴者|所属季节=圣岛季|所属地图=云野|装扮=面饰、发饰、斗篷、大型家具}}
 * ```
 * 注意：**这里没有蜡烛价格**（价格在兑换图 PNG 里，文本抓不到），价格另由图鉴数据提供。
 */
object TravelingSpiritParser {

    private val blockRegex = Regex("""\{\{复刻项\|([^}]*)\}\}""")
    private val commentRegex = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

    const val SOURCE = "B站光遇WIKI · 旅行先祖回归记录（国服）"

    fun parse(wikitext: String, fetchedAt: Long): List<SkyTravelingSpiritEntity> {
        val cleaned = commentRegex.replace(wikitext, "")
        val result = mutableListOf<SkyTravelingSpiritEntity>()
        for (match in blockRegex.findAll(cleaned)) {
            val fields = parseFields(match.groupValues[1])
            val name = fields["先祖中文名"]?.takeIf { it.isNotBlank() } ?: continue
            val start = normalizeDate(fields["开始时间"]) ?: continue
            val end = normalizeDate(fields["结束时间"]) ?: continue
            result += SkyTravelingSpiritEntity(
                spiritName = name,
                type = fields["复刻类型"]?.takeIf { it.isNotBlank() } ?: "旅行先祖",
                startDate = start,
                endDate = end,
                items = fields["装扮"].orEmpty(),
                source = SOURCE,
                fetchedAt = fetchedAt,
                manuallyEdited = false,
            )
        }
        return result
    }

    private fun parseFields(body: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (part in body.split('|')) {
            val index = part.indexOf('=')
            if (index <= 0) continue
            val key = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
        return map
    }

    /**
     * 只接受 `2026-10-01` 这种**真的能读懂**的日期。
     *
     * `2026.10.01` 格式不对的直接拒掉；`2026-13-45` 这种正则能过、
     * 但拿去 `LocalDate.parse` 会抛异常的，也必须拒掉 —— 否则界面上算天数时会闪退。
     */
    fun normalizeDate(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (!dateRegex.matches(text)) return null
        return text.takeIf { DateKeys.parseOrNull(it) != null }
    }
}
