package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailIdTokenFinderTest {
    @Test
    fun findMatches_detectsAsciiAndFullWidthColonForms() {
        val text = "ID:ABC123 と ID：xyz./+"

        val matches = DetailIdTokenFinder.findMatches(text)

        assertEquals(
            listOf(
                DetailIdTokenMatch(start = 0, end = 9, id = "ABC123"),
                DetailIdTokenMatch(start = 12, end = 21, id = "xyz./+")
            ),
            matches
        )
    }

    @Test
    fun findMatches_returnsEmptyWhenNoIdTokenExists() {
        assertEquals(emptyList<DetailIdTokenMatch>(), DetailIdTokenFinder.findMatches("本文だけ"))
    }
}
