package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailHeaderLineRulesTest {
    @Test
    fun shouldHighlightBackground_acceptsFirstDateTimeLine() {
        assertTrue(DetailHeaderLineRules.shouldHighlightBackground("24/01/01(月)12:34:56 No.12345"))
        assertTrue(DetailHeaderLineRules.shouldHighlightBackground("\n24/01/01(月)12:34:56 No.12345\n本文"))
    }

    @Test
    fun shouldHighlightBackground_rejectsNonDateHeader() {
        assertFalse(DetailHeaderLineRules.shouldHighlightBackground("1 無念 Name としあき No.12345"))
        assertFalse(DetailHeaderLineRules.shouldHighlightBackground(""))
    }

    @Test
    fun isHeaderLine_acceptsDateTimeLinesAnywhere() {
        assertTrue(DetailHeaderLineRules.isHeaderLine("24/01/01(月)12:34:56", lineIndex = 2))
    }

    @Test
    fun isHeaderLine_acceptsFirstLineMetaPattern() {
        assertTrue(DetailHeaderLineRules.isHeaderLine("1 無念 Name としあき No.12345", lineIndex = 0))
        assertTrue(DetailHeaderLineRules.isHeaderLine("1 無念 Name としあき Ｎｏ．12345", lineIndex = 0))
    }

    @Test
    fun isHeaderLine_rejectsSamePatternAfterFirstLine() {
        assertFalse(DetailHeaderLineRules.isHeaderLine("1 無念 Name としあき No.12345", lineIndex = 1))
    }

    @Test
    fun isHeaderLine_rejectsPlainBodyLine() {
        assertFalse(DetailHeaderLineRules.isHeaderLine("これは本文です", lineIndex = 0))
    }
}
