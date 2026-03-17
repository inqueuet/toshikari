package com.valoser.toshikari

import java.util.LinkedHashMap

internal object DetailPlainTextCachePolicy {
    private const val maxEntries = 500
    private const val retainedEntries = 300

    fun addMissing(
        current: Map<String, String>,
        missingEntries: List<Pair<String, String>>
    ): Map<String, String>? {
        if (missingEntries.isEmpty()) return null

        val updated = LinkedHashMap(current)
        var changed = false
        for ((id, plainText) in missingEntries) {
            if (!updated.containsKey(id)) {
                updated[id] = plainText
                changed = true
            }
        }

        if (!changed) return null
        return trimIfNeeded(updated)
    }

    fun put(
        current: Map<String, String>,
        id: String,
        plainText: String
    ): Map<String, String>? {
        if (current.containsKey(id)) return null

        val updated = LinkedHashMap(current)
        updated[id] = plainText
        return trimIfNeeded(updated)
    }

    private fun trimIfNeeded(updated: Map<String, String>): Map<String, String> {
        if (updated.size <= maxEntries) return updated

        // 元の実装と同様、現在のエントリ列の後ろ側を優先して残す。
        return updated.entries
            .toList()
            .takeLast(retainedEntries)
            .associate { it.key to it.value }
    }
}
