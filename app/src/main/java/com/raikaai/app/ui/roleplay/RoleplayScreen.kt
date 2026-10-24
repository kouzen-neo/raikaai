package com.raikaai.app.ui.roleplay

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.roleplay.pages.chat.RoleplayChatScreen
import com.raikaai.app.ui.roleplay.pages.home.RoleplayCharacter
import com.raikaai.app.ui.roleplay.pages.create.RoleplayCreateScreen
import com.raikaai.app.ui.roleplay.pages.home.RoleplayHomeScreen
import com.raikaai.app.ui.roleplay.pages.history.RoleplayHistoryScreen
import com.raikaai.app.ui.roleplay.pages.profile.RoleplayProfileScreen
import com.raikaai.app.ui.roleplay.pages.safebooru.RoleplaySafeBooruScreen
import com.raikaai.app.ui.roleplay.pages.search.RoleplaySearchScreen

@Composable
fun RoleplayScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenRightDrawer: () -> Unit = {}
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var selectedCharacter by remember { mutableStateOf<RoleplayCharacter?>(null) }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf("") }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        "Home" to Icons.Default.Home,
        "History" to Icons.Default.ChatBubble,
        "Create" to Icons.Default.Add,
        "Profile" to Icons.Default.Person
    )
    val showTopBar = route in setOf(
        "home",
        "history",
        "create",
        "profile",
    )
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Roleplay") },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Open menu",
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (route in setOf("home", "history", "create", "profile")) NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    val destination = listOf("home", "history", "create", "profile")[i]
                    NavigationBarItem(
                        route == destination,
                        { navController.navigate(destination) },
                        icon = { Icon(tab.second, tab.first) },
                        label = { Text(tab.first) })
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                RoleplayHomeScreen(
                    onOpenSearch = { navController.navigate("search") },
                    onOpenChat = { character ->
                    viewModel.setRoleplayMode(true)
                    scope.launch {
                        val recentConversation = viewModel.findRecentRoleplayConversation(
                            personaId = "roleplay_${character.id}",
                        )
                        selectedCharacter = if (recentConversation == null) character else null
                        selectedConversationId = recentConversation
                        navController.navigate("chat")
                    }
                    },
                )
            }
            composable("history") {
                RoleplayHistoryScreen(
                    viewModel = viewModel,
                    onOpenChat = { conversationId ->
                        selectedCharacter = null
                        selectedConversationId = conversationId
                        navController.navigate("chat")
                    },
                )
            }
            composable("create") {
                RoleplayCreateScreen(
                    viewModel = viewModel,
                    selectedImageUrl = selectedImageUrl,
                    onSearchImage = { navController.navigate("safebooru") },
                    onPublished = {
                        navController.navigate("history") {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable("safebooru") {
                RoleplaySafeBooruScreen(
                    onBack = { navController.popBackStack() },
                    onSelectImage = { imageUrl ->
                        selectedImageUrl = imageUrl
                        navController.popBackStack()
                    },
                )
            }
            composable("search") {
                RoleplaySearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { character ->
                        viewModel.setRoleplayMode(true)
                        scope.launch {
                            val recentConversation = viewModel.findRecentRoleplayConversation("roleplay_${character.id}")
                            selectedCharacter = if (recentConversation == null) character else null
                            selectedConversationId = recentConversation
                            navController.navigate("chat")
                        }
                    },
                )
            }
            composable("profile") { RoleplayProfileScreen() }
            composable("chat") {
                RoleplayChatScreen(
                    character = selectedCharacter,
                    conversationId = selectedConversationId,
                    viewModel = viewModel,
                    onOpenDrawer = onOpenDrawer,
                    onBack = {
                        viewModel.persistCurrentChat()
                        navController.popBackStack()
                    },
                    onOpenRightDrawer = onOpenRightDrawer,
                )
            }
        }
    }
}

@Composable
fun RolePlayHomeScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenRightDrawer: () -> Unit = {}
) =
    RoleplayScreen(viewModel, onOpenDrawer, onOpenRightDrawer)
