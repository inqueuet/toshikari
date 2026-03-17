package com.valoser.toshikari.metadata

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class ExifPromptExtractorTest {

    // ====== decodeUserComment ======

    @Test
    fun `ASCII マーカー付きコメントをデコードできる`() {
        // "ASCII\0\0\0" + テキスト
        val marker = "ASCII\u0000\u0000\u0000"
        val text = "a beautiful landscape"
        val raw = marker + text
        val result = ExifPromptExtractor.decodeUserComment(raw)
        assertEquals("a beautiful landscape", result)
    }

    @Test
    fun `UNICODE マーカー付きコメントをデコードできる`() {
        // "UNICODE\0" (8 bytes) + UTF-16LE text
        val marker = "UNICODE\u0000".toByteArray(StandardCharsets.ISO_8859_1)
        val textBytes = "hello world".toByteArray(StandardCharsets.UTF_16LE)
        val combined = marker + textBytes
        val raw = String(combined, StandardCharsets.ISO_8859_1)
        val result = ExifPromptExtractor.decodeUserComment(raw)
        assertEquals("hello world", result)
    }

    @Test
    fun `マーカーなしの短い文字列をそのまま返す`() {
        val result = ExifPromptExtractor.decodeUserComment("short")
        assertEquals("short", result)
    }

    @Test
    fun `空白のみの場合は null を返す`() {
        val result = ExifPromptExtractor.decodeUserComment("   ")
        assertNull(result)
    }

    @Test
    fun `マーカーなしの8文字以上の文字列をそのまま返す`() {
        val result = ExifPromptExtractor.decodeUserComment("this is a long prompt text without any marker")
        assertEquals("this is a long prompt text without any marker", result)
    }

    // ====== decodeXpString ======

    @Test
    fun `UTF-16LE 文字列をデコードできる`() {
        val text = "test comment"
        val utf16leBytes = text.toByteArray(StandardCharsets.UTF_16LE)
        val raw = String(utf16leBytes, StandardCharsets.ISO_8859_1)
        val result = ExifPromptExtractor.decodeXpString(raw)
        assertEquals("test comment", result)
    }

    @Test
    fun `空の XP 文字列では null を返す`() {
        val result = ExifPromptExtractor.decodeXpString("")
        assertNull(result)
    }
}
