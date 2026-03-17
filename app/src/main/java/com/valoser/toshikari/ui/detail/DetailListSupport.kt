/**
 * DetailListCompose から抽出した純粋ロジック系ヘルパー関数群。
 * UI に依存しない補助処理をまとめ、DetailList.kt の見通しを改善する。
 */
package com.valoser.toshikari.ui.detail

import androidx.collection.LruCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.request.transitionFactory
import coil3.transition.CrossfadeTransition
import com.valoser.toshikari.DetailContent
import com.valoser.toshikari.image.ImageKeys

internal object DetailListSupport {

    private val imageRequestCache = LruCache<String, ImageRequest>(2000)
    private val headersCache = LruCache<String, NetworkHeaders>(100)
    private val stringProcessingCache = LruCache<String, String>(100)

    /**
     * Compose の LazyColumn で利用する安定キー。
     * DetailContent の各要素は ViewModel/Worker 層で衝突しない ID を付与しているため、
     * 原則としてそのまま利用する。空文字の場合のみ型＋indexでフォールバック。
     */
    internal fun stableKey(item: DetailContent, index: Int): String {
        val id = item.id
        if (id.isNotBlank()) return id
        return when (item) {
            is DetailContent.Text -> "text_fallback_$index"
            is DetailContent.Image -> "image_fallback_$index"
            is DetailContent.Video -> "video_fallback_$index"
            is DetailContent.ThreadEndTime -> "thread_end_fallback_$index"
        }
    }

    /**
     * リスト内の index に対応する投稿の序数（1-based）を返す。
     * Text 要素だけをカウントし、先頭から index までに何番目の Text があるかを返す。
     */
    internal fun ordinalForIndex(all: List<DetailContent>, index: Int): Int {
        var ord = 0
        var i = 0
        while (i <= index && i < all.size) {
            if (all[i] is DetailContent.Text) ord++
            i++
        }
        return ord
    }

    /**
     * ヘッダ風の行（No./ID 行など）を除いた「本文のみ」のプレーンテキストを抽出する。
     */
    internal fun extractBodyOnlyPlain(plain: String): String {
        return DetailBodyTextExtractor.extract(plain)
    }

    /**
     * プレーンテキスト上で詰まりやすいトークン（ID／No／+／そうだね）の間に空白を補正し、
     * ヘッダー行かつ非引用行で No. を含む場合に行末へ そうだね トークンが無ければ付与する。
     */
    internal fun padTokensForSpacingCached(src: String): String {
        return stringProcessingCache.get(src) ?: run {
            val result = DetailSodaneTextFormatter.padTokensForSpacing(src)
            stringProcessingCache.put(src, result)
            result
        }
    }

    /**
     * 「そうだね」の楽観表示を適用してテキストを上書きする。
     */
    internal fun applySodaneDisplay(text: String, overrides: Map<String, Int>, selfResNum: String?): String {
        return DetailSodaneTextFormatter.applySodaneDisplay(text, overrides, selfResNum)
    }

    /**
     * Text + (Image|Video)* のブロックの区切りかを判定する。
     * 次要素が Text/ThreadEndTime/末尾 の場合に区切りとみなし、末尾(null)では線を描かない。
     */
    internal fun isEndOfBlock(items: List<DetailContent>, index: Int): Boolean {
        if (index !in items.indices) return false
        val next = items.getOrNull(index + 1)
        return when (next) {
            null -> false
            is DetailContent.Text, is DetailContent.ThreadEndTime -> true
            is DetailContent.Image, is DetailContent.Video -> false
        }
    }

    /**
     * Referer ヘッダを含む NetworkHeaders を生成（キャッシュ付き）。
     */
    internal fun createHeaders(referer: String): NetworkHeaders {
        return headersCache.get(referer) ?: run {
            val headers = NetworkHeaders.Builder()
                .add("Referer", referer)
                .add("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .add("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                .build()
            headersCache.put(referer, headers)
            headers
        }
    }

    /**
     * 画像読み込み用の ImageRequest を生成（キャッシュ付き）。
     */
    internal fun createImageRequest(
        context: android.content.Context,
        url: String,
        referer: String?,
        forDisplay: Boolean = true
    ): ImageRequest {
        val cacheKey = "$url|$referer|$forDisplay"
        return imageRequestCache.get(cacheKey) ?: run {
            val request = ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(ImageKeys.full(url))
                .diskCacheKey(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .precision(Precision.INEXACT)
                .transitionFactory(CrossfadeTransition.Factory())
                .apply {
                    if (!referer.isNullOrBlank()) {
                        httpHeaders(createHeaders(referer))
                    }
                }
                .build()
            imageRequestCache.put(cacheKey, request)
            request
        }
    }
}
