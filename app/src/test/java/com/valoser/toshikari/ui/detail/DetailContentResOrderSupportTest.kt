package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailContentResOrderSupportTest {
    @Test
    fun extractResNumber_prefersModelResNumThenFallsBackToPlainText() {
        val withResNum = DetailContent.Text(id = "t1", htmlContent = "ignored", resNum = "12")
        val fromPlain = DetailContent.Text(id = "t2", htmlContent = "No.34")

        assertEquals(12, DetailContentResOrderSupport.extractResNumber(withResNum) { it.htmlContent })
        assertEquals(34, DetailContentResOrderSupport.extractResNumber(fromPlain) { it.htmlContent })
    }

    @Test
    fun extractResNumber_returnsNullForNonTextOrMissingNumber() {
        assertNull(DetailContentResOrderSupport.extractResNumber(DetailContent.Image(id = "i1", imageUrl = "a.jpg")) { "" })
        assertNull(
            DetailContentResOrderSupport.extractResNumber(
                DetailContent.Text(id = "t1", htmlContent = "body only")
            ) { it.htmlContent }
        )
    }

    @Test
    fun sortGroupsByResNumber_ordersByExtractedNumber() {
        val first = listOf(DetailContent.Text(id = "t2", htmlContent = "No.20"))
        val second = listOf(DetailContent.Text(id = "t1", htmlContent = "No.10"))
        val third = listOf(DetailContent.Text(id = "tX", htmlContent = "body only"))

        val sorted = DetailContentResOrderSupport.sortGroupsByResNumber(
            listOf(first, second, third)
        ) { it.htmlContent }

        assertEquals(listOf(second, first, third), sorted)
    }
}
