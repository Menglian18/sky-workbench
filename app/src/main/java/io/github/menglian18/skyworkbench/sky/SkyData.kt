package io.github.menglian18.skyworkbench.sky

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 图鉴里的一件物品（价格是"从树根走到它这一路的花费之和"）。 */
data class CodexItem(
    val guid: String,
    val nameZh: String,
    val nameEn: String,
    val type: String,
    val partZh: String,
    val spiritId: String,
    val spiritNameZh: String,
    val spiritNameEn: String,
    val seasonZh: String,
    val candles: Int,
    val hearts: Int,
    val ascended: Int,
)

/** 图鉴里的一个先祖（含"全收要多少"）。 */
data class CodexSpirit(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val type: String,
    val seasonZh: String,
    val totalCandles: Int,
    val totalHearts: Int,
    val totalAscended: Int,
    val itemCount: Int,
) {
    /** 有中文名就用中文，没有就退回英文（并让界面标出来） */
    val displayName: String get() = nameZh.ifBlank { nameEn }
    val hasChineseName: Boolean get() = nameZh.isNotBlank()
}

/**
 * 离线图鉴：数据在构建期生成、打包进 App（见 tools/build_codex.py），
 * 所以打开就能查，不用联网、不转圈。
 */
class CodexRepository(private val context: Context) {

    private var cachedItems: List<CodexItem>? = null
    private var cachedSpirits: List<CodexSpirit>? = null

    fun spirits(): List<CodexSpirit> = cachedSpirits ?: synchronized(this) {
        cachedSpirits ?: loadSpirits().also { cachedSpirits = it }
    }

    fun items(): List<CodexItem> = cachedItems ?: synchronized(this) {
        cachedItems ?: loadItems().also { cachedItems = it }
    }

    fun findSpirit(id: String?): CodexSpirit? =
        if (id == null) null else spirits().firstOrNull { it.id == id }

    fun searchItems(query: String, spiritId: String? = null): List<CodexItem> {
        val q = query.trim()
        return items().filter { item ->
            val matchSpirit = spiritId == null || item.spiritId == spiritId
            val matchQuery = q.isEmpty() ||
                item.nameZh.contains(q, ignoreCase = true) ||
                item.nameEn.contains(q, ignoreCase = true) ||
                item.spiritNameZh.contains(q, ignoreCase = true) ||
                item.spiritNameEn.contains(q, ignoreCase = true) ||
                item.seasonZh.contains(q, ignoreCase = true) ||
                item.partZh.contains(q, ignoreCase = true)
            matchSpirit && matchQuery
        }
    }

    private fun readAsset(name: String): String =
        context.assets.open("codex/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun loadSpirits(): List<CodexSpirit> {
        val array = runCatching { JSONArray(readAsset("spirits.json")) }.getOrNull() ?: return emptyList()
        val list = ArrayList<CodexSpirit>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            list += CodexSpirit(
                id = obj.optString("id"),
                nameZh = obj.optString("nameZh"),
                nameEn = obj.optString("nameEn"),
                type = obj.optString("type"),
                seasonZh = obj.optString("seasonZh"),
                totalCandles = obj.optInt("totalCandles"),
                totalHearts = obj.optInt("totalHearts"),
                totalAscended = obj.optInt("totalAscended"),
                itemCount = obj.optInt("itemCount"),
            )
        }
        return list.sortedWith(compareByDescending<CodexSpirit> { it.hasChineseName }.thenBy { it.displayName })
    }

    private fun loadItems(): List<CodexItem> {
        val array = runCatching { JSONArray(readAsset("items.json")) }.getOrNull() ?: return emptyList()
        val list = ArrayList<CodexItem>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            list += CodexItem(
                guid = obj.optString("guid"),
                nameZh = obj.optString("nameZh"),
                nameEn = obj.optString("nameEn"),
                type = obj.optString("type"),
                partZh = obj.optString("partZh"),
                spiritId = obj.optString("spiritId"),
                spiritNameZh = obj.optString("spiritNameZh"),
                spiritNameEn = obj.optString("spiritNameEn"),
                seasonZh = obj.optString("seasonZh"),
                candles = obj.optInt("candles"),
                hearts = obj.optInt("hearts"),
                ascended = obj.optInt("ascended"),
            )
        }
        return list
    }
}

/**
 * 联网抓光遇数据。
 *
 * 只抓两样（PRD 定下的范围）：复刻页的 wikitext、季节列表。
 * 这个 WIKI 会限流（返回 567 之类），所以必须带重试与退避；抓不到就由上层保留旧数据。
 */
class SkyFetchClient(private val userAgent: String = "sky-workbench/1.0") {

    private val api = "https://wiki.biligame.com/sky/api.php"

    fun fetchTravelingSpiritWikitext(): String =
        request(mapOf(
            "action" to "parse",
            "page" to "旅行先祖回归记录",
            "prop" to "wikitext",
            "format" to "json",
        )).let { body ->
            runCatching { JSONObject(body).getJSONObject("parse").getJSONObject("wikitext").getString("*") }
                .getOrElse { throw IOException("复刻页内容格式不对") }
        }

    fun fetchSeasonsJson(): String =
        request(mapOf(
            "action" to "ask",
            "format" to "json",
            "query" to "[[分类:奇妙之旅]]|?开启时间|?结束时间|limit=200",
        ))

    /**
     * 节日活动列表（1.3.0 新增）。WIKI 上「分类:节日活动」有 60 多个活动页，
     * 每个都带「开启时间 / 结束时间」，所以能自动抓，不用手填。
     */
    fun fetchEventsJson(): String =
        request(mapOf(
            "action" to "ask",
            "format" to "json",
            "query" to "[[分类:节日活动]]|?开启时间|?结束时间|limit=200",
        ))

    private fun request(params: Map<String, String>, attempts: Int = 3): String {
        val query = params.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        var lastError: Exception? = null
        for (attempt in 1..attempts) {
            try {
                val connection = (URL("$api?$query").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", userAgent)
                    connectTimeout = 15_000
                    readTimeout = 20_000
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                lastError = IOException("服务器返回 $code")
                connection.disconnect()
            } catch (error: Exception) {
                lastError = error
            }
            if (attempt < attempts) {
                Thread.sleep(1500L * attempt)
            }
        }
        throw lastError ?: IOException("请求失败")
    }
}
