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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
    backend: TerminalBackend? = null,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val manager = remember { viewModel.containerManager }
    val runtimeMode = viewModel.settings.value.runtimeMode
    val terminalBackend: TerminalBackend = remember(runtimeMode, backend) {
        backend ?: if (runtimeMode == com.raikaai.app.runtime.RuntimeMode.KAZETERM_PI.name) {
            KazeTermTerminalBackend()
        } else {
            ProotTerminalBackend(viewModel, manager)
        }
    }
    val installState by manager.installState.collectAsState()
    val servedPort by manager.servedPort.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var filesTick by remember { mutableIntStateOf(0) }
    val outdated = remember(settings.alpineInstalled, installState) { manager.isOutdated() }
    val fsInstalled = remember(settings.alpineInstalled, installState) { manager.isInstalled() }
    // Alpine stays the default; Debian is an explicit opt-in download.
    var selectedDistro by remember { mutableStateOf(DistroChoice.ALPINE) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Linux Environment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = servedPort?.let { "Serving :$it · /workspace" }
                            ?: if (fsInstalled) "Alpine ${settings.alpineVersion} · /workspace" else "Alpine via PRoot (no root)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val text = manager.diagnostics()
                            withContext(Dispatchers.Main) {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("env-diag", text))
                                android.widget.Toast.makeText(context, "Diagnostics copied", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Copy diagnostics")
                }
            }

            if (!fsInstalled || outdated) {
                val debianSelected = selectedDistro == DistroChoice.DEBIAN
                NotInstalledCard(
                    installState = installState,
                    selected = selectedDistro,
                    onSelect = { choice ->
                        manager.cancelInstall()
                        selectedDistro = choice
                    },
                    onInstall = {
                        if (selectedDistro == DistroChoice.DEBIAN) manager.installDebian() else manager.install()
                    },
                    onCancel = { manager.cancelInstall() },
                    title = if (debianSelected) {
                        "Debian 13 (download)"
                    } else if (outdated) {
                        "Environment update available"
                    } else {
                        "Linux Environment not installed"
                    },
                    desc = if (debianSelected) {
                        "Downloads the Debian 13 rootfs (~50 MB), verifies its pinned sha256 before install, and keeps /workspace."
                    } else if (outdated) {
                        "The on-device rootfs uses an old format. Press Update to reinstall it (downloads ~3 MB, /workspace is kept)."
                    } else {
                        "Uses the Alpine rootfs bundled in the app when available; otherwise downloads the pinned ~3 MB tarball."
                    },
                    actionLabel = if (debianSelected) {
                        "Download Debian 13"
                    } else if (outdated) {
                        "Update Alpine"
                    } else {
                        "Install Alpine"
                    },
                    debianInstalled = remember(installState) { manager.isDebianInstalled() },
                )
                return@Column
            }

            // Live listener banner: any dev server started from chat or the
            // terminal is discovered by port probe; open it in one tap.
            val previewUrl by viewModel.containerPreviewUrl.collectAsState()
            if (!previewUrl.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = previewUrl.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
            }

            SecondaryTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Terminal", fontWeight = FontWeight.SemiBold)
                    }
                })
                Tab(selected = tab == 1, onClick = { tab = 1; filesTick++ }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Files", fontWeight = FontWeight.SemiBold)
                    }
                })
            }

            if (tab == 0) {
                TerminalTab(
                    viewModel = viewModel,
                    manager = manager,
                    backend = terminalBackend,
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

/** The rootfs choices offered before the environment is installed. */
private enum class DistroChoice(val label: String) {
    ALPINE("Alpine (bundled)"),
    DEBIAN("Debian 13 (download)"),
}

@Composable
private fun NotInstalledCard(
    installState: ContainerManager.InstallState,
    selected: DistroChoice = DistroChoice.ALPINE,
    onSelect: (DistroChoice) -> Unit = {},
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Linux Environment not installed",
    desc: String = "Downloads the Alpine minirootfs (~3 MB). proot ships inside the app and can be removed anytime.",
    actionLabel: String = "Install Alpine",
    debianInstalled: Boolean = false,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistroChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = selected == choice,
                        onClick = { onSelect(choice) },
                        label = { Text(choice.label) },
                    )
                }
            }
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
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(actionLabel)
                    }
                }
            }
            if (selected == DistroChoice.DEBIAN && debianInstalled) {
                Text(
                    "Debian 13 is unpacked and sha256-verified. Runtime selection follows the KazeTerm backend migration.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val TermGreen = Color(0xFF4CAF50)

@Composable
private fun TerminalTab(
    viewModel: ChatViewModel,
    manager: ContainerManager,
    backend: TerminalBackend,
    onCommandDone: () -> Unit,
) {
    val context = LocalContext.current
    val sessions = backend.sessions()
    val activeSession = viewModel.activeTerminalSession
    val lines = activeSession.lines
    val history = activeSession.history
    val running = activeSession.isRunning
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

    LaunchedEffect(lines.size, activeSession.id) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
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
        if (!backend.isAvailable()) {
            toast(context, backend.unavailableReason())
            return
        }
        input = TextFieldValue("")
        histPos = -1
        val sid = activeSession.id
        try {
            backend.execute(
                sessionId = sid,
                command = clean,
                onLine = onLine@{ kind, line ->
                    // Resolve by id so a tab switch mid-command cannot misplace
                    // output from a backend that streams its own lines.
                    val target = viewModel.terminalSessions.firstOrNull { it.id == sid } ?: return@onLine
                    target.lines.add(TermLine(kind, line))
                    if (target.lines.size > 1500) target.lines.removeRange(0, 100)
                },
                onDone = onCommandDone,
            )
        } catch (t: Throwable) {
            toast(context, t.message ?: backend.unavailableReason())
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

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Chrome-style Horizontal Tabs for multiple Terminal sessions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sessions.forEach { s ->
                val isSelected = s.id == activeSession.id
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f),
                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.clickable { viewModel.selectTerminalSession(s.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (s.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                        if (sessions.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.closeTerminalSession(s.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close tab",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }

            // '+' New Tab button
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(28.dp)
                    .clickable { viewModel.newTerminalSession() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Tab",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier.fillMaxWidth().weight(1f).clickable { fieldFocus.requestFocus() },
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                if (lines.isEmpty()) {
                    Text(
                        "Alpine shell — type below. TAB completes, ↑↓ history, CTRL/ALT/SHIFT sticky.\nTip: add '&' for background servers (e.g. npm run dev &).",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(lines.size) { i ->
                            val line = lines[i]
                            when (line.kind) {
                                TermKind.CMD -> Text(
                                    "$ ${line.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = TermGreen,
                                )
                                TermKind.ERR -> Text(
                                    line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TermKind.SYS -> Text(
                                    line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                                TermKind.OUT -> Text(
                                    line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                // Inline shell prompt (Termux-style: type directly in the shell).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (running) "… " else "$ ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = if (running) Color(0xFFFFC107) else TermGreen,
                    )
                    BasicTextField(
                        value = input,
                        onValueChange = ::onInputChange,
                        modifier = Modifier.weight(1f).focusRequester(fieldFocus),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = Color.White,
                        ),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { exec(input.text) }),
                    )
                    if (running) {
                        IconButton(
                            onClick = { viewModel.cancelTerminal(activeSession.id) },
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Stop (^C)",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Termux-style extra keys in three compact rows (all fit without clipping).
        // CTRL/ALT/SHIFT are sticky: tap one, then tap the target key.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            termKey("ESC") { input = TextFieldValue(""); clearModifiers(); fieldFocus.requestFocus() }
            termKey("TAB") { tabComplete() }
            termKey("CTRL", active = ctrlActive) {
                val on = !ctrlActive
                clearModifiers()
                ctrlActive = on
                fieldFocus.requestFocus()
            }
            termKey("ALT", active = altActive) {
                val on = !altActive
                clearModifiers()
                altActive = on
                fieldFocus.requestFocus()
            }
            termKey("SHIFT", active = shiftActive) {
                val on = !shiftActive
                clearModifiers()
                shiftActive = on
                fieldFocus.requestFocus()
            }
            termKey("↑") { historyStep(1) }
            termKey("↓") { historyStep(-1) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            termKey("←") { arrowLeft() }
            termKey("→") { arrowRight() }
            termKey("END") { goEnd() }
            termKey("PGUP") { scope.launch { listState.animateScrollToItem(0) } }
            termKey("PGDN") { scope.launch { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) } }
            termKey("/") { insertAtCursor("/") }
            termKey("|") { insertAtCursor("|") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            termKey("~") { insertAtCursor("~") }
            termKey("-") { insertAtCursor("-") }
            termKey("_") { insertAtCursor("_") }
            termKey(".") { insertAtCursor(".") }
            termKey("*") { insertAtCursor("*") }
            termKey("$") { insertAtCursor("$") }
            termKey("\"") { insertAtCursor("\"") }
            termKey("'") { insertAtCursor("'") }
        }
    }
}

@Composable
private fun termKey(label: String, active: Boolean = false, onTap: () -> Unit) {
    TextButton(
        onClick = onTap,
        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
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
                                if (isTextFile(f)) Icons.Default.InsertDriveFile else Icons.Default.Folder,
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
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    saveToDownload(context, f)
                                    withContext(Dispatchers.Main) { refresh() }
                                }
                            }) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Save", modifier = Modifier.size(18.dp))
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
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
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
