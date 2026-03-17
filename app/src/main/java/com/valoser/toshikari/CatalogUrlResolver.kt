package com.valoser.toshikari

import android.util.LruCache
import java.net.URL

/**
 * プレビューURLからフル画像URLへの推測・解決、およびサムネイル候補URLの構築を担当。
 * MainViewModelから委譲されるURL解決ロジックを集約する。
 */
internal object CatalogUrlResolver {

    // 正規表現をプリコンパイル
    private val THUMB_PATTERN = Regex("(/thumb/|/cat/|/jun/)")
    private val EXTENSION_PATTERN = Regex("s\\.(jpg|jpeg|png|gif|webp|webm|mp4)$", RegexOption.IGNORE_CASE)
    private val VALID_EXTENSION_PATTERN = Regex("\\.(jpg|jpeg|png|gif|webp|webm|mp4)$", RegexOption.IGNORE_CASE)

    // URL推測結果をキャッシュ（サイズを拡大）
    // nullと未キャッシュを区別するためOptionalラッパーを使用
    internal data class CachedUrl(val url: String?)
    internal val urlGuessCache = LruCache<String, CachedUrl>(500)
    internal val failedUrlCache = LruCache<String, Boolean>(200)

    /**
     * プレビューURLからフル画像URLを推測する。
     * - `/thumb/` または `/cat/` を `/src/` に置換
     * - 末尾の `s.` を通常拡張子（.jpg/.png 等）に置換（webm/mp4 も対象）
     * 失敗時は `null` を返す。
     */
    fun guessFullFromPreview(previewUrl: String): String? {
        // キャッシュから取得（CachedUrlでラップされているのでnullと未キャッシュを区別可能）
        val cached = urlGuessCache.get(previewUrl)
        if (cached != null) {
            return cached.url
        }

        // 既に失敗記録があるなら即座にnullを返す
        if (failedUrlCache.get(previewUrl) == true) return null

        val result = guessFullFromPreviewInternal(previewUrl)
        // 結果をキャッシュ（nullでもCachedUrlとして保存）
        urlGuessCache.put(previewUrl, CachedUrl(result))
        if (result == null) {
            failedUrlCache.put(previewUrl, true)
        }
        return result
    }

    private fun guessFullFromPreviewInternal(previewUrl: String): String? {
        return try {
            var s = previewUrl
                .replace("/thumb/", "/src/")
                .replace("/cat/", "/src/")
                .replace("/jun/", "/src/")

            // 末尾の "s.ext" を通常の拡張子へ（例: 12345s.jpg -> 12345.jpg）
            s = s.replace(EXTENSION_PATTERN, ".$1")

            // 既に正しい拡張子形式ならそのまま、拡張子が無ければ .jpg を仮置き
            s = when {
                s.contains(VALID_EXTENSION_PATTERN) -> s
                else -> "$s.jpg"
            }
            URL(s).toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * サムネイル候補URL（cat/thumb の拡張子違い）を列挙する。
     */
    fun buildCatalogThumbCandidates(detailUrl: String): List<String> {
        val m = Regex("""/res/(\d+)\.htm""").find(detailUrl) ?: return emptyList()
        val id = m.groupValues[1]
        val boardBase = detailUrl.substringBeforeLast("/res/")
        // 優先度順: cat/{id}s.* → cat/{id}.* → thumb/{id}s.*
        return listOf(
            "$boardBase/cat/${id}s.jpg",
            "$boardBase/cat/${id}s.png",
            "$boardBase/cat/${id}s.webp",
            "$boardBase/cat/$id.jpg",
            "$boardBase/cat/$id.png",
            "$boardBase/cat/$id.webp",
            "$boardBase/thumb/${id}s.jpg",
            "$boardBase/thumb/${id}s.png",
            "$boardBase/thumb/${id}s.webp"
        )
    }

    fun isMediaHref(raw: String): Boolean {
        val h = raw.lowercase()
        return h.contains("/src/") ||
                h.endsWith(".png") || h.endsWith(".jpg") || h.endsWith(".jpeg") ||
                h.endsWith(".gif") || h.endsWith(".webp") ||
                h.endsWith(".webm") || h.endsWith(".mp4")
    }
}
