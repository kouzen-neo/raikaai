package com.raikaai.app.mcp

import com.google.gson.JsonObject
import com.raikaai.app.util.orFallback
import java.util.UUID
import kotlin.jvm.Transient

/**
 * Configuration for one MCP (Model Context Protocol) server, persisted in
 * SettingsRepository as JSON.
 *
 * Transport: Streamable HTTP (JSON-RPC 2.0 over HTTP POST). [url] is the MCP
 * endpoint, e.g. `https://example.com/mcp` or `http://192.168.1.10:8000/mcp`.
 */
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    /**
     * Optional bearer token sent as `Authorization: Bearer …`.
     *
     * SECURITY: never persisted in the DataStore JSON — SettingsRepository
     * strips it on write and keeps it in EncryptedSharedPreferences, then
     * re-hydrates it on read. A non-blank value here only exists in memory.
     */
    val authToken: String = "",
    val enabled: Boolean = true,
    /**
     * Stdio server: shell command run inside the Alpine environment
     * (e.g. `npx -y @upstash/context7-mcp`). When set, [url] is unused.
     */
    val command: String = "",
    /** Added by Raika itself (add_mcp_server) rather than by hand. */
    val addedByAgent: Boolean = false,
    /**
     * True when a token is stored for this server. Never serialized
     * ([Transient]) — used by the UI to show "token saved" without
     * revealing the secret.
     */
    @Transient var hasToken: Boolean = false,
) {
    val isStdio: Boolean get() = (command as String?).orEmpty().isNotBlank()

    /** Where the server runs, for display. */
    val location: String get() = if (isStdio) "Linux · $command" else url

    /** Normalized endpoint URL, or null when blank/malformed. */
    fun endpoint(): String? {
        var u = (url as String?).orEmpty().trim()
        if (u.isBlank()) return null
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }
}

/**
 * Gson bypasses the Kotlin constructor: name/url/authToken can arrive as
 * JVM-null from hand-edited or legacy JSON, crashing url.trim() and friends.
 */
@Suppress("SENSELESS_COMPARISON")
internal fun McpServerConfig.normalized(): McpServerConfig {
    val safeUrl = (url as String?).orEmpty()
    return McpServerConfig(
        id = (id as String?).orFallback("mcp_" + System.currentTimeMillis()),
        name = (name as String?).orFallback(safeUrl.ifBlank { "MCP Server" }),
        url = safeUrl,
        authToken = (authToken as String?).orEmpty(),
        enabled = (enabled as Boolean?) ?: true,
        command = (command as String?).orEmpty(),
        addedByAgent = (addedByAgent as Boolean?) ?: false,
    ).also {
        it.hasToken = this.hasToken
    }
}

/** A tool advertised by an MCP server via `tools/list`. */
data class McpTool(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String = "",
    /** Raw JSON Schema of the tool's input. */
    val inputSchema: JsonObject = JsonObject(),
) {
    /** Short human-readable signature, e.g. `read_file(path: string, …)`. */
    val signature: String
        get() {
            val props = inputSchema.getAsJsonObject("properties")
            val required = inputSchema.getAsJsonArray("required")?.map { it.asString }?.toSet().orEmpty()
            val parts =
                props?.entrySet()?.map { (k, v) ->
                    val t = (v as? JsonObject)?.get("type")?.asString ?: "any"
                    val req = if (required.contains(k)) "" else "?"
                    "$k$req: $t"
                }.orEmpty()
            return "$name(${parts.joinToString(", ")})"
        }
}

/** A tool call parsed from model output, awaiting user confirmation. */
data class McpCallRequest(
    val serverId: String,
    val serverName: String,
    val toolName: String,
    /** Arguments as parsed from the model's `<tool_call>` JSON. */
    val arguments: JsonObject = JsonObject(),
) {
    val argumentsJson: String get() = McpJson.gson.toJson(arguments)
}

/** Result of an MCP `tools/call`. */
data class McpCallResult(
    val success: Boolean,
    /** Human-readable text extracted from the result content blocks. */
    val text: String,
    /** True when the server flagged the result as an error. */
    val isError: Boolean = false,
)
