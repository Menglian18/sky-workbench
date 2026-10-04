package io.github.menglian18.skyworkbench.study

/**
 * 学科名与任务内容的整理规则（1.1 改进 · B 模块：学科 + 任务）。
 *
 * 规则集中在这里，界面上"新建学科""改名""加任务"都走同一套，避免各写一份。
 */

/** 学科名最长多少个字（太长会把上面的标签挤爆） */
const val SUBJECT_MAX_LENGTH = 20

/**
 * 整理学科名：
 *  - 去掉前后空格；中间连续的空白压成一个空格；
 *  - 全是空白的不接受（返回 null，界面据此提示）；
 *  - 超长会截断。
 */
fun normalizeSubject(raw: String): String? {
    val name = raw.trim().replace(Regex("\\s+"), " ")
    if (name.isEmpty()) return null
    return name.take(SUBJECT_MAX_LENGTH)
}

/**
 * 任务内容的整理：去掉前后空格；空的不接受。
 */
fun normalizeTaskTitle(raw: String): String? {
    val title = raw.trim()
    return title.ifEmpty { null }
}

/**
 * 把"数据库里已有的学科"和"刚建好、还没往里加任务的学科"合起来，去重并排好序。
 *
 * 为什么需要：学科不是单独一张表 —— 一个学科之所以存在，是因为有任务挂在它下面。
 * 所以刚点"新建学科"时它还不存在于数据库，这里要把它也显示出来（不然像是没建成）。
 */
fun mergeSubjects(fromDatabase: List<String>, pending: List<String>): List<String> =
    (fromDatabase + pending)
        .mapNotNull { normalizeSubject(it) }
        .distinct()
        .sorted()
