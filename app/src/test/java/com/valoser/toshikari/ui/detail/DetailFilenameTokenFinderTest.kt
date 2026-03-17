package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailFilenameTokenFinderTest {
    @Test
    fun findMatches_detectsImageAndVideoFileNames() {
        val text = "a.jpg と MOVIE.MP4 と text"

        val matches = DetailFilenameTokenFinder.findMatches(text)

        assertEquals(
            listOf(
                DetailFilenameTokenMatch(start = 0, end = 5, fileName = "a.jpg"),
                DetailFilenameTokenMatch(start = 8, end = 17, fileName = "MOVIE.MP4")
            ),
            matches
        )
    }

    @Test
    fun findMatches_ignoresNonMediaExtensions() {
        assertEquals(emptyList<DetailFilenameTokenMatch>(), DetailFilenameTokenFinder.findMatches("a.txt b.zip"))
    }
}
