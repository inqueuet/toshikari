package com.valoser.toshikari

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CatalogUrlResolverTest {

    @Before
    fun setUp() {
        // キャッシュをクリアして各テストを独立させる
        CatalogUrlResolver.urlGuessCache.evictAll()
        CatalogUrlResolver.failedUrlCache.evictAll()
    }

    // ====== guessFullFromPreview ======

    @Test
    fun `cat パスを src に置換できる`() {
        val result = CatalogUrlResolver.guessFullFromPreview("https://example.com/cat/12345s.jpg")
        assertEquals("https://example.com/src/12345.jpg", result)
    }

    @Test
    fun `thumb パスを src に置換できる`() {
        val result = CatalogUrlResolver.guessFullFromPreview("https://example.com/thumb/12345s.png")
        assertEquals("https://example.com/src/12345.png", result)
    }

    @Test
    fun `jun パスを src に置換できる`() {
        val result = CatalogUrlResolver.guessFullFromPreview("https://example.com/jun/12345s.gif")
        assertEquals("https://example.com/src/12345.gif", result)
    }

    @Test
    fun `末尾の s を除去して拡張子を保持する`() {
        val result = CatalogUrlResolver.guessFullFromPreview("https://example.com/cat/99999s.webp")
        assertEquals("https://example.com/src/99999.webp", result)
    }

    @Test
    fun `拡張子がない場合は jpg を仮置きする`() {
        val result = CatalogUrlResolver.guessFullFromPreview("https://example.com/cat/12345")
        assertNotNull(result)
        assertTrue(result!!.endsWith(".jpg"))
    }

    @Test
    fun `キャッシュが効く`() {
        val url = "https://example.com/cat/100s.jpg"
        val result1 = CatalogUrlResolver.guessFullFromPreview(url)
        val result2 = CatalogUrlResolver.guessFullFromPreview(url)
        assertEquals(result1, result2)
    }

    // ====== buildCatalogThumbCandidates ======

    @Test
    fun `正しいdetailUrlからサムネイル候補を生成できる`() {
        val candidates = CatalogUrlResolver.buildCatalogThumbCandidates("https://zip.2chan.net/32/res/178828.htm")
        assertEquals(9, candidates.size)
        assertTrue(candidates[0].contains("/cat/178828s.jpg"))
        assertTrue(candidates[1].contains("/cat/178828s.png"))
        assertTrue(candidates[2].contains("/cat/178828s.webp"))
        assertTrue(candidates[3].contains("/cat/178828.jpg"))
        assertTrue(candidates[6].contains("/thumb/178828s.jpg"))
    }

    @Test
    fun `不正なdetailUrlでは空リストを返す`() {
        val candidates = CatalogUrlResolver.buildCatalogThumbCandidates("https://example.com/other/page")
        assertTrue(candidates.isEmpty())
    }

    // ====== isMediaHref ======

    @Test
    fun `src パスを含むURLはメディアと判定`() {
        assertTrue(CatalogUrlResolver.isMediaHref("https://example.com/src/12345.jpg"))
    }

    @Test
    fun `jpg 拡張子はメディアと判定`() {
        assertTrue(CatalogUrlResolver.isMediaHref("image.jpg"))
    }

    @Test
    fun `png 拡張子はメディアと判定`() {
        assertTrue(CatalogUrlResolver.isMediaHref("image.png"))
    }

    @Test
    fun `webm 拡張子はメディアと判定`() {
        assertTrue(CatalogUrlResolver.isMediaHref("video.webm"))
    }

    @Test
    fun `mp4 拡張子はメディアと判定`() {
        assertTrue(CatalogUrlResolver.isMediaHref("video.mp4"))
    }

    @Test
    fun `html はメディアでないと判定`() {
        assertFalse(CatalogUrlResolver.isMediaHref("page.html"))
    }

    @Test
    fun `拡張子なしはメディアでないと判定`() {
        assertFalse(CatalogUrlResolver.isMediaHref("https://example.com/page"))
    }
}
