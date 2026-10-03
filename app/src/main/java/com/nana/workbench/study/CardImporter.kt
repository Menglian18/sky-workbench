package com.nana.workbench.study

/**
 * 批量导入卡片：粘贴多行文本，一行一张，正面与背面用 `|` 分开（全角的 `｜` 和制表符也认）。
 *
 * 规则：
 *  - 空白行忽略；
 *  - 没有分隔符的行**跳过**并计入 skipped（而不是硬凑成只有正面的卡片，避免导进一堆垃圾）；
 *  - 按**第一个**分隔符切开，所以背面里可以再出现 `|`。
 */
object CardImporter {

    data class ParsedCard(val front: String, val back: String)

    data class Result(val cards: List<ParsedCard>, val skipped: Int) {
        val isEmpty: Boolean get() = cards.isEmpty()
    }

    private val separators = charArrayOf('|', '｜', '\t')

    fun parse(text: String): Result {
        val cards = mutableListOf<ParsedCard>()
        var skipped = 0
        for (rawLine in text.split('\n')) {
            val line = rawLine.trim().trimEnd('\r')
            if (line.isEmpty()) continue
            val index = line.indexOfFirst { it in separators }
            if (index <= 0) {
                skipped++
                continue
            }
            val front = line.substring(0, index).trim()
            val back = line.substring(index + 1).trim()
            if (front.isEmpty()) {
                skipped++
                continue
            }
            cards += ParsedCard(front = front, back = back)
        }
        return Result(cards = cards, skipped = skipped)
    }
}
