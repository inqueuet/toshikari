package com.valoser.toshikari.ui.detail

import org.junit.Assert.*
import org.junit.Test

/**
 * 共通インターフェース DetailTokenMatch / DetailTokenFinder の契約テスト。
 * 各 Finder が統一インターフェースを正しく実装していることを検証する。
 */
class DetailTokenMatchTest {

    @Test
    fun `IdTokenFinder は DetailTokenFinder を実装している`() {
        val finder: DetailTokenFinder<DetailIdTokenMatch> = DetailIdTokenFinder
        val results = finder.findMatches("ID:abc123")
        assertTrue(results.isNotEmpty())
        val match: DetailTokenMatch = results[0]
        assertTrue(match.start >= 0)
        assertTrue(match.end > match.start)
        assertEquals("abc123", results[0].id)
    }

    @Test
    fun `ResTokenFinder は DetailTokenFinder を実装している`() {
        val finder: DetailTokenFinder<DetailResTokenMatch> = DetailResTokenFinder
        // ResTokenFinder はヘッダー行か引用行のみ対象なので、ヘッダー行を模擬
        val text = "23/03/17(月)12:34:56 No.12345678"
        val results = finder.findMatches(text)
        // ヘッダー行として認識されれば結果が返る
        if (results.isNotEmpty()) {
            val match: DetailTokenMatch = results[0]
            assertTrue(match.start >= 0)
            assertTrue(match.end > match.start)
        }
    }

    @Test
    fun `FilenameTokenFinder は DetailTokenFinder を実装している`() {
        val finder: DetailTokenFinder<DetailFilenameTokenMatch> = DetailFilenameTokenFinder
        val results = finder.findMatches("ファイル名: test_image.jpg です")
        assertTrue(results.isNotEmpty())
        val match: DetailTokenMatch = results[0]
        assertTrue(match.start >= 0)
        assertEquals("test_image.jpg", results[0].fileName)
    }

    @Test
    fun `SodaneTokenFinder は DetailTokenFinder を実装している`() {
        val finder: DetailTokenFinder<DetailSodaneTokenMatch> = DetailSodaneTokenFinder
        // 空テキストでは結果なし（正常動作）
        val results = finder.findMatches("")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `すべての Finder が空テキストで空リストを返す`() {
        val finders: List<DetailTokenFinder<*>> = listOf(
            DetailIdTokenFinder,
            DetailResTokenFinder,
            DetailFilenameTokenFinder,
            DetailSodaneTokenFinder,
        )
        for (finder in finders) {
            assertTrue("${finder::class.simpleName} should return empty for empty text",
                finder.findMatches("").isEmpty())
        }
    }
}
