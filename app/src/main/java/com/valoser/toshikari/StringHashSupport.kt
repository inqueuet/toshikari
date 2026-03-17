package com.valoser.toshikari

import java.security.MessageDigest
import java.net.URL

/**
 * 文字列ハッシュの共通ユーティリティ。
 * DetailCacheManager / ThreadMonitorWorker などで重複していた sha256 拡張を統一する。
 */
internal object StringHashSupport {

    /**
     * 文字列の SHA-256 ハッシュ（小文字16進）を返す。
     */
    fun sha256(input: String): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }
    }

    /**
     * 文字列の MD5 ハッシュ（小文字16進）を返す。
     * チェックサム用途（暗号強度は不要）。
     */
    fun md5(input: String): String {
        return MessageDigest
            .getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 寸法スケーリング。最長辺が [maxDimension] を超える場合にアスペクト比を保ったまま縮小する。
     */
    fun scaleDimensions(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val longest = maxOf(width, height)
        if (longest <= maxDimension) return width to height
        val scale = maxDimension.toDouble() / longest.toDouble()
        val scaledW = (width * scale).toInt().coerceAtLeast(1)
        val scaledH = (height * scale).toInt().coerceAtLeast(1)
        return scaledW to scaledH
    }

    /**
     * URLからアーカイブ用のメディアファイル名を生成する。
     * SHA-256ハッシュ + 元の拡張子（小文字）。
     */
    fun buildMediaFileName(url: String): String {
        val ext = runCatching {
            URL(url)
                .path
                .substringAfterLast('/')
                .substringAfterLast('.', "")
                .takeIf { it.isNotBlank() }
        }.getOrNull().orEmpty()
        return sha256(url) + if (ext.isNotBlank()) ".${ext.lowercase()}" else ""
    }

    /**
     * 動画の長さ（ms）からサムネイル取得用のフレーム時刻（μs）を計算する。
     * 動画中央付近（半分の位置）を返し、最低1秒を保証する。
     */
    fun calculateFrameTimeUs(durationMs: Long?): Long {
        return when {
            durationMs == null || durationMs <= 0L -> 1_000_000L
            else -> (durationMs * 1000L / 2).coerceAtLeast(1_000_000L)
        }
    }
}
