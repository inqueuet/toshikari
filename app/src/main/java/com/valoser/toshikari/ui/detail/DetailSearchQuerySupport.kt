package com.valoser.toshikari.ui.detail

/**
 * Detail 画面の検索入力を正規化する補助。
 */
internal object DetailSearchQuerySupport {
    fun normalize(query: String): String? {
        return query.trim().takeIf { it.isNotEmpty() }
    }
}
