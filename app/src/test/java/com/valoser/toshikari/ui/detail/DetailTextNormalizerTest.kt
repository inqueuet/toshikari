package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailTextNormalizerTest {
    @Test
    fun normalizePlain_unifiesSpacingAndQuoteSymbols() {
        val result = DetailTextNormalizer.normalizePlain("A\u200B　＞≫B")

        assertEquals("A >>B", result)
    }

    @Test
    fun normalizeCollapsed_collapsesWhitespaceAndTrims() {
        val result = DetailTextNormalizer.normalizeCollapsed("  A\u3000 \n B  ")

        assertEquals("A B", result)
    }

    @Test
    fun normalizeQuoteToken_trimsLeadingWhitespaceOnly() {
        val result = DetailTextNormalizer.normalizeQuoteToken("  ＞＞ test  ")

        assertEquals(">> test  ", result)
    }
}
