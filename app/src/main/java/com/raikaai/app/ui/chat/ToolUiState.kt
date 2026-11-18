package com.raikaai.app.ui.chat

/**
 * Immutable state for the tool-call and approval UI. The composables in this
 * package are intentionally stateless: these models are the only input, and
 * every user action is delivered through a callback. No business logic lives
 * here, so the same models can be driven by a ViewModel or a preview.
 */
enum class ToolStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    AWAITING_APPROVAL,
    DENIED,
    CANCELLED,
}

/**
 * One tool call as rendered by [ToolCard].
 *
 * @param name tool name, used to derive the verb and the approval title.
 * @param arguments raw arguments (usually JSON); shown in the detail well.
 * @param summary short one-line description shown in the collapsed header.
 * @param status current lifecycle stage of the call.
 * @param output raw tool output; shown (and clipped) in the detail well.
 */
data class ToolUiState(
    val name: String,
    val arguments: String = "",
    val summary: String = "",
    val status: ToolStatus = ToolStatus.RUNNING,
    val output: String = "",
)

/**
 * A pending approval as rendered by [ApprovalCard].
 *
 * @param name tool requesting permission, used for the card title.
 * @param command command/path to display in the monospace well.
 * @param reason optional human-readable explanation, capped at three lines.
 * @param busy when true every action is disabled and a spinner is shown.
 */
data class ApprovalUiState(
    val name: String,
    val command: String = "",
    val reason: String? = null,
    val busy: Boolean = false,
)
