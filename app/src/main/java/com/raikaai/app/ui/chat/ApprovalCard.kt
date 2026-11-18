package com.raikaai.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Title question derived from the tool asking for permission. */
private fun approvalTitle(name: String): String =
    when (name.lowercase()) {
        "bash", "pwsh", "shell", "run_command", "execute_in_container" -> "Allow running this command?"
        "write", "write_file" -> "Allow writing this file?"
        "edit", "edit_file", "str_replace_editor" -> "Allow editing this file?"
        "web_fetch", "fetch" -> "Allow opening this webpage?"
        "web_search", "search" -> "Allow this web search?"
        else -> "Allow ${toolVerb(name).ifEmpty { "this tool" }}?"
    }

/**
 * Warning-tone approval prompt with a monospace command well and three actions.
 * Purely presentational: every button reports through a callback, and [state.busy]
 * disables all actions and shows a spinner. Matches DSH Go's layout — warning
 * strip and title, capped command well, three-line reason, two primary buttons
 * plus a lower-emphasis "Allow for chat" text button.
 */
@Composable
fun ApprovalCard(
    state: ApprovalUiState,
    onDecline: () -> Unit,
    onAllowOnce: () -> Unit,
    onAllowForChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val warning = scheme.toolWarning
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, warning.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(warning.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = warning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = approvalTitle(state.name),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.busy) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = warning,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.command.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.surfaceContainerHighest.copy(alpha = 0.75f),
                        border = BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 148.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "$ ${state.command.trim()}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                ),
                                color = scheme.onSurface,
                            )
                        }
                    }
                }

                if (!state.reason.isNullOrBlank()) {
                    Text(
                        text = state.reason.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDecline,
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Decline")
                    }
                    FilledButton(
                        onClick = onAllowOnce,
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Allow once")
                    }
                }
                TextButton(
                    onClick = onAllowForChat,
                    enabled = !state.busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Allow for chat")
                }
            }
        }
    }
}
