package io.github.menglian18.skyworkbench.study

import io.github.menglian18.skyworkbench.data.db.StudyCardEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 数据库记录 ↔ 算法状态 的互转。
 *
 * 单独抽出来是因为：这里一旦映射错（比如阶段序号搞反、时间戳和本地时间互转错），
 * 复习节奏会**悄悄乱掉**，而且很难发现。所以它有专门的测试。
 */
object StudyCardMapper {

    fun toSchedule(card: StudyCardEntity, zone: ZoneId = ZoneId.systemDefault()): CardSchedule =
        CardSchedule(
            phase = StudyPhase.entries.getOrElse(card.phase) { StudyPhase.NEW },
            stability = card.stability,
            difficulty = card.difficulty,
            reps = card.reps,
            lapses = card.lapses,
            lastReviewedAt = card.lastReviewedAt?.let { toLocal(it, zone) },
            dueAt = toLocal(card.dueAt, zone),
        )

    /** 把算法算出来的新状态写回数据库记录（其他字段保持不变）。 */
    fun applyTo(
        card: StudyCardEntity,
        schedule: CardSchedule,
        zone: ZoneId = ZoneId.systemDefault(),
        updatedAt: Long = System.currentTimeMillis(),
    ): StudyCardEntity = card.copy(
        phase = schedule.phase.ordinal,
        stability = schedule.stability,
        difficulty = schedule.difficulty,
        reps = schedule.reps,
        lapses = schedule.lapses,
        lastReviewedAt = schedule.lastReviewedAt?.let { toMillis(it, zone) },
        dueAt = toMillis(schedule.dueAt, zone),
        updatedAt = updatedAt,
    )

    fun toLocal(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun toMillis(moment: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        moment.atZone(zone).toInstant().toEpochMilli()
}
