package com.valoser.toshikari

import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WebView の [CookieManager] からの Cookie 取得を安全に行うヘルパー。
 *
 * [CookieManager.getInstance] はメインスレッドの Looper を必要とするため、
 * このヘルパーは `Dispatchers.Main` へ切り替えたうえでタイムアウト付きでアクセスする。
 *
 * 低スペック端末やバックグラウンド移行中でも操作が完了しない場合に備え、
 * デフォルト 3 秒のタイムアウトを設けている。
 */
internal object WebViewCookieHelper {

    private const val TAG = "WebViewCookieHelper"

    /** デフォルトのタイムアウト（ミリ秒）。 */
    const val DEFAULT_TIMEOUT_MS = 3_000L

    /** 削除系など低優先度操作向けの短いタイムアウト。 */
    const val SHORT_TIMEOUT_MS = 1_500L

    /**
     * 指定 URL の Cookie 文字列を取得する。
     *
     * @param url Cookie を取得する URL
     * @param timeoutMs タイムアウト（ミリ秒）
     * @return Cookie 文字列。取得失敗・タイムアウト時は `null`
     */
    suspend fun getCookie(url: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) {
                try {
                    CookieManager.getInstance().getCookie(url)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get WebView cookie for $url", e)
                    null
                }
            }
        }
    }

    /**
     * 複数 URL を順に試して最初に取得できた Cookie 文字列を返す。
     *
     * @param urls 試行する URL のリスト（先頭優先）
     * @param timeoutMs 全体のタイムアウト（ミリ秒）
     * @return Cookie 文字列。すべて失敗した場合は `null`
     */
    suspend fun getCookieFirstOf(
        vararg urls: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? {
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) {
                try {
                    val cm = CookieManager.getInstance()
                    for (url in urls) {
                        val cookie = cm.getCookie(url)
                        if (!cookie.isNullOrBlank()) return@withContext cookie
                    }
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get WebView cookies", e)
                    null
                }
            }
        }
    }

    /**
     * 複数 URL それぞれの Cookie を取得して Pair で返す（そうだね / 削除用）。
     *
     * @param refererUrl Referer URL
     * @param originUrl Origin URL
     * @param timeoutMs タイムアウト（ミリ秒）
     * @return (refererCookie, originCookie)。タイムアウト時はどちらも `null`
     */
    suspend fun getCookiePair(
        refererUrl: String,
        originUrl: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Pair<String?, String?> {
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) {
                try {
                    val cm = CookieManager.getInstance()
                    cm.getCookie(refererUrl) to cm.getCookie(originUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get WebView cookies", e)
                    null to null
                }
            }
        } ?: run {
            Log.w(TAG, "WebView cookie fetch timed out (${timeoutMs}ms)")
            null to null
        }
    }
}
