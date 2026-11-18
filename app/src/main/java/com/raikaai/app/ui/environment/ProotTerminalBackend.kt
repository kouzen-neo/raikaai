package com.raikaai.app.ui.environment

import com.raikaai.app.core.container.ContainerManager
import com.raikaai.app.ui.chat.ChatViewModel

/**
 * [TerminalBackend] backed by the existing Alpine PRoot [ContainerManager].
 *
 * This is a thin wrapper: session bookkeeping, line streaming, cancellation and
 * title/history updates stay in [ChatViewModel.execTerminal] exactly as before,
 * so user-visible behavior is unchanged. The extra indirection exists only so
 * the screen can later switch to [KazeTermTerminalBackend] without being rewritten.
 */
class ProotTerminalBackend(
    private val viewModel: ChatViewModel,
    private val manager: ContainerManager,
) : TerminalBackend {

    override fun sessions(): List<ChatViewModel.TerminalSession> = viewModel.terminalSessions

    override fun execute(
        sessionId: String,
        command: String,
        onLine: (ContainerManager.TermKind, String) -> Unit,
        onDone: () -> Unit,
    ) {
        // The ViewModel owns the session objects and appends streamed lines to
        // them itself, so onLine is intentionally unused for this backend.
        // Keeping execTerminal as the execution path also preserves ^C handling
        // (it stores the Job on the session, which cancelTerminal relies on).
        viewModel.execTerminal(command, sessionId = sessionId, onDone = onDone)
    }

    override suspend fun startDaemon(command: String): ContainerManager.RunResult =
        manager.startDaemon(command)

    override suspend fun stopDaemon(id: Int): Boolean = manager.stopDaemon(id)

    override fun isAvailable(): Boolean = manager.isInstalled()
}
