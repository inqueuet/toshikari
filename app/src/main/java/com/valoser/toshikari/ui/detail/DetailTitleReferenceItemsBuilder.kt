package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import com.valoser.toshikari.DetailPlainTextFormatter

/**
 * スレタイタップ時に表示する参照一覧を構築する補助。
 */
internal object DetailTitleReferenceItemsBuilder {
    fun build(
        items: List<DetailContent>,
        title: String,
        plainTextOf: (DetailContent.Text) -> String = DetailPlainTextFormatter::fromText,
    ): List<DetailContent> {
        val source = items.firstOrNull { it is DetailContent.Text } as? DetailContent.Text ?: return emptyList()

        val byContent = buildSelfAndBackrefItems(
            all = items,
            source = source,
            extraCandidates = setOf(title),
            plainTextOf = plainTextOf
        )
        val byNumber = source.resNum?.takeIf { it.isNotBlank() }?.let { resNum ->
            buildResReferencesItems(items, resNum, plainTextOf = plainTextOf)
        }.orEmpty()

        return DetailContentGroupSupport.mergeDistinctItems(byContent + byNumber)
    }
}
