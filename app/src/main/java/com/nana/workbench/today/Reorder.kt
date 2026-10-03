package com.nana.workbench.today

/**
 * 拖拽排序的纯逻辑（1.1 改进第 3 条）。
 *
 * 手势那部分只能在手机上试，但"挪完之后的顺序是什么"这件事可以在这里定死、测清楚。
 */

/**
 * 把第 [from] 个元素挪到第 [to] 个位置，其余元素顺次让位。
 * 下标越界会被夹回合法范围（拖到列表外也不会崩）。
 */
fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (size <= 1) return this
    val source = from.coerceIn(0, lastIndex)
    val target = to.coerceIn(0, lastIndex)
    if (source == target) return this
    val result = toMutableList()
    val moved = result.removeAt(source)
    result.add(target, moved)
    return result
}

/**
 * 拖完之后重新编号。
 *
 * 列表是按四象限分组的，所以：
 *  - **同一个象限内**从 1 开始编；
 *  - 不同象限各编各的（这样在一个象限里拖动，不会把别的象限的顺序搅乱）。
 *
 * 输入是按显示顺序排好的 (id, 象限编码)，输出是每一行应有的 (id, 新的 sortOrder)。
 */
fun renumberWithinGroups(orderedIds: List<Pair<Long, Int>>): List<Pair<Long, Int>> {
    val counters = mutableMapOf<Int, Int>()
    return orderedIds.map { (id, group) ->
        val next = (counters[group] ?: 0) + 1
        counters[group] = next
        id to next
    }
}
