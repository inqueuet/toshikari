package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent

internal data class DetailMediaGridEntry(
    val mediaIndex: Int,
    val parentTextIndex: Int,
    val fullUrl: String,
    val previewUrl: String,
    val prompt: String?
)

/**
 * メディア一覧シート用のグリッド項目を構築する補助。
 */
internal object DetailMediaGridEntries {
    fun build(items: List<DetailContent>, lowBandwidthMode: Boolean): List<DetailMediaGridEntry> {
        val entries = ArrayList<DetailMediaGridEntry>()
        for (index in items.indices) {
            when (val content = items[index]) {
                is DetailContent.Image -> {
                    entries += DetailMediaGridEntry(
                        mediaIndex = index,
                        parentTextIndex = parentTextIndex(items, index) ?: index,
                        fullUrl = content.imageUrl,
                        previewUrl = if (lowBandwidthMode) {
                            content.thumbnailUrl?.takeIf { it.isNotBlank() } ?: content.imageUrl
                        } else {
                            content.imageUrl
                        },
                        prompt = content.prompt
                    )
                }
                is DetailContent.Video -> {
                    entries += DetailMediaGridEntry(
                        mediaIndex = index,
                        parentTextIndex = parentTextIndex(items, index) ?: index,
                        fullUrl = content.videoUrl,
                        previewUrl = content.thumbnailUrl ?: content.videoUrl,
                        prompt = content.prompt
                    )
                }
                else -> Unit
            }
        }
        return entries
    }

    private fun parentTextIndex(items: List<DetailContent>, fromIndex: Int): Int? {
        return (fromIndex - 1 downTo 0).firstOrNull { items[it] is DetailContent.Text }
    }
}
