package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailPromptSanitizerTest {

    // ====== normalize ======

    @Test
    fun normalize_decodesHtmlPrompt() {
        val result = DetailPromptSanitizer.normalize("  &lt;b&gt;hello&lt;/b&gt;  ")

        assertEquals("hello", result)
    }

    @Test
    fun normalize_returnsTrimmedPlainTextWhenNoHtmlNormalizationNeeded() {
        val result = DetailPromptSanitizer.normalize("  plain prompt  ")

        assertEquals("plain prompt", result)
    }

    @Test
    fun normalize_returnsNullForBlankPrompt() {
        val result = DetailPromptSanitizer.normalize("   ")

        assertNull(result)
    }

    @Test
    fun `normalize_nullを渡すとnullを返す`() {
        assertNull(DetailPromptSanitizer.normalize(null))
    }

    @Test
    fun `normalize_空文字列はnullを返す`() {
        assertNull(DetailPromptSanitizer.normalize(""))
    }

    @Test
    fun `normalize_ampエンティティをデコードする`() {
        assertEquals("A&B", DetailPromptSanitizer.normalize("A&amp;B"))
    }

    @Test
    fun `normalize_quotエンティティはHTMLタグと共存時にデコードする`() {
        // &quot; 単体では正規化不要だが、&lt;等と共存する場合はデコードされる
        assertEquals("say \"hi\"", DetailPromptSanitizer.normalize("&lt;p&gt;say &quot;hi&quot;&lt;/p&gt;"))
    }

    @Test
    fun `normalize_数値参照をデコードする`() {
        // &#65; = 'A', &#x42; = 'B'
        assertEquals("AB", DetailPromptSanitizer.normalize("&#65;&#x42;"))
    }

    @Test
    fun `normalize_ネストされたHTMLタグを除去する`() {
        assertEquals("bold italic", DetailPromptSanitizer.normalize("&lt;div&gt;&lt;b&gt;bold&lt;/b&gt; &lt;i&gt;italic&lt;/i&gt;&lt;/div&gt;"))
    }

    @Test
    fun `normalize_タグのみの入力はnullを返す`() {
        assertNull(DetailPromptSanitizer.normalize("&lt;br&gt;"))
    }

    @Test
    fun `normalize_エンティティなしのプレーンテキストはそのまま返す`() {
        assertEquals("masterpiece, best quality", DetailPromptSanitizer.normalize("masterpiece, best quality"))
    }

    // ====== sanitizeContents ======

    @Test
    fun sanitizeContents_updatesOnlyMediaPrompts() {
        val input = listOf(
            DetailContent.Image(
                id = "image",
                imageUrl = "https://example.com/a.jpg",
                prompt = "&lt;i&gt;image&lt;/i&gt;"
            ),
            DetailContent.Video(
                id = "video",
                videoUrl = "https://example.com/a.mp4",
                prompt = "plain"
            ),
            DetailContent.Text(
                id = "text",
                htmlContent = "body"
            )
        )

        val result = DetailPromptSanitizer.sanitizeContents(input)

        assertEquals("image", (result[0] as DetailContent.Image).prompt)
        assertEquals("plain", (result[1] as DetailContent.Video).prompt)
        assertEquals(input[2], result[2])
    }

    @Test
    fun sanitizeContents_returnsSameListWhenNothingChanges() {
        val input = listOf(
            DetailContent.Image(
                id = "image",
                imageUrl = "https://example.com/a.jpg",
                prompt = "plain"
            )
        )

        val result = DetailPromptSanitizer.sanitizeContents(input)

        assertSame(input, result)
    }

    @Test
    fun `sanitizeContents_空リストはそのまま返す`() {
        val input = emptyList<DetailContent>()
        assertSame(input, DetailPromptSanitizer.sanitizeContents(input))
    }

    @Test
    fun `sanitizeContents_Videoのプロンプトもサニタイズする`() {
        val input = listOf(
            DetailContent.Video(
                id = "v1",
                videoUrl = "https://example.com/v.mp4",
                prompt = "&lt;script&gt;alert(1)&lt;/script&gt;test"
            )
        )
        val result = DetailPromptSanitizer.sanitizeContents(input)
        assertEquals("alert(1)test", (result[0] as DetailContent.Video).prompt)
    }

    @Test
    fun `sanitizeContents_Textコンテンツは変更しない`() {
        val text = DetailContent.Text(id = "t1", htmlContent = "&lt;b&gt;bold&lt;/b&gt;")
        val input = listOf(text)
        val result = DetailPromptSanitizer.sanitizeContents(input)
        assertSame(input, result)
    }
}
