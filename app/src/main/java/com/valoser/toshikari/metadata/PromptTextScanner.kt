package com.valoser.toshikari.metadata

import java.util.regex.Pattern

/**
 * テキスト中から prompt / workflow / XMP 由来のプロンプト候補を正規表現で抽出する。
 *
 * MetadataExtractor の各フォーマット別 Extractor から共通的に呼ばれる純粋関数群。
 */
internal object PromptTextScanner {

    // ===== 正規表現（プリコンパイル） =====
    val RE_JSON_PROMPT: Pattern = Pattern.compile(
        """prompt"\s*:\s*("([^"\\]*(\\.[^"\\]*)*)"|\{.*?\})""",
        Pattern.DOTALL
    )
    val RE_JSON_WORKFLOW: Pattern = Pattern.compile(
        """workflow"\s*:\s*(\{.*?\})""",
        Pattern.DOTALL
    )
    val RE_CLIPTEXTENCODE: Pattern = Pattern.compile(
        """CLIPTextEncode"[\s\S]{0,2000}?"title"\s*:\s*"([^"]*Positive[^"]*)"[\s\S]{0,1000}?"(text|string)"\s*:\s*"((?:\\.|[^"\\])*)"""",
        Pattern.CASE_INSENSITIVE
    )
    val RE_XMP_ATTR: Pattern = Pattern.compile(
        """([a-zA-Z0-9_:.\-]*?(prompt|parameters))\s*=\s*"((?:\\.|[^"])*)"""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )
    val RE_XMP_TAG: Pattern = Pattern.compile(
        """<([a-zA-Z0-9_:.\-]*?(prompt|parameters))[^>]*>([\\s\\S]*?)</[^>]+>""",
        Pattern.CASE_INSENSITIVE
    )
    val RE_XMP_DESC: Pattern = Pattern.compile(
        "<dc:description[^>]*>\\s*<rdf:Alt>\\s*<rdf:li[^>]*>([\\s\\S]*?)</rdf:li>",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * テキスト中から prompt / workflow / CLIPTextEncode 由来の候補を正規表現で抽出する。
     */
    fun scanTextForPrompts(text: String): String? {
        RE_JSON_PROMPT.matcher(text).apply {
            if (find()) WorkflowPromptExtractor.parsePromptJson(group(1) ?: "")?.let {
                if (it.trim() != "UNICODE") return it
            }
        }
        RE_JSON_WORKFLOW.matcher(text).apply {
            if (find()) WorkflowPromptExtractor.parseWorkflowJson(group(1) ?: "")?.let {
                if (it.trim() != "UNICODE") return it
            }
        }
        RE_CLIPTEXTENCODE.matcher(text).apply {
            if (find()) {
                val result = (group(3) ?: "").replace("\\\"", "\"")
                if (result.trim() != "UNICODE") return result
            }
        }
        return null
    }

    /**
     * XMP 文字列からプロンプトを抽出する。
     * 属性 → タグ → dc:description → 埋め込みJSON の順に試行。
     */
    fun scanXmpForPrompts(xmp: String): String? {
        // 属性 prompt/parameters="..."
        run {
            val m = RE_XMP_ATTR.matcher(xmp)
            if (m.find()) {
                val v = m.group(3) ?: ""
                if (v.isNotBlank() && v.trim() != "UNICODE") return v.replace("\\\"", "\"")
            }
        }
        // タグ <ns:prompt>...</ns:prompt> or <ns:parameters>...</ns:parameters>
        run {
            val m = RE_XMP_TAG.matcher(xmp)
            if (m.find()) {
                val v = m.group(3) ?: ""
                if (v.isNotBlank() && v.trim() != "UNICODE") return v.trim()
            }
        }
        // dc:description/rdf:Alt/rdf:li のテキスト
        run {
            val m = RE_XMP_DESC.matcher(xmp)
            if (m.find()) {
                val v = m.group(1) ?: ""
                if (v.isNotBlank() && !WorkflowPromptExtractor.isLabely(v) && v.trim() != "UNICODE") return v.trim()
            }
        }
        // XMP内にJSONが埋まっている可能性にも対応
        scanTextForPrompts(xmp)?.let { return it }
        return null
    }

    /**
     * C2PA データからプロンプトを抽出する。
     * バイナリ中にJSON文字列が含まれる場合は既存のテキスト解析で拾う。
     */
    fun extractPromptFromC2paData(data: ByteArray): String? {
        kotlin.run {
            val latin = try { String(data, java.nio.charset.StandardCharsets.ISO_8859_1) } catch (_: Exception) { null }
            if (!latin.isNullOrEmpty()) scanTextForPrompts(latin)?.let { return it }
        }
        kotlin.run {
            val utf8 = try { String(data, java.nio.charset.StandardCharsets.UTF_8) } catch (_: Exception) { null }
            if (!utf8.isNullOrEmpty()) scanTextForPrompts(utf8)?.let { return it }
        }
        return null
    }
}
