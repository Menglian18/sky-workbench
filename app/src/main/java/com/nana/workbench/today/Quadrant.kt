package com.nana.workbench.today

/**
 * 紧急 / 重要四象限（1.1 改进第 3 条）。
 *
 * 编码直接复用数据库里原来的 `priority` 字段（Int，不用改表结构）：
 * 用两个比特位表示两条轴 —— 第 1 位是"重要"，第 0 位是"紧急"。
 *
 *   3 = 重要且紧急（马上做）
 *   2 = 重要不紧急（安排时间做）
 *   1 = 紧急不重要（尽快处理）
 *   0 = 都不（有空再说）
 *
 * 旧版本存的 1（重要）/ 2（紧急）语义和现在不同，升级时用 [legacyPriorityToQuadrantCode] 换过来。
 */
enum class Quadrant(
    val code: Int,
    /** 完整说法，用在首页四象限卡片上 */
    val title: String,
    /** 简短说法，用在按钮上（屏幕窄） */
    val shortTitle: String,
    /** 建议怎么处理 */
    val advice: String,
) {
    DO_FIRST(3, "重要又紧急", "重要且紧急", "马上做"),
    SCHEDULE(2, "重要不紧急", "重要不紧急", "安排时间做"),
    DELEGATE(1, "紧急不重要", "紧急不重要", "尽快处理"),
    ELIMINATE(0, "不重要也不紧急", "都不", "有空再说"),
    ;

    companion object {
        /** 显示顺序：从最该做的开始 */
        val ordered: List<Quadrant> = listOf(DO_FIRST, SCHEDULE, DELEGATE, ELIMINATE)

        /** 认不出的值一律当"都不"，不崩 */
        fun fromCode(code: Int): Quadrant = ordered.firstOrNull { it.code == code } ?: ELIMINATE
    }
}

/** 这条待办属于哪一格 */
fun quadrantOf(priority: Int): Quadrant = Quadrant.fromCode(priority)

/**
 * 旧版优先级的换算（数据库迁移用）。
 * 旧版：0 普通 / 1 重要 / 2 紧急。
 * 因为迁移要在 SQLite 里跑，SQL 里也写了同一套映射（见 NanaDatabase），所以这里保持简单可核对。
 */
fun legacyPriorityToQuadrantCode(legacy: Int): Int = when (legacy) {
    2 -> Quadrant.DO_FIRST.code
    1 -> Quadrant.SCHEDULE.code
    else -> Quadrant.ELIMINATE.code
}
