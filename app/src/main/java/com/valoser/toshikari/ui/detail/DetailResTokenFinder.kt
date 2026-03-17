package com.valoser.toshikari.ui.detail

internal data class DetailResTokenMatch(
    val start: Int,
    val end: Int,
    val number: String
)

/**
 * 表示テキスト内の `No.xxx` トークンを位置付きで抽出する補助。
 * ヘッダー行か引用行に含まれるものだけを対象にする。
 */
internal object DetailResTokenFinder {
    private val resPattern = Regex("""(?i)[NＮ][oＯｏ][.\uFF0E．]?\s*(\d+)""")

    fun findMatches(text: String): List<DetailResTokenMatch> {
        return resPattern.findAll(text).mapNotNull { match ->
            val matchStart = match.range.first
            val lineStart = text.lastIndexOf('\n', matchStart).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', matchStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val lineIndex = text.substring(0, lineStart).count { it == '\n' }
            val isQuoteLine = DetailTextNormalizer.normalizeTrimmed(line).startsWith(">")

            if (!DetailHeaderLineRules.isHeaderLine(line, lineIndex) && !isQuoteLine) {
                return@mapNotNull null
            }

            DetailResTokenMatch(
                start = match.range.first,
                end = match.range.last + 1,
                number = match.groupValues[1]
            )
        }.toList()
    }
}
