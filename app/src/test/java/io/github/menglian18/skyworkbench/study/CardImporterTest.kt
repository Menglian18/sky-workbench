package io.github.menglian18.skyworkbench.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5 阶段的测试之一：粘贴文本批量导入卡片。
 */
class CardImporterTest {

    @Test
    fun `一行一张_用竖线分开正反面`() {
        val result = CardImporter.parse("apple | 苹果\nbanana | 香蕉")
        assertEquals(2, result.cards.size)
        assertEquals("apple", result.cards[0].front)
        assertEquals("苹果", result.cards[0].back)
        assertEquals("banana", result.cards[1].front)
    }

    @Test
    fun `全角竖线和制表符也认`() {
        val result = CardImporter.parse("apple ｜ 苹果\nbanana\t香蕉")
        assertEquals(2, result.cards.size)
        assertEquals("苹果", result.cards[0].back)
        assertEquals("香蕉", result.cards[1].back)
    }

    @Test
    fun `背面里还可以有竖线`() {
        val result = CardImporter.parse("公式 | a | b")
        assertEquals(1, result.cards.size)
        assertEquals("公式", result.cards[0].front)
        assertEquals("a | b", result.cards[0].back)
    }

    @Test
    fun `空行忽略`() {
        val result = CardImporter.parse("\n\napple | 苹果\n\n\nbanana | 香蕉\n")
        assertEquals(2, result.cards.size)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `没有分隔符的行被跳过并计数`() {
        val result = CardImporter.parse("apple | 苹果\n这行忘了分隔符\nbanana | 香蕉")
        assertEquals(2, result.cards.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `正面为空的行跳过`() {
        val result = CardImporter.parse(" | 只有背面")
        assertEquals(0, result.cards.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `只有反面也算合法_比如填空`() {
        val result = CardImporter.parse("首都是哪里 | 北京")
        assertEquals("北京", result.cards[0].back)
        assertEquals("首都是哪里", result.cards[0].front)
    }

    @Test
    fun `前后空格会被去掉`() {
        val result = CardImporter.parse("   apple   |   苹果   ")
        assertEquals("apple", result.cards[0].front)
        assertEquals("苹果", result.cards[0].back)
    }

    @Test
    fun `空文本不报错`() {
        val result = CardImporter.parse("")
        assertTrue(result.isEmpty)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `Windows 换行也能处理`() {
        val result = CardImporter.parse("apple | 苹果\r\nbanana | 香蕉\r\n")
        assertEquals(2, result.cards.size)
        assertEquals("苹果", result.cards[0].back)
        assertEquals("香蕉", result.cards[1].back)
    }
}
