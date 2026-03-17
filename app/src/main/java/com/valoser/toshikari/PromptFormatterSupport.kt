package com.valoser.toshikari

/**
 * PromptFormatter の純粋なテキスト処理ユーティリティ。
 * Android 依存を持たないため JUnit でテスト可能。
 */
internal object PromptFormatterSupport {

    /**
     * 大文字で始まる `Xxx: value` 形式の行（設定行）を削除する。
     */
    fun stripSettingsLines(text: String?): String {
        if (text.isNullOrBlank()) return text ?: ""
        val headerLike = Regex("""(?im)^[ \t]*[A-Z][\w ]+:\s?.*$\r?\n?""")
        return text.replace(headerLike, "").trim()
    }

    /**
     * 重み表記をユーザー向けに正規化する。
     * - `(tag: 1.2)` → `tag (×1.2)`
     * - `tag: 1.2` → `tag (×1.2)`
     * - `<lora:...>` はそのまま
     */
    fun normalizeWeight(tag: String): String {
        val t = tag.trim()
        if (t.startsWith("<") && t.endsWith(">")) return t

        val paren = Regex("""^\(([^():]+):\s*([0-9.]+)\)$""")
        paren.matchEntire(t)?.let { m ->
            return "${m.groupValues[1].trim()} (×${m.groupValues[2].trim()})"
        }

        val plain = Regex("""^([^():]+):\s*([0-9.]+)$""")
        plain.matchEntire(t)?.let { m ->
            return "${m.groupValues[1].trim()} (×${m.groupValues[2].trim()})"
        }

        return t
    }

    /**
     * タグ文字列をカンマで分割する。
     * - 括弧 `()` や山括弧 `<>` 内部のカンマは区切りとみなさない。
     * - エスケープされたカンマ `\,` も区切りとみなさない。
     * - 各タグの重みは [normalizeWeight] で整形する。
     */
    fun splitTags(src: String?): List<String> {
        if (src.isNullOrBlank()) return emptyList()
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var depthParen = 0
        var depthAngle = 0
        var escape = false

        fun flush() {
            val raw = sb.toString().trim()
            if (raw.isNotEmpty()) out += normalizeWeight(raw)
            sb.setLength(0)
        }

        src.forEach { ch ->
            if (escape) { sb.append(ch); escape = false; return@forEach }
            when (ch) {
                '\\' -> { sb.append(ch); escape = true }
                '(' -> { depthParen++; sb.append(ch) }
                ')' -> { depthParen = maxOf(0, depthParen - 1); sb.append(ch) }
                '<' -> { depthAngle++; sb.append(ch) }
                '>' -> { depthAngle = maxOf(0, depthAngle - 1); sb.append(ch) }
                ',' -> {
                    if (depthParen > 0 || depthAngle > 0) sb.append(ch)
                    else flush()
                }
                else -> sb.append(ch)
            }
        }
        flush()
        return out.map { it.replace("\\(", "(").replace("\\)", ")").replace("\\,", ",") }
    }
}
