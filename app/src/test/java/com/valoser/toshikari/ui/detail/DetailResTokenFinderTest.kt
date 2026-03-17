package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailResTokenFinderTest {
    @Test
    fun findMatches_detectsHeaderAndQuoteTokens() {
        val text = """
            24/01/01(月)12:34:56 No.12345
            > No.67890
            本文 No.11111
        """.trimIndent()

        val matches = DetailResTokenFinder.findMatches(text)

        assertEquals(
            listOf(
                DetailResTokenMatch(start = 20, end = 28, number = "12345"),
                DetailResTokenMatch(start = 31, end = 39, number = "67890")
            ),
            matches
        )
    }

    @Test
    fun findMatches_acceptsFullWidthNoOnFirstLineMeta() {
        val text = "1 無念 Name としあき Ｎｏ．222"

        val matches = DetailResTokenFinder.findMatches(text)

        assertEquals(
            listOf(DetailResTokenMatch(start = 15, end = 21, number = "222")),
            matches
        )
    }
}
