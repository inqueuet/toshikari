package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailResNumberExtractorTest {
    @Test
    fun extract_acceptsRegularAndWrappedFormats() {
        assertEquals("12345", DetailResNumberExtractor.extract("No.12345"))
        assertEquals("67890", DetailResNumberExtractor.extract("No\n 67890"))
        assertEquals("222", DetailResNumberExtractor.extract("Ｎｏ．222"))
    }

    @Test
    fun extract_returnsNullWhenMissing() {
        assertNull(DetailResNumberExtractor.extract("本文だけ"))
    }
}
