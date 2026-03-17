package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent

/**
 * `ThreadEndTime` を一覧内で最後の 1 件だけ残すための純粋ロジック。
 */
internal object DetailThreadEndTimeNormalizer {
    fun normalize(contents: List<DetailContent>): List<DetailContent> {
        val endIndexes = contents.withIndex()
            .filter { it.value is DetailContent.ThreadEndTime }
            .map { it.index }
        if (endIndexes.isEmpty()) return contents

        val keepIndex = endIndexes.last()
        val normalized = ArrayList<DetailContent>(contents.size - (endIndexes.size - 1))
        for ((index, item) in contents.withIndex()) {
            if (item is DetailContent.ThreadEndTime) {
                if (index == keepIndex) normalized += item
            } else {
                normalized += item
            }
        }
        return normalized
    }
}
