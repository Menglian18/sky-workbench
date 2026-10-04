package io.github.menglian18.skyworkbench.sky

import org.json.JSONObject

/**
 * 兑换树（1.1 改进 · C2）。
 *
 * 由 `tools/build_trees.py` 生成、打包在 App 里的 `assets/codex/trees.json` 解析而来。
 * 注意：只带**图标网址**，不带图片本身 —— 图片是打开时联网现取的。
 */
data class TreeItem(
    val index: Int,
    val itemGuid: String,
    val zh: String,
    val en: String,
    val type: String,
    val icon: String,
    val candles: Int = 0,
    val hearts: Int = 0,
    val ascended: Int = 0,
    /** 季节蜡烛 + 季节爱心 + 活动货币，合成一个"其他"数 */
    val otherCurrency: Int = 0,
    val kids: List<Int> = emptyList(),
) {
    /** 界面上显示的名字：优先中文部位名，没有就用英文名 */
    val displayName: String get() = zh.ifBlank { en.ifBlank { "未知道具" } }

    fun costText(): String {
        if (candles + hearts + ascended == 0) {
            return if (otherCurrency > 0) "要 $otherCurrency 个季节/活动货币" else "免费"
        }
        return buildString {
            append("白蜡 ").append(candles)
            if (hearts > 0) append(" · 爱心 ").append(hearts)
            if (ascended > 0) append(" · 升华 ").append(ascended)
            if (otherCurrency > 0) append(" · 其他 ").append(otherCurrency)
        }
    }
}

/** 一位先祖的整棵兑换树 */
data class SpiritTree(
    val spiritId: String,
    val root: Int,
    val nodes: List<TreeItem>,
) {
    fun node(index: Int): TreeItem? = nodes.firstOrNull { it.index == index }

    val totalCandles: Int get() = nodes.sumOf { it.candles }
    val totalHearts: Int get() = nodes.sumOf { it.hearts }
    val totalAscended: Int get() = nodes.sumOf { it.ascended }

    /** 全收要多少（一句话） */
    fun totalText(): String = buildString {
        append("全收：白蜡 ").append(totalCandles)
        if (totalHearts > 0) append(" · 爱心 ").append(totalHearts)
        if (totalAscended > 0) append(" · 升华 ").append(totalAscended)
    }

    /**
     * 按"离根几步"分层，方便一层一层地画出来。
     * 认不出的下标一律当第 0 层，保证每个节点都会出现（不会漏画）。
     */
    fun levels(): List<List<TreeItem>> {
        val byIndex = nodes.associateBy { it.index }
        val depth = mutableMapOf(root to 0)
        var frontier = listOf(root)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Int>()
            frontier.forEach { index ->
                val current = depth[index] ?: 0
                byIndex[index]?.kids?.forEach { kid ->
                    if (kid !in depth) {
                        depth[kid] = current + 1
                        next += kid
                    }
                }
            }
            frontier = next
        }
        return nodes
            .groupBy { depth[it.index] ?: 0 }
            .toSortedMap()
            .values
            .toList()
    }

    /** 从根到某个节点的那条路（用来解释"这件东西怎么来的"） */
    fun pathTo(index: Int): List<TreeItem> {
        val byIndex = nodes.associateBy { it.index }
        val parents = mutableMapOf<Int, Int>()
        nodes.forEach { node -> node.kids.forEach { kid -> parents.putIfAbsent(kid, node.index) } }
        val path = mutableListOf<TreeItem>()
        var cursor: Int? = index
        val guard = mutableSetOf<Int>()
        while (cursor != null && cursor !in guard) {
            guard += cursor
            byIndex[cursor]?.let { path += it }
            cursor = parents[cursor]
        }
        return path.reversed()
    }
}

object ExchangeTreeParser {

    /** 解析不出来就返回空列表（界面显示"暂无兑换树"），绝不崩 */
    fun parse(text: String): List<SpiritTree> {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("trees") ?: return emptyList()
        val result = mutableListOf<SpiritTree>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            parseTree(obj)?.let { result += it }
        }
        return result
    }

    private fun parseTree(obj: JSONObject): SpiritTree? {
        val spiritId = obj.optString("spirit")
        val nodesArray = obj.optJSONArray("nodes") ?: return null
        val nodes = mutableListOf<TreeItem>()
        for (index in 0 until nodesArray.length()) {
            val node = nodesArray.optJSONObject(index) ?: continue
            val kidsArray = node.optJSONArray("kids")
            val kids = if (kidsArray == null) {
                emptyList()
            } else {
                (0 until kidsArray.length()).map { kidsArray.optInt(it) }
            }
            nodes += TreeItem(
                index = node.optInt("i"),
                itemGuid = node.optString("item"),
                zh = node.optString("zh"),
                en = node.optString("en"),
                type = node.optString("type"),
                icon = node.optString("icon"),
                candles = node.optInt("c"),
                hearts = node.optInt("h"),
                ascended = node.optInt("ac"),
                otherCurrency = node.optInt("ec") + node.optInt("sc") + node.optInt("sh"),
                kids = kids,
            )
        }
        if (nodes.isEmpty()) return null
        return SpiritTree(spiritId = spiritId, root = obj.optInt("root"), nodes = nodes)
    }
}
