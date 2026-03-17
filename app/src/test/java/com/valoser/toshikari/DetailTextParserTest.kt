package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailTextParserTest {
    @Test
    fun extractPlainBodyFromPlain_removesMetadataAndAttachmentLines() {
        val plain = """
            24/01/01(月)12:34:56
            ID:ABC123
            No.12345
            本文1
            画像: sample.jpg (12KB)
            サムネ sample.jpg
            本文2
        """.trimIndent()

        val result = DetailTextParser.extractPlainBodyFromPlain(plain)

        assertEquals("本文1\n本文2", result)
    }

    @Test
    fun extractIdFromHtml_usesHtmlMatchBeforeFallback() {
        val html = "<span>ID:ABC123</span>"

        val result = DetailTextParser.extractIdFromHtml(html) {
            error("fallback should not be used")
        }

        assertEquals("ABC123", result)
    }

    @Test
    fun extractIdFromHtml_usesPlainTextFallbackWhenHtmlRegexCannotMatch() {
        val html = "<span>ID</span><span>:</span><span>ABC123</span>"

        val result = DetailTextParser.extractIdFromHtml(html) {
            "ID:ABC123 No.12345"
        }

        assertEquals("ABC123", result)
    }

    @Test
    fun extractIdFromHtml_returnsNullWhenMissing() {
        val result = DetailTextParser.extractIdFromHtml("<span>No id</span>") {
            "plain text without id"
        }

        assertNull(result)
    }
}
