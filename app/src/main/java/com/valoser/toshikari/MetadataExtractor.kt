package com.valoser.toshikari

import android.content.Context
import android.net.Uri
import android.util.Log
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import com.valoser.toshikari.metadata.ExifPromptExtractor
import com.valoser.toshikari.metadata.JpegSegmentExtractor
import com.valoser.toshikari.metadata.MetadataByteUtils
import com.valoser.toshikari.metadata.MetadataByteUtils.containsChunkType
import com.valoser.toshikari.metadata.MetadataByteUtils.readBytesLimited
import com.valoser.toshikari.metadata.PngPromptExtractor
import com.valoser.toshikari.metadata.PromptTextScanner

/**
 * 画像からプロンプト/説明テキストを抽出するユーティリティ。
 *
 * 公開 API・キャッシュ・同時接続制御・ルーティングのみを担い、
 * フォーマット別の解析ロジックは `metadata` パッケージの各 Extractor に委譲する。
 *
 * - 入力: ローカルURI/ファイル、HTTP(S) URL をサポート（HTTP は `NetworkClient` 経由）。
 * - 取得戦略: セマフォで同時接続数を制限しつつ、Range GET で必要最小限のバイトのみ取得。
 * - 非対応: 動画は解析対象外（常に null を返す）。
 * - 戦略: 失敗時は null を返す（例外を投げない）。
 */
object MetadataExtractor {
    private const val TAG = "MetadataExtractor"

    // ====== 同時接続数制限設定（ユーザー設定で可変） ======
    @Volatile
    private var currentPermits: Int = 1
    private val connectionSemaphoreRef = java.util.concurrent.atomic.AtomicReference(Semaphore(currentPermits))
    private val connectionSemaphore: Semaphore get() = connectionSemaphoreRef.get()
    private fun permitsForLevel(level: Int): Int = minOf(level, 3)
    @Synchronized
    private fun ensureSemaphore(context: Context) {
        val level = AppPreferences.getConcurrencyLevel(context)
        val desired = permitsForLevel(level)
        if (desired != currentPermits) {
            currentPermits = desired
            connectionSemaphoreRef.set(Semaphore(desired))
        }
    }
    private val activeConnectionCount = AtomicInteger(0)

    // ====== 定数 ======
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    private const val FIRST_EXIF_BYTES = 128 * 1024
    private const val PNG_WINDOW_BYTES = 256 * 1024
    private const val GLOBAL_MAX_BYTES = 1024 * 1024

    // ===== 結果キャッシュ（陽性のみ保存） =====
    private const val CACHE_MAX = 128
    private val resultCache: java.util.LinkedHashMap<String, String> = object : java.util.LinkedHashMap<String, String>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > CACHE_MAX
    }
    @Synchronized private fun cacheGet(key: String): String? = resultCache[key]
    @Synchronized private fun cachePut(key: String, value: String) { resultCache[key] = value }

    // ====== ScreenShot判定 ======
    private fun isScreenShotFile(uriOrUrl: String): Boolean {
        return uriOrUrl.lowercase().contains("screenshot")
    }

    // ====== Coil キャッシュ統合 ======
    private suspend fun extractFromCoilCacheIfAvailable(
        context: Context,
        imageUrl: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val imageLoader = coil3.SingletonImageLoader.get(context)
            val diskCache = imageLoader.diskCache ?: return@withContext null
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()

            val candidateKeys = buildList {
                add(imageUrl)
                request.diskCacheKey?.let { add(it) }
            }.distinct()

            for (key in candidateKeys) {
                val snapshot = diskCache.openSnapshot(key) ?: continue
                snapshot.use {
                    val cachedFile = snapshot.data.toFile()

                    if (!cachedFile.exists() || !cachedFile.canRead()) {
                        Log.d(TAG, "Coil cache file not accessible: $imageUrl (key=$key)")
                        return@use null
                    }

                    val fileSize = cachedFile.length()
                    Log.d(TAG, "Coil cache hit (${fileSize / 1024}KB): $imageUrl (key=$key)")

                    return@withContext if (fileSize > GLOBAL_MAX_BYTES) {
                        cachedFile.inputStream().use { input ->
                            val bytes = input.readBytesLimited(GLOBAL_MAX_BYTES)
                            extractBySniff(bytes, imageUrl)
                        }
                    } else {
                        val bytes = cachedFile.readBytes()
                        extractBySniff(bytes, imageUrl)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract metadata from Coil cache: $imageUrl", e)
            null
        }
    }

    // ====== Public API ======
    /**
     * URI/URL からプロンプトらしき文字列を抽出して返す。見つからなければ null。
     */
    suspend fun extract(context: Context, uriOrUrl: String, networkClient: NetworkClient): String? = withContext(Dispatchers.IO) {
        try {
            if (!PromptSettings.isPromptFetchEnabled(context)) {
                Log.d(TAG, "Prompt extraction disabled. Skipping for $uriOrUrl")
                return@withContext null
            }

            if (isScreenShotFile(uriOrUrl)) {
                Log.d(TAG, "Skipping prompt extraction for screenshot file: $uriOrUrl")
                return@withContext null
            }

            // 1) in-memory LRU
            cacheGet(uriOrUrl)?.let { return@withContext it }
            // 2) persistent cache
            val metadataCache = MetadataCacheEntryPoint.resolve(context)
            runCatching { metadataCache.get(uriOrUrl) }.getOrNull()?.let { cached ->
                cachePut(uriOrUrl, cached)
                return@withContext cached
            }
            // 3) Coilディスクキャッシュをチェック（HTTP(S)のみ）
            if (uriOrUrl.startsWith("http://") || uriOrUrl.startsWith("https://")) {
                extractFromCoilCacheIfAvailable(context, uriOrUrl)?.let { result ->
                    Log.d(TAG, "Metadata extracted from Coil disk cache (no network): $uriOrUrl")
                    cachePut(uriOrUrl, result)
                    runCatching { metadataCache.put(uriOrUrl, result) }
                    return@withContext result
                }
            }
            // 4) ローカル読み込み/ネットワーク取得
            ensureSemaphore(context)
            val result: String? = if (uriOrUrl.startsWith("content://") || uriOrUrl.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(uriOrUrl))?.use { input ->
                    val all = input.readBytesLimited(GLOBAL_MAX_BYTES)
                    extractBySniff(all, uriOrUrl)
                }
            } else {
                val ext = uriOrUrl.substringAfterLast('.', "").lowercase()
                when (ext) {
                    "jpg", "jpeg", "webp" -> {
                        val head = httpGetRangeWithLimit(context, uriOrUrl, 0, FIRST_EXIF_BYTES.toLong(), networkClient)
                        if (head != null) {
                            ExifPromptExtractor.extractFromExif(head)
                                ?: JpegSegmentExtractor.extractFromJpegAppSegments(head)
                                ?: extractBySniff(head, uriOrUrl)
                        } else null
                    }
                    "png" -> extractPngPromptStreamingWithLimit(context, uriOrUrl, networkClient)
                    "mp4", "mov", "m4v", "webm" -> null
                    else -> {
                        val head = httpGetRangeWithLimit(context, uriOrUrl, 0, FIRST_EXIF_BYTES.toLong(), networkClient)
                        if (head != null) extractBySniff(head, uriOrUrl) else null
                    }
                }
            }
            if (!result.isNullOrBlank()) {
                if (result.trim() == "UNICODE") {
                    Log.w(TAG, "Extracted metadata is just 'UNICODE' marker, treating as null: $uriOrUrl")
                    return@withContext null
                }
                cachePut(uriOrUrl, result)
                runCatching { metadataCache.put(uriOrUrl, result) }
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    // ====== 同時接続数制限付きHTTPメソッド ======

    private suspend fun httpGetRangeWithLimit(context: Context, urlStr: String, start: Long, length: Long, networkClient: NetworkClient): ByteArray? {
        ensureSemaphore(context)
        return connectionSemaphore.withPermit {
            val connectionCount = activeConnectionCount.incrementAndGet()
            try {
                Log.d(TAG, "activeConnections=$connectionCount")
                httpGetRange(urlStr, start, length, networkClient)
            } finally {
                activeConnectionCount.decrementAndGet()
            }
        }
    }

    private suspend fun httpHeadWithLimit(context: Context, urlStr: String, networkClient: NetworkClient): HeadInfo? {
        ensureSemaphore(context)
        return connectionSemaphore.withPermit {
            val connectionCount = activeConnectionCount.incrementAndGet()
            try {
                Log.d(TAG, "activeConnections=$connectionCount")
                httpHead(urlStr, networkClient)
            } finally {
                activeConnectionCount.decrementAndGet()
            }
        }
    }

    // ====== PNG: 同時接続数制限付きストリーミング処理 ======
    private suspend fun extractPngPromptStreamingWithLimit(context: Context, fileUrl: String, networkClient: NetworkClient): String? {
        var windowSize = PNG_WINDOW_BYTES
        var totalFetched = 0
        val buf = java.io.ByteArrayOutputStream()

        val first = httpGetRangeWithLimit(context, fileUrl, 0, windowSize.toLong(), networkClient) ?: return null
        buf.write(first)
        totalFetched += first.size

        var bytes = buf.toByteArray()
        if (!MetadataByteUtils.isPng(bytes)) return null
        PngPromptExtractor.extractFromPngChunks(bytes)?.let { return it }

        while (totalFetched < GLOBAL_MAX_BYTES) {
            val offset = bytes.size.toLong()
            windowSize = kotlin.math.min(PNG_WINDOW_BYTES, GLOBAL_MAX_BYTES - totalFetched)
            if (windowSize <= 0) break

            val more = httpGetRangeWithLimit(context, fileUrl, offset, windowSize.toLong(), networkClient) ?: break
            buf.write(more)
            totalFetched += more.size
            bytes = buf.toByteArray()
            PngPromptExtractor.extractFromPngChunks(bytes)?.let { return it }

            if (bytes.containsChunkType("IEND")) break
        }
        return null
    }

    // ====== 接続管理用のユーティリティ関数 ======
    fun getActiveConnectionCount(): Int = activeConnectionCount.get()
    fun getMaxConcurrentConnections(): Int = currentPermits

    // ====== HTTPヘルパー ======
    private data class HeadInfo(val contentLength: Long?, val acceptRanges: Boolean)

    private suspend fun httpHead(urlStr: String, networkClient: NetworkClient): HeadInfo? {
        return try {
            val len = networkClient.headContentLength(urlStr)
            HeadInfo(len, true)
        } catch (_: Exception) { null }
    }

    private suspend fun httpGetRange(urlStr: String, start: Long, length: Long, networkClient: NetworkClient): ByteArray? {
        return try { networkClient.fetchRange(urlStr, start, length) } catch (_: Exception) { null }
    }

    // ====== ルーティング ======

    /**
     * バイト先頭を嗅ぎ分けて EXIF / JPEG セグメント / テキストを順に試すフォールバック。
     */
    private fun extractBySniff(bytes: ByteArray, @Suppress("UNUSED_PARAMETER") uriOrUrl: String): String? {
        if (MetadataByteUtils.isPng(bytes)) {
            return PngPromptExtractor.extractFromPngChunks(bytes)
        }
        ExifPromptExtractor.extractFromExif(bytes)?.let { return it }
        JpegSegmentExtractor.extractFromJpegAppSegments(bytes)?.let { return it }
        return PromptTextScanner.scanTextForPrompts(String(bytes, StandardCharsets.UTF_8))
    }
}
