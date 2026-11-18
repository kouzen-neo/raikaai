package com.raikaai.app.ui.settings
import com.raikaai.app.BuildConfig
import com.raikaai.app.data.ChatConstants

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.raikaai.app.data.SettingsRepository
import com.raikaai.app.runtime.RuntimeMode
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.component.Material3SettingsGroup
import com.raikaai.app.ui.component.Material3SettingsItem
import com.raikaai.app.ui.component.SettingsTextField
import com.raikaai.app.ui.component.createM3Item
import kotlinx.coroutines.launch

/**
 * Dedicated App Settings Screen adhering to kzkt Material 3 Expressive design.
 */
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()

    // Runtime settings are owned by SettingsRepository; ChatViewModel has no
    // setters for them yet. A screen-scoped repository keeps token writes in the
    // same flow that feeds this section (secure token bumps are per instance).
    val runtimeRepo = remember(context.applicationContext) {
        SettingsRepository(context.applicationContext)
    }
    val runtimeSettings by runtimeRepo.settingsFlow.collectAsState(initial = settings)
    var showPiToken by remember { mutableStateOf(false) }

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Top Header ──
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Theme, Preferences & Storage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 1. Appearance & Theme Group ──
            item(key = "appearance_group") {
                Material3SettingsGroup(
                    title = "Appearance",
                    items =
                        listOf(
                            Material3SettingsItem(
                                leadingContent = { Icon(Icons.Outlined.Brightness4, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                                title = { Text("Theme Mode") },
                                description = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 2.dp),
                                    ) {
                                        Text(
                                            when (settings.themeMode) {
                                                "dark" -> "Dark theme enabled"
                                                else -> "Light theme enabled"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                ChatConstants.THEME_LIGHT to "Light",
                                                ChatConstants.THEME_DARK to "Dark",
                                            ).forEach { (mode, label) ->
                                                FilterChip(
                                                    selected = settings.themeMode == mode,
                                                    onClick = { viewModel.setThemeMode(mode) },
                                                    label = { Text(label) },
                                                    shape = RoundedCornerShape(50),
                                                )
                                            }
                                        }
                                    }
                                },
                            ),
                        ),
                )
            }

            // ── 2. Chat UI & Interaction Preferences ──
            item(key = "chat_preferences_group") {
                Material3SettingsGroup(
                    title = "Chat Behavior",
                    items =
                        listOf(
                            createM3Item(
                                icon = Icons.Outlined.Speed,
                                title = "Show Tokens Per Second",
                                description = "Displays real-time generation speed below AI messages",
                                trailingContent = {
                                    Switch(
                                        checked = settings.showTokensPerSec,
                                        onCheckedChange = { viewModel.setShowTokensPerSec(it) },
                                    )
                                },
                            ),
                            createM3Item(
                                icon = Icons.Outlined.SwapVert,
                                title = "Auto-Scroll During Generation",
                                description = "Automatically scrolls message feed as tokens arrive",
                                trailingContent = {
                                    Switch(
                                        checked = settings.autoScroll,
                                        onCheckedChange = { viewModel.setAutoScroll(it) },
                                    )
                                },
                            ),
                            createM3Item(
                                icon = Icons.Outlined.Keyboard,
                                title = "Send on Enter Key",
                                description = "Submits composer prompt immediately when pressing Enter",
                                trailingContent = {
                                    Switch(
                                        checked = settings.sendOnEnter,
                                        onCheckedChange = { viewModel.setSendOnEnter(it) },
                                    )
                                },
                            ),
                            createM3Item(
                                icon = Icons.Outlined.StayCurrentPortrait,
                                title = "Keep Screen Awake",
                                description = "Prevents device display from sleeping during generation",
                                trailingContent = {
                                    Switch(
                                        checked = settings.keepAwake,
                                        onCheckedChange = { viewModel.setKeepAwake(it) },
                                    )
                                },
                            ),
                        ),
                )
            }

            // ── 3. Runtime ──
            item(key = "runtime_mode_group") {
                Material3SettingsGroup(
                    title = "Runtime",
                    items =
                        listOf(
                            Material3SettingsItem(
                                leadingContent = { Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                                title = { Text("Runtime Mode") },
                                description = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 2.dp),
                                    ) {
                                        Text(
                                            "Current: ${runtimeSettings.runtimeMode}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                RuntimeMode.ON_DEVICE.name to "On-device",
                                                RuntimeMode.KAZETERM_PI.name to "KazeTerm Pi",
                                                RuntimeMode.LEGACY_PROOT.name to "Legacy PRoot",
                                            ).forEach { (mode, label) ->
                                                FilterChip(
                                                    selected = runtimeSettings.runtimeMode == mode,
                                                    onClick = { scope.launch { runtimeRepo.setRuntimeMode(mode) } },
                                                    label = { Text(label) },
                                                    shape = RoundedCornerShape(50),
                                                )
                                            }
                                        }
                                        Text(
                                            "ON_DEVICE runs without Linux. KAZETERM_PI needs the KazeTerm guest and Pi adapter (not bundled yet). LEGACY_PROOT is transitional.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            ),
                        ),
                )
            }

            item(key = "pi_adapter_group") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Pi Adapter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Connection details used when the runtime mode is KAZETERM_PI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SettingsTextField(
                                initialValue = runtimeSettings.piAdapterHost,
                                onSave = { value -> scope.launch { runtimeRepo.setPiAdapterHost(value) } },
                                label = { Text("Adapter Host") },
                                placeholder = { Text("127.0.0.1") },
                                modifier = Modifier.weight(0.6f),
                            )
                            SettingsTextField(
                                initialValue = runtimeSettings.piAdapterPort.toString(),
                                onSave = { value ->
                                    value.toIntOrNull()?.let { port -> scope.launch { runtimeRepo.setPiAdapterPort(port) } }
                                },
                                label = { Text("Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.4f),
                            )
                        }

                        SettingsTextField(
                            initialValue = runtimeSettings.piAdapterToken,
                            onSave = { value -> scope.launch { runtimeRepo.setPiAdapterToken(value) } },
                            label = { Text("Adapter Token (optional)") },
                            placeholder = { Text("Leave empty for none") },
                            visualTransformation = if (showPiToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPiToken = !showPiToken }) {
                                    Icon(
                                        if (showPiToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPiToken) "Hide token" else "Show token",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ── 4. Data & Storage Management ──
            item(key = "data_management_group") {
                Material3SettingsGroup(
                    title = "Data & Storage",
                    items =
                        listOf(
                            createM3Item(
                                icon = Icons.Outlined.Share,
                                title = "Export All Chats (JSON)",
                                description = "Share and backup all conversations as JSON file",
                                trailingContent = {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    scope.launch {
                                        val json = viewModel.exportAllConversationsJson()
                                        val sendIntent =
                                            Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, json)
                                                type = "application/json"
                                            }
                                        context.startActivity(Intent.createChooser(sendIntent, "Export All Chats"))
                                    }
                                },
                            ),
                            createM3Item(
                                icon = Icons.Outlined.DeleteOutline,
                                title = "Clear All Chat History",
                                description = "Permanently remove all chat sessions and saved messages",
                                trailingContent = {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = { showClearHistoryDialog = true },
                            ),
                        ),
                )
            }

            // ── 5. About RaikaAI Card ──
            item(key = "about_card") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "About RaikaAI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Text(
                            "RaikaAI is a 100% on-device AI inference engine built with Google AI Edge LiteRT-LM, OpenCL GPU acceleration, and an embedded OpenAI-compatible local API server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Runtime Engine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("LiteRT-LM (OpenCL / NEON)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Target Architecture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("ARM64-v8a", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently delete all saved conversations on your device. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "All chat history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
