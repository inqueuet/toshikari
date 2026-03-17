package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent

/**
 * `Text + 直後のメディア群` で構成されるグループ操作の共通補助。
 */
internal object DetailContentGroupSupport {
    fun collectGroupAt(all: List<DetailContent>, startIndex: Int): List<DetailContent> {
        if (startIndex !in all.indices) return emptyList()

        val group = mutableListOf<DetailContent>()
        group += all[startIndex]
        var index = startIndex + 1
        while (index < all.size) {
            when (val content = all[index]) {
                is DetailContent.Image, is DetailContent.Video -> {
                    group += content
                    index++
                }
                is DetailContent.Text, is DetailContent.ThreadEndTime -> break
            }
        }
        return group
    }

    fun collectGroupsAt(all: List<DetailContent>, startIndexes: Iterable<Int>): List<List<DetailContent>> {
        return startIndexes.mapNotNull { index ->
            collectGroupAt(all, index).takeIf { it.isNotEmpty() }
        }
    }

    fun regroupFlatItems(items: List<DetailContent>): List<List<DetailContent>> {
        if (items.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<DetailContent>>()
        for (item in items) {
            if (item is DetailContent.Text || groups.isEmpty()) {
                groups.add(mutableListOf())
            }
            groups.last() += item
        }
        return groups
    }

    fun flattenDistinctGroups(groups: List<List<DetailContent>>): List<DetailContent> {
        val uniqueGroups = groups.distinctBy { it.firstOrNull()?.id }
        val flat = uniqueGroups.flatten()
        return mergeDistinctItems(flat)
    }

    fun mergeDistinctItems(items: Iterable<DetailContent>): List<DetailContent> {
        val seen = HashSet<String>()
        val output = ArrayList<DetailContent>()
        for (content in items) {
            if (seen.add(content.id)) output += content
        }
        return output
    }
}
