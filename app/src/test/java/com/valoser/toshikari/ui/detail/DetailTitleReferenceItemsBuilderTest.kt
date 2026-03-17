package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailTitleReferenceItemsBuilderTest {
    @Test
    fun build_mergesContentAndNumberReferencesInOrder() {
        val op = DetailContent.Text(id = "t1", htmlContent = "title", resNum = "1")
        val byContentAndNumber = DetailContent.Text(id = "t2", htmlContent = ">title\n>No.1", resNum = "2")
        val byNumberOnly = DetailContent.Text(id = "t3", htmlContent = ">No.1", resNum = "3")

        val result = DetailTitleReferenceItemsBuilder.build(
            items = listOf(op, byContentAndNumber, byNumberOnly),
            title = "title",
            plainTextOf = { it.htmlContent }
        )

        assertEquals(listOf(op, byContentAndNumber, byNumberOnly), result)
    }

    @Test
    fun build_returnsEmptyWhenNoTextExists() {
        val result = DetailTitleReferenceItemsBuilder.build(
            items = listOf(DetailContent.Image(id = "i1", imageUrl = "a.jpg")),
            title = "title",
            plainTextOf = { it.htmlContent }
        )

        assertEquals(emptyList<DetailContent>(), result)
    }
}
