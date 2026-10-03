package com.nana.workbench.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nana.workbench.App
import com.nana.workbench.data.db.TodoEntity
import com.nana.workbench.util.DateKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 今日计划。
 *
 * 打开时先做一次"顺延结算"：昨天没做完、且不是今天的条目，自动挪到今天并累加顺延天数。
 * 结算在**本机补算**，不需要 App 常驻后台（见 PRD §5.1）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = App.from(application).database.todoDao()

    private val _dateKey = MutableStateFlow(DateKeys.todayKey())
    val dateKey: StateFlow<String> = _dateKey.asStateFlow()

    val openItems: StateFlow<List<TodoEntity>> = _dateKey
        .flatMapLatest { dao.watchForDate(it) }
        .map { TodoOrdering.sortForDisplay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneItems: StateFlow<List<TodoEntity>> = _dateKey
        .flatMapLatest { dao.watchDoneForDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 刚删掉、还能撤销的那一条。 */
    private val _justDeleted = MutableStateFlow<TodoEntity?>(null)
    val justDeleted: StateFlow<TodoEntity?> = _justDeleted.asStateFlow()

    init {
        viewModelScope.launch { TodoRolloverRunner.run(dao) }
    }

    /** 切回本页时再结算一次（用户可能已经跨过了凌晨 4 点）。 */
    fun refreshToday() {
        viewModelScope.launch { TodoRolloverRunner.run(dao) }
    }

    fun shiftDays(days: Long) {
        _dateKey.value = DateKeys.of(DateKeys.parse(_dateKey.value).plusDays(days))
    }

    fun backToToday() {
        _dateKey.value = DateKeys.todayKey()
    }

    fun isViewingToday(): Boolean = _dateKey.value == DateKeys.todayKey()

    fun add(title: String, priority: Int = 0, timePoint: String? = null) {
        val text = title.trim()
        if (text.isEmpty()) return
        val date = _dateKey.value
        viewModelScope.launch {
            dao.insert(
                TodoEntity(
                    title = text,
                    priority = priority,
                    timePoint = timePoint?.takeIf { it.isNotBlank() },
                    originalDate = date,
                    dueDate = date,
                    createdAt = now(),
                    updatedAt = now(),
                ),
            )
        }
    }

    fun toggle(item: TodoEntity) {
        viewModelScope.launch {
            val willBeDone = !item.done
            dao.update(
                item.copy(
                    done = willBeDone,
                    doneAt = if (willBeDone) now() else null,
                    updatedAt = now(),
                ),
            )
        }
    }

    fun save(item: TodoEntity) {
        viewModelScope.launch { dao.update(item.copy(updatedAt = now())) }
    }

    fun delete(item: TodoEntity) {
        viewModelScope.launch {
            dao.deleteById(item.id)
            _justDeleted.value = item
        }
    }

    /** 撤销删除：把同一条（连 id 一起）插回去。 */
    fun undoDelete() {
        val item = _justDeleted.value ?: return
        viewModelScope.launch {
            dao.insert(item)
            _justDeleted.value = null
        }
    }

    fun clearJustDeleted() {
        _justDeleted.value = null
    }

    /** 延后：把这条挪到 [days] 天以后。 */
    fun postpone(item: TodoEntity, days: Long) {
        viewModelScope.launch {
            val target = DateKeys.of(DateKeys.parse(item.dueDate).plusDays(days))
            dao.update(item.copy(dueDate = target, updatedAt = now()))
        }
    }

    fun clearDone() {
        viewModelScope.launch { dao.deleteDoneOn(_dateKey.value) }
    }

    private fun now(): Long = System.currentTimeMillis()
}
