package com.raikaai.app.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.raikaai.app.util.KLog
import com.raikaai.app.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal MCP client speaking JSON-RPC 2.0 over Streamable HTTP
 * (plain HTTP POST, `Accept: application/json, text/event-stream`).
 *
 * Lifecycle per server: [initialize] once (captures the optional
 * `mcp-session-id`), then [listTools] / [callTool]. All calls run on
 * Dispatchers.IO and throw [McpException] on protocol/transport errors —
 * callers decide how to surface them.
 */
private const val EOF = "\u0000EOF"

class McpClient(
    private val config: McpServerConfig,
    private val stdioLauncher: ((String) -> Process)? = null,
) {
    // ── stdio transport (server runs inside Alpine) ──
    private var proc: Process? = null
    private var stdin: java.io.Writer? = null
    private val stdioRpcLock = Any()
    private val stdoutLines = java.util.concurrent.LinkedBlockingQueue<String>()
    private val stderrTail = ArrayDeque<String>()

    @Synchronized
    private fun ensureProcess(): Process {
        proc?.takeIf { it.isAlive }?.let { return it }
        val launcher = stdioLauncher ?: throw McpException("Linux environment unavailable for '${config.name}'")
        initialized = false
        stdoutLines.clear()
        val p = launcher(config.command)
        proc = p
        stdin = java.io.OutputStreamWriter(p.outputStream, Charsets.UTF_8)
        Thread({
            runCatching { p.inputStream.bufferedReader().forEachLine { stdoutLines.put(it) } }
            stdoutLines.put(EOF)
        }, "mcp-out-${config.name}").apply { isDaemon = true }.start()
        Thread({
            runCatching {
                p.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(stderrTail) {
                        stderrTail.addLast(line)
                        while (stderrTail.size > 20) stderrTail.removeFirst()
                    }
                }
            }
        }, "mcp-err-${config.name}").apply { isDaemon = true }.start()
        return p
    }

    private fun stderrHint(): String =
        synchronized(stderrTail) { stderrTail.takeLast(4).joinToString(" | ") }.take(400)

    @Synchronized
    private fun stdioSend(payload: String) {
        ensureProcess()
        val w = stdin ?: throw McpException("MCP server '${config.name}' is not running")
        w.write(payload)
        w.write("\n")
        w.flush()
    }

    /**
     * First-run installs (npx/uvx downloads) can take a while.
     * Serialized via [stdioRpcLock] so concurrent callers on the same stdio
     * server never steal or discard each other's responses from [stdoutLines].
     */
    private fun stdioRpc(id: Long, payload: String): JsonObject {
        synchronized(stdioRpcLock) {
            stdioSend(payload)
            val deadline = System.currentTimeMillis() + (if (initialized) 120_000 else 300_000)
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) throw McpException("MCP server '${config.name}' did not answer in time. ${stderrHint()}")
                val line = stdoutLines.poll(left, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                if (line == EOF) {
                    throw McpException("MCP server '${config.name}' exited. ${stderrHint()}".trim())
                }
                val obj = runCatching { JsonParser.parseString(line.trim()).asJsonObject }.getOrNull() ?: continue
                val respId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: continue
                if (respId == id) return obj
            }
        }
    }

    /** Stops a stdio server process. */
    fun close() {
        val p = proc
        proc = null
        stdin = null
        initialized = false
        runCatching { p?.destroy() }
        runCatching {
            if (p != null && p.isAlive) p.destroyForcibly()
        }
    }


    private val http = NetworkUtils.HttpClient.streamingClient
    private val idSeq = AtomicLong(1)
    private val initMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var sessionId: String? = null
    @Volatile private var initialized = false

    class McpException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private fun buildRequest(payload: String): Request {
        val builder =
            Request.Builder()
                .url(config.endpoint() ?: throw McpException("Invalid MCP endpoint URL"))
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
        if (config.authToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.authToken.trim()}")
        }
        sessionId?.let { builder.header("mcp-session-id", it) }
        return builder.build()
    }

    /** Sends one JSON-RPC request and returns the parsed `result` object. */
    private fun rpcSync(method: String, params: JsonObject?): JsonObject {
        val id = idSeq.getAndIncrement()
        val payload =
            buildString {
                append("{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"")
                append(method)
                append("\"")
                if (params != null) {
                    append(",\"params\":")
                    append(McpJson.gson.toJson(params))
                }
                append("}")
            }
        if (config.isStdio) {
            val envelope = stdioRpc(id, payload)
            envelope.getAsJsonObject("error")?.let { err ->
                throw McpException("MCP error from '${config.name}': ${err.get("message")?.asString ?: "unknown error"}")
            }
            return envelope.getAsJsonObject("result")
                ?: throw McpException("Missing result in MCP response from '${config.name}'")
        }
        http.newCall(buildRequest(payload)).execute().use { resp ->
            val newSession = resp.header("mcp-session-id")
            if (!newSession.isNullOrBlank()) sessionId = newSession
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw McpException("HTTP ${resp.code} from MCP server '${config.name}': ${body.take(300)}")
            }
            val envelope = extractJsonRpcBody(body)
                ?: throw McpException("Unrecognized response from MCP server '${config.name}'")
            envelope.getAsJsonObject("error")?.let { err ->
                val msg = err.get("message")?.asString ?: "unknown error"
                throw McpException("MCP error from '${config.name}': $msg")
            }
            return envelope.getAsJsonObject("result")
                ?: throw McpException("Missing result in MCP response from '${config.name}'")
        }
    }

    /**
     * Sends a JSON-RPC notification (no `id`, no response expected).
     * Used for spec-mandated notifications such as
     * `notifications/initialized`; any transport failure is swallowed
     * because notifications are fire-and-forget by design.
     */
    private fun notifySync(method: String, params: JsonObject?) {
        val payload =
            buildString {
                append("{\"jsonrpc\":\"2.0\",\"method\":\"")
                append(method)
                append("\"")
                if (params != null) {
                    append(",\"params\":")
                    append(McpJson.gson.toJson(params))
                }
                append("}")
            }
        if (config.isStdio) {
            runCatching { stdioSend(payload) }
            return
        }
        runCatching {
            http.newCall(buildRequest(payload)).execute().use { resp ->
                val newSession = resp.header("mcp-session-id")
                if (!newSession.isNullOrBlank()) sessionId = newSession
                // Drain the body; notifications expect no meaningful response.
                resp.body?.close()
            }
        }
    }

    /**
     * Extracts the JSON-RPC envelope from a response body that may be plain
     * JSON or a Server-Sent Events stream (`data: {...}` lines).
     * Internal visibility for unit tests.
     */
    internal fun extractJsonRpcBody(body: String): JsonObject? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            return runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull()
        }
        // SSE: take the last well-formed `data:` JSON object.
        var last: JsonObject? = null
        trimmed.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.startsWith("data:")) {
                val json = t.removePrefix("data:").trim()
                if (json.startsWith("{")) {
                    runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()?.let { last = it }
                }
            }
        }
        return last
    }

    /** Runs the MCP handshake. Idempotent per client instance. */
    suspend fun initialize() =
        withContext(Dispatchers.IO) {
            if (initialized) return@withContext
            initMutex.lock()
            try {
                if (initialized) return@withContext
                val params =
                    JsonObject().apply {
                        addProperty("protocolVersion", "2025-06-18")
                        add("capabilities", JsonObject())
                        add(
                            "clientInfo",
                            JsonObject().apply {
                                addProperty("name", "RaikaAI")
                                addProperty("version", "1.0")
                            },
                        )
                    }
                val result = rpcSync("initialize", params)
                val serverVersion = result.get("protocolVersion")?.takeIf { it.isJsonPrimitive }?.asString
                KLog.d("McpClient", "Initialized '${config.name}' (server protocol: $serverVersion)")
                // Notification per spec: no id, no response — fire and forget.
                notifySync("notifications/initialized", JsonObject())
                initialized = true
            } finally {
                initMutex.unlock()
            }
        }

    /** Returns the tools advertised by the server. */
    suspend fun listTools(): List<McpTool> =
        withContext(Dispatchers.IO) {
            initialize()
            val result = rpcSync("tools/list", null)
            val arr = result.getAsJsonArray("tools") ?: return@withContext emptyList<McpTool>()
            arr.mapNotNull { el ->
                runCatching {
                    val o = el.asJsonObject
                    McpTool(
                        serverId = config.id,
                        serverName = config.name,
                        name = o.get("name").asString,
                        description = o.get("description")?.asString.orEmpty(),
                        inputSchema = o.getAsJsonObject("inputSchema") ?: JsonObject(),
                    )
                }.getOrNull()
            }
        }

    /** Executes a tool call. Throws [McpException] on failure. */
    suspend fun callTool(
        toolName: String,
        arguments: JsonObject,
    ): McpCallResult =
        withContext(Dispatchers.IO) {
            initialize()
            val params =
                JsonObject().apply {
                    addProperty("name", toolName)
                    add("arguments", arguments)
                }
            val result = rpcSync("tools/call", params)
            val isError = result.get("isError")?.asBoolean == true
            val text =
                result.getAsJsonArray("content")?.mapNotNull { block ->
                    runCatching {
                        val o = block.asJsonObject
                        when (o.get("type")?.asString) {
                            "text" -> o.get("text")?.asString
                            "image" -> "[image: ${o.get("mimeType")?.asString ?: "unknown type"}]"
                            "resource" -> {
                                val r = o.getAsJsonObject("resource")
                                "[resource: ${r?.get("uri")?.asString ?: "?"}]\n${r?.get("text")?.asString.orEmpty()}"
                            }
                            else -> o.toString().take(2000)
                        }
                    }.getOrNull()
                }?.filter { it.isNotBlank() }?.joinToString("\n\n").orEmpty()
            McpCallResult(success = !isError, text = text.ifBlank { "(empty result)" }, isError = isError)
        }
}
