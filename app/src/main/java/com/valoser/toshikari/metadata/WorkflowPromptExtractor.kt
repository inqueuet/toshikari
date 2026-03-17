package com.valoser.toshikari.metadata

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlin.math.floor
import kotlin.math.min

/**
 * ComfyUI ワークフロー JSON / プロンプト JSON からプロンプト文字列を抽出する。
 *
 * CLIPTextEncode ノードの Positive タイトルを優先し、
 * ヒューリスティックにスコアリングして最良の候補を返す。
 */
internal object WorkflowPromptExtractor {

    private val GSON = Gson()

    fun parsePromptJson(jsonCandidate: String): String? {
        return try {
            if (jsonCandidate.startsWith("\"") && jsonCandidate.endsWith("\"")) {
                val unescaped = GSON.fromJson(jsonCandidate, String::class.java)
                val map = GSON.fromJson<Map<String, Any>>(unescaped, object : TypeToken<Map<String, Any>>() {}.type)
                extractDataFromMap(map)
            } else {
                val map = GSON.fromJson<Map<String, Any>>(jsonCandidate, object : TypeToken<Map<String, Any>>() {}.type)
                extractDataFromMap(map)
            }
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    fun parseWorkflowJson(jsonCandidate: String): String? {
        return try {
            val map = GSON.fromJson<Map<String, Any>>(jsonCandidate, object : TypeToken<Map<String, Any>>() {}.type)
            extractDataFromMap(map)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun extractDataFromMap(dataMap: Map<String, Any>): String? {
        @Suppress("UNCHECKED_CAST")
        val nodes = dataMap["nodes"] as? List<Map<String, Any>>
        if (nodes != null) {
            return pickFromNodes(nodes) ?: scanHeuristically(dataMap)
        }
        return scanHeuristically(dataMap)
    }

    /**
     * 文字列がラベルのみ（短すぎる/TxtEmb等の特殊マーカー）かどうかを判定する。
     */
    fun isLabely(text: String?): Boolean {
        val t = text?.trim() ?: return false
        return t.matches(Regex("^(TxtEmb|TextEmb)", RegexOption.IGNORE_CASE)) ||
                (!t.contains(Regex("""\s""")) && t.length < 24)
    }

    private fun bestStrFromInputs(inputs: Any?): String? {
        if (inputs !is Map<*, *>) return null
        val priorityKeys = listOf("populated_text", "wildcard_text", "prompt", "positive_prompt", "result", "text", "string", "value")
        for (key in priorityKeys) {
            val value = inputs[key]
            if (value is String && value.trim().isNotEmpty()) {
                return value.trim()
            }
        }
        var best: String? = null
        for ((_, value) in inputs) {
            if (value is String && value.trim().isNotEmpty()) {
                if (best == null || value.length > best.length) {
                    best = value
                }
            }
        }
        return best?.trim()
    }

    private fun pickFromNodes(nodes: List<Map<String, Any>>): String? {
        val nodeMap: Map<String, Map<String, Any>> =
            nodes.mapNotNull { node ->
                val id = node["id"]?.toString()
                if (id.isNullOrEmpty()) null else id to node
            }.toMap()

        fun resolveNode(node: Map<String, Any>?, depth: Int = 0): String? {
            if (node == null || depth > 4) return null

            val inputs = node["inputs"]
            var s = bestStrFromInputs(inputs)
            if (s != null && s.isNotEmpty() && !isLabely(s)) return s

            if (inputs is Map<*, *>) {
                for ((_, value) in inputs) {
                    if (value is List<*> && value.isNotEmpty()) {
                        val linkedNodeId = value[0]?.toString()
                        val linkedNode = if (linkedNodeId != null) nodeMap[linkedNodeId] else null
                        val r = resolveNode(linkedNode, depth + 1)
                        if (r != null && !isLabely(r)) return r
                    } else if (value is String && value.trim().isNotEmpty() && !isLabely(value)) {
                        return value.trim()
                    }
                }
            }

            val widgetsValues = node["widgets_values"] as? List<*>
            if (widgetsValues != null) {
                for (v in widgetsValues) {
                    if (v is String && v.trim().isNotEmpty() && !isLabely(v)) return v.trim()
                }
            }
            return null
        }

        val specificChecks = listOf(
            "ImpactWildcardProcessor",
            "WanVideoTextEncodeSingle",
            "WanVideoTextEncode"
        )
        for (typePattern in specificChecks) {
            for (node in nodes) {
                val nodeType = node["type"] as? String ?: node["class_type"] as? String ?: ""
                if (nodeType.contains(typePattern, ignoreCase = true)) {
                    val s = resolveNode(node)
                    if (s != null && s.isNotEmpty()) return s
                }
            }
        }

        for (node in nodes) {
            val nodeType = node["type"] as? String ?: node["class_type"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val title = node["title"] as? String ?: (node["_meta"] as? Map<String, String>)?.get("title") ?: ""
            if (nodeType.contains("CLIPTextEncode", ignoreCase = true) && title.contains("Positive", ignoreCase = true) && !title.contains("Negative", ignoreCase = true)) {
                var s = bestStrFromInputs(node["inputs"])
                if (s.isNullOrEmpty() && node["widgets_values"] is List<*>) {
                    s = (node["widgets_values"] as List<*>).getOrNull(0) as? String
                }
                if (s != null && s.trim().isNotEmpty() && !isLabely(s)) return s.trim()
            }
        }
        for (node in nodes) {
            @Suppress("UNCHECKED_CAST")
            val title = node["title"] as? String ?: (node["_meta"] as? Map<String, String>)?.get("title") ?: ""
            if (Regex("PointMosaic|Mosaic|Mask|TxtEmb|TextEmb", RegexOption.IGNORE_CASE).containsMatchIn(title)) continue

            var s = bestStrFromInputs(node["inputs"])
            if (s.isNullOrEmpty() && node["widgets_values"] is List<*>) {
                s = (node["widgets_values"] as List<*>).getOrNull(0) as? String
            }
            if (s != null && s.trim().isNotEmpty() && !isLabely(s) && title.contains("Positive", ignoreCase = true) && !title.contains("Negative", ignoreCase = true)) return s.trim()
        }
        return null
    }

    private fun scanHeuristically(obj: Map<String, Any>): String? {
        val EX_T = Regex("PointMosaic|Mosaic|Mask|TxtEmb|TextEmb", RegexOption.IGNORE_CASE)
        val EX_C = Regex("ShowText|Display|Note|Preview|VHS_|Image|Resize|Seed|INTConstant|SimpleMath|Any Switch|StringConstant(?!Multiline)", RegexOption.IGNORE_CASE)
        var best: String? = null
        var maxScore = -1_000_000_000.0
        val stack = mutableListOf<Any>(obj)

        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.size - 1)
            if (current !is Map<*, *>) continue
            @Suppress("UNCHECKED_CAST")
            val currentMap = current as Map<String, Any>

            val classType = currentMap["class_type"] as? String ?: currentMap["type"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val meta = currentMap["_meta"] as? Map<String, Any>
            val title = meta?.get("title") as? String ?: currentMap["title"] as? String ?: ""

            var v = bestStrFromInputs(currentMap["inputs"])
            if (v.isNullOrEmpty()) {
                val widgetsValues = currentMap["widgets_values"] as? List<*>
                if (widgetsValues != null && widgetsValues.isNotEmpty()) v = widgetsValues[0] as? String
            }

            if (v is String && v.trim().isNotEmpty()) {
                var score = 0.0
                if (title.contains("Positive", ignoreCase = true)) score += 1000
                if (title.contains("Negative", ignoreCase = true)) score -= 1000
                if (classType.contains("TextEncode", ignoreCase = true) || classType.contains("CLIPText", ignoreCase = true)) score += 120
                if (classType.contains("ImpactWildcardProcessor", ignoreCase = true) || classType.contains("WanVideoTextEncodeSingle", ignoreCase = true)) score += 300
                score += min(220.0, floor(v.length / 8.0))
                if (EX_T.containsMatchIn(title) || EX_T.containsMatchIn(classType)) score -= 900
                if (EX_C.containsMatchIn(classType)) score -= 400
                if (isLabely(v)) score -= 500
                if (score > maxScore) { maxScore = score; best = v.trim() }
            }

            currentMap.values.forEach { value ->
                if (value is Map<*, *> || value is List<*>) stack.add(value)
            }
        }
        return best
    }
}
