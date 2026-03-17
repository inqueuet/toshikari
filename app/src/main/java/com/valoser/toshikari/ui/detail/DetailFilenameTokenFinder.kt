package com.valoser.toshikari.ui.detail

internal data class DetailFilenameTokenMatch(
    override val start: Int,
    override val end: Int,
    val fileName: String
) : DetailTokenMatch

/**
 * 表示テキスト内のファイル名トークンを位置付きで抽出する補助。
 */
internal object DetailFilenameTokenFinder : DetailTokenFinder<DetailFilenameTokenMatch> {
    private const val extensionPattern = "(?:jpg|jpeg|png|gif|webp|bmp|mp4|webm|avi|mov|mkv)"
    private val fileNamePattern = Regex("""(?i)([A-Za-z0-9._-]+\.$extensionPattern)""")

    override fun findMatches(text: String): List<DetailFilenameTokenMatch> {
        return fileNamePattern.findAll(text).map { match ->
            DetailFilenameTokenMatch(
                start = match.range.first,
                end = match.range.last + 1,
                fileName = match.groupValues[1]
            )
        }.toList()
    }
}
