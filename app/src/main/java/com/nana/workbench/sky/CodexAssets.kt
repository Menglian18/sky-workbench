package com.nana.workbench.sky

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 图鉴里的一件物品（1.1 改进 · C3：带"游戏内分类"和"图标网址"）。
 */
data class CodexEntry(
    val guid: String,
    val name: String,
    val nameZh: String,
    val partZh: String,
    val type: String,
    val icon: String,
    val candles: Int = 0,
    val hearts: Int = 0,
    val ascended: Int = 0,
    val spiritId: String = "",
) {
    /**
     * 界面上显示的名字（1.2.2 改进 · 第 2 条：只要中文、不要英文）。
     *
     * 名字在生成数据时就已经翻成中文（tools/translate_items.py），这里只管取用；
     * 万一哪条没有中文名，也**不会退回英文**，而是显示"未知物品"。
     */
    val displayName: String get() = nameZh.ifBlank { partZh }.trim().ifEmpty { "未知物品" }

    val category: CodexCategory get() = CodexCategory.fromType(type)
}

/** 图鉴 + 兑换树的整包数据 */
data class CodexData(
    val entries: List<CodexEntry> = emptyList(),
    val trees: Map<String, SpiritTree> = emptyMap(),
    /** 先祖 guid → 显示名 */
    val spiritNames: Map<String, String> = emptyMap(),
    /** 名字（归一化后）→ 先祖 guid：把"正在复刻"的条目对上先祖要用 */
    val spiritIdByName: Map<String, String> = emptyMap(),
    /** 先祖 guid → 照片网址（1.2.2 第 3 条：复刻卡片上放先祖的照片）。只存网址，图片联网现取 */
    val spiritImages: Map<String, String> = emptyMap(),
)

/**
 * 从安装包自带的 assets 里读图鉴与兑换树。
 *
 * 这是**只读本地文件、不联网**的一步；图片是界面显示时才去联网取的（见 RemoteImage）。
 * 解析失败一律返回空数据（界面显示"数据读不出来"），不崩。
 */
class CodexAssets(private val context: Context) {

    suspend fun load(): CodexData = withContext(Dispatchers.IO) {
        val entries = runCatching { parseEntries(read("codex/items.json")) }.getOrDefault(emptyList())
        val trees = runCatching {
            ExchangeTreeParser.parse(read("codex/trees.json")).associateBy { it.spiritId }
        }.getOrDefault(emptyMap())
        val parsed = runCatching { parseSpirits(read("codex/spirits.json")) }.getOrDefault(emptyMap())

        // 1.2.3 更新：把从 wiki 抓来的"旅行先祖官方中英名"并进来。
        // 这批名字正好补上了原来对不上的那批（蟹语者、踏舞孩童、远古…记忆…）。
        val pairs = runCatching { parseNamePairs(read("codex/spirit-name-pairs.json")) }
            .getOrDefault(emptyMap())
        val englishToGuid = parsed.entries
            .filter { (_, pair) -> pair.second.isNotBlank() }
            .associate { (guid, pair) -> SpiritNameIndex.normalizeWithBrackets(pair.second) to guid }

        val namesByGuid = mutableMapOf<String, MutableList<String>>()
        parsed.forEach { (guid, pair) ->
            namesByGuid.getOrPut(guid) { mutableListOf() }.addAll(listOf(pair.first, pair.second))
        }
        pairs.forEach { (english, chinese) ->
            val guid = englishToGuid[SpiritNameIndex.normalizeWithBrackets(english)] ?: return@forEach
            namesByGuid.getOrPut(guid) { mutableListOf() }.add(chinese)
        }

        // 显示名：优先中文（原本没有中文名的，现在能借 wiki 的官方中文名补上）
        val spiritNames = namesByGuid.mapValues { (_, names) ->
            names.firstOrNull { name -> name.any { it in '\u4e00'..'\u9fff' } }
                ?: names.firstOrNull().orEmpty()
        }
        val index = SpiritNameIndex.buildNames(namesByGuid)
        val images = runCatching { parseSpiritImages(read("codex/spirits.json")) }
            .getOrDefault(emptyMap())
        CodexData(
            entries = entries,
            trees = trees,
            spiritNames = spiritNames,
            spiritIdByName = index,
            spiritImages = images,
        )
    }

    /** 旅行先祖的中英对照：{"English Name": "中文名"}（1.2.3 更新，从 BWIKI 抓的） */
    private fun parseNamePairs(text: String): Map<String, String> {
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        obj.keys().forEach { key ->
            val value = obj.optString(key)
            if (key.isNotBlank() && value.isNotBlank()) result[key] = value
        }
        return result
    }

    /** 先祖：guid → 照片网址（1.2.2 第 3 条） */
    private fun parseSpiritImages(text: String): Map<String, String> {
        val array = runCatching { JSONArray(text) }.getOrNull()
            ?: runCatching { JSONObject(text).optJSONArray("items") }.getOrNull()
            ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val guid = obj.optString("guid")
            val url = obj.optString("imageUrl")
            if (guid.isNotEmpty() && url.isNotEmpty()) result[guid] = url
        }
        return result
    }

    /** 先祖：guid →（中文名, 英文名） */
    private fun parseSpirits(text: String): Map<String, Pair<String, String>> {
        val array = runCatching { JSONArray(text) }.getOrNull()
            ?: runCatching { JSONObject(text).optJSONArray("items") }.getOrNull()
            ?: return emptyMap()
        val result = mutableMapOf<String, Pair<String, String>>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val guid = obj.optString("guid")
            if (guid.isEmpty()) continue
            result[guid] = obj.optString("nameZh") to obj.optString("nameEn")
        }
        return result
    }

    private fun read(path: String): String =
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun parseEntries(text: String): List<CodexEntry> {
        val array = runCatching { JSONArray(text) }.getOrNull()
            ?: runCatching { JSONObject(text).optJSONArray("items") }.getOrNull()
            ?: return emptyList()

        val result = mutableListOf<CodexEntry>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val guid = obj.optString("guid")
            if (guid.isEmpty()) continue
            result += CodexEntry(
                guid = guid,
                name = obj.optString("nameEn"),
                nameZh = obj.optString("nameZh"),
                partZh = obj.optString("partZh"),
                type = obj.optString("type"),
                icon = obj.optString("icon"),
                candles = obj.optInt("candles"),
                hearts = obj.optInt("hearts"),
                ascended = obj.optInt("ascended"),
                spiritId = obj.optString("spiritId"),
            )
        }
        return result
    }
}
