package com.nana.workbench.study

import com.nana.workbench.data.db.StudyCardEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * M5 阶段的测试之一：卡片状态与数据库记录的互转。
 * 这里错了会让复习节奏悄悄乱掉，所以字段逐个核对。
 */
class StudyCardMapperTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val moment: LocalDateTime = LocalDateTime.of(2026, 10, 9, 20, 0)

    private fun entity() = StudyCardEntity(
        id = 7,
        front = "正面",
        back = "背面",
        note = "备注",
        phase = StudyPhase.REVIEW.ordinal,
        stability = 12.5,
        difficulty = 3.25,
        reps = 4,
        lapses = 1,
        lastReviewedAt = StudyCardMapper.toMillis(moment.minusDays(2), zone),
        dueAt = StudyCardMapper.toMillis(moment, zone),
        createdAt = 111L,
        updatedAt = 222L,
    )

    @Test
    fun `从记录读出的状态与字段一致`() {
        val schedule = StudyCardMapper.toSchedule(entity(), zone)
        assertEquals(StudyPhase.REVIEW, schedule.phase)
        assertEquals(12.5, schedule.stability, 1e-9)
        assertEquals(3.25, schedule.difficulty, 1e-9)
        assertEquals(4, schedule.reps)
        assertEquals(1, schedule.lapses)
        assertEquals(moment.minusDays(2), schedule.lastReviewedAt)
        assertEquals(moment, schedule.dueAt)
    }

    @Test
    fun `写回记录时只动复习相关字段`() {
        val original = entity()
        val newSchedule = CardSchedule(
            phase = StudyPhase.LEARNING,
            stability = 0.6,
            difficulty = 7.4,
            reps = 5,
            lapses = 2,
            lastReviewedAt = moment,
            dueAt = moment.plusMinutes(5),
        )
        val updated = StudyCardMapper.applyTo(original, newSchedule, zone, updatedAt = 999L)

        assertEquals(StudyPhase.LEARNING.ordinal, updated.phase)
        assertEquals(0.6, updated.stability, 1e-9)
        assertEquals(7.4, updated.difficulty, 1e-9)
        assertEquals(5, updated.reps)
        assertEquals(2, updated.lapses)
        assertEquals(moment.plusMinutes(5), StudyCardMapper.toLocal(updated.dueAt, zone))
        assertEquals(999L, updated.updatedAt)
        // 内容不能被改动
        assertEquals(original.front, updated.front)
        assertEquals(original.back, updated.back)
        assertEquals(original.note, updated.note)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun `来回转换不丢精度`() {
        val original = entity()
        val roundTrip = StudyCardMapper.applyTo(
            original,
            StudyCardMapper.toSchedule(original, zone),
            zone,
            updatedAt = original.updatedAt,
        )
        assertEquals(original, roundTrip)
    }

    @Test
    fun `阶段的序号顺序固定`() {
        assertEquals(0, StudyPhase.NEW.ordinal)
        assertEquals(1, StudyPhase.LEARNING.ordinal)
        assertEquals(2, StudyPhase.REVIEW.ordinal)
        assertEquals(3, StudyPhase.SUSPENDED.ordinal)
    }

    @Test
    fun `序号越界时按新卡处理`() {
        val broken = entity().copy(phase = 99)
        assertEquals(StudyPhase.NEW, StudyCardMapper.toSchedule(broken, zone).phase)
    }

    @Test
    fun `没有复习过的卡片_lastReviewedAt 为空`() {
        val fresh = entity().copy(lastReviewedAt = null)
        assertEquals(null, StudyCardMapper.toSchedule(fresh, zone).lastReviewedAt)
        val back = StudyCardMapper.applyTo(fresh, StudyCardMapper.toSchedule(fresh, zone), zone)
        assertEquals(null, back.lastReviewedAt)
    }
}
