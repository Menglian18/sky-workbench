package com.nana.workbench.today

import com.nana.workbench.data.db.TodoEntity

/**
 * 今日计划列表的显示顺序。
 *
 * 规则（写死在这里，避免界面上各写一套）：
 *  0. **已划掉的沉到最下面**（1.1 改进第 4 条：划掉后放到所有任务最下层、锁定不动）
 *  1. 没完成的按四象限分组：重要又紧急 → 重要不紧急 → 紧急不重要 → 都不
 *  2. **同一格内按手工顺序**（拖拽调过就以拖的为准）
 *  3. 再按"有具体时间点的在前、时间早的在前"
 *  4. 最后按创建先后
 *
 * 第 2 条放在第 3 条前面，是 1.1 改进第 3 条带来的变化：
 * 不这样排的话，用户拖完顺序会被"时间早的在前"顶回去，拖了等于没拖。
 */
object TodoOrdering {

    fun sortForDisplay(items: List<TodoEntity>): List<TodoEntity> =
        items.sortedWith(
            compareBy<TodoEntity> { it.done }
                .thenByDescending { it.priority }
                .thenBy { it.sortOrder }
                .thenBy { it.timePoint == null }
                .thenBy { it.timePoint ?: "" }
                .thenBy { it.id },
        )

    /** 只有没划掉的能拖 */
    fun draggable(items: List<TodoEntity>): List<TodoEntity> = items.filter { !it.done }
}
