package com.raikaai.app.mcp

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.raikaai.app.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns MCP server configurations and their discovered tools.
 *
 * - Configurations are persisted by SettingsRepository (JSON); this manager
 *   keeps the in-memory copy in sync via [setServers].
 * - Tool discovery results are cached per server id and exposed via
 *   [toolsByServer]; [refreshTools] re-runs `tools/list` on enabled servers.
 * - [callTool] executes a tool on the matching server. The caller (chat flow)
 *   is responsible for the explicit user-confirmation boundary before
 *   invoking it.
 */
class McpManager private constructor(private val appContext: Context) {

    companion object {
        @Volatile private var instance: McpManager? = null

        fun getInstance(context: Context): McpManager =
            instance ?: synchronized(this) {
                instance ?: McpManager(context.applicationContext).also { instance = it }
            }

        private val SERVER_LIST_TYPE = object : TypeToken<List<McpServerConfig>>() {}.type

        fun serversToJson(servers: List<McpServerConfig>): String =
            McpJson.gson.toJson(servers)

        fun serversFromJson(json: String): List<McpServerConfig> =
            runCatching {
                if (json.isBlank()) return emptyList()
                (McpJson.gson.fromJson<List<McpServerConfig>>(json, SERVER_LIST_TYPE) ?: emptyList())
                    .map { it.normalized() }
                    .filter { it.url.isNotBlank() || it.isStdio }
            }.getOrElse {
                KLog.w("McpManager", "Failed to parse MCP servers JSON: ${it.message}")
                emptyList()
            }
    }

    /** Starts a stdio server's command inside Alpine; set by the app. */
    @Volatile var stdioLauncher: ((String) -> Process)? = null

    private val mutex = Mutex()
    private val clients = mutableMapOf<String, McpClient>()
    private val clientLock = Any()

    private val _servers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val _toolsByServer = MutableStateFlow<Map<String, List<McpTool>>>(emptyMap())
    val toolsByServer: StateFlow<Map<String, List<McpTool>>> = _toolsByServer.asStateFlow()

    /** Servers currently connecting / listing tools. */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    /** Last discovery error per server id (null = no error). */
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    /** Syncs in-memory configs (called when SettingsRepository emits). */
    suspend fun setServers(servers: List<McpServerConfig>) =
        mutex.withLock {
            synchronized(clientLock) {
                val removed = _servers.value.map { it.id }.toSet() - servers.map { it.id }.toSet()
                removed.forEach { clients.remove(it)?.close() }
                // Drop clients whose endpoint/auth changed so the next call re-handshakes.
                servers.forEach { s ->
                    val cur = _servers.value.find { it.id == s.id }
                    if (cur != null && (cur.url != s.url || cur.authToken != s.authToken || cur.command != s.command || (cur.enabled && !s.enabled))) {
                        clients.remove(s.id)?.close()
                    }
                }
            }
            _servers.value = servers
            // Prune cached tools/errors for removed servers.
            _toolsByServer.value = _toolsByServer.value.filterKeys { id -> servers.any { it.id == id } }
            _errors.value = _errors.value.filterKeys { id -> servers.any { it.id == id } }
        }

    private fun clientFor(server: McpServerConfig): McpClient =
        synchronized(clientLock) {
            val existing = clients[server.id]
            if (existing != null) return existing
            // Rebuild if the endpoint/auth changed since the client was cached.
            McpClient(server, stdioLauncher).also { clients[server.id] = it }
        }

    /** All tools from enabled servers that have been discovered so far. */
    fun allEnabledTools(): List<McpTool> {
        val enabledIds = _servers.value.filter { it.enabled }.map { it.id }.toSet()
        return _toolsByServer.value.filterKeys { it in enabledIds }.values.flatten()
    }

    fun findTool(name: String): McpTool? =
        allEnabledTools().find { it.name.equals(name, ignoreCase = true) }

    /** Discovers tools on one server, updating cache + error state. */
    suspend fun refreshTools(server: McpServerConfig): List<McpTool> =
        withContext(Dispatchers.IO) {
            _busy.update { it + server.id }
            try {
                val tools = clientFor(server).listTools()
                mutex.withLock {
                    _toolsByServer.value = _toolsByServer.value + (server.id to tools)
                    _errors.value = _errors.value - server.id
                }
                tools
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                KLog.w("McpManager", "tools/list failed for '${server.name}': $msg")
                mutex.withLock { _errors.value = _errors.value + (server.id to msg) }
                throw e
            } finally {
                _busy.update { it - server.id }
            }
        }

    /** Discovers tools on all enabled servers; per-server failures are recorded, not thrown. */
    suspend fun refreshAllEnabled(): Map<String, List<McpTool>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, List<McpTool>>()
            _servers.value.filter { it.enabled }.forEach { server ->
                runCatching { refreshTools(server) }
                    .onSuccess { result[server.id] = it }
                    .onFailure { result[server.id] = emptyList() }
            }
            result
        }

    /** Executes a tool call against its server. Throws on transport/protocol errors. */
    suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: JsonObject,
    ): McpCallResult =
        withContext(Dispatchers.IO) {
            val server = _servers.value.find { it.id == serverId }
                ?: throw McpClient.McpException("MCP server not found")
            if (!server.enabled) throw McpClient.McpException("MCP server '${server.name}' is disabled")
            clientFor(server).callTool(toolName, arguments)
        }
}
