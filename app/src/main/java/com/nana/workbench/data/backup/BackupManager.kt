package com.nana.workbench.data.backup

import android.content.Context
import android.net.Uri
import com.nana.workbench.App
import com.nana.workbench.data.db.AppStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** 占用空间。 */
data class DataUsage(val databaseBytes: Long, val cacheBytes: Long) {
    val totalBytes: Long get() = databaseBytes + cacheBytes
    fun describe(): String = "数据库 ${formatSize(databaseBytes)} · 缓存 ${formatSize(cacheBytes)}"
}

/** 一键清理之前先告诉用户"能清出多少"。 */
data class CleanPreview(val cacheBytes: Long, val spirits: Int, val seasons: Int)

/** 清理结果。 */
data class CleanResult(val freedBytes: Long, val spirits: Int, val seasons: Int)

/** 导入的结果：恢复进来的内容 + 导入前自动保留下来的那份。 */
data class ImportResult(val restored: BackupSummary, val safety: BackupSummary?)

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

/**
 * 备份与清理。
 *
 * 备份走系统自带的文件选择器（SAF），所以**不需要存储权限**：
 * 用户自己选存在哪、从哪读。
 */
class BackupManager(private val context: Context) {

    private val database = App.from(context).database

    // ── 导出 / 导入 ───────────────────────────────────────────────────────────

    suspend fun exportTo(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val snapshot = createSnapshot()
        val text = BackupCodec.encode(snapshot)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("写不进选中的位置")
        database.appStateDao().put(
            AppStateEntity(KEY_LAST_EXPORT, System.currentTimeMillis().toString()),
        )
        snapshot.summary()
    }

    /**
     * 读不出来时返回 null（界面提示"这个文件读不出来"）。
     *
     * **导入前会先把手机现状写进 App 内部的一个安全文件**，界面据此提供"撤销这次导入"。
     * 这样即使用户选错文件，也不会把手机上的数据弄丢 —— 这是界面上对用户的承诺，必须真的做到。
     */
    suspend fun importFrom(uri: Uri): ImportResult? = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return@withContext null
        val snapshot = BackupCodec.decode(text) ?: return@withContext null

        val safety = saveSafetySnapshot()
        ImportResult(restored = restore(snapshot), safety = safety)
    }

    /** 把"现在手机上是什么样"写进安全文件。 */
    private suspend fun saveSafetySnapshot(): BackupSummary? = runCatching {
        val snapshot = createSnapshot()
        val file = safetyFile()
        file.parentFile?.mkdirs()
        file.writeText(BackupCodec.encode(snapshot), Charsets.UTF_8)
        snapshot.summary()
    }.getOrNull()

    suspend fun hasSafetySnapshot(): Boolean = withContext(Dispatchers.IO) { safetyFile().exists() }

    /** 撤销这次导入：把导入前自动存的那份恢复回来（只能用一次）。 */
    suspend fun undoImport(): BackupSummary? = withContext(Dispatchers.IO) {
        val file = safetyFile()
        if (!file.exists()) return@withContext null
        val snapshot = BackupCodec.decode(file.readText(Charsets.UTF_8)) ?: return@withContext null
        val summary = restore(snapshot)
        file.delete()
        summary
    }

    private fun safetyFile(): java.io.File =
        java.io.File(context.filesDir, "safety/last-import-backup.json")

    suspend fun createSnapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        BackupSnapshot(
            exportedAt = System.currentTimeMillis(),
            todos = database.todoDao().listAll(),
            studyCards = database.studyDao().listAll(),
            studyLogs = database.studyDao().listAllLogs(),
            memos = database.memoDao().listAll(),
            numberTables = database.numberDao().listTables(),
            numberRecords = database.numberDao().listAllRecords(),
            skyAccounts = database.skyDao().listAccounts(),
            skyWishes = database.skyDao().listWishes(),
            skyHolding = database.skyDao().findHolding(),
            skySpirits = database.skyDao().listTravelingSpirits(),
            skySeasons = database.skyDao().listSeasons(),
            appState = listOfNotNull(
                database.appStateDao().get(KEY_LAST_EXPORT)?.let { AppStateEntity(KEY_LAST_EXPORT, it) },
            ),
        )
    }

    /** 用备份内容**替换**手机上现有的数据（界面上会先提示、并先自动存一份现状）。 */
    suspend fun restore(snapshot: BackupSnapshot): BackupSummary = withContext(Dispatchers.IO) {
        wipeUserData()
        database.todoDao().insertAll(snapshot.todos)
        database.studyDao().insertAll(snapshot.studyCards)
        database.studyDao().insertAllLogs(snapshot.studyLogs)
        database.memoDao().insertAll(snapshot.memos)
        database.numberDao().insertTables(snapshot.numberTables)
        database.numberDao().insertRecords(snapshot.numberRecords)
        database.skyDao().insertAccounts(snapshot.skyAccounts)
        database.skyDao().insertWishes(snapshot.skyWishes)
        snapshot.skyHolding?.let { database.skyDao().saveHolding(it) }
        database.skyDao().insertTravelingSpirits(snapshot.skySpirits)
        database.skyDao().insertSeasons(snapshot.skySeasons)
        snapshot.summary()
    }

    // ── 一键清理 ──────────────────────────────────────────────────────────────

    suspend fun previewClean(): CleanPreview = withContext(Dispatchers.IO) {
        CleanPreview(
            cacheBytes = cacheSize(),
            spirits = database.skyDao().listTravelingSpirits().count { !it.manuallyEdited },
            seasons = database.skyDao().listSeasons().count { !it.manuallyEdited },
        )
    }

    /**
     * 真正清理。**只清缓存和抓来的光遇数据**（手动改过的不动），
     * 用户自己记的计划、卡片、备忘、表格一条都不碰。
     */
    suspend fun cleanJunk(): CleanResult = withContext(Dispatchers.IO) {
        val before = cacheSize()
        clearCache()
        val spirits = database.skyDao().listTravelingSpirits().count { !it.manuallyEdited }
        val seasons = database.skyDao().listSeasons().count { !it.manuallyEdited }
        database.skyDao().deleteFetchedTravelingSpirits()
        database.skyDao().deleteFetchedSeasons()
        database.appStateDao().put(
            AppStateEntity(KEY_LAST_CLEAN, System.currentTimeMillis().toString()),
        )
        CleanResult(freedBytes = (before - cacheSize()).coerceAtLeast(0L), spirits = spirits, seasons = seasons)
    }

    // ── 占用空间与清空 ────────────────────────────────────────────────────────

    suspend fun usage(): DataUsage = withContext(Dispatchers.IO) {
        DataUsage(databaseBytes = databaseSize(), cacheBytes = cacheSize())
    }

    suspend fun lastExportAt(): Long? = withContext(Dispatchers.IO) {
        database.appStateDao().get(KEY_LAST_EXPORT)?.toLongOrNull()
    }

    /** 清空全部数据（界面上必须先手动打出"清空"两个字）。 */
    suspend fun clearEverything(): Unit = withContext(Dispatchers.IO) {
        wipeUserData()
    }

    private suspend fun wipeUserData() {
        database.todoDao().deleteAll()
        database.studyDao().deleteAll()
        database.studyDao().deleteAllLogs()
        database.memoDao().deleteAll()
        database.numberDao().deleteAllRecords()
        database.numberDao().deleteAllTables()
        database.skyDao().deleteAllAccounts()
        database.skyDao().deleteAllWishes()
        database.skyDao().deleteAllTravelingSpirits()
        database.skyDao().deleteAllSeasons()
        database.skyDao().saveHolding(com.nana.workbench.data.db.SkyHoldingEntity())
    }

    private fun databaseSize(): Long =
        context.getDatabasePath(com.nana.workbench.data.db.NanaDatabase.DB_NAME)?.length() ?: 0L

    private fun cacheSize(): Long = cacheFiles().sumOf { it.length() }

    private fun cacheFiles(): List<java.io.File> =
        runCatching { context.cacheDir.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun clearCache() {
        runCatching {
            context.cacheDir.listFiles()?.forEach { file -> file.deleteRecursively() }
        }
    }

    companion object {
        const val KEY_LAST_EXPORT = "last_export_at"
        const val KEY_LAST_CLEAN = "last_clean_at"
    }
}
