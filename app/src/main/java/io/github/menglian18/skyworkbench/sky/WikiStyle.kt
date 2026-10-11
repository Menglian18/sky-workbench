package io.github.menglian18.skyworkbench.sky

import org.json.JSONObject
import java.util.Locale

/**
 * 让 WIKI 的正文在 App 里看着像 App 自己的页面（1.4.0 新增样式，1.4.1 加了"清理"）。
 *
 * 为什么需要：App 内展示的是 `action=render` 的**正文 HTML**，它不带 WIKI 自己的样式表。
 * 不补一套的话 —— 表格没有框线、图片撑破屏幕、白底黑字跟深色主题打架。
 *
 * 1.4.1（用户说「点进去太乱」「有些东西完全不需要」「已经结束的就不要了，关注当下」）之后，
 * 这里做两件事：
 *  1. [cleanupScript]：**先把不该看的东西从页面上拿掉**（编辑工具条、面包屑、上传提示、
 *     往期页面表格、空的小节、重复出现的信息框）—— 规则来自 [WikiNoise]；
 *  2. [readerCss]：再套一套安静的排版（跟随主题、图片不撑破屏幕、标题不要一堆下划线）。
 *
 * 这里全是纯字符串处理，所以能在电脑上用普通单元测试跑（不必开手机看）。
 */
object WikiStyle {

    /**
     * 交给 CSS 藏起来的元素。
     *
     * 前四个是**拿真实页面结构对过的**（1.4.1 专门去侦察了 WIKI 的渲染 HTML）：
     *  - `.crumb`：面包屑模板，里面既有「首页 > 节日活动 > 秋宵节」，也有「短 刷 编 历」那排编辑工具
     *  - `#toc`：MediaWiki 自动生成的目录（由「左侧目录」模板放进栅格左列）
     *  - `.hidden-xs`：节日信息模板里**给电脑看的那份拷贝**（同一段公告会重复两遍，只留手机那份，那张节日大图两份都有）
     *  - `#noscrolltoc`、`#nohovertoc`：模板留下的两个空壳
     *
     * 后面那串是通用保险：万一 WIKI 改了结构，选择器不存在也不会报错，最多是没藏干净，不会把正文藏没了。
     */
    const val HIDDEN =
        ".crumb,#toc,.toc,.tocnumber,.toctitle,.hidden-xs,#noscrolltoc,#nohovertoc," +
            "#breadcrumb,.breadcrumb,#siteSub,#jump-to-nav,.mw-jump-link,.mw-editsection," +
            "#catlinks,.printfooter,.noprint,.navbox,#mw-navigation,.mw-indicators," +
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
        append("body{margin:0;padding:12px 14px 40px;font-size:15px;line-height:1.8;")
        append("word-wrap:break-word;-webkit-text-size-adjust:100%;}")
        append("a{color:").append(accent).append(";text-decoration:none;}")
        append("img,video,iframe{max-width:100%!important;height:auto!important;border-radius:6px;}")
        // WIKI 里很多图外面还套了一层写死宽度的 div（模板生成的），
        // 光压图片不够，那层壳照样会把内容顶出屏幕（WebView 不会横向滚动，就被裁掉了）
        append("div,section{max-width:100%;}")
        append("table{max-width:100%;border-collapse:collapse;}")
        append("table.wikitable{width:100%;font-size:0.95em;}")
        append("table.wikitable td,table.wikitable th{border:1px solid ").append(muted)
        append(";padding:5px 7px;vertical-align:top;}")
        append("th{background:").append(surface).append(";}")
        // 标题：只留一点点分量，不要一层层下划线（那是最显乱的地方）
        append("h1,h2,h3,h4,h5{line-height:1.45;margin:22px 0 8px;padding:0;border:none;")
        append("font-size:1.06em;font-weight:600;}")
        append("h2{font-size:1.12em;border-left:3px solid ").append(accent).append(";padding-left:8px;}")
        append("h1{font-size:1.2em;}")
        append("p{margin:8px 0;}")
        append("hr{display:none;}")
        append(HIDDEN).append("{display:none!important;}")
    }

    /**
     * 页面清理脚本（1.4.1）。在页面加载完后跑一遍，把不该给玩家看的东西从 DOM 上拿掉。
     *
     * 几条原则（都是"宁可不删，也不删错"）：
     *  - 先删**整块就是"没人传图"占位**的东西（那种块里还挂着一张 80px 缩略图，只删文字会剩下一堆没头没尾的小图）；
     *  - 再按**整段文字**删（正则带 `^…$`），正文里提到"点此上传"不会中招；
     *  - 元素里**有图/表**就只清里面的提示文字，绝不整块删掉；
     *  - 「往期页面」这种表头 → 连整张表端掉（已经结束的东西不看）；
     *  - 空的小节标题（比如「活动介绍」下面什么都没有）藏掉；
     *  - 最后收拾空壳，但**不动表格单元格**（免得表格错位）。
     */
    fun cleanupScript(): String = buildString {
        append("var BLOCK=").append(jsonArray(WikiNoise.blockPatterns())).append(";")
        append("var NOISE=").append(jsonArray(WikiNoise.noisePatterns())).append(";")
        append("var PAST=").append(jsonArray(WikiNoise.pastPatterns())).append(";")
        append("var BADLINK=").append(jsonArray(WikiNoise.stationLinks())).append(";")
        append("var DECOR=").append(jsonArray(WikiNoise.decorImageAlts())).append(";")
        append(
            """
            var doc=document;
            function norm(s){return (s||'').replace(/[\s\u00A0\u3000]+/g,' ').trim();}
            function hit(list,t){for(var i=0;i<list.length;i++){try{if(new RegExp(list[i]).test(t))return true;}catch(e){}}return false;}
            function killText(root,list){
              var w=doc.createTreeWalker(root,NodeFilter.SHOW_TEXT,null,false);
              var dead=[],n;
              while((n=w.nextNode())){var t=norm(n.nodeValue);if(t&&hit(list,t))dead.push(n);}
              for(var i=0;i<dead.length;i++){try{dead[i].parentNode.removeChild(dead[i]);}catch(e){}}
            }
            try{
              // 1) 整块就是"没人传图"的占位：连里面的小图一起删
              var blocks=doc.querySelectorAll('*');
              for(var i=blocks.length-1;i>=0;i--){
                var b=blocks[i];
                if(!b.parentNode)continue;
                var bt=norm(b.textContent);
                if(bt&&hit(BLOCK,bt)){b.parentNode.removeChild(b);}
              }
              // 2) 整段就是提示的文字节点
              killText(doc.body,NOISE);
              // 3) 整块就是提示的元素（面包屑、工具条这种由好几个链接拼起来的）
              var els=doc.querySelectorAll('*');
              for(var i=els.length-1;i>=0;i--){
                var el=els[i];
                if(!el.parentNode)continue;
                var t=norm(el.textContent);
                if(!t||!hit(NOISE,t))continue;
                var kids=el.children,inner=false;
                for(var k=0;k<kids.length;k++){if(hit(NOISE,norm(kids[k].textContent))){inner=true;break;}}
                if(inner)continue;
                if(el.querySelector('img,video,iframe,table')){killText(el,NOISE);continue;}
                el.parentNode.removeChild(el);
              }
              // 4) 站务链接：编辑 / 上传 / 页面不存在
              var links=doc.querySelectorAll('a[href]');
              for(var i=0;i<links.length;i++){
                var h=links[i].getAttribute('href')||'',bad=false;
                for(var k=0;k<BADLINK.length;k++){if(h.indexOf(BADLINK[k])>=0){bad=true;break;}}
                if(bad){try{links[i].parentNode.removeChild(links[i]);}catch(e){}}
              }
              // 5) 装饰性重复背景图（礼包卡片里同一张背景图会贴几十遍）
              var imgs=doc.querySelectorAll('img');
              for(var i=0;i<imgs.length;i++){
                var alt=imgs[i].getAttribute('alt')||'';
                for(var k=0;k<DECOR.length;k++){
                  if(alt.toLowerCase().indexOf(DECOR[k].toLowerCase())>=0){
                    try{imgs[i].parentNode.removeChild(imgs[i]);}catch(e){}
                    break;
                  }
                }
              }
              // 6) 「往期页面」这类小节：连它下面那张表一起端掉（已经结束的东西不看）
              var all=doc.querySelectorAll('*');
              for(var i=all.length-1;i>=0;i--){
                var e2=all[i];
                if(!e2.parentNode)continue;
                var t2=norm(e2.textContent);
                if(!t2||!hit(PAST,t2))continue;
                var box=e2.closest?e2.closest('table,ul,ol,div'):e2.parentNode;
                if(box&&box.parentNode){box.parentNode.removeChild(box);}
                else if(e2.parentNode){e2.parentNode.removeChild(e2);}
              }
              // 7) 空的小节：标题下面既没字也没图没表 → 这个标题就别显示了
              var hs=doc.querySelectorAll('h2,h3,h4');
              for(var i=0;i<hs.length;i++){
                var h=hs[i];
                if(!h.parentNode)continue;
                var lvl=parseInt(h.tagName.substring(1),10),sib=h.nextElementSibling,empty=true;
                while(sib){
                  var tag=sib.tagName||'';
                  if(/^H[1-6]${'$'}/.test(tag)&&parseInt(tag.substring(1),10)<=lvl)break;
                  if(tag!=='STYLE'&&tag!=='SCRIPT'){
                    if(norm(sib.textContent)!==''){empty=false;break;}
                    if(sib.querySelector&&sib.querySelector('img,video,iframe,table')){empty=false;break;}
                  }
                  sib=sib.nextElementSibling;
                }
                if(empty)h.style.display='none';
              }
              // 8) 收拾空壳（表格单元格不动，免得表格错位）
              var boxes=doc.querySelectorAll('div,p,span,li,ul,ol,section');
              for(var i=boxes.length-1;i>=0;i--){
                var bx=boxes[i];
                if(!bx.parentNode)continue;
                if(bx.querySelector('img,video,iframe,table,br,input'))continue;
                if(norm(bx.textContent)===''){try{bx.parentNode.removeChild(bx);}catch(e){}}
              }
            }catch(e){}
            """.trimIndent(),
        )
    }

    /**
     * 注入用的脚本：先清理页面，再把 viewport 和 [readerCss] 塞进去。
     *
     * CSS 用 [JSONObject.quote] 转义后再拼进 JS —— 不然 CSS 里只要有一个引号或换行，
     * 整段脚本就断了（轻则样式没生效，重则脚本报错）。
     */
    fun injectScript(css: String): String = buildString {
        append("(function(){")
        append(cleanupScript())
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

    private fun jsonArray(items: List<String>): String =
        items.joinToString(separator = ",", prefix = "[", postfix = "]") { JSONObject.quote(it) }
}
