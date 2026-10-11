package io.github.menglian18.skyworkbench.sky

import java.net.URLEncoder

/**
 * WIKI 页面地址（1.4.0 新增「点开看详细介绍」）。
 *
 * 两个地址各有用途：
 *  - [readerUrl]：`action=render`，服务器只返回**正文**，没有导航栏、侧边栏、页脚、广告。
 *    详细介绍就加载这个 —— 看着才像 App 自己的页面，而不是"App 里塞了个网页"。
 *  - [pageUrl]：正常网页地址，给「用浏览器打开」用（能继续点站内链接、能收藏）。
 *
 * 编码上的一个坑：用 [URLEncoder] 编码后必须把 `+` 换回 `%20`。
 * `+` 只在表单参数里代表空格，放在页面名里就是字面上的加号，服务器会找不到页面。
 */
object WikiPage {

    const val BASE = "https://wiki.biligame.com/sky"

    /** 只要正文的地址（App 内展示用）。 */
    fun readerUrl(title: String): String =
        "$BASE/index.php?title=" + encode(title) + "&action=render"

    /** 正常网页地址（用浏览器打开用）。 */
    fun pageUrl(title: String): String = "$BASE/" + encode(title)

    /** 页面名 → URL 片段（UTF-8 百分号编码，空格用 %20 而不是 +）。 */
    fun encode(raw: String): String =
        URLEncoder.encode(raw.trim(), "UTF-8").replace("+", "%20")

    /** 名字能不能拿去查（空名字拼出来的地址会打开 WIKI 首页，别给用户看那个）。 */
    fun isValidTitle(title: String): Boolean = title.trim().isNotEmpty()
}
