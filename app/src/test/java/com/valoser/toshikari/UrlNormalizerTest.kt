package com.valoser.toshikari

import org.junit.Assert.*
import org.junit.Test

class UrlNormalizerTest {

    // ====== threadKey ======

    @Test
    fun `標準的なスレURLからキーを生成する`() {
        val result = UrlNormalizer.threadKey("https://zip.2chan.net/32/res/12345.htm")
        assertEquals("https://zip.2chan.net/32#12345", result)
    }

    @Test
    fun `http スキームでもキーを生成できる`() {
        val result = UrlNormalizer.threadKey("http://img.2chan.net/b/res/9999.htm")
        assertEquals("http://img.2chan.net/b#9999", result)
    }

    @Test
    fun `ホスト名が大文字でも小文字化される`() {
        val result = UrlNormalizer.threadKey("https://ZIP.2CHAN.NET/32/res/100.htm")
        assertEquals("https://zip.2chan.net/32#100", result)
    }

    @Test
    fun `同じスレの異なるURLは同じキーになる`() {
        val key1 = UrlNormalizer.threadKey("https://zip.2chan.net/32/res/12345.htm")
        val key2 = UrlNormalizer.threadKey("HTTPS://zip.2chan.net/32/res/12345.htm")
        assertEquals(key1, key2)
    }

    @Test
    fun `不正なURLはフォールバックとしてそのまま返す`() {
        val badUrl = "not a valid url"
        assertEquals(badUrl, UrlNormalizer.threadKey(badUrl))
    }

    // ====== legacyThreadKey ======

    @Test
    fun `レガシーキーはスキームを含まない`() {
        val result = UrlNormalizer.legacyThreadKey("https://zip.2chan.net/32/res/12345.htm")
        assertEquals("zip.2chan.net/32/res#12345", result)
    }

    @Test
    fun `レガシーキーとthreadKeyは異なる`() {
        val url = "https://zip.2chan.net/32/res/12345.htm"
        assertNotEquals(
            UrlNormalizer.threadKey(url),
            UrlNormalizer.legacyThreadKey(url)
        )
    }

    @Test
    fun `htm拡張子を除去してスレ番号を抽出する`() {
        val result = UrlNormalizer.legacyThreadKey("https://example.com/b/res/99999.htm")
        assertTrue(result.endsWith("#99999"))
    }

    @Test
    fun `スキームなしでも処理できる`() {
        // legacyThreadKey はString操作のみなので例外は発生しない
        val result = UrlNormalizer.legacyThreadKey("bad url")
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }
}
