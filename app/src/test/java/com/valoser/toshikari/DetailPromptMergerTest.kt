package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailPromptMergerTest {
    @Test
    fun merge_inheritsPromptByFileNameFirst() {
        val prior = listOf(
            DetailContent.Image(
                id = "prior-image",
                imageUrl = "https://example.com/a/original-name.jpg",
                fileName = "shared-name.jpg",
                prompt = "inherited prompt"
            )
        )
        val base = listOf(
            DetailContent.Image(
                id = "base-image",
                imageUrl = "https://example.com/b/another-name.jpg",
                fileName = "shared-name.jpg"
            )
        )

        val merged = DetailPromptMerger.merge(base, prior)

        assertEquals("inherited prompt", (merged.single() as DetailContent.Image).prompt)
    }

    @Test
    fun merge_fallsBackToUrlTailForVideos() {
        val prior = listOf(
            DetailContent.Video(
                id = "prior-video",
                videoUrl = "https://example.com/media/clip.mp4",
                prompt = "video prompt"
            )
        )
        val base = listOf(
            DetailContent.Video(
                id = "base-video",
                videoUrl = "https://cdn.example.net/files/clip.mp4"
            )
        )

        val merged = DetailPromptMerger.merge(base, prior)

        assertEquals("video prompt", (merged.single() as DetailContent.Video).prompt)
    }

    @Test
    fun merge_preservesExistingPromptOnBase() {
        val prior = listOf(
            DetailContent.Image(
                id = "prior",
                imageUrl = "https://example.com/image.jpg",
                prompt = "old prompt"
            )
        )
        val base = listOf(
            DetailContent.Image(
                id = "base",
                imageUrl = "https://example.com/image.jpg",
                prompt = "current prompt"
            )
        )

        val merged = DetailPromptMerger.merge(base, prior)

        assertEquals("current prompt", (merged.single() as DetailContent.Image).prompt)
    }

    @Test
    fun merge_leavesPromptEmptyWhenNoMatchExists() {
        val prior = listOf(
            DetailContent.Image(
                id = "prior",
                imageUrl = "https://example.com/image-a.jpg",
                prompt = "old prompt"
            )
        )
        val base = listOf(
            DetailContent.Image(
                id = "base",
                imageUrl = "https://example.com/image-b.jpg"
            )
        )

        val merged = DetailPromptMerger.merge(base, prior)

        assertNull((merged.single() as DetailContent.Image).prompt)
    }

    @Test
    fun mergeByFileName_doesNotFallBackToUrlTail() {
        val prior = listOf(
            DetailContent.Image(
                id = "prior",
                imageUrl = "https://example.com/path/shared.jpg",
                prompt = "old prompt"
            )
        )
        val base = listOf(
            DetailContent.Image(
                id = "base",
                imageUrl = "https://cdn.example.net/shared.jpg"
            )
        )

        val merged = DetailPromptMerger.mergeByFileName(base, prior)

        assertNull((merged.single() as DetailContent.Image).prompt)
    }

    @Test
    fun mergeByFileName_inheritsWhenFileNameMatches() {
        val prior = listOf(
            DetailContent.Video(
                id = "prior",
                videoUrl = "https://example.com/a.mp4",
                fileName = "shared.mp4",
                prompt = "old prompt"
            )
        )
        val base = listOf(
            DetailContent.Video(
                id = "base",
                videoUrl = "https://example.com/b.mp4",
                fileName = "shared.mp4"
            )
        )

        val merged = DetailPromptMerger.mergeByFileName(base, prior)

        assertEquals("old prompt", (merged.single() as DetailContent.Video).prompt)
    }
}
