package com.valoser.toshikari.ui.detail

/**
 * 「そうだね」タップ位置から対象レス番号を解決する補助。
 */
internal object DetailSodaneTargetResolver {
    fun resolve(displayText: String, offset: Int, fallbackResNum: String?): String? {
        val fallback = fallbackResNum?.takeIf { it.isNotBlank() }
        if (displayText.isEmpty()) return fallback

        val adjustedOffset = offset.coerceAtMost(displayText.length)
        val lineStart = displayText.lastIndexOf('\n', startIndex = adjustedOffset, ignoreCase = false)
            .let { if (it < 0) 0 else it + 1 }
        val lineEnd = displayText.indexOf('\n', startIndex = lineStart)
            .let { if (it < 0) displayText.length else it }
        val lineText = displayText.substring(lineStart, lineEnd)
        return DetailResNumberExtractor.extract(lineText) ?: fallback
    }
}
