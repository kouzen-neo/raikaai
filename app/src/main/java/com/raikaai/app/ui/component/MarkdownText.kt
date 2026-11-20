package com.raikaai.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.util.CodeArtifacts
import com.raikaai.app.util.FileSaver
import java.util.regex.Pattern
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dependency-free, high-performance Markdown renderer for streaming chat tokens.
 * Handles:
 * - Collapsible reasoning/thinking blocks (<think>...</think>)
 * - Headers (#, ##, ###)
 * - Code blocks (```lang) with copy feedback
 * - Blockquotes (>)
 * - Bullets (- / *) and Numbered lists (1.)
 * - Inline formatting (**bold**, *italic*, `inline code`, [links](url))
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    enableThinking: Boolean = true,
    onPreviewHtml: ((String) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current

    // Split into ordered segments so every <think> block renders where the
    // model produced it (think -> answer -> tool -> think -> answer), instead
    // of all thoughts being merged into one card at the top.
    val segments = remember(markdown, enableThinking) { splitThinkingSegments(markdown, enableThinking) }
    val rendered =
        remember(segments) {
            segments.map { seg ->
                when (seg) {
                    is MdSegment.Thought -> seg
                    is MdSegment.Body -> RenderedBody(parseBlocks(formatToolCallsForDisplay(seg.text)))
                }
            }
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rendered.forEachIndexed { index, item ->
            key(index) {
                when (item) {
                    is MdSegment.Thought ->
                        ThinkingCard(thought = item.text, isComplete = item.isComplete)
                    is RenderedBody ->
                        MarkdownBlocks(
                            blocks = item.blocks,
                            color = color,
                            scheme = scheme,
                            onCopyCode = { clipboard.setText(AnnotatedString(it)) },
                            onPreviewHtml = onPreviewHtml,
                        )
                }
            }
        }
    }
}

private class RenderedBody(val blocks: List<Block>)

@Composable
private fun MarkdownBlocks(
    blocks: List<Block>,
    color: Color,
    scheme: ColorScheme,
    onCopyCode: (String) -> Unit,
    onPreviewHtml: ((String) -> Unit)?,
) {
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { i, block ->
            // Extra breathing room between logical groups (paragraph -> list,
            // list -> paragraph, ...), tight spacing inside a list.
            if (i > 0 && block.gapBefore) Spacer(Modifier.height(4.dp))
            when (block.type) {
                BlockType.CODE_BLOCK ->
                    CodeBlockCard(
                        language = block.language,
                        code = block.text,
                        onCopy = { onCopyCode(block.text) },
                        onPreviewHtml = onPreviewHtml,
                    )
                BlockType.HEADER_1 ->
                    Text(
                        text = rememberAnnotated(block.text, scheme),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (color != Color.Unspecified) color else scheme.onSurface,
                        modifier = Modifier.padding(top = if (i > 0) 6.dp else 0.dp),
                    )
                BlockType.HEADER_2 ->
                    Text(
                        text = rememberAnnotated(block.text, scheme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (color != Color.Unspecified) color else scheme.onSurface,
                        modifier = Modifier.padding(top = if (i > 0) 4.dp else 0.dp),
                    )
                BlockType.HEADER_3 ->
                    Text(
                        text = rememberAnnotated(block.text, scheme),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (color != Color.Unspecified) color else scheme.onSurface,
                        modifier = Modifier.padding(top = if (i > 0) 2.dp else 0.dp),
                    )
                BlockType.BLOCKQUOTE -> BlockquoteCard(text = block.text, scheme = scheme)
                BlockType.RULE ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = scheme.outlineVariant.copy(alpha = 0.6f),
                    )
                BlockType.BULLET, BlockType.NUMBERED, BlockType.TASK -> {
                    val marker =
                        when (block.type) {
                            BlockType.NUMBERED -> block.language
                            BlockType.TASK -> if (block.language == "x") "\u2611" else "\u2610"
                            else -> if (block.indent % 2 == 0) "\u2022" else "\u25E6"
                        }
                    Row(modifier = Modifier.padding(start = (4 + block.indent * 16).dp)) {
                        Text(
                            text = marker,
                            style = bodyStyle,
                            color = scheme.primary,
                            fontWeight = if (block.type == BlockType.NUMBERED) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.widthIn(min = if (block.type == BlockType.NUMBERED) 20.dp else 12.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = rememberAnnotated(block.text, scheme),
                            style = bodyStyle,
                            color = color,
                            textDecoration =
                                if (block.type == BlockType.TASK && block.language == "x") TextDecoration.LineThrough else null,
                        )
                    }
                }
                BlockType.TABLE -> TableCard(tableRaw = block.text, scheme = scheme)
                BlockType.TOOL -> ToolCard(block = block, scheme = scheme)
                BlockType.PARAGRAPH ->
                    if (block.text.isNotBlank()) {
                        Text(
                            text = rememberAnnotated(block.text, scheme),
                            style = bodyStyle,
                            color = color,
                        )
                    }
            }
        }
    }
}

/**
 * Collapsible Thinking Process Card (for DeepSeek R1, Qwen3, and reasoning LLMs).
 */
@Composable
private fun ThinkingCard(
    thought: String,
    isComplete: Boolean,
) {
    // Collapsed by default when complete, expanded while generating
    var expanded by remember(isComplete) { mutableStateOf(!isComplete) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isComplete) "Thought Process" else "Thinking…",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isComplete) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = thought.ifBlank { "…" },
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

private fun findUnescapedQuote(s: String): Int {
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\') {
            i += 2
        } else if (s[i] == '"') {
            return i
        } else {
            i++
        }
    }
    return -1
}

private fun unescapeJsonString(raw: String): String {
    if (raw.isEmpty()) return ""
    val text = if (raw.endsWith("\\")) raw.dropLast(1) else raw
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            when (val next = text[i + 1]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'u' -> {
                    if (i + 5 < text.length) {
                        val hex = text.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                            continue
                        }
                    }
                    sb.append(c)
                }
                else -> sb.append(next)
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private const val TOOL_FENCE = "raika-tool"

/** Encodes a tool call as a fence that [parseBlocks] renders as a compact card. */
private fun toolCardFence(kind: String, title: String, status: String): String {
    val oneLine = title.trim().lineSequence().firstOrNull().orEmpty().replace("`", "'")
    val clipped = if (oneLine.length > 160) oneLine.take(160) + "…" else oneLine
    return "```$TOOL_FENCE\n$kind\n$clipped\n$status\n```"
}

private fun formatToolCallBlock(body: String, isComplete: Boolean): String {
    val cleanBody = body.trim()
    val pending = if (isComplete) "called" else "pending"
    if (cleanBody.isBlank()) return if (isComplete) "" else toolCardFence("tool", "Preparing tool call", "pending")

    val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(cleanBody)
    val toolName = nameMatch?.groupValues?.get(1)?.trim().orEmpty()

    val pathMatch = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(cleanBody)
    val path = pathMatch?.groupValues?.get(1)?.trim().orEmpty()

    val cmdMatch = Regex("\"command\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", RegexOption.IGNORE_CASE).find(cleanBody)
    val command = cmdMatch?.let { unescapeJsonString(it.groupValues[1]) }.orEmpty()

    return when {
        toolName.contains("write_file", ignoreCase = true) -> {
            // Count lines of the streamed content instead of dumping the
            // whole file into the chat while it is being written.
            val contentIdx = cleanBody.indexOf("\"content\"")
            val lineCount =
                if (contentIdx >= 0) {
                    val rawContent = cleanBody.substring(contentIdx)
                    rawContent.split("\\n").size
                } else {
                    0
                }
            val status =
                if (isComplete) "called" else if (lineCount > 0) "writing · $lineCount lines" else "pending"
            toolCardFence("write", path.ifBlank { "file" }, status)
        }
        toolName.contains("edit_file", ignoreCase = true) -> toolCardFence("edit", path.ifBlank { "file" }, pending)
        toolName.contains("read_file", ignoreCase = true) -> toolCardFence("read", path.ifBlank { "file" }, pending)
        toolName.contains("execute_in_container", ignoreCase = true) ||
            toolName.contains("run_command", ignoreCase = true) ->
            toolCardFence("run", command.ifBlank { "command" }, pending)
        toolName.isNotBlank() -> toolCardFence("tool", toolName, pending)
        cleanBody.startsWith("{") || cleanBody.startsWith("[") ->
            if (isComplete) "" else toolCardFence("tool", "Preparing tool call", "pending")
        else -> {
            val firstLine = cleanBody.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (firstLine.isNotBlank()) toolCardFence("action", firstLine, pending) else if (isComplete) "" else toolCardFence("action", "Action", "pending")
        }
    }
}

private fun formatToolCallsForDisplay(text: String): String {
    if (!text.contains("<tool_call", ignoreCase = true) &&
        !text.contains("<action", ignoreCase = true) &&
        !text.contains("```tool_call", ignoreCase = true) &&
        !text.contains("```tool", ignoreCase = true)
    ) {
        return text
    }

    var result = text

    // 1. Format <tool_call> and <action> XML blocks
    val xmlPattern = Pattern.compile("<(tool_call|action)(?:\\s+[^>]*)?>([\\s\\S]*?)(?:<\\/\\1>|$)", Pattern.CASE_INSENSITIVE)
    val xmlMatcher = xmlPattern.matcher(result)
    val xmlReplacements = mutableListOf<Pair<String, String>>()
    while (xmlMatcher.find()) {
        val fullMatch = xmlMatcher.group(0) ?: continue
        val tag = xmlMatcher.group(1).orEmpty()
        val body = xmlMatcher.group(2).orEmpty()
        val isComplete = fullMatch.contains("</$tag>", ignoreCase = true)
        val formatted = formatToolCallBlock(body, isComplete)
        xmlReplacements.add(fullMatch to formatted)
    }
    for ((orig, repl) in xmlReplacements) {
        result = result.replace(orig, "\n\n$repl\n\n")
    }

    // 2. Format ```tool_call and ```tool markdown code fences
    val mdPattern = Pattern.compile("```(?:tool_call|tool)(?:\\s+([\\s\\S]*?))?(?:```|$)", Pattern.CASE_INSENSITIVE)
    val mdMatcher = mdPattern.matcher(result)
    val mdReplacements = mutableListOf<Pair<String, String>>()
    while (mdMatcher.find()) {
        val fullMatch = mdMatcher.group(0) ?: continue
        val body = mdMatcher.group(1).orEmpty()
        val isComplete = fullMatch.endsWith("```")
        val formatted = formatToolCallBlock(body, isComplete)
        mdReplacements.add(fullMatch to formatted)
    }
    for ((orig, repl) in mdReplacements) {
        result = result.replace(orig, "\n\n$repl\n\n")
    }

    // Clean any leftover orphan closing tags
    result = result.replace(Regex("</(?:tool_call|action)>", RegexOption.IGNORE_CASE), "")

    return result.trim()
}

private sealed interface MdSegment {
    class Thought(val text: String, val isComplete: Boolean) : MdSegment

    class Body(val text: String) : MdSegment
}

private val THINK_OPEN_REGEX =
    Regex("<(think|thought|thinking)(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE)
private val THINK_STRAY_CLOSE_REGEX =
    Regex("</(?:think|thought|thinking)>", RegexOption.IGNORE_CASE)

/**
 * Splits [raw] into ordered thought / body segments. Each `<think>` block
 * keeps its position, so multi-round replies (tool call -> follow-up) show a
 * fresh thinking card under the previous answer. A closing tag without an
 * opener at the start (some templates pre-open the block) is treated as a
 * thought that began at the start of the text. Only the last thought can be
 * incomplete (still streaming).
 */
internal fun splitThinkingSegmentsForTest(raw: String, enableThinking: Boolean): List<Pair<String, String>> =
    splitThinkingSegments(raw, enableThinking).map {
        when (it) {
            is MdSegment.Thought -> (if (it.isComplete) "think" else "think…") to it.text
            is MdSegment.Body -> "body" to it.text
        }
    }

private fun splitThinkingSegments(raw: String, enableThinking: Boolean): List<MdSegment> {
    if (raw.isBlank()) return emptyList()
    val out = mutableListOf<MdSegment>()

    fun addBody(text: String) {
        val clean = text.replace(THINK_STRAY_CLOSE_REGEX, "")
        if (clean.isBlank()) return
        val last = out.lastOrNull()
        if (last is MdSegment.Body) {
            out[out.lastIndex] = MdSegment.Body(last.text + "\n\n" + clean.trim())
        } else {
            out.add(MdSegment.Body(clean.trim()))
        }
    }

    fun addThought(text: String, complete: Boolean) {
        val t = text.trim()
        if (!enableThinking) return
        // Keep an empty, still-open block so the "Thinking…" card appears
        // as soon as the model starts reasoning.
        if (t.isBlank() && complete) return
        out.add(MdSegment.Thought(t, complete))
    }

    var pos = 0
    val firstOpen = THINK_OPEN_REGEX.find(raw)
    val firstClose = THINK_STRAY_CLOSE_REGEX.find(raw)
    if (firstClose != null && (firstOpen == null || firstClose.range.first < firstOpen.range.first)) {
        addThought(raw.substring(0, firstClose.range.first), complete = true)
        pos = firstClose.range.last + 1
    }

    while (pos < raw.length) {
        val open = THINK_OPEN_REGEX.find(raw, pos)
        if (open == null) {
            addBody(raw.substring(pos))
            break
        }
        addBody(raw.substring(pos, open.range.first))
        val tag = open.groupValues[1]
        val closeRegex = Regex("</$tag>", RegexOption.IGNORE_CASE)
        val close = closeRegex.find(raw, open.range.last + 1)
        if (close == null) {
            addThought(raw.substring(open.range.last + 1), complete = false)
            break
        }
        addThought(raw.substring(open.range.last + 1, close.range.first), complete = true)
        pos = close.range.last + 1
    }
    return out
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    onCopy: () -> Unit,
    onPreviewHtml: ((String) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf<String?>(null) }
    // B36: rapid taps used to stack delay-reset coroutines; keep the latest
    // job and cancel it before starting a new one.
    var copyResetJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isHtml = remember(language, code) {
        val l = language.trim().lowercase()
        l == "html" || l == "htm" || code.contains("<!DOCTYPE html", ignoreCase = true) || code.contains("<html", ignoreCase = true)
    }

    val lines = remember(code) { code.lines() }
    val totalLines = lines.size
    val isCollapsible = totalLines > 10
    var isExpanded by remember { mutableStateOf(false) }
    val displayCode = remember(code, isExpanded) {
        if (isCollapsible && !isExpanded) lines.take(10).joinToString("\n") else code
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = language.ifBlank { "code" }.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (isCollapsible) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "· $totalLines lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCollapsible) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Minimize code" else "Maximize code",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (isHtml && onPreviewHtml != null) {
                        IconButton(
                            onClick = { onPreviewHtml(code) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = "Preview HTML",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val resolvedName = CodeArtifacts.deriveFileName(language.ifBlank { "txt" }, code)
                                val name = FileSaver.saveToDownloads(
                                    context,
                                    resolvedName,
                                    code,
                                )
                                if (name != null) {
                                    savedName = name
                                    android.widget.Toast.makeText(
                                        context,
                                        "Saved to Download/RaikaAI/$name",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    delay(2000)
                                    savedName = null
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to save file",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            if (savedName != null) Icons.Outlined.Check else Icons.Outlined.SaveAlt,
                            contentDescription = "Save code file",
                            modifier = Modifier.size(14.dp),
                            tint = if (savedName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            onCopy()
                            copied = true
                            copyResetJob?.cancel()
                            copyResetJob =
                                scope.launch {
                                    delay(2000)
                                    copied = false
                                }
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(14.dp),
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = displayCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (isCollapsible) {
                Surface(
                    onClick = { isExpanded = !isExpanded },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isExpanded) "Collapse ($totalLines lines)" else "Show all $totalLines lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One-line tool card: icon, verb, command/path, and status. Output stays
 * collapsed (tap to expand, height-capped) so agent loops in the Linux
 * environment no longer flood the screen.
 */
/** How much tool cards show: 0 quiet, 1 normal (tap to open), 2 full (open). */
val LocalToolVerbosity = androidx.compose.runtime.staticCompositionLocalOf { 1 }

@Composable
private fun ToolCard(
    block: Block,
    scheme: ColorScheme,
) {
    val verbosity = LocalToolVerbosity.current
    var expanded by remember(block.text, block.language, verbosity) { mutableStateOf(verbosity >= 2) }
    val hasDetail = verbosity > 0 && block.detail.isNotBlank()
    val status = block.status
    val isResult = status.startsWith("exit=")
    val running = status == "pending" || status.startsWith("writing")
    val exitCode = if (isResult) status.removePrefix("exit=").substringBefore(' ').toIntOrNull() else null
    val timedOut = status.contains("timeout")
    val failed = isResult && (exitCode != 0 || timedOut)
    val (icon, verb) =
        when (block.language) {
            "run" -> Icons.Outlined.Terminal to "Run"
            "ran" -> Icons.Outlined.Terminal to "Ran"
            "write" -> Icons.Outlined.Description to "Write"
            "wrote" -> Icons.Outlined.Description to "Wrote"
            "edit" -> Icons.Outlined.Edit to "Edit"
            "edited" -> Icons.Outlined.Edit to "Edited"
            "read" -> Icons.Outlined.Visibility to "Read"
            "action" -> Icons.Outlined.Bolt to "Action"
            "memory" -> Icons.Outlined.Psychology to "Remembered"
            "mcp" -> Icons.Outlined.Hub to "MCP"
            else -> Icons.Outlined.Build to "Tool"
        }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, (if (failed) scheme.error else scheme.outlineVariant).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = verb,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = block.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                when {
                    running -> {
                        if (status.startsWith("writing")) {
                            Text(
                                text = status.removePrefix("writing · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = scheme.primary)
                    }
                    failed -> {
                        Text(
                            text = if (timedOut) "timed out" else "exit $exitCode",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = "Failed", modifier = Modifier.size(14.dp), tint = scheme.error)
                    }
                    isResult ->
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Done", modifier = Modifier.size(14.dp), tint = scheme.primary)
                    else -> Unit
                }
                if (hasDetail) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = if (expanded) "Hide output" else "Show output",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded && hasDetail) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(scheme.surfaceContainerHighest.copy(alpha = 0.5f))
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = block.detail,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = scheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockquoteCard(
    text: String,
    scheme: ColorScheme,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(scheme.primary.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = rememberAnnotated(text, scheme),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TableCard(
    tableRaw: String,
    scheme: ColorScheme,
) {
    val table = remember(tableRaw) { parseTable(tableRaw) }
    if (table.headers.isEmpty() && table.rows.isEmpty()) return

    val colCount = maxOf(
        table.headers.size,
        table.rows.maxOfOrNull { it.size } ?: 0,
    )
    if (colCount == 0) return

    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            // Header Row
            if (table.headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (c in 0 until colCount) {
                        val headerText = table.headers.getOrNull(c).orEmpty()
                        val align = table.alignments.getOrNull(c) ?: TextAlign.Start
                        Box(
                            modifier = Modifier
                                .widthIn(min = 120.dp, max = 320.dp)
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = rememberAnnotated(headerText, scheme),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = align,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }

            // Data Rows
            table.rows.forEachIndexed { rIdx, row ->
                val rowBg = if (rIdx % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (c in 0 until colCount) {
                        val cellText = row.getOrNull(c).orEmpty()
                        val align = table.alignments.getOrNull(c) ?: TextAlign.Start
                        Box(
                            modifier = Modifier
                                .widthIn(min = 120.dp, max = 320.dp)
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = rememberAnnotated(cellText, scheme),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = align,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (rIdx < table.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }
}

private data class TableData(
    val headers: List<String>,
    val alignments: List<TextAlign>,
    val rows: List<List<String>>,
)

private fun parseTable(raw: String): TableData {
    val lines = raw.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return TableData(emptyList(), emptyList(), emptyList())

    fun splitRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split('|').map { it.trim() }
    }

    val headerLine = lines.getOrNull(0) ?: ""
    val sepLine = lines.getOrNull(1) ?: ""
    val headers = splitRow(headerLine)

    val alignments = if (sepLine.isNotBlank()) {
        splitRow(sepLine).map { col ->
            val c = col.trim()
            when {
                c.startsWith(":") && c.endsWith(":") -> TextAlign.Center
                c.endsWith(":") -> TextAlign.End
                else -> TextAlign.Start
            }
        }
    } else {
        emptyList()
    }

    val rows = lines.drop(2).map { splitRow(it) }
    return TableData(headers, alignments, rows)
}

private enum class BlockType { PARAGRAPH, HEADER_1, HEADER_2, HEADER_3, BULLET, NUMBERED, TASK, BLOCKQUOTE, CODE_BLOCK, TABLE, RULE, TOOL }

private data class Block(
    val type: BlockType,
    val text: String,
    val language: String = "",
    /** Nesting level for list items (2 spaces or 1 tab per level). */
    val indent: Int = 0,
    /** True when a blank line (or a change of block kind) preceded this block. */
    val gapBefore: Boolean = false,
    /** TOOL blocks: collapsible output. */
    val detail: String = "",
    /** TOOL blocks: "pending", "called", "writing · N lines", "exit=0", ... */
    val status: String = "",
)

internal fun parseBlockTypesForTest(markdown: String): List<String> =
    parseBlocks(markdown).map { "${it.type}:${it.indent}:${it.text}" }

private fun leadingIndent(line: String): Int {
    var spaces = 0
    for (c in line) {
        when (c) {
            ' ' -> spaces++
            '\t' -> spaces += 4
            else -> break
        }
    }
    return spaces / 2
}

private fun parseBlocks(markdown: String): List<Block> {
    if (markdown.isBlank()) return emptyList()

    val blocks = mutableListOf<Block>()
    val lines = markdown.lines()
    var inCodeBlock = false
    var codeLang = ""
    val codeContent = StringBuilder()
    var sawBlank = false

    fun add(block: Block) {
        val prev = blocks.lastOrNull()
        val isList = block.type == BlockType.BULLET || block.type == BlockType.NUMBERED || block.type == BlockType.TASK
        val prevIsList = prev != null &&
            (prev.type == BlockType.BULLET || prev.type == BlockType.NUMBERED || prev.type == BlockType.TASK)
        val gap = prev != null && (sawBlank || (isList != prevIsList))
        // Consecutive plain lines form one paragraph (soft line breaks kept),
        // rather than one widget per line.
        if (block.type == BlockType.PARAGRAPH && prev?.type == BlockType.PARAGRAPH && !sawBlank) {
            blocks[blocks.lastIndex] = prev.copy(text = prev.text + "\n" + block.text)
        } else if (block.type == BlockType.BLOCKQUOTE && prev?.type == BlockType.BLOCKQUOTE && !sawBlank) {
            blocks[blocks.lastIndex] = prev.copy(text = prev.text + "\n" + block.text)
        } else {
            blocks.add(block.copy(gapBefore = gap))
        }
        sawBlank = false
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            if (inCodeBlock) {
                if (codeLang == TOOL_FENCE) {
                    val parts = codeContent.toString().trimEnd().lines()
                    add(
                        Block(
                            BlockType.TOOL,
                            text = parts.getOrNull(1).orEmpty(),
                            language = parts.getOrNull(0).orEmpty(),
                            status = parts.getOrNull(2).orEmpty(),
                        ),
                    )
                } else {
                    add(Block(BlockType.CODE_BLOCK, codeContent.toString().trimEnd(), codeLang))
                }
                codeContent.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = trimmed.drop(3).trim().ifBlank { "code" }
                codeContent.clear()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeContent.appendLine(line)
            i++
            continue
        }

        // Table Detection: current line is table row and next line is table separator
        if (isTableRow(trimmed) && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val tableLines = mutableListOf<String>()
            tableLines.add(line)
            tableLines.add(lines[i + 1])
            i += 2
            while (i < lines.size && isTableRow(lines[i])) {
                tableLines.add(lines[i])
                i++
            }
            add(Block(BlockType.TABLE, tableLines.joinToString("\n")))
            continue
        }

        val indent = leadingIndent(line)
        val heading = HEADING_REGEX.matchEntire(trimmed)
        val task = TASK_REGEX.matchEntire(trimmed)
        val numbered = NUMBERED_LIST_REGEX.find(trimmed)
        when {
            trimmed.isEmpty() -> sawBlank = true
            heading != null -> {
                val level = heading.groupValues[1].length
                val type =
                    when (level) {
                        1 -> BlockType.HEADER_1
                        2 -> BlockType.HEADER_2
                        else -> BlockType.HEADER_3
                    }
                add(Block(type, heading.groupValues[2].trim().trimEnd('#').trim()))
            }
            RULE_REGEX.matches(trimmed) -> add(Block(BlockType.RULE, ""))
            trimmed.startsWith(">") -> add(Block(BlockType.BLOCKQUOTE, trimmed.removePrefix(">").trim()))
            task != null ->
                add(Block(BlockType.TASK, task.groupValues[2].trim(), task.groupValues[1].lowercase().ifBlank { " " }, indent))
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                add(Block(BlockType.BULLET, trimmed.drop(2).trim(), indent = indent))
            numbered != null ->
                add(Block(BlockType.NUMBERED, trimmed.substring(numbered.range.last + 1).trim(), numbered.groupValues[1] + ".", indent))
            else -> add(Block(BlockType.PARAGRAPH, line.trimEnd()))
        }
        i++
    }

    if (inCodeBlock && codeContent.isNotEmpty() && codeLang != TOOL_FENCE) {
        add(Block(BlockType.CODE_BLOCK, codeContent.toString().trimEnd(), codeLang))
    }

    return collapseToolResults(blocks).ifEmpty { listOf(Block(BlockType.PARAGRAPH, markdown)) }
}

private val TOOL_HEAD_REGEX = Regex("^`([^`]+)`( \\[auto\\])?$")
private val TOOL_EXIT_REGEX = Regex("^exit=(-?\\d+)( \\(timed out\\))?$")

/**
 * Folds a container result ("`cmd`" line + fence whose last line is
 * "exit=N", see formatContainerRunBlock) into one compact TOOL card. Works
 * for messages saved by older versions too.
 */
private fun collapseToolResults(blocks: List<Block>): List<Block> {
    if (blocks.none { it.type == BlockType.CODE_BLOCK }) return blocks
    val out = mutableListOf<Block>()
    for (block in blocks) {
        val prev = out.lastOrNull()
        val codeLines = if (block.type == BlockType.CODE_BLOCK) block.text.lines() else emptyList()
        val exit = codeLines.lastOrNull()?.trim()?.let { TOOL_EXIT_REGEX.matchEntire(it) }
        val headLine = prev?.takeIf { it.type == BlockType.PARAGRAPH }?.text?.lines()?.lastOrNull()?.trim()
        val head = headLine?.let { TOOL_HEAD_REGEX.matchEntire(it) }
        if (exit != null && head != null && prev != null) {
            val remaining = prev.text.lines().dropLast(1).joinToString("\n").trimEnd()
            if (remaining.isBlank()) out.removeAt(out.lastIndex) else out[out.lastIndex] = prev.copy(text = remaining)
            val title = head.groupValues[1]
            val kind =
                when {
                    title.startsWith("write_file ") -> "wrote"
                    title.startsWith("edit_file ") -> "edited"
                    else -> "ran"
                }
            val shownTitle = title.removePrefix("write_file ").removePrefix("edit_file ")
            val detail = codeLines.dropLast(1).joinToString("\n").trim().takeUnless { it == "(no output)" }.orEmpty()
            val status = "exit=" + exit.groupValues[1] + (if (exit.groupValues[2].isNotEmpty()) " timeout" else "") +
                (if (head.groupValues[2].isNotEmpty()) " auto" else "")
            out.add(Block(BlockType.TOOL, shownTitle, kind, gapBefore = prev.gapBefore || remaining.isNotBlank(), detail = detail, status = status))
        } else {
            out.add(block)
        }
    }
    return out
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") && (trimmed.endsWith("|") || trimmed.count { it == '|' } >= 2)
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("|")) return false
    val cols = trimmed.trim('|').split('|')
    return cols.isNotEmpty() && cols.all { col ->
        val c = col.trim()
        c.isNotEmpty() && c.matches(TABLE_SEPARATOR_CELL_REGEX)
    }
}

/** Citation number -> source URL for the message being rendered ([n] becomes a link). */
val LocalCitationUrls = androidx.compose.runtime.staticCompositionLocalOf<Map<Int, String>> { emptyMap() }

private val CITATION_REGEX = Regex("\\[(\\d{1,2})\\](?!\\()")

// Order matters: ***bold italic*** before **bold** before *italic*.
private val INLINE_REGEX =
    Regex(
        "(\\*\\*\\*[^*\\n]+?\\*\\*\\*)|(\\*\\*[^*\\n]+?\\*\\*)|(__[^_\\n]+?__)|(~~[^~\\n]+?~~)|" +
            "(\\*(?!\\s)[^*\\n]+?(?<!\\s)\\*)|((?<![A-Za-z0-9_])_(?![\\s_])[^_\\n]+?(?<!\\s)_(?![A-Za-z0-9_]))|(`[^`\\n]+?`)|(\\[[^\\]\\n]+\\]\\([^)\\s]+\\))",
    )
private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.*)$")
private val RULE_REGEX = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")
private val TASK_REGEX = Regex("^[-*+]\\s+\\[([ xX]?)\\]\\s+(.*)$")

// B32: hoisted — previously recompiled per line / per table cell.
private val NUMBERED_LIST_REGEX = Regex("^(\\d+)[.)]\\s")
private val TABLE_SEPARATOR_CELL_REGEX = Regex("^:?-+:?$")

/**
 * B31: memoizes the regex-heavy [annotate] pass. Called from composition, so
 * without remember every ~50ms token batch re-ran the inline-format regexes
 * over the whole message (O(n²) while streaming long responses).
 */
@Composable
private fun rememberAnnotated(
    text: String,
    scheme: ColorScheme,
): AnnotatedString {
    val citations = LocalCitationUrls.current
    return remember(text, scheme, citations) { annotate(text, scheme).withCitations(citations, scheme) }
}

/**
 * Turns "[n]" into a small superscript-style citation chip that opens the
 * n-th source. Without a matching source the text is left as is.
 */
private fun AnnotatedString.withCitations(
    citations: Map<Int, String>,
    scheme: ColorScheme,
): AnnotatedString {
    if (citations.isEmpty() || !text.contains('[')) return this
    val matches = CITATION_REGEX.findAll(text).filter { citations.containsKey(it.groupValues[1].toInt()) }.toList()
    if (matches.isEmpty()) return this
    val chipStyle =
        SpanStyle(
            color = scheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            baselineShift = androidx.compose.ui.text.style.BaselineShift(0.3f),
            background = scheme.primaryContainer.copy(alpha = 0.35f),
        )
    return buildAnnotatedString {
        var last = 0
        for (m in matches) {
            append(this@withCitations.subSequence(last, m.range.first))
            val n = m.groupValues[1].toInt()
            val styles = TextLinkStyles(style = chipStyle)
            withLink(LinkAnnotation.Url(citations.getValue(n), styles)) { append("\u2009$n\u2009") }
            last = m.range.last + 1
        }
        append(this@withCitations.subSequence(last, text.length))
    }
}

private fun annotate(
    text: String,
    scheme: ColorScheme,
): AnnotatedString {
    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val codeStyle =
        SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = scheme.surfaceContainerHighest.copy(alpha = 0.8f),
            color = scheme.primary,
            fontSize = 13.sp,
        )
    val linkStyle = SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline)

    return buildAnnotatedString {
        var lastIndex = 0
        for (match in INLINE_REGEX.findAll(text)) {
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }
            val raw = match.value
            when {
                raw.startsWith("***") && raw.endsWith("***") && raw.length > 6 -> {
                    pushStyle(boldStyle)
                    pushStyle(italicStyle)
                    append(raw.substring(3, raw.length - 3))
                    pop()
                    pop()
                }
                (raw.startsWith("**") && raw.endsWith("**")) || (raw.startsWith("__") && raw.endsWith("__")) -> {
                    pushStyle(boldStyle)
                    append(raw.substring(2, raw.length - 2))
                    pop()
                }
                raw.startsWith("~~") && raw.endsWith("~~") -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(raw.substring(2, raw.length - 2))
                    pop()
                }
                (raw.startsWith("*") && raw.endsWith("*")) || (raw.startsWith("_") && raw.endsWith("_") && raw.length > 2) -> {
                    pushStyle(italicStyle)
                    append(raw.substring(1, raw.length - 1))
                    pop()
                }
                raw.startsWith("`") && raw.endsWith("`") -> {
                    pushStyle(codeStyle)
                    append(raw.substring(1, raw.length - 1))
                    pop()
                }
                raw.startsWith("[") && raw.contains("](") -> {
                    val linkText = raw.substringAfter("[").substringBefore("]")
                    val url = raw.substringAfter("](").substringBeforeLast(")")
                    // Link APIs (TextLinkStyles, withLink) live in
                    // androidx.compose.ui.text in current Compose, not
                    // androidx.compose.foundation.text.
                    val linkTextStyles =
                        TextLinkStyles(
                            style = linkStyle,
                            focusedStyle = linkStyle,
                            hoveredStyle = linkStyle,
                            pressedStyle = linkStyle,
                        )
                    withLink(LinkAnnotation.Url(url, linkTextStyles)) {
                        append(linkText)
                    }
                }
                else -> append(raw)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
