package com.raikaai.app.ui.chat

import com.raikaai.app.R

import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

// SlashCommand, SpeechRecognizerHelper, TtsHelper extracted to separate files
// See: SlashCommands.kt, SpeechRecognizerHelper.kt, TtsHelper.kt

// Composables extracted to ChatComponents.kt
// See: ChatComponents.kt for BranchNavRow, EmptyChatHero, MessageList, MessageBubble, getIconVector, formatFileSize

// SpeechRecognizerHelper extracted to SpeechRecognizerHelper.kt
// TtsHelper extracted to TtsHelper.kt
// Composables extracted to ChatComponents.kt

/**
 * Short, truncation-friendly model label for compact UI spots (top bar subtitle, chips).
 * Strips provider prefixes ("provider/model"), paths, and file extensions so the
 * remaining name fits on one line instead of being cut mid-word.
 */
internal fun shortModelLabel(id: String): String =
    id.substringAfterLast('/')
        .substringAfterLast('\\')
        .removePrefix("custom:")
        .removeSuffix(".litertlm")
        .removeSuffix(".task")
        .removeSuffix(".bin")
        .replace("_", " ")
        .replace("-", " ")
        .trim()
        .ifBlank { id }

/**
 * ChatterUI-inspired Full Modern Chat Interface for RaikaAI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateToCharacters: () -> Unit = {},
    onNavigateToModels: () -> Unit = {},
    onBack: () -> Unit = {},
    isRolePlay: Boolean = false
) {
    LaunchedEffect(isRolePlay) {
        viewModel.setRoleplayMode(isRolePlay)
    }
    val settings by viewModel.settings.collectAsState()
    val activePersona by viewModel.activePersona.collectAsState()
    LaunchedEffect(isRolePlay, activePersona.id) {
        if (!isRolePlay) viewModel.clearRoleplayPersonaForNormalChat()
    }
    val loadedModelPath by viewModel.loadedModelPath.collectAsState()
    val isGenerating by viewModel.isGenerating
    // B39: hoisted derived state — topBar/bottomBar read this, and reading
    // messages.isNotEmpty() directly there recomposed them on every ~50ms
    // token batch.
    val hasMessages by remember { derivedStateOf { viewModel.messages.isNotEmpty() } }
    val errorMessage by viewModel.errorMessage
    val context = LocalContext.current

    var input by remember { mutableStateOf(TextFieldValue("")) }
    fun setInputText(text: String) {
        input = TextFieldValue(text = text, selection = TextRange(text.length))
    }

    // Widget prefill: "Ask RaikaAI" home-screen widget delivers a prompt via
    // MainActivity's intent extra; consume it once into the composer.
    val pendingPrefill by viewModel.pendingPrefill
    LaunchedEffect(pendingPrefill) {
        val p = pendingPrefill
        if (!p.isNullOrBlank()) {
            viewModel.consumePrefill()
            setInputText(p)
        }
    }
    // Overlay/dialog state shared with the composer and dialogs host.
    val overlays = remember { ChatOverlayState() }
    val urlFetchState by viewModel.urlFetchState
    val imageGenState by viewModel.imageGenState

    val installedModels by viewModel.installedModels
    val remoteModels by viewModel.remoteModels

    val artifacts by viewModel.artifacts.collectAsState()
    val isCompressing by viewModel.isCompressing

    val ragDocuments by viewModel.ragDocuments.collectAsState()
    val ragDocumentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importRagDocument)
        }

    val selectedAudioFile by viewModel.selectedAudioFile.collectAsState()
    val isAudioSupported by remember(settings.activeModelId, settings.customModelPath, settings.modelSource, loadedModelPath) {
        derivedStateOf { viewModel.isAudioSupported }
    }

    val selectedAttachments by viewModel.selectedAttachments.collectAsState()
    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)) { uris ->
            viewModel.addAttachments(uris, forceImage = true)
        }
    // Legacy fallback for devices without the system photo picker
    // (no Play Services backport): launching PickVisualMedia there throws
    // ActivityNotFoundException and kills the app instantly.
    val imageFallbackLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            viewModel.addAttachments(uris, forceImage = true)
        }
    fun launchImagePicker() {
        try {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } else {
                imageFallbackLauncher.launch("image/*")
            }
        } catch (_: Exception) {
            try {
                imageFallbackLauncher.launch("image/*")
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.chat_toast_no_picker), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.addAttachments(listOf(uri))
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(viewModel::attachAudioUri)
        }

    val speechIntentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotBlank()) {
                    val cur = input.text
                    setInputText(if (cur.isBlank()) spoken else "$cur $spoken")
                }
            }
        }

    // Helpers hold no Activity reference: applicationContext avoids leaking
    // the Activity across rotations (helpers survive in remember).
    val appContext = remember(context) { context.applicationContext }
    val ttsHelper = remember(appContext) { TtsHelper(appContext) }
    val speechHelper = remember(appContext) { SpeechRecognizerHelper(appContext) }
    val audioPlayerHelper = remember(appContext) { AudioPlayerHelper(appContext) }
    val audioRecorderHelper = remember(appContext) { AudioRecorderHelper(appContext) }
    val isRecordingAudio by audioRecorderHelper.isRecording.collectAsState()
    val recordingDurationMs by audioRecorderHelper.durationMs.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            speechHelper.destroy()
            ttsHelper.destroy()
            audioPlayerHelper.stop()
            audioRecorderHelper.destroy()
            // B40: stop an in-flight generation when leaving the chat screen,
            // but NOT on configuration change (rotation) — the VM survives it.
            if ((context as? Activity)?.isChangingConfigurations != true) {
                viewModel.stopGeneration()
            }
        }
    }

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                val sttAvailable =
                    try {
                        android.speech.SpeechRecognizer.isRecognitionAvailable(context)
                    } catch (_: Throwable) {
                        false
                    }
                if (isAudioSupported || !sttAvailable) {
                    val audioDir = File(context.filesDir, "chat_audio").apply { if (!exists()) mkdirs() }
                    val targetWav = File(audioDir, "rec_${System.currentTimeMillis()}.wav")
                    audioRecorderHelper.startRecording(targetWav)
                } else {
                    speechHelper.startListening(
                        onResult = { transcribed ->
                            val cur = input.text
                            setInputText(if (cur.isBlank()) transcribed else "$cur $transcribed")
                        },
                        onFallbackToIntent = { intent ->
                            try {
                                speechIntentLauncher.launch(intent)
                            } catch (e: Exception) {
                                KLog.w("STT", "Speech intent failed, fallback to audio: ${e.message}")
                                val audioDir = File(context.filesDir, "chat_audio").apply { if (!exists()) mkdirs() }
                                val targetWav = File(audioDir, "rec_${System.currentTimeMillis()}.wav")
                                audioRecorderHelper.startRecording(targetWav)
                            }
                        },
                    )
                }
            } else {
                Toast.makeText(context, context.getString(R.string.chat_toast_mic_permission), Toast.LENGTH_SHORT).show()
            }
        }

    val startVoiceRecordingSafely: () -> Unit = {
        val hasPerm =
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val audioDir = File(context.filesDir, "chat_audio").apply { if (!exists()) mkdirs() }
            val targetWav = File(audioDir, "rec_${System.currentTimeMillis()}.wav")
            audioRecorderHelper.startRecording(targetWav)
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val launchSpeechIntentSafely: (Intent) -> Unit = { intent ->
        try {
            speechIntentLauncher.launch(intent)
        } catch (e: Exception) {
            KLog.w("STT", "No speech recognition activity found, falling back to on-device audio recording: ${e.message}")
            startVoiceRecordingSafely()
        }
    }

    val isSlashActive = input.text.startsWith("/")
    val matchingSlashCommands =
        remember(input.text) {
            if (input.text.startsWith("/")) {
                val q = input.text.removePrefix("/").trim()
                if (q.isEmpty()) {
                    DEFAULT_SLASH_COMMANDS
                } else {
                    DEFAULT_SLASH_COMMANDS.filter {
                        it.command.contains(q, ignoreCase = true) ||
                            it.title.contains(q, ignoreCase = true) ||
                            it.description.contains(q, ignoreCase = true)
                    }
                }
            } else {
                emptyList()
            }
        }

    val modelPin by viewModel.modelPinState

    val ollamaLanLabel = stringResource(R.string.chat_model_ollama_lan)
    val noModelLabel = stringResource(R.string.chat_model_none_selected)
    val modelDisplayName = remember(settings.modelSource, settings.activeModelId, settings.customModelPath, settings.remoteModelId, loadedModelPath, modelPin, ollamaLanLabel, noModelLabel) {
        val pin = modelPin
        if (pin != null) {
            val (_, id) = pin
            shortModelLabel(id)
        } else if (settings.modelSource == ChatConstants.SOURCE_REMOTE) {
            shortModelLabel(settings.remoteModelId.ifBlank { ollamaLanLabel })
        } else {
            val fileName = loadedModelPath?.let { File(it).name }
                ?: settings.customModelPath.ifBlank { null }?.let { File(it).name }
                ?: settings.activeModelId.ifBlank { noModelLabel }
            shortModelLabel(fileName)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                activePersona = activePersona,
                onOpenDrawer = onOpenDrawer,
                onNavigateToModels = onNavigateToModels,
                onNewChat = { viewModel.newChat() },
                onBack = onBack,
                isRolePlay = isRolePlay
            )
        },
        bottomBar = {
            // ── Floating Compact Composer Bar ──
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                // Live dev-server banner: the agent may start listeners in the
                // sandbox, so surface the verified URL with a one-tap open
                // instead of making the user guess the port.
                val previewUrl by viewModel.containerPreviewUrl.collectAsState()
                if (previewUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = previewUrl.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.refreshContainerPreview() }) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = "Re-check port",
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            TextButton(
                                onClick = {
                                    val url = previewUrl ?: return@TextButton
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }.onFailure {
                                        Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) {
                                Text("Open", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                // Token Context indicator (Subtle status with dynamic progress line)
                if (hasMessages || input.text.isNotBlank()) {
                    val totalContextTokens = viewModel.liveContextTokens.intValue
                    val maxTokens = settings.contextWindowTokens
                    val ratio = if (maxTokens > 0) (totalContextTokens.toFloat() / maxTokens.toFloat()) * 100f else 0f
                    val progressFraction = (ratio / 100f).coerceIn(0.01f, 1f)

                    val progressColor = when {
                        ratio >= 80f -> MaterialTheme.colorScheme.error
                        // Fixed amber: intentional semantic warning color, legible
                        // on both dark and light themes (tertiary would be green).
                        ratio >= 60f -> Color(0xFFB26A00)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val inputTokens = viewModel.estimateTokens(input.text)
                            if (isCompressing || input.text.isNotBlank()) {
                                Text(
                                    text =
                                        if (isCompressing) {
                                            stringResource(R.string.chat_compressing)
                                        } else {
                                            stringResource(R.string.chat_context_tokens_extra, inputTokens)
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            Text(
                                text = stringResource(R.string.chat_context_status, totalContextTokens, maxTokens, ratio.toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ratio >= 60f) progressColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (ratio >= 80f) FontWeight.Bold else FontWeight.Normal,
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        // Dynamic thin progress line (2.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(progressColor),
                            )
                        }
                    }
                }

                // ── Slash Commands Quick Template Bar ──
                AnimatedVisibility(
                    visible = isSlashActive && matchingSlashCommands.isNotEmpty(),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        LazyRow(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(matchingSlashCommands, key = { it.command }) { cmd ->
                                Surface(
                                    onClick = {
                                        setInputText(cmd.promptTemplate)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            cmd.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            cmd.command,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // Voice Input Listening Status Bar
                        if (speechHelper.isListening.value) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, end = 10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.Mic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.chat_listening),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    IconButton(
                                        onClick = { speechHelper.stopListening() },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = "Stop listening",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }

                        // Multi-attachment preview (image thumbnails + file chips)
                        if (selectedAttachments.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .padding(start = 10.dp, top = 6.dp, end = 10.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                items(selectedAttachments, key = { it.uri.toString() }) { att ->
                                    if (att.isImage) {
                                        val bitmap by produceState<Bitmap?>(initialValue = null, att.uri) {
                                            value =
                                                withContext(Dispatchers.IO) {
                                                    decodeSampledBitmapFromUri(context.contentResolver, att.uri, reqSizePx = 192)
                                                }
                                        }
                                        // NOTE: no manual bitmap.recycle() here — the decoded
                                        // bitmap is still referenced by the Image below and
                                        // recycling it racy-ly crashes with
                                        // "Canvas: trying to use a recycled bitmap".
                                        val previewBitmap = bitmap
                                        if (previewBitmap != null) {
                                            Box(modifier = Modifier.size(44.dp)) {
                                                Image(
                                                    bitmap = previewBitmap.asImageBitmap(),
                                                    contentDescription = stringResource(R.string.chat_selected_image_preview),
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(6.dp)),
                                                    contentScale = ContentScale.Crop,
                                                )
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(16.dp)
                                                        .clickable { viewModel.removeAttachment(att.uri) },
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = stringResource(R.string.chat_remove_attached_image),
                                                        modifier = Modifier.padding(2.dp),
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Description,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    att.name.take(18),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.chat_remove_attached_file),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { viewModel.removeAttachment(att.uri) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Audio Attachment Preview (WAV / Audio file)
                        if (selectedAudioFile != null) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 10.dp, top = 6.dp, end = 10.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.chat_audio_clip_attached),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.clearSelectedAudio() },
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.chat_remove_attached_audio),
                                                modifier = Modifier.padding(2.dp),
                                                tint = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Live Native Audio Recording Banner
                        if (isRecordingAudio) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    val sec = (recordingDurationMs / 1000) % 60
                                    val min = (recordingDurationMs / 1000) / 60
                                    Text(
                                        text = stringResource(R.string.chat_recording, min, sec),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = { audioRecorderHelper.cancelRecording() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(stringResource(R.string.chat_action_cancel), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            audioRecorderHelper.stopRecordingAsync { wavFile ->
                                                wavFile?.let { viewModel.setSelectedAudio(it) }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Text(stringResource(R.string.chat_action_done), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError)
                                    }
                                }
                            }
                        }

                        // URL fetch / image generation progress indicator
                        val fetchingState = urlFetchState
                        val generatingImageState = imageGenState
                        val busyLabel = fetchingState ?: generatingImageState
                        if (busyLabel != null) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 10.dp, top = 6.dp, end = 10.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    busyLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Warn when an image is attached in remote mode but the
                        // selected remote model doesn't look vision-capable.
                        if (selectedAttachments.any { it.isImage } &&
                            settings.modelSource == ChatConstants.SOURCE_REMOTE &&
                            !RemoteAiClient.isLikelyVisionModel(settings.remoteModelId)
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.chat_vision_warning),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }

                        // ── Top Row: Compact Text Input Field ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                minLines = 1,
                                maxLines = 5,
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (input.text.isEmpty()) {
                                            Text(
                                                text =
                                                    when {
                                                        selectedAttachments.isNotEmpty() -> stringResource(R.string.chat_hint_attachments)
                                                        selectedAudioFile != null -> stringResource(R.string.chat_hint_audio)
                                                        else -> stringResource(R.string.chat_hint_message, activePersona.name)
                                                    },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }

                        // ── Bottom Row: Action Toolbar (Add Menu, Model Dropup, Badges, Send) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 8.dp, bottom = 6.dp, top = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Left Group: [+] Attachment Menu & Model Dropup Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                // 1. [+] Universal Attachment & Tools Menu
                                Box {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        IconButton(
                                            onClick = { overlays.showAttachmentMenu = true },
                                            modifier = Modifier.size(30.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.chat_add_attachment),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = overlays.showAttachmentMenu,
                                        onDismissRequest = { overlays.showAttachmentMenu = false },
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chat_attach_images), style = MaterialTheme.typography.bodySmall) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                overlays.showAttachmentMenu = false
                                                launchImagePicker()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chat_attach_file), style = MaterialTheme.typography.bodySmall) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                overlays.showAttachmentMenu = false
                                                filePickerLauncher.launch(arrayOf("*/*"))
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chat_attach_knowledge), style = MaterialTheme.typography.bodySmall) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                            },
                                            trailingIcon = {
                                                if (ragDocuments.isNotEmpty()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(50),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    ) {
                                                        Text(
                                                            "${ragDocuments.size}",
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                overlays.showAttachmentMenu = false
                                                overlays.showRagSheet = true
                                            },
                                        )
                                    }
                                }

                                // 2. Model Selector Pill with Floating Dropup Menu
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (overlays.showModelDropdown) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { overlays.showModelDropdown = true },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        ) {
                                            if (modelPin != null) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = stringResource(R.string.chat_pinned_model),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                                Spacer(Modifier.width(3.dp))
                                            }
                                            Text(
                                                text = modelDisplayName.take(16) + if (modelDisplayName.length > 16) "…" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Icon(
                                                imageVector = if (overlays.showModelDropdown) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = stringResource(R.string.chat_select_model),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }

                                    ModelQuickSwitchMenu(
                                        expanded = overlays.showModelDropdown,
                                        onDismiss = { overlays.showModelDropdown = false },
                                        viewModel = viewModel,
                                        onNavigateToModels = onNavigateToModels,
                                        onCompareClick = { overlays.showCompareDialog = true },
                                    )
                                }

                                // 3. Quick RAG Pill (when documents exist)
                                if (settings.ragEnabled && ragDocuments.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { overlays.showRagSheet = true },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.AttachFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(11.dp),
                                            )
                                                                    Text(
                                                stringResource(R.string.chat_docs_count, ragDocuments.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            // Right Group: Voice Input & Send / Stop Action Button
                            val canSend = input.text.isNotBlank() || selectedAttachments.isNotEmpty() || selectedAudioFile != null
                            val isListening = speechHelper.isListening.value || isRecordingAudio
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // Voice Input Mic Button (Native Audio recording for audio-capable models, STT for text models)
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isListening) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                        },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (isRecordingAudio) {
                                                audioRecorderHelper.stopRecordingAsync { wavFile ->
                                                    wavFile?.let { viewModel.setSelectedAudio(it) }
                                                }
                                            } else if (speechHelper.isListening.value) {
                                                speechHelper.stopListening()
                                            } else {
                                                val sttAvailable =
                                                    try {
                                                        android.speech.SpeechRecognizer.isRecognitionAvailable(context)
                                                    } catch (_: Throwable) {
                                                        false
                                                    }
                                                if (isAudioSupported || !sttAvailable) {
                                                    startVoiceRecordingSafely()
                                                } else {
                                                    val hasPerm =
                                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                                            context,
                                                            android.Manifest.permission.RECORD_AUDIO,
                                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    if (hasPerm) {
                                                        speechHelper.startListening(
                                                            onResult = { transcribed ->
                                                                val cur = input.text
                                                                setInputText(if (cur.isBlank()) transcribed else "$cur $transcribed")
                                                            },
                                                            onFallbackToIntent = launchSpeechIntentSafely,
                                                        )
                                                    } else {
                                                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                                            contentDescription = if (isListening) stringResource(R.string.chat_stop_recording) else stringResource(R.string.chat_voice_input),
                                            tint = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                }

                                // Send / Stop Button
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isGenerating) {
                                            MaterialTheme.colorScheme.error
                                        } else if (canSend) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                        },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (isGenerating) {
                                                viewModel.stopGeneration()
                                            } else if (canSend) {
                                                val text = input.text
                                                input = TextFieldValue("")
                                                viewModel.sendMessage(text)
                                            }
                                        },
                                        enabled = isGenerating || canSend,
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                            contentDescription = if (isGenerating) stringResource(R.string.chat_stop) else stringResource(R.string.chat_send),
                                            tint = if (isGenerating || canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (!hasMessages) {
                // ── Welcome Empty State Hero ──
                EmptyChatHero(
                    persona = activePersona,
                    modelName = modelDisplayName,
                    onPromptSelected = { prompt ->
                        viewModel.sendMessage(prompt)
                    },
                    onOpenCharacters = onNavigateToCharacters,
                )
            } else {
                // ── Messages Feed ──
                MessageList(
                    viewModel = viewModel,
                    activePersona = activePersona,
                    isGenerating = isGenerating,
                    enableThinking = settings.enableThinking,
                    ttsHelper = ttsHelper,
                    audioPlayerHelper = audioPlayerHelper,
                    onPreviewHtml = { code ->
                        val resolvedName = com.raikaai.app.util.CodeArtifacts.deriveFileName("html", code)
                        overlays.previewData = Triple(resolvedName, code, mapOf(resolvedName to code))
                    },
                    onPreviewImage = { path ->
                        overlays.previewImagePath = path
                    },
                )
            }

            // Error Banner
            errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    ChatDialogsHost(
        viewModel = viewModel,
        settings = settings,
        installedModels = installedModels,
        remoteModels = remoteModels,
        overlays = overlays,
        artifacts = artifacts,
        ragDocuments = ragDocuments,
        ragDocumentPicker = ragDocumentPicker,
        context = context,
    )
}

// Composables extracted to ChatComponents.kt
