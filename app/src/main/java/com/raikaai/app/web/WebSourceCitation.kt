package com.raikaai.app.web

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI

/**
 * Structured citation representing an external web source referenced during an AI response.
 */
data class WebSourceCitation(
    val title: String,
    val url: String,
    val snippet: String = "",
    val index: Int = 1,
) {
    /**
     * Extracts a clean domain hostname (e.g. "wikipedia.org", "bbc.com") for badge display.
     */
    val domain: String
        get() = try {
            val host = URI(url).host?.removePrefix("www.")
            if (!host.isNullOrBlank()) host else "web"
        } catch (_: Exception) {
            "web"
        }

    fun toJson(): String {
        val obj = JsonObject().apply {
            addProperty("type", "web_source")
            addProperty("title", title)
            addProperty("url", url)
            addProperty("snippet", snippet)
            addProperty("index", index)
        }
        return obj.toString()
    }

    companion object {
        private val gson = Gson()

        /** Reads a string field without throwing on missing or non-string values. */
        private fun JsonObject.optString(key: String): String =
            runCatching {
                get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
            }.getOrDefault("")

        /** Reads an int field without throwing on missing or non-numeric values. */
        private fun JsonObject.optInt(key: String, default: Int): Int =
            runCatching {
                get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            }.getOrNull() ?: default

        /**
         * Parses a source string from ChatMessageEntry.sources.
         * Gracefully handles:
         * 1. Structured JSON formatted strings.
         * 2. Legacy "Web: Title" format strings.
         * 3. Plain URL strings or custom delimiters.
         */
        fun parse(raw: String, defaultIndex: Int = 1): WebSourceCitation? {
            val text = raw.trim()
            if (text.isBlank()) return null

            // 1. JSON format
            if (text.startsWith("{") && text.endsWith("}")) {
                return try {
                    val obj = gson.fromJson(text, JsonObject::class.java) ?: return null
                    val title = obj.optString("title")
                    val url = obj.optString("url")
                    val snippet = obj.optString("snippet")
                    val idx = obj.optInt("index", defaultIndex)
                    if (title.isNotBlank() || url.isNotBlank()) {
                        WebSourceCitation(
                            title = title.ifBlank { url },
                            url = url.ifBlank { "https://google.com/search?q=${java.net.URLEncoder.encode(title, "UTF-8")}" },
                            snippet = snippet,
                            index = idx,
                        )
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }

            // 2. Legacy "Web: Title" format
            if (text.startsWith("Web:", ignoreCase = true)) {
                val title = text.substring(4).trim()
                if (title.isNotBlank()) {
                    return WebSourceCitation(
                        title = title,
                        url = "https://www.google.com/search?q=${java.net.URLEncoder.encode(title, "UTF-8")}",
                        snippet = "",
                        index = defaultIndex,
                    )
                }
            }

            // 3. Direct URL format
            if (text.startsWith("http://") || text.startsWith("https://")) {
                return WebSourceCitation(
                    title = text,
                    url = text,
                    snippet = "",
                    index = defaultIndex,
                )
            }

            return null
        }
    }
}
