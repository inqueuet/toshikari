package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DetailPromptSanitizerTest {
    @Test
    fun normalize_decodesHtmlPrompt() {
        val result = DetailPromptSanitizer.normalize("  &lt;b&gt;hello&lt;/b&gt;  ")

        assertEquals("hello", result)
    }

    @Test
    fun normalize_returnsTrimmedPlainTextWhenNoHtmlNormalizationNeeded() {
        val result = DetailPromptSanitizer.normalize("  plain prompt  ")

        assertEquals("plain prompt", result)
    }

    @Test
    fun normalize_returnsNullForBlankPrompt() {
        val result = DetailPromptSanitizer.normalize("   ")

        assertNull(result)
    }

    @Test
    fun sanitizeContents_updatesOnlyMediaPrompts() {
        val input = listOf(
            DetailContent.Image(
                id = "image",
                imageUrl = "https://example.com/a.jpg",
                prompt = "&lt;i&gt;image&lt;/i&gt;"
            ),
            DetailContent.Video(
                id = "video",
                videoUrl = "https://example.com/a.mp4",
                prompt = "plain"
            ),
            DetailContent.Text(
                id = "text",
                htmlContent = "body"
            )
        )

        val result = DetailPromptSanitizer.sanitizeContents(input)

        assertEquals("image", (result[0] as DetailContent.Image).prompt)
        assertEquals("plain", (result[1] as DetailContent.Video).prompt)
        assertEquals(input[2], result[2])
    }

    @Test
    fun sanitizeContents_returnsSameListWhenNothingChanges() {
        val input = listOf(
            DetailContent.Image(
                id = "image",
                imageUrl = "https://example.com/a.jpg",
                prompt = "plain"
            )
        )

        val result = DetailPromptSanitizer.sanitizeContents(input)

        assertSame(input, result)
    }
}
