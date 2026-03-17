package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailSearchEngineTest {
    @Test
    fun findHitPositions_matchesTextMediaPromptAndFileName() {
        val contents = listOf(
            DetailContent.Text(id = "1", htmlContent = "body"),
            DetailContent.Image(
                id = "2",
                imageUrl = "https://example.com/cat.png",
                prompt = "sleepy cat"
            ),
            DetailContent.Video(
                id = "3",
                videoUrl = "https://example.com/movie.mp4",
                fileName = "trailer.mp4"
            )
        )

        val textHits = DetailSearchEngine.findHitPositions("hello", contents) {
            if (it.id == "1") "say hello" else ""
        }
        val promptHits = DetailSearchEngine.findHitPositions("cat", contents) { "" }
        val fileHits = DetailSearchEngine.findHitPositions("trailer", contents) { "" }

        assertEquals(listOf(0), textHits)
        assertEquals(listOf(1), promptHits)
        assertEquals(listOf(2), fileHits)
    }

    @Test
    fun findHitPositions_ignoresBlankQuery() {
        val hits = DetailSearchEngine.findHitPositions("   ", listOf(DetailContent.Text("1", "body"))) { "body" }

        assertTrue(hits.isEmpty())
    }

    @Test
    fun buildState_marksSearchActiveOnlyWhenQueryAndHitsExist() {
        val active = DetailSearchEngine.buildState(
            hasQuery = true,
            hitPositions = listOf(1, 3),
            currentHitIndex = 1
        )
        val inactive = DetailSearchEngine.buildState(
            hasQuery = true,
            hitPositions = emptyList(),
            currentHitIndex = 0
        )

        assertTrue(active.active)
        assertEquals(2, active.currentIndexDisplay)
        assertEquals(2, active.total)
        assertFalse(inactive.active)
        assertEquals(0, inactive.currentIndexDisplay)
    }

    @Test
    fun buildState_zeroesCurrentIndexWhenOutOfRange() {
        val state = DetailSearchEngine.buildState(
            hasQuery = true,
            hitPositions = listOf(2),
            currentHitIndex = 5
        )

        assertEquals(0, state.currentIndexDisplay)
        assertEquals(1, state.total)
    }
}
