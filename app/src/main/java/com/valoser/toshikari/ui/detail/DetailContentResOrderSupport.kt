package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent

/**
 * `DetailContent.Text` からレス番号を取り出し、グループ並び順に使う共通補助。
 */
internal object DetailContentResOrderSupport {
    fun extractResNumber(
        content: DetailContent?,
        plainTextOf: (DetailContent.Text) -> String,
    ): Int? {
        return when (content) {
            is DetailContent.Text -> extractResNumber(content, plainTextOf)
            else -> null
        }
    }

    fun extractResNumber(
        content: DetailContent.Text,
        plainTextOf: (DetailContent.Text) -> String,
    ): Int? {
        return content.resNum?.toIntOrNull()
            ?: DetailResNumberExtractor.extract(plainTextOf(content))?.toIntOrNull()
    }

    fun sortGroupsByResNumber(
        groups: List<List<DetailContent>>,
        plainTextOf: (DetailContent.Text) -> String,
    ): List<List<DetailContent>> {
        return groups.sortedWith(compareBy<List<DetailContent>> { group ->
            extractResNumber(group.firstOrNull(), plainTextOf) ?: Int.MAX_VALUE
        })
    }
}
