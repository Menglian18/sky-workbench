package io.github.menglian18.skyworkbench.ui.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.menglian18.skyworkbench.App
import io.github.menglian18.skyworkbench.data.db.StudyCardEntity
import io.github.menglian18.skyworkbench.study.mergeSubjects
import io.github.menglian18.skyworkbench.study.normalizeSubject
import io.github.menglian18.skyworkbench.study.normalizeTaskTitle
import io.github.menglian18.skyworkbench.today.moveItem
import io.github.menglian18.skyworkbench.today.renumberWithinGroups
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 学习安排：学科 + 任务（1.1 改进 · B 模块）。
 *
 * 这里**不做任何复习排期**（用户明确不要）。学科不是单独一张表：
 * 一个学科之所以存在，是因为有任务挂在它下面。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyTasksViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = App.from(application).database.studyDao()

    /** 数据库里已经有任务的学科 */
    private val subjectsInDb: StateFlow<List<String>> = dao.watchSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 刚建好、还没加任务的学科（只在内存里；加了任务就进数据库了） */
    private val pendingSubjects = MutableStateFlow<List<String>>(emptyList())

    val subjects: StateFlow<List<String>> = combine(subjectsInDb, pendingSubjects) { fromDb, pending ->
        mergeSubjects(fromDb, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前看的学科；空字符串表示"未归科" */
    private val _selectedSubject = MutableStateFlow("")
    val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

    /** 当前学科下的任务：数据库已经排好（未划掉的在前、同科内按手工顺序） */
    val tasks: StateFlow<List<StudyCardEntity>> = _selectedSubject
        .flatMapLatest { subject ->
            if (subject.isEmpty()) dao.watchTasksWithoutSubject() else dao.watchTasksInSubject(subject)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 学科被删掉后自动退回"未归科"，不然会看着一片空白又不知道为什么 */
    init {
        viewModelScope.launch {
            subjects.collect { list ->
                val current = _selectedSubject.value
                if (current.isNotEmpty() && current !in list) {
                    _selectedSubject.value = ""
                }
            }
        }
    }

    fun selectSubject(subject: String) {
        _selectedSubject.value = subject
    }

    /** 返回要提示给用户的话；null 表示成功 */
    fun addSubject(rawName: String): String? {
        val name = normalizeSubject(rawName) ?: return "学科名不能是空的"
        if (name in subjects.value) return "已经有这个学科了"
        pendingSubjects.update { it + name }
        _selectedSubject.value = name
        return null
    }

    fun renameSubject(oldName: String, rawNewName: String): String? {
        val newName = normalizeSubject(rawNewName) ?: return "学科名不能是空的"
        if (newName != oldName && newName in subjects.value) return "已经有这个学科了"
        viewModelScope.launch {
            dao.renameSubject(oldName, newName, System.currentTimeMillis())
        }
        pendingSubjects.update { list -> list.map { if (it == oldName) newName else it } }
        if (_selectedSubject.value == oldName) _selectedSubject.value = newName
        return null
    }

    fun deleteSubject(name: String) {
        viewModelScope.launch { dao.deleteSubject(name) }
        pendingSubjects.update { it - name }
        if (_selectedSubject.value == name) _selectedSubject.value = ""
    }

    fun addTask(rawTitle: String) {
        val title = normalizeTaskTitle(rawTitle) ?: return
        val subject = _selectedSubject.value
        viewModelScope.launch {
            val nextOrder = (dao.maxSortOrderInSubject(subject) ?: 0) + 1
            val now = System.currentTimeMillis()
            dao.insert(
                StudyCardEntity(
                    front = title,
                    subject = subject,
                    sortOrder = nextOrder,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    fun toggle(item: StudyCardEntity) {
        viewModelScope.launch {
            dao.setDone(item.id, !item.done, System.currentTimeMillis())
        }
    }

    fun delete(item: StudyCardEntity) {
        viewModelScope.launch { dao.deleteById(item.id) }
    }

    /** 把当前学科里已划掉的清掉（只清划掉的，没划的一律不动） */
    fun clearDone() {
        viewModelScope.launch {
            tasks.value.filter { it.done }.forEach { dao.deleteById(it.id) }
        }
    }

    /**
     * 拖拽排序：只在**没划掉的**任务之间调（划掉的沉在下面、锁定不动）。
     * 编号沿用今日待办那套（同一组内从 1 开始）。
     */
    fun reorder(openTasks: List<StudyCardEntity>, from: Int, to: Int) {
        val moved = openTasks.moveItem(from, to)
        if (moved.map { it.id } == openTasks.map { it.id }) return
        viewModelScope.launch {
            val byId = moved.associateBy { it.id }
            renumberWithinGroups(moved.map { it.id to 0 }).forEach { (id, order) ->
                val item = byId[id] ?: return@forEach
                if (item.sortOrder != order) {
                    dao.update(item.copy(sortOrder = order, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }
}
