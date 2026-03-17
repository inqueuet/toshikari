package com.valoser.toshikari

/**
 * DetailActivity から抽出した引用・検索の純粋ロジック。
 * Android 依存を持たないため JUnit でテスト可能。
 */
internal object DetailQuoteSupport {

    /** 行頭が '>' 1個の引用行（最初の1つ）を抽出する。 */
    fun extractFirstLevelQuote(plainText: String): String? {
        val m = Regex("^>([^>].+)$", RegexOption.MULTILINE).find(plainText)
        return m?.groupValues?.getOrNull(1)?.trim()
    }

    /** 行頭が '>' 1個の引用行（複数）をすべて抽出する。 */
    fun extractAllFirstLevelQuotes(plainText: String): List<String> {
        return Regex("^>([^>].+)$", RegexOption.MULTILINE)
            .findAll(plainText)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    /**
     * クエリ文字列に基づき対象コンテンツを検索する。
     * サポート:
     * 1) "No.<番号>" による本文マッチ
     * 2) 画像/動画のファイル名またはURL末尾の一致
     * 3) 本文プレーンテキストの部分一致（空白圧縮・大文字小文字無視）
     *
     * @param plainTextProvider Text型コンテンツからプレーンテキストを取得する関数
     */
    fun findContentByText(
        all: List<DetailContent>,
        searchText: String,
        plainTextProvider: (DetailContent.Text) -> String
    ): DetailContent? {
        // 1) No.\d+
        Regex("""No\.(\d+)""").find(searchText)?.groupValues?.getOrNull(1)?.let { num ->
            val hit = all.firstOrNull {
                it is DetailContent.Text && plainTextProvider(it).contains("No.$num")
            }
            if (hit != null) return hit
        }

        // 2) 画像/動画 ファイル名末尾一致
        for (c in all) {
            when (c) {
                is DetailContent.Image -> if (c.fileName == searchText || c.imageUrl.endsWith(searchText)) return c
                is DetailContent.Video -> if (c.fileName == searchText || c.videoUrl.endsWith(searchText)) return c
                else -> {}
            }
        }

        // 3) 本文 部分一致（空白圧縮・大文字小文字無視）
        val needle = searchText.trim().replace(Regex("\\s+"), " ")
        return all.firstOrNull {
            it is DetailContent.Text && plainTextProvider(it)
                .trim()
                .replace(Regex("\\s+"), " ")
                .contains(needle, ignoreCase = true)
        }
    }
}
