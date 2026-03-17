package com.valoser.toshikari.ui.detail

internal data class DetailQuoteTokenInfo(
    val normalizedToken: String,
    val core: String,
    val normalizedCore: String,
)

/**
 * 引用トークンの前処理をまとめる補助。
 */
internal object DetailQuoteTokenSupport {
    private val fileNamePattern =
        Regex("""(?i)^[A-Za-z0-9._-]+\.(jpg|jpeg|png|gif|webp|bmp|mp4|webm|avi|mov|mkv)$""")

    fun parse(token: String): DetailQuoteTokenInfo? {
        val normalizedToken = DetailTextNormalizer.normalizeQuoteToken(token)
        val level = normalizedToken.takeWhile { it == '>' }.length.coerceAtLeast(1)
        val core = normalizedToken.drop(level).trim()
        if (core.isBlank()) return null

        return DetailQuoteTokenInfo(
            normalizedToken = normalizedToken,
            core = core,
            normalizedCore = DetailTextNormalizer.normalizeCollapsed(core)
        )
    }

    fun isFilenameToken(token: String): Boolean {
        val core = parse(token)?.core ?: return false
        return fileNamePattern.matches(core)
    }
}
