package io.github.menglian18.skyworkbench.sky

import org.json.JSONObject
import java.util.Locale

/**
 * 让 WIKI 的正文在 App 里看着像 App 自己的页面（1.4.0）。
 *
 * 为什么需要：App 内展示的是 `action=render` 的**正文 HTML**，它不带 WIKI 自己的样式表。
 * 不补一套的话 —— 表格没有框线、图片撑破屏幕、白底黑字跟深色主题打架。
 * 所以这里生成一小段 CSS 注入进去：
 *  - 底色 / 文字 / 链接跟随当前主题；
 *  - 图片、表格不超出屏幕宽；
 *  - 藏掉正文里用不着的零件（面包屑、编辑链接、页脚）。
 *
 * 这里全是纯字符串处理，所以能在电脑上用普通单元测试跑（不必开手机看）。
 */
object WikiStyle {

    /**
     * 要藏起来的元素。故意写长一点：选择器不存在也不会报错，
     * 万一 WIKI 改了页面结构，最多是没藏干净，不会把正文藏没了。
     */
    const val HIDDEN =
        "#breadcrumb,.breadcrumb,#siteSub,#jump-to-nav,.mw-jump-link,.mw-editsection," +
            "#catlinks,.printfooter,.noprint,#mw-navigation,.mw-indicators," +
            "#bili-header,.bili-header,.wiki-header,.content-header,#content-header," +
            ".wiki-content-header,#footer,.footer"

    fun readerCss(
        background: String,
        surface: String,
        text: String,
        muted: String,
        accent: String,
    ): String = buildString {
        append("html,body{background:").append(background).append(";color:").append(text).append(";}")
        append("body{margin:0;padding:10px 12px 32px;font-size:15px;line-height:1.75;")
        append("word-wrap:break-word;-webkit-text-size-adjust:100%;}")
        append("a{color:").append(accent).append(";}")
        append("img,video,iframe{max-width:100%!important;height:auto!important;}")
        // WIKI 里很多图外面还套了一层写死宽度的 div（模板生成的），
        // 光压图片不够，那层壳照样会把内容顶出屏幕（WebView 不会横向滚动，就被裁掉了）
        append("div,section{max-width:100%;}")
        append("table{max-width:100%;border-collapse:collapse;}")
        append("table.wikitable{width:100%;}")
        append("table.wikitable td,table.wikitable th{border:1px solid ").append(muted)
        append(";padding:4px 6px;vertical-align:top;}")
        append("th{background:").append(surface).append(";}")
        append("h1,h2,h3,h4,h5{line-height:1.5;margin:18px 0 6px;padding-bottom:4px;")
        append("border-bottom:1px solid ").append(muted).append(";font-size:1.08em;}")
        append("h1{font-size:1.2em;}h2{font-size:1.12em;}")
        append("hr{border:none;border-top:1px solid ").append(muted).append(";}")
        append("p{margin:8px 0;}")
        // 目录留着（长页面要靠它跳转），但缩小一点、加个底框，别占掉半屏
        append("#toc,.toc{background:").append(surface).append(";border:1px solid ").append(muted)
        append(";border-radius:8px;padding:8px 10px;font-size:0.9em;}")
        append("#toc .toctitle h2,.toc .toctitle h2{font-size:1em;border:none;margin:0 0 4px;}")
        append(HIDDEN).append("{display:none!important;}")
    }

    /**
     * 注入用的脚本：把 viewport 和上面那段 CSS 塞进页面。
     *
     * CSS 用 [JSONObject.quote] 转义后再拼进 JS —— 不然 CSS 里只要有一个引号或换行，
     * 整段脚本就断了（轻则样式没生效，重则脚本报错）。
     */
    fun injectScript(css: String): String = buildString {
        append("(function(){")
        append("var d=document;")
        append("var h=d.head||d.getElementsByTagName('head')[0];")
        append("if(!h){h=d.createElement('head');d.documentElement.appendChild(h);}")
        append("var m=d.querySelector('meta[name=viewport]');")
        append("if(!m){m=d.createElement('meta');m.setAttribute('name','viewport');h.appendChild(m);}")
        append("m.setAttribute('content','width=device-width, initial-scale=1');")
        append("var s=d.getElementById('sky-reader-style');")
        append("if(!s){s=d.createElement('style');s.id='sky-reader-style';h.appendChild(s);}")
        append("s.textContent=").append(JSONObject.quote(css)).append(";")
        append("})()")
    }

    /** 把 0f~1f 的颜色分量转成 `#RRGGBB`（超出范围的夹回来，别弄出怪颜色）。 */
    fun hex(red: Float, green: Float, blue: Float): String =
        String.format(Locale.ROOT, "#%02X%02X%02X", channel(red), channel(green), channel(blue))

    private fun channel(value: Float): Int = Math.round(value.coerceIn(0f, 1f) * 255f)
}
