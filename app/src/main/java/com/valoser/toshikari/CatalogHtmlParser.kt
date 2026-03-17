package com.valoser.toshikari

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * カタログHTMLからImageItemリストを解析するユーティリティ。
 * MainViewModelから委譲される純粋な解析ロジックを集約する。
 */
internal object CatalogHtmlParser {

    /**
     * ドキュメントから ImageItem のリストを解析する。
     * 構造に応じて処理を振り分け（#cattable 優先、準備ページは空、なければ cgi 風フォールバック）。
     */
    internal fun parseItemsFromDocument(document: Document, url: String): List<ImageItem> {
        // 1) まず #cattable を最優先（cgi でも普通に存在する）
        val hasCatalogTable = document.select("#cattable td").isNotEmpty()
        if (hasCatalogTable) return parseFromCattable(document)

        // 2) 一部の準備ページは空
        if (url.contains("/junbi/")) return emptyList()

        // 3) 最後の手段として旧 cgi 風のフォールバック
        return parseCgiFallback(document)
    }

    // #cattable 用パーサ（旧実装の整理版）。
    // <img> が無い行は res/{id}.htm からIDを抜き、候補URLを構築すると同時に previewUnavailable を立てる。
    internal fun parseFromCattable(document: Document): List<ImageItem> {
        val parsedItems = mutableListOf<ImageItem>()
        val cells = document.select("#cattable td")

        for (cell in cells) {
            val linkTag = cell.selectFirst("a") ?: continue
            val detailUrl = linkTag.absUrl("href")

            // 1) まず通常通り <img> があればそれを使う
            val imgTag = linkTag.selectFirst("img")
            var imageUrl: String? = imgTag?.absUrl("src")
            var missingPreview = false

            // 2) <img> が無い（今回のHTMLのような）場合、res/{id}.htm から id を抜いて推測構築（候補列挙は行うが選定は後段の検証に委譲）
            if (imageUrl.isNullOrEmpty()) {
                val href = linkTag.attr("href") // 例: "res/178828.htm"
                val m = Regex("""res/(\d+)\.htm""").find(href)
                if (m != null) {
                    val id = m.groupValues[1]
                    // 例: https://zip.2chan.net/32/res/... -> https://zip.2chan.net/32
                    val boardBase = detailUrl.substringBeforeLast("/res/")
                    // 2chan のカタログは "cat/{id}s.{ext}" 形式が基本（小サムネ）。
                    // まずもっとも一般的な jpg を既定にし、後段の HEAD 検証と 404 修正で適正化する。
                    imageUrl = "$boardBase/cat/${id}s.jpg"
                    missingPreview = true
                }
            }

            // サムネイルURLが最終的に得られない場合はスキップ
            val validImageUrl = imageUrl?.takeIf { it.isNotEmpty() } ?: continue

            // タイトル・レス数（無ければ空でOK）
            val title = firstLineFromSmall(cell.selectFirst("small"))
            val replies = cell.selectFirst("font")?.text() ?: ""

            parsedItems.add(
                ImageItem(
                    previewUrl = validImageUrl,
                    title = title,
                    replyCount = replies,
                    detailUrl = detailUrl,
                    fullImageUrl = null,
                    previewUnavailable = missingPreview
                )
            )
        }
        return parsedItems
    }

    // 置き換え：cgi フォールバック（旧 parseForCgiServer を安全側に縮約）
    internal fun parseCgiFallback(document: Document): List<ImageItem> {
        val parsedItems = mutableListOf<ImageItem>()
        val links = document.select("a[href*='/res/']")

        for (linkTag in links) {
            val imgTag = linkTag.selectFirst("img") ?: continue
            val imageUrl = imgTag.absUrl("src")
            val detailUrl = linkTag.absUrl("href")
            val infoText = firstLineFromSmall(linkTag.parent()?.selectFirst("small"))
            if (imageUrl.isNotEmpty() && detailUrl.isNotEmpty()) {
                parsedItems.add(
                    ImageItem(
                        previewUrl = imageUrl,
                        title = infoText,
                        replyCount = "",
                        detailUrl = detailUrl,
                        fullImageUrl = null
                    )
                )
            }
        }
        return parsedItems
    }

    // small 要素から <br> より前の一行目を抽出してプレーンテキスト化
    // - 例: "タイトル<br>サブタイトル" → "タイトル"
    // - `<br>` が無い場合は全体をプレーン化して trim のみ
    internal fun firstLineFromSmall(small: Element?): String {
        val html = small?.html() ?: return ""
        val idx = html.indexOf("<br", ignoreCase = true)
        val head = if (idx >= 0) html.substring(0, idx) else html
        return try {
            Jsoup.parse(head).text().trim()
        } catch (_: Exception) {
            head.replace(Regex("<[^>]+>"), "").trim()
        }
    }
}
