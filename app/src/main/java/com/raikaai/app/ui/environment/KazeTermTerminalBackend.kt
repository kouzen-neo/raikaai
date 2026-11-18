package com.raikaai.app.ui.environment

import com.raikaai.app.core.container.ContainerManager
import com.raikaai.app.ui.chat.ChatViewModel

private const val KAZETERM_UNAVAILABLE =
    "KazeTerm terminal backend requires the KazeTerm guest runtime, which is not available in this build."

/**
 * Placeholder [TerminalBackend] for the future KazeTerm runtime.
 *
 * It reports itself unavailable so [EnvironmentScreen] never crashes on it:
 * the screen checks [isAvailable] before executing and shows the reason through
 * the existing toast pattern. Any operation that is reached without that guard
 * fails fast with a clear, KazeTerm-specific message.
 */
class KazeTermTerminalBackend : TerminalBackend {

    override fun sessions(): List<ChatViewModel.TerminalSession> = emptyList()

    override fun execute(
        sessionId: String,
        command: String,
        onLine: (ContainerManager.TermKind, String) -> Unit,
        onDone: () -> Unit,
    ): Unit = unavailable()

    override suspend fun startDaemon(command: String): ContainerManager.RunResult = unavailable()

    override suspend fun stopDaemon(id: Int): Boolean = unavailable()

    override fun isAvailable(): Boolean = false

    override fun unavailableReason(): String = KAZETERM_UNAVAILABLE

    private fun unavailable(): Nothing = throw IllegalStateException(KAZETERM_UNAVAILABLE)
}
