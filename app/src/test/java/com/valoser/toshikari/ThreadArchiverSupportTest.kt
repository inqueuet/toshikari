package com.valoser.toshikari

import org.junit.Assert.*
import org.junit.Test

class ThreadArchiverSupportTest {

    // ====== sanitizeFileName ======

    @Test
    fun `正常なファイル名はそのまま返す`() {
        assertEquals("image_001.jpg", ThreadArchiverSupport.sanitizeFileName("image_001.jpg"))
    }

    @Test
    fun `スラッシュをアンダースコアに置換する`() {
        assertEquals("a_b.jpg", ThreadArchiverSupport.sanitizeFileName("a/b.jpg"))
    }

    @Test
    fun `バックスラッシュをアンダースコアに置換する`() {
        assertEquals("a_b.jpg", ThreadArchiverSupport.sanitizeFileName("a\\b.jpg"))
    }

    @Test
    fun `コロン、アスタリスク、疑問符、山括弧、パイプを置換する`() {
        val result = ThreadArchiverSupport.sanitizeFileName("a:b*c?d\"e<f>g|h")
        assertEquals("a_b_c_d_e_f_g_h", result)
    }

    // ====== generateDirectoryNameFromUrl ======

    @Test
    fun `板名とスレッドIDを含むディレクトリ名を生成する`() {
        val result = ThreadArchiverSupport.generateDirectoryNameFromUrl(
            "https://img.2chan.net/b/res/1234567890.htm",
            "20250131_123456"
        )
        assertEquals("b_1234567890_20250131_123456", result)
    }

    @Test
    fun `パスが短い場合はハッシュを使う`() {
        val url = "https://example.com/page"
        val result = ThreadArchiverSupport.generateDirectoryNameFromUrl(url, "20250101_000000")
        assertTrue(result.endsWith("_20250101_000000"))
    }

    @Test
    fun `不正なURLはthread_hashフォールバックを使う`() {
        // 空URLのような不正ケース
        val result = ThreadArchiverSupport.generateDirectoryNameFromUrl("not a url", "20250101_000000")
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    // ====== generateFileName ======

    @Test
    fun `URLのパス末尾からファイル名を抽出する`() {
        val result = ThreadArchiverSupport.generateFileName("https://example.com/src/12345.jpg")
        assertEquals("12345.jpg", result)
    }

    @Test
    fun `不正文字を含むファイル名をサニタイズする`() {
        val result = ThreadArchiverSupport.generateFileName("https://example.com/src/file:name.jpg")
        assertEquals("file_name.jpg", result)
    }

    @Test
    fun `拡張子なしの場合はfallbackExtensionを使う`() {
        val result = ThreadArchiverSupport.generateFileName("https://example.com/path/noext", "mp4")
        assertTrue(result.endsWith(".mp4"))
    }

    @Test
    fun `デフォルトfallbackExtensionはjpg`() {
        val result = ThreadArchiverSupport.generateFileName("https://example.com/path/noext")
        assertTrue(result.endsWith(".jpg"))
    }

    // ====== buildRelativePath ======

    @Test
    fun `サブディレクトリがある場合はスラッシュで結合する`() {
        assertEquals("images/photo.jpg", ThreadArchiverSupport.buildRelativePath("images", "photo.jpg"))
    }

    @Test
    fun `サブディレクトリが空の場合はファイル名のみ返す`() {
        assertEquals("photo.jpg", ThreadArchiverSupport.buildRelativePath("", "photo.jpg"))
    }

    @Test
    fun `サブディレクトリが空白の場合もファイル名のみ返す`() {
        assertEquals("photo.jpg", ThreadArchiverSupport.buildRelativePath("   ", "photo.jpg"))
    }

    // ====== escapeHtml ======

    @Test
    fun `アンパサンドをエスケープする`() {
        assertEquals("a&amp;b", ThreadArchiverSupport.escapeHtml("a&b"))
    }

    @Test
    fun `山括弧をエスケープする`() {
        assertEquals("&lt;div&gt;", ThreadArchiverSupport.escapeHtml("<div>"))
    }

    @Test
    fun `ダブルクォートをエスケープする`() {
        assertEquals("&quot;hello&quot;", ThreadArchiverSupport.escapeHtml("\"hello\""))
    }

    @Test
    fun `シングルクォートをエスケープする`() {
        assertEquals("it&#39;s", ThreadArchiverSupport.escapeHtml("it's"))
    }

    @Test
    fun `特殊文字なしの文字列はそのまま返す`() {
        assertEquals("hello world", ThreadArchiverSupport.escapeHtml("hello world"))
    }

    @Test
    fun `複数の特殊文字を同時にエスケープする`() {
        assertEquals("&lt;b&gt;a&amp;b&lt;/b&gt;", ThreadArchiverSupport.escapeHtml("<b>a&b</b>"))
    }

    // ====== isLongQuote ======

    @Test
    fun `短いHTMLは長文でない`() {
        assertFalse(ThreadArchiverSupport.isLongQuote("<blockquote>短文</blockquote>"))
    }

    @Test
    fun `400文字以上のテキストは長文と判定する`() {
        val longText = "あ".repeat(401)
        assertTrue(ThreadArchiverSupport.isLongQuote("<blockquote>$longText</blockquote>"))
    }

    @Test
    fun `br が6個以上あれば長文と判定する`() {
        val html = "行1<br>行2<br>行3<br>行4<br>行5<br>行6<br>行7"
        assertTrue(ThreadArchiverSupport.isLongQuote(html))
    }

    // ====== wrapLongQuoteIfNeeded ======

    @Test
    fun `短い blockquote はそのまま返す`() {
        val html = "<blockquote>短文</blockquote>"
        assertEquals(html, ThreadArchiverSupport.wrapLongQuoteIfNeeded(html))
    }

    @Test
    fun `長い blockquote は details で包む`() {
        val longText = "あ".repeat(401)
        val html = "<blockquote>$longText</blockquote>"
        val result = ThreadArchiverSupport.wrapLongQuoteIfNeeded(html)
        assertTrue(result.startsWith("<details"))
        assertTrue(result.contains("長文の引用を開く"))
        assertTrue(result.contains(html))
    }

    @Test
    fun `blockquote でない HTML は長文でもそのまま返す`() {
        val longText = "あ".repeat(401)
        val html = "<div>$longText</div>"
        assertEquals(html, ThreadArchiverSupport.wrapLongQuoteIfNeeded(html))
    }

    // ====== textToParagraphs ======

    @Test
    fun `空白テキストは空文字を返す`() {
        assertEquals("", ThreadArchiverSupport.textToParagraphs("  "))
    }

    @Test
    fun `単一段落はp要素で包む`() {
        assertEquals("<p>テスト</p>", ThreadArchiverSupport.textToParagraphs("テスト"))
    }

    @Test
    fun `空行で段落を分割する`() {
        val result = ThreadArchiverSupport.textToParagraphs("段落1\n\n段落2")
        assertEquals("<p>段落1</p><p>段落2</p>", result)
    }

    @Test
    fun `連続brを段落区切りにする`() {
        val result = ThreadArchiverSupport.textToParagraphs("段落1<br><br>段落2")
        assertEquals("<p>段落1</p><p>段落2</p>", result)
    }

    @Test
    fun `単独改行はbr要素にする`() {
        val result = ThreadArchiverSupport.textToParagraphs("行1\n行2")
        assertEquals("<p>行1<br>行2</p>", result)
    }

    // ====== formatParagraphsAndQuotes ======

    @Test
    fun `空白HTMLはそのまま返す`() {
        assertEquals("", ThreadArchiverSupport.formatParagraphsAndQuotes(""))
        assertEquals("  ", ThreadArchiverSupport.formatParagraphsAndQuotes("  "))
    }

    @Test
    fun `テキストのみのHTMLを段落化する`() {
        val result = ThreadArchiverSupport.formatParagraphsAndQuotes("テスト文")
        assertTrue(result.contains("<p>"))
        assertTrue(result.contains("テスト文"))
    }

    @Test
    fun `ブロック要素はそのまま保持する`() {
        val html = "<div>ブロック内容</div>"
        val result = ThreadArchiverSupport.formatParagraphsAndQuotes(html)
        assertTrue(result.contains("<div>"))
    }

    // ====== replaceLinksWithLocalPaths ======

    @Test
    fun `href のパスをローカルパスに置換する`() {
        val html = """<a href="/b/src/12345.jpg">画像</a>"""
        val files = mapOf("https://img.2chan.net/b/src/12345.jpg" to "images/12345.jpg")
        val result = ThreadArchiverSupport.replaceLinksWithLocalPaths(html, files)
        assertTrue(result.contains("href=\"images/12345.jpg\""))
    }

    @Test
    fun `src のパスをローカルパスに置換する`() {
        val html = """<img src="/b/src/12345.jpg">"""
        val files = mapOf("https://img.2chan.net/b/src/12345.jpg" to "images/12345.jpg")
        val result = ThreadArchiverSupport.replaceLinksWithLocalPaths(html, files)
        assertTrue(result.contains("src=\"images/12345.jpg\""))
    }

    @Test
    fun `マッチしないURLはそのまま保持する`() {
        val html = """<a href="/other/path.html">リンク</a>"""
        val files = mapOf("https://example.com/b/src/12345.jpg" to "images/12345.jpg")
        val result = ThreadArchiverSupport.replaceLinksWithLocalPaths(html, files)
        assertEquals(html, result)
    }

    @Test
    fun `空のダウンロードマップではHTMLが変化しない`() {
        val html = """<img src="/b/src/12345.jpg">"""
        val result = ThreadArchiverSupport.replaceLinksWithLocalPaths(html, emptyMap())
        assertEquals(html, result)
    }
}
