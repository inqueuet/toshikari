package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailThreadTitleQuoteFinderTest {
    @Test
    fun findMatches_detectsMatchingNonQuoteLines() {
        val text = "本文\n スレ　タイ \n別行"

        val matches = DetailThreadTitleQuoteFinder.findMatches(text, "スレ タイ")

        assertEquals(
            listOf(
                DetailThreadTitleQuoteMatch(
                    start = 3,
                    end = 10,
                    token = ">スレ　タイ"
                )
            ),
            matches
        )
    }

    @Test
    fun findMatches_skipsExistingQuoteLinesAndBlankNeedle() {
        val text = ">タイトル\nタイトル"

        assertEquals(
            listOf(
                DetailThreadTitleQuoteMatch(
                    start = 6,
                    end = 10,
                    token = ">タイトル"
                )
            ),
            DetailThreadTitleQuoteFinder.findMatches(text, "タイトル")
        )
        assertEquals(emptyList<DetailThreadTitleQuoteMatch>(), DetailThreadTitleQuoteFinder.findMatches(text, "  "))
    }
}
