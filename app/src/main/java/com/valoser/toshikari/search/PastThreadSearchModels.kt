package com.valoser.toshikari.search

import java.net.URL

/**
 * 過去スレ検索の対象板を表すモデル。
 * - server: 例) may
 * - board: 例) b
 */
data class PastSearchScope(
    val server: String,
    val board: String,
) {
    val label: String get() = "$server/$board"

    companion object {
        /**
         * カタログ URL から server/board を推定する。
         * Futaba 系の `https://{server}.2chan.net/{board}/futaba.php?...` 形式を想定。
         */
        fun fromCatalogUrl(url: String?): PastSearchScope? = runCatching {
            if (url.isNullOrBlank()) return null
            val parsed = URL(url)
            val hostPrefix = parsed.host.substringBefore(".").ifBlank { return null }
            val board = parsed.path.trim('/').substringBefore('/').ifBlank { return null }
            PastSearchScope(hostPrefix, board)
        }.getOrNull()
    }
}

/** API の1件分の検索結果。 */
data class PastThreadSearchResult(
    val threadId: String? = null,
    val server: String? = null,
    val board: String? = null,
    val title: String? = null,
    val htmlUrl: String = "",
    val thumbUrl: String? = null,
    val createdAt: String? = null,
)

/** API レスポンス全体。 */
data class PastThreadSearchResponse(
    val query: String? = null,
    val filter: String? = null,
    val count: Int = 0,
    val results: List<PastThreadSearchResult> = emptyList(),
)
