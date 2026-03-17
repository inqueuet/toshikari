package com.valoser.toshikari.ui.detail

internal data class DetailSearchHighlightMatch(
    val start: Int,
    val end: Int
)

/**
 * 表示テキスト内の検索ハイライト範囲を求める補助。
 */
internal object DetailSearchHighlightFinder {
    fun findMatches(text: String, query: String?): List<DetailSearchHighlightMatch> {
        if (query.isNullOrBlank()) return emptyList()

        val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        return pattern.findAll(text).map { match ->
            DetailSearchHighlightMatch(
                start = match.range.first,
                end = match.range.last + 1
            )
        }.toList()
    }
}
