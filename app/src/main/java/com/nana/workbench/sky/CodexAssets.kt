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
    /** 界面上显示的名字：中文优先 */
    val displayName: String get() = nameZh.ifBlank { partZh.ifBlank { name.ifBlank { "未知物品" } } }

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
        val spiritNames = parsed.mapValues { (_, pair) -> pair.first.ifBlank { pair.second } }
        // 中文名与英文名**都**建进索引：复刻数据里是中文名，图鉴里可能是英文名，两边都得能对上
        // （1.2 第 1 条就是这里没建好，导致"看兑换树"点开什么也没有）
        val index = SpiritNameIndex.buildNames(
            parsed.mapValues { (_, pair) -> listOf(pair.first, pair.second) },
        )
        CodexData(entries = entries, trees = trees, spiritNames = spiritNames, spiritIdByName = index)
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
