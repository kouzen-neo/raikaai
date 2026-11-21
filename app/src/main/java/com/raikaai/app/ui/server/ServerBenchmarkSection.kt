package com.raikaai.app.ui.server

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.raikaai.app.ui.theme.success
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.core.server.OpenAiServer
import com.raikaai.app.data.ChatConstants
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.util.NetworkUtils
import java.util.Locale
/**
 * Server & Benchmark section, embedded at the bottom of the Models screen
 * (on-device tab). Previously a standalone drawer destination; merged here to
 * keep the navigation drawer short.
 */
@Composable
internal fun ServerBenchmarkSection(viewModel: ChatViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("OpenAI Server", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Speed Benchmark", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        if (selectedTab == 0) {
            ServerControlCard(viewModel)
            ApiSnippetsCard(viewModel)
            ServerLogsCard(viewModel)
        } else {
            BenchmarkCard(viewModel)
        }
    }
}

/* Embedded OpenAI-Compatible Server Card */
@Composable
private fun ServerControlCard(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by viewModel.settings.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val requestCount by OpenAiServer.requestCount.collectAsState()
    val isRunning = serverStatus is OpenAiServer.Status.Running
    val lanIp = remember { NetworkUtils.getLocalIpAddress(context) ?: "127.0.0.1" }
    val serverEndpoint = "http://$lanIp:${settings.serverPort}/v1"
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Embedded OpenAI-Compatible Server",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Host a local HTTP /v1 endpoint on your Wi-Fi network for VS Code Continue, OpenWebUI, and third-party apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Server Switch & Status
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (isRunning) "Server Running ($requestCount requests)" else "Server Stopped",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (isRunning) "Ready to receive API calls" else "Ready to host on local Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { viewModel.toggleServer(context) },
                    )
                }
            }

            if (isRunning) {
                // Clickable LAN Endpoint Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(AnnotatedString(serverEndpoint))
                                Toast.makeText(context, "Endpoint copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("API Base URL (Tap to copy)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                serverEndpoint,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Port & Key Fields
            var portDraft by remember(settings.serverPort) { mutableStateOf("${settings.serverPort}") }
            val portNumber = portDraft.toIntOrNull()
            val portInvalid = portDraft.isNotEmpty() && (portNumber == null || portNumber !in 1024..65535)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = portDraft,
                    onValueChange = { str ->
                        val digits = str.filter { it.isDigit() }.take(5)
                        portDraft = digits
                        val parsed = digits.toIntOrNull()
                        if (parsed != null && parsed in 1024..65535) {
                            viewModel.setServerPort(parsed)
                        }
                    },
                    label = { Text("Port") },
                    isError = portInvalid,
                    supportingText = if (portInvalid) ({ Text("1024–65535") }) else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.4f),
                )

                OutlinedTextField(
                    value = settings.serverAuthToken,
                    onValueChange = { viewModel.setServerAuthToken(it) },
                    label = { Text("API Key (Optional)") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.6f),
                )
            }

            // Bind LAN Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bind to all network interfaces (LAN)", style = MaterialTheme.typography.titleMedium)
                    Text("Allows other devices on your Wi-Fi network to call this endpoint.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.serverBindAll,
                    onCheckedChange = { viewModel.setServerBindAll(it) },
                )
            }

            // Security warning: LAN mode without an API key exposes the
            // server to everyone on the Wi-Fi network. (Fail-closed
            // enforcement at the server layer is handled separately.)
            if (settings.serverBindAll && settings.serverAuthToken.isBlank()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Security warning: LAN mode is ON but no API key is set — " +
                            "anyone on your Wi-Fi network can use this device as an AI server. " +
                            "Set an API key above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
@Composable
private fun ApiSnippetsCard(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by viewModel.settings.collectAsState()
    val lanIp = remember { NetworkUtils.getLocalIpAddress(context) ?: "127.0.0.1" }
    val serverEndpoint = "http://$lanIp:${settings.serverPort}/v1"
    var selectedSnippetTab by remember { mutableStateOf("curl") }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "API Integration Snippets",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Sample code to connect external developer tools and scripts to this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Snippet Tab Switcher
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf("curl" to "cURL (Terminal)", "python" to "Python (OpenAI)", "vscode" to "VS Code Continue").forEach { (tab, label) ->
                                        val isSelected = selectedSnippetTab == tab
                                        Surface(
                                            onClick = { selectedSnippetTab = tab },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }

                                val snippetText =
                                    when (selectedSnippetTab) {
                                        "python" ->
                                            """
    from openai import OpenAI

    client = OpenAI(
        base_url="$serverEndpoint",
        api_key="${settings.serverAuthToken.ifBlank { "raikaai-local" }}"
    )

    response = client.chat.completions.create(
        model="raikaai",
        messages=[{"role": "user", "content": "Hello!"}],
        stream=True
    )

    for chunk in response:
        print(chunk.choices[0].delta.content or "", end="")
                                            """.trimIndent()

                                        "vscode" ->
                                            """
    // Add to your ~/.continue/config.json:
    {
      "models": [
        {
          "title": "RaikaAI Android",
          "provider": "openai",
          "model": "raikaai",
          "apiBase": "$serverEndpoint"
        }
      ]
    }
                                            """.trimIndent()

                                        else ->
                                            """
    curl -N $serverEndpoint/chat/completions \
      -H "Content-Type: application/json" \
      -d '{
        "model": "raikaai",
        "messages": [{"role": "user", "content": "Hello!"}],
        "stream": true
      }'
                                            """.trimIndent()
                                    }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                selectedSnippetTab.uppercase(Locale.ROOT),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            IconButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(snippetText))
                                                    Toast.makeText(context, "Snippet copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy code", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = snippetText,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
}
@Composable
private fun ServerLogsCard(viewModel: ChatViewModel) {
    val recentRequests by OpenAiServer.recentLogs.collectAsState()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Live HTTP Server Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (recentRequests.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No HTTP requests received yet. Start the server and send requests from VS Code or curl.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        recentRequests.takeLast(10).forEach { log ->
                            val statusColor = if (log.status in 200..299) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${log.method} ${log.path}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${log.status} · ${log.durationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun BenchmarkCard(viewModel: ChatViewModel) {
    val settings by viewModel.settings.collectAsState()
    val isBenchmarking by viewModel.isBenchmarking
    val benchmarkResult by viewModel.latestBenchmark
    val isModelLoaded by viewModel.isModelLoaded.collectAsState()
    var benchmarkPrompt by remember { mutableStateOf("Explain quantum computing in three simple bullet points.") }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "On-Device LLM Speed Benchmark",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Test inference throughput (TTFT, decode tok/s, GPU vs CPU) directly on your hardware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Target Backend Selector
            Text("Benchmark Target Backend", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(ChatConstants.BACKEND_GPU to "GPU (OpenCL Snapdragon)", ChatConstants.BACKEND_CPU to "CPU (Arm NEON)").forEach { (be, label) ->
                    val isSelected = settings.backend.equals(be, ignoreCase = true)
                    Surface(
                        onClick = { viewModel.setBackend(be) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            // Benchmark Results Display
            benchmarkResult?.let { res ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Decode Speed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.ROOT, "%.1f tok/s", res.decodeSpeedTokPerSec), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Time to First Token (TTFT)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${res.ttftMs} ms", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tokens Generated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${res.tokensGenerated} tokens (${res.totalDurationSec}s)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Hardware Target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(res.backend, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Run Button
            Button(
                onClick = { viewModel.runBenchmark(benchmarkPrompt) },
                enabled = isModelLoaded && !isBenchmarking,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isBenchmarking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Running Benchmark...")
                } else {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isModelLoaded) "Run Speed Test (${settings.backend})" else "Load Model First to Benchmark")
                }
            }
        }
    }
}
