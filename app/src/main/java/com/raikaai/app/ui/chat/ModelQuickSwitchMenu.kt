package com.raikaai.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.core.remote.RemoteModelItem
import com.raikaai.app.data.ChatConstants

/**
 * Clean, model-focused quick switcher popup.
 * Lists all installed on-device models and remote models directly.
 */
@Composable
internal fun ModelQuickSwitchMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: ChatViewModel,
    onNavigateToModels: () -> Unit = {},
    onCompareClick: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val installedModels by viewModel.installedModels
    val remoteModels by viewModel.remoteModels
    val isModelLoaded by viewModel.isModelLoaded.collectAsState()
    val activeBackend by viewModel.activeBackendState.collectAsState()

    val isLocal = settings.modelSource == ChatConstants.SOURCE_LOCAL
    val isFetching by viewModel.isFetchingRemoteModels

    // Each open lists the selected provider's current models.
    androidx.compose.runtime.LaunchedEffect(expanded, settings.remoteBaseUrl, settings.remoteApiKey) {
        if (expanded) viewModel.refreshActiveRemoteModels()
    }

    val allRemote = remember(remoteModels, settings.remoteModelId) {
        val list = remoteModels.toMutableList()
        if (settings.remoteModelId.isNotBlank() && list.none { it.id == settings.remoteModelId }) {
            list.add(0, RemoteModelItem(id = settings.remoteModelId, name = settings.remoteModelId, provider = "Configured"))
        }
        list
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .widthIn(min = 250.dp, max = 320.dp)
            .heightIn(max = 420.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isLocal) Icons.Outlined.Memory else Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Select Model",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    text = if (isLocal) (activeBackend ?: ChatConstants.BACKEND_GPU) else "Remote",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        if (installedModels.isEmpty() && allRemote.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No models available", style = MaterialTheme.typography.bodySmall) },
                onClick = {
                    onDismiss()
                    onNavigateToModels()
                },
            )
        } else {
            // ── 1. On-Device Models ──
            if (installedModels.isNotEmpty()) {
                if (allRemote.isNotEmpty()) {
                    Text(
                        text = "ON-DEVICE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                installedModels.forEach { model ->
                    val isActive = isLocal && (if (settings.customModelPath.isNotBlank()) {
                        settings.customModelPath == model.absolutePath
                    } else {
                        settings.activeModelId == model.id
                    })
                    val isCurrentlyLoaded = isModelLoaded && isActive

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${model.sizeDisplay} · ${if (isCurrentlyLoaded) "In RAM" else "Ready"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            onDismiss()
                            viewModel.setModelSource(ChatConstants.SOURCE_LOCAL)
                            if (model.isPreset) {
                                viewModel.setActivePreset(model.id)
                            } else {
                                viewModel.setActiveCustomPath(model.absolutePath)
                            }
                            viewModel.loadActiveModel()
                        },
                    )
                }
            }

            // ── 2. Remote Models ──
            if (allRemote.isNotEmpty()) {
                if (installedModels.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Text(
                    text = "REMOTE PROVIDER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 4.dp),
                )
                allRemote.forEach { rModel ->
                    val isRemoteActive = !isLocal && settings.remoteModelId == rModel.id
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = rModel.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isRemoteActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isRemoteActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Remote · ${rModel.provider.ifBlank { "API" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRemoteActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isRemoteActive) Icons.Default.CheckCircle else Icons.Outlined.Dns,
                                contentDescription = null,
                                tint = if (isRemoteActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            onDismiss()
                            viewModel.setModelSource(ChatConstants.SOURCE_REMOTE)
                            viewModel.setRemoteModelId(rModel.id)
                        },
                    )
                }
            }
        }
    }
}
