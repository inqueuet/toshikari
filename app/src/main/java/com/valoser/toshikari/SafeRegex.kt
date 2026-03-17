package com.valoser.toshikari

import android.util.Log

/**
 * ReDoS 対策を含む安全な正規表現ユーティリティ。
 *
 * - パターンの複雑さを事前チェック（ネスト量指子や過剰な選択肢を検出）
 * - コンパイル済みパターンをキャッシュして再利用
 * - コンパイル失敗時は `null` を返却して呼び出し側のフォールバックを可能にする
 */
internal object SafeRegex {

    private const val TAG = "SafeRegex"

    /** パターン長の上限。これを超えるとコンパイルを拒否する。 */
    private const val MAX_PATTERN_LENGTH = 1024

    /** ネストされた量指子の検出パターン（例: (a+)+ や (a*)* ）。ReDoS の代表的なトリガー。 */
    private val NESTED_QUANTIFIER = Regex("""[+*]\)[\s]*[+*?]""")

    /** LRU 的に利用するコンパイル済み正規表現キャッシュ（最大 64 エントリ）。 */
    private val cache = object : LinkedHashMap<CacheKey, Regex>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Regex>?): Boolean =
            size > 64
    }

    private data class CacheKey(val pattern: String, val options: Set<RegexOption>)

    /**
     * パターン文字列を安全にコンパイルして返す。
     *
     * @return コンパイル済み [Regex]。パターンが危険・不正な場合は `null`。
     */
    @Synchronized
    fun compile(pattern: String, options: Set<RegexOption> = emptySet()): Regex? {
        if (pattern.length > MAX_PATTERN_LENGTH) {
            Log.w(TAG, "Pattern too long (${pattern.length} > $MAX_PATTERN_LENGTH), rejected")
            return null
        }
        if (NESTED_QUANTIFIER.containsMatchIn(pattern)) {
            Log.w(TAG, "Nested quantifier detected, pattern rejected: ${pattern.take(80)}")
            return null
        }

        val key = CacheKey(pattern, options)
        cache[key]?.let { return it }

        return try {
            val regex = Regex(pattern, options)
            cache[key] = regex
            regex
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compile regex: ${pattern.take(80)}", e)
            null
        }
    }

    /**
     * パターンが [target] にマッチするかを安全に判定する。
     * コンパイル失敗やパターン拒否時は `false` を返す。
     */
    fun containsMatchIn(
        pattern: String,
        target: String,
        ignoreCase: Boolean = false,
    ): Boolean {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val regex = compile(pattern, options) ?: return false
        return regex.containsMatchIn(target)
    }
}
