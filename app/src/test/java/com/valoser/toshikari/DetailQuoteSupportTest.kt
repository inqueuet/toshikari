package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailQuoteSupportTest {

    // ---------- extractFirstLevelQuote ----------

    @Test
    fun `引用行から最初の引用を抽出する`() {
        val input = "こんにちは\n>引用テスト\n>次の引用"
        assertEquals("引用テスト", DetailQuoteSupport.extractFirstLevelQuote(input))
    }

    @Test
    fun `二重引用は無視する`() {
        assertNull(DetailQuoteSupport.extractFirstLevelQuote(">>二重引用"))
    }

    @Test
    fun `引用がない場合はnullを返す`() {
        assertNull(DetailQuoteSupport.extractFirstLevelQuote("普通のテキスト"))
    }

    @Test
    fun `空行混じりでも抽出する`() {
        val input = "\n\n>引用行\n\n"
        assertEquals("引用行", DetailQuoteSupport.extractFirstLevelQuote(input))
    }

    // ---------- extractAllFirstLevelQuotes ----------

    @Test
    fun `複数の引用行をすべて抽出する`() {
        val input = ">第一\nテキスト\n>第二"
        assertEquals(listOf("第一", "第二"), DetailQuoteSupport.extractAllFirstLevelQuotes(input))
    }

    @Test
    fun `引用がない場合は空リストを返す`() {
        assertTrue(DetailQuoteSupport.extractAllFirstLevelQuotes("テキスト").isEmpty())
    }

    @Test
    fun `二重引用は含まない`() {
        val input = ">一段\n>>二段"
        assertEquals(listOf("一段"), DetailQuoteSupport.extractAllFirstLevelQuotes(input))
    }

    // ---------- findContentByText ----------

    private fun textContent(id: String, plain: String) = DetailContent.Text(id, plain)
    private fun imageContent(id: String, fileName: String, url: String = "https://example.com/$fileName") =
        DetailContent.Image(id, url, fileName = fileName)
    private fun videoContent(id: String, fileName: String, url: String) =
        DetailContent.Video(id, url, fileName = fileName)

    private val plainTextProvider: (DetailContent.Text) -> String = { it.htmlContent }

    @Test
    fun `No番号でテキストを見つける`() {
        val text = textContent("t1", "本文 No.123456 です")
        val list = listOf(text)
        assertEquals(text, DetailQuoteSupport.findContentByText(list, "No.123456", plainTextProvider))
    }

    @Test
    fun `ファイル名で画像を見つける`() {
        val img = imageContent("i1", "test.jpg")
        val list = listOf(img)
        assertEquals(img, DetailQuoteSupport.findContentByText(list, "test.jpg", plainTextProvider))
    }

    @Test
    fun `URL末尾で動画を見つける`() {
        val vid = videoContent("v1", "movie.mp4", "https://example.com/path/video.mp4")
        val list = listOf(vid)
        assertEquals(vid, DetailQuoteSupport.findContentByText(list, "video.mp4", plainTextProvider))
    }

    @Test
    fun `本文の部分一致で見つける`() {
        val text = textContent("t1", "これはテスト本文です")
        val list = listOf(text)
        assertEquals(text, DetailQuoteSupport.findContentByText(list, "テスト本文", plainTextProvider))
    }

    @Test
    fun `マッチしない場合はnullを返す`() {
        val text = textContent("t1", "何かのテキスト")
        val list = listOf(text)
        assertNull(DetailQuoteSupport.findContentByText(list, "存在しない文字列", plainTextProvider))
    }
}
