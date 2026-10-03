package com.nana.workbench.today

import com.nana.workbench.data.db.TodoEntity

/**
 * 今日计划列表的显示顺序。
 *
 * 规则（写死在这里，避免界面上各写一套）：
 *  1. 优先级高的在前（2 紧急 / 1 重要 / 0 普通）
 *  2. 有具体时间点的在前，时间早的再前
 *  3. 最后按手工排序、再按创建先后
 */
object TodoOrdering {

    fun sortForDisplay(items: List<TodoEntity>): List<TodoEntity> =
        items.sortedWith(
            compareByDescending<TodoEntity> { it.priority }
                .thenBy { it.timePoint == null }
                .thenBy { it.timePoint ?: "" }
                .thenBy { it.sortOrder }
                .thenBy { it.id },
        )
}
