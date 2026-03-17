package com.valoser.toshikari

/**
 * スレ詳細画面のツールバータイトル整形を担う純粋ロジック。
 */
internal object DetailThreadTitleFormatter {
    private val longWhitespaceRegex = Regex("[\\s\u3000]{3,}")

    fun format(
        rawTitleHtml: String,
        htmlToPlain: (String) -> String = DetailPlainTextFormatter::fromHtml
    ): String {
        val plain = htmlToPlain(rawTitleHtml)
            .replace("\u200B", "")

        val cutByNewline = plain.substringBefore('\n').substringBefore('\r').trim()
        if (cutByNewline.isNotBlank() && cutByNewline.length < plain.length) {
            return cutByNewline
        }

        val whitespaceMatch = longWhitespaceRegex.find(plain)
        if (whitespaceMatch != null && whitespaceMatch.range.first > 0) {
            return plain.substring(0, whitespaceMatch.range.first).trim()
        }

        val threadWordIndex = plain.indexOf("スレ")
        if (threadWordIndex in 1 until plain.length) {
            return plain.substring(0, threadWordIndex + 2).trim()
        }

        return plain.trim()
    }
}
