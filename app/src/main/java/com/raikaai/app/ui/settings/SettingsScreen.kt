package com.raikaai.app.ui.settings
import com.raikaai.app.ui.component.ScreenHeader
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.material3.SegmentedButton
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
import androidx.compose.ui.unit.dp
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.component.ChipsRow
import com.raikaai.app.ui.component.Material3SettingsGroup
import com.raikaai.app.ui.component.Material3SettingsItem
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
    // Re-read after returning from the system "all files access" screen.
    var phoneMounted by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
            title = { Text("Allow access to phone storage?") },
            text = {
                Text(
                    "Android will open \"All files access\" for RaikaAI. Turn it on, then come back.\n\n" +
                        "Your phone folders appear in Linux at /sdcard, so Raika can read and change files there " +
                        "when you ask. Commands still follow your Permission setting. You can turn this off any time.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showStorageDialog = false
                    viewModel.containerManager.setPhoneStorageEnabled(true)
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                android.net.Uri.parse("package:" + context.packageName),
                            ),
                        )
                    }.onFailure {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showStorageDialog = false }) { Text("Not now") } },
        )
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        phoneMounted = viewModel.containerManager.phoneStorageMounted()
        onPauseOrDispose {}
    }
    val settings by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    var showResetEnvDialog by remember { mutableStateOf(false) }

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
                ScreenHeader(
                    title = "Settings",
                    subtitle = "Appearance, chat behavior and data",
                    onOpenDrawer = onOpenDrawer,
                    insideHorizontalPadding = true,
                )
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

            // ── Terminal ──
            item(key = "terminal_group") {
                Material3SettingsGroup(
                    title = "Terminal",
                    items =
                        listOf(
                            createM3Item(
                                icon = Icons.Outlined.FolderOpen,
                                title = "Phone storage in Linux",
                                description =
                                    if (phoneMounted) "Your phone folders are at /sdcard (Download, Documents, DCIM…)"
                                    else "Lets Raika work in your phone folders, not just /workspace. Needs all-files access.",
                                trailingContent = {
                                    Switch(
                                        checked = phoneMounted,
                                        onCheckedChange = { on ->
                                            if (on && android.os.Build.VERSION.SDK_INT >= 30 &&
                                                !android.os.Environment.isExternalStorageManager()
                                            ) {
                                                showStorageDialog = true
                                            } else {
                                                viewModel.containerManager.setPhoneStorageEnabled(on)
                                                phoneMounted = viewModel.containerManager.phoneStorageMounted()
                                            }
                                        },
                                    )
                                },
                            ),
                            createM3Item(
                                icon = Icons.Outlined.Security,
                                title = "Permission",
                                description = when {
                                    settings.containerFullAccess -> "Full access: nothing is blocked; destructive commands still ask once"
                                    settings.containerYolo -> "Auto: commands run without asking; destructive ones are blocked"
                                    else -> "Ask: Raika asks before each Linux command"
                                },
                                bottomContent = {
                                    val current = when {
                                        settings.containerFullAccess -> 2
                                        settings.containerYolo -> 1
                                        else -> 0
                                    }
                                    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                        listOf("Ask", "Auto", "Full access").forEachIndexed { i, label ->
                                            SegmentedButton(
                                                selected = current == i,
                                                onClick = { viewModel.setContainerPermission(i) },
                                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 3),
                                                icon = {},
                                            ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                                        }
                                    }
                                },
                            ),
                            createM3Item(
                                icon = Icons.Outlined.Terminal,
                                title = "Tool details in chat",
                                description = when (settings.toolVerbosity) {
                                    0 -> "Quiet: one line per command or file"
                                    2 -> "Full: output and file contents shown open"
                                    else -> "Normal: tap a card to see its output"
                                },
                                bottomContent = {
                                    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                        listOf("Quiet", "Normal", "Full").forEachIndexed { i, label ->
                                            SegmentedButton(
                                                selected = settings.toolVerbosity == i,
                                                onClick = { viewModel.setToolVerbosity(i) },
                                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 3),
                                                icon = {},
                                            ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                                        }
                                    }
                                },
                            ),
                            createM3Item(
                                icon = Icons.Outlined.RestartAlt,
                                title = "Reset Linux environment",
                                description = "Reinstalls a fresh Alpine: removes installed packages and settings in the sandbox",
                                trailingContent = {
                                    Icon(
                                        Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = { showResetEnvDialog = true },
                            ),
                        ),
                )
            }

            // ── 3. Data & Storage Management ──
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
                                        Icons.Outlined.ChevronRight,
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
                                        Icons.Outlined.ChevronRight,
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

            // ── 4. About RaikaAI Card ──
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
                                    Icons.Outlined.Info,
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

    if (showResetEnvDialog) {
        var alsoWorkspace by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showResetEnvDialog = false },
            icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset Linux environment?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Running commands and servers stop, and the sandbox is reinstalled from a fresh Alpine. " +
                            "Installed packages (npm -g, apk) and files outside /workspace are deleted.",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = alsoWorkspace, onCheckedChange = { alsoWorkspace = it })
                        Text("Also delete my files in /workspace")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetLinuxEnvironment(deleteWorkspace = alsoWorkspace)
                        showResetEnvDialog = false
                        Toast.makeText(context, "Reinstalling the Linux environment…", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetEnvDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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
