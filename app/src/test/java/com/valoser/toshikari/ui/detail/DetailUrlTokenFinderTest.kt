package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailUrlTokenFinderTest {
    @Test
    fun findMatches_returnsUrlsWithOffsets() {
        val pattern = Regex("""https?://[^\s]+""")

        val matches = DetailUrlTokenFinder.findMatches(
            text = "See https://example.com and http://test.local/a",
            pattern = pattern
        )

        assertEquals(
            listOf(
                DetailUrlTokenMatch(start = 4, end = 23, url = "https://example.com"),
                DetailUrlTokenMatch(start = 28, end = 47, url = "http://test.local/a")
            ),
            matches
        )
    }

    @Test
    fun findMatches_returnsEmptyWhenNoUrlFound() {
        val matches = DetailUrlTokenFinder.findMatches(
            text = "plain text only",
            pattern = Regex("""https?://[^\s]+""")
        )

        assertEquals(emptyList<DetailUrlTokenMatch>(), matches)
    }
}
