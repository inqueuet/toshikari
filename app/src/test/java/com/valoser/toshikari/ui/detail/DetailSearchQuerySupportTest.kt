package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailSearchQuerySupportTest {
    @Test
    fun normalize_trimsAroundQuery() {
        assertEquals("abc", DetailSearchQuerySupport.normalize("  abc  "))
        assertEquals("a b", DetailSearchQuerySupport.normalize("  a b  "))
    }

    @Test
    fun normalize_returnsNullForBlankInput() {
        assertNull(DetailSearchQuerySupport.normalize(""))
        assertNull(DetailSearchQuerySupport.normalize("   "))
    }
}
