package io.github.menglian18.skyworkbench.sky

/**
 * 「哪些东西是给 WIKI 编辑者看的、玩家完全不需要」（1.4.1 新增）。
 *
 * 用户对 1.4.0 的反馈原话：「点击进入后的界面太乱」「有些东西完全不需要」
 * 「已经结束的就不要了，关注当下」。
 *
 * 乱在哪：App 里显示的是 WIKI 的**正文**（`action=render`），不带站点的样式表，
 * 于是平时被样式表藏起来的东西全露出来了。规则都是**拿真实页面结构对过的**：
 *
 * | 页面上的东西 | 真实结构 | 怎么处理 |
 * |---|---|---|
 * | 「首页 > 节日活动 > 秋宵节」+「短 刷 编 历」 | `div.crumb`（面包屑模板） | CSS 藏掉整个 `.crumb` |
 * | 目录 | `div#toc`（在 `.toc-sticky` 栅格里） | CSS 藏掉，空列不留痕 |
 * | 同一段公告出现两遍 | `div.hidden-xs`（带表格）和 `div.visible-xs`（图在上） | CSS 藏掉 `.visible-xs`，留表格那份 |
 * | 「暂无展示图『点此上传』规格1280*256」 | 一个没有 class 的 `div`，缩略图和提示**同在一个 div 里** | 整块删（这块本身就是"没人传图"的占位） |
 * | 底部「节日 / 往期页面」表 | `table.wikitable`，表头单元格文字正好是「往期页面」 | 认文字 → 连整张表端掉 |
 * | 每个标题后的「[编辑]」 | `span.mw-editsection` | CSS 藏掉 |
 *
 * 规则全是**纯函数 + 正则**，所以能在电脑上拿**真实文字**逐条测；同一份正则也会交给
 * 注入网页的脚本用（见 [WikiStyle.cleanupScript]），保证"电脑上测过的"就是"手机上跑的"。
 */
object WikiNoise {

    /** 网页上的空白很杂：普通空格、换行、不换行空格（&#160;）、全角空格都要当成空白 */
    private val WHITESPACE = Regex("[\\s\\u00A0\\u3000]+")

    /**
     * 「这一整块就是在说这件事」的提示 —— **连块里的小图一起删**。
     *
     * 单独一类的原因：这种块里除了提示文字还挂着一张 80px 的缩略图，
     * 要是只删文字，会剩下一堆没头没尾的小图，看着更乱。
     */
    private val BLOCK_NOISE: List<Regex> = listOf(
        // 暂无展示图『点此上传』规格1280*256
        Regex("^暂无展示图.*规格\\s*\\d+\\s*[*×xX]\\s*\\d+$"),
        Regex("^暂无展示图$"),
        Regex("^暂无图片$"),
    )

    /**
     * 「整段文字长这样」就把这块拿掉。正则有 `^…$`，所以正文里出现"点此上传"这几个字
     * 不会被误删，只有"这一块整块就是在说这件事"才命中。
     */
    private val NOISE_TEXT: List<Regex> = listOf(
        Regex("^点此上传$"),
        Regex("^举报$"),
        Regex("^刷新缓存$"),
        // WIKI 左上角那排编辑工具：「短 • 刷 • 编 • 历」
        Regex("^短[\\s·•・|、]*刷[\\s·•・|、]*编[\\s·•・|、]*历$"),
        // 面包屑：「首页 > 节日活动 > 秋宵节 > 2026年秋宵节」
        Regex("^首页\\s*[>›»→]\\s*\\S.{0,80}$"),
        // 红链提示：「2024年有友节（页面不存在）」
        Regex("^.+（页面不存在）$"),
    )

    /**
     * 一个小标题/表头长这样就"整块端掉"（连它下面的表格一起）。
     * 页面底部那张「节日 | 往期页面」表列的全是往年活动 —— 已经结束的，不要。
     */
    private val PAST_TITLE: List<Regex> = listOf(
        Regex("^往期(页面|活动|礼包|兑换|内容|先祖)?$"),
        Regex("^历史(活动|页面|记录|复刻)?$"),
        Regex("^已结束的活动?$"),
    )

    /** 链接地址里出现这些 → 是站务链接（编辑 / 上传 / 页面不存在） */
    private val STATION_LINKS = listOf("action=edit", "redlink=1", "wpDestFile")

    /**
     * 「装饰性、重复出现的背景图」的文件名片段（比对的是 `img` 的 alt 文字）。
     *
     * 真实页面里每个礼包卡片都会塞同一张 `礼包bg1.png` 当背景（★一张卡片两张，11 个礼包就是 22 张），
     * 渲染出来就是一片糊 —— 删掉它们，卡片剩下的名字、图、价格反而看得清。
     *
     * 注意：不能拿图片地址去比 —— 地址是哈希路径（`/1/12/gwb0azc….jpg`），
     * 原始文件名只留在 alt 里。
     */
    private val DECOR_IMAGE_ALTS = listOf("礼包bg", "背景bg")

    /** 把换行、连续空格压成一个空格，再去掉两头空白 */
    fun normalize(raw: String): String = raw.replace(WHITESPACE, " ").trim()

    /** 这块文字是不是"给编辑者看的"（两类都算） */
    fun isNoiseText(raw: String): Boolean {
        val text = normalize(raw)
        return (BLOCK_NOISE + NOISE_TEXT).any { it.matches(text) }
    }

    /** 这块是不是"本身没有内容、只是叫你去 WIKI 传图"的占位块 */
    fun isBlockNoise(raw: String): Boolean {
        val text = normalize(raw)
        return BLOCK_NOISE.any { it.matches(text) }
    }

    /** 这个标题是不是"往期/历史"那类（连同下面的表一起不要） */
    fun isPastTitle(raw: String): Boolean {
        val text = normalize(raw)
        return PAST_TITLE.any { it.matches(text) }
    }

    /** 这个链接是不是站务链接 */
    fun isStationLink(href: String): Boolean =
        STATION_LINKS.any { href.contains(it, ignoreCase = true) }

    /** 这张图是不是"装饰用的重复背景图"（传 alt 文字，不是图片地址） */
    fun isDecorImage(alt: String): Boolean =
        DECOR_IMAGE_ALTS.any { alt.contains(it, ignoreCase = true) }

    // 交给网页脚本用的正则（Java 正则里这一小撮语法在 JS 里是通用的）
    fun blockPatterns(): List<String> = BLOCK_NOISE.map { it.pattern }

    fun noisePatterns(): List<String> = NOISE_TEXT.map { it.pattern }

    fun pastPatterns(): List<String> = PAST_TITLE.map { it.pattern }

    fun stationLinks(): List<String> = STATION_LINKS

    fun decorImageAlts(): List<String> = DECOR_IMAGE_ALTS
}
