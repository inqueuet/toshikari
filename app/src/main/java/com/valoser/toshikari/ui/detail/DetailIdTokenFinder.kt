package com.valoser.toshikari.ui.detail

internal data class DetailIdTokenMatch(
    val start: Int,
    val end: Int,
    val id: String
)

/**
 * 表示テキスト内の `ID:...` トークンを位置付きで抽出する補助。
 */
internal object DetailIdTokenFinder {
    private val idPattern = Regex("""ID([:：])([\u0021-\u007E\u00A0-\u00FF\w./+]+)""")

    fun findMatches(text: String): List<DetailIdTokenMatch> {
        return idPattern.findAll(text).mapNotNull { match ->
            val id = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DetailIdTokenMatch(
                start = match.range.first,
                end = match.range.last + 1,
                id = id
            )
        }.toList()
    }
}
