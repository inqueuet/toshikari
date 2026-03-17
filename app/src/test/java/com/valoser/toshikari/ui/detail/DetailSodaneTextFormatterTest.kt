package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailSodaneTextFormatterTest {
    @Test
    fun padTokensForSpacing_insertsMissingSpacesAndAppendsSodaneForHeaderLine() {
        val source = "24/01/01(月)12:34:56)No.12345ID:ABC123"

        val result = DetailSodaneTextFormatter.padTokensForSpacing(source)

        assertEquals("24/01/01(月)12:34:56) No.12345 そうだね ID:ABC123", result)
    }

    @Test
    fun padTokensForSpacing_doesNotAppendSodaneToQuoteLine() {
        val source = ">No.12345"

        val result = DetailSodaneTextFormatter.padTokensForSpacing(source)

        assertEquals("> No.12345", result)
    }

    @Test
    fun applySodaneDisplay_replacesHeaderTokenByResNumOverride() {
        val source = "24/01/01(月)12:34:56 No.12345 そうだね\n本文"

        val result = DetailSodaneTextFormatter.applySodaneDisplay(
            text = source,
            overrides = mapOf("12345" to 3),
            selfResNum = null
        )

        assertEquals("24/01/01(月)12:34:56 No.12345 そうだねx3\n本文", result)
    }

    @Test
    fun applySodaneDisplay_usesFallbackResNumWhenHeaderHasNoNoToken() {
        val source = "24/01/01(月)12:34:56 +"

        val result = DetailSodaneTextFormatter.applySodaneDisplay(
            text = source,
            overrides = mapOf("999" to 2),
            selfResNum = "999"
        )

        assertEquals("24/01/01(月)12:34:56 そうだねx2", result)
    }
}
