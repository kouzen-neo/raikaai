package com.raikaai.app.ui.chat

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.focusRequester
import com.raikaai.app.R

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Translate
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
                Icons.AutoMirrored.Outlined.NavigateBefore,
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
                Icons.AutoMirrored.Outlined.NavigateNext,
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
                    Icons.AutoMirrored.Outlined.CallSplit,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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
    /** Room for the composer, which floats over the list. */
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    /** Chat bubbles with the character's photo instead of flat replies. */
    bubbles: Boolean = false,
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
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Bottom of the reversed list: shows that Raika is still working,
            // also between agent steps when no new text is arriving.
            if (isGenerating) {
                item(key = "working_indicator", contentType = "working") {
                    WorkingIndicator(
                        lastContent = viewModel.messages.lastOrNull()?.content.orEmpty(),
                        bubbles = bubbles,
                        onStop = { viewModel.stopGeneration() },
                    )
                }
            }
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
                    bubbles = bubbles,
                    translation = msg.id?.let { viewModel.translations[it] },
                    onTranslate = { viewModel.toggleTranslation(msg) },
                )
            }
        }

        // Floating Jump to Bottom action button when user scrolls up
        AnimatedVisibility(
            visible = isScrolledUp,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp + bottomPadding),
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
                        Icons.Outlined.KeyboardArrowDown,
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
    bubbles: Boolean = false,
    translation: ChatViewModel.MessageTranslation? = null,
    onTranslate: (() -> Unit)? = null,
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

        // Pictures the assistant generated: a clean grid, no prompt text.
        if (!isUser && hasAttachmentImages) {
            val generated = remember(message.attachments) {
                message.attachments.orEmpty().filter { it.kind == AttachmentType.IMAGE }.map { it.path }
            }
            GeneratedImageGrid(paths = generated, onPreviewImage = onPreviewImage)
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
                        shape = if (bubbles) RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp) else RoundedCornerShape(16.dp),
                        color = if (bubbles) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            color = if (bubbles) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            // Edit via long-press on the bubble or card
        } else {
          AssistantFrame(bubbles = bubbles, persona = activePersona) {
            // Harness-style: the search step is shown BEFORE the answer, and
            // inline [n] citations in the answer open the matching source.
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
            val citationUrls = remember(webCitations) { webCitations.associate { it.index to it.url } }
            androidx.compose.runtime.CompositionLocalProvider(
                com.raikaai.app.ui.component.LocalCitationUrls provides citationUrls,
            ) {
                val reply: @Composable (Modifier) -> Unit = { mod ->
                    MarkdownText(
                        // Hide "[ran `cmd`: exit 0]" lines a model wrote itself: no command ran.
                        markdown = translation?.takeIf { it.shown && it.text.isNotBlank() }?.text
                            ?: message.content.replace(FAKE_RAN_LINE, "").ifBlank { "…" },
                        enableThinking = enableThinking,
                        onPreviewHtml = onPreviewHtml,
                        modifier = mod,
                    )
                }
                val imageOnly = hasAttachmentImages && message.content.isBlank() &&
                    translation?.takeIf { it.shown }?.text.isNullOrBlank()
                if (imageOnly) {
                    // Pictures only: the grid above is the whole reply.
                } else if (bubbles) {
                    Surface(
                        shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        reply(Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                } else {
                    reply(Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                }
            }

            if (translation != null) {
                Text(
                    text = when {
                        translation.loading -> translation.note ?: "Translating to ${translation.language}…"
                        translation.error != null -> "Couldn't translate: ${translation.error}"
                        translation.shown -> "Translated to ${translation.language} · tap the translate button for the original"
                        else -> "Original · tap the translate button for ${translation.language}"
                    },
                    fontSize = 11.sp,
                    color = if (translation.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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

            // A reply that stopped after thinking: say so plainly and offer a real Retry button.
            val stoppedEarly = message.stats?.let { it.contains("thinking only") || it.contains("incomplete") || it.contains("provider limit") } == true
            if (isLastMessage && !isGenerating && stoppedEarly) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when {
                                message.stats?.contains("thinking only") == true -> "The model stopped after thinking and gave no answer."
                                message.stats?.contains("provider limit") == true -> "The provider cut the reply at its length limit."
                                else -> "The reply stopped early (connection ended before it finished)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.FilledTonalButton(onClick = onRegenerate) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            // Footer Row: Telemetry Stats + Swipes Pagination + Quick Actions.
            // Hidden for greeting messages (no stats, single variant, not regenerable).
            if (!message.stats.isNullOrBlank() || message.variants.size > 1 || (isLastMessage && !isGenerating) || (messageIndex == 0 && onTranslate != null)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Stats take the space left over and ellipsize, so the action
                // buttons always keep their full size.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (!message.stats.isNullOrBlank()) {
                        Text(
                            text = message.stats,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
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
                                    Icons.AutoMirrored.Outlined.NavigateBefore,
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
                                    Icons.AutoMirrored.Outlined.NavigateNext,
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
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy_message),
                            modifier = Modifier.size(16.dp),
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Translate (display only) into the language from Settings
                    if (onTranslate != null) {
                        IconButton(onClick = onTranslate, enabled = translation?.loading != true, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Translate,
                                contentDescription = "Translate",
                                modifier = Modifier.size(16.dp),
                                tint = if (translation?.shown == true && translation.text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = if (isSpeaking) stringResource(R.string.chat_stop_reading) else stringResource(R.string.chat_read_aloud),
                            modifier = Modifier.size(17.dp),
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
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.chat_share_message),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Branch in New Chat Action
                    IconButton(
                        onClick = onBranchToNewChat,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.CallSplit,
                            contentDescription = stringResource(R.string.chat_branch_to_new_chat),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Regenerate Action (only on last assistant message when not generating)
                    if (isLastMessage && !isGenerating) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.chat_regenerate),
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
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
        EditMessageSheet(
            text = editInput,
            onTextChange = { editInput = it },
            onDismiss = { showEditDialog = false },
            onSend = {
                if (editInput.isNotBlank()) {
                    onEditUserMessage(editInput)
                    showEditDialog = false
                }
            },
            onBranch = {
                onBranchToNewChat()
                showEditDialog = false
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
 * Lays an assistant reply out flat (classic) or next to the character's photo
 * (bubbles). The reply itself is the same in both.
 */
@Composable
private fun AssistantFrame(
    bubbles: Boolean,
    persona: PersonaPreset,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!bubbles) {
        Column(Modifier.fillMaxWidth(), content = content)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatAvatar(persona, 30.dp, Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f), content = content)
    }
}

/** Round photo of the character, the app logo for Raika, or the persona icon. */
@Composable
internal fun ChatAvatar(persona: PersonaPreset, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val shown = PersonaAvatar(persona.avatarUri, size, contentDescription = persona.name)
        if (!shown) {
            if (persona.id == "general") {
                com.raikaai.app.ui.component.RaikaLogo(size, shape = CircleShape)
            } else {
                Icon(
                    getIconVector(persona.iconType),
                    contentDescription = persona.name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

/**
 * Maps persona icon type string to Material icon vector.
 */
internal fun getIconVector(iconType: String): ImageVector =
    when (iconType) {
        "assistant" -> Icons.Outlined.Person
        "coder" -> Icons.Outlined.Code
        "writer" -> Icons.Outlined.Description
        "translator" -> Icons.Outlined.Translate
        "reasoning" -> Icons.Outlined.Psychology
        "speed" -> Icons.Outlined.Speed
        "shell" -> Icons.Outlined.Terminal
        "general" -> Icons.Outlined.AutoAwesome
        else -> Icons.Outlined.AutoAwesome
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
                        imageVector = Icons.Outlined.GraphicEq,
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
                            Icons.Outlined.Close,
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


/** Edit a sent message: a sheet with the text and two clear actions. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditMessageSheet(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onBranch: () -> Unit,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.chat_action_cancel))
                }
                Text(
                    stringResource(R.string.chat_edit_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
                )
            }
            Text(
                "Sending creates a new branch; the earlier reply stays available with the < > arrows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onBranch,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New chat")
                }
                Button(
                    onClick = onSend,
                    enabled = text.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send")
                }
            }
        }
    }
}


/** Spinner, what Raika is doing, and how long it has been going. */
@Composable
private fun WorkingIndicator(lastContent: String, bubbles: Boolean = false, onStop: () -> Unit = {}) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            seconds++
        }
    }
    val tail = lastContent.takeLast(400)
    val label =
        when {
            lastContent.isBlank() -> "Thinking…"
            tail.contains("\npending\n") || tail.contains("Requesting approval") -> "Running command…"
            tail.contains("Running `") || tail.contains("Searching the web") -> "Using a tool…"
            tail.contains("<think>") && !tail.contains("</think>") -> "Thinking…"
            else -> "Writing…"
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (seconds > 0) "$label ${seconds}s" else label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bubbles) {
            Spacer(Modifier.width(10.dp))
            Surface(
                onClick = onStop,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Generated pictures: one fills the width, several sit in a two-column grid
 * of square tiles. Tapping a picture opens the full-size preview.
 */
@Composable
private fun GeneratedImageGrid(paths: List<String>, onPreviewImage: (String) -> Unit) {
    if (paths.isEmpty()) return
    @Composable
    fun tile(path: String, modifier: Modifier, crop: Boolean) {
        val bitmap by produceState<Bitmap?>(initialValue = null, path) {
            value = withContext(Dispatchers.IO) { decodeSampledBitmapFromFile(File(path), reqSizePx = 1024) }
        }
        val current = bitmap
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onPreviewImage(path) },
        ) {
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = stringResource(R.string.chat_attached_image),
                    modifier = if (crop) Modifier.matchParentSize() else Modifier.fillMaxWidth(),
                    contentScale = if (crop) ContentScale.Crop else ContentScale.FillWidth,
                )
            }
        }
    }
    if (paths.size == 1) {
        tile(paths.first(), Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(bottom = 6.dp), crop = false)
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        paths.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile(it, Modifier.weight(1f).aspectRatio(1f), crop = true) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
