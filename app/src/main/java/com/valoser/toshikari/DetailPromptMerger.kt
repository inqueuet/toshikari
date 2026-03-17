package com.valoser.toshikari

internal object DetailPromptMerger {
    fun merge(base: List<DetailContent>, prior: List<DetailContent>): List<DetailContent> {
        return mergeWithKeys(base, prior, ::defaultLookupKeys)
    }

    fun mergeByFileName(base: List<DetailContent>, prior: List<DetailContent>): List<DetailContent> {
        return mergeWithKeys(base, prior, ::fileNameLookupKeys)
    }

    private fun mergeWithKeys(
        base: List<DetailContent>,
        prior: List<DetailContent>,
        keysOf: (String?, String?) -> List<String>
    ): List<DetailContent> {
        if (base.isEmpty() || prior.isEmpty()) return base

        val promptByKey = buildPromptIndex(prior, keysOf)
        if (promptByKey.isEmpty()) return base

        return base.map { content ->
            when (content) {
                is DetailContent.Image -> mergeImage(content, promptByKey, keysOf)
                is DetailContent.Video -> mergeVideo(content, promptByKey, keysOf)
                else -> content
            }
        }
    }

    private fun buildPromptIndex(
        prior: List<DetailContent>,
        keysOf: (String?, String?) -> List<String>
    ): Map<String, String> {
        return buildMap {
            prior.forEach { content ->
                when (content) {
                    is DetailContent.Image -> {
                        if (!content.prompt.isNullOrBlank()) {
                            keysOf(content.imageUrl, content.fileName).forEach { key ->
                                put(key, content.prompt)
                            }
                        }
                    }
                    is DetailContent.Video -> {
                        if (!content.prompt.isNullOrBlank()) {
                            keysOf(content.videoUrl, content.fileName).forEach { key ->
                                put(key, content.prompt)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun mergeImage(
        content: DetailContent.Image,
        promptByKey: Map<String, String>,
        keysOf: (String?, String?) -> List<String>
    ): DetailContent.Image {
        if (!content.prompt.isNullOrBlank()) return content

        val inheritedPrompt = keysOf(content.imageUrl, content.fileName)
            .firstNotNullOfOrNull { key -> promptByKey[key] }
        return if (inheritedPrompt.isNullOrBlank()) content else content.copy(prompt = inheritedPrompt)
    }

    private fun mergeVideo(
        content: DetailContent.Video,
        promptByKey: Map<String, String>,
        keysOf: (String?, String?) -> List<String>
    ): DetailContent.Video {
        if (!content.prompt.isNullOrBlank()) return content

        val inheritedPrompt = keysOf(content.videoUrl, content.fileName)
            .firstNotNullOfOrNull { key -> promptByKey[key] }
        return if (inheritedPrompt.isNullOrBlank()) content else content.copy(prompt = inheritedPrompt)
    }

    private fun defaultLookupKeys(url: String?, fileName: String?): List<String> {
        val keys = mutableListOf<String>()
        fileName?.takeIf { it.isNotBlank() }?.let(keys::add)
        url?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let(keys::add)
        url?.takeIf { it.isNotBlank() }?.let(keys::add)
        return keys
    }

    private fun fileNameLookupKeys(url: String?, fileName: String?): List<String> {
        return fileName?.takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList()
    }
}
