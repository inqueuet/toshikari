package com.valoser.toshikari.ui.detail

/**
 * `No.12345` 形式のレス番号を寛容に抽出する共通ヘルパ。
 */
internal object DetailResNumberExtractor {
    private val resNumberPattern = Regex("""(?i)No[.\uFF0E]?\s*(?:\n?\s*)?(\d+)""")

    fun extract(text: String): String? {
        val normalized = DetailTextNormalizer.normalizePlain(text)
        return resNumberPattern.find(normalized)?.groupValues?.getOrNull(1)
    }
}
