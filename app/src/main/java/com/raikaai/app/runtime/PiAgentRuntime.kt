package com.raikaai.app.runtime

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * [AgentRuntime] backed by the Node adapter that runs Pi inside a KazeTerm
 * guest. The adapter exposes an event snapshot on `GET /events` and accepts a
 * prompt on `POST /v1/agent/prompt`; both are protected by a bearer token.
 *
 * This class only depends on `java.net.HttpURLConnection` and `org.json` so it
 * stays usable in JVM unit tests and free of Android UI types. The HTTP calls
 * are blocking; callers should invoke [runTurn] from a background dispatcher.
 */
class PiAgentRuntime(
    private val baseUrl: String,
    private val token: String,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) : AgentRuntime {

    /** Last event sequence consumed from the adapter's bounded snapshot. */
    private var lastSeq: Long = 0L

    /** Request an orderly abort of the active Pi turn. */
    suspend fun abort() {
        val root = normalizedBaseUrl()
        val url = "$root/v1/abort"
        val connection = openConnection(url, "POST")
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    "Pi adapter rejected abort at $url (HTTP $status): ${errorPreview(connection)}",
                )
            }
            connection.inputStream.use { it.readBytes() }
        } catch (error: IOException) {
            throw IllegalStateException("Could not abort Pi at $url: ${error.message}", error)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun runTurn(
        messages: List<AgentMessage>,
        onEvent: (RuntimeEvent) -> Unit,
    ): String {
        val root = normalizedBaseUrl()
        postPrompt(root, buildPromptBody(messages))
        val snapshot = fetchEvents(root, lastSeq)

        val reply = StringBuilder()
        for (index in 0 until snapshot.length()) {
            val raw = snapshot.optJSONObject(index)
                ?: throw IllegalStateException(
                    "Pi event snapshot contained a non-object entry at index $index",
                )
            lastSeq = maxOf(lastSeq, raw.optLong("seq", lastSeq))
            val event = translateEvent(raw) ?: continue
            onEvent(event)
            if (event is RuntimeEvent.AssistantDelta) {
                reply.append(event.text)
            }
        }
        return reply.toString()
    }

    private fun normalizedBaseUrl(): String {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) {
            throw IllegalStateException("Pi adapter baseUrl must not be blank")
        }
        return root
    }

    private fun postPrompt(root: String, body: String) {
        val url = "$root/v1/agent/prompt"
        val connection = openConnection(url, "POST")
        try {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    "Pi adapter rejected prompt at $url (HTTP $status): ${errorPreview(connection)}",
                )
            }
            // Drain the success body so the connection can be closed cleanly.
            connection.inputStream.use { it.readBytes() }
        } catch (error: IOException) {
            throw IllegalStateException(
                "Could not POST prompt to Pi adapter at $url: ${error.message}",
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchEvents(root: String, since: Long): JSONArray {
        val url = "$root/events?since=$since"
        val connection = openConnection(url, "GET")
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    "Pi adapter event snapshot unavailable at $url (HTTP $status): " +
                        errorPreview(connection),
                )
            }
            val payload = connection.inputStream.use {
                String(it.readBytes(), StandardCharsets.UTF_8)
            }
            return try {
                JSONArray(payload)
            } catch (error: JSONException) {
                throw IllegalStateException(
                    "Pi adapter returned a malformed event snapshot at $url: ${error.message}",
                    error,
                )
            }
        } catch (error: IOException) {
            throw IllegalStateException(
                "Could not read events from Pi adapter at $url: ${error.message}",
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as? HttpURLConnection
            ?: throw IllegalStateException("Unsupported connection type for Pi adapter URL: $url")
        return connection.apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Authorization", "Bearer $token")
            instanceFollowRedirects = false
        }
    }

    private fun errorPreview(connection: HttpURLConnection): String {
        val stream = connection.errorStream ?: return "no error body"
        return try {
            stream.use {
                val buffer = ByteArray(MAX_ERROR_PREVIEW_BYTES)
                val count = it.read(buffer)
                if (count <= 0) {
                    "empty error body"
                } else {
                    String(buffer, 0, count, StandardCharsets.UTF_8)
                        .replace(Regex("\\s+"), " ")
                        .take(MAX_ERROR_PREVIEW_BYTES)
                }
            }
        } catch (ignored: IOException) {
            "unreadable error body"
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS: Int = 10_000
        const val DEFAULT_READ_TIMEOUT_MS: Int = 30_000
        private const val MAX_ERROR_PREVIEW_BYTES: Int = 2_048
    }
}

private const val KEY_TYPE: String = "type"

/** Builds the OpenAI-shaped request body sent to the adapter. */
internal fun buildPromptBody(messages: List<AgentMessage>): String {
    val array = JSONArray()
    messages.forEach { message ->
        array.put(
            JSONObject()
                .put("role", message.role)
                .put("content", message.text),
        )
    }
    return JSONObject().put("messages", array).toString()
}

/**
 * Pure translation of one snapshot entry into a [RuntimeEvent]. Returns null
 * for recognized-but-ignored records (for example `settled`) and for unknown
 * types, so callers can skip them without special-casing.
 */
internal fun translateEvent(obj: JSONObject): RuntimeEvent? = when (obj.optString(KEY_TYPE, "")) {
    "assistant-stream" -> RuntimeEvent.AssistantDelta(obj.optString("delta", ""))
    "tool/call" -> RuntimeEvent.ToolCall(
        name = obj.optString("name", ""),
        argumentsJson = asJsonText(obj.opt("args")),
    )
    "approval/request" -> translateApproval(obj)
    "turn/end" -> RuntimeEvent.TurnEnd(translateTurnEndText(obj))
    else -> null
}

private fun translateApproval(obj: JSONObject): RuntimeEvent.ApprovalRequest {
    // The adapter nests the original Pi record under "request"; tolerate a
    // flat event too so hand-written snapshots keep working.
    val request = obj.optJSONObject("request") ?: obj
    val name = firstValue(request, "toolName", "name", "tool", "tool_name")
        ?.toString().orEmpty()
    val reason = firstValue(request, "reason", "description", "message")
        ?.toString().orEmpty()
    val args = firstValue(request, "args", "arguments", "input")
    val argumentsJson = when {
        args != null -> asJsonText(args)
        else -> {
            val command = firstValue(request, "command", "cmd")
            if (command != null) {
                JSONObject().put("command", command).toString()
            } else {
                "{}"
            }
        }
    }
    return RuntimeEvent.ApprovalRequest(name, argumentsJson, reason)
}

private fun translateTurnEndText(obj: JSONObject): String {
    val message = obj.optJSONObject("message")
    val content = when {
        message != null -> firstValue(message, "content", "text")
        else -> firstValue(obj, "text", "content")
    }
    return when (content) {
        is String -> content
        is JSONArray -> buildString {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                firstValue(block, "text", "content")?.let { append(it) }
            }
        }
        else -> content?.toString().orEmpty()
    }
}

private fun firstValue(obj: JSONObject, vararg keys: String): Any? {
    for (key in keys) {
        val value = obj.opt(key)
        if (value != null && value !== JSONObject.NULL) return value
    }
    return null
}

private fun asJsonText(value: Any?): String = when {
    value == null || value === JSONObject.NULL -> "{}"
    value is JSONObject || value is JSONArray -> value.toString()
    else -> value.toString()
}
