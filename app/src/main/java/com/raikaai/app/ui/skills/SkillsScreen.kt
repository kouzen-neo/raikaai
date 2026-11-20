package com.raikaai.app.ui.skills

import com.raikaai.app.ui.component.ScreenHeader
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.*
import com.raikaai.app.skills.toMarkdown
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.skills.Skill
import com.raikaai.app.skills.SkillsManager
import com.raikaai.app.ui.chat.ChatViewModel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Dedicated Skills Hub Screen cleanly styled with Material 3 uniform cards,
 * matching SamplerScreen and ModelsScreen aesthetics.
 */
@Composable
fun SkillsScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    embeddedTab: Int? = null,
    createRequests: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val skills by viewModel.skills.collectAsState()

    var selectedTabState by remember { mutableIntStateOf(0) }
    // Inside the Agent hub the hub owns the header and tabs.
    val selectedTab = embeddedTab ?: selectedTabState
    var showCreateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(createRequests) { if (createRequests > 0) showCreateDialog = true }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var deleteCandidate by remember { mutableStateOf<Skill?>(null) }
    var pendingImportReview by remember { mutableStateOf<Skill?>(null) }
    var viewingSkill by remember { mutableStateOf<Skill?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = SkillsManager.getInstance(context).importFromUri(uri)
                if (imported != null) {
                    // Imported skills stay disabled until the user reviews the
                    // instructions and explicitly enables them (B9).
                    pendingImportReview = imported
                } else {
                    Toast.makeText(context, "Failed to import skill file", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            if (embeddedTab == null) {
            // ── 0. Top Header ──
            ScreenHeader(
                title = "Skills & Plugins",
                subtitle = "Plugins add abilities · Skills add instructions",
                onOpenDrawer = onOpenDrawer,
            ) {
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Create Skill",
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
                    onClick = { selectedTabState = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Plugins", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTabState = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Skills (${skills.count { it.isEnabled }}/${skills.size})", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }

            }

            // ── 2. Content Views ──
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {

            if (selectedTab == 0) {
                // ── Group 1: Knowledge & Retrieval ──
                item(key = "group_knowledge") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "KNOWLEDGE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))

                            SkillItemRow(
                                title = "Web Search",
                                description = "Searches the web, reads the top pages, and answers to the point with tappable [1] citations.",
                                icon = Icons.Outlined.TravelExplore,
                                isEnabled = settings.enableWebSearch,
                                onToggle = { viewModel.setEnableWebSearch(it) },
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )

                            SkillItemRow(
                                title = "Documents",
                                description = "Answers from files you add, fully on-device. Cites them as [Doc1].",
                                icon = Icons.Outlined.FolderOpen,
                                isEnabled = settings.ragEnabled,
                                onToggle = { viewModel.setRagEnabled(it) },
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )

                            SkillItemRow(
                                title = "Chat Memory",
                                description = "Remembers older turns of long chats and recalls them when relevant.",
                                icon = Icons.Outlined.History,
                                isEnabled = settings.useChatMemory,
                                onToggle = { viewModel.setUseChatMemory(it) },
                            )
                        }
                    }
                }

                // ── Group 2: Execution & Context ──
                item(key = "group_exec") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "CODE & CONTEXT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))

                            SkillItemRow(
                                title = "Code Artifacts",
                                description = "Collects code from replies into projects you can save, export, and preview as web pages.",
                                icon = Icons.Outlined.DataObject,
                                isEnabled = settings.captureArtifacts,
                                onToggle = { viewModel.setCaptureArtifacts(it) },
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )

                            SkillItemRow(
                                title = "Auto-Compact",
                                description = "Summarizes earlier turns before the context fills up, so long chats keep working.",
                                icon = Icons.Outlined.Compress,
                                isEnabled = settings.autoCompress,
                                onToggle = { viewModel.setAutoCompress(it) },
                            )
                        }
                    }
                }

                // ── Group 3: Reasoning & Multimodal ──
                item(key = "group_reasoning") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "MODEL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))

                            SkillItemRow(
                                title = "Thinking",
                                description = "Lets reasoning models think first. Thoughts show as collapsible cards.",
                                icon = Icons.Outlined.Psychology,
                                isEnabled = settings.enableThinking,
                                onToggle = { viewModel.setEnableThinking(it) },
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )

                            SkillItemRow(
                                title = "Vision",
                                description = "Photo and camera input for models that can see (e.g. Gemma 4 E2B/E4B).",
                                icon = Icons.Outlined.Visibility,
                                isEnabled = settings.enableVision,
                                onToggle = { viewModel.setEnableVision(it) },
                            )
                        }
                    }
                }
            } else {
                // ── Skills: Markdown instructions added to the prompt ──
                item(key = "custom_actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Skills are short Markdown instructions added to the prompt while on. Tap one to read its SKILL.md. Toggle from chat with /skills <name>.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New skill")
                            }
                            OutlinedButton(
                                onClick = { filePicker.launch("*/*") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import .md")
                            }
                        }
                    }
                }

                // Preset Prompt Skills
                val presets = skills.filter { it.isBuiltIn }
                if (presets.isNotEmpty()) {
                    item(key = "header_presets") {
                        Text(
                            "BUILT-IN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(presets, key = { it.id }) { skill ->
                        PromptSkillCard(
                            skill = skill,
                            onOpen = { viewingSkill = skill },
                            onToggle = { isEnabled ->
                                scope.launch { SkillsManager.getInstance(context).toggleSkill(skill.id, isEnabled) }
                            },
                            onEdit = { editingSkill = skill },
                            onDelete = { deleteCandidate = skill },
                            onExport = {
                                scope.launch {
                                    val exported = SkillsManager.getInstance(context).exportSkill(skill)
                                    if (exported != null) {
                                        Toast.makeText(context, "Exported to Downloads/RaikaAI/Skills/${exported.name}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to export skill", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                }

                // User Created Skills
                val customOnly = skills.filterNot { it.isBuiltIn }
                if (customOnly.isNotEmpty()) {
                    item(key = "header_custom") {
                        Text(
                            "YOURS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    items(customOnly, key = { it.id }) { skill ->
                        PromptSkillCard(
                            skill = skill,
                            onOpen = { viewingSkill = skill },
                            onToggle = { isEnabled ->
                                scope.launch { SkillsManager.getInstance(context).toggleSkill(skill.id, isEnabled) }
                            },
                            onEdit = { editingSkill = skill },
                            onDelete = { deleteCandidate = skill },
                            onExport = {
                                scope.launch {
                                    val exported = SkillsManager.getInstance(context).exportSkill(skill)
                                    if (exported != null) {
                                        Toast.makeText(context, "Exported to Downloads/RaikaAI/Skills/${exported.name}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to export skill", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

    // ── Create / Edit Skill Dialog ──
    if (showCreateDialog || editingSkill != null) {
        val target = editingSkill
        SkillFormDialog(
            initialSkill = target,
            onDismiss = {
                showCreateDialog = false
                editingSkill = null
            },
            onSave = { name, desc, category, icon, instructions ->
                scope.launch {
                    val newSkill = Skill(
                        id = target?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        description = desc,
                        category = category,
                        iconCategory = icon,
                        instructions = instructions,
                        isEnabled = target?.isEnabled ?: false,
                        isBuiltIn = target?.isBuiltIn ?: false,
                    )
                    SkillsManager.getInstance(context).saveSkill(newSkill)
                    Toast.makeText(context, "Skill saved successfully", Toast.LENGTH_SHORT).show()
                }
                showCreateDialog = false
                editingSkill = null
            },
        )
    }

    // ── Delete Confirmation Dialog ──
    deleteCandidate?.let { skill ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Skill") },
            text = { Text("Are you sure you want to delete '${skill.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { SkillsManager.getInstance(context).deleteSkill(skill.id) }
                        deleteCandidate = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── SKILL.md viewer ──
    viewingSkill?.let { shown ->
        // Read the live copy so the switch reflects toggles made here.
        val skill = skills.firstOrNull { it.id == shown.id } ?: shown
        SkillDetailSheet(
            skill = skill,
            onDismiss = { viewingSkill = null },
            onToggle = { on -> scope.launch { SkillsManager.getInstance(context).toggleSkill(skill.id, on) } },
            onEdit = { viewingSkill = null; editingSkill = skill },
            onDelete = if (skill.isBuiltIn) null else ({ viewingSkill = null; deleteCandidate = skill }),
            onExport = {
                scope.launch {
                    val exported = SkillsManager.getInstance(context).exportSkill(skill)
                    Toast.makeText(
                        context,
                        if (exported != null) "Saved to Downloads/RaikaAI/Skills/${exported.name}" else "Failed to export skill",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    // ── Import Review Dialog (B9): preview instructions before enabling ──
    pendingImportReview?.let { skill ->
        SkillImportReviewDialog(
            skill = skill,
            onEnable = {
                scope.launch { SkillsManager.getInstance(context).toggleSkill(skill.id, true) }
                pendingImportReview = null
            },
            onDismiss = { pendingImportReview = null },
        )
    }
}

@Composable
private fun SkillItemRow(
    title: String,
    description: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, active = isEnabled)

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }

        Spacer(Modifier.width(10.dp))

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun PromptSkillCard(
    skill: Skill,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val iconVector = skillIcon(skill.iconCategory)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(iconVector, active = skill.isEnabled)

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (skill.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 3.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = skill.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (skill.isBuiltIn) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    text = "Built-in",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Switch(
                    checked = skill.isEnabled,
                    onCheckedChange = onToggle,
                )

                if (!skill.isBuiltIn) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export to Markdown") },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onExport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Skill") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "Export",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun SkillFormDialog(
    initialSkill: Skill?,
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, category: String, icon: String, instructions: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialSkill?.name.orEmpty()) }
    var desc by remember { mutableStateOf(initialSkill?.description.orEmpty()) }
    var category by remember { mutableStateOf(initialSkill?.category ?: "Coding") }
    var iconCategory by remember { mutableStateOf(initialSkill?.iconCategory ?: "code") }
    var instructions by remember { mutableStateOf(initialSkill?.instructions.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSkill != null) "Edit Skill" else "Create Custom Skill") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Skill Name") },
                    placeholder = { Text("e.g. Kotlin Jetpack Architect") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Short Description") },
                    placeholder = { Text("What this skill specializes in...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        placeholder = { Text("Coding / Writing") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )

                }

                Text("Icon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SKILL_ICON_KEYS.size) { i ->
                        val key = SKILL_ICON_KEYS[i]
                        val selected = iconCategory.equals(key, ignoreCase = true)
                        Surface(
                            onClick = { iconCategory = key },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    skillIcon(key),
                                    contentDescription = key,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("System Instructions & Directives") },
                    placeholder = { Text("Write detailed instructions on how the AI should format answers, guidelines, and expertise...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), desc.trim(), category.trim(), iconCategory.trim(), instructions.trim()) },
                enabled = name.isNotBlank() && instructions.isNotBlank(),
            ) {
                Text("Save Skill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** Bottom sheet that shows a skill's SKILL.md, rendered or as source. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(
    skill: Skill,
    onDismiss: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onExport: () -> Unit,
) {
    var showSource by remember(skill.id) { mutableStateOf(false) }
    val markdown = remember(skill) { skill.toMarkdown() }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(skillIcon(skill.iconCategory), active = skill.isEnabled)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(skill.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${skill.category}${if (skill.isBuiltIn) " · Built-in" else ""} · SKILL.md",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = skill.isEnabled, onCheckedChange = onToggle)
            }
            if (skill.description.isNotBlank()) {
                Text(skill.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = !showSource,
                    onClick = { showSource = false },
                    label = { Text("Preview") },
                )
                androidx.compose.material3.FilterChip(
                    selected = showSource,
                    onClick = { showSource = true },
                    label = { Text("Source") },
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(14.dp),
                ) {
                    if (showSource) {
                        Text(
                            markdown,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    } else {
                        com.raikaai.app.ui.component.MarkdownText(
                            markdown = skill.instructions.ifBlank { "_No instructions._" },
                            enableThinking = false,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Icon keys a skill can use (stored as Skill.iconCategory). */
private val SKILL_ICON_KEYS =
    listOf(
        "code", "terminal", "write", "short", "translate", "math", "school", "search",
        "palette", "theater", "book", "chat", "science", "work", "music", "health", "sparkles",
    )

/** Material icon for a skill icon key; unknown keys fall back to a neutral one. */
private fun skillIcon(key: String): ImageVector =
    when (key.lowercase()) {
        "code" -> Icons.Outlined.Code
        "terminal" -> Icons.Outlined.Terminal
        "write" -> Icons.Outlined.EditNote
        "short" -> Icons.AutoMirrored.Outlined.ShortText
        "translate" -> Icons.Outlined.Translate
        "math" -> Icons.Outlined.Functions
        "school" -> Icons.Outlined.School
        "search" -> Icons.Outlined.ManageSearch
        "palette" -> Icons.Outlined.Palette
        "theater" -> Icons.Outlined.TheaterComedy
        "book" -> Icons.Outlined.AutoStories
        "chat" -> Icons.Outlined.Forum
        "science" -> Icons.Outlined.Science
        "work" -> Icons.Outlined.WorkOutline
        "music" -> Icons.Outlined.MusicNote
        "health" -> Icons.Outlined.FavoriteBorder
        "sparkles" -> Icons.Outlined.AutoAwesome
        else -> Icons.Outlined.Extension
    }

/** Material You tonal icon tile: primary container when active. */
@Composable
private fun IconTile(
    icon: ImageVector,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
