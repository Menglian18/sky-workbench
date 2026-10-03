package com.nana.workbench.today

import com.nana.workbench.data.db.TodoDao
import com.nana.workbench.util.DateKeys

/**
 * 顺延结算：把"还没做完、又不是今天"的条目挪到今天，并累加顺延天数。
 *
 * 抽成单独一步是因为它要在**两个地方**调用，而且必须保证是同一套算法：
 *  1. App 一启动时（不管用户先打开哪个版块，首页的待办摘要都必须是今天的）；
 *  2. 打开"今日计划"页面时（用户可能隔了一夜才切回来）。
 *
 * 判定规则本身在 [TodoRollover] 里（纯函数、有测试），这里只负责"读出来、算出来、写回去"。
 */
object TodoRolloverRunner {

    /** 返回被挪动的条数。 */
    suspend fun run(dao: TodoDao, todayKey: String = DateKeys.todayKey()): Int {
        val unfinished = dao.listUnfinishedBefore(todayKey)
        if (unfinished.isEmpty()) return 0

        val decisions = TodoRollover.decide(
            items = unfinished.map { RolloverItem(it.id, it.dueDate, it.postponedCount) },
            todayKey = todayKey,
        )
        if (decisions.isEmpty()) return 0

        val byId = unfinished.associateBy { it.id }
        var moved = 0
        val now = System.currentTimeMillis()
        decisions.forEach { decision ->
            val item = byId[decision.id] ?: return@forEach
            dao.update(
                item.copy(
                    dueDate = decision.dueDate,
                    postponedCount = decision.postponedCount,
                    updatedAt = now,
                ),
            )
            moved++
        }
        return moved
    }
}
