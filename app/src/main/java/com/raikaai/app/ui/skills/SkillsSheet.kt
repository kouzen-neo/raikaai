package com.raikaai.app.ui.skills

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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

/**
 * Modular Skills Bottom Sheet matching SamplerScreen card layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsSheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onOpenCreateDialog: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by viewModel.settings.collectAsState()
    val skills by viewModel.skills.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingImportReview by remember { mutableStateOf<Skill?>(null) }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── Top Title ──
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    "Skills",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "On-device capabilities and custom prompt specializations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ── Mode Switcher Card ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        Pair(0, "System Tools (7)"),
                        Pair(1, "Custom Skills (${skills.count { it.isEnabled }})"),
                    ).forEach { (idx, label) ->
                        val isSelected = selectedTab == idx
                        Surface(
                            onClick = { selectedTab = idx },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Group 1: Knowledge & Retrieval
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "KNOWLEDGE & RETRIEVAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(10.dp))

                                SheetSkillItemRow(
                                    title = "Live Web Search (Internet)",
                                    description = "Live on-device search (DuckDuckGo, Wikipedia, URLs).",
                                    icon = Icons.Outlined.Search,
                                    isEnabled = settings.enableWebSearch,
                                    onToggle = { viewModel.setEnableWebSearch(it) },
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )

                                SheetSkillItemRow(
                                    title = "Document Knowledge (RAG)",
                                    description = "BM25 retrieval from imported text/PDF files.",
                                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                                    isEnabled = settings.ragEnabled,
                                    onToggle = { viewModel.setRagEnabled(it) },
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )

                                SheetSkillItemRow(
                                    title = "Chat Memory (Long-term Recall)",
                                    description = "Searchable long-term context memory ([Mem1]).",
                                    icon = Icons.Outlined.Psychology,
                                    isEnabled = settings.useChatMemory,
                                    onToggle = { viewModel.setUseChatMemory(it) },
                                )
                            }
                        }
                    }

                    // Group 2: Code & Context
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "CODE & CONTEXT MANAGEMENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(10.dp))

                                SheetSkillItemRow(
                                    title = "Code Artifacts & HTML Preview",
                                    description = "Multi-file detection and instant in-app web preview.",
                                    icon = Icons.Outlined.Code,
                                    isEnabled = settings.captureArtifacts,
                                    onToggle = { viewModel.setCaptureArtifacts(it) },
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )

                                SheetSkillItemRow(
                                    title = "Context Auto-Compression",
                                    description = "Automatic rolling summary near token limits.",
                                    icon = Icons.Outlined.Compress,
                                    isEnabled = settings.autoCompress,
                                    onToggle = { viewModel.setAutoCompress(it) },
                                )
                            }
                        }
                    }

                    // Group 3: Reasoning & Multimodal
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "REASONING & MULTIMODAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(10.dp))

                                SheetSkillItemRow(
                                    title = "Deep Thinking & Reasoning",
                                    description = "Collapsible reasoning block (<think> tags).",
                                    icon = Icons.Outlined.Lightbulb,
                                    isEnabled = settings.enableThinking,
                                    onToggle = { viewModel.setEnableThinking(it) },
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )

                                SheetSkillItemRow(
                                    title = "Multimodal Vision Input",
                                    description = "Camera & photo understanding for vision models.",
                                    icon = Icons.Outlined.AddPhotoAlternate,
                                    isEnabled = settings.enableVision,
                                    onToggle = { viewModel.setEnableVision(it) },
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onOpenCreateDialog,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Skill")
                            }

                            OutlinedButton(
                                onClick = { filePicker.launch("*/*") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import")
                            }
                        }
                    }

                    items(skills, key = { it.id }) { skill ->
                        SheetPromptSkillCard(
                            skill = skill,
                            onToggle = { isEnabled ->
                                scope.launch { SkillsManager.getInstance(context).toggleSkill(skill.id, isEnabled) }
                            },
                        )
                    }
                }
            }
        }
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
private fun SheetSkillItemRow(
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun SheetPromptSkillCard(
    skill: Skill,
    onToggle: (Boolean) -> Unit,
) {
    val iconVector = when (skill.iconCategory.lowercase()) {
        "code" -> Icons.Outlined.Code
        "terminal" -> Icons.Outlined.Terminal
        "palette" -> Icons.Outlined.Palette
        "math" -> Icons.Outlined.Functions
        "write" -> Icons.Outlined.Edit
        "search" -> Icons.Outlined.Search
        else -> Icons.Outlined.AutoAwesome
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = if (skill.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (skill.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
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
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    if (skill.isBuiltIn) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "Preset",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = skill.isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}
