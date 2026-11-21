package com.raikaai.app.ui.mcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.MoreVert
import com.raikaai.app.ui.component.ScreenHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.mcp.McpServerConfig
import com.raikaai.app.mcp.McpTool
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.component.EmptyState

/**
 * Manages MCP (Model Context Protocol) servers: add/edit/remove, discover
 * tools, and manually test-call a tool. Autonomous tool use during chat is
 * gated behind [ChatViewModel.setMcpEnabled] plus a per-call approval dialog.
 */
@Composable
fun McpScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
    addRequests: Int = 0,
) {
    val settings by viewModel.settings.collectAsState()
    val servers by viewModel.mcpServers.collectAsState()
    val toolsByServer by viewModel.mcpToolsByServer.collectAsState()
    val errors by viewModel.mcpToolErrors.collectAsState()
    val busy by viewModel.mcpBusy.collectAsState()
    // Tools are discovered per app session: connect enabled servers on open.
    LaunchedEffect(servers) {
        servers.filter { it.enabled && toolsByServer[it.id].isNullOrEmpty() && it.id !in busy && errors[it.id] == null }
            .forEach { viewModel.refreshMcpServerTools(it) }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    LaunchedEffect(addRequests) { if (addRequests > 0) showAddDialog = true }
    var editingServer by remember { mutableStateOf<McpServerConfig?>(null) }
    var expandedServer by remember { mutableStateOf<String?>(null) }
    var testTool by remember { mutableStateOf<Pair<McpServerConfig, McpTool>?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (embedded) Modifier else Modifier.statusBarsPadding())
                    .padding(top = if (embedded) 0.dp else 10.dp, start = 16.dp, end = 16.dp),
        ) {
            // ── 0. Top Header (same pattern as every other screen) ──
            if (!embedded) ScreenHeader(
                title = "MCP",
                subtitle = if (servers.isEmpty()) "Model Context Protocol & External Tools" else "${servers.size} Connected Servers",
                onOpenDrawer = onOpenDrawer,
                insideHorizontalPadding = true,
            ) {
                IconButton(
                    onClick = { viewModel.refreshMcpTools() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Discover tools",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add server",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            if (servers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Build,
                        title = "No MCP servers yet",
                        message = "Ask Raika in chat, e.g. \"install the context7 MCP\". It sets the server up in Linux and asks you first. Or tap + to add one yourself.",
                    )
                }
            }

            items(servers, key = { it.id }) { server ->
                McpServerCard(
                    server = server,
                    tools = toolsByServer[server.id].orEmpty(),
                    error = errors[server.id],
                    connecting = server.id in busy,
                    expanded = expandedServer == server.id,
                    onToggleExpanded = { expandedServer = if (expandedServer == server.id) null else server.id },
                    onEnabled = { viewModel.setMcpServerEnabled(server, it) },
                    onRefresh = { viewModel.refreshMcpServerTools(server) },
                    onEdit = { editingServer = server },
                    onRemove = { viewModel.removeMcpServer(server.id) },
                    onTestTool = { tool -> testTool = server to tool },
                )
            }

            item {
                if (servers.isNotEmpty()) Text(
                    "Raika asks before MCP calls in Ask and Auto modes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        }
    }

    if (showAddDialog || editingServer != null) {
        McpServerDialog(
            initial = editingServer,
            onDismiss = {
                showAddDialog = false
                editingServer = null
            },
            onSave = { server, authToken ->
                viewModel.upsertMcpServer(server, authToken)
                showAddDialog = false
                editingServer = null
            },
        )
    }

    testTool?.let { (server, tool) ->
        McpTestCallDialog(
            server = server,
            tool = tool,
            onDismiss = { testTool = null },
            onRun = { argsJson, onResult ->
                viewModel.testMcpCall(server.id, tool.name, argsJson, onResult)
            },
        )
    }
}

@Composable
private fun McpServerDialog(
    initial: McpServerConfig?,
    onDismiss: () -> Unit,
    /**
     * [authToken]: null = leave stored token untouched (edit mode, field left
     * blank); "" = clear the stored token; otherwise store the new token.
     */
    onSave: (McpServerConfig, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var command by remember { mutableStateOf(initial?.command.orEmpty()) }
    var stdio by remember { mutableStateOf(initial?.isStdio ?: true) }
    var authToken by remember { mutableStateOf("") }
    var clearToken by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank() && (if (stdio) command.isNotBlank() else url.isNotBlank())
    val hadToken = initial?.hasToken == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add MCP Server" else "Edit MCP Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("My tools") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = stdio,
                        onClick = { stdio = true },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Linux command") }
                    SegmentedButton(
                        selected = !stdio,
                        onClick = { stdio = false },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("URL") }
                }
                if (stdio) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command") },
                        placeholder = { Text("npx -y @upstash/context7-mcp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Endpoint URL") },
                        placeholder = { Text("https://mcp.example.com/mcp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!stdio) OutlinedTextField(
                    value = authToken,
                    onValueChange = {
                        authToken = it
                        if (it.isNotBlank()) clearToken = false
                    },
                    label = { Text("Bearer token (optional)") },
                    placeholder = { Text(if (hadToken) "•••••••• (saved)" else "Leave empty for none") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hadToken && !clearToken && authToken.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "A token is saved — leave empty to keep it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { clearToken = true }) { Text("Clear") }
                    }
                }
                if (clearToken) {
                    Text(
                        "The saved token will be removed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    if (stdio) "Runs inside the Alpine environment over stdio. Install the runtime first if needed (apk add nodejs npm)." else "Streamable HTTP. Tokens are stored encrypted on the device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val base = initial ?: McpServerConfig(name = "", url = "")
                    val tokenParam: String? =
                        when {
                            clearToken -> ""
                            authToken.isNotBlank() -> authToken.trim()
                            initial == null -> ""
                            else -> null // keep existing
                        }
                    onSave(
                        base.copy(
                            name = name.trim(),
                            url = if (stdio) "" else url.trim(),
                            command = if (stdio) command.trim() else "",
                        ),
                        if (stdio) "" else tokenParam,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun McpTestCallDialog(
    server: McpServerConfig,
    tool: McpTool,
    onDismiss: () -> Unit,
    onRun: (String, (String) -> Unit) -> Unit,
) {
    var argsJson by remember { mutableStateOf("{}") }
    var result by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test: ${tool.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    tool.signature,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
                Text(
                    "Server: ${server.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = argsJson,
                    onValueChange = { argsJson = it },
                    label = { Text("Arguments (JSON)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp),
                )
                result?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            it.take(3000),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(8.dp)
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    result = "Running…"
                    onRun(argsJson) {
                        result = it
                        running = false
                    }
                },
            ) { Text("Run") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}


@Composable
private fun McpServerCard(
    server: McpServerConfig,
    tools: List<McpTool>,
    error: String?,
    connecting: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTestTool: (McpTool) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val (dot, status) =
        when {
            !server.enabled -> scheme.outline to "Off"
            connecting -> scheme.tertiary to (if (server.isStdio && tools.isEmpty()) "Starting in Linux…" else "Connecting…")
            error != null -> scheme.error to "Not connected"
            tools.isNotEmpty() -> androidx.compose.ui.graphics.Color(0xFF4CAF50) to "Connected · ${tools.size} tools"
            else -> scheme.outline to "Not connected yet"
        }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (server.isStdio) Icons.Outlined.Terminal else Icons.Outlined.Language,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connecting) {
                            androidx.compose.material3.CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(10.dp))
                        } else {
                            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            status + if (server.addedByAgent) " · added by Raika" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = server.enabled, onCheckedChange = onEnabled)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Reconnect") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            enabled = server.enabled && !connecting,
                            onClick = { menu = false; onRefresh() },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Remove", color = scheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = scheme.error) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
            Text(
                server.location.removePrefix("Linux · "),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
            )
            if (error != null && !connecting && server.enabled) {
                Text(
                    error.take(240),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 6.dp, end = 8.dp),
                )
                TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Try again") }
            }
            if (tools.isNotEmpty()) {
                TextButton(onClick = onToggleExpanded, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (expanded) "Hide tools" else "Show ${tools.size} tools")
                }
                if (expanded) {
                    tools.forEach { tool ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onTestTool(tool) }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tool.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        tool.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = "Test", tint = scheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
