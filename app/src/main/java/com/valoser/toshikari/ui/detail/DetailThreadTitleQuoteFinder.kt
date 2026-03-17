package com.valoser.toshikari.ui.detail

internal data class DetailThreadTitleQuoteMatch(
    val start: Int,
    val end: Int,
    val token: String
)

/**
 * 表示テキスト中から、スレタイと一致する行を引用トークンとして扱うための補助。
 */
internal object DetailThreadTitleQuoteFinder {
    fun findMatches(text: String, threadTitle: String?): List<DetailThreadTitleQuoteMatch> {
        if (threadTitle.isNullOrBlank()) return emptyList()

        val needle = DetailTextNormalizer.normalizeCollapsed(threadTitle)
        if (needle.isBlank()) return emptyList()

        val matches = mutableListOf<DetailThreadTitleQuoteMatch>()
        var index = 0
        while (index <= text.length) {
            val newline = text.indexOf('\n', index)
            val end = if (newline < 0) text.length else newline
            val line = text.substring(index, end)
            val trimmed = line.trim()

            if (!trimmed.startsWith('>')) {
                val normalized = DetailTextNormalizer.normalizeCollapsed(line)
                if (normalized.isNotBlank() && normalized == needle) {
                    matches += DetailThreadTitleQuoteMatch(
                        start = index,
                        end = end,
                        token = ">$trimmed"
                    )
                }
            }

            if (newline < 0) break
            index = newline + 1
        }
        return matches
    }
}
