package com.raikaai.app.core.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Many models write tool calls in their own native markup instead of the
 * `<tool_call>{"name": …, "arguments": {…}}</tool_call>` RaikaAI asks for. Left
 * alone that text is shown raw and nothing runs. [convert] rewrites the popular
 * native formats into `<tool_call>` blocks:
 *
 *  - DeepSeek V3.2+ `<｜DSML｜invoke name="…">` and V3 `<｜tool▁calls▁begin｜>`
 *  - Claude style `<function_calls><invoke name="…"><parameter name="…">`
 *  - Qwen3-Coder / Llama `<function=name><parameter=key>value</parameter></function>`
 *  - GLM `<tool_call>name<arg_key>k</arg_key><arg_value>v</arg_value></tool_call>`
 *  - Llama 3.x `<|python_tag|>{"name": …, "parameters": …}` and a bare JSON reply
 *  - Mistral `[TOOL_CALLS][{…}]` and `[TOOL_CALLS]name[ARGS]{…}`
 *  - Kimi K2 `<|tool_call_begin|>functions.name:0<|tool_call_argument_begin|>{…}`
 *  - gpt-oss harmony `<|channel|>commentary to=functions.name … <|message|>{…}<|call|>`
 *
 * Hermes/Qwen `<tool_call>{json}</tool_call>` is already the app's own format and is left untouched.
 */
object NativeToolCalls {
    private const val BAR = "[｜|]"
    private const val SEP = "[▁_ ]"
    private val CI = RegexOption.IGNORE_CASE

    /** Prefix of DeepSeek's DSML tags; empty for the plain Claude-style tags. */
    private const val DSML = "(?:$BAR\\s*DSML\\s*$BAR\\s*)?"

    private val INVOKE = Regex("""<\s*${DSML}invoke\s+name\s*=\s*"([^"]+)"\s*>([\s\S]*?)<\s*/\s*${DSML}invoke\s*>""", CI)
    private val PARAM = Regex("""<\s*${DSML}parameter\s+name\s*=\s*"([^"]+)"(?:\s+string\s*=\s*"(true|false)")?\s*>([\s\S]*?)<\s*/\s*${DSML}parameter\s*>""", CI)
    private val WRAPPER = Regex("""<\s*/?\s*${DSML}function_calls\s*>""", CI)

    private val V3_CALL = Regex(
        """<\s*${BAR}tool${SEP}call${SEP}begin${BAR}\s*>\s*(?:function)?\s*<\s*${BAR}tool${SEP}sep${BAR}\s*>\s*([^\n<]+?)\s*\n\s*```(?:json)?\s*\n?([\s\S]*?)\s*```\s*<\s*${BAR}tool${SEP}call${SEP}end${BAR}\s*>""",
        CI,
    )
    private val V3_WRAPPER = Regex("""<\s*${BAR}tool${SEP}(?:calls|outputs)${SEP}(?:begin|end)${BAR}\s*>""", CI)

    private val FUNC = Regex("""(?:<tool_call>\s*)?<function=([^>\s]+)\s*>([\s\S]*?)</function>\s*(?:</tool_call>)?""", CI)
    private val FUNC_PARAM = Regex("""<parameter=([^>\s]+)\s*>([\s\S]*?)</parameter>""", CI)

    private val GLM = Regex(
        """<tool_call>\s*([A-Za-z_][\w.\-]*)\s*((?:<arg_key>[\s\S]*?</arg_key>\s*<arg_value>[\s\S]*?</arg_value>\s*)*)</tool_call>""",
        CI,
    )
    private val GLM_ARG = Regex("""<arg_key>([\s\S]*?)</arg_key>\s*<arg_value>([\s\S]*?)</arg_value>""", CI)

    private val KIMI_CALL = Regex(
        """<\|tool_call_begin\|>\s*([\w.\-]+(?::\d+)?)\s*<\|tool_call_argument_begin\|>([\s\S]*?)<\|tool_call_end\|>""",
        CI,
    )
    private val KIMI_WRAPPER = Regex("""<\|tool_calls_section_(?:begin|end)\|>""", CI)

    private val HARMONY = Regex(
        """<\|channel\|>\s*\w+\s+to=(?:functions\.)?([\w.\-]+)[^<]*?(?:<\|constrain\|>\s*\w+)?\s*<\|message\|>([\s\S]*?)<\|call\|>""",
        CI,
    )

    private val PYTHON_TAG = Regex("""<\|python_tag\|>\s*""", CI)
    private val MISTRAL_TAG = Regex("""\[TOOL_CALLS]\s*""")
    private val MISTRAL_ARGS = Regex("""([\w.\-]+)\s*\[ARGS]\s*""")
    private val STOP_TOKENS = Regex("""<\|(?:eom_id|eot_id|im_end|end_of_text)\|>""", CI)

    /** Where a native block begins; the stream is held back from here on. */
    val START = Regex(
        listOf(
            """<\s*${BAR}\s*DSML""",
            """<\s*${BAR}tool${SEP}calls${SEP}begin""",
            """<function_calls>""",
            """<invoke\s+name\s*=""",
            """<function=""",
            """<\|python_tag\|>""",
            """\[TOOL_CALLS]""",
            """<\|tool_calls_section_begin\|>""",
            """<\|channel\|>\s*\w+\s+to=""",
            // Hermes JSON ("{") and the app's own <name> form stay as they are.
            """<tool_call>\s*(?!\{|<name>)\S""",
        ).joinToString("|"),
        CI,
    )

    private val LITERAL_MARKERS =
        listOf(
            "<|DSML",
            "<|tool_calls_begin",
            "<function_calls>",
            "<invoke name=",
            "<function=",
            "<|python_tag|>",
            "[TOOL_CALLS]",
            "<|tool_calls_section_begin|>",
            "<|channel|>",
            "<tool_call>",
        )

    fun containsNative(text: String): Boolean = START.containsMatchIn(text) || asBareCall(text) != null

    /** Rewrites every complete native tool call in [text] into a `<tool_call>` block. */
    fun convert(text: String): String {
        if (!containsNative(text)) return text
        asBareCall(text)?.let { return it }
        var out = text
        out = INVOKE.replace(out) { m -> block(m.groupValues[1], paramsToJson(m.groupValues[2])) }
        out = WRAPPER.replace(out, "")
        out = V3_CALL.replace(out) { m -> block(m.groupValues[1].trim(), parseArgs(m.groupValues[2])) }
        out = V3_WRAPPER.replace(out, "")
        out = FUNC.replace(out) { m ->
            val body = m.groupValues[2]
            val params = FUNC_PARAM.findAll(body).toList()
            val args =
                if (params.isNotEmpty()) {
                    JsonObject().also { o -> params.forEach { o.addProperty(it.groupValues[1], it.groupValues[2].trim('\n', '\r')) } }
                } else {
                    parseArgs(body)
                }
            block(m.groupValues[1], args)
        }
        out = GLM.replace(out) { m ->
            val args = JsonObject()
            GLM_ARG.findAll(m.groupValues[2]).forEach { a -> args.add(a.groupValues[1].trim(), scalar(a.groupValues[2].trim())) }
            block(m.groupValues[1], args)
        }
        out = KIMI_CALL.replace(out) { m ->
            val name = m.groupValues[1].removePrefix("functions.").substringBefore(':')
            block(name, parseArgs(m.groupValues[2]))
        }
        out = KIMI_WRAPPER.replace(out, "")
        out = HARMONY.replace(out) { m -> block(m.groupValues[1], parseArgs(m.groupValues[2])) }
        out = convertPythonTag(out)
        out = convertMistral(out)
        return STOP_TOKENS.replace(out, "").trim()
    }

    // ── builders ─────────────────────────────────────────────

    private fun block(name: String, args: JsonElement): String {
        val call = JsonObject().apply {
            addProperty("name", name)
            add("arguments", if (args.isJsonObject) args else JsonObject())
        }
        return "<tool_call>$call</tool_call>"
    }

    private fun parseArgs(raw: String): JsonElement =
        runCatching { JsonParser.parseString(raw.trim()) }.getOrNull()?.takeIf { it.isJsonObject } ?: JsonObject()

    private fun paramsToJson(body: String): JsonObject {
        val args = JsonObject()
        PARAM.findAll(body).forEach { p ->
            val value = p.groupValues[3].trim('\n', '\r')
            args.add(p.groupValues[1], if (p.groupValues[2] == "false") scalar(value) else JsonPrimitive(value))
        }
        return args
    }

    /** A numeric/boolean/JSON literal keeps its type; anything else stays a string. */
    private fun scalar(raw: String): JsonElement =
        runCatching { JsonParser.parseString(raw.trim()) }.getOrNull()?.takeIf { !it.isJsonNull } ?: JsonPrimitive(raw)

    /** `{"name": …, "parameters"|"arguments": …}` → block; null when it is not a call object. */
    private fun objectToBlock(o: JsonObject): String? {
        val name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val args = o.get("arguments") ?: o.get("parameters") ?: JsonObject()
        val argObj =
            when {
                args.isJsonObject -> args
                args.isJsonPrimitive && args.asJsonPrimitive.isString -> parseArgs(args.asString)
                else -> JsonObject()
            }
        return block(name, argObj)
    }

    // ── Llama / Mistral scanners ─────────────────────────────

    /** End index (exclusive) of the balanced `{…}` or `[…]` starting at [start], or -1. */
    private fun balancedEnd(s: String, start: Int): Int {
        if (start >= s.length) return -1
        val open = s[start]
        val close = if (open == '{') '}' else if (open == '[') ']' else return -1
        var depth = 0
        var inString = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else if (c == '"') {
                inString = true
            } else if (c == open) {
                depth++
            } else if (c == close) {
                depth--
                if (depth == 0) return i + 1
            }
            i++
        }
        return -1
    }

    private fun convertPythonTag(text: String): String {
        val sb = StringBuilder()
        var pos = 0
        while (true) {
            val tag = PYTHON_TAG.find(text, pos) ?: break
            sb.append(text, pos, tag.range.first)
            var i = tag.range.last + 1
            var converted = false
            while (i < text.length) {
                while (i < text.length && (text[i].isWhitespace() || text[i] == ';')) i++
                if (i >= text.length || text[i] != '{') break
                val end = balancedEnd(text, i)
                if (end < 0) break
                val obj = runCatching { JsonParser.parseString(text.substring(i, end)) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                val b = obj?.let { objectToBlock(it) } ?: break
                sb.append(b)
                converted = true
                i = end
            }
            if (!converted) sb.append(text, tag.range.first, i)
            pos = i
        }
        sb.append(text, pos, text.length)
        return sb.toString()
    }

    private fun convertMistral(text: String): String {
        val sb = StringBuilder()
        var pos = 0
        while (true) {
            val tag = MISTRAL_TAG.find(text, pos) ?: break
            sb.append(text, pos, tag.range.first)
            var i = tag.range.last + 1
            var converted = false
            if (i < text.length && text[i] == '[') {
                val end = balancedEnd(text, i)
                val arr = if (end > 0) runCatching { JsonParser.parseString(text.substring(i, end)) }.getOrNull() else null
                if (arr != null && arr.isJsonArray) {
                    (arr as JsonArray).forEach { e -> if (e.isJsonObject) objectToBlock(e.asJsonObject)?.let { sb.append(it) } }
                    converted = true
                    i = end
                }
            } else {
                val named = MISTRAL_ARGS.find(text, i)?.takeIf { it.range.first == i }
                if (named != null) {
                    val s = named.range.last + 1
                    val end = balancedEnd(text, s)
                    if (end > 0) {
                        sb.append(block(named.groupValues[1], parseArgs(text.substring(s, end))))
                        converted = true
                        i = end
                    }
                }
            }
            if (!converted) sb.append(text, tag.range.first, i)
            pos = i
        }
        sb.append(text, pos, text.length)
        return sb.toString()
    }

    /** A whole reply that is one `{"name": …, "parameters": …}` object (Llama 3.x without the tag). */
    private fun asBareCall(text: String): String? {
        val t = text.trim()
        if (!t.startsWith("{") || !t.contains("\"name\"")) return null
        if (!(t.contains("\"parameters\"") || t.contains("\"arguments\""))) return null
        if (balancedEnd(t, 0) != t.length) return null
        val o = runCatching { JsonParser.parseString(t) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return objectToBlock(o)
    }

    // ── streaming ────────────────────────────────────────────

    /**
     * Passes text through, but once a native block starts it holds everything
     * back until the stream ends, then emits the converted `<tool_call>` text.
     * A trailing fragment that might still turn into a start marker is held for
     * one chunk, and a reply that begins with `{` is held in case it is a bare
     * JSON tool call.
     */
    class Filter {
        private var held = StringBuilder()
        private var inNative = false
        private var emittedAny = false
        private var decidedPlain = false

        fun push(delta: String): String {
            held.append(delta)
            if (inNative) return ""
            // A reply that opens with "{" may be a bare JSON call: decide at the end.
            if (!emittedAny && !decidedPlain) {
                val lead = held.toString().trimStart()
                if (lead.isEmpty()) return ""
                if (lead.startsWith("{")) {
                    inNative = true
                    return ""
                }
                decidedPlain = true
            }
            val m = START.find(held)
            if (m != null) {
                val before = held.substring(0, m.range.first)
                held = StringBuilder(held.substring(m.range.first))
                inNative = true
                if (before.isNotEmpty()) emittedAny = true
                return before
            }
            val keep = partialMarkerLength(held)
            val out = held.substring(0, held.length - keep)
            held = StringBuilder(held.substring(held.length - keep))
            if (out.isNotEmpty()) emittedAny = true
            return out
        }

        fun finish(): String {
            val rest = held.toString()
            held = StringBuilder()
            val wasNative = inNative
            inNative = false
            return if (wasNative) convert(rest) else rest
        }

        private fun partialMarkerLength(s: CharSequence): Int {
            val text = s.toString()
            // "<tool_call>" followed only by whitespace: the next character decides.
            Regex("""<tool_call>\s*$""", CI).find(text)?.let { return text.length - it.range.first }
            val maxLen = minOf(text.length, LITERAL_MARKERS.maxOf { it.length })
            for (len in maxLen downTo 1) {
                val tail = normalize(text.substring(text.length - len))
                if (tail.isEmpty() || (tail[0] != '<' && tail[0] != '[')) continue
                if (LITERAL_MARKERS.any { it.startsWith(tail, ignoreCase = true) }) return len
            }
            return 0
        }

        private fun normalize(s: String) = s.replace('｜', '|').replace('▁', '_')
    }
}
