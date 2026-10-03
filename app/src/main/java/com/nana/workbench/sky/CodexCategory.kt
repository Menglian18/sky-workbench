package com.nana.workbench.sky

/**
 * 图鉴的游戏内分类（1.1 改进 · C3：图鉴按游戏分类）。
 *
 * 数据里的 `type` 是英文（Cape / Mask / Hair …），这里给出中文名、展示顺序，
 * 以及"值不值得在图鉴里露出来"（Special 是占位、Quest 是任务物品，堆在图鉴里没意义）。
 */
enum class CodexCategory(
    val type: String,
    val label: String,
    val order: Int,
    val showInCodex: Boolean = true,
) {
    CAPE("Cape", "斗篷", 1),
    OUTFIT("Outfit", "套装", 2),
    HAIR("Hair", "发型", 3),
    MASK("Mask", "面具", 4),
    HAIR_ACCESSORY("HairAccessory", "发饰", 5),
    FACE_ACCESSORY("FaceAccessory", "面饰", 6),
    NECKLACE("Necklace", "项链挂饰", 7),
    HELD("Held", "手持物", 8),
    PROP("Prop", "道具", 9),
    SHOES("Shoes", "鞋子", 10),
    MUSIC("Music", "乐器乐谱", 11),
    WING_BUFF("WingBuff", "光翼加成", 12),
    EMOTE("Emote", "表情动作", 13),
    QUEST("Quest", "任务物品", 14, showInCodex = false),
    SPECIAL("Special", "其他占位", 15, showInCodex = false),
    ;

    companion object {
        /** 按展示顺序排好 */
        val displayOrder: List<CodexCategory> get() = entries.sortedBy { it.order }

        /** 图鉴里真正会显示的那些分类 */
        val shownInCodex: List<CodexCategory> get() = displayOrder.filter { it.showInCodex }

        /** 认不出的分类一律归到"其他占位"，不崩 */
        fun fromType(type: String): CodexCategory =
            entries.firstOrNull { it.type.equals(type.trim(), ignoreCase = true) } ?: SPECIAL

        /** 分类的中文名；认不出就给"其他占位" */
        fun labelOf(type: String): String = fromType(type).label

        /**
         * 按分类分组（只保留要展示的分类；空分类不出现；组内按名字排序）。
         * 泛型是为了不绑死具体的数据类 —— 界面和测试都能用。
         *
         * 排序用中文排序器（按拼音），不能用默认的字符串比较：
         * 后者是按字符编码排的，中文看起来就是乱序（"乙"会排到"甲"前面）。
         */
        fun <T> group(
            items: List<T>,
            typeOf: (T) -> String,
            nameOf: (T) -> String,
        ): List<Pair<CodexCategory, List<T>>> {
            val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
            return shownInCodex.mapNotNull { category ->
                val group = items
                    .filter { fromType(typeOf(it)) == category }
                    .sortedWith(compareBy(collator) { nameOf(it) })
                if (group.isEmpty()) null else category to group
            }
        }
    }
}
