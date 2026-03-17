package com.valoser.toshikari

/**
 * ReplyRepository から抽出した純粋関数群。
 * Android 依存を持たないため JUnit でテスト可能。
 */
internal object ReplyRepositorySupport {

    private val errorPatterns = listOf(
        Regex("エラー.*発生", RegexOption.IGNORE_CASE),
        Regex("書.*込.*失敗|投稿.*失敗", RegexOption.IGNORE_CASE),
        Regex("連続.*投稿|連投", RegexOption.IGNORE_CASE),
        Regex("本文.*必要|本文.*なし", RegexOption.IGNORE_CASE),
        Regex("規制.*中|ブロック.*中", RegexOption.IGNORE_CASE),
        Regex("時間.*おいて|しばらく.*待", RegexOption.IGNORE_CASE),
        Regex("Cookie.*無効|セッション.*切れ", RegexOption.IGNORE_CASE)
    )

    private val successPatterns = listOf(
        Regex("書.*込.*まし|送信.*完了|投稿.*完了", RegexOption.IGNORE_CASE),
        Regex("No\\.?\\s*\\d{6,}", RegexOption.IGNORE_CASE)
    )

    /** 投稿レスポンスの HTML がエラーかどうかを判定する。 */
    fun looksLikeError(html: String): Boolean {
        // HTMLタグを除去してプレーンテキストで判定
        val plainText = try {
            org.jsoup.Jsoup.parse(html).text()
        } catch (e: Exception) {
            html.replace(Regex("<[^>]+>"), "")
        }

        // 成功を示すキーワードがある場合は成功として扱う
        val hasSuccessPattern = successPatterns.any { it.containsMatchIn(plainText) }
        if (hasSuccessPattern) return false

        return errorPatterns.any { it.containsMatchIn(plainText) }
    }

    /** JSON レスポンスから thisno (投稿番号) を抽出する。 */
    fun extractJsonThisNo(response: String): String? {
        return Regex("""\"thisno\"\s*:\s*(\d{6,})""").find(response)?.groupValues?.getOrNull(1)
    }

    /** HTML レスポンスから No.xxxxx 形式の投稿番号を抽出する。 */
    fun extractHtmlPostNo(html: String): String? {
        return Regex("""No\.?\s*(\d{6,})""").find(html)?.groupValues?.getOrNull(1)
    }

    /** 成功を示すキーワードが含まれるか判定する。 */
    fun containsSuccessKeyword(text: String): Boolean {
        return Regex("書きこみ|完了|送信完了").containsMatchIn(text)
    }
}
