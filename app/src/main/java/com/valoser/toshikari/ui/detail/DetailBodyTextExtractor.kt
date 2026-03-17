package com.valoser.toshikari.ui.detail

/**
 * プレーンテキストから「本文のみ」を抽出する純粋ロジック。
 */
internal object DetailBodyTextExtractor {
    private val idPattern = Regex("""(?i)\bID(?:[:：]|無し)\b[\w./+\-]*""")
    private val noPattern = Regex("""(?i)\b(?:No|Ｎｏ)[\.\uFF0E]?\s*\d+\b""")
    private val dateTimePattern = Regex("""(?:(?:\d{2}|\d{4})/\d{1,2}/\d{1,2}).*?\d{1,2}:\d{2}:\d{2}""")
    private val fileInfoHeadPattern = Regex("""(?i)^\s*(?:ファイル名|画像|ファイル)[:：].*""")
    private const val fileExt = "(?:jpg|jpeg|png|gif|webp|bmp|mp4|webm|avi|mov|mkv)"
    private val fileInfoGenericPattern = Regex("""(?i)^\s*.*?\.$fileExt\s*[\-ー－]?\s*\([^)]*\).*""")
    private val fileSizePattern = Regex("""^\s*(?:\[[\d\s]+[KMGT]?B\]|.*?[\-ー－]\([\d\s]+[KMGT]?B\))\s*$""")

    fun extract(plain: String): String {
        val lines = plain.lines()
        var start = 0
        while (start < lines.size) {
            val raw = lines[start]
            val trimmed = raw.trimStart()
            if (trimmed.isBlank()) {
                start++
                continue
            }
            val normalized = DetailTextNormalizer.normalizePlain(trimmed)
            val isHeader = idPattern.containsMatchIn(normalized) ||
                noPattern.containsMatchIn(normalized) ||
                dateTimePattern.containsMatchIn(normalized) ||
                fileInfoHeadPattern.containsMatchIn(normalized) ||
                fileInfoGenericPattern.containsMatchIn(normalized) ||
                fileSizePattern.containsMatchIn(trimmed)
            if (isHeader) {
                start++
                continue
            }
            break
        }

        return lines.drop(start)
            .filterNot { line -> fileSizePattern.containsMatchIn(line.trim()) }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
    }
}
