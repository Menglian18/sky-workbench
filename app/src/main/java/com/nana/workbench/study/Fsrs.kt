package com.nana.workbench.study

import com.nana.workbench.util.LogicalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 界面上的三个按钮。对应 FSRS 四档评价里的前三档。
 *
 * 为什么不做第四档（"太简单"）：对使用者来说，三个按钮已经够用，多一个反而要想半天。
 */
enum class StudyGrade(val label: String, val rating: Int) {
    AGAIN("忘了", 1),
    HARD("有点印象", 2),
    GOOD("记住了", 3);

    companion object {
        val default: StudyGrade = GOOD
        fun byRating(rating: Int): StudyGrade? = entries.firstOrNull { it.rating == rating }
    }
}

/** 卡片所处的阶段。 */
enum class StudyPhase {
    /** 从没复习过。 */
    NEW,

    /** 学习步：几分钟内还要再看一次（刚学、或者刚忘了）。 */
    LEARNING,

    /** 已进入按"天"计算的正常复习。 */
    REVIEW,

    /** 被使用者暂停，不再出现在今天该复习里。 */
    SUSPENDED,
}

/** 一张卡片的复习状态（与数据库字段一一对应，便于存取）。 */
data class CardSchedule(
    val phase: StudyPhase,
    val stability: Double,
    val difficulty: Double,
    val reps: Int,
    val lapses: Int,
    val lastReviewedAt: LocalDateTime?,
    val dueAt: LocalDateTime,
) {
    companion object {
        /** 新建卡片时的初始状态：立刻可以复习。 */
        fun newCard(now: LocalDateTime): CardSchedule = CardSchedule(
            phase = StudyPhase.NEW,
            stability = 0.0,
            difficulty = 0.0,
            reps = 0,
            lapses = 0,
            lastReviewedAt = null,
            dueAt = now,
        )
    }
}

/** 某个按钮按下去之后会发生什么（给界面显示"下次复习：2 天后"用）。 */
data class GradePreview(
    val grade: StudyGrade,
    val dueAt: LocalDateTime,
    val label: String,
)

/**
 * 复习节奏算法：FSRS v6（Free Spaced Repetition Scheduler，公开发表的算法）。
 *
 * 实现说明与出处：
 *  - 公式依据 open-spaced-repetition 组织公开发表的 FSRS v6 规范与参考实现。
 *  - **没有照抄** open-spaced-repetition/FSRS-Kotlin 的代码：那份实现里有四处与规范不符
 *    （初始稳定度被写成了"取不大于 0.1"、难度阻尼漏了括号、遗忘曲线用了指数而非幂函数、
 *    间隔没有按 硬<良<易 做递增约束）。这里按规范重写，因此行为与那份实现不同。
 *  - 默认参数取自该组织公布的 FSRS-6 默认值。
 *  - 关闭随机抖动（fuzz）：同样的输入必须得到同样的结果，这样行为可预测、可测试。
 */
class FsrsScheduler(
    val requestRetention: Double = DEFAULT_RETENTION,
    val params: List<Double> = DEFAULT_PARAMS,
    val maxIntervalDays: Int = 36500,
    val againDelayMinutes: Long = 5,
    val hardDelayMinutes: Long = 10,
) {

    init {
        require(params.size >= 21) { "FSRS 参数需要至少 21 个，实际 ${params.size} 个" }
        require(requestRetention > 0.0 && requestRetention < 1.0) { "目标记住率必须在 0 与 1 之间" }
    }

    /** FSRS-6 的衰减系数（规范里是 -w[20]）。 */
    private val decay: Double = -params[20]

    /** FSRS-6 的缩放系数。 */
    private val factor: Double = 0.9.pow(1.0 / decay) - 1.0

    // ── 基础公式 ──────────────────────────────────────────────────────────────

    /**
     * 遗忘曲线：过了 [elapsedDays] 天之后，还能记住的概率。
     *
     * 结果被夹在 0～1 之间。这是必要的保险：如果 [elapsedDays] 是负数
     * （用户手动改过手机时间、或系统时间回拨），原式会算出大于 1 的概率，
     * 进而让"记住了"反而降低稳定度 —— 也就是越复习越差。
     */
    fun forgettingCurve(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val days = elapsedDays.coerceAtLeast(0.0)
        return (1.0 + factor * days / stability).pow(decay).coerceIn(0.0, 1.0)
    }

    /** 由稳定度算出间隔天数。默认目标记住率下，间隔约等于稳定度。 */
    fun intervalDaysFor(stability: Double): Int {
        if (stability <= 0.0) return 1
        val raw = stability / factor * (requestRetention.pow(1.0 / decay) - 1.0)
        return raw.roundToInt().coerceIn(1, maxIntervalDays)
    }

    /** 第一次复习时的初始稳定度：取对应的参数，且不小于 0.1 天。 */
    fun initStability(grade: StudyGrade): Double = max(params[grade.rating - 1], MIN_STABILITY)

    /** 第一次复习时的初始难度（1～10，越小越容易）。 */
    fun initDifficulty(grade: StudyGrade): Double = initDifficultyForRating(grade.rating)

    /**
     * 规范里 next_difficulty 的"回归均值"目标，用的是**评价=4（简单）**的初始难度。
     * 我们界面上没有"简单"这个按钮，所以单独按档位算，而不是拿某个按钮的难度顶替。
     */
    private fun initDifficultyForRating(rating: Int): Double =
        (params[4] - exp(params[5] * (rating - 1)) + 1.0).coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    /** 难度变化的阻尼：难度越高，同样的评价带来的变化越小。 */
    internal fun linearDamping(delta: Double, oldDifficulty: Double): Double =
        delta * (10.0 - oldDifficulty) / 9.0

    /** 难度回归均值，避免难度一路飘走。 */
    internal fun meanReversion(initDifficulty: Double, nextDifficulty: Double): Double =
        params[7] * initDifficulty + (1.0 - params[7]) * nextDifficulty

    /** 复习后的新难度。 */
    fun nextDifficulty(currentDifficulty: Double, grade: StudyGrade): Double {
        val delta = -params[6] * (grade.rating - 3)
        val damped = linearDamping(delta, currentDifficulty)
        val reverted = meanReversion(initDifficultyForRating(EASY_RATING), currentDifficulty + damped)
        return reverted.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    /** 同一轮（学习步）里的稳定度变化。 */
    fun nextShortTermStability(currentStability: Double, grade: StudyGrade): Double {
        var sinc = exp(params[17] * (grade.rating - 3 + params[18])) *
            currentStability.coerceAtLeast(MIN_STABILITY).pow(-params[19])
        if (grade.rating >= 3) sinc = max(sinc, 1.0)
        return (currentStability.coerceAtLeast(MIN_STABILITY) * sinc).coerceAtLeast(MIN_STABILITY)
    }

    /** 忘记之后的稳定度（会变小，所以下次很快又要看）。 */
    fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val floor = stability / exp(params[17] * params[18])
        val result = params[11] *
            difficulty.coerceAtLeast(MIN_DIFFICULTY).pow(-params[12]) *
            ((stability + 1.0).pow(params[13]) - 1.0) *
            exp((1.0 - retrievability) * params[14])
        return minOf(result, floor).coerceAtLeast(MIN_STABILITY)
    }

    /** 记住之后的稳定度（会变大）。 */
    fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        grade: StudyGrade,
    ): Double {
        val hardPenalty = if (grade == StudyGrade.HARD) params[15] else 1.0
        val easyBonus = if (grade.rating == 4) params[16] else 1.0
        val growth = exp(params[8]) *
            (11.0 - difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)) *
            stability.coerceAtLeast(MIN_STABILITY).pow(-params[9]) *
            (exp((1.0 - retrievability) * params[10]) - 1.0) *
            hardPenalty * easyBonus
        return (stability * (1.0 + growth)).coerceAtLeast(MIN_STABILITY)
    }

    // ── 主流程 ────────────────────────────────────────────────────────────────

    /** 按一次按钮之后，卡片的新状态。 */
    fun review(card: CardSchedule, grade: StudyGrade, now: LocalDateTime): CardSchedule =
        when (card.phase) {
            StudyPhase.SUSPENDED -> card
            StudyPhase.NEW -> reviewNew(card, grade, now)
            StudyPhase.LEARNING -> reviewLearning(card, grade, now)
            StudyPhase.REVIEW -> reviewReview(card, grade, now)
        }

    private fun reviewNew(card: CardSchedule, grade: StudyGrade, now: LocalDateTime): CardSchedule {
        val difficulty = initDifficulty(grade)
        val stability = initStability(grade)
        return if (grade == StudyGrade.AGAIN || grade == StudyGrade.HARD) {
            CardSchedule(
                phase = StudyPhase.LEARNING,
                stability = stability,
                difficulty = difficulty,
                reps = card.reps + 1,
                lapses = card.lapses,
                lastReviewedAt = now,
                dueAt = now.plusMinutes(if (grade == StudyGrade.AGAIN) againDelayMinutes else hardDelayMinutes),
            )
        } else {
            CardSchedule(
                phase = StudyPhase.REVIEW,
                stability = stability,
                difficulty = difficulty,
                reps = card.reps + 1,
                lapses = card.lapses,
                lastReviewedAt = now,
                dueAt = now.plusDays(intervalDaysFor(stability).toLong()),
            )
        }
    }

    private fun reviewLearning(card: CardSchedule, grade: StudyGrade, now: LocalDateTime): CardSchedule {
        val difficulty = nextDifficulty(card.difficulty.coerceAtLeast(MIN_DIFFICULTY), grade)
        val stability = nextShortTermStability(card.stability, grade)
        return if (grade == StudyGrade.AGAIN || grade == StudyGrade.HARD) {
            CardSchedule(
                phase = StudyPhase.LEARNING,
                stability = stability,
                difficulty = difficulty,
                reps = card.reps + 1,
                lapses = card.lapses,
                lastReviewedAt = now,
                dueAt = now.plusMinutes(if (grade == StudyGrade.AGAIN) againDelayMinutes else hardDelayMinutes),
            )
        } else {
            CardSchedule(
                phase = StudyPhase.REVIEW,
                stability = stability,
                difficulty = difficulty,
                reps = card.reps + 1,
                lapses = card.lapses,
                lastReviewedAt = now,
                dueAt = now.plusDays(intervalDaysFor(stability).toLong()),
            )
        }
    }

    private fun reviewReview(card: CardSchedule, grade: StudyGrade, now: LocalDateTime): CardSchedule {
        // 第二道保险：即使时间被改乱了，间隔也按"至少 0 天"算
        val elapsedDays = elapsedDaysBetween(card.lastReviewedAt ?: now, now).coerceAtLeast(0.0)
        val retrievability = forgettingCurve(elapsedDays, card.stability)
        val difficulty = nextDifficulty(card.difficulty.coerceAtLeast(MIN_DIFFICULTY), grade)

        return when (grade) {
            StudyGrade.AGAIN -> CardSchedule(
                phase = StudyPhase.LEARNING,
                stability = nextForgetStability(card.difficulty.coerceAtLeast(MIN_DIFFICULTY), card.stability, retrievability),
                difficulty = difficulty,
                reps = card.reps + 1,
                lapses = card.lapses + 1,
                lastReviewedAt = now,
                dueAt = now.plusMinutes(againDelayMinutes),
            )

            StudyGrade.HARD -> {
                val stability = nextRecallStability(card.difficulty, card.stability, retrievability, StudyGrade.HARD)
                CardSchedule(
                    phase = StudyPhase.REVIEW,
                    stability = stability,
                    difficulty = difficulty,
                    reps = card.reps + 1,
                    lapses = card.lapses,
                    lastReviewedAt = now,
                    dueAt = now.plusDays(intervalDaysFor(stability).toLong()),
                )
            }

            StudyGrade.GOOD -> {
                val hardStability = nextRecallStability(card.difficulty, card.stability, retrievability, StudyGrade.HARD)
                val goodStability = nextRecallStability(card.difficulty, card.stability, retrievability, StudyGrade.GOOD)
                // 规范和参考实现一致：间隔必须满足 忘了 < 有点印象 < 记住了
                val days = maxOf(intervalDaysFor(goodStability), intervalDaysFor(hardStability) + 1)
                CardSchedule(
                    phase = StudyPhase.REVIEW,
                    stability = goodStability,
                    difficulty = difficulty,
                    reps = card.reps + 1,
                    lapses = card.lapses,
                    lastReviewedAt = now,
                    dueAt = now.plusDays(days.toLong()),
                )
            }
        }
    }

    /** 三个按钮各自会导致什么（界面显示"下次复习：…"）。 */
    fun previewAll(card: CardSchedule, now: LocalDateTime): List<GradePreview> =
        listOf(StudyGrade.AGAIN, StudyGrade.HARD, StudyGrade.GOOD).map { grade ->
            val next = review(card, grade, now)
            GradePreview(grade = grade, dueAt = next.dueAt, label = labelFor(next.dueAt, now))
        }

    /** 这张卡现在该不该复习。 */
    fun isDue(card: CardSchedule, now: LocalDateTime): Boolean =
        card.phase != StudyPhase.SUSPENDED && !card.dueAt.isAfter(now)

    /** 人类看得懂的到期说法。 */
    fun labelFor(dueAt: LocalDateTime, now: LocalDateTime): String {
        val minutes = ChronoUnit.MINUTES.between(now, dueAt)
        if (minutes < 60) return "${max(minutes, 0)} 分钟后"
        val days = ChronoUnit.DAYS.between(LogicalDate.of(now), LogicalDate.of(dueAt))
        return when {
            days <= 0L -> "${minutes} 分钟后"
            days == 1L -> "明天"
            days < 30L -> "${days} 天后"
            days < 365L -> "${days / 30} 个月后"
            else -> "${days / 365} 年后"
        }
    }

    private fun elapsedDaysBetween(from: LocalDateTime, to: LocalDateTime): Double =
        ChronoUnit.MINUTES.between(from, to).toDouble() / 1440.0

    internal companion object {
        const val MIN_STABILITY = 0.1
        const val MIN_DIFFICULTY = 1.0
        const val MAX_DIFFICULTY = 10.0

        const val DEFAULT_RETENTION = 0.9

        /** 规范里"回归均值"的目标档位：评价 = 4（简单）。 */
        const val EASY_RATING = 4

        /** FSRS-6 官方默认参数（21 个）。 */
        val DEFAULT_PARAMS: List<Double> = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194,
            0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629,
            1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )
    }
}
