package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailSodaneTokenFinderTest {
    @Test
    fun findMatches_detectsTokenAfterNoInHeaderLine() {
        val text = "24/01/01(月)12:34:56 No.12345 そうだね ID:ABC"

        val matches = DetailSodaneTokenFinder.findMatches(text)

        assertEquals(listOf(DetailSodaneTokenMatch(start = 29, end = 33)), matches)
    }

    @Test
    fun findMatches_ignoresQuoteLinesAndTokensAfterId() {
        val text = """
            >24/01/01(月)12:34:56 No.12345 そうだね
            24/01/01(月)12:34:56 No.12345 ID:ABC そうだね
        """.trimIndent()

        val matches = DetailSodaneTokenFinder.findMatches(text)

        assertEquals(emptyList<DetailSodaneTokenMatch>(), matches)
    }

    @Test
    fun findMatches_acceptsPlusToken() {
        val text = "24/01/01(月)12:34:56 No.12345 +"

        val matches = DetailSodaneTokenFinder.findMatches(text)

        assertEquals(listOf(DetailSodaneTokenMatch(start = 29, end = 30)), matches)
    }
}
