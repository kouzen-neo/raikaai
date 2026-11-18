package com.raikaai.app.ui.environment

import com.raikaai.app.core.container.ContainerManager
import com.raikaai.app.ui.chat.ChatViewModel

/**
 * Minimal seam between the Environment UI and the runtime that executes shell
 * commands. The current runtime is the Alpine PRoot [ContainerManager]; a
 * KazeTerm-backed implementation can replace it without rewriting the screen.
 *
 * Deliberately small: only what [EnvironmentScreen] observes today. Session
 * state still lives in the host [ChatViewModel] so navigation never loses tabs.
 */
interface TerminalBackend {
    /** Sessions rendered by the terminal tab strip. */
    fun sessions(): List<ChatViewModel.TerminalSession>

    /**
     * Runs [command] in [sessionId].
     *
     * [onLine] receives streamed output for backends that own their own output
     * (a future KazeTerm PTY). The legacy PRoot backend keeps writing lines to
     * the session through [ChatViewModel.execTerminal], so it does not use
     * [onLine]. [onDone] fires once when the command finishes, succeeds or is
     * cancelled, and is where the screen refreshes the Files tab.
     */
    fun execute(
        sessionId: String,
        command: String,
        onLine: (ContainerManager.TermKind, String) -> Unit = { _, _ -> },
        onDone: () -> Unit = {},
    )

    /** Starts a detached background command (the `&` path in the terminal). */
    suspend fun startDaemon(command: String): ContainerManager.RunResult

    /** Stops one background daemon by id. */
    suspend fun stopDaemon(id: Int): Boolean

    /** Whether this backend can execute commands on the current build/device. */
    fun isAvailable(): Boolean

    /** Human-readable reason shown when [isAvailable] is false. */
    fun unavailableReason(): String = "Terminal backend is not available."
}
