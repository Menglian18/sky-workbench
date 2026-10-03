package com.nana.workbench.tools

import kotlin.random.Random

/**
 * 随机抽取：骰子、随机抽一个、转盘选一个。
 *
 * 带一个可传入的随机源，好处是**能在电脑上测**：给定种子，结果必须可复现。
 */
class RandomPicker(private val random: Random = Random.Default) {

    /** 骰子：1 到 [sides] 面。 */
    fun rollDice(sides: Int): Int {
        val safeSides = sides.coerceAtLeast(1)
        return random.nextInt(safeSides) + 1
    }

    /** 从列表里抽一个；列表为空返回 null。 */
    fun pickOne(options: List<String>): String? {
        val usable = options.filter { it.isNotBlank() }
        if (usable.isEmpty()) return null
        return usable[random.nextInt(usable.size)]
    }

    /**
     * 不重复地抽：把剩下的抽完之前不会重复。
     * [usedIndexes] 是已经抽过的下标（外部保存，便于界面记录）。
     */
    fun pickOneWithoutRepeat(options: List<String>, usedIndexes: Set<Int>): Pair<Int, String>? {
        val usable = options.withIndex().filter { it.value.isNotBlank() }
        if (usable.isEmpty()) return null
        val remaining = usable.filter { it.index !in usedIndexes }
        val pool = remaining.ifEmpty { usable }
        val chosen = pool[random.nextInt(pool.size)]
        return chosen.index to chosen.value
    }
}
