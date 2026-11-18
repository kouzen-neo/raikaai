package com.raikaai.app.skills

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.regex.Pattern

data class ParsedToolCall(
    val name: String,
    val query: String,
    val rawCallText: String,
    val lang: String = if (name.contains("en", ignoreCase = true) || name.contains("global", ignoreCase = true)) "en" else "id",
)

object ToolCallParser {
    private val gson = com.google.gson.GsonBuilder()
        .setStrictness(com.google.gson.Strictness.LENIENT)
        .create()
    private val SEARCH_FN_REGEX = Pattern.compile(
        "(?<![\\w])(?:call:)?(web_search(?:_id|_en|_indonesia|_global)?)\\s*\\(\\s*query\\s*=\\s*[\"']([^\"']+)[\"']\\s*\\)",
        Pattern.CASE_INSENSITIVE,
    )
    private val JSON_NAME_PATTERN = Pattern.compile(
        "\"name\"\\s*:\\s*\"web_search(?:_id|_en|_indonesia|_global)?\"",
        Pattern.CASE_INSENSITIVE,
    )

    /**
     * Extracts a tool call from the generated text if present.
     *
     * Iteration contract: this parser is stateless and never loops by itself.
     * Bounding the agentic loop is the caller's responsibility.
     * ChatViewModel.handleAutonomousToolCall() performs exactly ONE tool-call
     * round per generation (parse -> web search -> follow-up stream) and does
     * not re-parse the follow-up, so a runaway model cannot trigger unbounded
     * searches. If multi-step looping is added elsewhere, cap it (e.g. max 3
     * iterations) and stop when no new tool call is produced.
     */
    fun parse(text: String): ParsedToolCall? {
        if (text.isBlank()) return null

        // 1. Check for XML tag: <tool_call>...</tool_call> or <action>...</action>
        val xmlPattern = Pattern.compile(
            "<(?:tool_call|action)(?:\\s+[^>]*)?>([\\s\\S]*?)(?:<\\/(?:tool_call|action)>|$)",
            Pattern.CASE_INSENSITIVE,
        )
        val xmlMatcher = xmlPattern.matcher(text)
        if (xmlMatcher.find()) {
            val body = xmlMatcher.group(1).orEmpty().trim()
            val parsed = parseJsonOrFunction(body, xmlMatcher.group(0).orEmpty())
            if (parsed != null) return parsed
        }

        // 2. Check for Markdown code block: ```tool_call ... ```
        val mdPattern = Pattern.compile(
            "```(?:tool_call|tool)(?:\\s+([\\s\\S]*?))?(?:```|$)",
            Pattern.CASE_INSENSITIVE,
        )
        val mdMatcher = mdPattern.matcher(text)
        if (mdMatcher.find()) {
            val body = mdMatcher.group(1).orEmpty().trim()
            val parsed = parseJsonOrFunction(body, mdMatcher.group(0).orEmpty())
            if (parsed != null) return parsed
        }

        // 3. Check for direct JSON tool call: {"name": "web_search...", ...}
        // Balanced-brace scan: a lazy `.*?}` would stop at the first inner
        // brace and truncate nested "arguments" objects, producing invalid JSON.
        if ((text.contains("\"web_search\"") || text.contains("\"web_search_id\"") || text.contains("\"web_search_en\"")) && text.contains("query")) {
            val nameMatcher = JSON_NAME_PATTERN.matcher(text)
            if (nameMatcher.find()) {
                extractBalancedJson(text, nameMatcher.start())?.let { jsonStr ->
                    parseJson(jsonStr, jsonStr)?.let { return it }
                }
            }
        }

        // 4. Check for function style: web_search(query="...") or call:web_search(...)
        val fnMatcher = SEARCH_FN_REGEX.matcher(text)
        if (fnMatcher.find()) {
            val name = fnMatcher.group(1).orEmpty().trim()
            val query = fnMatcher.group(2).orEmpty().trim()
            if (query.isNotBlank()) {
                return ParsedToolCall(name, query, fnMatcher.group(0).orEmpty())
            }
        }

        return null
    }

    private fun parseJsonOrFunction(body: String, rawText: String): ParsedToolCall? {
        val fromJson = parseJson(body, rawText)
        if (fromJson != null) return fromJson

        val fnMatcher = SEARCH_FN_REGEX.matcher(body)
        if (fnMatcher.find()) {
            val name = fnMatcher.group(1).orEmpty().trim()
            val q = fnMatcher.group(2).orEmpty().trim()
            if (q.isNotBlank()) {
                return ParsedToolCall(name, q, rawText)
            }
        }
        return null
    }

    private fun parseJson(jsonStr: String, rawText: String): ParsedToolCall? {
        return try {
            val obj = gson.fromJson(jsonStr, JsonObject::class.java) ?: return null
            val name = obj.optString("name")
            if (!name.startsWith("web_search", ignoreCase = true)) return null

            val args = obj.get("arguments")
            val query = when {
                args != null && args.isJsonObject -> args.asJsonObject.optString("query")
                args != null && args.isJsonPrimitive && args.asJsonPrimitive.isString -> args.asString
                else -> obj.optString("query")
            }

            if (query.isNotBlank()) {
                ParsedToolCall(name, query.trim(), rawText)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts the first balanced `{...}` JSON object surrounding the match at
     * [fromIndex] (the `"name"` key sits *inside* the object, so the opening
     * brace is searched backwards). String literals (with escapes) are
     * respected so braces inside quoted values don't break the scan. Returns
     * null when no balanced object is found within a sane bound.
     */
    private fun extractBalancedJson(text: String, fromIndex: Int): String? {
        var i = fromIndex
        while (i >= 0 && text[i] != '{') i--
        if (i < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        val sb = StringBuilder()
        for (j in i until text.length) {
            val c = text[j]
            sb.append(c)
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return sb.toString()
                    }
                }
            }
            // Safety bound: a single tool call never needs more than this.
            if (sb.length > com.raikaai.app.data.AppLimits.TOOL_JSON_MAX_CHARS) return null
        }
        return null
    }

    private fun JsonObject.optString(key: String): String =
        runCatching {
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
        }.getOrDefault("")

    // ── Generic MCP tool-call parsing ────────────────────────────────

    /**
     * A tool call parsed from model output that is NOT a built-in web_search:
     * candidate for MCP dispatch. [arguments] is the raw `arguments` object
     * (empty when the model emitted none).
     */
    data class GenericToolCall(
        val name: String,
        val arguments: JsonObject,
        val rawCallText: String,
    )

    private val MCP_NAME_PATTERN = Pattern.compile(
        "\"name\"\\s*:\\s*\"([A-Za-z][A-Za-z0-9_.-]{0,127})\"",
    )

    /**
     * Extracts a generic `<tool_call>{"name": "...", "arguments": {...}}</tool_call>`
     * (or markdown-fenced / bare-JSON equivalent) from the text. Returns null
     * when the call targets a built-in `web_search*` tool — those keep flowing
     * through [parse].
     */
    fun parseGenericCall(text: String): GenericToolCall? {
        if (text.isBlank()) return null
        val clean = text
            .replace(Regex("<think(?:\\s+[^>]*)?>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<thought(?:\\s+[^>]*)?>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<thinking(?:\\s+[^>]*)?>[\\s\\S]*?</thinking>", RegexOption.IGNORE_CASE), "")
            .trim()
        // Thinking models sometimes emit the call INSIDE their thinking block.
        // Prefer calls outside thinking (a think-block call is often a
        // discarded draft), but fall back to raw text so those still execute
        // instead of dying as an "empty response".
        for (targetText in listOf(clean, text).distinct()) {
            collectBodies(targetText)?.let { return it }
        }
        return null
    }

    private fun collectBodies(targetText: String): GenericToolCall? {
        if (targetText.isBlank()) return null
        val bodies = mutableListOf<Pair<String, String>>()

        val xmlMatcher = Pattern.compile(
            "<(?:tool_call|action)(?:\\s+[^>]*)?>([\\s\\S]*?)(?:<\\/(?:tool_call|action)>|$)",
            Pattern.CASE_INSENSITIVE,
        ).matcher(targetText)
        while (xmlMatcher.find()) {
            val content = xmlMatcher.group(1).orEmpty().trim()
            if (content.isNotBlank()) {
                bodies.add(content to xmlMatcher.group(0).orEmpty())
            }
        }

        val mdMatcher = Pattern.compile(
            "```(?:tool_call|tool|xml)(?:\\s+([\\s\\S]*?))?(?:```|$)",
            Pattern.CASE_INSENSITIVE,
        ).matcher(targetText)
        while (mdMatcher.find()) {
            val content = mdMatcher.group(1).orEmpty().trim()
            if (content.isNotBlank()) {
                bodies.add(content to mdMatcher.group(0).orEmpty())
            }
        }

        for ((body, raw) in bodies) {
            parseGenericFirst(body, raw)?.let { return it }
        }

        // Bare JSON object containing a "name" key.
        val nameMatcher = MCP_NAME_PATTERN.matcher(targetText)
        while (nameMatcher.find()) {
            extractBalancedJson(targetText, nameMatcher.start())?.let { jsonStr ->
                parseGenericFirst(jsonStr, jsonStr)?.let { return it }
            }
        }
        return null
    }

    private fun parseGenericFirst(body: String, raw: String): GenericToolCall? =
        parseGenericJson(body, raw)
            ?: parseGenericXml(body, raw)
            ?: parseGenericJsonLenient(body, raw)

    /**
     * Lenient XML shape some models emit instead of JSON:
     * `<tool_call><name>run_command</name><arguments><command>…</command></arguments></tool_call>`.
     * Only triggers on an explicit `<name>` tag, so prose mentioning tags is ignored.
     */
    private fun parseGenericXml(body: String, rawText: String): GenericToolCall? {
        val name = Regex("<name>([\\s\\S]*?)</name>", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isBlank() || name.startsWith("web_search", ignoreCase = true)) return null
        val scope = Regex("<arguments>([\\s\\S]*?)</arguments>", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1) ?: body
        val args = JsonObject()
        val tagPattern = Regex("<([A-Za-z][A-Za-z0-9_.-]*)>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
        for (m in tagPattern.findAll(scope)) {
            val key = m.groupValues[1]
            if (key.equals("name", ignoreCase = true) ||
                key.equals("arguments", ignoreCase = true) ||
                key.equals("tool_call", ignoreCase = true)
            ) continue
            val value = m.groupValues[2].trim().unescapeXml()
            if (value.isNotEmpty() && !args.has(key)) args.addProperty(key, value)
        }
        return GenericToolCall(name, args, rawText)
    }

    private fun String.unescapeXml(): String {
        var s = this
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) {
            s = s.removePrefix("<![CDATA[").removeSuffix("]]>")
        }
        return s.replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    /**
     * Last resort for model-mangled JSON: literal newlines inside strings,
     * over-escaped quotes (`\"`), missing closing braces. Extracts
     * `"name"` plus flat `"key": "string"` pairs with a tolerant regex.
     * Only reached on text already shaped like a tool call, so prose with
     * quoted words can't trigger it.
     */
    private fun parseGenericJsonLenient(jsonStr: String, rawText: String): GenericToolCall? {
        val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(jsonStr)
            ?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isBlank() || name.startsWith("web_search", ignoreCase = true)) return null
        val args = JsonObject()
        val pairPattern = Regex("\"([A-Za-z0-9_.-]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", RegexOption.DOT_MATCHES_ALL)
        for (m in pairPattern.findAll(jsonStr)) {
            val key = m.groupValues[1]
            if (key.equals("name", ignoreCase = true)) continue
            val value = m.groupValues[2]
                .replace("\\\\", "\u0000")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\u0000", "\\")
            if (!args.has(key)) args.addProperty(key, value)
        }
        return GenericToolCall(name, args, rawText)
    }

    private fun parseGenericJson(jsonStr: String, rawText: String): GenericToolCall? {
        return try {
            val obj = gson.fromJson(jsonStr, JsonObject::class.java) ?: return null
            val name = obj.optString("name")
            if (name.isBlank() || name.startsWith("web_search", ignoreCase = true)) return null
            val args = obj.get("arguments")
            val arguments = when {
                args != null && args.isJsonObject -> args.asJsonObject
                args != null && args.isJsonPrimitive && args.asJsonPrimitive.isString -> {
                    // Lenient: some models emit arguments as a JSON-encoded string.
                    runCatching { gson.fromJson(args.asString, JsonObject::class.java) }.getOrNull() ?: JsonObject()
                }
                else -> JsonObject()
            }
            GenericToolCall(name, arguments, rawText)
        } catch (_: Exception) {
            null
        }
    }
}
