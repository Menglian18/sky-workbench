package com.nana.workbench.sky

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M4 阶段的测试之一：愿望清单"还差多少"。
 *
 * 这里特别要防的错误：把"已拥有"的物品也算进花费、以及忘记扣掉当前持有。
 */
class WishPlannerTest {

    private fun wish(
        id: Long,
        candles: Int = 0,
        hearts: Int = 0,
        ascended: Int = 0,
        owned: Boolean = false,
        obtained: Boolean = false,
    ) = WishItem(id, candles, hearts, ascended, owned, obtained)

    @Test
    fun `空清单`() {
        val plan = WishPlanner.plan(emptyList(), 100, 10, 2)
        assertEquals(0, plan.needCandles)
        assertEquals(0, plan.remainingCount)
        assertEquals(0, plan.shortCandles)
    }

    @Test
    fun `累加还没换到的`() {
        val plan = WishPlanner.plan(
            listOf(wish(1, candles = 60, hearts = 3), wish(2, candles = 45)),
            0, 0, 0,
        )
        assertEquals(105, plan.needCandles)
        assertEquals(3, plan.needHearts)
        assertEquals(2, plan.remainingCount)
    }

    @Test
    fun `已拥有的会被扣掉不算花费`() {
        val plan = WishPlanner.plan(
            listOf(
                wish(1, candles = 60, hearts = 3, owned = true),
                wish(2, candles = 45),
            ),
            0, 0, 0,
        )
        assertEquals("只算没拥有的那件", 45, plan.needCandles)
        assertEquals("已拥有的要单独告诉用户", 60, plan.ownedCandles)
        assertEquals(3, plan.ownedHearts)
        assertEquals(1, plan.remainingCount)
    }

    @Test
    fun `已经换到的不算花费也不算件数`() {
        val plan = WishPlanner.plan(
            listOf(wish(1, candles = 60, obtained = true), wish(2, candles = 45)),
            0, 0, 0,
        )
        assertEquals(45, plan.needCandles)
        assertEquals(1, plan.obtainedCount)
        assertEquals(1, plan.remainingCount)
    }

    @Test
    fun `够用的时候还差为零`() {
        val plan = WishPlanner.plan(listOf(wish(1, candles = 100, hearts = 5)), 150, 10, 0)
        assertEquals(100, plan.needCandles)
        assertEquals(0, plan.shortCandles)
        assertEquals(0, plan.shortHearts)
    }

    @Test
    fun `不够的时候算出差多少`() {
        val plan = WishPlanner.plan(listOf(wish(1, candles = 195, hearts = 13, ascended = 2)), 120, 5, 0)
        assertEquals(75, plan.shortCandles)
        assertEquals(8, plan.shortHearts)
        assertEquals(2, plan.shortAscended)
    }

    @Test
    fun `升华蜡烛单独算`() {
        val plan = WishPlanner.plan(listOf(wish(1, candles = 10, ascended = 3)), 0, 0, 1)
        assertEquals(3, plan.needAscended)
        assertEquals(2, plan.shortAscended)
    }

    @Test
    fun `持有数填零也能算`() {
        val plan = WishPlanner.plan(listOf(wish(1, candles = 30)), 0, 0, 0)
        assertEquals(30, plan.shortCandles)
    }

    @Test
    fun `同时有已拥有已换到和想要的`() {
        val plan = WishPlanner.plan(
            listOf(
                wish(1, candles = 10, owned = true),
                wish(2, candles = 20, obtained = true),
                wish(3, candles = 30, hearts = 1),
                wish(4, candles = 40, hearts = 2),
            ),
            50, 1, 0,
        )
        assertEquals(70, plan.needCandles)
        assertEquals(3, plan.needHearts)
        assertEquals(10, plan.ownedCandles)
        assertEquals(20, plan.shortCandles)
        assertEquals(2, plan.shortHearts)
        assertEquals(2, plan.remainingCount)
        assertEquals(1, plan.obtainedCount)
    }
}
