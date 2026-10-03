package com.nana.workbench.data.backup

import com.nana.workbench.data.db.AppStateEntity
import com.nana.workbench.data.db.MemoEntity
import com.nana.workbench.data.db.NumberRecordEntity
import com.nana.workbench.data.db.NumberTableEntity
import com.nana.workbench.data.db.SkyAccountEntity
import com.nana.workbench.data.db.SkyHoldingEntity
import com.nana.workbench.data.db.SkySeasonEntity
import com.nana.workbench.data.db.SkyTravelingSpiritEntity
import com.nana.workbench.data.db.SkyWishEntity
import com.nana.workbench.data.db.StudyCardEntity
import com.nana.workbench.data.db.StudyReviewLogEntity
import com.nana.workbench.data.db.TodoEntity
import org.json.JSONArray
import org.json.JSONObject

/** 一次备份所包含的全部内容。 */
data class BackupSnapshot(
    val exportedAt: Long,
    val version: Int = BackupCodec.CURRENT_VERSION,
    val todos: List<TodoEntity> = emptyList(),
    val studyCards: List<StudyCardEntity> = emptyList(),
    val studyLogs: List<StudyReviewLogEntity> = emptyList(),
    val memos: List<MemoEntity> = emptyList(),
    val numberTables: List<NumberTableEntity> = emptyList(),
    val numberRecords: List<NumberRecordEntity> = emptyList(),
    val skyAccounts: List<SkyAccountEntity> = emptyList(),
    val skyWishes: List<SkyWishEntity> = emptyList(),
    val skyHolding: SkyHoldingEntity? = null,
    val skySpirits: List<SkyTravelingSpiritEntity> = emptyList(),
    val skySeasons: List<SkySeasonEntity> = emptyList(),
    val appState: List<AppStateEntity> = emptyList(),
) {
    /** 导入完成后给用户看的条目摘要（数字能一眼核对）。 */
    fun summary(): BackupSummary = BackupSummary(
        todos = todos.size,
        studyCards = studyCards.size,
        studyLogs = studyLogs.size,
        memos = memos.size,
        numberTables = numberTables.size,
        numberRecords = numberRecords.size,
        skyAccounts = skyAccounts.size,
        skyWishes = skyWishes.size,
        skySpirits = skySpirits.size,
        skySeasons = skySeasons.size,
    )
}

data class BackupSummary(
    val todos: Int,
    val studyCards: Int,
    val studyLogs: Int,
    val memos: Int,
    val numberTables: Int,
    val numberRecords: Int,
    val skyAccounts: Int,
    val skyWishes: Int,
    val skySpirits: Int,
    val skySeasons: Int,
) {
    fun describe(): String = buildString {
        append("计划 ").append(todos).append(" 条、")
        append("学习卡 ").append(studyCards).append(" 张、")
        append("备忘 ").append(memos).append(" 条、")
        append("表格 ").append(numberTables).append(" 张（").append(numberRecords).append(" 笔）、")
        append("光遇账号 ").append(skyAccounts).append(" 个、")
        append("心愿 ").append(skyWishes).append(" 条、")
        append("复刻 ").append(skySpirits).append(" 条、")
        append("季节 ").append(skySeasons).append(" 条")
    }
}

/**
 * 备份文件的读写。
 *
 * 用 JSON，**不加密**（本来就没存密码，见 PRD §9），文件名里带日期便于分辨新旧。
 * 写成纯函数（不碰安卓的文件接口），所以在电脑上就能测往返是否一致。
 */
object BackupCodec {

    const val CURRENT_VERSION = 1

    fun encode(snapshot: BackupSnapshot): String {
        val root = JSONObject()
        root.put("version", snapshot.version)
        root.put("exportedAt", snapshot.exportedAt)
        root.put("todos", encodeList(snapshot.todos, ::todoToJson))
        root.put("studyCards", encodeList(snapshot.studyCards, ::cardToJson))
        root.put("studyLogs", encodeList(snapshot.studyLogs, ::logToJson))
        root.put("memos", encodeList(snapshot.memos, ::memoToJson))
        root.put("numberTables", encodeList(snapshot.numberTables, ::tableToJson))
        root.put("numberRecords", encodeList(snapshot.numberRecords, ::recordToJson))
        root.put("skyAccounts", encodeList(snapshot.skyAccounts, ::accountToJson))
        root.put("skyWishes", encodeList(snapshot.skyWishes, ::wishToJson))
        snapshot.skyHolding?.let { root.put("skyHolding", holdingToJson(it)) }
        root.put("skySpirits", encodeList(snapshot.skySpirits, ::spiritToJson))
        root.put("skySeasons", encodeList(snapshot.skySeasons, ::seasonToJson))
        root.put("appState", encodeList(snapshot.appState, ::stateToJson))
        return root.toString()
    }

    /** 解析失败返回 null（界面据此提示"这个文件读不出来"）。 */
    fun decode(text: String): BackupSnapshot? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return runCatching {
            BackupSnapshot(
                exportedAt = root.optLong("exportedAt", 0L),
                version = root.optInt("version", CURRENT_VERSION),
                todos = decodeList(root.optJSONArray("todos")) { todoFromJson(it) },
                studyCards = decodeList(root.optJSONArray("studyCards")) { cardFromJson(it) },
                studyLogs = decodeList(root.optJSONArray("studyLogs")) { logFromJson(it) },
                memos = decodeList(root.optJSONArray("memos")) { memoFromJson(it) },
                numberTables = decodeList(root.optJSONArray("numberTables")) { tableFromJson(it) },
                numberRecords = decodeList(root.optJSONArray("numberRecords")) { recordFromJson(it) },
                skyAccounts = decodeList(root.optJSONArray("skyAccounts")) { accountFromJson(it) },
                skyWishes = decodeList(root.optJSONArray("skyWishes")) { wishFromJson(it) },
                skyHolding = root.optJSONObject("skyHolding")?.let { holdingFromJson(it) },
                skySpirits = decodeList(root.optJSONArray("skySpirits")) { spiritFromJson(it) },
                skySeasons = decodeList(root.optJSONArray("skySeasons")) { seasonFromJson(it) },
                appState = decodeList(root.optJSONArray("appState")) { stateFromJson(it) },
            )
        }.getOrNull()
    }

    /** 备份文件名的建议值（带日期，一眼看出新旧）。 */
    fun suggestedFileName(dateKey: String): String = "娜娜工作台备份-$dateKey.json"

    private fun <T> encodeList(items: List<T>, convert: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        items.forEach { array.put(convert(it)) }
        return array
    }

    private fun <T> decodeList(array: JSONArray?, convert: (JSONObject) -> T?): List<T> {
        if (array == null) return emptyList()
        val result = mutableListOf<T>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            convert(obj)?.let { result += it }
        }
        return result
    }

    // ── 逐张表的读写 ──────────────────────────────────────────────────────────

    private fun todoToJson(item: TodoEntity) = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("note", item.note)
        put("priority", item.priority); item.timePoint?.let { put("timePoint", it) }
        put("done", item.done); put("originalDate", item.originalDate); put("dueDate", item.dueDate)
        put("postponedCount", item.postponedCount); item.doneAt?.let { put("doneAt", it) }
        put("sortOrder", item.sortOrder); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
    }

    private fun todoFromJson(obj: JSONObject) = TodoEntity(
        id = obj.optLong("id"),
        title = obj.optString("title"),
        note = obj.optString("note"),
        priority = obj.optInt("priority"),
        timePoint = obj.optString("timePoint").takeIf { it.isNotEmpty() },
        done = obj.optBoolean("done"),
        originalDate = obj.optString("originalDate"),
        dueDate = obj.optString("dueDate"),
        postponedCount = obj.optInt("postponedCount"),
        doneAt = if (obj.has("doneAt")) obj.optLong("doneAt") else null,
        sortOrder = obj.optInt("sortOrder"),
        createdAt = obj.optLong("createdAt"),
        updatedAt = obj.optLong("updatedAt"),
    )

    private fun cardToJson(item: StudyCardEntity) = JSONObject().apply {
        put("id", item.id); put("front", item.front); put("back", item.back); put("note", item.note)
        put("phase", item.phase); put("stability", item.stability); put("difficulty", item.difficulty)
        put("reps", item.reps); put("lapses", item.lapses)
        item.lastReviewedAt?.let { put("lastReviewedAt", it) }
        put("dueAt", item.dueAt); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
    }

    private fun cardFromJson(obj: JSONObject) = StudyCardEntity(
        id = obj.optLong("id"),
        front = obj.optString("front"),
        back = obj.optString("back"),
        note = obj.optString("note"),
        phase = obj.optInt("phase"),
        stability = obj.optDouble("stability", 0.0),
        difficulty = obj.optDouble("difficulty", 0.0),
        reps = obj.optInt("reps"),
        lapses = obj.optInt("lapses"),
        lastReviewedAt = if (obj.has("lastReviewedAt")) obj.optLong("lastReviewedAt") else null,
        dueAt = obj.optLong("dueAt"),
        createdAt = obj.optLong("createdAt"),
        updatedAt = obj.optLong("updatedAt"),
    )

    private fun logToJson(item: StudyReviewLogEntity) = JSONObject().apply {
        put("id", item.id); put("cardId", item.cardId); put("rating", item.rating)
        put("reviewedAt", item.reviewedAt); put("elapsedDays", item.elapsedDays)
        put("scheduledDays", item.scheduledDays)
    }

    private fun logFromJson(obj: JSONObject) = StudyReviewLogEntity(
        id = obj.optLong("id"),
        cardId = obj.optLong("cardId"),
        rating = obj.optInt("rating"),
        reviewedAt = obj.optLong("reviewedAt"),
        elapsedDays = obj.optDouble("elapsedDays", 0.0),
        scheduledDays = obj.optInt("scheduledDays"),
    )

    private fun memoToJson(item: MemoEntity) = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("content", item.content)
        put("category", item.category); put("pinned", item.pinned); put("sortOrder", item.sortOrder)
        put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
    }

    private fun memoFromJson(obj: JSONObject) = MemoEntity(
        id = obj.optLong("id"),
        title = obj.optString("title"),
        content = obj.optString("content"),
        category = obj.optString("category"),
        pinned = obj.optBoolean("pinned"),
        sortOrder = obj.optInt("sortOrder"),
        createdAt = obj.optLong("createdAt"),
        updatedAt = obj.optLong("updatedAt"),
    )

    private fun tableToJson(item: NumberTableEntity) = JSONObject().apply {
        put("id", item.id); put("name", item.name); put("unit", item.unit)
        put("decimals", item.decimals); put("allowMultiplePerDay", item.allowMultiplePerDay)
        put("sortOrder", item.sortOrder)
    }

    private fun tableFromJson(obj: JSONObject) = NumberTableEntity(
        id = obj.optLong("id"),
        name = obj.optString("name"),
        unit = obj.optString("unit"),
        decimals = obj.optInt("decimals"),
        allowMultiplePerDay = obj.optBoolean("allowMultiplePerDay"),
        sortOrder = obj.optInt("sortOrder"),
    )

    private fun recordToJson(item: NumberRecordEntity) = JSONObject().apply {
        put("id", item.id); put("tableId", item.tableId); put("date", item.date)
        put("value", item.value); put("note", item.note)
        put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
    }

    private fun recordFromJson(obj: JSONObject) = NumberRecordEntity(
        id = obj.optLong("id"),
        tableId = obj.optLong("tableId"),
        date = obj.optString("date"),
        value = obj.optDouble("value", 0.0),
        note = obj.optString("note"),
        createdAt = obj.optLong("createdAt"),
        updatedAt = obj.optLong("updatedAt"),
    )

    private fun accountToJson(item: SkyAccountEntity) = JSONObject().apply {
        put("id", item.id); put("nickname", item.nickname); put("loginMethod", item.loginMethod)
        put("server", item.server); put("binding", item.binding); put("note", item.note)
        put("sortOrder", item.sortOrder)
    }

    private fun accountFromJson(obj: JSONObject) = SkyAccountEntity(
        id = obj.optLong("id"),
        nickname = obj.optString("nickname"),
        loginMethod = obj.optString("loginMethod"),
        server = obj.optString("server"),
        binding = obj.optString("binding"),
        note = obj.optString("note"),
        sortOrder = obj.optInt("sortOrder"),
    )

    private fun wishToJson(item: SkyWishEntity) = JSONObject().apply {
        put("id", item.id); put("itemGuid", item.itemGuid); put("itemName", item.itemName)
        put("spiritName", item.spiritName); put("costCandle", item.costCandle)
        put("costHeart", item.costHeart); put("costAscended", item.costAscended)
        put("owned", item.owned); put("obtained", item.obtained); put("sortOrder", item.sortOrder)
    }

    private fun wishFromJson(obj: JSONObject) = SkyWishEntity(
        id = obj.optLong("id"),
        itemGuid = obj.optString("itemGuid"),
        itemName = obj.optString("itemName"),
        spiritName = obj.optString("spiritName"),
        costCandle = obj.optInt("costCandle"),
        costHeart = obj.optInt("costHeart"),
        costAscended = obj.optInt("costAscended"),
        owned = obj.optBoolean("owned"),
        obtained = obj.optBoolean("obtained"),
        sortOrder = obj.optInt("sortOrder"),
    )

    private fun holdingToJson(item: SkyHoldingEntity) = JSONObject().apply {
        put("id", item.id); put("candles", item.candles)
        put("hearts", item.hearts); put("ascended", item.ascended)
    }

    private fun holdingFromJson(obj: JSONObject) = SkyHoldingEntity(
        id = obj.optInt("id", SkyHoldingEntity.SINGLETON_ID),
        candles = obj.optInt("candles"),
        hearts = obj.optInt("hearts"),
        ascended = obj.optInt("ascended"),
    )

    private fun spiritToJson(item: SkyTravelingSpiritEntity) = JSONObject().apply {
        put("id", item.id); put("spiritName", item.spiritName); put("type", item.type)
        put("startDate", item.startDate); put("endDate", item.endDate)
        put("costCandle", item.costCandle); put("costHeart", item.costHeart)
        put("costAscended", item.costAscended); put("items", item.items)
        put("source", item.source); put("fetchedAt", item.fetchedAt)
        put("manuallyEdited", item.manuallyEdited)
    }

    private fun spiritFromJson(obj: JSONObject) = SkyTravelingSpiritEntity(
        id = obj.optLong("id"),
        spiritName = obj.optString("spiritName"),
        type = obj.optString("type"),
        startDate = obj.optString("startDate"),
        endDate = obj.optString("endDate"),
        costCandle = obj.optInt("costCandle"),
        costHeart = obj.optInt("costHeart"),
        costAscended = obj.optInt("costAscended"),
        items = obj.optString("items"),
        source = obj.optString("source"),
        fetchedAt = obj.optLong("fetchedAt"),
        manuallyEdited = obj.optBoolean("manuallyEdited"),
    )

    private fun seasonToJson(item: SkySeasonEntity) = JSONObject().apply {
        put("id", item.id); put("name", item.name); put("startDate", item.startDate)
        put("endDate", item.endDate); put("fetchedAt", item.fetchedAt)
        put("manuallyEdited", item.manuallyEdited)
    }

    private fun seasonFromJson(obj: JSONObject) = SkySeasonEntity(
        id = obj.optLong("id"),
        name = obj.optString("name"),
        startDate = obj.optString("startDate"),
        endDate = obj.optString("endDate"),
        fetchedAt = obj.optLong("fetchedAt"),
        manuallyEdited = obj.optBoolean("manuallyEdited"),
    )

    private fun stateToJson(item: AppStateEntity) = JSONObject().apply {
        put("key", item.key); put("value", item.value)
    }

    private fun stateFromJson(obj: JSONObject) = AppStateEntity(
        key = obj.optString("key"),
        value = obj.optString("value"),
    )
}
