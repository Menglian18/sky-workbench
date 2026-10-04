package io.github.menglian18.skyworkbench.memo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.menglian18.skyworkbench.App
import io.github.menglian18.skyworkbench.data.db.MemoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 生活文本备忘。 */
class MemoViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = App.from(application).database.memoDao()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow(CATEGORY_ALL)
    val category: StateFlow<String> = _category.asStateFlow()

    private val all: StateFlow<List<MemoEntity>> = dao.watchAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 分类条：固定几项 + 用户自己加过的 */
    val categories: StateFlow<List<String>> = all
        .map { list ->
            val custom = list.map { it.category }.filter { it.isNotBlank() && it !in DEFAULT_CATEGORIES }
            DEFAULT_CATEGORIES + custom.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_CATEGORIES)

    val items: StateFlow<List<MemoEntity>> =
        combine(all, _query, _category) { list, query, category ->
            val q = query.trim()
            list.filter { item ->
                val matchQuery = q.isEmpty() ||
                    item.title.contains(q, ignoreCase = true) ||
                    item.content.contains(q, ignoreCase = true) ||
                    item.category.contains(q, ignoreCase = true)
                val matchCategory = category == CATEGORY_ALL || item.category == category
                matchQuery && matchCategory
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _justDeleted = MutableStateFlow<MemoEntity?>(null)
    val justDeleted: StateFlow<MemoEntity?> = _justDeleted.asStateFlow()

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setCategory(category: String) {
        _category.value = category
    }

    fun add(
        title: String,
        content: String,
        category: String = "其他",
        pinned: Boolean = false,
    ) {
        val finalTitle = title.trim().ifEmpty { content.trim().take(12) }
        if (finalTitle.isEmpty() && content.isBlank()) return
        viewModelScope.launch {
            dao.insert(
                MemoEntity(
                    title = finalTitle,
                    content = content.trim(),
                    category = category.ifBlank { "其他" },
                    pinned = pinned,
                    createdAt = now(),
                    updatedAt = now(),
                ),
            )
        }
    }

    fun save(item: MemoEntity) {
        viewModelScope.launch { dao.update(item.copy(updatedAt = now())) }
    }

    fun togglePin(item: MemoEntity) {
        viewModelScope.launch { dao.update(item.copy(pinned = !item.pinned, updatedAt = now())) }
    }

    fun delete(item: MemoEntity) {
        viewModelScope.launch {
            dao.deleteById(item.id)
            _justDeleted.value = item
        }
    }

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

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        const val CATEGORY_ALL = "全部"
        val DEFAULT_CATEGORIES = listOf(CATEGORY_ALL, "证件", "快递", "尺寸", "账号", "其他")
    }
}
