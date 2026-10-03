package com.nana.workbench.sky

/**
 * 表情动作的分级合并（1.2 改进 · 第 5 条）。
 *
 * 同一个表情在游戏里有 1～4 级，数据里是分开的好几条（"Wave Level 2"、"Wave Lv.3"…），
 * 直接铺在图鉴里又乱又长。所以先按"去掉等级后的名字"合并成一组，
 * 点开再下滑看各级。
 */
object EmoteGrouping {

    /** 名字末尾的等级标记（英文/中文几种写法都认） */
    private val LEVEL_PATTERNS = listOf(
        Regex("(?i)[\\s_\\-]*level[\\s_\\-]*\\d+\\s*$"),
        Regex("(?i)[\\s_\\-]*lv\\.?[\\s_\\-]*\\d+\\s*$"),
        Regex("[\\s_\\-]*[（(]\\s*等级\\s*\\d+\\s*[）)]\\s*$"),
        Regex("[\\s_\\-]*[（(]\\s*\\d+\\s*[）)]\\s*$"),
        Regex("[\\s_\\-]*等级\\s*\\d+\\s*$"),
        Regex("[\\s_\\-]*第\\s*\\d+\\s*级\\s*$"),
    )

    private val LEVEL_NUMBER = Regex("(?i)(?:level|lv\\.?|等级|第)\\s*[（(]?\\s*(\\d+)")

    /** 去掉等级后的名字（"Wave Level 2" → "Wave"）；本来就没等级的原样返回 */
    fun baseName(raw: String): String {
        var name = raw.trim()
        LEVEL_PATTERNS.forEach { pattern -> name = pattern.replace(name, "") }
        val trimmed = name.trim()
        return trimmed.ifEmpty { raw.trim() }
    }

    /** 名字里的等级数字；没有就返回 null（界面据此显示"未分级"） */
    fun levelOf(raw: String): Int? =
        LEVEL_NUMBER.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /**
     * 按"去掉等级的名字"分组；组内按等级从小到大排（没有等级的排最后）。
     * 返回顺序按组名的中文拼音排，界面看着整齐。
     */
    fun <T> group(items: List<T>, nameOf: (T) -> String): List<Pair<String, List<T>>> {
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        return items
            .groupBy { baseName(nameOf(it)) }
            .toSortedMap(compareBy(collator) { it })
            .map { (base, group) ->
                base to group.sortedWith(
                    compareBy(nullsLast()) { levelOf(nameOf(it)) },
                )
            }
    }
}
