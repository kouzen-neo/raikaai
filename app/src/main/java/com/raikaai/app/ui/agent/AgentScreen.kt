package com.raikaai.app.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.core.agent.AgentConfig
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.component.ScreenHeader
import com.raikaai.app.ui.mcp.McpScreen
import com.raikaai.app.ui.skills.SkillsScreen

/**
 * One place for everything that shapes how Raika works:
 * Plugins (built-in abilities) · Skills (instructions) · MCP (external tools)
 * · Memory (SOUL.md, AGENTS.md, MEMORY.md).
 */
@Composable
fun AgentScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    startTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var createSkill by remember { mutableIntStateOf(0) }
    var addServer by remember { mutableIntStateOf(0) }
    val tabs =
        listOf(
            "Plugins" to Icons.Outlined.Extension,
            "Skills" to Icons.Outlined.Description,
            "MCP" to Icons.Outlined.Hub,
            "Memory" to Icons.Outlined.Psychology,
        )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp)) {
            ScreenHeader(
                title = "Agent",
                subtitle = "What Raika can do and what it remembers",
                onOpenDrawer = onOpenDrawer,
            ) {
                if (tab == 1 || tab == 2) {
                    IconButton(onClick = { if (tab == 1) createSkill++ else addServer++ }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = if (tab == 1) "Create skill" else "Add server",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            SecondaryScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 12.dp,
            ) {
                tabs.forEachIndexed { i, (label, icon) ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, fontWeight = FontWeight.SemiBold)
                            }
                        },
                    )
                }
            }
            when (tab) {
                0, 1 -> SkillsScreen(viewModel, onOpenDrawer, embeddedTab = tab, createRequests = createSkill)
                2 -> McpScreen(viewModel, onOpenDrawer, onBack, embedded = true, addRequests = addServer)
                else -> MemoryTab()
            }
        }
    }
}

@Composable
private fun MemoryTab() {
    val context = LocalContext.current
    val config = remember { AgentConfig.get(context) }
    var doc by rememberSaveable { mutableStateOf(AgentConfig.Doc.SOUL) }
    var text by remember(doc) { mutableStateOf(config.read(doc)) }
    var saved by remember(doc) { mutableStateOf(text) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AgentConfig.Doc.entries.forEachIndexed { i, d ->
                SegmentedButton(
                    selected = doc == d,
                    onClick = { doc = d },
                    shape = SegmentedButtonDefaults.itemShape(i, AgentConfig.Doc.entries.size),
                ) { Text(d.fileName, style = MaterialTheme.typography.labelMedium) }
            }
        }
        Text(doc.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(360.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            placeholder = { Text(if (doc == AgentConfig.Doc.MEMORY) "- Prefers short answers" else "") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                enabled = text != saved,
                onClick = {
                    config.write(doc, text)
                    saved = text
                },
            ) { Text(if (text != saved) "Save" else "Saved") }
            TextButton(onClick = {
                config.resetToDefault(doc)
                text = config.read(doc)
                saved = text
            }) { Text(if (doc == AgentConfig.Doc.MEMORY) "Clear" else "Reset to default") }
        }
        Text(
            "Same files as /root/.raika in the Linux environment, where Raika can read and edit them; " +
                "past chats are saved there too (/root/.raika/chats). On-device (LiteRT) models get a short " +
                "version to keep the small context free; roleplay uses only a short memory.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
