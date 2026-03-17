package com.valoser.toshikari

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.*
import org.junit.Test

class CatalogHtmlParserTest {

    // ====== firstLineFromSmall ======

    @Test
    fun `br の前のテキストのみを取得する`() {
        val html = "<small>タイトル<br>サブタイトル</small>"
        val small = Jsoup.parse(html).selectFirst("small")
        val result = CatalogHtmlParser.firstLineFromSmall(small)
        assertEquals("タイトル", result)
    }

    @Test
    fun `br がない場合は全体を返す`() {
        val html = "<small>単一行のタイトル</small>"
        val small = Jsoup.parse(html).selectFirst("small")
        val result = CatalogHtmlParser.firstLineFromSmall(small)
        assertEquals("単一行のタイトル", result)
    }

    @Test
    fun `null の場合は空文字を返す`() {
        val result = CatalogHtmlParser.firstLineFromSmall(null)
        assertEquals("", result)
    }

    @Test
    fun `HTML タグを除去してプレーンテキストにする`() {
        val html = "<small><b>太字タイトル</b><br>本文</small>"
        val small = Jsoup.parse(html).selectFirst("small")
        val result = CatalogHtmlParser.firstLineFromSmall(small)
        assertEquals("太字タイトル", result)
    }

    @Test
    fun `空の small は空文字を返す`() {
        val html = "<small></small>"
        val small = Jsoup.parse(html).selectFirst("small")
        val result = CatalogHtmlParser.firstLineFromSmall(small)
        assertEquals("", result)
    }

    // ====== parseItemsFromDocument ======

    @Test
    fun `junbi URLでは空リストを返す`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val result = CatalogHtmlParser.parseItemsFromDocument(doc, "https://example.com/junbi/")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `cattable からアイテムを解析する`() {
        val html = """
        <html><body>
        <table id="cattable">
            <tr>
                <td>
                    <a href="https://example.com/res/12345.htm">
                        <img src="https://example.com/cat/12345s.jpg">
                    </a>
                    <small>テストタイトル</small>
                    <font>3</font>
                </td>
            </tr>
        </table>
        </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://example.com/")
        val result = CatalogHtmlParser.parseItemsFromDocument(doc, "https://example.com/")
        assertEquals(1, result.size)
        assertEquals("テストタイトル", result[0].title)
        assertEquals("3", result[0].replyCount)
        assertTrue(result[0].previewUrl.contains("12345s.jpg"))
    }

    @Test
    fun `cattable で img がない場合は推測URLを構築する`() {
        val html = """
        <html><body>
        <table id="cattable">
            <tr>
                <td>
                    <a href="res/99999.htm">テキストリンク</a>
                    <small>画像なしスレ</small>
                </td>
            </tr>
        </table>
        </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://zip.2chan.net/32/")
        val result = CatalogHtmlParser.parseFromCattable(doc)
        assertEquals(1, result.size)
        assertTrue(result[0].previewUrl.contains("99999s.jpg"))
        assertTrue(result[0].previewUnavailable)
    }

    @Test
    fun `cgi フォールバックでアイテムを解析する`() {
        val html = """
        <html><body>
        <a href="https://example.com/res/67890.htm">
            <img src="https://example.com/thumb/67890s.jpg">
        </a>
        <small>CGIタイトル</small>
        </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://example.com/")
        val result = CatalogHtmlParser.parseCgiFallback(doc)
        assertEquals(1, result.size)
        assertTrue(result[0].previewUrl.contains("67890s.jpg"))
    }

    @Test
    fun `空のドキュメントでは空リストを返す`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val result = CatalogHtmlParser.parseItemsFromDocument(doc, "https://example.com/")
        assertTrue(result.isEmpty())
    }
}
