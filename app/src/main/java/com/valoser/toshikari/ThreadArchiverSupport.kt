package com.valoser.toshikari

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URL

/**
 * ThreadArchiver の純粋なユーティリティ関数を集約したオブジェクト。
 * Android 依存を持たないため JUnit でテスト可能。
 */
internal object ThreadArchiverSupport {

    /**
     * ファイル名として不正な文字を '_' に置換する（Windows/FAT32 互換）。
     */
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    /**
     * URL とタイムスタンプからアーカイブディレクトリ名を生成する。
     * 例: https://img.2chan.net/b/res/1234567890.htm, "20250131_123456"
     *   -> "b_1234567890_20250131_123456"
     */
    fun generateDirectoryNameFromUrl(url: String, timestamp: String): String {
        return try {
            val urlObj = URL(url)
            val pathParts = urlObj.path.split("/").filter { it.isNotBlank() }
            val boardName = if (pathParts.isNotEmpty()) pathParts[0] else "unknown"
            val threadId = if (pathParts.size >= 3) {
                pathParts[2].substringBeforeLast('.')
            } else {
                url.hashCode().toString(16)
            }
            "${boardName}_${threadId}_${timestamp}"
        } catch (_: Exception) {
            "thread_${url.hashCode().toString(16)}_${timestamp}"
        }
    }

    /**
     * URL からファイル名を生成する。
     * - パスの末尾に拡張子付きファイル名があればサニタイズして使用。
     * - 無ければ URL ハッシュと [fallbackExtension] を使用。
     */
    fun generateFileName(url: String, fallbackExtension: String = "jpg"): String {
        return try {
            val urlObj = URL(url)
            val fileName = urlObj.path.substringAfterLast('/')
            if (fileName.isNotBlank() && fileName.contains('.')) {
                sanitizeFileName(fileName)
            } else {
                "${url.hashCode().toString(16)}.$fallbackExtension"
            }
        } catch (_: Exception) {
            "${url.hashCode().toString(16)}.$fallbackExtension"
        }
    }

    /**
     * サブディレクトリとファイル名からアーカイブ内の相対パスを組み立てる。
     */
    fun buildRelativePath(subDirectory: String, fileName: String): String {
        return if (subDirectory.isBlank()) fileName else "$subDirectory/$fileName"
    }

    /**
     * HTML 特殊文字を実体参照にエスケープする。
     */
    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    // ===== HTML 整形ユーティリティ =====

    /**
     * blockquote の HTML がテキスト 400 文字以上または br 6 個以上なら「長文」と判定する。
     */
    fun isLongQuote(html: String): Boolean {
        val text = html.replace(Regex("(?is)<.*?>"), "")
        val brCount = Regex("(?i)<br\\s*/?>").findAll(html).count()
        return text.length >= 400 || brCount >= 6
    }

    /**
     * blockquote が長文の場合、折りたたみ `<details>` で包む。
     */
    fun wrapLongQuoteIfNeeded(html: String): String {
        return if (Regex("(?is)^\\s*<blockquote\\b").containsMatchIn(html) && isLongQuote(html)) {
            """<details class="long-quote"><summary>長文の引用を開く</summary>$html</details>"""
        } else html
    }

    /**
     * テキストを段落に整形する。
     * - 連続 `<br>` → 段落区切り
     * - 空行 → 段落区切り
     * - 各段落を `<p>` で包む
     */
    fun textToParagraphs(t: String): String {
        if (t.isBlank()) return ""
        var s = t.replace("\r\n", "\n")
        s = s.replace(Regex("(?i)(?:\\s*<br\\s*/?>\\s*){2,}"), "\n\n")
        val chunks = s.trim().split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return chunks.joinToString(separator = "") { para ->
            val inner = para.replace(Regex("\\n"), "<br>")
            "<p>$inner</p>"
        }
    }

    /**
     * 本文 HTML の整形:
     * - テキストノードのみ段落分割
     * - 既存のブロック要素はそのまま保持
     * - 長大な blockquote は details で折りたたみ
     */
    fun formatParagraphsAndQuotes(rawHtml: String): String {
        if (rawHtml.isBlank()) return rawHtml

        val blockTags = setOf(
            "blockquote", "details", "figure", "div", "ul", "ol", "li",
            "dl", "dt", "dd", "pre", "table", "thead", "tbody", "tr", "td", "th",
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "hr"
        )

        val parts = mutableListOf<Pair<Boolean, String>>()
        try {
            val doc = Jsoup.parseBodyFragment(rawHtml)
            val body = doc.body()
            body.childNodes().forEach { node ->
                when (node) {
                    is Element -> {
                        val isBlock = node.tagName().lowercase() in blockTags
                        parts += isBlock to node.outerHtml()
                    }
                    is TextNode -> {
                        val text = node.wholeText
                        if (text.isNotBlank()) {
                            parts += false to text
                        }
                    }
                    else -> {
                        parts += false to node.outerHtml()
                    }
                }
            }
        } catch (_: Exception) {
            return rawHtml
        }

        if (parts.isEmpty()) return rawHtml

        val out = StringBuilder()
        for ((isBlock, content) in parts) {
            if (isBlock) {
                out.append(wrapLongQuoteIfNeeded(content))
            } else {
                out.append(textToParagraphs(content))
            }
        }
        return out.toString()
    }

    /**
     * HTML コンテンツ内のリモート URL パスをローカル相対パスに置き換える。
     */
    fun replaceLinksWithLocalPaths(
        htmlContent: String,
        downloadedFiles: Map<String, String>,
    ): String {
        var result = htmlContent
        downloadedFiles.forEach { (originalUrl, localPath) ->
            try {
                val url = URL(originalUrl)
                val path = url.path
                result = result.replace("href=\"$path\"", "href=\"$localPath\"")
                result = result.replace("src=\"$path\"", "src=\"$localPath\"")
                result = result.replace("href='$path'", "href='$localPath'")
                result = result.replace("src='$path'", "src='$localPath'")
            } catch (_: Exception) {
                // URL パース失敗時はスキップ
            }
        }
        return result
    }
}
