package com.valoser.toshikari.ui.detail

/**
 * `DetailList` のそうだね表示まわりで使うテキスト整形の純粋ロジック。
 */
internal object DetailSodaneTextFormatter {
    private val noLookaheadPattern = Regex("""([0-9)])\s*(?=No[.\uFF0E]?)""", RegexOption.IGNORE_CASE)
    private val genericNoSpacingPattern = Regex("""(?i)(?<=\S)(?=No[.\uFF0E]?\s*\d+)""")
    private val idBeforeNoPattern = Regex("""(?i)(ID[:：][\\w./+\-]+)\s*(?=No[.\uFF0E]?)""", RegexOption.IGNORE_CASE)
    private val noBeforeIdPattern = Regex("""(?i)(No[.\uFF0E]?\s*\d+)\s*(?=ID[:：])""", RegexOption.IGNORE_CASE)
    private val noBeforeSodanePattern = Regex("""(No[.\uFF0E]?\s*\d+)(?=(?:[+＋]|そうだね))""")
    private val repeatedSpacesPattern = Regex("[ ]{2,}")
    private val anyNoPattern = Regex("""(?i)\bNo[.\uFF0E]?\s*\d+\b""")
    private val firstNoPattern = Regex("""(?i)(No[.\uFF0E]?\s*\d+)""")
    private val existingSodanePattern = Regex("""^\s*(?:[+＋]|そうだね(?:x\d+)?)""")
    private val sodaneTokenPattern = Regex("""(?:そうだねx\d+|そうだね|[+＋])""")

    fun padTokensForSpacing(src: String): String {
        var text = src.replace("\u200B", "")
        text = DetailTextNormalizer.normalizePlain(text.replace('　', ' '))
        text = noLookaheadPattern.replace(text, "$1 ")
        text = genericNoSpacingPattern.replace(text, " ")
        text = idBeforeNoPattern.replace(text, "$1 ")
        text = noBeforeIdPattern.replace(text, "$1 ")
        text = noBeforeSodanePattern.replace(text, "$1 ")
        text = repeatedSpacesPattern.replace(text, " ")

        val output = StringBuilder()
        var start = 0
        var lineIndex = 0
        while (start < text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val line = text.substring(start, end)
            val trimmed = line.trimStart()
            val isQuote = trimmed.startsWith(">")
            val hasNo = anyNoPattern.containsMatchIn(trimmed)

            if (DetailHeaderLineRules.isHeaderLine(line, lineIndex) && !isQuote && hasNo) {
                val noMatch = firstNoPattern.find(line)
                if (noMatch != null) {
                    val beforeNo = line.substring(0, noMatch.range.first)
                    val noText = noMatch.value
                    val afterNo = line.substring(noMatch.range.last + 1)
                    if (!existingSodanePattern.containsMatchIn(afterNo)) {
                        output.append(beforeNo).append(noText).append(" そうだね").append(afterNo)
                    } else {
                        output.append(line)
                    }
                } else {
                    output.append(line)
                }
            } else {
                output.append(line)
            }

            if (newline >= 0) output.append('\n')
            start = if (newline < 0) text.length else newline + 1
            lineIndex++
        }
        return output.toString()
    }

    fun applySodaneDisplay(text: String, overrides: Map<String, Int>, selfResNum: String?): String {
        if (overrides.isEmpty()) return text

        val output = StringBuilder(text.length + 100)
        var start = 0
        var lineIndex = 0
        while (start < text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            var line = text.substring(start, end)

            if (DetailHeaderLineRules.isHeaderLine(line, lineIndex)) {
                val resNum = DetailResNumberExtractor.extract(line) ?: selfResNum
                val count = resNum?.let { overrides[it] }
                if (count != null && count > 0) {
                    line = sodaneTokenPattern.replace(line, "そうだねx$count")
                }
            }

            output.append(line)
            if (newline >= 0) output.append('\n')
            start = if (newline < 0) text.length else newline + 1
            lineIndex++
        }
        return output.toString()
    }
}
