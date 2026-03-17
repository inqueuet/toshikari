package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import org.junit.Assert.*
import org.junit.Test

class DetailListSupportTest {

    // ====== stableKey ======

    @Test
    fun `ID がある場合はそのまま返す`() {
        val item = DetailContent.Text(id = "text_123", htmlContent = "hello", resNum = "123")
        assertEquals("text_123", DetailListSupport.stableKey(item, 0))
    }

    @Test
    fun `空の ID の場合はフォールバックキーを返す`() {
        val item = DetailContent.Text(id = "", htmlContent = "hello", resNum = "123")
        assertEquals("text_fallback_5", DetailListSupport.stableKey(item, 5))
    }

    @Test
    fun `Image のフォールバックキー`() {
        val item = DetailContent.Image(id = "", imageUrl = "url", prompt = null, fileName = null)
        assertEquals("image_fallback_3", DetailListSupport.stableKey(item, 3))
    }

    // ====== ordinalForIndex ======

    @Test
    fun `Text のみのリストで序数を返す`() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "a", resNum = "1"),
            DetailContent.Text(id = "t2", htmlContent = "b", resNum = "2"),
            DetailContent.Text(id = "t3", htmlContent = "c", resNum = "3"),
        )
        assertEquals(1, DetailListSupport.ordinalForIndex(items, 0))
        assertEquals(2, DetailListSupport.ordinalForIndex(items, 1))
        assertEquals(3, DetailListSupport.ordinalForIndex(items, 2))
    }

    @Test
    fun `Image を挟んでもText のカウントのみ増える`() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "a", resNum = "1"),
            DetailContent.Image(id = "i1", imageUrl = "url", prompt = null, fileName = null),
            DetailContent.Text(id = "t2", htmlContent = "b", resNum = "2"),
        )
        assertEquals(1, DetailListSupport.ordinalForIndex(items, 0))
        assertEquals(1, DetailListSupport.ordinalForIndex(items, 1)) // Image は Text カウントを増やさない
        assertEquals(2, DetailListSupport.ordinalForIndex(items, 2))
    }

    // ====== isEndOfBlock ======

    @Test
    fun `次が Text ならブロック末尾`() {
        val items = listOf(
            DetailContent.Image(id = "i1", imageUrl = "url", prompt = null, fileName = null),
            DetailContent.Text(id = "t1", htmlContent = "a", resNum = "1"),
        )
        assertTrue(DetailListSupport.isEndOfBlock(items, 0))
    }

    @Test
    fun `次が Image ならブロック末尾でない`() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "a", resNum = "1"),
            DetailContent.Image(id = "i1", imageUrl = "url", prompt = null, fileName = null),
        )
        assertFalse(DetailListSupport.isEndOfBlock(items, 0))
    }

    @Test
    fun `末尾要素はブロック末尾でない`() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "a", resNum = "1"),
        )
        assertFalse(DetailListSupport.isEndOfBlock(items, 0))
    }

    @Test
    fun `範囲外のインデックスはfalse`() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "a", resNum = "1"),
        )
        assertFalse(DetailListSupport.isEndOfBlock(items, 5))
    }

    // ====== applySodaneDisplay ======

    @Test
    fun `オーバーライドが空ならテキストそのまま`() {
        val text = "テスト文章"
        val result = DetailListSupport.applySodaneDisplay(text, emptyMap(), null)
        assertEquals(text, result)
    }
}
