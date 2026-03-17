package com.valoser.toshikari.ui.detail

import java.text.Normalizer

internal object DetailTextNormalizer {
    fun normalizePlain(value: String): String {
        return Normalizer.normalize(
            value
                .replace("\u200B", "")
                .replace('　', ' ')
                .replace('＞', '>')
                .replace('≫', '>'),
            Normalizer.Form.NFKC
        )
    }

    fun normalizeCollapsed(value: String): String {
        return normalizePlain(value)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun normalizeTrimmed(value: String): String {
        return normalizePlain(value).trim()
    }

    fun normalizeQuoteToken(token: String): String {
        return normalizePlain(token).trimStart()
    }
}
