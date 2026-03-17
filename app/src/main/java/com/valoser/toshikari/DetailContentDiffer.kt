package com.valoser.toshikari

/**
 * 既存コンテンツと新規解析結果の差分判定を担う純粋ロジック。
 * ID と内容ハッシュの両方を見て新規項目を抽出し、重複状況も返す。
 */
data class DetailContentDiff(
    val newItems: List<DetailContent>,
    val duplicateIds: Set<String>,
    val duplicateContentHashes: Set<String>
)

object DetailContentDiffer {
    fun diff(
        current: List<DetailContent>,
        parsed: List<DetailContent>,
        textBodyOf: (DetailContent.Text) -> String
    ): DetailContentDiff {
        val currentIds = current.map { it.id }.toSet()
        val currentContentHashes = current.map { contentHash(it, textBodyOf) }.toSet()

        val newItems = parsed.filter { item ->
            val itemHash = contentHash(item, textBodyOf)
            item.id !in currentIds && itemHash !in currentContentHashes
        }

        val duplicateIds = parsed.map { it.id }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        val duplicateContentHashes = parsed.map { contentHash(it, textBodyOf) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        return DetailContentDiff(
            newItems = newItems,
            duplicateIds = duplicateIds,
            duplicateContentHashes = duplicateContentHashes
        )
    }

    private fun contentHash(
        content: DetailContent,
        textBodyOf: (DetailContent.Text) -> String
    ): String {
        return when (content) {
            is DetailContent.Text -> textBodyOf(content).hashCode().toString()
            is DetailContent.Image -> content.imageUrl.hashCode().toString()
            is DetailContent.Video -> content.videoUrl.hashCode().toString()
            is DetailContent.ThreadEndTime -> content.endTime.hashCode().toString()
        }
    }
}
