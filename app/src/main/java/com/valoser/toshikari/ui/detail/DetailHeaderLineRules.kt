package com.valoser.toshikari.ui.detail

/**
 * `DetailList` 周辺で使うヘッダー行判定の共通規則。
 */
internal object DetailHeaderLineRules {
    private val dateTimePattern = Regex("""\d{2}/\d{2}/\d{2}\(\S+\)\d{2}:\d{2}:\d{2}""")
    private val postNumberPattern = Regex("""^\d+""")
    private val namePattern = Regex("""(?:無念|Name|としあき)""")

    fun shouldHighlightBackground(text: String): Boolean {
        val firstLine = text.trim().lineSequence().firstOrNull() ?: return false
        return dateTimePattern.containsMatchIn(firstLine)
    }

    fun isHeaderLine(line: String, lineIndex: Int): Boolean {
        val trimmed = line.trim()
        if (dateTimePattern.containsMatchIn(trimmed)) return true
        if (lineIndex != 0) return false

        val hasPostNumber = postNumberPattern.containsMatchIn(trimmed)
        val hasName = namePattern.containsMatchIn(trimmed)
        val hasNo = DetailResNumberExtractor.extract(trimmed) != null
        return hasPostNumber && hasName && hasNo
    }
}
