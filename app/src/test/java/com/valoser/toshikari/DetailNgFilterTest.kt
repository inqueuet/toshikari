package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailNgFilterTest {
    @Test
    fun bodyRuleHidesMatchedResponseAndAttachedMedia() {
        val result = DetailNgFilter.filter(
            items = listOf(
                text(id = "1", html = "keep"),
                text(id = "2", html = "hide"),
                image(id = "3"),
                text(id = "4", html = "keep2")
            ),
            rules = listOf(
                NgRule(
                    id = "rule-body",
                    type = RuleType.BODY,
                    pattern = "ng word"
                )
            ),
            idOf = { null },
            bodyOf = {
                when (it.id) {
                    "2" -> "contains ng word"
                    else -> "safe"
                }
            }
        )

        assertEquals(listOf("1", "4"), result.map { it.id })
    }

    @Test
    fun idRuleUsesCaseInsensitiveExactMatch() {
        val result = DetailNgFilter.filter(
            items = listOf(
                text(id = "1", html = "keep"),
                text(id = "2", html = "hide")
            ),
            rules = listOf(
                NgRule(
                    id = "rule-id",
                    type = RuleType.ID,
                    pattern = "abc123"
                )
            ),
            idOf = {
                when (it.id) {
                    "2" -> "ABC123"
                    else -> "zzz"
                }
            },
            bodyOf = { "safe" }
        )

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun invalidRegexDoesNotHideAnything() {
        val result = DetailNgFilter.filter(
            items = listOf(text(id = "1", html = "keep")),
            rules = listOf(
                NgRule(
                    id = "rule-regex",
                    type = RuleType.BODY,
                    pattern = "[invalid",
                    match = MatchType.REGEX
                )
            ),
            idOf = { null },
            bodyOf = { "safe" }
        )

        assertEquals(listOf("1"), result.map { it.id })
    }

    private fun text(id: String, html: String): DetailContent.Text {
        return DetailContent.Text(id = id, htmlContent = html)
    }

    private fun image(id: String): DetailContent.Image {
        return DetailContent.Image(id = id, imageUrl = "https://example.com/$id.jpg")
    }
}
