package io.github.menglian18.skyworkbench.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.menglian18.skyworkbench.data.backup.BackupManager
import io.github.menglian18.skyworkbench.data.backup.CleanPreview
import io.github.menglian18.skyworkbench.data.backup.DataUsage
import io.github.menglian18.skyworkbench.data.backup.formatSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 数据与设置页的状态。 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = BackupManager(application)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _usage = MutableStateFlow(DataUsage(0L, 0L))
    val usage: StateFlow<DataUsage> = _usage.asStateFlow()

    private val _lastExportAt = MutableStateFlow<Long?>(null)
    val lastExportAt: StateFlow<Long?> = _lastExportAt.asStateFlow()

    private val _cleanPreview = MutableStateFlow<CleanPreview?>(null)
    val cleanPreview: StateFlow<CleanPreview?> = _cleanPreview.asStateFlow()

    /** 有没有"导入前的安全备份"可以撤销回去 */
    private val _canUndoImport = MutableStateFlow(false)
    val canUndoImport: StateFlow<Boolean> = _canUndoImport.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _usage.value = manager.usage()
            _lastExportAt.value = manager.lastExportAt()
            _canUndoImport.value = manager.hasSafetySnapshot()
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { manager.exportTo(uri) }
                .onSuccess { summary ->
                    _message.value = "已导出：" + summary.describe()
                    refresh()
                }
                .onFailure { error ->
                    _message.value = "导出失败：" + (error.message ?: "写不进选中的位置")
                }
            _busy.value = false
        }
    }

    /**
     * 导入 = 用文件里的内容替换手机现在的数据。
     * 导入**之前**会先把手机现状存进 App 内部（BackupManager 里做），所以这里可以撤销。
     */
    fun import(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { manager.importFrom(uri) }.getOrNull()
            if (result == null) {
                _message.value = "这个文件读不出来，可能不是本 App 导出的备份"
            } else {
                _message.value = buildString {
                    append("已恢复：").append(result.restored.describe())
                    result.safety?.let { safety ->
                        append("\n导入前你手机上的数据（").append(safety.describe())
                        append("）已经存在 App 里了，可以点「撤销这次导入」整个还原。")
                    }
                }
                refresh()
            }
            _busy.value = false
        }
    }

    /** 撤销这次导入：还原到导入前的样子。 */
    fun undoImport() {
        viewModelScope.launch {
            _busy.value = true
            val summary = runCatching { manager.undoImport() }.getOrNull()
            _message.value = if (summary == null) {
                "没有可以撤销的导入记录"
            } else {
                "已还原到导入前：" + summary.describe()
            }
            refresh()
            _busy.value = false
        }
    }

    /** 第一步：算算能清出多少。 */
    fun previewClean() {
        viewModelScope.launch {
            _cleanPreview.value = manager.previewClean()
        }
    }

    fun cancelCleanPreview() {
        _cleanPreview.value = null
    }

    /** 第二步：用户确认后才真的清。 */
    fun doClean() {
        viewModelScope.launch {
            _busy.value = true
            val result = manager.cleanJunk()
            _message.value = "已清理：腾出 ${formatSize(result.freedBytes)}，" +
                "清掉抓来的复刻 ${result.spirits} 条、季节 ${result.seasons} 条（你自己记的东西一条没动）"
            _cleanPreview.value = null
            refresh()
            _busy.value = false
        }
    }

    fun clearEverything() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { manager.clearEverything() }
                .onSuccess {
                    _message.value = "已清空全部数据"
                    refresh()
                }
                .onFailure { error -> _message.value = "清空失败：" + (error.message ?: "未知原因") }
            _busy.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
