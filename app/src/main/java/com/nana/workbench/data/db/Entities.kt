package com.nana.workbench.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 全部 12 张表（《开发计划》§4.1）。
 *
 * 约定：
 *  - 日期一律存成 `yyyy-MM-dd` 字符串，且**都是"逻辑日期"**（凌晨 4 点日界的产物，见 LogicalDate）。
 *  - 时间点一律存成毫秒时间戳（Long）。
 *  - 抓来的光遇数据（表 10、11）与用户自己记的数据**分开存放**，所以"一键清理"可以整块删掉抓来的，
 *    而不会碰到用户数据。
 */

// ── 1. 今日计划 ──────────────────────────────────────────────────────────────

@Entity(tableName = "todo_item")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** 0 普通 / 1 重要 / 2 紧急 */
    val priority: Int = 0,
    /** "08:30" 这样的时间点，可以没有 */
    val timePoint: String? = null,
    val done: Boolean = false,
    /** 最初是哪一天的事 */
    val originalDate: String,
    /** 现在落在哪一天（顺延会改这个） */
    val dueDate: String,
    /** 已经被顺延了多少天 */
    val postponedCount: Int = 0,
    val doneAt: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

// ── 2. 学习任务（1.1 改进：原"学习卡片"改成"学科 + 任务"）────────────────────
//
// 说明：1.1 起不再按遗忘曲线排复习，所以 phase / stability / difficulty / reps /
// lapses / lastReviewedAt / dueAt 这些字段**不再参与任何逻辑**，只是为了让老数据
// （如果用户之前用过卡片）留着不丢。任务内容存在 front 里。

@Entity(tableName = "study_card")
data class StudyCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 任务内容 */
    val front: String,
    /** 老字段，留空不用 */
    val back: String = "",
    /** 老字段，留空不用 */
    val note: String = "",
    /** 学科名（用户自己填，例如"数学"）；空字符串表示还没归到任何学科 */
    @ColumnInfo(defaultValue = "''")
    val subject: String = "",
    /** 同一学科内的手工顺序（拖拽调整） */
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    /** 划掉了没有 */
    @ColumnInfo(defaultValue = "0")
    val done: Boolean = false,
    /** 老字段：StudyPhase 的序号，不再使用 */
    val phase: Int = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long? = null,
    val dueAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

// ── 3. 复习记录 ──────────────────────────────────────────────────────────────

@Entity(tableName = "study_review_log", indices = [Index("cardId")])
data class StudyReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    /** 1 忘了 / 2 有点印象 / 3 记住了 */
    val rating: Int,
    val reviewedAt: Long,
    val elapsedDays: Double,
    val scheduledDays: Int,
)

// ── 4. 生活备忘 ──────────────────────────────────────────────────────────────

@Entity(tableName = "memo_item")
data class MemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "其他",
    val pinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

// ── 5. 数字表格：表格定义 ────────────────────────────────────────────────────

@Entity(tableName = "number_table")
data class NumberTableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String = "",
    val decimals: Int = 1,
    val allowMultiplePerDay: Boolean = false,
    val sortOrder: Int = 0,
)

// ── 6. 数字表格：记录 ────────────────────────────────────────────────────────

@Entity(tableName = "number_record", indices = [Index(value = ["tableId", "date"])])
data class NumberRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val date: String,
    val value: Double,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

// ── 7. 光遇账号档案（不存密码） ──────────────────────────────────────────────

@Entity(tableName = "sky_account")
data class SkyAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    /** 华为 / 微信 / QQ / 手机号 / 邮箱 / 其他 */
    val loginMethod: String = "",
    /** 国服 / 国际服 */
    val server: String = "国服",
    val binding: String = "",
    val note: String = "",
    val sortOrder: Int = 0,
)

// ── 8. 光遇愿望清单 ──────────────────────────────────────────────────────────

@Entity(tableName = "sky_wish")
data class SkyWishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemGuid: String = "",
    val itemName: String,
    val spiritName: String = "",
    val costCandle: Int = 0,
    val costHeart: Int = 0,
    val costAscended: Int = 0,
    /** 已经拥有（兑换树里算差价用） */
    val owned: Boolean = false,
    /** 已经换到手 */
    val obtained: Boolean = false,
    val sortOrder: Int = 0,
)

// ── 9. 光遇当前持有蜡烛数（只填一次） ────────────────────────────────────────

@Entity(tableName = "sky_holding")
data class SkyHoldingEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val candles: Int = 0,
    val hearts: Int = 0,
    val ascended: Int = 0,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

// ── 10. 光遇复刻（抓来的，可整块清空） ───────────────────────────────────────

@Entity(tableName = "sky_traveling_spirit")
data class SkyTravelingSpiritEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spiritName: String,
    /** 旅行先祖 / 集体回归 */
    val type: String = "旅行先祖",
    val startDate: String,
    val endDate: String,
    val costCandle: Int = 0,
    val costHeart: Int = 0,
    val costAscended: Int = 0,
    /** 物品清单，用顿号分隔的纯文本 */
    val items: String = "",
    val source: String = "",
    val fetchedAt: Long,
    /** 手动改过的条目，联网更新时不覆盖 */
    val manuallyEdited: Boolean = false,
)

// ── 11. 光遇季节（抓来的，可整块清空） ───────────────────────────────────────

@Entity(tableName = "sky_season")
data class SkySeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    val endDate: String,
    val fetchedAt: Long,
    val manuallyEdited: Boolean = false,
)

// ── 12. 全局状态（上次抓取时间、上次导出时间、上次清理时间等） ────────────────

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)
