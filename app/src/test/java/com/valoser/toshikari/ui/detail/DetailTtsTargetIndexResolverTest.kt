package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailTtsTargetIndexResolverTest {
    @Test
    fun resolve_returnsMatchingTextIndex() {
        val items = listOf(
            DetailContent.Image(id = "i1", imageUrl = "a.jpg"),
            DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1"),
            DetailContent.Text(id = "t2", htmlContent = "No.2", resNum = "2")
        )

        assertEquals(2, DetailTtsTargetIndexResolver.resolve(items, "2"))
    }

    @Test
    fun resolve_returnsNullForBlankOrMissingResNum() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1")
        )

        assertNull(DetailTtsTargetIndexResolver.resolve(items, null))
        assertNull(DetailTtsTargetIndexResolver.resolve(items, " "))
        assertNull(DetailTtsTargetIndexResolver.resolve(items, "9"))
    }
}
