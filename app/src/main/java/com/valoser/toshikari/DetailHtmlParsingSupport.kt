package com.valoser.toshikari

import java.net.URL
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * スレ HTML 解析時に共有する純粋な補助関数群。
 * `DetailViewModel` と `ThreadMonitorWorker` で共通化し、レス番号抽出や媒体判定の重複を防ぐ。
 */
object DetailHtmlParsingSupport {
    private val docWritePattern = Regex("""document\.write\s*\(\s*'(.*?)'\s*\)""")
    private val threadEndTimePattern = Regex("""<span id="contdisp">([^<]+)</span>""")
    private val noPattern = Regex("""No\.?\s*(\n?\s*)?(\d+)""")
    private val noPatternFallback = Regex("""No\.?\s*(\d+)""")
    private val mediaUrlPattern = Regex("""\.(jpg|jpeg|png|gif|webp|webm|mp4)$""", RegexOption.IGNORE_CASE)
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp")
    private val videoExtensions = setOf("webm", "mp4")

    fun extractThreadId(url: String): String {
        return url.substringAfterLast('/').substringBefore(
            ".htm",
            missingDelimiterValue = url.substringAfterLast('/')
        ).ifBlank {
            url.hashCode().toUInt().toString(16)
        }
    }

    fun extractResNum(html: String, isOp: Boolean, threadId: String): String? {
        if (isOp) return threadId
        return noPattern.find(html)?.groupValues?.getOrNull(2)
            ?: noPatternFallback.find(html)?.groupValues?.getOrNull(1)
    }

    fun buildTextContentId(isOp: Boolean, threadId: String, resNum: String?, index: Int): String {
        return if (isOp) {
            "text_op_$threadId"
        } else {
            "text_${resNum ?: "reply_${threadId}_${index}"}"
        }
    }

    fun isMediaUrl(rawHref: String): Boolean {
        val normalized = rawHref.substringBefore('#').substringBefore('?')
        return mediaUrlPattern.containsMatchIn(normalized)
    }

    fun buildMediaContent(
        absoluteUrl: String,
        rawHref: String,
        thumbnailUrl: String? = null
    ): DetailContent? {
        val normalizedHref = rawHref.substringBefore('#').substringBefore('?')
        val extension = normalizedHref.substringAfterLast('.', "").lowercase()
        return when {
            extension in imageExtensions -> {
                DetailContent.Image(
                    id = "image_${absoluteUrl.hashCode().toUInt().toString(16)}",
                    imageUrl = absoluteUrl,
                    prompt = null,
                    fileName = absoluteUrl.substringAfterLast('/'),
                    thumbnailUrl = thumbnailUrl
                )
            }
            extension in videoExtensions -> {
                DetailContent.Video(
                    id = "video_${absoluteUrl.hashCode().toUInt().toString(16)}",
                    videoUrl = absoluteUrl,
                    prompt = null,
                    fileName = absoluteUrl.substringAfterLast('/'),
                    thumbnailUrl = thumbnailUrl
                )
            }
            else -> null
        }
    }

    fun extractThreadEndTime(document: Document): String? {
        for (scriptElement in document.select("script")) {
            val scriptData = scriptElement.data()
            if (!scriptData.contains("document.write") || !scriptData.contains("contdisp")) continue
            val writtenHtml = docWritePattern.find(scriptData)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace("\\'", "'")
                ?.replace("\\/", "/")
                ?: continue
            val endTime = threadEndTimePattern.find(writtenHtml)?.groupValues?.getOrNull(1)
            if (!endTime.isNullOrBlank()) return endTime
        }
        return null
    }

    fun buildThreadEndTimeContent(endTime: String): DetailContent.ThreadEndTime {
        return DetailContent.ThreadEndTime(
            id = "thread_end_time_${endTime.hashCode().toUInt().toString(16)}",
            endTime = endTime
        )
    }

    /** アンカータグ内の <img> からサムネイルURLを解決し、なければフルURLから推測する。 */
    fun resolveThumbnailUrl(
        linkNode: Element,
        documentUrl: String,
        fullImageUrl: String
    ): String? {
        val base = runCatching { URL(documentUrl) }.getOrNull()

        fun Element?.resolveAttr(vararg names: String): String? {
            if (this == null) return null
            for (name in names) {
                if (!hasAttr(name)) continue
                val raw = attr(name).trim()
                if (raw.isEmpty()) continue
                val normalized = raw.substringBefore(' ').substringBefore(',')
                if (normalized.isEmpty() || normalized.startsWith("data:", ignoreCase = true)) continue
                val absolute = if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                    normalized
                } else {
                    base?.let { runCatching { URL(it, normalized).toString() }.getOrNull() }
                }
                if (!absolute.isNullOrBlank()) return absolute
            }
            return null
        }

        val imgNodes = linkNode.select("img,source")
        for (node in imgNodes) {
            node.resolveAttr(
                "src",
                "data-src",
                "data-original",
                "data-thumb",
                "data-lazy-src",
                "data-lazy",
                "data-llsrc",
                "data-placeholder",
                "data-url"
            )?.let { return it }
            node.resolveAttr("srcset", "data-srcset")?.let { return it }
        }

        linkNode.select("*[src],*[data-src],*[srcset],*[data-srcset]").forEach { element ->
            element.resolveAttr(
                "src",
                "data-src",
                "data-original",
                "data-thumb",
                "data-lazy-src",
                "data-lazy",
                "data-llsrc",
                "data-placeholder",
                "data-url",
                "srcset",
                "data-srcset"
            )?.let { return it }
        }

        return guessThumbnailFromFull(fullImageUrl)
    }

    /** `/src/12345.jpg` -> `/thumb/12345s.jpg` 形式でサムネイルURLを推測する。 */
    fun guessThumbnailFromFull(fullImageUrl: String): String? {
        val sanitized = fullImageUrl
            .substringBefore('#')
            .substringBefore('?')
        val marker = "/src/"
        val markerIndex = sanitized.indexOf(marker)
        if (markerIndex == -1) return null
        val dot = sanitized.lastIndexOf('.')
        val hasExtension = dot > markerIndex && dot < sanitized.length - 1
        val baseWithoutExt = if (hasExtension) sanitized.substring(0, dot) else sanitized
        val originalExtension = if (hasExtension) sanitized.substring(dot + 1).lowercase() else ""
        val extensionCandidates = buildList {
            if (originalExtension.isNotBlank()) {
                if (originalExtension != "jpg" && originalExtension != "jpeg") add("jpg")
                add(originalExtension)
            } else {
                add("jpg")
            }
        }
        val replacements = listOf("/thumb/", "/cat/")
        for (replacement in replacements) {
            val replaced = baseWithoutExt.replaceFirst(marker, replacement)
            if (replaced == baseWithoutExt) continue
            val baseCandidates = buildList {
                val withSuffix = if (replaced.endsWith("s")) replaced else "${replaced}s"
                add(withSuffix)
                if (withSuffix != replaced) add(replaced)
            }
            for (baseCandidate in baseCandidates) {
                for (ext in extensionCandidates) {
                    val candidate = if (ext.isNotBlank()) "$baseCandidate.$ext" else baseCandidate
                    if (!candidate.equals(sanitized, ignoreCase = true)) return candidate
                }
            }
        }
        return null
    }
}
