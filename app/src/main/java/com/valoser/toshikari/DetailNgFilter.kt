package com.valoser.toshikari

internal object DetailNgFilter {
    fun filter(
        items: List<DetailContent>,
        rules: List<NgRule>,
        idOf: (DetailContent.Text) -> String?,
        bodyOf: (DetailContent.Text) -> String
    ): List<DetailContent> {
        if (items.isEmpty()) return items

        val result = ArrayList<DetailContent>(items.size)
        var skipping = false

        for (item in items) {
            when (item) {
                is DetailContent.Text -> {
                    if (shouldHide(item, rules, idOf, bodyOf)) {
                        skipping = true
                    } else {
                        skipping = false
                        result += item
                    }
                }
                is DetailContent.Image, is DetailContent.Video -> {
                    if (!skipping) result += item
                }
                is DetailContent.ThreadEndTime -> result += item
            }
        }

        return result
    }

    private fun shouldHide(
        item: DetailContent.Text,
        rules: List<NgRule>,
        idOf: (DetailContent.Text) -> String?,
        bodyOf: (DetailContent.Text) -> String
    ): Boolean {
        val id = idOf(item)
        val body = bodyOf(item)

        return rules.any { rule ->
            when (rule.type) {
                RuleType.ID -> {
                    !id.isNullOrBlank() && matches(
                        target = id,
                        pattern = rule.pattern,
                        type = rule.match ?: MatchType.EXACT,
                        ignoreCase = true
                    )
                }
                RuleType.BODY -> matches(
                    target = body,
                    pattern = rule.pattern,
                    type = rule.match ?: MatchType.SUBSTRING,
                    ignoreCase = true
                )
                RuleType.TITLE -> false
            }
        }
    }

    private fun matches(target: String, pattern: String, type: MatchType, ignoreCase: Boolean): Boolean {
        return when (type) {
            MatchType.EXACT -> target.equals(pattern, ignoreCase)
            MatchType.PREFIX -> target.startsWith(pattern, ignoreCase)
            MatchType.SUBSTRING -> target.contains(pattern, ignoreCase)
            MatchType.REGEX -> SafeRegex.containsMatchIn(
                pattern = pattern,
                target = target,
                ignoreCase = ignoreCase,
            )
        }
    }
}
