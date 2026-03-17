package com.valoser.toshikari

/**
 * NetworkClient の Cookie 関連ユーティリティ関数を集約したオブジェクト。
 * Android 依存を持たないため JUnit でテスト可能。
 */
internal object NetworkClientCookieSupport {

    /**
     * "k=v; k2=v2" 形式の Cookie 文字列を Map へ分解する。
     * RFC 6265 に準拠し、不正なエントリは無視する。
     */
    fun parseCookieString(s: String?): Map<String, String> {
        if (s.isNullOrBlank()) return emptyMap()

        return s.split(";").mapNotNull { segment ->
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) return@mapNotNull null

            val i = trimmed.indexOf('=')
            when {
                i <= 0 -> null // '='がない、または先頭にある
                else -> {
                    val key = trimmed.substring(0, i).trim()
                    val value = if (i + 1 < trimmed.length) trimmed.substring(i + 1).trim() else ""
                    when {
                        key.isEmpty() -> null
                        key.any { it.isWhitespace() || it in setOf(';', ',', '=', '"', '\\') } -> null
                        else -> key to value
                    }
                }
            }
        }.toMap()
    }

    /**
     * 複数の Cookie 文字列を安全にマージする。
     * - 後ろの引数が前の引数を上書き（後勝ち）。
     * - 認証系キー（session/token/csrf 等）は常に最後に配置して優先度を保証。
     * - すべての文字列が空/null なら null を返す。
     */
    fun mergeCookies(vararg cookieStrs: String?): String? {
        val criticalKeys = setOf("session", "sessionid", "auth", "token", "csrf", "xsrf", "jwt")

        val merged = mutableMapOf<String, String>()
        val criticalCookies = mutableMapOf<String, String>()

        cookieStrs.forEach { cookieStr ->
            parseCookieString(cookieStr).forEach { (key, value) ->
                val normalizedKey = key.lowercase()
                if (criticalKeys.any { normalizedKey.contains(it) }) {
                    criticalCookies[key] = value
                } else {
                    merged[key] = value
                }
            }
        }

        merged.putAll(criticalCookies)
        return if (merged.isEmpty()) null
        else merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
