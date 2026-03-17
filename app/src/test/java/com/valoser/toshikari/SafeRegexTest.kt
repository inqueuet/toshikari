package com.valoser.toshikari

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeRegexTest {

    // --- compile ---

    @Test
    fun compile_validPatternReturnsRegex() {
        val regex = SafeRegex.compile("""\d+""")
        assertNotNull(regex)
    }

    @Test
    fun compile_invalidPatternReturnsNull() {
        val regex = SafeRegex.compile("[unclosed")
        assertNull(regex)
    }

    @Test
    fun compile_tooLongPatternReturnsNull() {
        val longPattern = "a".repeat(2000)
        val regex = SafeRegex.compile(longPattern)
        assertNull(regex)
    }

    @Test
    fun compile_nestedQuantifierRejected() {
        // (a+)+ は ReDoS トリガーの典型例
        val regex = SafeRegex.compile("(a+)+")
        assertNull(regex)
    }

    @Test
    fun compile_nestedStarQuantifierRejected() {
        val regex = SafeRegex.compile("(a*)*")
        assertNull(regex)
    }

    @Test
    fun compile_simpleQuantifierAllowed() {
        // ネストされていない量指子は許可
        val regex = SafeRegex.compile("a+b*c?")
        assertNotNull(regex)
    }

    @Test
    fun compile_respectsOptions() {
        val regex = SafeRegex.compile("hello", setOf(RegexOption.IGNORE_CASE))
        assertNotNull(regex)
        assertTrue(regex!!.containsMatchIn("HELLO"))
    }

    @Test
    fun compile_cacheReturnsSameInstance() {
        val r1 = SafeRegex.compile("test")
        val r2 = SafeRegex.compile("test")
        assertTrue(r1 === r2)
    }

    @Test
    fun compile_differentOptionsDifferentCacheEntries() {
        val r1 = SafeRegex.compile("test")
        val r2 = SafeRegex.compile("test", setOf(RegexOption.IGNORE_CASE))
        assertTrue(r1 !== r2)
    }

    // --- containsMatchIn ---

    @Test
    fun containsMatchIn_matchesCorrectly() {
        assertTrue(SafeRegex.containsMatchIn("""\d{3}""", "abc123def"))
    }

    @Test
    fun containsMatchIn_noMatchReturnsFalse() {
        assertFalse(SafeRegex.containsMatchIn("""\d{3}""", "abcdef"))
    }

    @Test
    fun containsMatchIn_invalidPatternReturnsFalse() {
        assertFalse(SafeRegex.containsMatchIn("[bad", "anything"))
    }

    @Test
    fun containsMatchIn_ignoreCaseWorks() {
        assertTrue(SafeRegex.containsMatchIn("hello", "HELLO WORLD", ignoreCase = true))
        assertFalse(SafeRegex.containsMatchIn("hello", "HELLO WORLD", ignoreCase = false))
    }

    @Test
    fun containsMatchIn_nestedQuantifierReturnsFalse() {
        assertFalse(SafeRegex.containsMatchIn("(a+)+", "aaa"))
    }

    @Test
    fun containsMatchIn_emptyPatternMatchesAnything() {
        assertTrue(SafeRegex.containsMatchIn("", "anything"))
    }

    @Test
    fun containsMatchIn_emptyTargetMatchesEmptyPattern() {
        assertTrue(SafeRegex.containsMatchIn("", ""))
    }

    @Test
    fun containsMatchIn_japanesePatternWorks() {
        assertTrue(SafeRegex.containsMatchIn("テスト", "これはテストです"))
        assertFalse(SafeRegex.containsMatchIn("テスト", "これはサンプルです"))
    }
}
