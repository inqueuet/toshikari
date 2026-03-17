package com.valoser.toshikari

import android.text.Html

/**
 * `DetailContent.Text` の HTML をプレーンテキストへ変換する共通ヘルパ。
 */
internal object DetailPlainTextFormatter {
    fun fromHtml(html: String): String {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    fun fromText(text: DetailContent.Text): String {
        return fromHtml(text.htmlContent)
    }
}
