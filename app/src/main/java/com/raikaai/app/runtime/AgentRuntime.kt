package com.raikaai.app.runtime

/** One chat message for an agent turn; [role] is "system", "user" or "assistant". */
data class AgentMessage(
    val role: String,
    val text: String,
)

/**
 * Minimal seam for a Pi-backed agent loop. A future implementation will
 * translate Pi's JSONL RPC events into [RuntimeEvent]s and return the final
 * assistant text. No Pi implementation lives in this slice.
 */
interface AgentRuntime {
    /**
     * Runs one turn over [messages], streaming progress through [onEvent] and
     * returning the complete assistant reply.
     */
    suspend fun runTurn(
        messages: List<AgentMessage>,
        onEvent: (RuntimeEvent) -> Unit,
    ): String
}
