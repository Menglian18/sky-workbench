package io.github.menglian18.skyworkbench.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.menglian18.skyworkbench.App
import io.github.menglian18.skyworkbench.data.db.MemoEntity
import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.data.db.TodoEntity
import io.github.menglian18.skyworkbench.sky.SkyCountdown
import io.github.menglian18.skyworkbench.sky.SkyCountdownPicks
import io.github.menglian18.skyworkbench.today.TodoOrdering
import io.github.menglian18.skyworkbench.today.TodoRolloverRunner
import io.github.menglian18.skyworkbench.today.Quadrant
import io.github.menglian18.skyworkbench.today.quadrantOf
import io.github.menglian18.skyworkbench.util.DateKeys
import io.github.menglian18.skyworkbench.util.LogicalDate
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

    // 1.1 改进：学习版块改成"学科 + 任务"、不再有复习排期，所以首页这几项也跟着换。

    /** 学习安排：还没做完的任务数 */
    val studyOpenCount: StateFlow<Int> = db.studyDao()
        .watchOpenTaskCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 学习安排：已划掉的任务数 */
    val studyDoneCount: StateFlow<Int> = db.studyDao()
        .watchDoneTaskCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 学习安排：有几个学科 */
    val studySubjectCount: StateFlow<Int> = db.studyDao()
        .watchSubjectCount()
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
