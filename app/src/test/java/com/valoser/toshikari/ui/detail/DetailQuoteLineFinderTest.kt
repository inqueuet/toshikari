package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailQuoteLineFinderTest {
    @Test
    fun findMatches_detectsQuoteLinesWithOffsets() {
        val text = "本文\n  ＞引用1\n>引用2"

        val matches = DetailQuoteLineFinder.findMatches(text)

        assertEquals(
            listOf(
                DetailQuoteLineMatch(start = 5, end = 9, token = ">引用1"),
                DetailQuoteLineMatch(start = 10, end = 14, token = ">引用2")
            ),
            matches
        )
    }

    @Test
    fun findMatches_returnsEmptyWhenNoQuoteLinesExist() {
        assertEquals(emptyList<DetailQuoteLineMatch>(), DetailQuoteLineFinder.findMatches("本文\n次行"))
    }
}
