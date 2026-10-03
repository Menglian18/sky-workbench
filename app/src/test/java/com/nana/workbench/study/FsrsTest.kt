package com.nana.workbench.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * M5 阶段的测试（提前写好，因为这属于"先立地基"的部分）：
 * FSRS v6 复习节奏算法。
 *
 * 测试思路：
 *  1. **手算参考值**：下面那些 10.96 / 7.51 / 0.607 之类的数字，是我按公开公式用笔算出来的，
 *     写进测试当"标准答案"。实现一旦跑偏（比如公式抄错、忘了加约束），这些断言立刻会红。
 *  2. **不变量**：间隔 ≈ 稳定度、目标记住率越高间隔越短、间隔必须递增、难度必须落在 1～10 等。
 */
class FsrsTest {

    private val scheduler = FsrsScheduler()
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 9, 20, 0)

    /** 一张已经进入正常复习、2 天前复习过的卡（稳定度/难度用"新卡点记住了"之后的值）。 */
    private fun reviewCardAt2Days(): CardSchedule = CardSchedule(
        phase = StudyPhase.REVIEW,
        stability = 2.3065,
        difficulty = 2.11764,
        reps = 1,
        lapses = 0,
        lastReviewedAt = now.minusDays(2),
        dueAt = now,
    )

    // ── 新卡 ────────────────────────────────────────────────────────────────

    @Test
    fun `新卡点记住了_进入按天复习_两年后是两天`() {
        val after = scheduler.review(CardSchedule.newCard(now), StudyGrade.GOOD, now)
        assertEquals(StudyPhase.REVIEW, after.phase)
        assertEquals(2.3065, after.stability, 1e-9)          // = 参数 w[2]
        assertEquals(2.11764, after.difficulty, 0.05)          // = init_difficulty(记住了)
        assertEquals(now.plusDays(2), after.dueAt)
        assertEquals(1, after.reps)
        assertEquals(0, after.lapses)
    }

    @Test
    fun `新卡点忘了_当天还会再出现_五分钟后`() {
        val after = scheduler.review(CardSchedule.newCard(now), StudyGrade.AGAIN, now)
        assertEquals(StudyPhase.LEARNING, after.phase)
        assertEquals(now.plusMinutes(5), after.dueAt)
        assertEquals(0.212, after.stability, 1e-9)             // = 参数 w[0]
        assertEquals(6.4133, after.difficulty, 0.05)           // = init_difficulty(忘了)
        assertTrue("忘了的到期时间必须在同一天内", after.dueAt.isBefore(now.plusDays(1)))
    }

    @Test
    fun `新卡点有点印象_十分钟后再看`() {
        val after = scheduler.review(CardSchedule.newCard(now), StudyGrade.HARD, now)
        assertEquals(StudyPhase.LEARNING, after.phase)
        assertEquals(now.plusMinutes(10), after.dueAt)
        assertEquals(1.2931, after.stability, 1e-9)            // = 参数 w[1]
    }

    @Test
    fun `新卡不会凭空多出遗忘次数`() {
        listOf(StudyGrade.AGAIN, StudyGrade.HARD, StudyGrade.GOOD).forEach { grade ->
            val after = scheduler.review(CardSchedule.newCard(now), grade, now)
            assertEquals("$grade 不该增加遗忘次数", 0, after.lapses)
            assertEquals(1, after.reps)
        }
    }

    // ── 间隔与稳定度的关系 ────────────────────────────────────────────────────

    @Test
    fun `默认目标记住率下_间隔约等于稳定度`() {
        listOf(1.0, 2.0, 2.3065, 5.0, 10.0, 30.0, 100.0, 365.0).forEach { stability ->
            assertEquals(
                "稳定度 $stability 的间隔应约等于稳定度本身",
                stability.toInt(),
                scheduler.intervalDaysFor(stability),
            )
        }
    }

    @Test
    fun `目标记住率越高_间隔越短`() {
        val relaxed = FsrsScheduler(requestRetention = 0.80).intervalDaysFor(10.0)
        val normal = FsrsScheduler(requestRetention = 0.90).intervalDaysFor(10.0)
        val strict = FsrsScheduler(requestRetention = 0.95).intervalDaysFor(10.0)
        assertTrue("目标越松间隔越长：$relaxed 应大于 $normal", relaxed > normal)
        assertTrue("目标越紧间隔越短：$strict 应小于 $normal", strict < normal)
    }

    @Test
    fun `间隔永远至少一天_且不超过上限`() {
        assertEquals(1, scheduler.intervalDaysFor(0.0))
        assertEquals(1, scheduler.intervalDaysFor(0.1))
        val tiny = FsrsScheduler(maxIntervalDays = 10)
        assertEquals(10, tiny.intervalDaysFor(1000.0))
    }

    @Test
    fun `刚学过零点几天后_记住的概率接近一`() {
        val r = scheduler.forgettingCurve(0.0, 10.0)
        assertEquals(1.0, r, 1e-9)
        assertTrue(scheduler.forgettingCurve(10.0, 10.0) < 1.0)
        assertTrue(scheduler.forgettingCurve(20.0, 10.0) < scheduler.forgettingCurve(10.0, 10.0))
    }

    @Test
    fun `时间被改乱时_记住的概率仍在一以内`() {
        // 负数天数（系统时间回拨）不能让概率超过 1
        assertEquals(1.0, scheduler.forgettingCurve(-5.0, 10.0), 1e-9)
        assertTrue(scheduler.forgettingCurve(-100.0, 3.0) <= 1.0)
        // 正常范围也不超过 1
        listOf(0.0, 1.0, 30.0, 365.0).forEach { days ->
            val r = scheduler.forgettingCurve(days, 5.0)
            assertTrue("第 $days 天的概率越界: $r", r in 0.0..1.0)
        }
    }

    @Test
    fun `时钟回拨时_记住了不会让稳定度倒退`() {
        // 上次复习时间被标在了"未来"（例如用户手动改了手机时间）
        val weird = CardSchedule(
            phase = StudyPhase.REVIEW,
            stability = 10.0,
            difficulty = 5.0,
            reps = 3,
            lapses = 0,
            lastReviewedAt = now.plusDays(3),
            dueAt = now,
        )
        val after = scheduler.review(weird, StudyGrade.GOOD, now)
        assertTrue(
            "稳定度不该变小：${weird.stability} -> ${after.stability}",
            after.stability >= weird.stability,
        )
        assertTrue("间隔也不该变小", ChronoUnit.DAYS.between(now, after.dueAt) >= 10L)
    }

    // ── 复习卡：手算参考值 ────────────────────────────────────────────────────

    @Test
    fun `复习卡点记住了_稳定度与间隔符合手算结果`() {
        val after = scheduler.review(reviewCardAt2Days(), StudyGrade.GOOD, now)
        assertEquals(StudyPhase.REVIEW, after.phase)
        assertEquals(10.964, after.stability, 0.05)            // 手算：2.3065 × (1 + 3.7537)
        assertEquals(11L, ChronoUnit.DAYS.between(now, after.dueAt))
    }

    @Test
    fun `复习卡点有点印象_稳定度与间隔符合手算结果`() {
        val after = scheduler.review(reviewCardAt2Days(), StudyGrade.HARD, now)
        assertEquals(StudyPhase.REVIEW, after.phase)
        assertEquals(7.513, after.stability, 0.05)             // 手算：2.3065 × (1 + 2.2575)
        assertEquals(8L, ChronoUnit.DAYS.between(now, after.dueAt))
    }

    @Test
    fun `复习卡点忘了_稳定度下降_进入学习步_遗忘次数加一`() {
        val before = reviewCardAt2Days()
        val after = scheduler.review(before, StudyGrade.AGAIN, now)
        assertEquals(StudyPhase.LEARNING, after.phase)
        assertEquals(0.607, after.stability, 0.05)             // 手算：被下限压到 0.607
        assertTrue("忘了之后稳定度必须变小", after.stability < before.stability)
        assertEquals(now.plusMinutes(5), after.dueAt)
        assertEquals(1, after.lapses)
        assertEquals(7.400, after.difficulty, 0.05)             // 手算：难度上升
    }

    @Test
    fun `三种评价里_忘了的稳定度最低_记住了最高`() {
        val card = reviewCardAt2Days()
        val again = scheduler.review(card, StudyGrade.AGAIN, now).stability
        val hard = scheduler.review(card, StudyGrade.HARD, now).stability
        val good = scheduler.review(card, StudyGrade.GOOD, now).stability
        assertTrue("忘了 < 有点印象", again < hard)
        assertTrue("有点印象 < 记住了", hard < good)
    }

    @Test
    fun `记住了的间隔严格大于有点印象的`() {
        val card = reviewCardAt2Days()
        val hardDays = ChronoUnit.DAYS.between(now, scheduler.review(card, StudyGrade.HARD, now).dueAt)
        val goodDays = ChronoUnit.DAYS.between(now, scheduler.review(card, StudyGrade.GOOD, now).dueAt)
        assertTrue("间隔必须满足 有点印象 < 记住了", goodDays > hardDays)
    }

    // ── 长期行为 ─────────────────────────────────────────────────────────────

    @Test
    fun `连续点记住了_间隔一次比一次长`() {
        var card = CardSchedule.newCard(now)
        var cursor = now
        val intervals = mutableListOf<Long>()
        repeat(5) {
            card = scheduler.review(card, StudyGrade.GOOD, cursor)
            intervals += ChronoUnit.DAYS.between(cursor, card.dueAt)
            cursor = card.dueAt
        }
        assertEquals("第一次应是 2 天", 2L, intervals[0])
        for (i in 1 until intervals.size) {
            assertTrue("第 $i 次（${intervals[i]} 天）必须大于上一次（${intervals[i - 1]} 天）", intervals[i] > intervals[i - 1])
        }
    }

    @Test
    fun `反复点忘了_卡片不会爆炸_稳定度始终为正`() {
        var card = reviewCardAt2Days()
        var cursor = now
        repeat(5) {
            card = scheduler.review(card, StudyGrade.AGAIN, cursor)
            cursor = card.dueAt
            assertTrue("稳定度必须大于 0", card.stability > 0.0)
            assertTrue("难度必须在 1 到 10 之间", card.difficulty in 1.0..10.0)
            assertTrue("遗忘次数应累计", card.lapses >= 1)
        }
    }

    @Test
    fun `难度始终落在1到10之间`() {
        var card = CardSchedule.newCard(now)
        var cursor = now
        val grades = listOf(
            StudyGrade.GOOD, StudyGrade.AGAIN, StudyGrade.GOOD, StudyGrade.HARD,
            StudyGrade.GOOD, StudyGrade.GOOD, StudyGrade.GOOD,
        )
        grades.forEach { grade ->
            card = scheduler.review(card, grade, cursor)
            assertTrue("难度越界: ${card.difficulty}", card.difficulty in 1.0..10.0)
            assertTrue("稳定度必须为正: ${card.stability}", card.stability > 0.0)
            cursor = maxOf(card.dueAt, cursor)
        }
    }

    @Test
    fun `同样的输入永远得到同样的结果_没有随机`() {
        val a = scheduler.review(reviewCardAt2Days(), StudyGrade.GOOD, now)
        val b = scheduler.review(reviewCardAt2Days(), StudyGrade.GOOD, now)
        assertEquals(a, b)
    }

    @Test
    fun `暂停的卡片不会因为评价而改变`() {
        val paused = CardSchedule.newCard(now).copy(phase = StudyPhase.SUSPENDED)
        assertEquals(paused, scheduler.review(paused, StudyGrade.GOOD, now))
    }

    // ── 界面要用的东西 ────────────────────────────────────────────────────────

    @Test
    fun `该不该复习的判断`() {
        val card = CardSchedule.newCard(now)
        assertTrue("新卡立刻该复习", scheduler.isDue(card, now))
        assertFalse("还没到时间就不该复习", scheduler.isDue(card.copy(dueAt = now.plusMinutes(1)), now))
        assertFalse("暂停的卡不该复习", scheduler.isDue(card.copy(phase = StudyPhase.SUSPENDED), now))
    }

    @Test
    fun `三个按钮都能给出人看得懂的到期说法`() {
        val previews = scheduler.previewAll(CardSchedule.newCard(now), now)
        assertEquals(3, previews.size)
        assertEquals(listOf("忘了", "有点印象", "记住了"), previews.map { it.grade.label })
        previews.forEach { assertTrue("标签不能为空", it.label.isNotBlank()) }
        assertEquals("5 分钟后", previews[0].label)
        assertEquals("10 分钟后", previews[1].label)
        assertEquals("2 天后", previews[2].label)
    }

    @Test
    fun `到期的说法覆盖分钟_明天_天_月_年`() {
        assertEquals("5 分钟后", scheduler.labelFor(now.plusMinutes(5), now))
        assertEquals("明天", scheduler.labelFor(now.plusDays(1), now))
        assertEquals("3 天后", scheduler.labelFor(now.plusDays(3), now))
        assertEquals("2 个月后", scheduler.labelFor(now.plusDays(61), now))
        assertEquals("1 年后", scheduler.labelFor(now.plusDays(400), now))
    }

    @Test
    fun `三个按钮给出三种不同结果`() {
        val previews = scheduler.previewAll(reviewCardAt2Days(), now)
        assertEquals(3, previews.map { it.dueAt }.toSet().size)
        assertNotEquals(previews[0].dueAt, previews[2].dueAt)
    }
}
