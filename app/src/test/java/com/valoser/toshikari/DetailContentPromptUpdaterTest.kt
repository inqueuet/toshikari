package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DetailContentPromptUpdaterTest {
    @Test
    fun updatePrompt_updatesOnlyMatchingImageOrVideo() {
        val contents = listOf(
            DetailContent.Image(
                id = "image",
                imageUrl = "https://example.com/a.jpg"
            ),
            DetailContent.Video(
                id = "video",
                videoUrl = "https://example.com/a.mp4"
            ),
            DetailContent.Text(
                id = "text",
                htmlContent = "body"
            )
        )

        val updatedImage = DetailContentPromptUpdater.updatePrompt(contents, "image", "image prompt")
        val updatedVideo = DetailContentPromptUpdater.updatePrompt(contents, "video", "video prompt")

        assertEquals("image prompt", (updatedImage[0] as DetailContent.Image).prompt)
        assertEquals(null, (updatedImage[1] as DetailContent.Video).prompt)
        assertEquals("video prompt", (updatedVideo[1] as DetailContent.Video).prompt)
        assertEquals(contents[2], updatedVideo[2])
    }

    @Test
    fun updatePrompt_returnsSameListWhenNothingChanges() {
        val contents = listOf(
            DetailContent.Image(
                id = "image",
                imageUrl = "https://example.com/a.jpg",
                prompt = "prompt"
            )
        )

        val unchangedById = DetailContentPromptUpdater.updatePrompt(contents, "missing", "prompt")
        val unchangedByValue = DetailContentPromptUpdater.updatePrompt(contents, "image", "prompt")

        assertSame(contents, unchangedById)
        assertSame(contents, unchangedByValue)
    }
}
