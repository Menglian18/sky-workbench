package com.nana.workbench.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nana.workbench.App
import com.nana.workbench.data.db.StudyCardEntity
import com.nana.workbench.data.db.StudyReviewLogEntity
import com.nana.workbench.util.LogicalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 学习安排：今日复习 + 卡片库。
 *
 * 复习流程：今日该复习的卡片直接来自数据库查询（到期的、未暂停的）。
 * 复习完一张，它的到期时间就被推到将来，于是自动从队列里消失、露出下一张 —— 不需要界面自己维护下标。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = App.from(application).database.studyDao()
    private val scheduler = FsrsScheduler()

    /** 用一个"当前时刻"驱动查询，复习完立刻刷新。 */
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())

    val dueCards: StateFlow<List<StudyCardEntity>> = _nowMillis
        .flatMapLatest { dao.watchDue(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 今天（按凌晨 4 点日界）的起点，跨天后会重算。 */
    private val _dayStartMillis = MutableStateFlow(logicalDayStartMillis())

    val todayDone: StateFlow<Int> = _dayStartMillis
        .flatMapLatest { dao.watchReviewsSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalCards: StateFlow<Int> = dao.watchTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val masteredCards: StateFlow<Int> = dao.watchMasteredCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val libraryCards: StateFlow<List<StudyCardEntity>> = _query
        .flatMapLatest { query -> dao.search(query.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 刚删掉、还能撤销的那张。 */
    private val _justDeleted = MutableStateFlow<StudyCardEntity?>(null)
    val justDeleted: StateFlow<StudyCardEntity?> = _justDeleted.asStateFlow()

    private val _lastImportMessage = MutableStateFlow<String?>(null)
    val lastImportMessage: StateFlow<String?> = _lastImportMessage.asStateFlow()

    fun setQuery(query: String) {
        _query.value = query
    }

    /** 让查询用"现在"重新算一次（进入页面或复习完时调用）。 */
    fun refreshNow() {
        _nowMillis.value = System.currentTimeMillis()
        _dayStartMillis.value = logicalDayStartMillis()
    }

    fun addCard(front: String, back: String, note: String = "") {
        val frontText = front.trim()
        if (frontText.isEmpty()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.insert(
                StudyCardEntity(
                    front = frontText,
                    back = back.trim(),
                    note = note.trim(),
                    phase = StudyPhase.NEW.ordinal,
                    dueAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            refreshNow()
        }
    }

    /** 粘贴文本批量导入，返回导入了多少张（同时把结果写进 lastImportMessage）。 */
    fun importCards(text: String) {
        val result = CardImporter.parse(text)
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.insertAll(
                result.cards.map {
                    StudyCardEntity(
                        front = it.front,
                        back = it.back,
                        phase = StudyPhase.NEW.ordinal,
                        dueAt = now,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
            _lastImportMessage.value = buildString {
                append("已导入 ").append(result.cards.size).append(" 张")
                if (result.skipped > 0) append("，跳过 ").append(result.skipped).append(" 行（没有 | 分隔符）")
            }
            refreshNow()
        }
    }

    fun clearImportMessage() {
        _lastImportMessage.value = null
    }

    /** 三个按钮分别会导致什么（显示"下次复习：…"）。 */
    fun previews(card: StudyCardEntity): List<GradePreview> {
        val schedule = StudyCardMapper.toSchedule(card)
        return scheduler.previewAll(schedule, LocalDateTime.now())
    }

    fun review(card: StudyCardEntity, grade: StudyGrade) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val schedule = StudyCardMapper.toSchedule(card, zone)
        val next = scheduler.review(schedule, grade, now)
        val elapsedDays = card.lastReviewedAt
            ?.let { ChronoUnit.MINUTES.between(StudyCardMapper.toLocal(it, zone), now).toDouble() / 1440.0 }
            ?: 0.0
        val scheduledDays = ChronoUnit.DAYS.between(
            LogicalDate.of(now),
            LogicalDate.of(next.dueAt),
        ).toInt()

        viewModelScope.launch {
            dao.update(StudyCardMapper.applyTo(card, next, zone))
            dao.insertLog(
                StudyReviewLogEntity(
                    cardId = card.id,
                    rating = grade.rating,
                    reviewedAt = StudyCardMapper.toMillis(now, zone),
                    elapsedDays = elapsedDays,
                    scheduledDays = scheduledDays,
                ),
            )
            refreshNow()
        }
    }

    fun deleteCard(card: StudyCardEntity) {
        viewModelScope.launch {
            dao.deleteById(card.id)
            _justDeleted.value = card
        }
    }

    fun undoDelete() {
        val card = _justDeleted.value ?: return
        viewModelScope.launch {
            dao.insert(card)
            _justDeleted.value = null
            refreshNow()
        }
    }

    fun clearJustDeleted() {
        _justDeleted.value = null
    }

    fun suspendCard(card: StudyCardEntity) {
        viewModelScope.launch {
            dao.suspend(card.id, System.currentTimeMillis())
            refreshNow()
        }
    }

    fun resumeCard(card: StudyCardEntity) {
        viewModelScope.launch {
            dao.resume(card.id, System.currentTimeMillis())
            refreshNow()
        }
    }

    fun saveCard(card: StudyCardEntity) {
        viewModelScope.launch {
            dao.update(card.copy(updatedAt = System.currentTimeMillis()))
            refreshNow()
        }
    }

    private fun logicalDayStartMillis(): Long =
        LogicalDate.startOfDayOf(LocalDateTime.now())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
