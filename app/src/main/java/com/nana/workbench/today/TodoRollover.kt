package com.nana.workbench.today

import com.nana.workbench.util.DateKeys
import java.time.temporal.ChronoUnit

/** 顺延计算需要的最小信息。 */
data class RolloverItem(
    val id: Long,
    val dueDate: String,
    val postponedCount: Int,
)

/** 需要写回数据库的更新。 */
data class RolloverUpdate(
    val id: Long,
    val dueDate: String,
    val postponedCount: Int,
)

/**
 * "没做完自动跟到明天"的规则（PRD §5.1）。
 *
 * 这是纯函数，不碰数据库，所以在电脑上就能测。
 *
 * 规则：
 *  - 只有"还没做完、且落在今天之前"的条目会被挪。
 *  - 挪到**今天**（不是明天）——用户打开 App 看到的是"今天要做的事"，昨天没做完的就该出现在今天。
 *  - 顺延天数按**实际过了几天**累加：隔了 3 天没打开，就一次加 3 天，而不是只加 1。
 */
object TodoRollover {

    fun decide(items: List<RolloverItem>, todayKey: String): List<RolloverUpdate> {
        val today = DateKeys.parse(todayKey)
        return items.mapNotNull { item ->
            val due = DateKeys.parse(item.dueDate)
            val daysLate = ChronoUnit.DAYS.between(due, today)
            if (daysLate <= 0L) {
                null
            } else {
                RolloverUpdate(
                    id = item.id,
                    dueDate = todayKey,
                    postponedCount = item.postponedCount + daysLate.toInt(),
                )
            }
        }
    }
}
