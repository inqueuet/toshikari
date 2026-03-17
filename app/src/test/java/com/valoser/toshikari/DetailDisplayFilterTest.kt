package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailDisplayFilterTest {
    @Test
    fun phantomQuoteResponseAndAttachedMediaAreHidden() {
        val items = listOf(
            text(id = "1", html = "first"),
            text(id = "2", html = "phantom"),
            image(id = "3")
        )

        val result = DetailDisplayFilter.apply(
            items = items,
            plainTextCache = mapOf(
                "1" to "既出の本文",
                "2" to ">未出の本文"
            ),
            config = DisplayFilterConfig(
                hideDeletedRes = false,
                hideDuplicateRes = false,
                duplicateResThreshold = 2
            ),
            plainTextOf = { error("plainTextCache should be used in this test") }
        )

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun duplicateResponsesOverThresholdAreHiddenWithAttachedMedia() {
        val items = listOf(
            text(id = "1", html = "first"),
            text(id = "2", html = "duplicate"),
            image(id = "3"),
            text(id = "4", html = "duplicate")
        )

        val result = DetailDisplayFilter.apply(
            items = items,
            plainTextCache = mapOf(
                "1" to "同じ本文",
                "2" to "同じ本文",
                "4" to "同じ本文"
            ),
            config = DisplayFilterConfig(
                hideDeletedRes = false,
                hideDuplicateRes = true,
                duplicateResThreshold = 1
            ),
            plainTextOf = { error("plainTextCache should be used in this test") }
        )

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun deletedResponseIsHiddenOnlyWhenConfigured() {
        val deleted = text(
            id = "1",
            html = "スレッドを立てた人によって削除されました"
        )
        val normal = text(id = "2", html = "normal")

        val hidden = DetailDisplayFilter.apply(
            items = listOf(deleted, normal),
            plainTextCache = emptyMap(),
            config = DisplayFilterConfig(
                hideDeletedRes = true,
                hideDuplicateRes = false,
                duplicateResThreshold = 2
            ),
            plainTextOf = { it.htmlContent }
        )
        val visible = DetailDisplayFilter.apply(
            items = listOf(deleted, normal),
            plainTextCache = emptyMap(),
            config = DisplayFilterConfig(
                hideDeletedRes = false,
                hideDuplicateRes = false,
                duplicateResThreshold = 2
            ),
            plainTextOf = { it.htmlContent }
        )

        assertEquals(listOf("2"), hidden.map { it.id })
        assertEquals(listOf("1", "2"), visible.map { it.id })
    }

    private fun text(id: String, html: String): DetailContent.Text {
        return DetailContent.Text(id = id, htmlContent = html)
    }

    private fun image(id: String): DetailContent.Image {
        return DetailContent.Image(id = id, imageUrl = "https://example.com/$id.jpg")
    }
}
