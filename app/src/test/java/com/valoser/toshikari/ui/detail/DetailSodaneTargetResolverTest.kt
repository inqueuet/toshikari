package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailSodaneTargetResolverTest {
    @Test
    fun resolve_returnsResNumberFromTappedLine() {
        val text = """
            24/01/01(月)12:34:56 No.12345 そうだね
            24/01/01(月)12:35:00 No.67890 そうだね
        """.trimIndent()

        assertEquals("12345", DetailSodaneTargetResolver.resolve(text, offset = 25, fallbackResNum = "99999"))
        assertEquals("67890", DetailSodaneTargetResolver.resolve(text, offset = 60, fallbackResNum = "99999"))
    }

    @Test
    fun resolve_returnsFallbackWhenLineHasNoResNumber() {
        assertEquals(
            "55555",
            DetailSodaneTargetResolver.resolve("そうだねだけ", offset = 2, fallbackResNum = "55555")
        )
    }

    @Test
    fun resolve_returnsNullWhenBothLineAndFallbackMissing() {
        assertNull(DetailSodaneTargetResolver.resolve("そうだねだけ", offset = 2, fallbackResNum = null))
        assertNull(DetailSodaneTargetResolver.resolve("", offset = 0, fallbackResNum = ""))
    }
}
