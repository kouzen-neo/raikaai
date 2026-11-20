package com.raikaai.app.ui.environment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.raikaai.app.core.container.ContainerManager
import com.raikaai.app.core.container.ContainerManager.TermKind
import com.raikaai.app.core.container.ContainerManager.TermLine
import com.raikaai.app.ui.chat.ChatViewModel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated Linux Environment screen (Fase 3–4): manual Terminal + /workspace Files.
 * Terminal is shell-in-a-box over [ContainerManager.run] (no PTY yet);
 * interactive programs (vim/htop) are out of scope for this milestone.
 */
@Composable
fun EnvironmentScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val manager = remember { viewModel.containerManager }
    val installState by manager.installState.collectAsState()
    val servedPort by manager.servedPort.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var filesTick by remember { mutableIntStateOf(0) }
    val outdated = remember(settings.alpineInstalled, installState) { manager.isOutdated() }
    val fsInstalled = remember(settings.alpineInstalled, installState) { manager.isInstalled() }
    val scope = rememberCoroutineScope()

    val onTerminal = tab == 0 && fsInstalled && !outdated
    // Terminal look: the bar and the shell share one black surface (no card,
    // no margins) so the screen reads as a real terminal; Files keeps the
    // app theme.
    val barColor = if (onTerminal) TermBg else MaterialTheme.colorScheme.background
    val barContent = if (onTerminal) TermMuted else MaterialTheme.colorScheme.onSurfaceVariant
    var menuOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = if (onTerminal) TermBg else MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().background(barColor).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open Menu", tint = barContent)
                }
                if (onTerminal) {
                    TerminalTabStrip(viewModel = viewModel, modifier = Modifier.weight(1f))
                } else {
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            if (tab == 1 && fsInstalled) "Files" else "Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = servedPort?.let { "Serving :$it · /workspace" }
                                ?: if (fsInstalled) "Alpine ${settings.alpineVersion} · /workspace" else "Alpine via PRoot (no root)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tab == 0 && viewModel.activeTerminalSession.ptyInput != null) {
                    // Gboard hides its clipboard in the key-capture field, so paste here.
                    IconButton(
                        onClick = { viewModel.pasteToTerminal() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = barContent)
                    }
                }
                if (fsInstalled && !outdated) {
                    IconButton(
                        onClick = {
                            tab = if (tab == 0) 1 else 0
                            if (tab == 1) filesTick++
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (tab == 0) Icons.Outlined.Folder else Icons.Outlined.Terminal,
                            contentDescription = if (tab == 0) "Files" else "Terminal",
                            tint = barContent,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = barContent)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            servedPort?.let { "Serving :$it" } ?: "Alpine ${settings.alpineVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        if (fsInstalled && !outdated) {
                            DropdownMenuItem(
                                text = { Text("New tab") },
                                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                onClick = { menuOpen = false; tab = 0; viewModel.newTerminalSession() },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear screen") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menuOpen = false; viewModel.clearTerminal(viewModel.activeTerminalSession.id) },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy output") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menuOpen = false; viewModel.copyTerminalOutput() },
                            )
                            if (viewModel.activeTerminalSession.isRunning) {
                                DropdownMenuItem(
                                    text = { Text("Stop program") },
                                    leadingIcon = { Icon(Icons.Outlined.Close, null) },
                                    onClick = { menuOpen = false; viewModel.cancelTerminal(viewModel.activeTerminalSession.id) },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Copy diagnostics") },
                            leadingIcon = { Icon(Icons.Outlined.Info, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch(Dispatchers.IO) {
                                    val text = manager.diagnostics()
                                    withContext(Dispatchers.Main) {
                                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("env-diag", text))
                                        android.widget.Toast.makeText(context, "Diagnostics copied", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Back to chat") },
                            leadingIcon = { Icon(Icons.Default.Send, null) },
                            onClick = { menuOpen = false; onNavigateToChat() },
                        )
                    }
                }
            }

            if (!fsInstalled || outdated) {
                NotInstalledCard(
                    installState = installState,
                    onInstall = { manager.install() },
                    onCancel = { manager.cancelInstall() },
                    title = if (outdated) "Environment update available" else "Linux Environment not installed",
                    desc = if (outdated) {
                        "The on-device rootfs uses an old format. Press Update to reinstall it (downloads ~3 MB, /workspace is kept)."
                    } else {
                        "Downloads the Alpine minirootfs (~3 MB). proot ships inside the app and can be removed anytime."
                    },
                    actionLabel = if (outdated) "Update Alpine" else "Install Alpine",
                )
                return@Column
            }

            // Live listener banner: any dev server started from chat or the
            // terminal is discovered by port probe; open it in one tap.
            val previewUrl by viewModel.containerPreviewUrl.collectAsState()
            if (!previewUrl.isNullOrBlank()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(if (onTerminal) Color(0xFF14261A) else MaterialTheme.colorScheme.primaryContainer)
                            .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(6.dp).background(TermGreen, RoundedCornerShape(3.dp)))
                    Text(
                        text = previewUrl.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (onTerminal) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.refreshContainerPreview() }) { Text("Re-check") }
                    TextButton(
                        onClick = {
                            previewUrl?.let { url ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                    )
                                }.onFailure {
                                    android.widget.Toast.makeText(
                                        context, "No browser available", android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    ) { Text("Open", fontWeight = FontWeight.SemiBold) }
                }
            }

            if (tab == 0) {
                TerminalTab(
                    viewModel = viewModel,
                    manager = manager,
                    onCommandDone = { filesTick++ },
                )
            } else {
                FilesTab(
                    key = filesTick,
                    manager = manager,
                    viewModel = viewModel,
                    onNavigateToChat = onNavigateToChat,
                )
            }
        }
    }
}

@Composable
private fun NotInstalledCard(
    installState: ContainerManager.InstallState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Linux Environment not installed",
    desc: String = "Downloads the Alpine minirootfs (~3 MB). proot ships inside the app and can be removed anytime.",
    actionLabel: String = "Install Alpine",
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val st = installState) {
                is ContainerManager.InstallState.Downloading -> {
                    Text("Downloading ${st.label}… ${(st.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is ContainerManager.InstallState.Extracting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Extracting ${st.label}…", style = MaterialTheme.typography.labelSmall)
                    }
                }
                is ContainerManager.InstallState.Error -> {
                    Text("Failed: ${st.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onInstall) { Text("Retry") }
                }
                else -> {
                    Button(onClick = onInstall) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

private val TermGreen = Color(0xFF4CAF50)
private val TermBg = Color(0xFF000000)
private const val MIN_FONT_SP = 7f
private const val MAX_FONT_SP = 26f
private val TermMuted = Color(0xFF9E9E9E)

@Composable
private fun TerminalTab(
    viewModel: ChatViewModel,
    manager: ContainerManager,
    onCommandDone: () -> Unit,
) {
    val sessions = viewModel.terminalSessions
    val activeSession = viewModel.activeTerminalSession
    val lines = activeSession.lines
    val history = activeSession.history
    val running = activeSession.isRunning
    // PTY mode with a program running: keys go straight to the program.
    val passThrough = activeSession.ptyInput != null
    val grid = activeSession.grid
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var histPos by remember(activeSession.id) { mutableIntStateOf(activeSession.histPos) }
    var input by remember(activeSession.id) { mutableStateOf(activeSession.input) }

    LaunchedEffect(input) { activeSession.input = input }
    LaunchedEffect(histPos) { activeSession.histPos = histPos }

    // Sticky modifiers like Termux: tap one, then tap the target key.
    // Only one sticky at a time; any tap consumes it.
    var ctrlActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var selAnchor by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    // Termux keys must hand focus back to the input, otherwise the IME keeps
    // a stale buffer and typed text appears to vanish.
    val fieldFocus = remember { FocusRequester() }

    // The prompt is the last item of the stream (banner + lines + prompt):
    // follow it when output arrives or the session changes, and bring it back
    // into view as soon as the user types while scrolled up.
    val lastIndex = (if (lines.isEmpty()) 1 else 0) + lines.size
    LaunchedEffect(lines.size, activeSession.id) {
        listState.animateScrollToItem(lastIndex)
    }
    LaunchedEffect(input.text) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible < lastIndex) listState.scrollToItem(lastIndex)
    }
    // Keyboard opening shrinks the viewport; keep the prompt above it.
    val latestLastIndex by rememberUpdatedState(lastIndex)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .distinctUntilChanged()
            .collect { listState.scrollToItem(latestLastIndex) }
    }

    fun setInputText(text: String) {
        input = TextFieldValue(text, selection = TextRange(text.length))
        selAnchor = -1
    }

    /** Inserts at the cursor and keeps IME focus (keys must not jump to the end). */
    fun insertAtCursor(s: String) {
        val cur = input
        val len = cur.text.length
        val lo = minOf(cur.selection.start, cur.selection.end).coerceIn(0, len)
        val hi = maxOf(cur.selection.start, cur.selection.end).coerceIn(0, len)
        val newText = cur.text.substring(0, lo) + s + cur.text.substring(hi)
        input = cur.copy(text = newText, selection = TextRange(lo + s.length))
        selAnchor = -1
        fieldFocus.requestFocus()
    }

    fun exec(cmd: String) {
        val clean = cmd.trim()
        if (clean.isEmpty() || running) return
        input = TextFieldValue("")
        histPos = -1
        viewModel.execTerminal(clean, sessionId = activeSession.id) {
            onCommandDone()
        }
    }

    fun moveCursor(delta: Int) {
        val pos = (input.selection.end + delta).coerceIn(0, input.text.length)
        input = input.copy(selection = TextRange(pos))
        selAnchor = -1
        fieldFocus.requestFocus()
    }

    fun isWordChar(c: Char) = c.isLetterOrDigit() || c in "_./~-$"

    fun moveCursorWord(dir: Int) {
        val t = input.text
        var p = input.selection.end.coerceIn(0, t.length)
        if (dir < 0) {
            while (p > 0 && !isWordChar(t[p - 1])) p--
            while (p > 0 && isWordChar(t[p - 1])) p--
        } else {
            while (p < t.length && !isWordChar(t[p])) p++
            while (p < t.length && isWordChar(t[p])) p++
        }
        input = input.copy(selection = TextRange(p))
        selAnchor = -1
        fieldFocus.requestFocus()
    }

    /** Extends the selection from the anchor (SHIFT + arrows/END). */
    fun extendSelectionTo(pos: Int) {
        val t = input.text
        val p = pos.coerceIn(0, t.length)
        val a = if (selAnchor < 0) input.selection.end.coerceIn(0, t.length) else selAnchor
        selAnchor = a
        input = input.copy(selection = TextRange(minOf(a, p), maxOf(a, p)))
        fieldFocus.requestFocus()
    }

    fun clearModifiers() {
        ctrlActive = false
        shiftActive = false
        altActive = false
    }

    fun arrowLeft() {
        val a = altActive
        val s = shiftActive
        clearModifiers()
        when {
            a -> moveCursorWord(-1)
            s -> extendSelectionTo(input.selection.end - 1)
            else -> moveCursor(-1)
        }
    }

    fun arrowRight() {
        val a = altActive
        val s = shiftActive
        clearModifiers()
        when {
            a -> moveCursorWord(1)
            s -> extendSelectionTo(input.selection.end + 1)
            else -> moveCursor(1)
        }
    }

    fun goEnd() {
        val s = shiftActive
        clearModifiers()
        if (s) extendSelectionTo(input.text.length)
        else {
            input = input.copy(selection = TextRange(input.text.length))
            fieldFocus.requestFocus()
        }
    }

    /** Completes the current token against /workspace (TAB). */
    fun tabComplete() {
        clearModifiers()
        val text = input.text
        val cursor = input.selection.end.coerceIn(0, text.length)
        val before = text.substring(0, cursor)
        val cut = before.indexOfLast { it == ' ' || it == '\t' || it == ';' || it == '|' || it == '&' || it == '(' || it == '"' || it == '\'' }
        val token = before.substring(cut + 1)
        if (token.isEmpty()) return
        val root = manager.workspaceDir
        val dirPart = token.substringBeforeLast('/', "")
        val prefix = token.substringAfterLast('/')
        val base = if (dirPart.startsWith("/")) File(root, dirPart.removePrefix("/")) else File(root, dirPart)
        val baseOk = runCatching {
            base.canonicalPath == root.canonicalPath || base.canonicalPath.startsWith(root.canonicalPath + File.separator)
        }.getOrDefault(false) && base.isDirectory
        if (!baseOk) return
        val cands = (base.listFiles() ?: return).filter { it.name.startsWith(prefix) }.sortedBy { it.name }
        if (cands.isEmpty()) return
        if (cands.size == 1) {
            insertAtCursor(cands[0].name.removePrefix(prefix) + if (cands[0].isDirectory) "/" else " ")
        } else {
            val rest = cands.map { it.name.removePrefix(prefix) }
            val common = rest.reduce { acc, s -> acc.commonPrefixWith(s) }
            if (common.isNotEmpty()) insertAtCursor(common)
            lines.add(TermLine(TermKind.SYS, cands.joinToString("  ") { it.name + if (it.isDirectory) "/" else "" }.take(600)))
        }
        fieldFocus.requestFocus()
    }

    fun historyStep(dir: Int) {
        if (history.isEmpty()) return
        clearModifiers()
        histPos = (histPos + dir).coerceIn(-1, history.size - 1)
        setInputText(if (histPos < 0) "" else history[history.size - 1 - histPos])
        fieldFocus.requestFocus()
    }

    /** Sticky-modifier combos from typed letters (CTRL/ALT/SHIFT then a key). */
    fun onInputChange(v: TextFieldValue) {
        if ((ctrlActive || altActive) && v.text.length > input.text.length) {
            val key = v.text.last().lowercaseChar()
            val wasAlt = altActive
            clearModifiers()
            if (!wasAlt && key == 'c') {
                viewModel.cancelTerminal(activeSession.id)
            } else if (!wasAlt && key == 'l') {
                viewModel.clearTerminal(activeSession.id)
            } else if (!wasAlt && key == 'u') {
                setInputText("")
            } else {
                input = v
                histPos = -1
            }
            selAnchor = -1
            fieldFocus.requestFocus()
            return
        }
        if (shiftActive && v.text.length > input.text.length) {
            shiftActive = false
            altActive = false
            ctrlActive = false
            input = v.copy(text = v.text.dropLast(1) + v.text.last().uppercaseChar())
            histPos = -1
            selAnchor = -1
            fieldFocus.requestFocus()
            return
        }
        input = v
        histPos = -1
        selAnchor = -1
    }

    // ── Pass-through input (PTY mode) ──
    // The IME needs a text field; it holds a fixed sentinel so typed text and
    // backspaces (which an empty field never reports) can be told apart.
    val sentinel = "  "
    var ptyField by remember { mutableStateOf(TextFieldValue(sentinel, TextRange(sentinel.length))) }

    fun sendKey(seq: String) {
        viewModel.sendTerminalInput(seq, activeSession.id)
        fieldFocus.requestFocus()
    }

    /** Applies the sticky CTRL/ALT/SHIFT to one typed character. */
    fun keyFor(ch: Char): String {
        val ctrl = ctrlActive
        val alt = altActive
        val shift = shiftActive
        clearModifiers()
        if (ch == '\n') return "\r"
        var out =
            if (ctrl) {
                when (val l = ch.lowercaseChar()) {
                    in 'a'..'z' -> (l.code - 96).toChar().toString()
                    ' ', '@', '2' -> "\u0000"
                    '[', '3' -> "\u001B"
                    '\\', '4' -> "\u001C"
                    ']', '5' -> "\u001D"
                    '^', '6' -> "\u001E"
                    '_', '-', '7' -> "\u001F"
                    '?', '8' -> "\u007F"
                    else -> ch.toString()
                }
            } else if (shift) {
                ch.uppercaseChar().toString()
            } else {
                ch.toString()
            }
        if (alt) out = "\u001B" + out
        return out
    }

    fun onPtyChange(v: TextFieldValue) {
        val t = v.text
        val common = t.commonPrefixWith(sentinel).length
        val deleted = sentinel.length - common
        val added = t.substring(common)
        if (deleted > 0 || added.isNotEmpty()) {
            val sb = StringBuilder()
            repeat(deleted) { sb.append('\u007F') }
            added.forEach { sb.append(keyFor(it)) }
            viewModel.sendTerminalInput(sb.toString(), activeSession.id)
        }
        ptyField = TextFieldValue(sentinel, TextRange(sentinel.length))
    }

    /** Cursor/edit key with xterm modifier encoding (SHIFT=2, ALT=3, CTRL=5). */
    fun csiKey(final: Char) {
        val mod = when { ctrlActive -> 5; altActive -> 3; shiftActive -> 2; else -> 1 }
        clearModifiers()
        sendKey(if (mod == 1) "\u001B[$final" else "\u001B[1;$mod$final")
    }

    var symPage by remember { mutableStateOf(false) }
    // Pinch-to-zoom font size, remembered across launches.
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("terminal", android.content.Context.MODE_PRIVATE) }
    var fontSp by remember { mutableStateOf(prefs.getFloat("font_sp", 12f).coerceIn(MIN_FONT_SP, MAX_FONT_SP)) }
    LaunchedEffect(fontSp) {
        kotlinx.coroutines.delay(400)
        prefs.edit().putFloat("font_sp", fontSp).apply()
    }
    val termText = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSp.sp,
        lineHeight = (fontSp * 1.3f).sp,
        letterSpacing = 0.sp,
    )
    // Columns = usable width / one monospace cell, so programs format to fit.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cellWidthF = remember(fontSp) { measurer.measure("MMMMMMMMMM", termText).size.width / 10f }
    val cellWidthPx = cellWidthF.toInt().coerceAtLeast(1)
    val cellHeightF = with(density) { (fontSp * 1.3f).sp.toPx() }
    var areaSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    LaunchedEffect(areaSize, cellWidthF, cellHeightF) {
        if (areaSize.width > 0 && areaSize.height > 0) {
            // The area has no padding of its own (a full-screen program paints
            // edge to edge); the scrolling view keeps an 8dp/6dp inset.
            val padX = with(density) { 8.dp.toPx() } * 2
            val padY = with(density) { 6.dp.toPx() } * 2
            val cols = ((areaSize.width - padX) / cellWidthF).toInt().coerceIn(20, 400)
            val rows = ((areaSize.height - padY) / cellHeightF).toInt().coerceIn(4, 300)
            viewModel.terminalCellPx = cellWidthPx to cellHeightF.toInt()
            viewModel.resizeTerminal(cols, rows)
        }
    }
    fun promptOf(cwd: String?) =
        buildAnnotatedString {
            withStyle(SpanStyle(color = TermGreen, fontWeight = FontWeight.Bold)) { append("root@raika") }
            append(":")
            withStyle(SpanStyle(color = Color(0xFF61AFEF), fontWeight = FontWeight.Bold)) {
                append((cwd ?: "/workspace").replaceFirst(Regex("^/root(?=/|$)"), "~"))
            }
            append("# ")
        }

    Column(modifier = Modifier.fillMaxSize().background(TermBg).imePadding().navigationBarsPadding()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {
                        fieldFocus.requestFocus()
                        keyboard?.show()
                    }
                    .onSizeChanged { areaSize = it }
                    .pointerInput(Unit) {
                        // Two-finger pinch changes the font size; one finger
                        // still scrolls (events are only consumed while pinching).
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f) {
                                        fontSp = (fontSp * zoom).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
        ) {
            // One scrolling stream like a real terminal: banner, output, and
            // the prompt as the LAST line right under the latest output (not
            // pinned to the bottom of the screen).
            if (grid != null) {
                // Full-screen program (alternate screen): fixed cell grid.
                TerminalGridCanvas(
                    grid = grid,
                    version = { activeSession.gridVersion },
                    style = termText.copy(lineHeight = (fontSp * 1.3f).sp),
                    cellWidth = cellWidthF,
                    cellHeight = cellHeightF,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(grid, cellWidthF, cellHeightF) {
                                // Touch as a mouse, for programs that ask for one:
                                // tap = click, swipe = wheel, long press + drag =
                                // drag (selects text; opencode copies it via OSC 52).
                                val slop = viewConfiguration.touchSlop
                                fun cellOf(pos: androidx.compose.ui.geometry.Offset): Pair<Int, Int> {
                                    val ox = ((size.width - grid.cols * cellWidthF) / 2f).coerceAtLeast(0f)
                                    val oy = ((size.height - grid.rows * cellHeightF) / 2f).coerceAtLeast(0f)
                                    val col = ((pos.x - ox) / cellWidthF).toInt().coerceIn(0, grid.cols - 1)
                                    val row = ((pos.y - oy) / cellHeightF).toInt().coerceIn(0, grid.rows - 1)
                                    return col to row
                                }
                                fun send(seq: String?) {
                                    if (seq != null) viewModel.sendTerminalInput(seq, activeSession.id)
                                }
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val start = down.position
                                    // 0 undecided, 1 wheel scroll, 2 drag, 3 multi-touch (pinch)
                                    var mode = 0
                                    var wheelY = start.y
                                    var lastCell = cellOf(start)
                                    while (true) {
                                        val ev =
                                            if (mode == 0) {
                                                withTimeoutOrNull(450L) { awaitPointerEvent() }
                                            } else {
                                                awaitPointerEvent()
                                            }
                                        if (ev == null) {
                                            // Held still: long press starts a drag.
                                            if (grid.mouseTracking != 0) {
                                                mode = 2
                                                send(grid.mouseEvent(0, lastCell.first, lastCell.second, press = true))
                                            } else {
                                                mode = 1
                                            }
                                            continue
                                        }
                                        if (ev.changes.count { it.pressed } >= 2) mode = 3
                                        if (mode == 3) {
                                            if (ev.changes.none { it.pressed }) break
                                            continue
                                        }
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) {
                                            when (mode) {
                                                0 -> {
                                                    send(grid.mouseEvent(0, lastCell.first, lastCell.second, press = true))
                                                    send(grid.mouseEvent(0, lastCell.first, lastCell.second, press = false))
                                                    fieldFocus.requestFocus()
                                                    keyboard?.show()
                                                }
                                                2 -> {
                                                    val (c, r) = cellOf(ch.position)
                                                    send(grid.mouseEvent(0, c, r, press = false))
                                                }
                                            }
                                            ch.consume()
                                            break
                                        }
                                        val d = ch.position - start
                                        if (mode == 0 && (kotlin.math.abs(d.x) > slop || kotlin.math.abs(d.y) > slop)) mode = 1
                                        if (mode == 1) {
                                            // Finger down shows earlier content (wheel up), like a touch list.
                                            while (ch.position.y - wheelY > cellHeightF) {
                                                send(grid.mouseEvent(64, lastCell.first, lastCell.second, press = true))
                                                wheelY += cellHeightF
                                            }
                                            while (wheelY - ch.position.y > cellHeightF) {
                                                send(grid.mouseEvent(65, lastCell.first, lastCell.second, press = true))
                                                wheelY -= cellHeightF
                                            }
                                        } else if (mode == 2) {
                                            val cell = cellOf(ch.position)
                                            if (cell != lastCell) {
                                                lastCell = cell
                                                send(grid.mouseEvent(0, cell.first, cell.second, press = true, motion = true))
                                            }
                                        }
                                        ch.consume()
                                    }
                                }
                            },
                )
            } else LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (lines.isEmpty()) {
                    item(key = "banner") {
                        Text(
                            "Alpine Linux · /workspace\n" +
                                "TAB completes paths · ↑↓ history · CTRL+C stops · CTRL+L clears\n" +
                                "Add '&' to keep a server running (npm run dev &)",
                            style = termText,
                            color = TermMuted,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                items(lines.size) { i ->
                    val line = lines[i]
                    // Long press selects and copies a line.
                    androidx.compose.foundation.text.selection.SelectionContainer {
                    when (line.kind) {
                        TermKind.CMD -> Text(
                            promptOf(line.cwd) + AnnotatedString(line.text),
                            style = termText,
                            color = Color.White,
                        )
                        TermKind.ERR -> Text(line.rich ?: AnnotatedString(line.text), style = termText, color = Color(0xFFFF6B6B))
                        TermKind.SYS -> Text(line.text, style = termText, color = TermMuted)
                        TermKind.OUT -> Text(line.rich ?: AnnotatedString(line.text), style = termText, color = Color(0xFFE6E6E6))
                    }
                    }
                }
                if (!passThrough) item(key = "prompt") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (running) {
                    Text("… ", style = termText, fontWeight = FontWeight.Bold, color = Color(0xFFFFC107))
                } else {
                    Text(promptOf(activeSession.cwd), style = termText, color = Color.White)
                }
                BasicTextField(
                    value = input,
                    onValueChange = ::onInputChange,
                    modifier = Modifier.weight(1f).focusRequester(fieldFocus),
                    textStyle = termText.copy(color = Color.White),
                    cursorBrush = SolidColor(TermGreen),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { exec(input.text) }),
                )
                if (running) {
                    IconButton(
                        onClick = { viewModel.cancelTerminal(activeSession.id) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Stop (^C)",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
                }
            }
        }

        if (passThrough) {
            // Invisible field that feeds keystrokes to the program.
            BasicTextField(
                value = ptyField,
                onValueChange = ::onPtyChange,
                modifier = Modifier.size(1.dp).alpha(0f).focusRequester(fieldFocus),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = ImeAction.Send,
                        autoCorrectEnabled = false,
                    ),
                keyboardActions = KeyboardActions(onSend = { sendKey("\r") }),
            )
            LaunchedEffect(Unit) { fieldFocus.requestFocus() }
        }

        // Extra keys: two rows of eight equal cells. SHIFT and SYM stay in
        // the last column; SYM swaps the first seven columns for shell
        // symbols. CTRL/ALT/SHIFT are one-shot: tap, then the next key.
        HorizontalDivider(color = Color(0xFF1E1E1E))
        val ctrlKey = ExtraKey("CTRL", active = ctrlActive) {
            val on = !ctrlActive
            clearModifiers()
            ctrlActive = on
            fieldFocus.requestFocus()
        }
        val altKey = ExtraKey("ALT", active = altActive) {
            val on = !altActive
            clearModifiers()
            altActive = on
            fieldFocus.requestFocus()
        }
        val shiftKey = ExtraKey("SHIFT", active = shiftActive) {
            val on = !shiftActive
            clearModifiers()
            shiftActive = on
            fieldFocus.requestFocus()
        }
        val symKey = ExtraKey("SYM", active = symPage) { symPage = !symPage; fieldFocus.requestFocus() }
        fun sym(s: String) = ExtraKey(s) { if (passThrough) sendKey(keyFor(s.single())) else insertAtCursor(s) }
        val rows =
            if (!symPage) {
                listOf(
                    listOf(
                        ExtraKey("ESC") {
                            if (passThrough) { clearModifiers(); sendKey("\u001B") }
                            else { input = TextFieldValue(""); clearModifiers(); fieldFocus.requestFocus() }
                        },
                        sym("/"),
                        sym("-"),
                        ExtraKey("HOME") {
                            if (passThrough) { csiKey('H'); return@ExtraKey }
                            clearModifiers()
                            input = input.copy(selection = TextRange(0))
                            fieldFocus.requestFocus()
                        },
                        ExtraKey("↑", repeats = true) { if (passThrough) csiKey('A') else historyStep(1) },
                        ExtraKey("END") { if (passThrough) csiKey('F') else goEnd() },
                        sym("|"),
                        shiftKey,
                    ),
                    listOf(
                        ExtraKey("TAB") {
                            if (passThrough) {
                                val back = shiftActive
                                clearModifiers()
                                sendKey(if (back) "\u001B[Z" else "\t")
                            } else {
                                tabComplete()
                            }
                        },
                        ctrlKey,
                        altKey,
                        ExtraKey("←", repeats = true) { if (passThrough) csiKey('D') else arrowLeft() },
                        ExtraKey("↓", repeats = true) { if (passThrough) csiKey('B') else historyStep(-1) },
                        ExtraKey("→", repeats = true) { if (passThrough) csiKey('C') else arrowRight() },
                        sym("~"),
                        symKey,
                    ),
                )
            } else {
                listOf(
                    listOf(sym("`"), sym("$"), sym("*"), sym("="), sym("^"), sym("\\"), sym("&"), sym("%")),
                    listOf(sym("["), sym("]"), sym("{"), sym("}"), sym("<"), sym(">"), sym(";"), symKey),
                )
            }
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                row.forEach { key -> ExtraKeyCell(key, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** One extra key: label, highlighted while its sticky modifier is on. */
private class ExtraKey(
    val label: String,
    val active: Boolean = false,
    val repeats: Boolean = false,
    val onTap: () -> Unit,
)

@Composable
private fun ExtraKeyCell(
    key: ExtraKey,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val tapState = rememberUpdatedState(key.onTap)
    Box(
        modifier =
            modifier.pointerInput(key.label, key.repeats) {
                awaitEachGesture {
                    awaitFirstDown()
                    // Holding an arrow repeats it, like a hardware key.
                    val repeatJob =
                        if (key.repeats) {
                            scope.launch {
                                tapState.value()
                                delay(400)
                                while (true) {
                                    tapState.value()
                                    delay(60)
                                }
                            }
                        } else {
                            null
                        }
                    val up = waitForUpOrCancellation()
                    repeatJob?.cancel()
                    if (!key.repeats && up != null) tapState.value()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key.label,
            fontFamily = FontFamily.Monospace,
            fontSize = if (key.label.length > 3) 11.sp else 13.sp,
            fontWeight = if (key.active) FontWeight.Bold else FontWeight.Normal,
            color = if (key.active) TermGreen else TermMuted,
            maxLines = 1,
        )
        if (key.active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(width = 14.dp, height = 2.dp)
                    .background(TermGreen, RoundedCornerShape(1.dp)),
            )
        }
    }
}

/** Numbered terminal tabs in the top bar (running dot, close on the active tab). */
@Composable
private fun TerminalTabStrip(
    viewModel: ChatViewModel,
    modifier: Modifier,
) {
    val sessions = viewModel.terminalSessions
    val active = viewModel.activeTerminalSession
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sessions.forEachIndexed { index, s ->
            val selected = s.id == active.id
            Row(
                modifier =
                    Modifier
                        .height(48.dp)
                        .clickable { viewModel.selectTerminalSession(s.id) }
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else TermMuted,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    s.title,
                    fontSize = 13.sp,
                    color = if (selected) Color.White else TermMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                if (s.isRunning) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(Color(0xFFFFC107), RoundedCornerShape(3.dp)))
                }
                if (selected && sessions.size > 1) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close tab",
                        tint = TermMuted,
                        modifier = Modifier.size(16.dp).clickable { viewModel.closeTerminalSession(s.id) },
                    )
                }
            }
        }
        IconButton(onClick = { viewModel.newTerminalSession() }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "New tab", tint = TermMuted, modifier = Modifier.size(20.dp))
        }
    }
}


@Composable
private fun FilesTab(
    key: Int,
    manager: ContainerManager,
    viewModel: ChatViewModel,
    onNavigateToChat: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember { manager.workspaceDir }
    var currentDir by remember { mutableStateOf(manager.workspaceDir) }
    var files by remember { mutableStateOf(listOf<File>()) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val list = manager.workspaceFiles(currentDir)
            withContext(Dispatchers.Main) { files = list }
        }
    }
    LaunchedEffect(key, currentDir) { refresh() }

    val relPath = remember(currentDir) {
        runCatching {
            val rel = File(currentDir.canonicalPath.removePrefix(rootDir.canonicalPath)).path
                .removePrefix(File.separator)
            if (rel.isBlank()) "/workspace" else "/workspace/$rel"
        }.getOrDefault("/workspace")
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val name = sanitizeFileName(queryDisplayName(context, uri) ?: "import_${System.currentTimeMillis()}")
                val dest = File(currentDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    toast(context, "Imported $name")
                    refresh()
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) { toast(context, "Import failed") }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$relPath (${files.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import") }
        }
        Spacer(Modifier.height(4.dp))
        if (currentDir != rootDir && isWithinRoot(rootDir, currentDir)) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                onClick = {
                    val parent = currentDir.parentFile
                    if (parent != null && isWithinRoot(rootDir, parent)) currentDir = parent else currentDir = rootDir
                },
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("↑", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(".. (up)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (files.isEmpty()) {
            Text(
                "This folder is empty. Import a file or ask the AI to save results here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.absolutePath }) { f ->
                    val isDir = f.isDirectory
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (isDir) {
                                if (isWithinRoot(rootDir, f)) currentDir = f
                            } else if (isTextFile(f)) {
                                scope.launch(Dispatchers.IO) {
                                    val text = runCatching {
                                        f.bufferedReader().use { r ->
                                            val sb = StringBuilder()
                                            var c: Int
                                            var n = 0
                                            while (r.read().also { c = it } != -1 && n < 100_000) {
                                                sb.append(c.toChar())
                                                n++
                                            }
                                            sb.toString()
                                        }
                                    }.getOrNull() ?: "(unreadable)"
                                    withContext(Dispatchers.Main) {
                                        previewFile = f
                                        previewText = text
                                    }
                                }
                            } else {
                                openWith(context, f)
                            }
                        },
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isTextFile(f)) Icons.Outlined.InsertDriveFile else Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = if (isDir) {
                                        "Folder · ${java.text.SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(java.util.Date(f.lastModified()))}"
                                    } else {
                                        "${formatBytes(f.length())} · ${java.text.SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(java.util.Date(f.lastModified()))}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { shareFile(context, f) }) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    saveToDownload(context, f)
                                    withContext(Dispatchers.Main) { refresh() }
                                }
                            }) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = "Save", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                val uri = fileUri(context, f)
                                viewModel.addAttachments(listOf(uri))
                                onNavigateToChat()
                            }) {
                                Icon(Icons.Default.Send, contentDescription = "Send to Chat", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val ok = runCatching {
                                        if (f.isDirectory) f.deleteRecursively() else f.delete()
                                    }.getOrDefault(false)
                                    withContext(Dispatchers.Main) {
                                        toast(context, if (ok) "Deleted ${f.name}" else "Delete failed")
                                        refresh()
                                    }
                                }
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewFile != null) {
        AlertDialog(
            onDismissRequest = { previewFile = null; previewText = null },
            title = { Text(previewFile!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Text(
                    previewText ?: "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { previewFile = null; previewText = null }) { Text("Close") }
            },
        )
    }
}

// ── helpers ───────────────────────────────────────────────────────────

private fun sanitizeFileName(name: String): String {
    val clean = name.substringAfterLast('/').take(128).trim()
    if (clean.isEmpty() || clean == "." || clean == "..") return "import_${System.currentTimeMillis()}"
    return clean.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

private fun isTextFile(f: File): Boolean {
    if (f.isDirectory) return false
    val ext = f.extension.lowercase()
    return ext in setOf("txt", "md", "csv", "json", "xml", "yml", "yaml", "log", "py", "js", "ts", "sh", "kt", "java", "html", "css", "toml", "ini", "conf", "rc") || f.length() < 200_000 && ext.isEmpty()
}

/** True when [f] lives inside [root] (prevents escaping /workspace). */
private fun isWithinRoot(root: File, f: File): Boolean {
    return runCatching {
        f.canonicalPath == root.canonicalPath || f.canonicalPath.startsWith(root.canonicalPath + File.separator)
    }.getOrDefault(false)
}

private fun toast(context: Context, msg: String) {
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}

private fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val kb = n / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024)
}

private fun fileUri(context: Context, f: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)

private fun openWith(context: Context, f: File) {
    runCatching {
        val uri = fileUri(context, f)
        val mime = java.net.URLConnection.guessContentTypeFromName(f.name) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${f.name}"))
    }
}

private fun shareFile(context: Context, f: File) {
    runCatching {
        val uri = fileUri(context, f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = java.net.URLConnection.guessContentTypeFromName(f.name) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${f.name}"))
    }
}

private fun saveToDownload(context: Context, f: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, java.net.URLConnection.guessContentTypeFromName(f.name) ?: "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RaikaAI")
            put(MediaStore.MediaColumns.SIZE, f.length())
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(itemUri)?.use { out ->
            f.inputStream().use { input -> input.copyTo(out) }
        }
    } else {
        @Suppress("DEPRECATION")
        val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RaikaAI/${f.name}")
        dest.parentFile?.mkdirs()
        f.copyTo(dest, overwrite = true)
        // Make it visible to gallery/file managers.
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dest)))
    }
}
