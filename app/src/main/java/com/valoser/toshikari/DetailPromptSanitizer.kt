package com.valoser.toshikari

internal object DetailPromptSanitizer {
    fun sanitizeContents(list: List<DetailContent>): List<DetailContent> {
        if (list.isEmpty()) return list
        if (!list.any(::hasPromptNeedingSanitize)) return list

        var changed = false
        val sanitized = list.map { content ->
            when (content) {
                is DetailContent.Image -> {
                    val normalized = normalize(content.prompt)
                    if (normalized != content.prompt) {
                        changed = true
                        content.copy(prompt = normalized)
                    } else {
                        content
                    }
                }
                is DetailContent.Video -> {
                    val normalized = normalize(content.prompt)
                    if (normalized != content.prompt) {
                        changed = true
                        content.copy(prompt = normalized)
                    } else {
                        content
                    }
                }
                else -> content
            }
        }
        return if (changed) sanitized else list
    }

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (!needsHtmlNormalization(trimmed)) return trimmed

        val plain = stripHtmlAndDecodeEntities(trimmed).trim()
        return plain.ifBlank { null }
    }

    /** HTMLタグを除去しエンティティをデコードする（Android非依存） */
    private fun stripHtmlAndDecodeEntities(html: String): String {
        // 1) 名前付きエンティティをデコード（&amp; は最後）
        val decoded = html
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("&#x([0-9A-Fa-f]+);")) { mr ->
                mr.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: mr.value
            }
            .replace(Regex("&#([0-9]+);")) { mr ->
                mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: mr.value
            }
            .replace("&amp;", "&")
        // 2) タグを除去
        return decoded.replace(Regex("<[^>]*>"), "")
    }

    private fun hasPromptNeedingSanitize(content: DetailContent): Boolean = when (content) {
        is DetailContent.Image -> needsHtmlNormalization(content.prompt)
        is DetailContent.Video -> needsHtmlNormalization(content.prompt)
        else -> false
    }

    private fun needsHtmlNormalization(value: String?): Boolean {
        val trimmed = value?.trim() ?: return false
        if (trimmed.isEmpty()) return false

        val hasAngleBrackets = trimmed.indexOf('<') >= 0 && trimmed.indexOf('>') > trimmed.indexOf('<')
        if (hasAngleBrackets) return true

        val lower = trimmed.lowercase()
        return lower.contains("&lt;") || lower.contains("&gt;") || lower.contains("&amp;") || lower.contains("&#")
    }
}
