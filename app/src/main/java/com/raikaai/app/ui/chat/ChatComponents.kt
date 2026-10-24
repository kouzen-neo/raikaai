package com.raikaai.app.ui.chat

import com.raikaai.app.R

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.raikaai.app.util.FileSaver
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.raikaai.app.core.audio.AudioPlayerHelper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.raikaai.app.data.AttachmentType
import com.raikaai.app.data.ChatAttachment
import com.raikaai.app.data.ChatConstants
import com.raikaai.app.data.ChatMessageEntry
import com.raikaai.app.ui.component.MarkdownText
import com.raikaai.app.ui.component.SearchSourcesCard
import com.raikaai.app.web.WebSourceCitation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Branch navigation pill shown under bubbles that have sibling branches
 * (from editing an older message or regenerating). Matches the variant
 * swipe pagination styling.
 */
@Composable
internal fun BranchNavRow(
    position: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBranch: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(top = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        IconButton(
            onClick = onPrev,
            enabled = position > 1,
            modifier = Modifier.size(18.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = stringResource(R.string.chat_branch_prev),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = "$position/$total",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        IconButton(
            onClick = onNext,
            enabled = position < total,
            modifier = Modifier.size(18.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = stringResource(R.string.chat_branch_next),
                modifier = Modifier.size(14.dp),
            )
        }
        if (onBranch != null) {
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = onBranch,
                modifier = Modifier.size(18.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = stringResource(R.string.chat_branch_to_new_chat),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Claude-style card for attached documents and text files.
 * Shows file type badge, file size, truncated file name, and is tap-inspectable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AttachmentFileCard(
    file: ChatAttachment,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ext = remember(file.name) {
        val raw = file.name.substringAfterLast('.', "").uppercase()
        if (raw.isNotBlank() && raw.length <= 4) raw else "FILE"
    }
    val sizeStr: String? = remember(file.path) {
        if (file.path.isNotBlank()) {
            val f = File(file.path)
            if (f.exists()) formatFileSize(f.length()) else null
        } else null
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .width(140.dp)
            .height(105.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = ext,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (sizeStr != null) {
                    Text(
                        text = sizeStr,
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }

            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Minimal empty-state hero: plain centered greeting only.
 * Signature kept so the caller in ChatScreen.kt is unaffected.
 */
@Composable
internal fun EmptyChatHero(
    persona: PersonaPreset,
    modelName: String,
    onPromptSelected: (String) -> Unit,
    onOpenCharacters: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chat_hero_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_hero_tip),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Message list with ChatterUI-style bubble and frame layouts.
 * Uses reverseLayout = true so incoming streaming tokens (and long thinking blocks)
 * naturally anchor to the bottom without jumping or locking user scroll position.
 */
@Composable
internal fun MessageList(
    viewModel: ChatViewModel,
    activePersona: PersonaPreset,
    isGenerating: Boolean,
    enableThinking: Boolean,
    ttsHelper: TtsHelper,
    audioPlayerHelper: AudioPlayerHelper? = null,
    onPreviewHtml: (String) -> Unit = {},
    onPreviewImage: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messageCount = viewModel.messages.size
    // Collect ONLY autoScroll (not the whole Settings): any other settings
    // change must not recompose the entire message list.
    val autoScroll by viewModel.settings.map { it.autoScroll }.distinctUntilChanged()
        .collectAsState(initial = true)

    // Scroll to bottom (index 0 in reverseLayout) when a new message is sent or started.
    // Respects the "Auto-scroll" setting — when off, the list stays where the user left it.
    LaunchedEffect(messageCount, autoScroll) {
        if (messageCount > 0 && autoScroll) {
            listState.animateScrollToItem(0)
        }
    }

    val isScrolledUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 250
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                count = messageCount,
                key = { i ->
                    val reversedIndex = messageCount - 1 - i
                    val msg = viewModel.messages.getOrNull(reversedIndex)
                    msg?.id ?: "msg_${reversedIndex}_$i"
                },
                // Distinct composition types per shape lets Compose recycle
                // slots efficiently (text bubble vs image/audio variants).
                contentType = { i ->
                    val reversedIndex = messageCount - 1 - i
                    val msg = viewModel.messages.getOrNull(reversedIndex)
                    val kind = when {
                        msg == null -> "null"
                        msg.attachments.orEmpty().any { it.kind == AttachmentType.IMAGE } || !msg.imagePath.isNullOrBlank() -> "media"
                        !msg.audioPath.isNullOrBlank() -> "audio"
                        else -> "text"
                    }
                    "${msg?.role}_$kind"
                },
            ) { i ->
                val reversedIndex = messageCount - 1 - i
                val msg = viewModel.messages.getOrNull(reversedIndex) ?: return@items
                val isLast = (reversedIndex == messageCount - 1)
                MessageBubble(
                    messageIndex = reversedIndex,
                    message = msg,
                    activePersona = activePersona,
                    isLastMessage = isLast,
                    isGenerating = isGenerating,
                    enableThinking = enableThinking,
                    ttsHelper = ttsHelper,
                    audioPlayerHelper = audioPlayerHelper,
                    branchInfo = viewModel.branchInfoFor(reversedIndex),
                    onEditUserMessage = { newText -> viewModel.editUserMessageAndRegenerate(reversedIndex, newText) },
                    onSelectVariant = { varIndex -> viewModel.selectMessageVariant(reversedIndex, varIndex) },
                    onSwitchBranch = { delta -> viewModel.switchBranch(reversedIndex, delta) },
                    onBranchToNewChat = { viewModel.branchToNewChat(reversedIndex) },
                    onRegenerate = { viewModel.regenerateLastResponse() },
                    onPreviewHtml = onPreviewHtml,
                    onPreviewImage = onPreviewImage,
                )
            }
        }

        // Floating Jump to Bottom action button when user scrolls up
        AnimatedVisibility(
            visible = isScrolledUp,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        ) {
            Surface(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp,
                tonalElevation = 4.dp,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.chat_scroll_bottom),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Minimal chat message layout (Claude-style): assistant replies render flat
 * directly on the background (no Surface, border, or header), user messages
 * use a thin neutral bubble. All actions (edit, copy, TTS, regenerate,
 * branch nav) are preserved in a small action row below the message.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    messageIndex: Int,
    message: ChatMessageEntry,
    activePersona: PersonaPreset,
    isLastMessage: Boolean,
    isGenerating: Boolean,
    enableThinking: Boolean,
    ttsHelper: TtsHelper,
    audioPlayerHelper: AudioPlayerHelper? = null,
    branchInfo: Pair<Int, Int>?,
    onEditUserMessage: (String) -> Unit,
    onSelectVariant: (Int) -> Unit,
    onSwitchBranch: (Int) -> Unit,
    onBranchToNewChat: () -> Unit = {},
    onRegenerate: () -> Unit,
    onPreviewHtml: (String) -> Unit = {},
    onPreviewImage: (String) -> Unit = {},
) {
    if (message.role == ChatConstants.ROLE_COMPACTION) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 2.dp),
        ) {
            // ── OpenCode-style Compaction Divider ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = message.stats ?: "Compaction",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(10.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }

            // ── Structured Markdown Briefing ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    MarkdownText(
                        markdown = message.content,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        return
    }

    val isUser = message.role == ChatConstants.ROLE_USER
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<ChatAttachment?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Attached Image if present (above the message body).
        // Skipped when the message already carries image attachments below:
        // those render inside the bubble and the legacy imagePath would
        // otherwise duplicate the same picture.
        val hasAttachmentImages = message.attachments.orEmpty().any { it.kind == AttachmentType.IMAGE }
        if (!message.imagePath.isNullOrBlank() && !hasAttachmentImages) {
            // Decode off the main thread, downsampled (was: main-thread
            // full-size decode in remember — ANR/OOM on large camera photos).
            // No manual recycle: the bitmap is referenced by the Image below.
            val legacyPath = message.imagePath
            val bitmap by produceState<Bitmap?>(initialValue = null, legacyPath) {
                value =
                    withContext(Dispatchers.IO) {
                        if (legacyPath.isNullOrBlank()) {
                            null
                        } else {
                            decodeSampledBitmapFromFile(File(legacyPath), reqSizePx = 1024)
                        }
                    }
            }
            // Copy to a local val first: smart cast doesn't work on
            // delegated properties.
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.chat_attached_image),
                    modifier = Modifier
                        .fillMaxWidth(if (isUser) 0.85f else 1f)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPreviewImage(message.imagePath) },
                    contentScale = ContentScale.FillWidth,
                )
            }
        }

        // Attached Audio if present (Native multimodal voice note)
        if (!message.audioPath.isNullOrBlank()) {
            AudioMessagePlayer(
                audioPath = message.audioPath,
                audioPlayerHelper = audioPlayerHelper,
                isUser = isUser,
                modifier = Modifier
                    .fillMaxWidth(if (isUser) 0.85f else 1f)
                    .padding(vertical = 6.dp),
            )
        }

        // Message Text Content
        if (isUser) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                // Attachments: Claude-style Document Cards + Images
                val attachmentFiles =
                    remember(message.attachments, message.content) {
                        AttachmentPipeline.resolveFileAttachments(message)
                    }
                val attachmentImages =
                    remember(message.attachments, message.imagePath) {
                        val fromList = message.attachments.orEmpty().filter { it.kind == AttachmentType.IMAGE }.map { it.path }
                        if (fromList.isNotEmpty()) fromList
                        else listOfNotNull(message.imagePath?.takeIf { it.isNotBlank() })
                    }

                // 1. Attached document cards carousel (Claude-style)
                if (attachmentFiles.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(attachmentFiles) { file ->
                            AttachmentFileCard(
                                file = file,
                                onClick = { previewFile = file },
                                onLongClick = { showEditDialog = true },
                            )
                        }
                    }
                }

                // 2. Attached images
                attachmentImages.forEach { imagePath ->
                    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
                        value =
                            withContext(Dispatchers.IO) {
                                decodeSampledBitmapFromFile(File(imagePath), reqSizePx = 1024)
                            }
                    }
                    val currentBitmap = bitmap
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.chat_attached_image),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPreviewImage(imagePath) },
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }

                // 3. User message text bubble
                val userDisplayText = remember(message.content) {
                    AttachmentPipeline.extractUserDisplayText(message.content)
                }
                val isDefaultSummary = remember(userDisplayText) {
                    AttachmentPipeline.isDefaultAttachmentPrompt(userDisplayText)
                }

                if (userDisplayText.isNotBlank() && (!isDefaultSummary || attachmentFiles.isEmpty())) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier =
                            Modifier
                                .wrapContentWidth()
                                .widthIn(max = 340.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { showEditDialog = true },
                                ),
                    ) {
                        Text(
                            text = userDisplayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            // Edit via long-press on the bubble or card
        } else {
            MarkdownText(
                markdown = message.content.ifBlank { "…" },
                enableThinking = enableThinking,
                onPreviewHtml = onPreviewHtml,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )

            // Web Search Clickable & Collapsible Sources Card
            val webCitations = remember(message.sources) {
                message.sources.orEmpty().mapNotNull { raw ->
                    WebSourceCitation.parse(raw)
                }
            }
            if (webCitations.isNotEmpty()) {
                SearchSourcesCard(
                    sources = webCitations,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            // Local Document RAG citation chips (if non-web)
            val docSources = remember(message.sources, webCitations) {
                if (webCitations.isNotEmpty()) {
                    message.sources.orEmpty().filter { raw ->
                        WebSourceCitation.parse(raw) == null
                    }
                } else {
                    message.sources.orEmpty()
                }
            }
            if (docSources.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                            docSources.take(3).forEach { label ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(11.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                }
            }

            // Footer Row: Telemetry Stats + Swipes Pagination + Quick Actions.
            // Hidden for greeting messages (no stats, single variant, not regenerable).
            if (!message.stats.isNullOrBlank() || message.variants.size > 1 || (isLastMessage && !isGenerating)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!message.stats.isNullOrBlank()) {
                        Text(
                            text = message.stats,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (message.variants.size > 1) {
                        Spacer(Modifier.width(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 4.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (message.selectedVariant > 0) {
                                        onSelectVariant(message.selectedVariant - 1)
                                    }
                                },
                                enabled = message.selectedVariant > 0,
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = stringResource(R.string.chat_variant_prev),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                text = "${message.selectedVariant + 1}/${message.variants.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                            IconButton(
                                onClick = {
                                    if (message.selectedVariant < message.variants.lastIndex) {
                                        onSelectVariant(message.selectedVariant + 1)
                                    }
                                },
                                enabled = message.selectedVariant < message.variants.lastIndex,
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = stringResource(R.string.chat_variant_next),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy Action
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(message.content))
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy_message),
                            modifier = Modifier.size(13.dp),
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Read Aloud / TTS Action
                    val isSpeaking = (ttsHelper.currentlySpeakingId.value == (message.id ?: "msg_$messageIndex"))
                    IconButton(
                        onClick = {
                            val msgId = message.id ?: "msg_$messageIndex"
                            if (isSpeaking) {
                                ttsHelper.stop()
                            } else {
                                ttsHelper.speak(msgId, message.content)
                            }
                        },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = if (isSpeaking) stringResource(R.string.chat_stop_reading) else stringResource(R.string.chat_read_aloud),
                            modifier = Modifier.size(14.dp),
                            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Share Action
                    IconButton(
                        onClick = {
                            val sendIntent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.content)
                                    type = "text/plain"
                                }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Message"))
                        },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.chat_share_message),
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Branch in New Chat Action
                    IconButton(
                        onClick = onBranchToNewChat,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = stringResource(R.string.chat_branch_to_new_chat),
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Regenerate Action (only on last assistant message when not generating)
                    if (isLastMessage && !isGenerating) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.chat_regenerate),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            }
        }

        if (branchInfo != null) {
            BranchNavRow(
                position = branchInfo.first,
                total = branchInfo.second,
                onPrev = { onSwitchBranch(-1) },
                onNext = { onSwitchBranch(1) },
                onBranch = onBranchToNewChat,
            )
        }
    }

    if (showEditDialog) {
        val initialPrompt = remember(message.content) {
            AttachmentPipeline.extractUserDisplayText(message.content)
        }
        var editInput by remember(initialPrompt) { mutableStateOf(initialPrompt) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.chat_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it },
                    label = { Text(stringResource(R.string.chat_edit_prompt_label)) },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editInput.isNotBlank()) {
                            onEditUserMessage(editInput)
                            showEditDialog = false
                        }
                    },
                    enabled = editInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.chat_edit_save_regenerate))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        onBranchToNewChat()
                        showEditDialog = false
                    }) {
                        Text(stringResource(R.string.chat_branch_to_new_chat))
                    }
                    TextButton(onClick = { showEditDialog = false }) {
                        Text(stringResource(R.string.chat_action_cancel))
                    }
                }
            },
        )
    }

    if (previewFile != null) {
        val target = previewFile!!
        var contentText by remember(target.path) { mutableStateOf<String?>(null) }
        LaunchedEffect(target.path) {
            contentText = withContext(Dispatchers.IO) {
                try {
                    if (target.path.isNotBlank()) {
                        val f = File(target.path)
                        if (f.exists()) {
                            f.readText().take(50_000)
                        } else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }
        AlertDialog(
            onDismissRequest = { previewFile = null },
            title = {
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = contentText ?: stringResource(R.string.chat_attachment_loading_or_binary),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewFile = null }) {
                    Text(stringResource(R.string.chat_action_close))
                }
            },
        )
    }
}

/**
 * Maps persona icon type string to Material icon vector.
 */
internal fun getIconVector(iconType: String): ImageVector =
    when (iconType) {
        "assistant" -> Icons.Default.Person
        "coder" -> Icons.Default.Code
        "writer" -> Icons.Default.Description
        "translator" -> Icons.Default.Translate
        "reasoning" -> Icons.Default.Psychology
        "speed" -> Icons.Default.Speed
        "shell" -> Icons.Default.Terminal
        "general" -> Icons.Default.AutoAwesome
        else -> Icons.Default.AutoAwesome
    }

/**
 * Formats file size in human-readable format.
 */
internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * Clean Material 3 Audio Player Card for native audio multimodal voice notes.
 */
@Composable
fun AudioMessagePlayer(
    audioPath: String,
    audioPlayerHelper: AudioPlayerHelper?,
    isUser: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val currentlyPlaying by audioPlayerHelper?.currentlyPlayingPath?.collectAsState() ?: remember { mutableStateOf(null) }
    val isPlaying = currentlyPlaying == audioPath

    // Metadata read stays off the main thread (disk I/O can block on
    // slow storage). Shows a placeholder until resolved.
    val durationText by produceState(initialValue = "Audio", audioPath) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    mmr.setDataSource(audioPath)
                    val durMs = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    mmr.release()
                    val totalSec = durMs / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    "%d:%02d".format(Locale.US, min, sec)
                } catch (_: Throwable) {
                    "Audio"
                }
            }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isUser) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp),
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            audioPlayerHelper?.stop()
                        } else {
                            audioPlayerHelper?.play(audioPath)
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.chat_pause) else stringResource(R.string.chat_play),
                        tint = if (isPlaying) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = if (isPlaying) "Playing voice note…" else "Voice Prompt",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "$durationText · 16kHz PCM WAV",
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Circular character profile photo. Returns false when [avatarUri] is blank.
 * Supports remote HTTP(S) URLs, absolute local file paths, and content URIs.
 */
@Composable
fun PersonaAvatar(
    avatarUri: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
): Boolean {
    if (avatarUri.isBlank()) return false
    val imageModel: Any = when {
        avatarUri.startsWith("http://") || avatarUri.startsWith("https://") ->
            com.raikaai.app.data.RoleplayRepository.toThumbnailUrl(avatarUri)
        avatarUri.startsWith("content://") || avatarUri.startsWith("file://") -> android.net.Uri.parse(avatarUri)
        else -> File(avatarUri).takeIf { it.exists() } ?: avatarUri
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageModel)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
    return true
}

/**
 * Fullscreen Image Viewer with Save to Pictures (Gallery) and Share via FileProvider.
 */
@Composable
fun ImageViewerDialog(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    if (imagePath.isBlank()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    // Cheap handle for the save/share actions below (no I/O here).
    val file = remember(imagePath) { File(imagePath) }

    // Decode off the main thread, downsampled to screen size (was:
    // main-thread full-res decode in remember — ANR/OOM on large photos).
    // No manual recycle: the bitmap is referenced by the Image below.
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    val f = File(imagePath)
                    if (f.exists()) decodeSampledBitmapFromFile(f, reqSizePx = 2048) else null
                } catch (_: Throwable) {
                    null
                }
            }
    }
    val currentBitmap = bitmap

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.95f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Image Container centered
                val viewerBitmap = currentBitmap
                if (viewerBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = viewerBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.chat_full_image_preview),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.chat_image_load_failed), color = Color.White)
                    }
                }

                // Top Bar with Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.chat_action_close),
                            tint = Color.White,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Share Button
                        IconButton(
                            onClick = {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        val ext = file.extension.lowercase(Locale.ROOT)
                                        type = if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.chat_share_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.chat_share),
                                tint = Color.White,
                            )
                        }

                        // Save to Pictures Gallery Button
                        IconButton(
                            onClick = {
                                if (isSaving) return@IconButton
                                isSaving = true
                                scope.launch {
                                    val saved = FileSaver.saveImageToPictures(context, file)
                                    isSaving = false
                                    if (saved != null) {
                                        Toast.makeText(context, context.getString(R.string.chat_saved_pictures, saved), Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.chat_save_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = stringResource(R.string.chat_save_to_pictures),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
