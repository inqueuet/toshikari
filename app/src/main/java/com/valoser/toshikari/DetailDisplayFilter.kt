package com.valoser.toshikari

import java.text.Normalizer

internal data class DisplayFilterConfig(
    val hideDeletedRes: Boolean,
    val hideDuplicateRes: Boolean,
    val duplicateResThreshold: Int
)

internal object DetailDisplayFilter {
    private val duplicateWhitespaceRegex = Regex("\\s+")

    fun apply(
        items: List<DetailContent>,
        plainTextCache: Map<String, String>,
        config: DisplayFilterConfig,
        plainTextOf: (DetailContent.Text) -> String
    ): List<DetailContent> {
        if (items.isEmpty()) return items

        val cachedPlainText = { text: DetailContent.Text -> plainTextCache[text.id] ?: plainTextOf(text) }
        val withoutDeleted = if (config.hideDeletedRes) filterDeletedResponses(items) else items
        val withoutPhantomQuotes = filterPhantomQuoteResponses(withoutDeleted, cachedPlainText)

        return if (config.hideDuplicateRes) {
            filterDuplicateResponses(
                items = withoutPhantomQuotes,
                threshold = config.duplicateResThreshold,
                plainTextOf = cachedPlainText
            )
        } else {
            withoutPhantomQuotes
        }
    }

    private fun filterDeletedResponses(items: List<DetailContent>): List<DetailContent> {
        val filtered = items.filter { item ->
            item !is DetailContent.Text || !isDeletedRes(item)
        }
        return if (filtered.size == items.size) items else filtered
    }

    private fun filterPhantomQuoteResponses(
        items: List<DetailContent>,
        plainTextOf: (DetailContent.Text) -> String
    ): List<DetailContent> {
        if (items.isEmpty()) return items

        val seenBodyLines = mutableSetOf<String>()
        val result = mutableListOf<DetailContent>()
        var index = 0
        var anyFiltered = false

        while (index < items.size) {
            val item = items[index]
            if (item !is DetailContent.Text) {
                result += item
                index++
                continue
            }

            val plainText = plainTextOf(item)
            if (shouldHideAsPhantomQuote(plainText, seenBodyLines)) {
                anyFiltered = true
                index = skipAttachedMedia(items, index + 1).also {
                    if (it > index + 1) anyFiltered = true
                }
                continue
            }

            result += item
            rememberVisibleBodyLines(plainText, seenBodyLines)
            index++
        }

        return if (anyFiltered) result else items
    }

    private fun shouldHideAsPhantomQuote(
        plainText: String,
        seenBodyLines: Set<String>
    ): Boolean {
        for (line in plainText.lines()) {
            val trimmedStart = line
                .replace("\u200B", "")
                .replace('　', ' ')
                .replace('＞', '>')
                .trimStart()
            if (!trimmedStart.startsWith(">")) continue

            val leadingGtCount = trimmedStart.takeWhile { it == '>' }.length
            if (leadingGtCount != 1) continue

            val normalizedQuote = normalizeBodyLineForQuoteDetection(
                trimmedStart.drop(leadingGtCount).trimStart()
            ) ?: continue

            if (normalizedQuote.length < 2) continue
            if (normalizedQuote.all { it.isDigit() }) continue
            if (normalizedQuote !in seenBodyLines) return true
        }
        return false
    }

    private fun rememberVisibleBodyLines(
        plainText: String,
        seenBodyLines: MutableSet<String>
    ) {
        for (line in plainText.lines()) {
            val normalized = normalizeBodyLineForQuoteDetection(line) ?: continue
            val trimmedStart = line
                .replace("\u200B", "")
                .replace('　', ' ')
                .replace('＞', '>')
                .trimStart()
            val leadingGt = trimmedStart.takeWhile { it == '>' }.length
            if (leadingGt == 0) {
                seenBodyLines += normalized
            }
        }
    }

    private fun filterDuplicateResponses(
        items: List<DetailContent>,
        threshold: Int,
        plainTextOf: (DetailContent.Text) -> String
    ): List<DetailContent> {
        if (items.isEmpty()) return items

        val limit = threshold.coerceAtLeast(1)
        val result = mutableListOf<DetailContent>()
        val counters = mutableMapOf<String, Int>()
        var index = 0
        var anyFiltered = false

        while (index < items.size) {
            val item = items[index]
            if (item is DetailContent.Text) {
                val key = buildDuplicateContentKey(plainTextOf(item))
                if (key != null) {
                    val newCount = (counters[key] ?: 0) + 1
                    counters[key] = newCount
                    if (newCount > limit) {
                        anyFiltered = true
                        index = skipAttachedMedia(items, index + 1).also {
                            if (it > index + 1) anyFiltered = true
                        }
                        continue
                    }
                }
            }

            result += item
            index++
        }

        return if (anyFiltered) result else items
    }

    private fun skipAttachedMedia(items: List<DetailContent>, startIndex: Int): Int {
        var index = startIndex
        while (index < items.size) {
            val attachment = items[index]
            if (attachment is DetailContent.Image || attachment is DetailContent.Video) {
                index++
            } else {
                break
            }
        }
        return index
    }

    private fun normalizeBodyLineForQuoteDetection(raw: String): String? {
        var normalized = raw
            .replace("\u200B", "")
            .replace('　', ' ')
            .replace('＞', '>')
            .trim()
        if (normalized.isEmpty()) return null

        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC)
        normalized = duplicateWhitespaceRegex.replace(normalized, " ").trim()
        if (normalized.isEmpty()) return null
        if (normalized.startsWith("No.", ignoreCase = true)) return null
        if (normalized.startsWith("ID:", ignoreCase = true)) return null
        return normalized
    }

    private fun isDeletedRes(text: DetailContent.Text): Boolean {
        return text.htmlContent.contains("スレッドを立てた人によって削除されました") ||
            text.htmlContent.contains("書き込みをした人によって削除されました")
    }

    private fun buildDuplicateContentKey(plainText: String): String? {
        val bodyLines = mutableListOf<String>()
        for (line in plainText.lines()) {
            var normalized = line
                .replace("\u200B", "")
                .replace('　', ' ')
                .replace('＞', '>')
                .replace('≫', '>')
                .trim()
            if (normalized.isEmpty()) continue
            if (normalized.startsWith(">")) continue

            normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC)
            val collapsed = duplicateWhitespaceRegex.replace(normalized, " ").trim()
            if (collapsed.isEmpty()) continue
            if (collapsed.startsWith("No.", ignoreCase = true)) continue
            if (collapsed.startsWith("ID:", ignoreCase = true)) continue
            bodyLines += collapsed
        }

        if (bodyLines.isEmpty()) return null
        return bodyLines.joinToString("\n")
    }
}
