package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailSearchHighlightFinderTest {
    @Test
    fun findMatches_returnsCaseInsensitiveLiteralMatches() {
        val matches = DetailSearchHighlightFinder.findMatches("Abc abc ABC", "abc")

        assertEquals(
            listOf(
                DetailSearchHighlightMatch(start = 0, end = 3),
                DetailSearchHighlightMatch(start = 4, end = 7),
                DetailSearchHighlightMatch(start = 8, end = 11)
            ),
            matches
        )
    }

    @Test
    fun findMatches_treatsRegexMetaCharactersAsPlainText() {
        val matches = DetailSearchHighlightFinder.findMatches("a+b a.b", "a+b")

        assertEquals(
            listOf(DetailSearchHighlightMatch(start = 0, end = 3)),
            matches
        )
    }

    @Test
    fun findMatches_returnsEmptyWhenQueryBlank() {
        assertEquals(
            emptyList<DetailSearchHighlightMatch>(),
            DetailSearchHighlightFinder.findMatches("abc", " ")
        )
    }
}
