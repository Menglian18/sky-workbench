package com.nana.workbench.ui.sky

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nana.workbench.sky.CodexAssets
import com.nana.workbench.sky.CodexCategory
import com.nana.workbench.sky.CodexData
import com.nana.workbench.sky.CodexEntry
import com.nana.workbench.sky.SpiritTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 图鉴与兑换树的数据（1.1 改进 · C2 / C3）。
 *
 * 与 SkyViewModel 分开是有意的：这里只管"打包在 App 里的图鉴/树"，不碰联网抓复刻。
 */
class CodexViewModel(application: Application) : AndroidViewModel(application) {

    private val assets = CodexAssets(application)

    private val data = MutableStateFlow(CodexData())
    private val query = MutableStateFlow("")

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { assets.load() }
                .onSuccess { data.value = it }
                .onFailure { _loadFailed.value = true }
            _loading.value = false
        }
    }

    val searchText: StateFlow<String> = query.asStateFlow()
    fun setSearch(text: String) { query.value = text }

    /** 图鉴一共多少件 */
    val totalCount: StateFlow<Int> = data
        .map { it.entries.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 按游戏内分类分好组的图鉴（搜索词会过滤） */
    val groups: StateFlow<List<Pair<CodexCategory, List<CodexEntry>>>> =
        combine(data, query) { loaded, text ->
            val keyword = text.trim()
            val filtered = if (keyword.isEmpty()) {
                loaded.entries
            } else {
                loaded.entries.filter {
                    it.displayName.contains(keyword, ignoreCase = true) ||
                        it.name.contains(keyword, ignoreCase = true) ||
                        it.partZh.contains(keyword, ignoreCase = true)
                }
            }
            CodexCategory.group(filtered, typeOf = { it.type }, nameOf = { it.displayName })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 某位先祖的兑换树；没有就返回 null */
    fun treeFor(spiritId: String): SpiritTree? = data.value.trees[spiritId]

    /**
     * 用"复刻条目上的先祖名"去对上先祖，再取它的兑换树（1.1 改进 · C2）。
     * 复刻数据来自 wiki（只有名字），兑换树来自 SkyGame-Data（按 guid），两者靠名字对上。
     */
    fun treeForName(spiritName: String): SpiritTree? {
        val guid = data.value.spiritIdByName[CodexAssets.normalizeName(spiritName)] ?: return null
        return data.value.trees[guid]
    }

    /** 先祖显示名：知道 guid 就用打包好的名字，不知道就原样返回传进来的 */
    fun spiritDisplayName(spiritId: String, fallback: String): String =
        data.value.spiritNames[spiritId] ?: fallback

    /** 某位先祖有多少件东西（详情页标题上显示） */
    fun itemCountOf(spiritId: String): Int =
        data.value.entries.count { it.spiritId == spiritId }

    /** "如何获取"：这件物品属于哪位先祖、在树里第几层 */
    fun howToGet(entry: CodexEntry): String? {
        val tree = data.value.trees[entry.spiritId] ?: return null
        val node = tree.nodes.firstOrNull { it.itemGuid == entry.guid } ?: return null
        return "在「${tree.spiritId}」的兑换树第 ${tree.pathTo(node.index).size} 层"
    }
}
