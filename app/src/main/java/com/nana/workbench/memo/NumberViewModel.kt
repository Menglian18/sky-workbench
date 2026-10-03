package com.nana.workbench.memo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nana.workbench.App
import com.nana.workbench.data.db.NumberRecordEntity
import com.nana.workbench.data.db.NumberTableEntity
import com.nana.workbench.util.DateKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 数字表格：预设几张表，记一笔，看涨跌与近 30 天趋势。 */
@OptIn(ExperimentalCoroutinesApi::class)
class NumberViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = App.from(application).database.numberDao()

    val tables: StateFlow<List<NumberTableEntity>> = dao.watchTables()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId.asStateFlow()

    val selectedTable: StateFlow<NumberTableEntity?> =
        tables.map { list -> list.firstOrNull { it.id == _selectedId.value } ?: list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val records: StateFlow<List<NumberRecordEntity>> = _selectedId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.watchRecords(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<NumberStats> = records
        .map { list ->
            NumberStatsCalculator.compute(
                records = list.map { NumberPoint(date = it.date, value = it.value) },
                todayKey = DateKeys.todayKey(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NumberStats(null, null, null, emptyList(), 0))

    private val _justDeleted = MutableStateFlow<NumberRecordEntity?>(null)
    val justDeleted: StateFlow<NumberRecordEntity?> = _justDeleted.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = dao.listTables()
            if (existing.isEmpty()) {
                PRESET_TABLES.forEach { dao.insertTable(it) }
            }
            _selectedId.value = dao.listTables().firstOrNull()?.id
        }
    }

    fun select(id: Long) {
        _selectedId.value = id
    }

    /**
     * 记一笔。同一天已经记过、且这张表不允许一天多条时，覆盖当天那条（PRD §5.3）。
     */
    fun addRecord(value: Double, note: String = "", dateKey: String = DateKeys.todayKey()) {
        val tableId = selectedTable.value?.id ?: return
        val allowMultiple = selectedTable.value?.allowMultiplePerDay ?: false
        viewModelScope.launch {
            val existing = dao.recordOn(tableId, dateKey)
            if (existing != null && !allowMultiple) {
                dao.updateRecord(existing.copy(value = value, note = note, updatedAt = now()))
            } else {
                dao.insertRecord(
                    NumberRecordEntity(
                        tableId = tableId,
                        date = dateKey,
                        value = value,
                        note = note,
                        createdAt = now(),
                        updatedAt = now(),
                    ),
                )
            }
        }
    }

    fun addTable(name: String, unit: String, decimals: Int, allowMultiplePerDay: Boolean = false) {
        val finalName = name.trim()
        if (finalName.isEmpty()) return
        viewModelScope.launch {
            val id = dao.insertTable(
                NumberTableEntity(
                    name = finalName,
                    unit = unit.trim(),
                    decimals = decimals.coerceIn(0, 3),
                    allowMultiplePerDay = allowMultiplePerDay,
                    sortOrder = dao.listTables().size,
                ),
            )
            _selectedId.value = id
        }
    }

    fun deleteTable(id: Long) {
        viewModelScope.launch {
            dao.listRecords(id).forEach { dao.deleteRecord(it.id) }
            dao.deleteTable(id)
            _selectedId.value = dao.listTables().firstOrNull()?.id
        }
    }

    fun deleteRecord(record: NumberRecordEntity) {
        viewModelScope.launch {
            dao.deleteRecord(record.id)
            _justDeleted.value = record
        }
    }

    fun undoDelete() {
        val record = _justDeleted.value ?: return
        viewModelScope.launch {
            dao.insertRecord(record)
            _justDeleted.value = null
        }
    }

    fun clearJustDeleted() {
        _justDeleted.value = null
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        /** 预设表格（PRD §5.3）。 */
        val PRESET_TABLES = listOf(
            NumberTableEntity(name = "体重", unit = "kg", decimals = 1, sortOrder = 0),
            NumberTableEntity(name = "开支", unit = "元", decimals = 2, sortOrder = 1),
            NumberTableEntity(name = "里程", unit = "公里", decimals = 1, sortOrder = 2),
            NumberTableEntity(name = "血压", unit = "mmHg", decimals = 0, sortOrder = 3),
        )
    }
}
