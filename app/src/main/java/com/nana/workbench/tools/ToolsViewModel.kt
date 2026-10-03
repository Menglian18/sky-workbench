package com.nana.workbench.tools

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nana.workbench.App
import com.nana.workbench.data.db.AppStateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 解压小工具：骰子、转盘（可存好几套）、随机抽一个。
 * 选项存在手机本地的键值表里，重启不丢。
 */
class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val stateDao = App.from(application).database.appStateDao()
    private val picker = RandomPicker()

    private val _wheelSets = MutableStateFlow(WheelSetsCodec.defaults())
    val wheelSets: StateFlow<List<WheelSet>> = _wheelSets.asStateFlow()

    private val _selectedSetIndex = MutableStateFlow(0)
    val selectedSetIndex: StateFlow<Int> = _selectedSetIndex.asStateFlow()

    private val _spinResult = MutableStateFlow<String?>(null)
    val spinResult: StateFlow<String?> = _spinResult.asStateFlow()

    private val _noRepeat = MutableStateFlow(false)
    val noRepeat: StateFlow<Boolean> = _noRepeat.asStateFlow()

    private val _diceSides = MutableStateFlow(6)
    val diceSides: StateFlow<Int> = _diceSides.asStateFlow()

    private val _diceResult = MutableStateFlow<Int?>(null)
    val diceResult: StateFlow<Int?> = _diceResult.asStateFlow()

    private val _randomList = MutableStateFlow<List<String>>(emptyList())
    val randomList: StateFlow<List<String>> = _randomList.asStateFlow()

    private val _randomResult = MutableStateFlow<String?>(null)
    val randomResult: StateFlow<String?> = _randomResult.asStateFlow()

    private var usedIndexes = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            _wheelSets.value = WheelSetsCodec.decode(stateDao.get(KEY_WHEEL_SETS))
            _randomList.value = WheelSetsCodec.decode(stateDao.get(KEY_RANDOM_LIST))
                .firstOrNull()?.options.orEmpty()
        }
    }

    // ── 骰子 ──────────────────────────────────────────────────────────────────

    fun setDiceSides(sides: Int) {
        _diceSides.value = sides
        _diceResult.value = null
    }

    fun rollDice() {
        _diceResult.value = picker.rollDice(_diceSides.value)
    }

    // ── 转盘 ──────────────────────────────────────────────────────────────────

    fun selectSet(index: Int) {
        if (index in _wheelSets.value.indices) {
            _selectedSetIndex.value = index
            _spinResult.value = null
            usedIndexes = mutableSetOf()
        }
    }

    fun setNoRepeat(enabled: Boolean) {
        _noRepeat.value = enabled
        usedIndexes = mutableSetOf()
    }

    fun currentOptions(): List<String> =
        _wheelSets.value.getOrNull(_selectedSetIndex.value)?.options.orEmpty()

    fun spin() {
        val options = currentOptions()
        if (options.isEmpty()) {
            _spinResult.value = null
            return
        }
        if (_noRepeat.value) {
            val result = picker.pickOneWithoutRepeat(options, usedIndexes)
            if (result != null) {
                if (usedIndexes.size >= options.count { it.isNotBlank() }) usedIndexes = mutableSetOf()
                usedIndexes += result.first
                _spinResult.value = result.second
            }
        } else {
            _spinResult.value = picker.pickOne(options)
        }
    }

    /** 保存某套转盘的选项（界面上是一行一个）。 */
    fun saveSetOptions(index: Int, options: List<String>) {
        val cleaned = options.map { it.trim() }.filter { it.isNotEmpty() }
        val current = _wheelSets.value.toMutableList()
        if (index !in current.indices) return
        current[index] = current[index].copy(options = cleaned)
        persist(current)
        usedIndexes = mutableSetOf()
        _spinResult.value = null
    }

    fun addSet(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val current = _wheelSets.value + WheelSet(cleanName, listOf("选项一", "选项二"))
        persist(current)
        _selectedSetIndex.value = current.lastIndex
    }

    fun deleteSet(index: Int) {
        val current = _wheelSets.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        persist(current)
        _selectedSetIndex.value = 0
        _spinResult.value = null
    }

    private fun persist(sets: List<WheelSet>) {
        _wheelSets.value = sets
        viewModelScope.launch {
            stateDao.put(AppStateEntity(KEY_WHEEL_SETS, WheelSetsCodec.encode(sets)))
        }
    }

    // ── 随机抽一个 ────────────────────────────────────────────────────────────

    fun saveRandomList(options: List<String>) {
        val cleaned = options.map { it.trim() }.filter { it.isNotEmpty() }
        _randomList.value = cleaned
        _randomResult.value = null
        viewModelScope.launch {
            stateDao.put(
                AppStateEntity(
                    KEY_RANDOM_LIST,
                    WheelSetsCodec.encode(listOf(WheelSet(RANDOM_SET_NAME, cleaned))),
                ),
            )
        }
    }

    fun pickRandom() {
        _randomResult.value = picker.pickOne(_randomList.value)
    }

    companion object {
        private const val KEY_WHEEL_SETS = "tools_wheel_sets"
        private const val KEY_RANDOM_LIST = "tools_random_list"
        private const val RANDOM_SET_NAME = "随机名单"
    }
}
