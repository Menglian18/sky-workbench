package com.nana.workbench.sky

/**
 * 先祖名字的查找（1.2 改进 · 第 1 条：兑换树看不到）。
 *
 * 为什么需要它：复刻数据来自 wiki（只有名字），兑换树按 guid 索引，
 * 所以"名字能不能对上"直接决定了用户点了"看兑换树"能不能看到东西。
 * 之前就是因为数据里缺 guid，这里永远查不到 —— 现在抽成纯逻辑并测起来。
 */
object SpiritNameIndex {

    /**
     * 归一化：去掉括号里的补充说明、去掉空格与常见标点、转小写。
     * 例："放松日浴者（复刻）" → "放松日浴者"；"Traveling Spirit: Butterfly" → "travelingspiritbutterfly"
     */
    fun normalize(raw: String): String = raw
        .replace(Regex("[（(\\[【].*?[）)\\]】]"), "")
        .replace(Regex("[\\s　]+"), "")
        .replace(Regex("[:：·、,，.。!！?？\"'“”‘’\\-—_/]+"), "")
        .lowercase()
        .trim()

    /**
     * 由" guid → 名字列表"建索引。
     *
     * 之所以要"列表"而不是单个名字：同一位先祖既有中文名也有英文名，
     * 如果按 guid 存单个名字，后写进去的会把前一个覆盖掉
     * （这正是 1.2 第 1 条修复过程中踩到的坑：英文名把中文名覆盖了，中文就查不到）。
     */
    fun buildNames(namesByGuid: Map<String, List<String>>): Map<String, String> {
        val index = mutableMapOf<String, String>()
        namesByGuid.forEach { (guid, names) ->
            names.forEach { name ->
                val key = normalize(name)
                if (key.isNotEmpty()) index.putIfAbsent(key, guid)
            }
        }
        return index
    }

    /** 每位先祖只有一个名字时的简便写法 */
    fun build(names: Map<String, String>): Map<String, String> =
        buildNames(names.mapValues { (_, name) -> listOf(name) })

    /**
     * 找先祖 guid：
     *  1. 先精确匹配；
     *  2. 再"你包含我 / 我包含你"（两边都得有 3 个字以上，避免乱配）；
     *  3. 找不到就返回 null（界面会提示"没自动对上"）。
     */
    fun find(query: String, index: Map<String, String>): String? {
        val key = normalize(query)
        if (key.isEmpty()) return null
        index[key]?.let { return it }
        if (key.length < 3) return null
        return index.entries
            .firstOrNull { (name, _) ->
                name.length >= 3 && (name.contains(key) || key.contains(name))
            }
            ?.value
    }
}
