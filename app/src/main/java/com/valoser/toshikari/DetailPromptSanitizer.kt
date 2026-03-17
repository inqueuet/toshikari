package com.valoser.toshikari

import androidx.core.text.HtmlCompat

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

        val plain = HtmlCompat.fromHtml(trimmed, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        return plain.ifBlank { null }
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
