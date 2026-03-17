package com.valoser.toshikari.ui.detail

internal data class DetailSodaneTokenMatch(
    override val start: Int,
    override val end: Int
) : DetailTokenMatch

/**
 * ヘッダー行内の「そうだね」トークン位置を抽出する補助。
 */
internal object DetailSodaneTokenFinder : DetailTokenFinder<DetailSodaneTokenMatch> {
    private val sodaneTokenPattern = Regex("""(?:そうだねx\d+|そうだね|[+＋])""")
    private val noPattern = Regex("""(?i)No[.\uFF0E]?\s*(\n?\s*)?(\d+)""")
    private val idPattern = Regex("""ID[:：]""")

    override fun findMatches(text: String): List<DetailSodaneTokenMatch> {
        val matches = mutableListOf<DetailSodaneTokenMatch>()
        var start = 0
        var lineIndex = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val line = text.substring(start, end)
            val trimmed = line.trimStart()
            val isQuote = trimmed.startsWith(">")

            if (!isQuote && DetailHeaderLineRules.isHeaderLine(line, lineIndex)) {
                noPattern.findAll(line).forEach { noMatch ->
                    val afterNoStart = noMatch.range.last + 1
                    if (afterNoStart < line.length) {
                        val afterNo = line.substring(afterNoStart)
                        val searchEnd = idPattern.find(afterNo)?.range?.first ?: afterNo.length
                        val searchRange = afterNo.substring(0, searchEnd)
                        sodaneTokenPattern.find(searchRange)?.let { sodaneMatch ->
                            matches += DetailSodaneTokenMatch(
                                start = start + afterNoStart + sodaneMatch.range.first,
                                end = start + afterNoStart + sodaneMatch.range.last + 1
                            )
                        }
                    }
                }
            }

            if (newline < 0) break
            start = newline + 1
            lineIndex++
        }
        return matches
    }
}
