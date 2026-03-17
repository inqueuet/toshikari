package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DetailThreadEndTimeNormalizerTest {
    @Test
    fun normalize_returnsSameListWhenNoThreadEndTimeExists() {
        val items = listOf(
            DetailContent.Text(id = "text_1", htmlContent = "a"),
            DetailContent.Image(id = "image_1", imageUrl = "https://example.com/a.jpg")
        )

        val normalized = DetailThreadEndTimeNormalizer.normalize(items)

        assertSame(items, normalized)
    }

    @Test
    fun normalize_keepsOnlyLastThreadEndTime() {
        val first = DetailContent.ThreadEndTime(id = "end_1", endTime = "10:00")
        val last = DetailContent.ThreadEndTime(id = "end_2", endTime = "11:00")
        val items = listOf(
            DetailContent.Text(id = "text_1", htmlContent = "a"),
            first,
            DetailContent.Image(id = "image_1", imageUrl = "https://example.com/a.jpg"),
            last
        )

        val normalized = DetailThreadEndTimeNormalizer.normalize(items)

        assertEquals(
            listOf(
                DetailContent.Text(id = "text_1", htmlContent = "a"),
                DetailContent.Image(id = "image_1", imageUrl = "https://example.com/a.jpg"),
                last
            ),
            normalized
        )
    }
}
