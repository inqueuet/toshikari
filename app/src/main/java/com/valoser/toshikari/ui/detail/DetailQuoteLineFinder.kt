package com.valoser.toshikari.ui.detail

internal data class DetailQuoteLineMatch(
    val start: Int,
    val end: Int,
    val token: String
)

/**
 * 表示テキストから引用行（`>` / `＞`）を位置付きで抽出する補助。
 */
internal object DetailQuoteLineFinder {
    private val quoteLineRegex = Regex("^(?:[\\t \\u3000])*[>＞]+[^\\n]*", RegexOption.MULTILINE)

    fun findMatches(text: String): List<DetailQuoteLineMatch> {
        return quoteLineRegex.findAll(text).map { match ->
            val raw = match.value
            val trimmed = raw.trimStart()
            DetailQuoteLineMatch(
                start = match.range.first + (raw.length - trimmed.length),
                end = match.range.last + 1,
                token = DetailTextNormalizer.normalizeQuoteToken(trimmed)
            )
        }.toList()
    }
}
