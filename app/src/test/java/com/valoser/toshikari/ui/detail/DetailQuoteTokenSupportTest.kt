package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailQuoteTokenSupportTest {
    @Test
    fun parse_returnsCoreAndNormalizedCore() {
        val info = DetailQuoteTokenSupport.parse(" ＞＞  Foo　Bar ")

        assertEquals(">>  Foo Bar ", info?.normalizedToken)
        assertEquals("Foo Bar", info?.core)
        assertEquals("Foo Bar", info?.normalizedCore)
    }

    @Test
    fun parse_returnsNullWhenCoreBlank() {
        assertNull(DetailQuoteTokenSupport.parse(">>   "))
    }

    @Test
    fun isFilenameToken_acceptsQuotedFileNamesOnly() {
        assertTrue(DetailQuoteTokenSupport.isFilenameToken(">> sample.JPG"))
        assertFalse(DetailQuoteTokenSupport.isFilenameToken(">> not a file"))
    }
}
