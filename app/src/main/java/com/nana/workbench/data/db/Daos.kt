package com.nana.workbench.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT * FROM todo_item WHERE dueDate = :date AND done = 0 ORDER BY sortOrder, id")
    fun watchForDate(date: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_item WHERE dueDate = :date AND done = 1 ORDER BY doneAt DESC")
    fun watchDoneForDate(date: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_item WHERE dueDate = :date AND done = 0 ORDER BY sortOrder, id")
    suspend fun listForDate(date: String): List<TodoEntity>

    /** 还没做完、且不在今天的条目——顺延要用。 */
    @Query("SELECT * FROM todo_item WHERE done = 0 AND dueDate < :date ORDER BY dueDate, sortOrder, id")
    suspend fun listUnfinishedBefore(date: String): List<TodoEntity>

    @Query("SELECT COUNT(*) FROM todo_item WHERE dueDate = :date AND done = 0")
    fun watchOpenCount(date: String): Flow<Int>

    @Query("SELECT * FROM todo_item WHERE done = 0 ORDER BY dueDate, sortOrder, id")
    suspend fun listAllOpen(): List<TodoEntity>

    @Query("SELECT * FROM todo_item")
    suspend fun listAll(): List<TodoEntity>

    @Insert
    suspend fun insert(item: TodoEntity): Long

    @Insert
    suspend fun insertAll(items: List<TodoEntity>)

    @Update
    suspend fun update(item: TodoEntity)

    @Delete
    suspend fun delete(item: TodoEntity)

    @Query("DELETE FROM todo_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM todo_item WHERE dueDate = :date AND done = 1")
    suspend fun deleteDoneOn(date: String)

    @Query("DELETE FROM todo_item")
    suspend fun deleteAll()
}

@Dao
interface StudyDao {

    /** 今天该复习的（到期的，不含暂停）。 */
    @Query("SELECT * FROM study_card WHERE phase != 3 AND dueAt <= :now ORDER BY dueAt, id")
    suspend fun listDue(now: Long): List<StudyCardEntity>

    @Query("SELECT * FROM study_card WHERE phase != 3 AND dueAt <= :now ORDER BY dueAt, id")
    fun watchDue(now: Long): Flow<List<StudyCardEntity>>

    @Query("SELECT * FROM study_card ORDER BY updatedAt DESC")
    fun watchAll(): Flow<List<StudyCardEntity>>

    @Query(
        "SELECT * FROM study_card WHERE front LIKE '%' || :query || '%' " +
            "OR back LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' " +
            "ORDER BY updatedAt DESC"
    )
    fun search(query: String): Flow<List<StudyCardEntity>>

    @Query("SELECT * FROM study_card")
    suspend fun listAll(): List<StudyCardEntity>

    @Query("SELECT * FROM study_card WHERE id = :id")
    suspend fun findById(id: Long): StudyCardEntity?

    @Query("SELECT COUNT(*) FROM study_card")
    fun watchTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM study_card WHERE phase != 3 AND dueAt <= :now")
    fun watchDueCount(now: Long): Flow<Int>

    /** "已掌握"的定义：复习中、且稳定度超过 21 天。 */
    @Query("SELECT COUNT(*) FROM study_card WHERE phase = 2 AND stability >= 21")
    fun watchMasteredCount(): Flow<Int>

    @Insert
    suspend fun insert(card: StudyCardEntity): Long

    @Insert
    suspend fun insertAll(cards: List<StudyCardEntity>)

    @Update
    suspend fun update(card: StudyCardEntity)

    @Query("DELETE FROM study_card WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE study_card SET phase = 3, updatedAt = :now WHERE id = :id")
    suspend fun suspend(id: Long, now: Long)

    @Query("UPDATE study_card SET phase = 0, updatedAt = :now WHERE id = :id")
    suspend fun resume(id: Long, now: Long)

    @Query("DELETE FROM study_card")
    suspend fun deleteAll()

    // 复习记录
    @Insert
    suspend fun insertLog(log: StudyReviewLogEntity)

    @Query("SELECT * FROM study_review_log WHERE cardId = :cardId ORDER BY reviewedAt DESC")
    suspend fun logsFor(cardId: Long): List<StudyReviewLogEntity>

    @Query("SELECT * FROM study_review_log")
    suspend fun listAllLogs(): List<StudyReviewLogEntity>

    /** 从某个时刻起复习了多少张（首页"今天已完成"用）。 */
    @Query("SELECT COUNT(*) FROM study_review_log WHERE reviewedAt >= :since")
    fun watchReviewsSince(since: Long): Flow<Int>

    @Insert
    suspend fun insertAllLogs(logs: List<StudyReviewLogEntity>)

    @Query("DELETE FROM study_review_log")
    suspend fun deleteAllLogs()
}

@Dao
interface MemoDao {

    @Query(
        "SELECT * FROM memo_item WHERE title LIKE '%' || :query || '%' " +
            "OR content LIKE '%' || :query || '%' " +
            "ORDER BY pinned DESC, sortOrder, updatedAt DESC"
    )
    fun search(query: String): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memo_item ORDER BY pinned DESC, sortOrder, updatedAt DESC")
    fun watchAll(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memo_item WHERE pinned = 1 ORDER BY sortOrder, updatedAt DESC LIMIT :limit")
    fun watchPinned(limit: Int): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memo_item")
    suspend fun listAll(): List<MemoEntity>

    @Query("SELECT * FROM memo_item WHERE id = :id")
    suspend fun findById(id: Long): MemoEntity?

    @Query("SELECT COUNT(*) FROM memo_item")
    fun watchCount(): Flow<Int>

    @Insert
    suspend fun insert(item: MemoEntity): Long

    @Insert
    suspend fun insertAll(items: List<MemoEntity>)

    @Update
    suspend fun update(item: MemoEntity)

    @Query("DELETE FROM memo_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memo_item")
    suspend fun deleteAll()
}

@Dao
interface NumberDao {

    @Query("SELECT * FROM number_table ORDER BY sortOrder, id")
    fun watchTables(): Flow<List<NumberTableEntity>>

    @Query("SELECT * FROM number_table ORDER BY sortOrder, id")
    suspend fun listTables(): List<NumberTableEntity>

    @Query("SELECT * FROM number_table WHERE id = :id")
    suspend fun findTable(id: Long): NumberTableEntity?

    @Insert
    suspend fun insertTable(table: NumberTableEntity): Long

    @Insert
    suspend fun insertTables(tables: List<NumberTableEntity>)

    @Update
    suspend fun updateTable(table: NumberTableEntity)

    @Query("DELETE FROM number_table WHERE id = :id")
    suspend fun deleteTable(id: Long)

    @Query("DELETE FROM number_table")
    suspend fun deleteAllTables()

    @Query("SELECT * FROM number_record WHERE tableId = :tableId ORDER BY date DESC, id DESC")
    fun watchRecords(tableId: Long): Flow<List<NumberRecordEntity>>

    @Query("SELECT * FROM number_record WHERE tableId = :tableId ORDER BY date DESC, id DESC")
    suspend fun listRecords(tableId: Long): List<NumberRecordEntity>

    @Query("SELECT * FROM number_record WHERE tableId = :tableId ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun latestRecord(tableId: Long): NumberRecordEntity?

    @Query("SELECT * FROM number_record WHERE tableId = :tableId AND date = :date ORDER BY id DESC LIMIT 1")
    suspend fun recordOn(tableId: Long, date: String): NumberRecordEntity?

    @Query("SELECT * FROM number_record WHERE tableId = :tableId AND date >= :from ORDER BY date ASC")
    suspend fun recordsFrom(tableId: Long, from: String): List<NumberRecordEntity>

    @Query("SELECT * FROM number_record")
    suspend fun listAllRecords(): List<NumberRecordEntity>

    @Insert
    suspend fun insertRecord(record: NumberRecordEntity): Long

    @Insert
    suspend fun insertRecords(records: List<NumberRecordEntity>)

    @Update
    suspend fun updateRecord(record: NumberRecordEntity)

    @Query("DELETE FROM number_record WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM number_record")
    suspend fun deleteAllRecords()
}

@Dao
interface SkyDao {

    // 账号
    @Query("SELECT * FROM sky_account ORDER BY sortOrder, id")
    fun watchAccounts(): Flow<List<SkyAccountEntity>>

    @Query("SELECT * FROM sky_account ORDER BY sortOrder, id")
    suspend fun listAccounts(): List<SkyAccountEntity>

    @Insert
    suspend fun insertAccount(account: SkyAccountEntity): Long

    @Insert
    suspend fun insertAccounts(accounts: List<SkyAccountEntity>)

    @Update
    suspend fun updateAccount(account: SkyAccountEntity)

    @Query("DELETE FROM sky_account WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM sky_account")
    suspend fun deleteAllAccounts()

    // 愿望清单
    @Query("SELECT * FROM sky_wish ORDER BY obtained, sortOrder, id")
    fun watchWishes(): Flow<List<SkyWishEntity>>

    @Query("SELECT * FROM sky_wish ORDER BY obtained, sortOrder, id")
    suspend fun listWishes(): List<SkyWishEntity>

    @Query("SELECT * FROM sky_wish WHERE itemGuid = :guid AND itemGuid != '' LIMIT 1")
    suspend fun findWishByGuid(guid: String): SkyWishEntity?

    @Insert
    suspend fun insertWish(wish: SkyWishEntity): Long

    @Insert
    suspend fun insertWishes(wishes: List<SkyWishEntity>)

    @Update
    suspend fun updateWish(wish: SkyWishEntity)

    @Query("DELETE FROM sky_wish WHERE id = :id")
    suspend fun deleteWish(id: Long)

    @Query("DELETE FROM sky_wish")
    suspend fun deleteAllWishes()

    // 当前持有
    @Query("SELECT * FROM sky_holding WHERE id = 1")
    fun watchHolding(): Flow<SkyHoldingEntity?>

    @Query("SELECT * FROM sky_holding WHERE id = 1")
    suspend fun findHolding(): SkyHoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHolding(holding: SkyHoldingEntity)

    // 复刻（抓来的）
    @Query("SELECT * FROM sky_traveling_spirit ORDER BY startDate DESC")
    fun watchTravelingSpirits(): Flow<List<SkyTravelingSpiritEntity>>

    @Query("SELECT * FROM sky_traveling_spirit ORDER BY startDate DESC")
    suspend fun listTravelingSpirits(): List<SkyTravelingSpiritEntity>

    @Query("SELECT * FROM sky_traveling_spirit WHERE manuallyEdited = 1")
    suspend fun listManuallyEditedSpirits(): List<SkyTravelingSpiritEntity>

    @Insert
    suspend fun insertTravelingSpirits(items: List<SkyTravelingSpiritEntity>)

    @Insert
    suspend fun insertTravelingSpirit(item: SkyTravelingSpiritEntity): Long

    @Update
    suspend fun updateTravelingSpirit(item: SkyTravelingSpiritEntity)

    @Query("DELETE FROM sky_traveling_spirit WHERE manuallyEdited = 0")
    suspend fun deleteFetchedTravelingSpirits()

    @Query("DELETE FROM sky_traveling_spirit")
    suspend fun deleteAllTravelingSpirits()

    // 季节（抓来的）
    @Query("SELECT * FROM sky_season ORDER BY startDate DESC")
    fun watchSeasons(): Flow<List<SkySeasonEntity>>

    @Query("SELECT * FROM sky_season ORDER BY startDate DESC")
    suspend fun listSeasons(): List<SkySeasonEntity>

    @Query("SELECT * FROM sky_season WHERE manuallyEdited = 1")
    suspend fun listManuallyEditedSeasons(): List<SkySeasonEntity>

    @Insert
    suspend fun insertSeasons(items: List<SkySeasonEntity>)

    @Update
    suspend fun updateSeason(item: SkySeasonEntity)

    @Query("DELETE FROM sky_season WHERE manuallyEdited = 0")
    suspend fun deleteFetchedSeasons()

    @Query("DELETE FROM sky_season")
    suspend fun deleteAllSeasons()
}

@Dao
interface AppStateDao {

    @Query("SELECT value FROM app_state WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: AppStateEntity)

    @Query("DELETE FROM app_state")
    suspend fun deleteAll()
}
