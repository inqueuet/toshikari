package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailBodyTextExtractorTest {
    @Test
    fun extract_skipsHeaderLikeLinesAndKeepsBody() {
        val plain = """
            24/01/01(月)12:34:56
            ID:ABC123
            No.12345
            本文1
            >引用行
            本文2
        """.trimIndent()

        val result = DetailBodyTextExtractor.extract(plain)

        assertEquals("本文1\n>引用行\n本文2", result)
    }

    @Test
    fun extract_removesFileInfoAndTrailingSizeLines() {
        val plain = """
            画像: sample.jpg (12KB)
            foo.jpg-(180986 B)
            本文
            [180986 B]
            
        """.trimIndent()

        val result = DetailBodyTextExtractor.extract(plain)

        assertEquals("本文", result)
    }

    @Test
    fun extract_returnsEmptyWhenOnlyHeadersExist() {
        val plain = """
            ID:ABC123
            No.12345
            24/01/01(月)12:34:56
        """.trimIndent()

        val result = DetailBodyTextExtractor.extract(plain)

        assertEquals("", result)
    }
}
