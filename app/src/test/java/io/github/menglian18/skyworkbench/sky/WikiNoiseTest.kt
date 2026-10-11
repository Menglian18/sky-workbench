package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.1 新增：认出"给 WIKI 编辑者看的、玩家完全不需要"的东西。
 *
 * 这些期望值全部来自**真实页面**：1.4.1 这次专门去抓了 WIKI 的渲染 HTML
 * （《2026年秋宵节》），结构对过一遍才写规则 —— 所以规则是"照着真实文字写"的，
 * 不是拍脑袋想的。
 */
class WikiNoiseTest {

    @Test
    fun `没人传图的占位整块算噪音`() {
        // 真实页面原话：暂无展示图『点此上传』规格1280*256
        assertTrue(WikiNoise.isNoiseText("暂无展示图『点此上传』规格1280*256"))
        assertTrue(WikiNoise.isBlockNoise("暂无展示图『点此上传』规格1280*256"))
        assertTrue(WikiNoise.isBlockNoise("暂无展示图 点此上传 规格 1280 × 256"))
        assertTrue(WikiNoise.isNoiseText("暂无展示图"))
    }

    @Test
    fun `正文里出现同样的词不算噪音`() {
        // 真实正文（《2026年秋宵节》活动代币那一节）
        val real = "本次活动代币为月牙代币。通过活动场景内拾取15枚、收集代币烛火每日5枚、" +
            "在向导旁的活动礼盒兑换三种方式获取。"
        assertFalse(WikiNoise.isNoiseText(real))
        assertFalse(WikiNoise.isNoiseText("点此上传你的兑换图，给其他旅人看看"))
        assertFalse(WikiNoise.isNoiseText("活动介绍"))
        assertFalse(WikiNoise.isNoiseText("兑换"))
        assertFalse(WikiNoise.isNoiseText(""))
        assertFalse(WikiNoise.isNoiseText("   "))
    }

    @Test
    fun `面包屑和编辑工具条算噪音`() {
        assertTrue(WikiNoise.isNoiseText("首页 > 节日活动 > 秋宵节 > 2026年秋宵节"))
        assertTrue(WikiNoise.isNoiseText("短 • 刷 • 编 • 历"))
        assertTrue(WikiNoise.isNoiseText("短 刷 编 历"))
        // 单个词不能误伤
        assertFalse(WikiNoise.isNoiseText("首页"))
        assertFalse(WikiNoise.isNoiseText("短"))
        assertFalse(WikiNoise.isNoiseText("历"))
    }

    @Test
    fun `各种空白都当成空白`() {
        assertEquals(
            "暂无展示图 『点此上传』 规格1280*256",
            WikiNoise.normalize("  暂无展示图\n 『点此上传』\u00A0规格1280*256  "),
        )
        // &#160;（不换行空格）在网页里到处都是，也要能匹配上
        assertTrue(WikiNoise.isNoiseText("暂无展示图\u00A0『点此上传』\u00A0规格1280*256"))
        assertTrue(WikiNoise.isNoiseText("短\u3000•\u3000刷\u3000•\u3000编\u3000•\u3000历"))
    }

    @Test
    fun `往期页面那类标题要被认出来`() {
        // 真实页面底部那张表的表头单元格就是「往期页面」
        assertTrue(WikiNoise.isPastTitle("往期页面"))
        assertTrue(WikiNoise.isPastTitle("往期活动"))
        assertTrue(WikiNoise.isPastTitle("历史复刻"))
        assertFalse(WikiNoise.isPastTitle("往期页面表"))
        // 正常小节名不能被误伤
        assertFalse(WikiNoise.isPastTitle("活动代币"))
        assertFalse(WikiNoise.isPastTitle("兑换"))
        assertFalse(WikiNoise.isPastTitle("季节先祖"))
    }

    @Test
    fun `站务链接认得出`() {
        assertTrue(
            WikiNoise.isStationLink(
                "https://wiki.biligame.com/sky/index.php?title=2026年秋宵节&action=edit",
            ),
        )
        assertTrue(
            WikiNoise.isStationLink(
                "http://wiki.biligame.com/sky/index.php?title=特殊:上传文件&wpDestFile=月莲坐垫小型家具展示图.png",
            ),
        )
        assertTrue(
            WikiNoise.isStationLink("/sky/index.php?title=2024年有友节&action=edit&redlink=1"),
        )
        assertFalse(WikiNoise.isStationLink("https://wiki.biligame.com/sky/2026年秋宵节"))
        assertFalse(WikiNoise.isStationLink("https://patchwiki.biligame.com/images/sky/1/12/x.jpg"))
    }

    @Test
    fun `交给网页脚本的规则一条都不能少而且是整段匹配`() {
        val all = WikiNoise.blockPatterns() + WikiNoise.noisePatterns() + WikiNoise.pastPatterns()
        assertTrue(WikiNoise.blockPatterns().isNotEmpty())
        assertTrue(WikiNoise.noisePatterns().isNotEmpty())
        assertTrue(WikiNoise.pastPatterns().isNotEmpty())
        assertTrue(WikiNoise.stationLinks().isNotEmpty())
        assertTrue(WikiNoise.decorImageAlts().isNotEmpty())
        for (pattern in all) {
            // 没有 ^…$ 就可能把正文里的一小段误判成整块噪音
            assertTrue("这条规则没有整段匹配：$pattern", pattern.startsWith("^") && pattern.endsWith("$"))
        }
    }

    @Test
    fun `礼包卡片的重复背景图认得出`() {
        // 真实页面里每个礼包卡片都贴同一张「礼包bg1.png」当背景，11 个礼包就是二十多张
        assertTrue(WikiNoise.isDecorImage("礼包bg1.png"))
        assertTrue(WikiNoise.isDecorImage("礼包bg3.png"))
        // 真物品图不能误伤
        assertFalse(WikiNoise.isDecorImage("UI-小型家具-月莲坐垫.png"))
        assertFalse(WikiNoise.isDecorImage("节日：2026年秋宵节.jpg"))
        assertFalse(WikiNoise.isDecorImage(""))
    }
}
