package com.valoser.toshikari

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailHtmlParsingSupportTest {
    @Test
    fun extractThreadIdAndResNum_followThreadParsingRules() {
        val threadId = DetailHtmlParsingSupport.extractThreadId("https://may.2chan.net/b/res/123456.htm")
        val replyHtml = "abc<br>No.789012<br>def"

        assertEquals("123456", threadId)
        assertEquals("123456", DetailHtmlParsingSupport.extractResNum(replyHtml, isOp = true, threadId = threadId))
        assertEquals("789012", DetailHtmlParsingSupport.extractResNum(replyHtml, isOp = false, threadId = threadId))
        assertEquals(
            "text_789012",
            DetailHtmlParsingSupport.buildTextContentId(isOp = false, threadId = threadId, resNum = "789012", index = 2)
        )
    }

    @Test
    fun buildTextContentId_usesFallbackWhenReplyHasNoResNum() {
        val id = DetailHtmlParsingSupport.buildTextContentId(
            isOp = false,
            threadId = "123456",
            resNum = null,
            index = 4
        )

        assertEquals("text_reply_123456_4", id)
    }

    @Test
    fun isMediaUrl_acceptsQueriesAndRejectsNonMedia() {
        assertTrue(DetailHtmlParsingSupport.isMediaUrl("/src/12345.JPG?foo=1"))
        assertTrue(DetailHtmlParsingSupport.isMediaUrl("https://example.com/a.webm#hash"))
        assertFalse(DetailHtmlParsingSupport.isMediaUrl("/img/12345.html"))
    }

    @Test
    fun buildMediaContent_createsImageAndVideoEntries() {
        val image = DetailHtmlParsingSupport.buildMediaContent(
            absoluteUrl = "https://example.com/src/12345.jpg",
            rawHref = "/src/12345.jpg",
            thumbnailUrl = "https://example.com/thumb/12345s.jpg"
        )
        val video = DetailHtmlParsingSupport.buildMediaContent(
            absoluteUrl = "https://example.com/src/999.webm",
            rawHref = "/src/999.webm"
        )

        assertEquals(
            DetailContent.Image(
                id = "image_${"https://example.com/src/12345.jpg".hashCode().toUInt().toString(16)}",
                imageUrl = "https://example.com/src/12345.jpg",
                prompt = null,
                fileName = "12345.jpg",
                thumbnailUrl = "https://example.com/thumb/12345s.jpg"
            ),
            image
        )
        assertEquals(
            DetailContent.Video(
                id = "video_${"https://example.com/src/999.webm".hashCode().toUInt().toString(16)}",
                videoUrl = "https://example.com/src/999.webm",
                prompt = null,
                fileName = "999.webm",
                thumbnailUrl = null
            ),
            video
        )
        assertNull(
            DetailHtmlParsingSupport.buildMediaContent(
                absoluteUrl = "https://example.com/file.txt",
                rawHref = "/file.txt"
            )
        )
    }

    @Test
    fun resolveThumbnailUrl_prefersEmbeddedThumbnailAttributes() {
        val link = Jsoup.parseBodyFragment(
            """<a target="_blank" href="/src/12345.jpg"><img data-src="/thumb/12345s.jpg"></a>"""
        ).selectFirst("a")!!

        val thumbnail = DetailHtmlParsingSupport.resolveThumbnailUrl(
            linkNode = link,
            documentUrl = "https://example.com/b/res/123.htm",
            fullImageUrl = "https://example.com/src/12345.jpg"
        )

        assertEquals("https://example.com/thumb/12345s.jpg", thumbnail)
    }

    @Test
    fun resolveThumbnailUrl_fallsBackToGuessedPath() {
        val link = Jsoup.parseBodyFragment(
            """<a target="_blank" href="/src/55555.png">image</a>"""
        ).selectFirst("a")!!

        val thumbnail = DetailHtmlParsingSupport.resolveThumbnailUrl(
            linkNode = link,
            documentUrl = "https://example.com/b/res/123.htm",
            fullImageUrl = "https://example.com/src/55555.png?foo=1"
        )

        assertEquals("https://example.com/thumb/55555s.jpg", thumbnail)
    }

    @Test
    fun extractThreadEndTime_readsDocumentWriteHtmlAndBuildsStableContent() {
        val document = Jsoup.parse(
            """
            <html><body>
            <script>
            document.write('<span id="contdisp">25/01/31(金)23:59</span>');
            </script>
            </body></html>
            """.trimIndent()
        )

        val endTime = DetailHtmlParsingSupport.extractThreadEndTime(document)

        assertEquals("25/01/31(金)23:59", endTime)
        assertEquals(
            DetailContent.ThreadEndTime(
                id = "thread_end_time_${"25/01/31(金)23:59".hashCode().toUInt().toString(16)}",
                endTime = "25/01/31(金)23:59"
            ),
            DetailHtmlParsingSupport.buildThreadEndTimeContent(requireNotNull(endTime))
        )
    }
}
