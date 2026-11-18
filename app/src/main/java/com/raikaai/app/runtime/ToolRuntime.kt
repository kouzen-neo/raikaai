package com.raikaai.app.runtime

/** Description of one tool a runtime can expose to a model. */
data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)

/** A request to execute a tool with JSON-encoded arguments. */
data class ToolRequest(
    val name: String,
    val argumentsJson: String,
)

/**
 * Outcome of a tool call. [isError] is true for failures and for unavailability,
 * so callers can report the text back to the model as a tool error.
 */
data class ToolResult(
    val text: String,
    val isError: Boolean = false,
)

/** Events streamed while a tool call or an agent turn is in progress. */
sealed interface RuntimeEvent {
    /** A chunk of the user-visible assistant reply. */
    data class AssistantDelta(val text: String) : RuntimeEvent

    /** A chunk of reasoning text, kept separate from the visible reply. */
    data class ThinkingDelta(val text: String) : RuntimeEvent

    /** The model asked to invoke a tool with JSON-encoded arguments. */
    data class ToolCall(val name: String, val argumentsJson: String) : RuntimeEvent

    /** A privileged tool needs user consent before it may run. */
    data class ApprovalRequest(
        val name: String,
        val argumentsJson: String,
        val reason: String,
    ) : RuntimeEvent

    /** The turn finished; [text] is the complete assistant reply. */
    data class TurnEnd(val text: String) : RuntimeEvent
}

/**
 * Seam between the UI and the tool-execution backend selected by a [RuntimeMode].
 * Implementations stay free of Android framework types so they are usable in
 * JVM unit tests. It is intentionally not sealed: fakes and the transitional
 * legacy backend can implement it without changing this file.
 */
interface ToolRuntime {
    /** Tools this runtime exposes in the current mode. */
    fun availableTools(): List<ToolSpec>

    /** Whether [name] is a tool this runtime can dispatch. */
    fun supports(name: String): Boolean

    /**
     * Executes [request], streaming progress through [onEvent] and returning
     * the final outcome. Implementations that are not connected yet return a
     * clear error [ToolResult] instead of throwing.
     */
    suspend fun call(request: ToolRequest, onEvent: (RuntimeEvent) -> Unit): ToolResult

    /** Releases resources held by the runtime; must be idempotent. */
    fun close()
}

/**
 * Linux-free tool runtime for [RuntimeMode.ON_DEVICE]. It exposes only tools
 * backed by Kotlin/network services, so no container is needed for them.
 *
 * This slice defines the seam only: execution is deliberately not wired to the
 * concrete managers, so [call] reports "not connected" rather than depending
 * on Android components.
 */
class DirectRuntime : ToolRuntime {

    override fun availableTools(): List<ToolSpec> = AVAILABLE_TOOLS

    override fun supports(name: String): Boolean = AVAILABLE_TOOLS.any { it.name == name }

    override suspend fun call(
        request: ToolRequest,
        onEvent: (RuntimeEvent) -> Unit,
    ): ToolResult = ToolResult(
        text = "Tool '${request.name}' is exposed by the on-device runtime but " +
            "execution is not connected yet.",
        isError = true,
    )

    override fun close() = Unit

    companion object {
        /** Canonical names of the Linux-free tools. */
        val TOOL_NAMES: List<String> = listOf("web_search", "rag_search", "generate_image", "http_mcp_call")

        private const val SEARCH_PARAMS =
            """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}"""
        private const val RAG_PARAMS =
            """{"type":"object","properties":{"query":{"type":"string"},"topK":{"type":"integer"}},"required":["query"]}"""
        private const val IMAGE_PARAMS =
            """{"type":"object","properties":{"prompt":{"type":"string"}},"required":["prompt"]}"""
        private const val MCP_PARAMS =
            """{"type":"object","properties":{"server":{"type":"string"},"tool":{"type":"string"},"arguments":{"type":"object"}},"required":["server","tool"]}"""

        private val AVAILABLE_TOOLS: List<ToolSpec> =
            listOf(
                ToolSpec("web_search", "Search the web and return cited results.", SEARCH_PARAMS),
                ToolSpec("rag_search", "Search the local RAG index for relevant chunks.", RAG_PARAMS),
                ToolSpec("generate_image", "Generate an image from a text prompt.", IMAGE_PARAMS),
                ToolSpec("http_mcp_call", "Call a tool on a configured HTTP MCP server.", MCP_PARAMS),
            )
    }
}

/**
 * Tool runtime for [RuntimeMode.KAZETERM_PI]. It is configured with the HTTP
 * adapter that will run inside the KazeTerm guest. Until that adapter lands,
 * every container tool returns a clear "unavailable" error result.
 */
class ContainerRuntime(
    val host: String,
    val port: Int,
    val token: String,
) : ToolRuntime {

    /** Base URL the future guest adapter will listen on. Never includes [token]. */
    val adapterBaseUrl: String get() = "http://$host:$port"

    override fun availableTools(): List<ToolSpec> = CONTAINER_TOOLS

    override fun supports(name: String): Boolean = CONTAINER_TOOLS.any { it.name == name }

    override suspend fun call(
        request: ToolRequest,
        onEvent: (RuntimeEvent) -> Unit,
    ): ToolResult = ToolResult(
        text = "Container tool '${request.name}' is unavailable: the KazeTerm Pi " +
            "adapter at $adapterBaseUrl is not connected yet.",
        isError = true,
    )

    override fun close() = Unit

    companion object {
        /** Pi built-in tools that live inside the KazeTerm guest. */
        val TOOL_NAMES: List<String> = listOf("read", "bash", "edit", "write", "grep", "find", "ls")

        private const val PATH_PARAMS =
            """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""
        private const val COMMAND_PARAMS =
            """{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}"""
        private const val EDIT_PARAMS =
            """{"type":"object","properties":{"path":{"type":"string"},"oldText":{"type":"string"},"newText":{"type":"string"}},"required":["path","oldText","newText"]}"""
        private const val WRITE_PARAMS =
            """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""
        private const val SEARCH_PARAMS =
            """{"type":"object","properties":{"pattern":{"type":"string"},"path":{"type":"string"}},"required":["pattern"]}"""

        private val CONTAINER_TOOLS: List<ToolSpec> =
            listOf(
                ToolSpec("read", "Read a file in the guest workspace.", PATH_PARAMS),
                ToolSpec("bash", "Run a shell command in the guest.", COMMAND_PARAMS),
                ToolSpec("edit", "Replace text in a guest file.", EDIT_PARAMS),
                ToolSpec("write", "Write a file in the guest workspace.", WRITE_PARAMS),
                ToolSpec("grep", "Search file contents in the guest.", SEARCH_PARAMS),
                ToolSpec("find", "Find files by name in the guest.", SEARCH_PARAMS),
                ToolSpec("ls", "List a directory in the guest.", PATH_PARAMS),
            )
    }
}
