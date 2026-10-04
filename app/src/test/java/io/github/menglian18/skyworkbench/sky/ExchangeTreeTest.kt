package io.github.menglian18.skyworkbench.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 改进 · C2（兑换树）对应的测试。
 *
 * 用一段和真实文件同格式的 JSON 来测，验证：解析、全收花费、分层、路径、容错。
 */
class ExchangeTreeTest {

    private val sample = """
    {"version":1,"trees":[
      {"spirit":"spirit-1","root":0,"nodes":[
        {"i":0,"item":"g0","zh":"","en":"Root","type":"Special","icon":"https://a/0.webp","kids":[1,2]},
        {"i":1,"item":"g1","zh":"斗篷","en":"Cape","type":"Cape","icon":"https://a/1.webp","c":60,"kids":[3]},
        {"i":2,"item":"g2","zh":"爱心","en":"Heart","type":"Held","icon":"https://a/2.webp","h":3},
        {"i":3,"item":"g3","zh":"","en":"Wing","type":"WingBuff","icon":"https://a/3.webp","ac":2,"ec":5}
      ]},
      {"spirit":"spirit-2","root":0,"nodes":[
        {"i":0,"item":"h0","zh":"面具","en":"Mask","type":"Mask","icon":"https://b/0.webp","c":30}
      ]}
    ]}
    """.trimIndent()

    @Test
    fun `能解析出每棵树的节点`() {
        val trees = ExchangeTreeParser.parse(sample)
        assertEquals(2, trees.size)
        assertEquals("spirit-1", trees[0].spiritId)
        assertEquals(4, trees[0].nodes.size)
        assertEquals(1, trees[1].nodes.size)
    }

    @Test
    fun `节点字段都读对了`() {
        val cape = ExchangeTreeParser.parse(sample)[0].node(1)!!
        assertEquals("斗篷", cape.zh)
        assertEquals("Cape", cape.en)
        assertEquals("Cape", cape.type)
        assertEquals(60, cape.candles)
        assertEquals("https://a/1.webp", cape.icon)
        assertEquals(listOf(3), cape.kids)
    }

    @Test
    fun `名字优先用中文_没有就用英文`() {
        val tree = ExchangeTreeParser.parse(sample)[0]
        assertEquals("斗篷", tree.node(1)!!.displayName)
        assertEquals("Wing", tree.node(3)!!.displayName)
    }

    @Test
    fun `全都不是中文时会兜底`() {
        val bare = TreeItem(index = 0, itemGuid = "g", zh = "", en = "", type = "", icon = "")
        assertEquals("未知道具", bare.displayName)
    }

    @Test
    fun `其他货币会被合成一个数`() {
        val wing = ExchangeTreeParser.parse(sample)[0].node(3)!!
        assertEquals(5, wing.otherCurrency)
        assertEquals(2, wing.ascended)
    }

    @Test
    fun `全收的合计是对的`() {
        val tree = ExchangeTreeParser.parse(sample)[0]
        assertEquals(60, tree.totalCandles)
        assertEquals(3, tree.totalHearts)
        assertEquals(2, tree.totalAscended)
        assertTrue(tree.totalText().contains("60"))
    }

    @Test
    fun `花费文案分三种情况`() {
        val tree = ExchangeTreeParser.parse(sample)[0]
        assertTrue(tree.node(1)!!.costText().contains("白蜡 60"))
        assertTrue(tree.node(3)!!.costText().contains("升华 2"))
        val free = TreeItem(index = 0, itemGuid = "g", zh = "", en = "", type = "", icon = "")
        assertEquals("免费", free.costText())
        val season = free.copy(otherCurrency = 12)
        assertTrue(season.costText().contains("12"))
    }

    @Test
    fun `分层是按离根几步来的`() {
        val levels = ExchangeTreeParser.parse(sample)[0].levels()
        assertEquals(3, levels.size)
        assertEquals(listOf(0), levels[0].map { it.index })
        assertEquals(listOf(1, 2), levels[1].map { it.index })
        assertEquals(listOf(3), levels[2].map { it.index })
    }

    @Test
    fun `每一个节点都会出现在某一层里`() {
        val tree = ExchangeTreeParser.parse(sample)[0]
        val shown = tree.levels().flatten().map { it.index }.toSet()
        assertEquals(tree.nodes.map { it.index }.toSet(), shown)
    }

    @Test
    fun `能查出一条物品的来路`() {
        val tree = ExchangeTreeParser.parse(sample)[0]
        val path = tree.pathTo(3)
        assertEquals(listOf(0, 1, 3), path.map { it.index })
    }

    @Test
    fun `读不出来的内容返回空列表而不是崩掉`() {
        assertEquals(0, ExchangeTreeParser.parse("").size)
        assertEquals(0, ExchangeTreeParser.parse("这不是 json").size)
        assertEquals(0, ExchangeTreeParser.parse("""{"version":1}""").size)
        assertEquals(0, ExchangeTreeParser.parse("""{"trees":[]}""").size)
    }

    @Test
    fun `缺字段的节点也能读_不会崩`() {
        val trees = ExchangeTreeParser.parse("""{"trees":[{"spirit":"s","root":0,"nodes":[{"i":0}]}]}""")
        assertEquals(1, trees.size)
        assertEquals(0, trees[0].nodes[0].candles)
        assertEquals(0, trees[0].nodes[0].kids.size)
        assertEquals("未知道具", trees[0].nodes[0].displayName)
    }

    @Test
    fun `没有节点列表的树会被忽略`() {
        assertEquals(0, ExchangeTreeParser.parse("""{"trees":[{"spirit":"s","root":0}]}""").size)
    }

    @Test
    fun `查不存在的节点返回 null`() {
        val tree = ExchangeTreeParser.parse(sample)[0]
        assertNull(tree.node(99))
    }
}
