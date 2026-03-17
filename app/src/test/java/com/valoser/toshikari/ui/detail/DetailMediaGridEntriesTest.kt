package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailMediaGridEntriesTest {
    @Test
    fun build_linksMediaToPreviousTextAndChoosesPreviewByMode() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1"),
            DetailContent.Image(
                id = "i1",
                imageUrl = "https://example.com/full.jpg",
                thumbnailUrl = "https://example.com/thumb.jpg",
                prompt = "image prompt"
            ),
            DetailContent.Video(
                id = "v1",
                videoUrl = "https://example.com/video.mp4",
                thumbnailUrl = "https://example.com/video.jpg",
                prompt = "video prompt"
            )
        )

        val lowBandwidth = DetailMediaGridEntries.build(items, lowBandwidthMode = true)
        val normal = DetailMediaGridEntries.build(items, lowBandwidthMode = false)

        assertEquals(
            listOf(
                DetailMediaGridEntry(
                    mediaIndex = 1,
                    parentTextIndex = 0,
                    fullUrl = "https://example.com/full.jpg",
                    previewUrl = "https://example.com/thumb.jpg",
                    prompt = "image prompt"
                ),
                DetailMediaGridEntry(
                    mediaIndex = 2,
                    parentTextIndex = 0,
                    fullUrl = "https://example.com/video.mp4",
                    previewUrl = "https://example.com/video.jpg",
                    prompt = "video prompt"
                )
            ),
            lowBandwidth
        )

        assertEquals("https://example.com/full.jpg", normal.first().previewUrl)
        assertEquals("https://example.com/video.jpg", normal[1].previewUrl)
    }

    @Test
    fun build_fallsBackToOwnIndexAndOriginalUrl() {
        val items = listOf(
            DetailContent.Image(
                id = "i1",
                imageUrl = "https://example.com/full.jpg",
                thumbnailUrl = "",
                prompt = null
            )
        )

        val entries = DetailMediaGridEntries.build(items, lowBandwidthMode = true)

        assertEquals(
            listOf(
                DetailMediaGridEntry(
                    mediaIndex = 0,
                    parentTextIndex = 0,
                    fullUrl = "https://example.com/full.jpg",
                    previewUrl = "https://example.com/full.jpg",
                    prompt = null
                )
            ),
            entries
        )
    }
}
