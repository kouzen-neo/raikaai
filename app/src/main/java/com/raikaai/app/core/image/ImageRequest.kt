package com.raikaai.app.core.image

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * One `generate_image` tool call, parsed. [prompts] holds one prompt per
 * picture (a single prompt repeated for `count` > 1), [caption] is the short
 * line shown with the pictures, and [self] says whether the picture shows the
 * active character (null when the model did not say; character chats then
 * send the reference anyway, since most requests are for the character).
 */
data class ImageRequest(
    val prompts: List<String>,
    val caption: String = "",
    val self: Boolean? = null,
) {
    companion object {
        const val MAX_IMAGES = 4

        /** Parses tool arguments; returns null when there is nothing to draw. Pure, unit tested. */
        fun fromArgs(args: JsonObject): ImageRequest? {
            fun str(key: String): String =
                args.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

            val list = args.get("prompts")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { el: JsonElement -> el.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null } }
                .orEmpty()
            val single = str("prompt").ifBlank { str("description") }.ifBlank { str("query") }
            val count = args.get("count")?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asInt }.getOrNull() }
                ?.coerceIn(1, MAX_IMAGES) ?: 1
            val prompts = when {
                list.isNotEmpty() -> list.take(MAX_IMAGES)
                single.isNotBlank() -> List(count) { single }
                else -> return null
            }
            val self = args.get("self")?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asBoolean }.getOrNull() }
            return ImageRequest(prompts, caption = str("caption"), self = self)
        }
    }
}

/** Prompt text for the image tool and for the image model; pure, unit tested. */
object ImagePrompt {
    /** The tool description added to the system prompt. */
    fun toolBlock(characterName: String? = null): String = buildString {
        append("\n\n[IMAGE TOOL]\n")
        append("You can send pictures. When the user asks for a picture, drawing, photo or selfie, reply with one tool call and nothing else:\n")
        append("<tool_call>{\"name\": \"generate_image\", \"arguments\": {\"prompt\": \"<detailed English description of the whole picture>\", \"count\": 1, \"caption\": \"<one short line to send with it, in the user's language>\"")
        if (characterName != null) append(", \"self\": true")
        append("}}</tool_call>\n")
        append("- count: how many pictures (1-${ImageRequest.MAX_IMAGES}). Use it when the user asks for several; for different scenes use \"prompts\": [\"...\", \"...\"] instead of prompt.\n")
        if (characterName != null) {
            append("- self: true when the picture shows you ($characterName), false when it doesn't (a landscape, food, an object). Your avatar is then sent as the reference, so describe your look exactly as your character card says (hair colour and style, eyes, outfit). Never guess it from the name.\n")
            append("- caption: speak in character.\n")
            append("- Sending pictures is allowed even in Roleplay mode.\n")
        }
    }

    /** Wraps a prompt for the image model when the avatar is sent as a reference. */
    fun withReference(prompt: String, characterName: String): String =
        "The reference image(s) show $characterName. Draw exactly this character: same face, hair colour, " +
            "hairstyle, eye colour and outfit as the reference. If the description below disagrees with the " +
            "reference about how $characterName looks, follow the reference. Scene: $prompt"

    /** What the chat model sees later in history instead of the pictures. */
    fun historyNote(count: Int, prompts: String): String =
        "[Sent ${if (count == 1) "a picture" else "$count pictures"}: $prompts]"
}

/** Finds the picture in an images or chat-completions response; pure, unit tested. */
object ImageResponse {
    private val DATA_URL = Regex("""data:image/[a-zA-Z+.-]+;base64,[A-Za-z0-9+/=\s]+""")
    private val MD_IMAGE = Regex("""!\[[^\]]*]\((https?://[^)\s]+)\)""")

    /**
     * Returns a `data:image/...;base64,` URL or an http(s) URL, or null when
     * the response carries no picture (for example a text-only reply).
     */
    fun find(body: String): String? {
        val root = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return null
        root.optJSONArray("data")?.optJSONObject(0)?.let { d ->
            d.optString("b64_json").takeIf { it.isNotBlank() }?.let { return "data:image/png;base64,$it" }
            d.optString("url").takeIf { it.isNotBlank() }?.let { return it }
        }
        val msg = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: return null
        msg.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                urlOf(o)?.let { return it }
            }
        }
        val content = msg.opt("content")
        if (content is org.json.JSONArray) {
            for (i in 0 until content.length()) {
                val part = content.optJSONObject(i) ?: continue
                urlOf(part)?.let { return it }
                part.optString("text").let { t -> fromText(t)?.let { return it } }
            }
        } else if (content is String) {
            fromText(content)?.let { return it }
        }
        return null
    }

    private fun urlOf(o: org.json.JSONObject): String? {
        o.optJSONObject("image_url")?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        o.optString("image_url").takeIf { it.startsWith("data:") || it.startsWith("http") }?.let { return it }
        o.optString("b64_json").takeIf { it.isNotBlank() }?.let { return "data:image/png;base64,$it" }
        o.optJSONObject("inline_data")?.optString("data")?.takeIf { it.isNotBlank() }?.let {
            return "data:image/png;base64,$it"
        }
        return null
    }

    private fun fromText(t: String): String? =
        DATA_URL.find(t)?.value?.filterNot { it.isWhitespace() } ?: MD_IMAGE.find(t)?.groupValues?.get(1)
}
