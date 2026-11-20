package com.raikaai.app.ui.chat

import com.raikaai.app.R

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.raikaai.app.data.ChatConstants
import com.raikaai.app.data.effectiveRemoteContextWindow
import com.raikaai.app.core.remote.RemoteAiClient
import android.app.Activity
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import com.raikaai.app.util.KLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.raikaai.app.core.audio.AudioPlayerHelper
import com.raikaai.app.core.audio.AudioRecorderHelper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.raikaai.app.ui.component.SearchSourcesCard
import com.raikaai.app.web.WebSourceCitation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.raikaai.app.data.ChatMessageEntry
import com.raikaai.app.ui.component.MarkdownText
import com.raikaai.app.ui.theme.success
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raikaai.app.data.Settings

/**
 * All chat overlays moved out of ChatScreen: compare dialog, MCP approval,
 * URL summarizer, image-gen dialog, library sheet, web preview, and the
 * image lightbox. State lives in [ChatOverlayState], shared with the
 * composer (which opens these overlays).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDialogsHost(
    viewModel: ChatViewModel,
    settings: Settings,
    installedModels: List<com.raikaai.app.localai.InstalledModel>,
    remoteModels: List<com.raikaai.app.core.remote.RemoteModelItem>,
    overlays: ChatOverlayState,
    artifacts: List<com.raikaai.app.artifacts.Artifact>,
    ragDocuments: List<com.raikaai.app.rag.RagDocument>,
    ragDocumentPicker: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    context: Context,
) {
    // ── Compare Mode Dialog ──
    if (overlays.showCompareDialog) {
        AlertDialog(
            onDismissRequest = { overlays.showCompareDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_compare_title))
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.chat_compare_desc),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = settings.compareMode,
                            onCheckedChange = { viewModel.setCompareMode(it) },
                        )
                    }
                    Text(
                        stringResource(R.string.chat_compare_pick),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (installedModels.isNotEmpty()) {
                            Text(
                                stringResource(R.string.chat_compare_ondevice),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        installedModels.forEach { model ->
                            val selected =
                                settings.compareSource == ChatConstants.SOURCE_LOCAL &&
                                    settings.compareModelId == model.absolutePath
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.setCompareChallenger(ChatConstants.SOURCE_LOCAL, model.absolutePath)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                            ) {
                                RadioButton(selected = selected, onClick = {
                                    viewModel.setCompareChallenger(ChatConstants.SOURCE_LOCAL, model.absolutePath)
                                })
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    model.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (remoteModels.isNotEmpty()) {
                            Text(
                                stringResource(R.string.chat_compare_remote),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        remoteModels.forEach { rModel ->
                            val selected =
                                settings.compareSource == ChatConstants.SOURCE_REMOTE &&
                                    settings.compareModelId == rModel.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.setCompareChallenger(ChatConstants.SOURCE_REMOTE, rModel.id)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                            ) {
                                RadioButton(selected = selected, onClick = {
                                    viewModel.setCompareChallenger(ChatConstants.SOURCE_REMOTE, rModel.id)
                                })
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    rModel.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (installedModels.isEmpty() && remoteModels.isEmpty()) {
                            Text(
                                stringResource(R.string.chat_compare_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.chat_compare_result_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { overlays.showCompareDialog = false }) { Text(stringResource(R.string.chat_action_done)) }
            },
        )
    }

    val mcpApprovalReq = viewModel.pendingMcpApproval.value
    if (mcpApprovalReq != null) {
        val toolSig =
            viewModel.mcpToolsByServer.value[mcpApprovalReq.serverId]
                ?.find { it.name == mcpApprovalReq.toolName }
                ?.signature ?: mcpApprovalReq.toolName
        AlertDialog(
            onDismissRequest = { viewModel.respondMcpApproval(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_mcp_title))
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.chat_mcp_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = toolSig,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.chat_mcp_server, mcpApprovalReq.serverName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = mcpApprovalReq.argumentsJson.ifBlank { "{}" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .padding(8.dp)
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.respondMcpApproval(true) }) { Text(stringResource(R.string.chat_mcp_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondMcpApproval(false) }) { Text(stringResource(R.string.chat_mcp_deny)) }
            },
        )
    }

    val containerApprovalReq = viewModel.pendingContainerApproval.value
    if (containerApprovalReq != null) {
        AlertDialog(
            onDismissRequest = { viewModel.respondContainerApproval(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Run in Linux Environment?")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "The AI wants to run this command in the Alpine sandbox (files limited to /workspace):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = containerApprovalReq.command,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .padding(8.dp)
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.approveContainerAlways() }) { Text("Always") }
                    TextButton(onClick = { viewModel.respondContainerApproval(true) }) { Text(stringResource(R.string.chat_mcp_allow)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondContainerApproval(false) }) { Text(stringResource(R.string.chat_mcp_deny)) }
            },
        )
    }

    if (overlays.showUrlDialog) {
        AlertDialog(
            onDismissRequest = { overlays.showUrlDialog = false },
            title = { Text(stringResource(R.string.chat_attach_summarize_link)) },
            text = {
                OutlinedTextField(
                    value = overlays.urlInput,
                    onValueChange = { overlays.urlInput = it },
                    label = { Text(stringResource(R.string.chat_url_label)) },
                    placeholder = { Text(stringResource(R.string.chat_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        overlays.showUrlDialog = false
                        viewModel.summarizeUrl(overlays.urlInput)
                        overlays.urlInput = ""
                    },
                    enabled = overlays.urlInput.isNotBlank(),
                ) { Text(stringResource(R.string.chat_url_summarize)) }
            },
            dismissButton = {
                TextButton(onClick = { overlays.showUrlDialog = false }) { Text(stringResource(R.string.chat_action_cancel)) }
            },
        )
    }

    if (overlays.showImageGenDialog) {
        AlertDialog(
            onDismissRequest = { overlays.showImageGenDialog = false },
            title = { Text(stringResource(R.string.chat_attach_generate_image)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = overlays.imageGenPrompt,
                        onValueChange = { overlays.imageGenPrompt = it },
                        label = { Text(stringResource(R.string.chat_imagegen_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.chat_imagegen_prompt_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.chat_imagegen_size), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1024x1024", "1024x1792", "1792x1024").forEach { size ->
                            FilterChip(
                                selected = overlays.imageGenSize == size,
                                onClick = { overlays.imageGenSize = size },
                                label = { Text(size, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    val imgModel = settings.remoteImageModelId.ifBlank { settings.remoteModelId }
                    Text(
                        if (imgModel.isBlank()) stringResource(R.string.chat_imagegen_no_model)
                        else stringResource(R.string.chat_imagegen_model, imgModel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        overlays.showImageGenDialog = false
                        viewModel.generateImage(overlays.imageGenPrompt, overlays.imageGenSize)
                        overlays.imageGenPrompt = ""
                    },
                    enabled = overlays.imageGenPrompt.isNotBlank(),
                ) { Text(stringResource(R.string.chat_imagegen_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { overlays.showImageGenDialog = false }) { Text(stringResource(R.string.chat_action_cancel)) }
            },
        )
    }

    // ── Library Sheet: Knowledge Base + Artifacts ──
    if (overlays.showRagSheet) {
        var libraryTab by remember { mutableStateOf(0) }
        val projects =
            remember(artifacts) {
                artifacts
                    .groupBy { it.projectId }
                    .map { (_, files) -> files.sortedBy { it.fileName } }
                    .sortedByDescending { group -> group.maxOf { it.updatedAt } }
            }

        ModalBottomSheet(
            onDismissRequest = { overlays.showRagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.chat_library_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = { overlays.showRagSheet = false },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.chat_action_close), modifier = Modifier.size(20.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = libraryTab == 0,
                        onClick = { libraryTab = 0 },
                        label = { Text(stringResource(R.string.chat_library_knowledge)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                    )
                    FilterChip(
                        selected = libraryTab == 1,
                        onClick = { libraryTab = 1 },
                        label = { Text(stringResource(R.string.chat_library_artifacts)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                    )
                }

                if (libraryTab == 0) {
                    Text(
                        stringResource(R.string.chat_library_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.chat_library_use_in_chat),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (ragDocuments.isEmpty()) {
                                        stringResource(R.string.chat_library_empty)
                                    } else {
                                        stringResource(R.string.chat_library_indexed, ragDocuments.size)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.ragEnabled,
                                onCheckedChange = { viewModel.setRagEnabled(it) },
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { ragDocumentPicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.chat_library_import))
                    }

                    Spacer(Modifier.height(12.dp))

                    if (ragDocuments.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No documents yet. Import one to get started.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(ragDocuments, key = { it.id }) { doc ->
                                val addedStr =
                                    SimpleDateFormat("MMM d \u00b7 HH:mm", Locale.getDefault()).format(Date(doc.addedAt))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = doc.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${doc.chunkCount} chunks \u00b7 ${doc.charCount / 1024} KB \u00b7 $addedStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteRagDocument(doc.id) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.chat_delete_document),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (projects.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No artifacts yet. Ask the assistant to build something and it will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(380.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(projects, key = { it.first().projectId }) { files ->
                                val projectId = files.first().projectId
                                val entryHtml = files.firstOrNull { it.fileName.endsWith(".html", true) }
                                val label =
                                    (entryHtml?.fileName?.substringBeforeLast('.')
                                        ?: files.first().fileName.substringBeforeLast('.'))
                                        .ifBlank { "project" }
                                val subtitle = files.joinToString(" \u00b7 ") { it.fileName }
                                val dateStr =
                                    SimpleDateFormat("MMM d \u00b7 HH:mm", Locale.getDefault())
                                        .format(Date(files.maxOf { it.updatedAt }))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.chat_artifact_title, label, files.size),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = dateStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Row {
                                                if (entryHtml != null) {
                                                    IconButton(
                                                        onClick = {
                                                            val target = entryHtml
                                                            viewModel.loadWebPreview(projectId) { _, map ->
                                                                val html = map[target.fileName]
                                                                if (html != null) {
                                                                    overlays.previewData = Triple(target.fileName, html, map)
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Visibility,
                                                            contentDescription = stringResource(R.string.chat_preview_project),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(17.dp),
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.exportArtifactProject(projectId, label) { n ->
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                if (n > 0) context.getString(R.string.chat_exported_files, n, label) else context.getString(R.string.chat_export_failed),
                                                                android.widget.Toast.LENGTH_SHORT,
                                                            ).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.FileDownload,
                                                        contentDescription = stringResource(R.string.chat_export_folder),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteArtifactProject(projectId) },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Delete,
                                                        contentDescription = stringResource(R.string.chat_delete_project),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp),
                                                    )
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
    }

    // ── Web Preview Sheet ──
    overlays.previewData?.let { data ->
        val (entryFile, entryHtml, fileMap) = data
        ModalBottomSheet(
            onDismissRequest = { overlays.previewData = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(0.92f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Preview: $entryFile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { overlays.previewData = null },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.chat_close_preview), modifier = Modifier.size(20.dp))
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        val web = WebView(ctx)
                        web.settings.javaScriptEnabled = true
                        web.settings.domStorageEnabled = true
                        // Sandboxed preview: the HTML comes from model output
                        // (untrusted). Block ALL network/file access except the
                        // in-memory project files served below.
                        web.settings.allowFileAccess = false
                        web.settings.allowContentAccess = false
                        web.webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean = true // never leave the preview

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    // In-document schemes carry no network
                                    // access. Newer WebViews route the
                                    // data: document of loadDataWithBaseURL
                                    // itself through here; blocking it (no
                                    // host) made every preview fail with
                                    // ERR_HTTP_RESPONSE_CODE_FAILURE.
                                    when (request.url.scheme?.lowercase()) {
                                        "data", "about", "blob" -> return null
                                    }
                                    val host = request.url.host
                                    if (host != "appassets.raikaai.local") {
                                        // Foreign host (exfil via <img>/fetch):
                                        // answer with an empty body instead of
                                        // letting the WebView fetch it.
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            403,
                                            "Blocked",
                                            emptyMap(),
                                            ByteArray(0).inputStream(),
                                        )
                                    }
                                    val path = Uri.decode(request.url.path.orEmpty()).trimStart('/')
                                    val content = fileMap[path]
                                        ?: fileMap.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
                                        ?: return null
                                    return WebResourceResponse(
                                        com.raikaai.app.util.FileSaver.mimeFor(path),
                                        "utf-8",
                                        content.byteInputStream(),
                                    )
                                }
                            }
                        web.loadDataWithBaseURL(
                            "https://appassets.raikaai.local/",
                            entryHtml,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        web
                    },
                    // B37: WebViews hold native resources; destroy on dismiss.
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
            }
        }
    }

    // ── Fullscreen Image Preview Lightbox ──
    overlays.previewImagePath?.let { path ->
        ImageViewerDialog(
            imagePath = path,
            onDismiss = { overlays.previewImagePath = null },
        )
    }
}
