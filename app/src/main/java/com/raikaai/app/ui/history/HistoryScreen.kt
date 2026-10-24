package com.raikaai.app.ui.history

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.data.ConversationHeader
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.component.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HistorySortMode { TIME, NAME }

private fun stripThinkingProcess(raw: String): String {
    val clean =
        raw.replace(Regex("(?is)<(?:think|thought|thinking)(?:\\s+[^>]*)?>.*?</(?:think|thought|thinking)>"), "")
            .replace(Regex("(?is)<(?:think|thought|thinking)(?:\\s+[^>]*)?>.*"), "")
            .replace(Regex("(?is)<(?:tool_call|action)(?:\\s+[^>]*)?>.*?</(?:tool_call|action)>"), "")
            .replace(Regex("(?is)<(?:tool_call|action)(?:\\s+[^>]*)?>.*"), "")
            .trim()
    return clean.ifBlank { raw.trim() }
}

/**
 * Full-screen History view matching KZKT's design system:
 * - Large title header with Delete All action
 * - Pill-shaped search bar
 * - Filter & Sort chips (By Time, By Name)
 * - Grouped date headers (Today, Yesterday, Date)
 * - Conversation cards with icon, message count, timestamp, Rename, Delete, and Export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenConversation: () -> Unit = {},
) {
    val conversations = viewModel.conversations.value
    val activeConvId = viewModel.currentConversationId
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(HistorySortMode.TIME) }
    var sortDescending by remember { mutableStateOf(true) }

    var renameTarget by remember { mutableStateOf<ConversationHeader?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ConversationHeader?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var folderTarget by remember { mutableStateOf<ConversationHeader?>(null) }
    var folderInput by remember { mutableStateOf("") }
    var folderFilter by remember { mutableStateOf<String?>(null) } // null = all
    var tagTarget by remember { mutableStateOf<ConversationHeader?>(null) }
    var tagInput by remember { mutableStateOf("") } // comma-separated
    var tagFilter by remember { mutableStateOf<String?>(null) } // null = all

    var bodyMatchIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bodySearchQuery by remember { mutableStateOf("") }

    // Full-text body search runs debounced on demand; bodies are NOT parsed for the
    // default list view (P6). Title matches show instantly, body matches stream in.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            bodyMatchIds = emptySet()
            bodySearchQuery = ""
            return@LaunchedEffect
        }
        delay(350)
        val q = query
        bodyMatchIds = viewModel.searchConversationBodies(q)
        bodySearchQuery = q
    }

    val filteredList =
        remember(conversations, query, sortMode, sortDescending, bodyMatchIds, bodySearchQuery, folderFilter, tagFilter) {
            var list =
                if (query.isBlank()) {
                    conversations
                } else {
                    conversations.filter {
                        it.title.contains(query, ignoreCase = true) ||
                            (query == bodySearchQuery && bodyMatchIds.contains(it.id))
                    }
                }
            val ff = folderFilter
            if (ff != null) {
                list = list.filter { it.folder == ff }
            }
            val tf = tagFilter
            if (tf != null) {
                list = list.filter { it.tags.contains(tf) }
            }
            list =
                when (sortMode) {
                    HistorySortMode.TIME -> if (sortDescending) list.sortedByDescending { it.updatedAt } else list.sortedBy { it.updatedAt }
                    HistorySortMode.NAME -> if (sortDescending) list.sortedByDescending { it.title.lowercase() } else list.sortedBy { it.title.lowercase() }
                }
            list
        }

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = "Chat History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${conversations.size} Saved Conversations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (conversations.isNotEmpty()) {
                    IconButton(onClick = { showClearAllConfirm = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 1. Secondary Tabs Row ──
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Conversations (${conversations.size})", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Backup & Storage", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }

            // ── 2. Content Views ──
            // Hoisted: remember() is illegal inside LazyListScope.
            val folderNames = remember(conversations) {
                conversations.mapNotNull { it.folder.takeIf { f -> f.isNotBlank() } }.distinct().sorted()
            }
            val allTagNames = remember(conversations) {
                conversations.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
            }
            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    // Search & Filter Toolbar Card
                    item(key = "history_search_card") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = { Text("Search conversations…") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = { query = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        ),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val isTime = sortMode == HistorySortMode.TIME
                                    Surface(
                                        onClick = {
                                            if (sortMode == HistorySortMode.TIME) {
                                                sortDescending = !sortDescending
                                            } else {
                                                sortMode = HistorySortMode.TIME
                                                sortDescending = true
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isTime) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                    ) {
                                        Text(
                                            text = if (isTime && !sortDescending) "By Time (Oldest)" else "By Time (Newest)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isTime) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isTime) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }

                                    val isName = sortMode == HistorySortMode.NAME
                                    Surface(
                                        onClick = {
                                            if (sortMode == HistorySortMode.NAME) {
                                                sortDescending = !sortDescending
                                            } else {
                                                sortMode = HistorySortMode.NAME
                                                sortDescending = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isName) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                    ) {
                                        Text(
                                            text = if (isName && sortDescending) "By Name (Z-A)" else "By Name (A-Z)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isName) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isName) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Folder filter chips
                    if (folderNames.isNotEmpty()) {
                        item(key = "folder_filters") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                item(key = "folder_all") {
                                    FilterChip(
                                        selected = folderFilter == null,
                                        onClick = { folderFilter = null },
                                        label = { Text("All") },
                                    )
                                }
                                items(folderNames, key = { "folder_$it" }) { folder ->
                                    FilterChip(
                                        selected = folderFilter == folder,
                                        onClick = { folderFilter = if (folderFilter == folder) null else folder },
                                        label = { Text(folder) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Folder,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Tag filter chips
                    if (allTagNames.isNotEmpty()) {
                        item(key = "tag_filters") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                item(key = "tag_all") {
                                    FilterChip(
                                        selected = tagFilter == null,
                                        onClick = { tagFilter = null },
                                        label = { Text("All tags") },
                                    )
                                }
                                items(allTagNames, key = { "tag_$it" }) { tag ->
                                    FilterChip(
                                        selected = tagFilter == tag,
                                        onClick = { tagFilter = if (tagFilter == tag) null else tag },
                                        label = { Text(tag) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Tag,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (filteredList.isEmpty()) {
                        item(key = "empty_state") {
                            EmptyState(
                                icon = Icons.Default.History,
                                title = if (query.isBlank()) "No conversation history" else "No matching chats found",
                                message = if (query.isBlank()) "Start a new chat and it will show up here." else "Try a different search term or filter.",
                            )
                        }
                    } else {
                        items(filteredList, key = { it.id }) { conv ->
                            val isActive = conv.id == activeConvId
                            val dateFormatted = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(conv.updatedAt))
                            val rawLastMsg = conv.lastMessagePreview
                            val preview = stripThinkingProcess(rawLastMsg).take(80).replace("\n", " ").ifBlank { "No messages" }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isActive) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                ),
                        border =
                            if (isActive) {
                                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            } else {
                                null
                            },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.loadConversation(conv.id)
                                    onOpenConversation()
                                },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left Icon container
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )

                            Spacer(Modifier.width(12.dp))

                            // Middle Info
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = conv.title.ifBlank { "Untitled Chat" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (conv.hasPinnedModel) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Pinned model",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                                if (conv.folder.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            conv.folder,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            maxLines = 1,
                                        )
                                    }
                                }
                                if (conv.tags.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        conv.tags.take(3).forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                            ) {
                                                Text(
                                                    "#$tag",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        if (conv.tags.size > 3) {
                                            Text(
                                                "+${conv.tags.size - 3}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "$dateFormatted · ${conv.messageCount} msgs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }

                            // Actions
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        folderTarget = conv
                                        folderInput = conv.folder
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = "Move to folder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        tagTarget = conv
                                        tagInput = conv.tags.joinToString(", ")
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Tag,
                                        contentDescription = "Edit tags",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val md = viewModel.exportChatMarkdownById(conv.id)
                                            val sendIntent =
                                                Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, md)
                                                    type = "text/plain"
                                                }
                                            context.startActivity(Intent.createChooser(sendIntent, "Export Conversation"))
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Export",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        renameTarget = conv
                                        renameTitle = conv.title
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                IconButton(
                                    onClick = { deleteTarget = conv },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // ── Tab 1: Backup & Storage ──
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Backup & Export Card
            item(key = "backup_card") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            Text(
                                "Backup & Export",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Export all conversations to Markdown or JSON for safekeeping",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val allText = viewModel.exportAllChatsMarkdown()
                                    val sendIntent =
                                        Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, allText)
                                            type = "text/plain"
                                        }
                                context.startActivity(Intent.createChooser(sendIntent, "Export All Conversations"))
                                }
                            },
                            enabled = conversations.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export All Conversations (${conversations.size})")
                        }
                    }
                }
            }

            // Storage & Data Management Card
            item(key = "storage_management_card") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            Text(
                                "Data Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Clear cached history or delete all stored local conversations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        val totalMsgs = conversations.sumOf { it.messageCount }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Total Saved Messages", style = MaterialTheme.typography.bodyMedium)
                                Text("$totalMsgs messages", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { showClearAllConfirm = true },
                            enabled = conversations.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear All Conversation History")
                        }
                    }
                }
            }
        }
    }
}
}

    // ── Dialogs ──
    if (folderTarget != null) {
        val existingFolders = remember(conversations) {
            conversations.mapNotNull { it.folder.takeIf { f -> f.isNotBlank() } }.distinct().sorted()
        }
        AlertDialog(
            onDismissRequest = { folderTarget = null },
            icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Move to Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        label = { Text("Folder name (empty = no folder)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (existingFolders.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(existingFolders, key = { "pick_$it" }) { folder ->
                                FilterChip(
                                    selected = folderInput == folder,
                                    onClick = { folderInput = folder },
                                    label = { Text(folder, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        folderTarget?.let { viewModel.setConversationFolder(it.id, folderInput) }
                        folderTarget = null
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (tagTarget != null) {
        val existingTags = remember(conversations) {
            conversations.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val currentTags = remember(tagInput) {
            tagInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        }
        AlertDialog(
            onDismissRequest = { tagTarget = null },
            icon = { Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Edit Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Tags, comma-separated") },
                        placeholder = { Text("work, idea, todo") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (existingTags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(existingTags, key = { "tagpick_$it" }) { tag ->
                                val selected = currentTags.contains(tag)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        tagInput =
                                            if (selected) {
                                                currentTags.filter { it != tag }.joinToString(", ")
                                            } else {
                                                (currentTags + tag).joinToString(", ")
                                            }
                                    },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                    Text(
                        "A chat can carry several tags — unlike folders, which hold one.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        tagTarget?.let { viewModel.setConversationTags(it.id, currentTags) }
                        tagTarget = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        renameTarget?.let { viewModel.renameConversation(it.id, renameTitle) }
                        renameTarget = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Conversation?") },
            text = { Text("This will permanently delete '${deleteTarget?.title}'.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget?.let { viewModel.deleteConversation(it.id) }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All History?") },
            text = { Text("This will delete all saved conversation transcripts. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllConversations()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
