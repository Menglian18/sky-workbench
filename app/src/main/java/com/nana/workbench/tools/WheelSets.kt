package com.nana.workbench.tools

import org.json.JSONArray
import org.json.JSONObject

/** 一套转盘选项（例如"午饭吃什么"）。 */
data class WheelSet(val name: String, val options: List<String>)

/**
 * 转盘选项的保存与读取（PRD 要求"选项能存好几套"）。
 *
 * 存在手机本地的键值表里，格式用 JSON。写成纯函数便于测试。
 */
object WheelSetsCodec {

    fun defaults(): List<WheelSet> = listOf(
        WheelSet("午饭吃什么", listOf("火锅", "烤肉", "面条", "盖饭", "随便吃点")),
        WheelSet("周末去哪", listOf("宅家", "逛街", "公园", "看电影")),
    )

    fun encode(sets: List<WheelSet>): String {
        val array = JSONArray()
        for (set in sets) {
            val obj = JSONObject()
            obj.put("name", set.name)
            val options = JSONArray()
            set.options.forEach { options.put(it) }
            obj.put("options", options)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * 读取。注意区分两种情况：
     *  - 从来没存过（null / 空串）→ 用默认的几套；
     *  - 存过但内容为空数组 `[]` → 尊重用户选择，返回空（不要又冒出默认值）。
     */
    fun decode(text: String?): List<WheelSet> {
        if (text.isNullOrBlank()) return defaults()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return defaults()
        val result = mutableListOf<WheelSet>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
            val rawOptions = obj.optJSONArray("options") ?: JSONArray()
            val options = mutableListOf<String>()
            for (optionIndex in 0 until rawOptions.length()) {
                val value = rawOptions.optString(optionIndex)
                if (value.isNotBlank()) options += value
            }
            if (options.isNotEmpty()) result += WheelSet(name, options)
        }
        return result
    }
}
