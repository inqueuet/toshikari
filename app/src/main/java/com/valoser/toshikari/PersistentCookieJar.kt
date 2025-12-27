package com.valoser.toshikari

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OkHttp 用の永続化対応 CookieJar。
 *
 * - セッション Cookie はメモリにのみ保持（アプリ再起動で消える）。
 * - 永続 Cookie（有効期限付き）は `SharedPreferences` にドメイン別の JSON として保存・復元。
 * - WebView との Cookie 同期も行い、UI スレッドで `CookieManager` に反映します。
 * - スレッドセーフにするため主要メソッドは `@Synchronized` で保護しています。
 * - 利用前に `init(context)` を必ず呼び出してください。
 */
object PersistentCookieJar : CookieJar {

    private const val PREFS_NAME = "CookiePrefs"
    private const val COOKIES_KEY_PREFIX = "cookies_for_domain_"

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    // CopyOnWriteArrayListを使用してスレッドセーフなリストを保持
    private val cookieStore = ConcurrentHashMap<String, CopyOnWriteArrayList<SerializableCookie>>()

    @Volatile
    private var isInitialized = false

    /**
     * SharedPreferences を初期化し、過去の永続 Cookie を読み込みます。
     * 既に初期化済みの場合は何もしません（ダブルチェックロッキング）。
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadCookiesFromPrefs()
            isInitialized = true
            Log.d("PersistentCookieJar", "Initialized and cookies loaded from SharedPreferences.")
        }
    }

    /**
     * 未初期化時に使用されるのを防ぐためのチェック。
     * 初期化されていない場合は例外を投げます。
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("PersistentCookieJar has not been initialized. Call init() first.")
        }
    }

    /**
     * サーバー応答から受け取った Cookie を保存します。
     *
     * - ホスト限定（hostOnly）Cookie はリクエストホストにのみ適用。
     * - 永続 Cookie は有効期限が切れていなければ保存、期限切れは破棄。
     * - セッション Cookie はメモリのみで保持し、Preferences には保存しません。
     * - 保存後、WebView 側の `CookieManager` にも同期します。
     */
    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        ensureInitialized()
        val requestHost = url.host
        val now = System.currentTimeMillis()

        cookies.forEach { cookie ->
            val hostOnly = cookie.hostOnly
            val baseDomain = if (hostOnly) requestHost else cookie.domain
            val effectiveDomain = normalizeDomain(baseDomain.ifEmpty { requestHost })

            val storedCookiesForDomain = cookieStore.getOrPut(effectiveDomain) { CopyOnWriteArrayList() }
            // domain も考慮して同一 Cookie を削除（異なるサブドメインの Cookie を誤削除しない）
            storedCookiesForDomain.removeIf {
                it.name == cookie.name && it.path == cookie.path && it.domain == cookie.domain
            }

            if (cookie.persistent) {
                if (cookie.expiresAt > now) {
                    storedCookiesForDomain.add(SerializableCookie.fromOkHttpCookie(cookie))
                } else {
                    // expired persistent cookie: do not add
                }
            } else {
                // session cookie: keep in memory only
                storedCookiesForDomain.add(SerializableCookie.fromOkHttpCookie(cookie))
            }
        }
        saveCookiesToPrefs()

        // WebView への Cookie 同期（UI スレッドで実行、リトライ機構付き）
        syncCookiesToWebView(url.toString(), cookies)
    }

    /**
     * WebView の CookieManager に Cookie を同期する（コルーチンベースのリトライ機構付き）
     */
    private fun syncCookiesToWebView(url: String, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return

        // グローバルスコープで実行（UIライフサイクルに依存しない）
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val maxRetries = 3
            var retryCount = 0
            var success = false

            while (retryCount < maxRetries && !success) {
                try {
                    val cm = android.webkit.CookieManager.getInstance()
                    cookies.forEach { cookie ->
                        cm.setCookie(url, cookie.toString())
                    }
                    cm.flush()
                    success = true
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount < maxRetries) {
                        Log.w("PersistentCookieJar", "WebView sync failed, retrying ($retryCount/$maxRetries): ${e.message}")
                        kotlinx.coroutines.delay(500L * retryCount)
                    } else {
                        Log.e("PersistentCookieJar", "WebView sync failed after $maxRetries retries", e)
                    }
                }
            }
        }
    }

    /**
     * リクエストに付与すべき Cookie を返します。
     *
     * - 期限切れの永続 Cookie はこのタイミングで破棄し、Preferences を更新。
     * - ドメインとパスのマッチング、`secure` 属性、hostOnly を考慮します。
     */
    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureInitialized()
        val requestHost = url.host
        val matchingCookies = mutableListOf<Cookie>()
        val now = System.currentTimeMillis()

        var removedAnyPersistent = false

        cookieStore.forEach { (cookieDomain, storedCookiesList) ->
            // CopyOnWriteArrayListはiterator.remove()をサポートしないため、removeIf()を使用
            val removedCount = storedCookiesList.removeIf { sc ->
                sc.persistent && sc.expiresAt <= now
            }
            if (removedCount) {
                removedAnyPersistent = true
            }

            // Domain and path checks per cookie
            storedCookiesList.forEach { sc ->
                if (domainMatches(sc.domain, sc.hostOnly, requestHost) && pathMatches(sc.path, url.encodedPath)) {
                    if (sc.secure && !url.isHttps) {
                        // skip secure cookie over http
                    } else {
                        matchingCookies.add(sc.toOkHttpCookie())
                    }
                }
            }
        }
        if (removedAnyPersistent) {
            saveCookiesToPrefs()
        }
        return matchingCookies
    }

    /**
     * すべての Cookie（メモリと Preferences）を削除します。
     */
    @Synchronized
    fun clearAllCookies() {
        ensureInitialized()
        cookieStore.clear()
        sharedPreferences.edit().clear().apply()
        Log.d("PersistentCookieJar", "All cookies cleared from memory and SharedPreferences.")
    }

    /**
     * 指定ホストに関連する Cookie を削除します。
     *
     * - 指定ホストと完全一致するドメイン
     * - 指定ホストが属する上位ドメイン
     * に対応する保存領域を全て削除します。
     */
    @Synchronized
    fun clearCookiesForHost(host: String) {
        ensureInitialized()
        val domainsToRemove = mutableListOf<String>()
        val normalizedHost = normalizeDomain(host)
        cookieStore.keys.forEach { domain ->
            val d = normalizeDomain(domain)
            if (normalizedHost == d || normalizedHost.endsWith(".$d")) {
                domainsToRemove.add(domain)
            }
        }
        if (domainsToRemove.isNotEmpty()) {
            // バッチ処理で SharedPreferences を更新（競合状態を回避）
            val editor = sharedPreferences.edit()
            domainsToRemove.forEach { domain ->
                cookieStore.remove(domain)
                editor.remove(COOKIES_KEY_PREFIX + domain)
                Log.d("PersistentCookieJar", "Cleared cookies for domain pattern: $domain (related to host $host)")
            }
            editor.apply()
        }
    }

    /**
     * メモリ上の永続 Cookie を `SharedPreferences` に保存します。
     * 永続 Cookie が存在しないドメインのキーは削除します。
     */
    private fun saveCookiesToPrefs() {
        val editor = sharedPreferences.edit()
        // remove keys which are no longer present or have no persistent cookies
        val currentPrefKeys = sharedPreferences.all.keys.filter { it.startsWith(COOKIES_KEY_PREFIX) }
        val domainsWithPersistent = cookieStore.filterValues { list -> list.any { it.persistent } }.keys
        val storeDomainsForPrefs = domainsWithPersistent.map { COOKIES_KEY_PREFIX + it }
        currentPrefKeys.forEach { prefKey ->
            if (prefKey !in storeDomainsForPrefs) {
                editor.remove(prefKey)
            }
        }

        // write only persistent cookies
        domainsWithPersistent.forEach { domain ->
            val persistentOnly = cookieStore[domain]?.filter { it.persistent } ?: emptyList()
            val jsonCookies = gson.toJson(persistentOnly)
            editor.putString(COOKIES_KEY_PREFIX + domain, jsonCookies)
        }
        editor.apply()
    }

    /**
     * `SharedPreferences` から永続 Cookie を読み込み、期限切れは取り除きます。
     * 初期化時の競合を防ぐため同期化。
     */
    @Synchronized
    private fun loadCookiesFromPrefs() {
        cookieStore.clear()
        val keysToRemove = mutableListOf<String>()

        sharedPreferences.all.forEach { (key, value) ->
            if (key.startsWith(COOKIES_KEY_PREFIX) && value is String) {
                try {
                    val domain = key.removePrefix(COOKIES_KEY_PREFIX)
                    val typeToken = object : TypeToken<MutableList<SerializableCookie>>() {}.type
                    val cookiesList: MutableList<SerializableCookie>? = gson.fromJson(value, typeToken)
                    if (cookiesList != null) {
                        val now = System.currentTimeMillis()
                        // prefs should contain only persistent cookies; filter any expired ones just in case
                        val validCookies = cookiesList.filter { it.persistent && it.expiresAt > now }
                        if (validCookies.isNotEmpty()) {
                            cookieStore[domain] = CopyOnWriteArrayList(validCookies)
                        } else if (cookiesList.isNotEmpty()) {
                            // all expired; mark for removal
                            keysToRemove.add(key)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PersistentCookieJar", "Error deserializing cookies for key $key from SharedPreferences", e)
                    keysToRemove.add(key)
                }
            }
        }

        // バッチ処理で期限切れ/不正なキーを削除（競合状態を回避）
        if (keysToRemove.isNotEmpty()) {
            val editor = sharedPreferences.edit()
            keysToRemove.forEach { key -> editor.remove(key) }
            editor.apply()
        }
    }

    /**
     * Cookie のドメインがリクエストホストに適合するかを判定します。
     * hostOnly の場合は完全一致、それ以外はサブドメインも許可します。
     */
    private fun domainMatches(cookieDomain: String, hostOnly: Boolean, requestHost: String): Boolean {
        val cd = normalizeDomain(cookieDomain)
        val rh = normalizeDomain(requestHost)
        if (hostOnly) return cd == rh
        return rh == cd || rh.endsWith(".$cd")
    }
    
    /**
     * 先頭のドットを除去してドメイン表記を正規化します。
     */
    private fun normalizeDomain(domain: String): String {
        return domain.trimStart('.')
    }

    /**
     * Cookie のパス属性がリクエストパスに適合するかを判定します（RFC6265準拠）。
     */
    private fun pathMatches(cookiePath: String, requestPath: String): Boolean {
        // RFC6265 Section 5.1.4 に従った実装
        if (cookiePath == requestPath) return true
        if (requestPath.startsWith(cookiePath)) {
            if (cookiePath.endsWith("/")) return true
            val nextCharIndex = cookiePath.length
            if (nextCharIndex < requestPath.length && requestPath[nextCharIndex] == '/') return true
        }
        return false
    }

    /**
     * `SharedPreferences` 保存用にシリアライズ可能な Cookie 表現。
     * OkHttp の `Cookie` と相互変換します。
     */
    private data class SerializableCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val persistent: Boolean,
        val hostOnly: Boolean
    ) {
        /** OkHttp の `Cookie` に復元します。 */
        fun toOkHttpCookie(): Cookie {
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path)

            if (httpOnly) builder.httpOnly()
            if (secure) builder.secure()
            // Restore hostOnly vs domain cookie
            if (hostOnly) builder.hostOnlyDomain(this.domain) else builder.domain(this.domain)

            return builder.build()
        }

        companion object {
            /** OkHttp の `Cookie` からシリアライズ用の表現に変換します。 */
            fun fromOkHttpCookie(cookie: Cookie): SerializableCookie {
                return SerializableCookie(
                    name = cookie.name,
                    value = cookie.value,
                    expiresAt = cookie.expiresAt,
                    domain = cookie.domain,
                    path = cookie.path,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    persistent = cookie.persistent,
                    hostOnly = cookie.hostOnly
                )
            }
        }
    }
}
