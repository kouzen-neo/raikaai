package com.raikaai.app.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import com.raikaai.app.ui.theme.success
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raikaai.app.ui.chat.PERSONA_PRESETS
import com.raikaai.app.ui.chat.PersonaAvatar
import com.raikaai.app.ui.chat.PersonaPreset
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.raikaai.app.ui.about.AboutScreen
import com.raikaai.app.ui.characters.CharactersScreen
import com.raikaai.app.ui.chat.ChatScreen
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.history.HistoryScreen
import com.raikaai.app.ui.logs.LogsScreen
import com.raikaai.app.ui.models.ModelsScreen
import com.raikaai.app.ui.roleplay.RolePlayHomeScreen
import com.raikaai.app.ui.sampler.SamplerScreen
import com.raikaai.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object NavRoute {
    const val CHAT = "chat"
    const val CHARACTERS = "characters"
    const val ROLEPLAY = "roleplay"
    const val SKILLS = "skills"
    const val SAMPLER = "sampler"
    const val MODELS = "models"
    const val SERVER = "server"
    const val LOGS = "logs"
    const val HISTORY = "history"
    const val MCP = "mcp"
    const val ENVIRONMENT = "environment"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

data class DrawerMenuItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private data class SafeDrawerConversation(val id: String, val title: String, val roleplay: Boolean = false)

/**
 * Root Application Container with Swipeable Modal Navigation Drawer (Sidebar).
 * Replicating ChatterUI's modular menu organization, mode toggle, and design structure.
 */
@Composable
fun RaikaAIApp(
    viewModel: ChatViewModel,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val rightDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoute.CHAT

    // Recent chats: normal and roleplay together (roleplay has its own icon
    // and opens in the Characters hub). Persisted Gson data can contain JVM
    // nulls in Kotlin non-null fields, so only validated values are copied.
    val roleplayIds = viewModel.roleplayConversations.value.mapNotNull { runCatching { it.id }.getOrNull() }.toSet()
    val conversations = (viewModel.normalConversations.value + viewModel.roleplayConversations.value)
        .sortedByDescending { runCatching { it.updatedAt }.getOrDefault(0L) }
        .mapNotNull { conversation ->
            runCatching {
                val id = conversation.id.trim()
                if (id.isBlank()) return@runCatching null
                SafeDrawerConversation(id, conversation.title.trim().ifBlank { "New conversation" }, id in roleplayIds)
            }.getOrNull()
        }.distinctBy { it.id }

    fun navigateTo(route: String) {
        scope.launch {
            drawerState.close()
            rightDrawerState.close()
        }
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Opening a drawer while the composer still has focus left the IME on
    // screen, so the sidebar opened behind the keyboard and the chat input
    // stayed focused underneath. Drop focus and hide the keyboard as soon as
    // either drawer opens — this also covers the edge-swipe gesture, which
    // never goes through a click handler.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(drawerState.isOpen, rightDrawerState.isOpen) {
        if (drawerState.isOpen || rightDrawerState.isOpen) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val leftDrawerGesturesEnabled = !rightDrawerState.isOpen
    val rightDrawerGesturesEnabled = !drawerState.isOpen && (currentRoute == NavRoute.CHAT || currentRoute == NavRoute.ROLEPLAY)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = leftDrawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(290.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp)
                            .statusBarsPadding(),
                ) {
                    // Header: brand + close only
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "RaikaAI",
                            style = MaterialTheme.typography.titleLarge,
                        )

                        IconButton(
                            onClick = { scope.launch { drawerState.close() } },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close Drawer", modifier = Modifier.size(18.dp))
                        }
                    }

                    // New chat: the most common action, as a tonal button.
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            viewModel.newGeneralChat()
                            navigateTo(NavRoute.CHAT)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New chat")
                    }

                    Spacer(Modifier.height(8.dp))

                    // Priority order: talk, then build/tune, then recent chats.
                    // Settings / Logs / About live in the footer.
                    val mainItems =
                        listOf(
                            DrawerMenuItem(NavRoute.CHAT, "Chat", Icons.Outlined.ChatBubbleOutline),
                            // Characters and Roleplay are one hub (Discover · Mine · History · Create · Profile).
                            DrawerMenuItem(NavRoute.ROLEPLAY, "Characters", Icons.Outlined.TheaterComedy),
                        )
                    val toolItems =
                        listOf(
                            DrawerMenuItem(NavRoute.MODELS, "Models", Icons.Outlined.Memory),
                            DrawerMenuItem(NavRoute.SAMPLER, "Parameters", Icons.Outlined.Tune),
                            // Plugins, Skills, MCP and Memory live in one Agent hub.
                            DrawerMenuItem(NavRoute.SKILLS, "Agent", Icons.Outlined.Psychology),
                            DrawerMenuItem(NavRoute.ENVIRONMENT, "Terminal", Icons.Outlined.Terminal),
                            DrawerMenuItem(NavRoute.SERVER, "Local API", Icons.Outlined.Dns),
                        )

                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(mainItems, key = { it.route }) { item ->
                            DrawerRow(
                                icon = item.icon,
                                label = item.label,
                                selected = currentRoute == item.route || (item.route == NavRoute.ROLEPLAY && currentRoute == NavRoute.CHARACTERS),
                                onClick = { navigateTo(item.route) },
                            )
                        }
                        item(key = "h_tools") { DrawerSectionHeader("Tools") }
                        items(toolItems, key = { it.route }) { item ->
                            DrawerRow(
                                icon = item.icon,
                                label = item.label,
                                selected = currentRoute == item.route || (item.route == NavRoute.SKILLS && currentRoute == NavRoute.MCP),
                                onClick = { navigateTo(item.route) },
                            )
                        }
                        if (conversations.isNotEmpty()) {
                            item(key = "h_recent") { DrawerSectionHeader("Recent") }
                            items(conversations.take(8), key = { "c_" + it.id }) { conv ->
                                DrawerRow(
                                    icon = if (conv.roleplay) Icons.Outlined.TheaterComedy else null,
                                    label = conv.title,
                                    selected = conv.id == viewModel.currentConversationId &&
                                        currentRoute == (if (conv.roleplay) NavRoute.ROLEPLAY else NavRoute.CHAT),
                                    onClick = {
                                        if (conv.roleplay) {
                                            viewModel.pendingRoleplayChatId.value = conv.id
                                            navigateTo(NavRoute.ROLEPLAY)
                                        } else {
                                            viewModel.setRoleplayMode(false)
                                            viewModel.loadConversation(conv.id)
                                            navigateTo(NavRoute.CHAT)
                                        }
                                    },
                                    compact = true,
                                )
                            }
                        }
                        item(key = "all_chats") {
                            DrawerRow(
                                icon = Icons.Outlined.History,
                                label = "All chats",
                                selected = currentRoute == NavRoute.HISTORY,
                                onClick = { navigateTo(NavRoute.HISTORY) },
                                compact = true,
                            )
                        }
                    }

                    // Footer: rarely used destinations, one compact row.
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DrawerFooterItem(Icons.Outlined.Settings, "Settings", currentRoute == NavRoute.SETTINGS) { navigateTo(NavRoute.SETTINGS) }
                        DrawerFooterItem(Icons.Outlined.ReceiptLong, "Logs", currentRoute == NavRoute.LOGS) { navigateTo(NavRoute.LOGS) }
                        DrawerFooterItem(Icons.Outlined.Info, "About", currentRoute == NavRoute.ABOUT) { navigateTo(NavRoute.ABOUT) }
                    }
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = rightDrawerState,
                gesturesEnabled = rightDrawerState.isOpen,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        CharacterRightDrawerSheet(
                            viewModel = viewModel,
                            onClose = { scope.launch { rightDrawerState.close() } },
                            onManageCharacters = {
                                scope.launch { rightDrawerState.close() }
                                navigateTo(NavRoute.CHARACTERS)
                            },
                        )
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = NavRoute.CHAT,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable(NavRoute.CHAT) {
                                ChatScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onOpenRightDrawer = { scope.launch { rightDrawerState.open() } },
                                    onNavigateToCharacters = {
                                        navController.navigate(NavRoute.CHARACTERS) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToModels = {
                                        navController.navigate(NavRoute.MODELS) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                            composable(NavRoute.CHARACTERS) {
                                RolePlayHomeScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onOpenRightDrawer = { scope.launch { rightDrawerState.open() } },
                                    onOpenNormalChat = {
                                        viewModel.setRoleplayMode(false)
                                        navigateTo(NavRoute.CHAT)
                                    },
                                    startTab = "mine",
                                )
                            }

                            composable(NavRoute.ROLEPLAY) {
                                RolePlayHomeScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onOpenRightDrawer = { scope.launch { rightDrawerState.open() } },
                                    onOpenNormalChat = {
                                        viewModel.setRoleplayMode(false)
                                        navigateTo(NavRoute.CHAT)
                                    },
                                )
                            }

                composable(NavRoute.SKILLS) {
                    com.raikaai.app.ui.agent.AgentScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(NavRoute.MCP) {
                    com.raikaai.app.ui.agent.AgentScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onBack = { navController.popBackStack() },
                        startTab = 2,
                    )
                }
                composable(NavRoute.ENVIRONMENT) {
                    com.raikaai.app.ui.environment.EnvironmentScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToChat = {
                            navController.navigate(NavRoute.CHAT) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(NavRoute.SAMPLER) {
                    SamplerScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                    )
                }
                composable(NavRoute.MODELS) {
                    ModelsScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(NavRoute.SERVER) {
                    com.raikaai.app.ui.server.ServerScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                    )
                }
                composable(NavRoute.LOGS) {
                    LogsScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                    )
                }
                composable(NavRoute.HISTORY) {
                    HistoryScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onBack = { navController.popBackStack() },
                        onOpenConversation = {
                            navController.navigate(NavRoute.CHAT) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenRoleplayChat = { navigateTo(NavRoute.ROLEPLAY) },
                    )
                }
                composable(NavRoute.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(NavRoute.ABOUT) {
                    AboutScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                    )
                }
            }
        }
    }
            }
        }
    }
}

@Composable
private fun CharacterRightDrawerSheet(
    viewModel: ChatViewModel,
    onClose: () -> Unit,
    onManageCharacters: () -> Unit,
) {
    val activePersona by viewModel.activePersona.collectAsState()
    val customPersonas = viewModel.customPersonas
    val allPersonas = remember(customPersonas.toList()) {
        PERSONA_PRESETS + customPersonas.toList()
    }
    var query by remember { mutableStateOf("") }
    val filteredPersonas = remember(query, allPersonas) {
        if (query.isBlank()) allPersonas
        else {
            val q = query.trim().lowercase(java.util.Locale.ROOT)
            allPersonas.filter {
                it.name.lowercase(java.util.Locale.ROOT).contains(q) ||
                    it.description.lowercase(java.util.Locale.ROOT).contains(q)
            }
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(300.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp)
                .statusBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Characters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${allPersonas.size} available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // Quick search field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                placeholder = { Text("Search characters…", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }
                },
            )

            // Character rows
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
            ) {
                items(filteredPersonas, key = { it.id }) { persona ->
                    CharacterDrawerRow(
                        persona = persona,
                        isActive = persona.id == activePersona.id,
                        onClick = {
                            viewModel.selectPersonaQuick(persona)
                            onClose()
                        },
                    )
                }
            }

            // Bottom manage button
            OutlinedButton(
                onClick = onManageCharacters,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage Characters", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CharacterDrawerRow(
    persona: PersonaPreset,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (persona.id == "general") {
                com.raikaai.app.ui.component.RaikaLogo(size = 40.dp, shape = CircleShape)
            } else if (persona.avatarUri.isNotBlank()) {
                PersonaAvatar(
                    avatarUri = persona.avatarUri,
                    size = 40.dp,
                    contentDescription = persona.name,
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (persona.id == "general") Icons.Outlined.AutoAwesome else Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = persona.description.ifBlank { persona.greeting }.trim()
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isActive) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Minimal 48dp drawer row: outline icon only, selected state uses neutral
 * surfaceContainerHigh without primary tint.
 */
@Composable
private fun DrawerRow(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        modifier = modifier.fillMaxWidth().height(if (compact) 40.dp else 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                color = if (selected) scheme.onSecondaryContainer else if (compact) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerFooterItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}
