package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailThreadTitleFormatterTest {
    @Test
    fun format_prefersFirstLineWhenNewlineExists() {
        val title = DetailThreadTitleFormatter.format("1行目<br>2行目") {
            it.replace("<br>", "\n")
        }

        assertEquals("1行目", title)
    }

    @Test
    fun format_splitsByLongWhitespaceWhenNoNewline() {
        val title = DetailThreadTitleFormatter.format("前半   後半", htmlToPlain = { it })

        assertEquals("前半", title)
    }

    @Test
    fun format_usesThreadWordHeuristic() {
        val title = DetailThreadTitleFormatter.format("キルヒアイスレ生き残り", htmlToPlain = { it })

        assertEquals("キルヒアイスレ", title)
    }

    @Test
    fun format_trimsAndRemovesZeroWidthSpace() {
        val title = DetailThreadTitleFormatter.format(" \u200B通常タイトル ", htmlToPlain = { it })

        assertEquals("通常タイトル", title)
    }
}
