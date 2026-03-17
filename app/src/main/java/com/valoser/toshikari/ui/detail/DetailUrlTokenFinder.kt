package com.valoser.toshikari.ui.detail

import android.util.Patterns

internal data class DetailUrlTokenMatch(
    override val start: Int,
    override val end: Int,
    val url: String
) : DetailTokenMatch

/**
 * 表示テキスト内の URL トークンを位置付きで抽出する補助。
 */
internal object DetailUrlTokenFinder : DetailTokenFinder<DetailUrlTokenMatch> {
    override fun findMatches(text: String): List<DetailUrlTokenMatch> {
        return findMatches(text, Patterns.WEB_URL.toRegex())
    }

    fun findMatches(text: String, pattern: Regex): List<DetailUrlTokenMatch> {
        return pattern.findAll(text).map { match ->
            DetailUrlTokenMatch(
                start = match.range.first,
                end = match.range.last + 1,
                url = match.value
            )
        }.toList()
    }
}
