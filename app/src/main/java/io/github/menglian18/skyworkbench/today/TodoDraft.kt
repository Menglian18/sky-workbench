package io.github.menglian18.skyworkbench.today

/**
 * 新建 / 编辑待办时的草稿，以及"什么内容算能存"的规则。
 *
 * 集中在这里的原因：新建面板和编辑弹窗**必须遵守同一套规则**，
 * 而且上一版就是这里出的问题 —— 点"完成"只关了面板、没保存，用户以为添加了。
 * 规则写成可测的纯数据，就不会各写一套。
 */
data class TodoDraft(
    val title: String = "",
    val note: String = "",
    val priority: Int = 0,
    /** "08:30" 这样的时间点；空的话要存成 null，而不是空串 */
    val timePoint: String? = null,
    /** 这条事落到哪一天（yyyy-MM-dd）；空表示用当前查看的日期 */
    val dateKey: String? = null,
) {
    val cleanTitle: String get() = title.trim()
    val cleanNote: String get() = note.trim()
    val cleanTimePoint: String? get() = timePoint?.trim()?.takeIf { it.isNotEmpty() }
    val cleanDateKey: String? get() = dateKey?.trim()?.takeIf { it.isNotEmpty() }

    /** 标题为空（或只有空格）就不该存 —— 点"完成"也不能凭空多出一条空记录 */
    val isSavable: Boolean get() = cleanTitle.isNotEmpty()

    companion object {
        fun from(title: String, note: String = "", priority: Int = 0, timePoint: String? = null): TodoDraft =
            TodoDraft(title = title, note = note, priority = priority, timePoint = timePoint)
    }
}
