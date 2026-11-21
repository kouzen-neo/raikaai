package com.raikaai.app.core.server

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.raikaai.app.core.engine.LiteRtEngineManager
import com.raikaai.app.util.KLog
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Embedded OpenAI-compatible HTTP server (Ktor CIO).
 *
 * Endpoints:
 *   GET  /v1/models              -> list the active model
 *   POST /v1/chat/completions    -> stream:true = SSE, stream:false = JSON
 *   GET  /health                 -> "ok"
 *
 * All generation goes through [LiteRtEngineManager], whose mutex serializes
 * concurrent requests from multiple clients.
 */
object OpenAiServer {
    // ── Rate Limiting ────────────────────────────────────────────────
    private data class RateLimitEntry(
        val count: Int = 1,
        val windowStart: Long = System.currentTimeMillis(),
    )

    private val rateLimits = ConcurrentHashMap<String, RateLimitEntry>()
    private const val RATE_LIMIT_MAX_REQUESTS = 10
    private const val RATE_LIMIT_WINDOW_MS = 60_000L // 1 minute
    private val lastRateLimitCleanup = AtomicLong(0L)

    /**
     * Client IP taken from the actual TCP connection.
     * Never trust X-Forwarded-For here: on a direct LAN connection no proxy
     * sets it, and a client-supplied value is trivially spoofable — one client
     * could evade the limit or poison another client's bucket.
     */
    private fun ApplicationCall.remoteIp(): String = request.origin.remoteHost

    /** Returns true if the request is allowed, false if rate limited. */
    private fun checkRateLimit(clientIp: String): Boolean {
        val now = System.currentTimeMillis()
        // Lazy eviction: drop expired windows at most once per window so the
        // map cannot grow unboundedly from rotating/spoofed client IPs.
        val lastCleanup = lastRateLimitCleanup.get()
        if (now - lastCleanup > RATE_LIMIT_WINDOW_MS &&
            lastRateLimitCleanup.compareAndSet(lastCleanup, now)
        ) {
            rateLimits.entries.removeIf { now - it.value.windowStart > RATE_LIMIT_WINDOW_MS }
        }
        // NB: copy() instead of mutating — compute()'s lambda may be retried.
        val entry =
            rateLimits.compute(clientIp) { _, existing ->
                if (existing == null || now - existing.windowStart > RATE_LIMIT_WINDOW_MS) {
                    RateLimitEntry(1, now)
                } else {
                    existing.copy(count = existing.count + 1)
                }
            } ?: return true
        return entry.count <= RATE_LIMIT_MAX_REQUESTS
    }
    sealed class Status {
        data object Stopped : Status()

        data object Starting : Status()

        data class Running(
            val port: Int,
            val bindAll: Boolean,
        ) : Status()

        data class Error(
            val message: String,
        ) : Status()
    }

    data class ServerRequestLog(
        val id: String,
        val timestamp: Long = System.currentTimeMillis(),
        val method: String,
        val path: String,
        val clientIp: String,
        val status: Int,
        val durationMs: Long,
        val stream: Boolean,
    )

    private val _recentLogs = MutableStateFlow<List<ServerRequestLog>>(emptyList())
    val recentLogs: StateFlow<List<ServerRequestLog>> = _recentLogs.asStateFlow()

    private fun logRequest(entry: ServerRequestLog) {
        _recentLogs.update { list ->
            (if (list.size >= 25) list.drop(1) else list) + entry
        }
    }

    /** Everything the routes need, resolved once at startup. */
    data class Config(
        val port: Int,
        val bindAll: Boolean,
        val authToken: String,
        val systemPrompt: String,
        val temperature: Float,
        val topK: Int,
        val topP: Float = 0.90f,
        val maxTokens: Int,
        val backend: String,
        val modelPath: String,
        /**
         * Single context-window source of truth, shared by [PromptBuilder]
         * truncation and [LiteRtEngineManager.EngineParams] (B4).
         */
        val contextWindow: Int = 2048,
        val enableThinking: Boolean = false,
    )

    private val gson = Gson()
    private var server: EmbeddedServer<*, *>? = null

    /** Guards start()/stop() so two embedded servers can never be created (B19). */
    private val lifecycleLock = Any()

    /**
     * Shared bearer-token check; true when the request may proceed.
     * No token configured = open access (unchanged legacy behavior).
     * Uses constant-time comparison so the token can't be probed byte-wise.
     */
    private suspend fun ApplicationCall.checkAuth(config: Config): Boolean {
        val token = config.authToken
        if (token.isNotBlank()) {
            val provided = request.header("Authorization")?.removePrefix("Bearer ")?.trim().orEmpty()
            if (!MessageDigest.isEqual(provided.toByteArray(), token.toByteArray())) {
                respondText(
                    errorJson("invalid_api_key", "Invalid or missing API key"),
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized,
                )
                return false
            }
        }
        return true
    }

    /**
     * Browser cross-origin guard: a request carrying an `Origin` header comes
     * from a web page, which means ANY website the user visits could drive
     * this phone's AI server (compute abuse). Browser clients must present
     * the API token: when no token is configured, cross-origin browser
     * requests are rejected with instructions. Non-browser clients
     * (curl, Python, desktop apps) send no Origin header and are unaffected.
     */
    private suspend fun ApplicationCall.checkBrowserOrigin(config: Config): Boolean {
        val origin = request.header("Origin") ?: return true
        if (origin.isBlank()) return true
        if (config.authToken.isBlank()) {
            KLog.w("OpenAiServer", "Blocked cross-origin browser request from $origin (no API token configured)")
            respondText(
                errorJson(
                    "origin_blocked",
                    "Cross-origin browser requests require an API token. Set one in Server settings and send it as Authorization: Bearer.",
                ),
                ContentType.Application.Json,
                HttpStatusCode.Forbidden,
            )
            return false
        }
        return true
    }

    /**
     * Appends the permissive CORS header required by browser-based API
     * clients (OpenWebUI etc.). Browser abuse is handled separately by
     * [checkBrowserOrigin], which requires an API token for cross-origin
     * calls when none is configured server-side.
     */
    private fun ApplicationCall.appendCors() {
        response.headers.append("Access-Control-Allow-Origin", "*")
    }

    /**
     * Central validation for client-supplied sampler parameters (B13).
     * Never throws: out-of-range values are clamped into the safe range, and
     * max_tokens of 0/negative becomes 1 instead of "unlimited".
     */
    internal fun clampSampler(
        temperature: Float?,
        topK: Int?,
        topP: Float?,
        maxTokens: Int?,
        config: Config,
        systemPrompt: String,
    ): LiteRtEngineManager.EngineParams {
        val baseTemp = config.temperature.takeUnless { it.isNaN() } ?: 0.7f
        val baseTopP = config.topP.takeUnless { it.isNaN() } ?: 0.9f
        return LiteRtEngineManager.EngineParams(
            modelPath = config.modelPath,
            temperature = (temperature?.takeUnless { it.isNaN() } ?: baseTemp).coerceIn(0f, 2f),
            topK = (topK ?: config.topK).coerceAtLeast(1),
            topP = (topP?.takeUnless { it.isNaN() } ?: baseTopP).coerceIn(0f, 1f),
            maxTokens = (maxTokens ?: config.maxTokens).coerceIn(1, 8192),
            backend = config.backend,
            contextWindow = config.contextWindow,
            systemPrompt = systemPrompt,
            enableThinking = config.enableThinking,
        )
    }

    private val _status = MutableStateFlow<Status>(Status.Stopped)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _requestCount = MutableStateFlow(0L)
    val requestCount: StateFlow<Long> = _requestCount.asStateFlow()

    private val _activeRequests = MutableStateFlow(0)

    /** Number of generations currently in flight (used for WakeLock management). */
    val activeRequests: StateFlow<Int> = _activeRequests.asStateFlow()

    /** Runs [block] while counting it as an in-flight request. */
    private suspend fun <T> trackActive(block: suspend () -> T): T {
        _activeRequests.update { it + 1 }
        try {
            return block()
        } finally {
            _activeRequests.update { it - 1 }
        }
    }

    private const val MAX_BODY_BYTES = 2 * 1024 * 1024 // 2 MB

    /**
     * Reads the request body, rejecting oversized payloads with 413.
     * Returns null when the request was already answered with an error.
     *
     * The body is consumed through the channel with a bounded accumulator so
     * a chunked body without Content-Length cannot fill RAM before the limit
     * is enforced (B8).
     */
    private suspend fun ApplicationCall.receiveTextLimited(): String? {
        val declared = request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declared != null && declared > MAX_BODY_BYTES) {
            respondTooLarge()
            return null
        }
        val channel = receiveChannel()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        // readAvailable() returns -1 at end-of-stream, so no isClosedForRead
        // check is needed (that API was removed in Ktor 3.x).
        while (true) {
            val n = channel.readAvailable(buf)
            if (n == -1) break
            total += n
            if (total > MAX_BODY_BYTES) {
                respondTooLarge()
                try {
                    channel.cancel()
                } catch (_: Exception) {
                }
                return null
            }
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8)
    }

    private suspend fun ApplicationCall.respondTooLarge() {
        respondText(
            errorJson("invalid_request_error", "Request body too large (max 2 MB)"),
            ContentType.Application.Json,
            HttpStatusCode.PayloadTooLarge,
        )
    }

    fun start(config: Config, engineManager: LiteRtEngineManager) {
        synchronized(lifecycleLock) {
            if (_status.value is Status.Running || _status.value is Status.Starting) return
            // S1: fail closed — never expose the server to the LAN without an
            // API token, even if the user enabled bind-all mode.
            if (config.bindAll && config.authToken.isBlank()) {
                val msg = "Refusing to start: bind-all (LAN) mode requires a non-empty API token"
                KLog.e("OpenAiServer", msg)
                _status.value = Status.Error(msg)
                return
            }
            _status.value = Status.Starting
            try {
                val host = if (config.bindAll) "0.0.0.0" else "127.0.0.1"
                val srv =
                    embeddedServer(CIO, host = host, port = config.port) {
                        routing {
                            // Global CORS preflight handler
                            options("{...}") {
                                call.appendCors()
                                call.response.headers.append("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
                                call.response.headers.append("Access-Control-Allow-Headers", "*")
                                call.respondText("")
                            }
                            get("/health") {
                                call.appendCors()
                                if (!call.checkBrowserOrigin(config)) return@get
                                if (!call.checkAuth(config)) return@get
                                call.respondText("ok")
                            }
                            get("/v1/models") {
                                call.appendCors()
                                if (!call.checkBrowserOrigin(config)) return@get
                                if (!call.checkAuth(config)) return@get
                                call.respondText(modelsJson(config.modelId()), ContentType.Application.Json)
                            }
                            get("/v1/models/{model}") {
                                call.appendCors()
                                if (!call.checkBrowserOrigin(config)) return@get
                                if (!call.checkAuth(config)) return@get
                                val activeModelId = config.modelId()
                                val requestedModel = call.parameters["model"].orEmpty()
                                if (requestedModel != activeModelId && requestedModel != "raikaai") {
                                    call.respondText(
                                        errorJson("model_not_found", "Model '$requestedModel' not found"),
                                        ContentType.Application.Json,
                                        HttpStatusCode.NotFound,
                                    )
                                    return@get
                                }
                                call.respondText(modelDetailJson(activeModelId), ContentType.Application.Json)
                            }
                            post("/v1/chat/completions") {
                                call.appendCors()
                                if (!call.checkBrowserOrigin(config)) return@post
                                call.handleChatCompletion(config, engineManager)
                            }
                            post("/v1/completions") {
                                call.appendCors()
                                if (!call.checkBrowserOrigin(config)) return@post
                                call.handleRawCompletion(config, engineManager)
                            }
                        }
                    }
                server = srv
                srv.start(wait = false)
                _status.value = Status.Running(config.port, config.bindAll)
                KLog.d("OpenAiServer", "Listening on http://$host:${config.port}/v1")
            } catch (e: Exception) {
                KLog.e("OpenAiServer", "Failed to start on port ${config.port}", e)
                _status.value = Status.Error(e.message ?: e.javaClass.simpleName)
                server = null
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            try {
                server?.stop(500, 1500)
            } catch (_: Exception) {
            }
            server = null
            _status.value = Status.Stopped
            KLog.d("OpenAiServer", "Server stopped")
        }
    }

    private fun Config.modelId(): String =
        modelPath.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".litertlm").ifBlank { "raikaai" }

    private suspend fun ApplicationCall.handleChatCompletion(
        config: Config,
        engineManager: LiteRtEngineManager,
    ) {
        if (!checkAuth(config)) return

        // Rate limiting (keyed by the real connection IP, not a client header)
        val clientHost = remoteIp()
        if (!checkRateLimit(clientHost)) {
            respondText(
                errorJson("rate_limit_exceeded", "Too many requests. Please try again later."),
                ContentType.Application.Json,
                HttpStatusCode.TooManyRequests,
            )
            return
        }

        val bodyText = receiveTextLimited() ?: return
        val request =
            try {
                gson.fromJson(bodyText, ChatCompletionRequest::class.java)
            } catch (_: Exception) {
                null
            }
        val safeMessages =
            request?.messages
                ?.mapNotNull { msg -> msg?.normalize()?.takeIf { it.role.isNotEmpty() || it.content.isNotEmpty() } }
                .orEmpty()
        if (request == null || safeMessages.isEmpty()) {
            respondText(
                errorJson("invalid_request_error", "messages[] is required"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return
        }

        val startTime = System.currentTimeMillis()
        val isStream = request.stream == true

        val modelId = config.modelId()
        val prompt =
            PromptBuilder.build(
                safeMessages,
                config.systemPrompt,
                modelPath = config.modelPath,
                enableThinking = config.enableThinking,
                contextWindow = config.contextWindow,
            )
        val params =
            clampSampler(
                request.temperature,
                request.top_k,
                request.top_p,
                request.max_tokens,
                config,
                config.systemPrompt,
            )

        _requestCount.update { it + 1 }

        if (isStream) {
            response.headers.append("Cache-Control", "no-cache")
            respondTextWriter(contentType = ContentType.Text.EventStream) {
                val id = SseProtocol.newId()
                write(SseProtocol.roleChunk(id, modelId))
                write("\n\n")
                flush()
                try {
                    trackActive {
                        engineManager.streamResponse(prompt, params).collect { delta ->
                            write(SseProtocol.chunk(id, modelId, delta))
                            write("\n\n")
                            flush()
                        }
                    }
                    write(SseProtocol.chunk(id, modelId, null, finishReason = "stop"))
                    write("\n\n")
                    write(SseProtocol.done())
                    write("\n\n")
                    flush()
                    logRequest(
                        ServerRequestLog(
                            id = id,
                            timestamp = startTime,
                            method = "POST",
                            path = "/v1/chat/completions",
                            clientIp = clientHost,
                            status = 200,
                            durationMs = System.currentTimeMillis() - startTime,
                            stream = true,
                        ),
                    )
                } catch (e: Exception) {
                    KLog.e("OpenAiServer", "Streaming failed", e)
                    try {
                        // Signal the truncation instead of letting the client
                        // mistake a bare [DONE] for a clean finish.
                        write(SseProtocol.errorChunk("Generation failed"))
                        write("\n\n")
                        write(SseProtocol.done())
                        write("\n\n")
                        flush()
                    } catch (_: Exception) {
                    }
                    logRequest(
                        ServerRequestLog(
                            id = id,
                            timestamp = startTime,
                            method = "POST",
                            path = "/v1/chat/completions",
                            clientIp = clientHost,
                            status = 500,
                            durationMs = System.currentTimeMillis() - startTime,
                            stream = true,
                        ),
                    )
                }
            }
        } else {
            val reqId = SseProtocol.newId()
            try {
                val content = trackActive { engineManager.generateResponse(prompt, params) }
                respondText(
                    SseProtocol.completion(reqId, modelId, content).toString(),
                    ContentType.Application.Json,
                )
                logRequest(
                    ServerRequestLog(
                        id = reqId,
                        timestamp = startTime,
                        method = "POST",
                        path = "/v1/chat/completions",
                        clientIp = clientHost,
                        status = 200,
                        durationMs = System.currentTimeMillis() - startTime,
                        stream = false,
                    ),
                )
            } catch (e: Exception) {
                KLog.e("OpenAiServer", "Generation failed", e)
                respondText(
                    errorJson("generation_failed", "Generation failed"),
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError,
                )
                logRequest(
                    ServerRequestLog(
                        id = reqId,
                        timestamp = startTime,
                        method = "POST",
                        path = "/v1/chat/completions",
                        clientIp = clientHost,
                        status = 500,
                        durationMs = System.currentTimeMillis() - startTime,
                        stream = false,
                    ),
                )
            }
        }
    }

    private suspend fun ApplicationCall.handleRawCompletion(
        config: Config,
        engineManager: LiteRtEngineManager,
    ) {
        if (!checkAuth(config)) return

        // Rate limiting (keyed by the real connection IP, not a client header)
        val clientHost = remoteIp()
        if (!checkRateLimit(clientHost)) {
            respondText(
                errorJson("rate_limit_exceeded", "Too many requests. Please try again later."),
                ContentType.Application.Json,
                HttpStatusCode.TooManyRequests,
            )
            return
        }

        val bodyText = receiveTextLimited() ?: return
        val request =
            try {
                gson.fromJson(bodyText, RawCompletionRequest::class.java)
            } catch (_: Exception) {
                null
            }

        val prompt = request?.prompt?.ifBlank { null } ?: run {
            respondText(
                errorJson("invalid_request_error", "prompt is required"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return
        }

        val startTime = System.currentTimeMillis()
        val isStream = request.stream == true
        val modelId = config.modelId()

        // Optional per-request system prompt: prepended to the raw prompt so it
        // actually takes effect (mirrors the chat endpoint's behavior).
        val requestSystem = request.system?.takeIf { it.isNotBlank() }
        val effectivePrompt = if (requestSystem != null) "$requestSystem\n\n$prompt" else prompt

        val params =
            clampSampler(
                request.temperature,
                request.top_k,
                request.top_p,
                request.max_tokens,
                config,
                request.system ?: config.systemPrompt,
            )

        _requestCount.update { it + 1 }

        if (isStream) {
            response.headers.append("Cache-Control", "no-cache")
            respondTextWriter(contentType = ContentType.Text.EventStream) {
                val id = SseProtocol.newCompletionId()
                try {
                    trackActive {
                        engineManager.streamResponse(effectivePrompt, params).collect { delta ->
                            write(SseProtocol.textChunk(id, modelId, delta))
                            write("\n\n")
                            flush()
                        }
                    }
                    write(SseProtocol.textChunk(id, modelId, null, finishReason = "stop"))
                    write("\n\n")
                    write(SseProtocol.done())
                    write("\n\n")
                    flush()
                    logRequest(
                        ServerRequestLog(
                            id = id,
                            timestamp = startTime,
                            method = "POST",
                            path = "/v1/completions",
                            clientIp = clientHost,
                            status = 200,
                            durationMs = System.currentTimeMillis() - startTime,
                            stream = true,
                        ),
                    )
                } catch (e: Exception) {
                    KLog.e("OpenAiServer", "Raw streaming failed", e)
                    try {
                        // Signal the truncation instead of closing the stream silently.
                        write(SseProtocol.errorChunk("Generation failed"))
                        write("\n\n")
                        write(SseProtocol.done())
                        write("\n\n")
                        flush()
                    } catch (_: Exception) {
                    }
                    logRequest(
                        ServerRequestLog(
                            id = id,
                            timestamp = startTime,
                            method = "POST",
                            path = "/v1/completions",
                            clientIp = clientHost,
                            status = 500,
                            durationMs = System.currentTimeMillis() - startTime,
                            stream = true,
                        ),
                    )
                }
            }
        } else {
            val reqId = SseProtocol.newCompletionId()
            try {
                val content = trackActive { engineManager.generateResponse(effectivePrompt, params) }
                respondText(
                    SseProtocol.textCompletion(reqId, modelId, content).toString(),
                    ContentType.Application.Json,
                )
                logRequest(
                    ServerRequestLog(
                        id = reqId,
                        timestamp = startTime,
                        method = "POST",
                        path = "/v1/completions",
                        clientIp = clientHost,
                        status = 200,
                        durationMs = System.currentTimeMillis() - startTime,
                        stream = false,
                    ),
                )
            } catch (e: Exception) {
                KLog.e("OpenAiServer", "Raw generation failed", e)
                respondText(
                    errorJson("generation_failed", "Generation failed"),
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError,
                )
                logRequest(
                    ServerRequestLog(
                        id = reqId,
                        timestamp = startTime,
                        method = "POST",
                        path = "/v1/completions",
                        clientIp = clientHost,
                        status = 500,
                        durationMs = System.currentTimeMillis() - startTime,
                        stream = false,
                    ),
                )
            }
        }
    }

    private fun modelDetailJson(modelId: String): String =
        JsonObject()
            .apply {
                addProperty("id", modelId)
                addProperty("object", "model")
                addProperty("owned_by", "raikaai")
                add("permission", JsonArray())
            }.toString()

    private fun modelsJson(modelId: String): String =
        JsonObject()
            .apply {
                addProperty("object", "list")
                add(
                    "data",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("id", modelId)
                                addProperty("object", "model")
                                addProperty("owned_by", "raikaai")
                            },
                        )
                    },
                )
            }.toString()

    private fun errorJson(
        type: String,
        message: String,
    ): String =
        JsonObject()
            .apply {
                add(
                    "error",
                    JsonObject().apply {
                        addProperty("message", message)
                        addProperty("type", type)
                    },
                )
            }.toString()
}

/** Gson-mapped OpenAI chat completion request. */
data class ChatCompletionRequest(
    val model: String? = null,
    val messages: List<PromptBuilder.ChatMessage?>? = null,
    val stream: Boolean? = false,
    val temperature: Float? = null,
    val top_k: Int? = null,
    val top_p: Float? = null,
    val max_tokens: Int? = null,
)

/** Gson-mapped raw completion request (/v1/completions). */
data class RawCompletionRequest(
    val model: String? = null,
    val prompt: String? = null,
    val stream: Boolean? = false,
    val temperature: Float? = null,
    val top_k: Int? = null,
    val top_p: Float? = null,
    val max_tokens: Int? = null,
    /** Optional system prompt; prepended to [prompt] when present. */
    val system: String? = null,
)
