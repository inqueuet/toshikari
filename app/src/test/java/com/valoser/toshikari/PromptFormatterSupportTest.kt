package com.valoser.toshikari

import org.junit.Assert.*
import org.junit.Test

class PromptFormatterSupportTest {

    // ====== normalizeWeight ======

    @Test
    fun `括弧付き重み表記を正規化する`() {
        assertEquals("tag (×1.2)", PromptFormatterSupport.normalizeWeight("(tag: 1.2)"))
    }

    @Test
    fun `括弧なし重み表記を正規化する`() {
        assertEquals("tag (×0.8)", PromptFormatterSupport.normalizeWeight("tag: 0.8"))
    }

    @Test
    fun `山括弧タグはそのまま返す`() {
        assertEquals("<lora:test:0.5>", PromptFormatterSupport.normalizeWeight("<lora:test:0.5>"))
    }

    @Test
    fun `重みなしのタグはそのまま返す`() {
        assertEquals("simple tag", PromptFormatterSupport.normalizeWeight("simple tag"))
    }

    @Test
    fun `空白のみのタグはトリムされる`() {
        assertEquals("tag", PromptFormatterSupport.normalizeWeight("  tag  "))
    }

    // ====== splitTags ======

    @Test
    fun `カンマで分割する`() {
        val result = PromptFormatterSupport.splitTags("tag1, tag2, tag3")
        assertEquals(listOf("tag1", "tag2", "tag3"), result)
    }

    @Test
    fun `null は空リストを返す`() {
        assertTrue(PromptFormatterSupport.splitTags(null).isEmpty())
    }

    @Test
    fun `空文字列は空リストを返す`() {
        assertTrue(PromptFormatterSupport.splitTags("").isEmpty())
    }

    @Test
    fun `括弧内のカンマは分割しない`() {
        val result = PromptFormatterSupport.splitTags("(tag1, tag2: 1.2), tag3")
        assertEquals(2, result.size)
        assertEquals("tag3", result[1])
    }

    @Test
    fun `山括弧内のカンマは分割しない`() {
        val result = PromptFormatterSupport.splitTags("<lora:test,0.5>, tag1")
        assertEquals(2, result.size)
        assertTrue(result[0].contains("lora"))
    }

    @Test
    fun `エスケープカンマは分割しない`() {
        val result = PromptFormatterSupport.splitTags("tag1\\, still tag1, tag2")
        assertEquals(2, result.size)
        assertTrue(result[0].contains("tag1"))
    }

    @Test
    fun `重み付きタグを正規化して返す`() {
        val result = PromptFormatterSupport.splitTags("(beautiful: 1.3), simple")
        assertEquals(2, result.size)
        assertEquals("beautiful (×1.3)", result[0])
        assertEquals("simple", result[1])
    }

    // ====== stripSettingsLines ======

    @Test
    fun `null は空文字を返す`() {
        assertEquals("", PromptFormatterSupport.stripSettingsLines(null))
    }

    @Test
    fun `設定行を除去する`() {
        val input = "tag1, tag2\nSteps: 20\nSampler: Euler"
        val result = PromptFormatterSupport.stripSettingsLines(input)
        assertTrue(result.contains("tag1"))
        assertFalse(result.contains("Steps"))
        assertFalse(result.contains("Sampler"))
    }

    @Test
    fun `設定行がない場合はそのまま返す`() {
        val input = "tag1, tag2, tag3"
        assertEquals(input, PromptFormatterSupport.stripSettingsLines(input))
    }

    @Test
    fun `小文字で始まる行は残す`() {
        val input = "masterpiece, best quality\nSteps: 30"
        val result = PromptFormatterSupport.stripSettingsLines(input)
        assertTrue(result.contains("masterpiece"))
    }
}
