package io.github.menglian18.skyworkbench.sky

/** 愿望清单里的一条（只保留算账需要的字段）。 */
data class WishItem(
    val id: Long,
    val candles: Int,
    val hearts: Int,
    val ascended: Int,
    /** 已经拥有这件（兑换树里算差价要扣掉） */
    val owned: Boolean,
    /** 已经换到手 */
    val obtained: Boolean,
)

/** 算账结果。 */
data class WishPlan(
    /** 还想要的物品一共需要多少（已扣掉"已拥有"的） */
    val needCandles: Int,
    val needHearts: Int,
    val needAscended: Int,
    /** 已拥有部分值多少（让用户看懂差价是怎么来的） */
    val ownedCandles: Int,
    val ownedHearts: Int,
    /** 按"我现在有多少"算，还差多少（不够就是正数，够了就是 0） */
    val shortCandles: Int,
    val shortHearts: Int,
    val shortAscended: Int,
    /** 还没换到的件数 */
    val remainingCount: Int,
    /** 已经换到的件数 */
    val obtainedCount: Int,
)

/**
 * 愿望清单的"还差多少"。
 *
 * 关键点：光遇的兑换树是**累积成本**——一件物品的花费是"从树根走到它这一路所有节点的花费之和"。
 * 所以每件物品的价格在生成图鉴数据时就已经算好了（见 tools/build_codex.py），
 * 这里只做加法与减法，**绝不能把单价简单相加或忽略"已拥有"的部分**。
 */
object WishPlanner {

    fun plan(
        items: List<WishItem>,
        holdingCandles: Int,
        holdingHearts: Int,
        holdingAscended: Int,
    ): WishPlan {
        var needCandles = 0
        var needHearts = 0
        var needAscended = 0
        var ownedCandles = 0
        var ownedHearts = 0
        var remaining = 0
        var obtained = 0

        for (item in items) {
            if (item.obtained) {
                obtained++
                continue
            }
            if (item.owned) {
                ownedCandles += item.candles
                ownedHearts += item.hearts
                continue
            }
            needCandles += item.candles
            needHearts += item.hearts
            needAscended += item.ascended
            remaining++
        }

        return WishPlan(
            needCandles = needCandles,
            needHearts = needHearts,
            needAscended = needAscended,
            ownedCandles = ownedCandles,
            ownedHearts = ownedHearts,
            shortCandles = (needCandles - holdingCandles).coerceAtLeast(0),
            shortHearts = (needHearts - holdingHearts).coerceAtLeast(0),
            shortAscended = (needAscended - holdingAscended).coerceAtLeast(0),
            remainingCount = remaining,
            obtainedCount = obtained,
        )
    }
}
