package com.valoser.toshikari

import java.text.Normalizer

internal object DetailTextParser {
    private val htmlIdRegex = Regex("""(?i)\bID\s*:\s*([^\s<)]+)""")
    private val plainIdRegex = Regex("""\b[Ii][Dd]\s*:\s*([A-Za-z0-9+/_\.-]+)(?=\s|\(|$|No\.)""")
    private val dateRegex = Regex("""\d{2}/\d{2}/\d{2}\([^)]+\)\d{2}:\d{2}:\d{2}""")
    private val fileExtRegex = Regex("""\.(?:jpg|jpeg|png|gif|webp|bmp|svg|webm|mp4|mov|mkv|avi|wmv|flv)\b""", RegexOption.IGNORE_CASE)
    private val sizeSuffixRegex = Regex("""[ \t]*[\\-ー−―–—]?\s*\(\s*\d+(?:\.\d+)?\s*(?:[kKmMgGtT]?[bB])\s*\)""")
    private val headLabelRegex = Regex("""^(?:画像|動画|ファイル名|ファイル|添付|サムネ|サムネイル)(?:\s*ファイル名)?\s*[:：]""", RegexOption.IGNORE_CASE)

    fun extractIdFromHtml(
        html: String,
        htmlToPlainText: (String) -> String
    ): String? {
        val normalizedHtml = normalizeIdSource(html)
        htmlIdRegex.find(normalizedHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return it }

        val normalizedPlain = normalizeIdSource(htmlToPlainText(html))
        return plainIdRegex.find(normalizedPlain)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    fun extractPlainBodyFromPlain(plain: String): String {
        fun isLabeledSizeOnlyLine(text: String): Boolean {
            return headLabelRegex.containsMatchIn(text) && sizeSuffixRegex.containsMatchIn(text)
        }

        return plain
            .lineSequence()
            .map { it.trimEnd() }
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("ID:") ||
                    trimmed.startsWith("No.") ||
                    dateRegex.containsMatchIn(trimmed) ||
                    trimmed.contains("Name")
            }
            .filterNot { line ->
                val trimmed = line.trim()
                headLabelRegex.containsMatchIn(trimmed) ||
                    (fileExtRegex.containsMatchIn(trimmed) && sizeSuffixRegex.containsMatchIn(trimmed)) ||
                    isLabeledSizeOnlyLine(trimmed) ||
                    (fileExtRegex.containsMatchIn(trimmed) && trimmed.contains("サムネ"))
            }
            .joinToString("\n")
            .trimEnd()
    }

    private fun normalizeIdSource(value: String): String {
        return Normalizer.normalize(
            value
                .replace("\u200B", "")
                .replace('　', ' ')
                .replace('：', ':'),
            Normalizer.Form.NFKC
        )
    }
}
