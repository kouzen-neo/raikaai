package com.raikaai.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.ui.component.MarkdownText
import com.raikaai.app.ui.theme.success

/** Warning tone adapted to the active palette; mirrors the theme's `success` helper. */
internal val ColorScheme.toolWarning: Color
    get() = if (background.luminance() > 0.5f) Color(0xFFED6C02) else Color(0xFFFFB74D)

private const val DETAIL_MAX_HEIGHT_DP = 280

/** Short human verb for the collapsed header; falls back to a cleaned-up name. */
internal fun toolVerb(name: String): String =
    when (name.lowercase()) {
        "bash", "pwsh", "shell", "run_command", "execute_in_container" -> "Run"
        "write", "write_file" -> "Write"
        "edit", "edit_file", "str_replace_editor" -> "Edit"
        "read", "read_file" -> "Read"
        "search", "grep", "glob", "web_search" -> "Search"
        "web_fetch", "fetch" -> "Fetch"
        "subagent", "subagent_fork" -> "Subagent"
        else -> name.replace('_', ' ').trim()
    }

/**
 * Collapsed tool row (32 dp) that expands into a code-well detail. Header and
 * detail are driven entirely by [state]; [onViewAll] receives the full,
 * unclipped text of the section the user wants to open. No call sites are
 * modified — this is an additive, reusable component.
 */
@Composable
fun ToolCard(
    state: ToolUiState,
    modifier: Modifier = Modifier,
    onViewAll: (String) -> Unit = {},
    renderMarkdown: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val labelColor =
        when (state.status) {
            ToolStatus.FAILED -> scheme.error
            ToolStatus.AWAITING_APPROVAL -> scheme.toolWarning
            else -> scheme.onSurfaceVariant
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                val verb = toolVerb(state.name)
                if (verb.isNotEmpty()) {
                    Text(
                        text = verb,
                        style = scheme.labelStyle(),
                        color = scheme.onSurface,
                    )
                    if (state.summary.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        Text("·", style = scheme.labelStyle(), color = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                    }
                }
                Text(
                    text = state.summary.ifBlank { state.name },
                    style = scheme.labelStyle(),
                    color = labelColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                ToolStatusGlyph(state.status)
                Spacer(Modifier.width(2.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse tool" else "Expand tool",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                val hasArguments = state.arguments.isNotBlank() && state.arguments.trim() != "{}"
                val hasOutput = state.output.isNotBlank()
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(Modifier.height(8.dp))
                    if (hasArguments) {
                        DetailTitle("Parameters")
                        PlainDetailWell(text = state.arguments.trim(), onViewAll = onViewAll)
                    }
                    if (hasOutput) {
                        if (hasArguments) Spacer(Modifier.height(10.dp))
                        DetailTitle("Result")
                        if (renderMarkdown) {
                            MarkdownDetailWell(text = state.output, onViewAll = onViewAll)
                        } else {
                            PlainDetailWell(text = state.output, onViewAll = onViewAll)
                        }
                    }
                    if (!hasArguments && !hasOutput) {
                        Text(
                            text = "No details",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorScheme.labelStyle() =
    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun DetailTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToolStatusGlyph(status: ToolStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        ToolStatus.RUNNING ->
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = scheme.primary,
            )
        ToolStatus.SUCCEEDED ->
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Succeeded",
                tint = scheme.success,
                modifier = Modifier.size(14.dp),
            )
        ToolStatus.FAILED ->
            Icon(
                Icons.Default.Close,
                contentDescription = "Failed",
                tint = scheme.error,
                modifier = Modifier.size(14.dp),
            )
        ToolStatus.AWAITING_APPROVAL ->
            Icon(
                Icons.Default.Warning,
                contentDescription = "Awaiting approval",
                tint = scheme.toolWarning,
                modifier = Modifier.size(14.dp),
            )
        ToolStatus.DENIED ->
            Icon(
                Icons.Default.Close,
                contentDescription = "Denied",
                tint = scheme.toolWarning,
                modifier = Modifier.size(14.dp),
            )
        ToolStatus.CANCELLED ->
            Icon(
                Icons.Default.Close,
                contentDescription = "Cancelled",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
    }
}

/** Plain monospace code well, clipped to head/tail with a "View all" bar. */
@Composable
private fun PlainDetailWell(
    text: String,
    onViewAll: (String) -> Unit,
) {
    val clip = remember(text) { clipToolText(text) }
    val mono =
        MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = DETAIL_MAX_HEIGHT_DP.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
                    Text(clip.head, style = mono, color = MaterialTheme.colorScheme.onSurface)
                    if (clip.tail != null) {
                        HiddenMarker(clip = clip, onClick = { onViewAll(text) })
                        Text(clip.tail, style = mono, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (clip.clipped) {
                ViewAllBar(clip = clip, chars = text.length, onClick = { onViewAll(text) })
            }
        }
    }
}

/** Markdown-aware code well: reuses the existing [MarkdownText] renderer. */
@Composable
private fun MarkdownDetailWell(
    text: String,
    onViewAll: (String) -> Unit,
) {
    val clip = remember(text) { clipToolText(text) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = DETAIL_MAX_HEIGHT_DP.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MarkdownText(markdown = clip.head)
                    if (clip.tail != null) {
                        HiddenMarker(clip = clip, onClick = { onViewAll(text) })
                        MarkdownText(markdown = clip.tail)
                    }
                }
            }
            if (clip.clipped) {
                ViewAllBar(clip = clip, chars = text.length, onClick = { onViewAll(text) })
            }
        }
    }
}

@Composable
private fun HiddenMarker(
    clip: ClippedText,
    onClick: () -> Unit,
) {
    val label =
        if (clip.hiddenLines > 0) {
            "${clip.hiddenLines} lines hidden"
        } else {
            "${clip.hiddenChars} characters hidden"
        }
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Text(
                text = "⋯ $label ⋯",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ViewAllBar(
    clip: ClippedText,
    chars: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${clip.totalLines} lines · $chars chars",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "View all",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
