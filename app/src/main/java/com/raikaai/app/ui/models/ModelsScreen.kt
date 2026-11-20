package com.raikaai.app.ui.models

import com.raikaai.app.ui.component.ScreenHeader
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import com.raikaai.app.data.ChatConstants
import com.raikaai.app.data.effectiveRemoteContextWindow
import com.raikaai.app.data.parseRemoteModelContextWindows
import com.raikaai.app.data.parseRemoteModelPricing
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.util.Locale
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.localai.InstalledModel
import com.raikaai.app.localai.LocalAiCatalog
import com.raikaai.app.localai.LocalAiModel
import com.raikaai.app.localai.LocalModelDownloader.DownloadState
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.component.EmptyState
import com.raikaai.app.ui.component.SettingsTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dedicated Models Management Screen cleanly styled with Material 3 uniform cards,
 * matching SamplerScreen aesthetic.
 */
@Composable
fun ModelsScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val installedModels by viewModel.installedModels
    val downloadStates by viewModel.downloadStates.collectAsState()
    val isModelLoaded by viewModel.isModelLoaded.collectAsState()
    val isLoadingModel by viewModel.isLoadingModel.collectAsState()
    val activeBackend by viewModel.activeBackendState.collectAsState()

    val remoteModels by viewModel.remoteModels
    val isFetchingRemoteModels by viewModel.isFetchingRemoteModels
    val remoteSpendUsd by viewModel.remoteSpendUsd
    val pricingMap = remember(settings.remoteModelPricingJson) {
        parseRemoteModelPricing(settings.remoteModelPricingJson)
    }

    val freeDiskBytes by viewModel.freeDiskSpaceBytes
    val freeDiskGb = freeDiskBytes / (1024.0 * 1024.0 * 1024.0)

    var customUrlInput by remember { mutableStateOf("") }
    // Mirrors of the remote text fields below, used by the "Fetch Remote Models" button.
    // The fields themselves use SettingsTextField (focus-guarded one-way sync) so typing
    // is never wiped by unrelated settings emissions; onValueChange keeps these in sync.
    var remoteUrlInput by remember { mutableStateOf(settings.remoteBaseUrl) }
    var remoteKeyInput by remember { mutableStateOf(settings.remoteApiKey) }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let { viewModel.importCustomModel(it) }
        }

    val isLocalMode = settings.modelSource == ChatConstants.SOURCE_LOCAL

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 10.dp),
        ) {
            // ── 0. Top Header ──
            ScreenHeader(
                title = "Models",
                subtitle = "Run on this phone, or through a provider",
                onOpenDrawer = onOpenDrawer,
            )

            Spacer(Modifier.height(8.dp))

            // ── 1. Secondary Tabs Row ──
            SecondaryTabRow(
                selectedTabIndex = if (isLocalMode) 0 else 1,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = isLocalMode,
                    onClick = { viewModel.setModelSource(ChatConstants.SOURCE_LOCAL) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Local", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                Tab(
                    selected = !isLocalMode,
                    onClick = { viewModel.setModelSource(ChatConstants.SOURCE_REMOTE) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Remote", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }

            // ── 2. Content View ──
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {

            if (isLocalMode) {
                // ── 2. Storage & Hardware Target Card ──
                item(key = "storage_hardware_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Device Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Free storage space for local models", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = String.format(Locale.ROOT, "%.1f GB Free", freeDiskGb),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            Text("Hardware Acceleration Target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(ChatConstants.BACKEND_GPU to "GPU (OpenCL Snapdragon)", ChatConstants.BACKEND_CPU to "CPU (Arm NEON)").forEach { (be, label) ->
                                    val isSelected = settings.backend.equals(be, ignoreCase = true)
                                    Surface(
                                        onClick = { viewModel.setBackend(be) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 3. Active Downloads (if any) ──
                val activeDownloads = downloadStates.filter {
                    it.value is DownloadState.Downloading || it.value is DownloadState.Error
                }
                if (activeDownloads.isNotEmpty()) {
                    item(key = "active_downloads_card") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Active Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                activeDownloads.forEach { (modelId, state) ->
                                    when (state) {
                                        is DownloadState.Downloading -> {
                                            val percent = (state.progress * 100).toInt()
                                            val speedMb = state.speedBps.toFloat() / (1024 * 1024)
                                            val downloadedMb = state.downloadedBytes.toFloat() / (1024 * 1024)
                                            val totalMb = state.totalBytes.toFloat() / (1024 * 1024)
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(state.displayName.ifBlank { modelId }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { state.progress },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        String.format(Locale.ROOT, "%.1f / %.1f MB (%.1f MB/s)", downloadedMb, totalMb, speedMb),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    TextButton(onClick = { viewModel.cancelDownload(modelId) }) {
                                                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                        is DownloadState.Error -> {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        state.fileName.ifBlank { modelId },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                    Text(
                                                        state.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End,
                                                    ) {
                                                        TextButton(onClick = { viewModel.cancelDownload(modelId) }) {
                                                            Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. Installed Models Card ──
                item(key = "installed_models_card") {
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
                                "Installed Models (${installedModels.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Offline models downloaded and stored in app sandbox.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (installedModels.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Outlined.Memory,
                                    title = "No models installed",
                                    message = "Download a preset below or import a .litertlm file.",
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    installedModels.forEach { item ->
                                        val isCurrent = (settings.activeModelId.isNotBlank() && item.id == settings.activeModelId) ||
                                            (settings.customModelPath.isNotBlank() && item.absolutePath == settings.customModelPath)
                                        val isLoaded = isCurrent && isModelLoaded

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = item.displayName,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        if (isCurrent) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Icon(
                                                                Icons.Outlined.Check,
                                                                contentDescription = "Active",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp),
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${item.sizeDisplay} · ${item.fileName}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 11.sp,
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    IconButton(
                                                        onClick = { viewModel.deleteModel(item.fileName) },
                                                        modifier = Modifier.size(36.dp),
                                                    ) {
                                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                    }

                                                    if (isCurrent) {
                                                        if (isLoaded) {
                                                            Button(
                                                                onClick = { viewModel.unloadActiveModel() },
                                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                                                shape = RoundedCornerShape(10.dp),
                                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                            ) {
                                                                Text("Unload", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else {
                                                            Button(
                                                                onClick = { viewModel.loadActiveModel() },
                                                                enabled = !isLoadingModel,
                                                                shape = RoundedCornerShape(10.dp),
                                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                            ) {
                                                                if (isLoadingModel) {
                                                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                                                } else {
                                                                    Text("Load into Memory", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Button(
                                                            onClick = {
                                                                if (item.isPreset) {
                                                                    viewModel.setActivePreset(item.id)
                                                                } else {
                                                                    viewModel.setActiveCustomPath(item.absolutePath)
                                                                }
                                                            },
                                                            shape = RoundedCornerShape(50),
                                                        ) {
                                                            Text("Use Model", style = MaterialTheme.typography.labelMedium)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 5. Verified LiteRT Catalog Card ──
                item(key = "catalog_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Verified LiteRT Catalog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Official mobile-quantized models tested for high token generation speeds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LocalAiCatalog.PRESET_MODELS.forEach { model ->
                                    val isInstalled = installedModels.any { it.fileName == model.fileName } || viewModel.isModelDownloaded(model)
                                    val isActive = settings.activeModelId == model.id && settings.customModelPath.isBlank()
                                    val downloadState = downloadStates[model.id]

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(model.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text("${model.sizeDisplay} · Format: .litertlm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                                }
                                                if (isActive) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    ) {
                                                        Text(
                                                            "ACTIVE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(10.dp))

                                            if (isInstalled) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Installed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(onClick = { viewModel.deleteModel(model) }) {
                                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                                        }
                                                        if (!isActive) {
                                                            Button(
                                                                onClick = { viewModel.setActivePreset(model.id) },
                                                                shape = RoundedCornerShape(8.dp),
                                                            ) {
                                                                Text("Use Model", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else if (downloadState is DownloadState.Downloading) {
                                                val percent = (downloadState.progress * 100).toInt()
                                                Column {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("Downloading...", style = MaterialTheme.typography.labelSmall)
                                                        Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = { downloadState.progress },
                                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                    )
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.startDownload(model) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Download (${model.sizeDisplay})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 6. Custom Model Import & HuggingFace Card ──
                item(key = "import_hf_card") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomUrlDownloadCard(
                            urlInput = customUrlInput,
                            onUrlInputChange = { customUrlInput = it },
                            hfToken = settings.huggingFaceToken,
                            onStartDownload = { url, fileName, sizeBytes, sha256 ->
                                viewModel.downloadCustomUrl(url, fileName, sizeBytes, sha256.ifBlank { null })
                                Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
                            },
                        )

                        LocalSafImportCard(
                            onImportModel = { uri -> viewModel.importCustomModel(uri) },
                        )
                    }
                }
            } else {
                // ── Estimated Remote Spend Card ──
                item(key = "remote_spend_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Estimated Remote Spend",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Accumulated API usage cost",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "$%.4f".format(remoteSpendUsd),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        if (remoteSpendUsd > 0) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { viewModel.resetRemoteSpend() }) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }

                // ── Remote Provider Setup Card ──
                item(key = "remote_provider_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val provider = viewModel.currentRemoteProvider(settings)
                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                            Text("Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            // Preset grid: two per row, the selected one in primaryContainer.
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.raikaai.app.core.remote.RemoteProviders.all.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { p ->
                                            ProviderTile(
                                                provider = p,
                                                selected = p.id == provider.id,
                                                onClick = { viewModel.selectRemoteProvider(p.id) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            // Fields are keyed by provider so switching shows that provider's values.
                            androidx.compose.runtime.key(provider.id, settings.remoteBaseUrl.isBlank()) {
                                LaunchedEffect(provider.id, settings.remoteBaseUrl, settings.remoteApiKey) {
                                    remoteUrlInput = settings.remoteBaseUrl
                                    remoteKeyInput = settings.remoteApiKey
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(provider.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (provider.editableUrl) {
                                        SettingsTextField(
                                            initialValue = settings.remoteBaseUrl,
                                            onSave = { viewModel.setRemoteBaseUrl(it) },
                                            onValueChange = { remoteUrlInput = it },
                                            label = { Text("Server URL") },
                                            placeholder = { Text(provider.baseUrl.ifBlank { "http://192.168.1.100:1234/v1" }) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        Text(
                                            settings.remoteBaseUrl,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    SettingsTextField(
                                        initialValue = settings.remoteApiKey,
                                        onSave = { viewModel.setRemoteApiKey(it) },
                                        onValueChange = { remoteKeyInput = it },
                                        label = { Text(if (provider.needsKey) "API key" else "API key (optional)") },
                                        placeholder = { Text(provider.keyPrefix.ifBlank { "key" } + "…") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { viewModel.fetchRemoteModels(remoteUrlInput, remoteKeyInput) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Load models")
                                        }
                                        if (provider.keyUrl.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = { runCatching { uriHandler.openUri(provider.keyUrl) } },
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Get a key")
                                            }
                                        }
                                    }
                                }
                            }

                            if (remoteModels.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                Text("Available Remote Models (${remoteModels.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    remoteModels.forEach { item ->
                                        val isSelected = settings.remoteModelId == item.id
                                        Surface(
                                            onClick = { viewModel.setRemoteModelId(item.id) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.name.ifBlank { item.id },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        text = item.id,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 11.sp,
                                                    )
                                                    pricingMap[item.id]?.let { pricing ->
                                                        Text(
                                                            text = "%.2f / %.2f per 1M tokens".format(
                                                                pricing.promptPerToken * 1_000_000,
                                                                pricing.completionPerToken * 1_000_000,
                                                            ),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.tertiary,
                                                            fontSize = 11.sp,
                                                        )
                                                    }
                                                }
                                                if (isSelected) {
                                                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                                                        Text(
                                                            "Active",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            // ── Image Generation Model ──
                            Text(
                                "Image Generation Model",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Model ID for /v1/images/generations (e.g. an OpenRouter image model). Leave empty to use the active chat model.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = settings.remoteImageModelId,
                                onValueChange = { viewModel.setRemoteImageModelId(it) },
                                label = { Text("Image model ID (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
}

/** A provider preset tile: tonal initial badge, name, one-line description. */
@Composable
private fun ProviderTile(
    provider: com.raikaai.app.core.remote.RemoteProvider,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) scheme.primary else scheme.secondaryContainer,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (provider.id) {
                        com.raikaai.app.core.remote.RemoteProviders.OLLAMA ->
                            Icon(Icons.Outlined.Lan, null, Modifier.size(18.dp), tint = if (selected) scheme.onPrimary else scheme.onSecondaryContainer)
                        com.raikaai.app.core.remote.RemoteProviders.CUSTOM ->
                            Icon(Icons.Outlined.Hub, null, Modifier.size(18.dp), tint = if (selected) scheme.onPrimary else scheme.onSecondaryContainer)
                        else ->
                            Text(
                                provider.name.first().toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (selected) scheme.onPrimary else scheme.onSecondaryContainer,
                            )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    provider.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    provider.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
