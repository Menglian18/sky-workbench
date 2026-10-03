package com.nana.workbench.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nana.workbench.App
import com.nana.workbench.data.db.MemoEntity
import com.nana.workbench.data.db.SkySeasonEntity
import com.nana.workbench.data.db.TodoEntity
import com.nana.workbench.sky.SkyCountdown
import com.nana.workbench.sky.SkyCountdownPicks
import com.nana.workbench.today.TodoOrdering
import com.nana.workbench.today.TodoRolloverRunner
import com.nana.workbench.today.Quadrant
import com.nana.workbench.today.quadrantOf
import com.nana.workbench.util.DateKeys
import com.nana.workbench.util.LogicalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 首页总览：只读聚合 + 三个"就地操作"（快速备忘、勾选待办、开关置顶）。
 * 自己**不保存任何数据**（见 PRD §6.2）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = App.from(application).database

    private val _dateKey = MutableStateFlow(DateKeys.todayKey())
    val dateKey: StateFlow<String> = _dateKey.asStateFlow()

    /**
     * 切回首页时：先结算顺延，再按"现在"重算今天。
     * 这样即使 App 一直开着跨过了凌晨 4 点，首页显示的也是正确的一天。
     */
    fun refreshToday() {
        viewModelScope.launch {
            val today = DateKeys.todayKey()
            TodoRolloverRunner.run(db.todoDao(), today)
            _dateKey.value = today
        }
    }

    /** 今天还剩几件待办 */
    val openCount: StateFlow<Int> = dateKey
        .flatMapLatest { db.todoDao().watchOpenCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 摘要卡上显示的待办（前 3 条） */
    val topTodos: StateFlow<List<TodoEntity>> = dateKey
        .flatMapLatest { db.todoDao().watchForDate(it) }
        .map { TodoOrdering.sortForDisplay(it).take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 首页的"紧急 / 重要四象限"（1.1 改进第 3 条）。
     * 四格都要显示（哪怕是 0 条），所以先把四格都摆好，再往里填。
     */
    val quadrants: StateFlow<Map<Quadrant, List<TodoEntity>>> = dateKey
        .flatMapLatest { db.todoDao().watchForDate(it) }
        .map { items ->
            val sorted = TodoOrdering.sortForDisplay(items)
            Quadrant.ordered.associateWith { quadrant ->
                sorted.filter { quadrantOf(it.priority) == quadrant }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 今天该复习多少张 */
    val studyDueCount: StateFlow<Int> = db.studyDao()
        .watchDueCount(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 今天已经复习了多少张 */
    val studyDoneToday: StateFlow<Int> = db.studyDao()
        .watchReviewsSince(logicalDayStartMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 置顶的备忘（最多 3 条） */
    val pinnedMemos: StateFlow<List<MemoEntity>> = db.memoDao()
        .watchPinned(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val countdown: StateFlow<SkyCountdownPicks> =
        combine(db.skyDao().watchTravelingSpirits(), dateKey) { list, today ->
            SkyCountdown.pick(list, today)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SkyCountdownPicks(null, null),
        )

    val currentSeason: StateFlow<SkySeasonEntity?> =
        combine(db.skyDao().watchSeasons(), dateKey) { list, today ->
            SkyCountdown.currentSeason(list, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 快速备忘：写进"备忘 · 生活文本"，不是首页自己的数据。 */
    fun quickAddMemo(text: String, pinned: Boolean) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            db.memoDao().insert(
                MemoEntity(
                    title = content.take(12),
                    content = content,
                    category = "其他",
                    pinned = pinned,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 首页就地勾选：直接改"今日计划"里的同一条。 */
    fun toggleTodo(item: TodoEntity) {
        viewModelScope.launch {
            val willBeDone = !item.done
            db.todoDao().update(
                item.copy(
                    done = willBeDone,
                    doneAt = if (willBeDone) System.currentTimeMillis() else null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun setTodoPinned(item: MemoEntity, pinned: Boolean) {
        viewModelScope.launch { db.memoDao().update(item.copy(pinned = pinned)) }
    }

    private fun logicalDayStartMillis(): Long =
        LogicalDate.startOfDayOf(LocalDateTime.now())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
