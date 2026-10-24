package com.raikaai.app.ui.characters
import com.raikaai.app.data.ChatConstants

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.chat.PERSONA_PRESETS
import com.raikaai.app.ui.chat.PersonaPreset
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ChatterUI-inspired Character & Persona Hub.
 * Manages Character Cards v2, Greetings, Custom Sampler Overrides, and JSON Import/Export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onStartChatWithCharacter: (PersonaPreset) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val activePersona by viewModel.activePersona.collectAsState()
    val customPersonas = viewModel.customPersonas

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var editingPersona by remember { mutableStateOf<PersonaPreset?>(null) }

    // Quick inline form state for Create Tab
    var newName by remember { mutableStateOf("") }
    var newIconType by remember { mutableStateOf("robot") }
    var newGreeting by remember { mutableStateOf("") }
    var newPrompt by remember { mutableStateOf("") }
    var newPersonality by remember { mutableStateOf("") }
    var newScenario by remember { mutableStateOf("") }
    var newExamples by remember { mutableStateOf("") }
    var newTemp by remember { mutableFloatStateOf(0.7f) }
    var newTopK by remember { mutableFloatStateOf(40f) }
    var newAvatarPath by remember { mutableStateOf("") }
    var isSavingNewAvatar by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    val screenScope = rememberCoroutineScope()
    val newAvatarPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                isSavingNewAvatar = true
                screenScope.launch {
                    newAvatarPath = viewModel.savePersonaAvatar(uri).orEmpty()
                    isSavingNewAvatar = false
                }
            }
        }

    val allPersonas = remember(customPersonas.toList()) {
        PERSONA_PRESETS + customPersonas.toList()
    }

    val filteredPersonas = remember(searchQuery, selectedFilter, allPersonas) {
        var list = allPersonas
        if (selectedFilter == "Custom") {
            list = list.filter { p -> customPersonas.any { it.id == p.id } }
        } else if (selectedFilter == "Presets") {
            list = list.filter { p -> PERSONA_PRESETS.any { it.id == p.id } }
        } else if (selectedFilter == "Active") {
            list = list.filter { it.id == activePersona.id }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            list = list.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                    it.systemPrompt.lowercase(Locale.ROOT).contains(q) ||
                    it.greeting.lowercase(Locale.ROOT).contains(q)
            }
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
            // ── 0. Top Header Row ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Characters",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${allPersonas.size} Personas & System Prompts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Personas (${allPersonas.size})", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create & Import", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }

            // ── 2. Content Views ──
            if (selectedTab == 0) {
                // Tab 0: Personas List with Search & Filter
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Search & Filter Card
                    item(key = "search_card") {
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
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search personas or system prompts…") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    listOf("All", "Active", "Custom", "Presets").forEach { filter ->
                                        val isSel = selectedFilter == filter
                                        Surface(
                                            onClick = { selectedFilter = filter },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        ) {
                                            Text(
                                                text = filter,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Persona Cards
                    items(items = filteredPersonas, key = { it.id }) { persona ->
                        val isActive = activePersona.id == persona.id
                        val isCustom = customPersonas.any { it.id == persona.id }

                        CharacterCardItem(
                            persona = persona,
                            isActive = isActive,
                            isCustom = isCustom,
                            onSelect = {
                                viewModel.applyPersona(persona)
                                viewModel.newChat(withPersona = persona)
                                onStartChatWithCharacter(persona)
                            },
                            onEdit = { editingPersona = persona },
                            onDelete = { viewModel.deleteCustomPersona(persona.id) },
                            onExportJson = {
                                val json = viewModel.exportPersonaJson(persona)
                                clipboard.setText(AnnotatedString(json))
                                Toast.makeText(context, "Copied ${persona.name} Character JSON to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            } else {
                // Tab 1: Create & Import
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Create Custom Persona Card
                    item(key = "create_persona_card") {
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column {
                                        Text(
                                            "Create Custom Persona",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Define custom identity, greeting, and system prompts",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Text(
                                            "New",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("Persona Name") },
                                    placeholder = { Text("e.g. Science Tutor, Python Expert") },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                OutlinedTextField(
                                    value = newGreeting,
                                    onValueChange = { newGreeting = it },
                                    label = { Text("Greeting Message") },
                                    placeholder = { Text("e.g. Hello! How can I assist you with code today?") },
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                OutlinedTextField(
                                    value = newPrompt,
                                    onValueChange = { newPrompt = it },
                                    label = { Text("System Instructions") },
                                    placeholder = { Text("You are an expert AI assistant specialized in...") },
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 3,
                                    maxLines = 6,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                OutlinedTextField(
                                    value = newPersonality,
                                    onValueChange = { newPersonality = it },
                                    label = { Text("Personality (Optional)") },
                                    placeholder = { Text("e.g. Gentle, soft-spoken, warm, slightly shy...") },
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                OutlinedTextField(
                                    value = newScenario,
                                    onValueChange = { newScenario = it },
                                    label = { Text("Scenario (Optional)") },
                                    placeholder = { Text("e.g. A quiet night in Penacony's dreamscape...") },
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                OutlinedTextField(
                                    value = newExamples,
                                    onValueChange = { newExamples = it },
                                    label = { Text("Example Dialogues (Optional)") },
                                    placeholder = { Text("{{user}}: Hi!\n{{char}}: Eh? You can see me...?") },
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 3,
                                    maxLines = 6,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.size(52.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (newAvatarPath.isNotBlank()) {
                                                com.raikaai.app.ui.chat.PersonaAvatar(
                                                    avatarUri = newAvatarPath,
                                                    size = 52.dp,
                                                    contentDescription = "Profile photo preview",
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(26.dp),
                                                )
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = { newAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                            enabled = !isSavingNewAvatar,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                if (isSavingNewAvatar) "Saving…" else if (newAvatarPath.isBlank()) "Choose photo (optional)" else "Change photo",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                        if (newAvatarPath.isNotBlank()) {
                                            TextButton(
                                                onClick = { newAvatarPath = "" },
                                                contentPadding = PaddingValues(horizontal = 4.dp),
                                            ) {
                                                Text("Remove photo", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (newName.isNotBlank()) {
                                            viewModel.addCustomPersona(
                                                name = newName.trim(),
                                                iconType = newIconType,
                                                systemPrompt = newPrompt.trim(),
                                                greeting = newGreeting.trim(),
                                                avatarUri = newAvatarPath.trim(),
                                                temperature = newTemp,
                                                topK = newTopK.toInt(),
                                                personality = newPersonality.trim(),
                                                scenario = newScenario.trim(),
                                                exampleDialogues = newExamples.trim(),
                                            )
                                            Toast.makeText(context, "Persona '$newName' created!", Toast.LENGTH_SHORT).show()
                                            newName = ""
                                            newGreeting = ""
                                            newPrompt = ""
                                            newPersonality = ""
                                            newScenario = ""
                                            newExamples = ""
                                            newAvatarPath = ""
                                            selectedTab = 0
                                        }
                                    },
                                    enabled = newName.isNotBlank() && !isSavingNewAvatar,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save Persona")
                                }
                            }
                        }
                    }

                    // Import / Export JSON Card
                    item(key = "import_json_card") {
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
                                        "Import & Export JSON",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Compatible with Character Card v2 and RaikaAI persona schemas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                OutlinedTextField(
                                    value = importJsonText,
                                    onValueChange = { importJsonText = it },
                                    label = { Text("Paste Character JSON") },
                                    placeholder = { Text("{\n  \"name\": \"...\",\n  \"system_prompt\": \"...\"\n}") },
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 3,
                                    maxLines = 6,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Button(
                                    onClick = {
                                        val ok = viewModel.importPersonaFromJson(importJsonText.trim())
                                        if (ok) {
                                            importJsonText = ""
                                            Toast.makeText(context, "Character imported successfully!", Toast.LENGTH_SHORT).show()
                                            selectedTab = 0
                                        } else {
                                            Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = importJsonText.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import Character JSON")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Edit Character Dialog ──
    editingPersona?.let { persona ->
        CharacterEditorDialog(
            viewModel = viewModel,
            initialPersona = persona,
            onDismiss = { editingPersona = null },
            onSave = { name, iconType, prompt, greeting, temp, topK, avatar, personality, scenario, examples ->
                // Edit = update in place. Builtin presets (unknown id) are
                // still saved as a new custom copy with a fresh id.
                val isCustom = viewModel.customPersonas.any { it.id == persona.id }
                viewModel.upsertCustomPersona(
                    PersonaPreset(
                        id = if (isCustom) persona.id else "custom_" + System.currentTimeMillis(),
                        name = name,
                        iconType = iconType,
                        description = persona.description,
                        greeting = greeting,
                        systemPrompt = prompt,
                        avatarUri = avatar,
                        temperature = temp,
                        topK = topK,
                        personality = personality,
                        scenario = scenario,
                        exampleDialogues = examples,
                    ),
                )
                editingPersona = null
                Toast.makeText(context, "Character '$name' updated!", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/**
 * ChatterUI-style Character Listing Card.
 */
@Composable
private fun CharacterCardItem(
    persona: PersonaPreset,
    isActive: Boolean,
    isCustom: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExportJson: () -> Unit,
) {
    val vectorIcon = remember(persona.iconType) { getIconVector(persona.iconType) }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        shape = RoundedCornerShape(18.dp),
        border =
            androidx.compose.foundation.BorderStroke(
                width = if (isActive) 1.5.dp else 1.dp,
                color =
                    if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    },
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Avatar + Name + Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (persona.avatarUri.isNotBlank()) {
                        com.raikaai.app.ui.chat.PersonaAvatar(
                            avatarUri = persona.avatarUri,
                            size = 46.dp,
                            contentDescription = "Character photo",
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = vectorIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                persona.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (isActive) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            if (isCustom) "Custom Character" else "Built-in Persona",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Quick Export & Edit menu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExportJson, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Export JSON",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (isCustom) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // System Prompt preview
            Text(
                persona.systemPrompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Greeting Bubble preview if present
            if (persona.greeting.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Greeting: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            persona.greeting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Footer row: Sampler info & Start Chat button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (persona.temperature != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        ) {
                            Text(
                                "Temp: ${persona.temperature}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (persona.topK != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        ) {
                            Text(
                                "Top-K: ${persona.topK}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Button(
                    onClick = onSelect,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isActive) "Continue Chat" else "Chat Now", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Rich Dialog for Creating / Editing Characters & Personas.
 */
@Composable
private fun CharacterEditorDialog(
    viewModel: ChatViewModel,
    initialPersona: PersonaPreset?,
    onDismiss: () -> Unit,
    onSave: (name: String, iconType: String, prompt: String, greeting: String, temp: Float?, topK: Int?, avatar: String, personality: String, scenario: String, examples: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialPersona?.name ?: "") }
    var prompt by remember { mutableStateOf(initialPersona?.systemPrompt ?: "") }
    var greeting by remember { mutableStateOf(initialPersona?.greeting ?: "") }
    var personality by remember { mutableStateOf(initialPersona?.personality ?: "") }
    var scenario by remember { mutableStateOf(initialPersona?.scenario ?: "") }
    var examples by remember { mutableStateOf(initialPersona?.exampleDialogues ?: "") }
    var iconType by remember { mutableStateOf(initialPersona?.iconType ?: "custom") }
    var tempSlider by remember { mutableFloatStateOf((initialPersona?.temperature ?: 0.7f).coerceIn(0f, 2f)) }
    var useSpecificSampler by remember { mutableStateOf(initialPersona?.temperature != null) }
    var avatarPath by remember { mutableStateOf(initialPersona?.avatarUri.orEmpty()) }
    var isSavingAvatar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val avatarPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                isSavingAvatar = true
                scope.launch {
                    avatarPath = viewModel.savePersonaAvatar(uri).orEmpty()
                    isSavingAvatar = false
                }
            }
        }

    val iconOptions = listOf(
        "custom" to Icons.Default.SmartToy,
        ChatConstants.ROLE_ASSISTANT to Icons.Default.Person,
        "coder" to Icons.Default.Code,
        "writer" to Icons.Default.Description,
        "translator" to Icons.Default.Translate,
        "reasoning" to Icons.Default.Psychology,
        "shell" to Icons.Default.Terminal,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPersona == null) "Create Character Card" else "Edit Character") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Character Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Text("Profile Photo (Optional)", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (avatarPath.isNotBlank()) {
                                    com.raikaai.app.ui.chat.PersonaAvatar(
                                        avatarUri = avatarPath,
                                        size = 52.dp,
                                        contentDescription = "Profile photo preview",
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    enabled = !isSavingAvatar,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        if (isSavingAvatar) "Saving…" else if (avatarPath.isBlank()) "Choose photo" else "Change photo",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                if (avatarPath.isNotBlank()) {
                                    TextButton(
                                        onClick = { avatarPath = "" },
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                    ) {
                                        Text("Remove", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            Text(
                                "Square photos work best",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Text("Avatar Icon", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        iconOptions.forEach { (type, icon) ->
                            val isSelected = iconType == type
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { iconType = type },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = greeting,
                        onValueChange = { greeting = it },
                        label = { Text("First Message / Greeting (Optional)") },
                        placeholder = { Text("e.g. Hello! How can I assist you today?") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("System Instructions / Persona Description") },
                        placeholder = { Text("Describe the character's personality, style, and rules...") },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = personality,
                        onValueChange = { personality = it },
                        label = { Text("Personality (Optional)") },
                        placeholder = { Text("e.g. Gentle, soft-spoken, warm, slightly shy...") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = scenario,
                        onValueChange = { scenario = it },
                        label = { Text("Scenario (Optional)") },
                        placeholder = { Text("e.g. A quiet night in Penacony's dreamscape...") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = examples,
                        onValueChange = { examples = it },
                        label = { Text("Example Dialogues (Optional)") },
                        placeholder = { Text("{{user}}: Hi!\n{{char}}: Eh? You can see me...?") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Custom Sampler (Temperature: ${String.format(Locale.ROOT, "%.2f", tempSlider)})", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = useSpecificSampler,
                            onCheckedChange = { useSpecificSampler = it },
                        )
                    }
                    if (useSpecificSampler) {
                        Slider(
                            value = tempSlider,
                            onValueChange = { tempSlider = it },
                            valueRange = 0.0f..2.0f,
                            steps = 20,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && prompt.isNotBlank()) {
                        onSave(
                            name.trim(),
                            iconType,
                            prompt.trim(),
                            greeting.trim(),
                            if (useSpecificSampler) tempSlider else null,
                            if (useSpecificSampler) 40 else null,
                            avatarPath.trim(),
                            personality.trim(),
                            scenario.trim(),
                            examples.trim(),
                        )
                    }
                },
                enabled = name.isNotBlank() && prompt.isNotBlank() && !isSavingAvatar,
            ) {
                Text("Save Character")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun getIconVector(iconType: String): ImageVector =
    when (iconType) {
        ChatConstants.ROLE_ASSISTANT -> Icons.Default.Person
        "coder" -> Icons.Default.Code
        "writer" -> Icons.Default.Description
        "translator" -> Icons.Default.Translate
        "reasoning" -> Icons.Default.Psychology
        "speed" -> Icons.Default.Speed
        "shell" -> Icons.Default.Terminal
        "roleplay" -> Icons.Outlined.EmojiEmotions
        "general" -> Icons.Default.AutoAwesome
        else -> Icons.Default.AutoAwesome
    }
