package io.github.menglian18.skyworkbench.data.db

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

    /** 某天已有的最大手工顺序；新建的条目排在最后（1.1 改进第 3 条：拖拽排序） */
    @Query("SELECT MAX(sortOrder) FROM todo_item WHERE dueDate = :date")
    suspend fun maxSortOrder(date: String): Int?

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

    // ── 1.1 改进：今日复习改成"学科 + 任务" ──────────────────────────────────

    /** 所有学科名（去重、去掉空的），按名字排 */
    @Query("SELECT DISTINCT subject FROM study_card WHERE subject != '' ORDER BY subject")
    fun watchSubjects(): Flow<List<String>>

    /** 某个学科下的任务：没划掉的在前，同科内按手工顺序 */
    @Query("SELECT * FROM study_card WHERE subject = :subject ORDER BY done, sortOrder, id")
    fun watchTasksInSubject(subject: String): Flow<List<StudyCardEntity>>

    /** 没归学科的任务（老数据可能落在这一堆） */
    @Query("SELECT * FROM study_card WHERE subject = '' ORDER BY done, sortOrder, id")
    fun watchTasksWithoutSubject(): Flow<List<StudyCardEntity>>

    /** 某学科下已有的最大手工顺序；新建的任务排在最后 */
    @Query("SELECT MAX(sortOrder) FROM study_card WHERE subject = :subject")
    suspend fun maxSortOrderInSubject(subject: String): Int?

    /** 划掉 / 取消划掉 */
    @Query("UPDATE study_card SET done = :done, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, now: Long)

    /** 改一条任务的学科 */
    @Query("UPDATE study_card SET subject = :subject, updatedAt = :now WHERE id = :id")
    suspend fun moveToSubject(id: Long, subject: String, now: Long)

    /** 整个学科改名（该学科下所有任务一起改） */
    @Query("UPDATE study_card SET subject = :newName, updatedAt = :now WHERE subject = :oldName")
    suspend fun renameSubject(oldName: String, newName: String, now: Long)

    /** 删掉整个学科（连同它下面的任务） */
    @Query("DELETE FROM study_card WHERE subject = :subject")
    suspend fun deleteSubject(subject: String)

    /** 只接受一份份任务用：查某学科下的任务（非 Flow，导入时用得上） */
    @Query("SELECT * FROM study_card WHERE subject = :subject ORDER BY sortOrder, id")
    suspend fun listTasksInSubject(subject: String): List<StudyCardEntity>

    /** 全部任务（首页统计用）：未划掉的在前 */
    @Query("SELECT * FROM study_card ORDER BY done, sortOrder, id")
    fun watchAllTasks(): Flow<List<StudyCardEntity>>

    /** 首页用：还没划掉的任务数 */
    @Query("SELECT COUNT(*) FROM study_card WHERE done = 0")
    fun watchOpenTaskCount(): Flow<Int>

    /** 首页用：已划掉的任务数 */
    @Query("SELECT COUNT(*) FROM study_card WHERE done = 1")
    fun watchDoneTaskCount(): Flow<Int>

    /** 首页用：有几个学科 */
    @Query("SELECT COUNT(DISTINCT subject) FROM study_card WHERE subject != ''")
    fun watchSubjectCount(): Flow<Int>

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

    /** 按 id 精确删除（1.1 改进 · D1：只清早就过期的，不碰正在进行的） */
    @Query("DELETE FROM sky_traveling_spirit WHERE id IN (:ids)")
    suspend fun deleteTravelingSpiritsByIds(ids: List<Long>)

    /** 按 id 精确删除季节 */
    @Query("DELETE FROM sky_season WHERE id IN (:ids)")
    suspend fun deleteSeasonsByIds(ids: List<Long>)

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

    // 节日活动（抓来的，1.3.0 新增"活动查看"）
    @Query("SELECT * FROM sky_event ORDER BY startDate DESC")
    fun watchEvents(): Flow<List<SkyEventEntity>>

    @Query("SELECT * FROM sky_event ORDER BY startDate DESC")
    suspend fun listEvents(): List<SkyEventEntity>

    @Insert
    suspend fun insertEvents(items: List<SkyEventEntity>)

    @Query("DELETE FROM sky_event WHERE manuallyEdited = 0")
    suspend fun deleteFetchedEvents()

    @Query("DELETE FROM sky_event")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM sky_event WHERE id IN (:ids)")
    suspend fun deleteEventsByIds(ids: List<Long>)
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
