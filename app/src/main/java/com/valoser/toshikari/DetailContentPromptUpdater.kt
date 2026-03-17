package com.valoser.toshikari

internal object DetailContentPromptUpdater {
    fun updatePrompt(
        contents: List<DetailContent>,
        contentId: String,
        prompt: String
    ): List<DetailContent> {
        if (contents.isEmpty()) return contents

        var changed = false
        val updated = contents.map { content ->
            when (content) {
                is DetailContent.Image -> {
                    if (content.id == contentId && content.prompt != prompt) {
                        changed = true
                        content.copy(prompt = prompt)
                    } else {
                        content
                    }
                }
                is DetailContent.Video -> {
                    if (content.id == contentId && content.prompt != prompt) {
                        changed = true
                        content.copy(prompt = prompt)
                    } else {
                        content
                    }
                }
                else -> content
            }
        }

        return if (changed) updated else contents
    }
}
