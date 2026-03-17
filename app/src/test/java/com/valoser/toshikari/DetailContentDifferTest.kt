package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailContentDifferTest {
    @Test
    fun diff_filtersOutItemsWithSameContentHashEvenIfIdDiffers() {
        val current = listOf(
            DetailContent.Text(id = "text_1", htmlContent = "<b>meta</b>", resNum = "1")
        )
        val parsed = listOf(
            DetailContent.Text(id = "text_2", htmlContent = "<i>meta2</i>", resNum = "2"),
            DetailContent.Text(id = "text_3", htmlContent = "new body", resNum = "3")
        )

        val diff = DetailContentDiffer.diff(current, parsed) { text ->
            when (text.id) {
                "text_1", "text_2" -> "same-body"
                else -> "new-body"
            }
        }

        assertEquals(listOf(parsed[1]), diff.newItems)
    }

    @Test
    fun diff_detectsDuplicateIdsAndContentGroups() {
        val parsed = listOf(
            DetailContent.Text(id = "text_1", htmlContent = "a", resNum = "1"),
            DetailContent.Text(id = "text_1", htmlContent = "b", resNum = "2"),
            DetailContent.Image(id = "image_1", imageUrl = "https://example.com/a.jpg"),
            DetailContent.Image(id = "image_2", imageUrl = "https://example.com/a.jpg")
        )

        val diff = DetailContentDiffer.diff(emptyList(), parsed) { text ->
            "body-${text.resNum}"
        }

        assertEquals(setOf("text_1"), diff.duplicateIds)
        assertEquals(1, diff.duplicateContentHashes.size)
    }

    @Test
    fun diff_ignoresImagePromptChangesWhenUrlMatches() {
        val current = listOf(
            DetailContent.Image(
                id = "image_1",
                imageUrl = "https://example.com/a.jpg",
                prompt = "old"
            )
        )
        val parsed = listOf(
            DetailContent.Image(
                id = "image_2",
                imageUrl = "https://example.com/a.jpg",
                prompt = "new"
            )
        )

        val diff = DetailContentDiffer.diff(current, parsed) { "" }

        assertTrue(diff.newItems.isEmpty())
    }
}
