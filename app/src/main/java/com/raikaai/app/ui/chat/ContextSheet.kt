package com.raikaai.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One row of the context breakdown. */
data class ContextRow(val label: String, val tokens: Int, val color: Color)

private val partColors =
    mapOf(
        "Messages" to Color(0xFF4F8DF5),
        "Summary" to Color(0xFF7FA8F0),
        "System prompt" to Color(0xFFE8743B),
        "Character card" to Color(0xFFE8743B),
        "Memory files" to Color(0xFFD9A21B),
        "Skills" to Color(0xFF2BA36B),
        "MCP tools" to Color(0xFF9B6BDF),
        "Linux tools" to Color(0xFF3BB3C3),
        "Image tool" to Color(0xFFC75B9B),
        "Knowledge" to Color(0xFF8A9A5B),
        "Web results" to Color(0xFF5B8A9A),
    )

/** Builds the rows in display order: messages first, then the prompt pieces. */
fun contextRows(messageTokens: Int, summaryTokens: Int, parts: List<ChatViewModel.ContextPart>): List<ContextRow> =
    buildList {
        add(ContextRow("Messages", messageTokens, partColors.getValue("Messages")))
        if (summaryTokens > 0) add(ContextRow("Summary", summaryTokens, partColors.getValue("Summary")))
        parts.groupBy { it.label }.forEach { (label, list) ->
            add(ContextRow(label, list.sumOf { it.tokens }, partColors[label] ?: Color(0xFF8C8C8C)))
        }
    }

/**
 * Where the context window goes: messages, the conversation summary and every
 * piece of the system prompt (persona, memory files, skills, tool catalogs,
 * retrieved knowledge), with the free space left. Token counts are estimates
 * of about 3.5 characters per token.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(
    rows: List<ContextRow>,
    windowTokens: Int,
    onCompact: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val used = rows.sumOf { it.tokens }
    val free = (windowTokens - used).coerceAtLeast(0)
    val pct = if (windowTokens > 0) used * 100f / windowTokens else 0f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Context", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Context window", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (pct < 1f && used > 0) "<1% used" else "${pct.toInt()}% used",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SegmentedBar(rows, windowTokens)
                    Text(
                        "${fmt(used)} of ${fmt(windowTokens)} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    rows.forEach { r -> BreakdownRow(r.label, r.tokens, windowTokens, r.color) }
                    BreakdownRow("Free space", free, windowTokens, MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Text(
                "Counts are estimates (about 3.5 characters per token). The system prompt pieces are measured " +
                    "from the last reply in this chat. On-device models get shorter versions of the memory files and tool lists.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onCompact != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCompact) { Text("Compact now") }
                }
            }
        }
    }
}

@Composable
private fun SegmentedBar(rows: List<ContextRow>, windowTokens: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        val total = windowTokens.coerceAtLeast(1).toFloat()
        var usedFraction = 0f
        rows.filter { it.tokens > 0 }.forEach { r ->
            // Tiny pieces stay visible as a sliver.
            val f = (r.tokens / total).coerceAtLeast(0.004f).coerceAtMost(1f - usedFraction)
            if (f > 0f) {
                Box(Modifier.weight(f).fillMaxHeight().background(r.color))
                usedFraction += f
            }
        }
        val rest = 1f - usedFraction
        if (rest > 0f) Spacer(Modifier.weight(rest))
    }
}

@Composable
private fun BreakdownRow(label: String, tokens: Int, windowTokens: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(fmt(tokens), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        val pct = if (windowTokens > 0) tokens * 100f / windowTokens else 0f
        Text(
            if (tokens > 0 && pct < 0.1f) "<0.1%" else "%.1f%%".format(pct),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun fmt(n: Int): String =
    when {
        n >= 1_000_000 -> "%.2fM".format(n / 1_000_000.0)
        n >= 10_000 -> "%.1fk".format(n / 1_000.0)
        else -> n.toString()
    }
