package com.valoser.toshikari

import com.valoser.toshikari.ui.detail.SearchState

internal object DetailSearchEngine {
    fun findHitPositions(
        query: String,
        contents: List<DetailContent>,
        plainTextOf: (DetailContent.Text) -> String
    ): List<Int> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        return buildList {
            contents.forEachIndexed { index, content ->
                val searchableText = searchableTextOf(content, plainTextOf) ?: return@forEachIndexed
                if (searchableText.contains(normalizedQuery, ignoreCase = true)) {
                    add(index)
                }
            }
        }
    }

    fun buildState(
        hasQuery: Boolean,
        hitPositions: List<Int>,
        currentHitIndex: Int
    ): SearchState {
        val active = hasQuery && hitPositions.isNotEmpty()
        val currentDisplay = if (active && currentHitIndex in hitPositions.indices) currentHitIndex + 1 else 0
        return SearchState(
            active = active,
            currentIndexDisplay = currentDisplay,
            total = hitPositions.size
        )
    }

    private fun searchableTextOf(
        content: DetailContent,
        plainTextOf: (DetailContent.Text) -> String
    ): String? {
        return when (content) {
            is DetailContent.Text -> plainTextOf(content)
            is DetailContent.Image -> "${content.prompt.orEmpty()} ${content.fileName.orEmpty()} ${content.imageUrl.substringAfterLast('/')}"
            is DetailContent.Video -> "${content.prompt.orEmpty()} ${content.fileName.orEmpty()} ${content.videoUrl.substringAfterLast('/')}"
            is DetailContent.ThreadEndTime -> null
        }
    }
}
