package com.raikaai.app.ui.roleplay

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
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
import com.raikaai.app.ui.roleplay.pages.profile.RoleplayProfileScreen
import com.raikaai.app.ui.roleplay.pages.safebooru.RoleplaySafeBooruScreen
import com.raikaai.app.ui.roleplay.pages.search.RoleplaySearchScreen

@Composable
fun RoleplayScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenRightDrawer: () -> Unit = {},
    onOpenNormalChat: () -> Unit = {},
    startTab: String = "home",
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var selectedCharacter by remember { mutableStateOf<RoleplayCharacter?>(null) }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf("") }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    // One Characters hub: the online catalog (Discover), your own characters
    // (Mine) and Create. Roleplay chats live in the sidebar's Recent list;
    // your roleplay profile is behind the header's person button.
    val tabs = listOf(
        "Discover" to Icons.Outlined.Explore,
        "Mine" to Icons.Outlined.Face,
        "Publish" to Icons.Outlined.CloudUpload,
    )
    val tabRoutes = listOf("home", "mine", "create")
    val showTopBar = route in tabRoutes || route == "profile"

    // Opened from the sidebar's Recent list.
    val pendingChat by viewModel.pendingRoleplayChatId
    LaunchedEffect(pendingChat) {
        val id = pendingChat ?: return@LaunchedEffect
        viewModel.pendingRoleplayChatId.value = null
        viewModel.setRoleplayMode(true)
        selectedCharacter = null
        selectedConversationId = id
        navController.navigate("chat")
    }
    Scaffold(
        topBar = {
            if (showTopBar) {
                androidx.compose.foundation.layout.Box(
                    Modifier.statusBarsPadding().padding(top = 10.dp),
                ) {
                    com.raikaai.app.ui.component.ScreenHeader(
                        title = if (route == "profile") "Your profile" else "Characters",
                        subtitle = if (route == "profile") "How characters know you" else "Roleplay and your own characters",
                        onOpenDrawer = onOpenDrawer,
                    ) {
                        IconButton(onClick = {
                            if (route == "profile") navController.popBackStack() else navController.navigate("profile")
                        }) {
                            Icon(
                                if (route == "profile") Icons.Outlined.Close else Icons.Outlined.AccountCircle,
                                contentDescription = if (route == "profile") "Close profile" else "Your profile",
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (route in tabRoutes) NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    val destination = tabRoutes[i]
                    NavigationBarItem(
                        route == destination,
                        { navController.navigate(destination) },
                        icon = { Icon(tab.second, tab.first) },
                        label = { Text(tab.first) })
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = startTab, modifier = Modifier.padding(padding)) {
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
            composable("mine") {
                com.raikaai.app.ui.characters.CharactersScreen(
                    viewModel = viewModel,
                    onOpenDrawer = onOpenDrawer,
                    onStartChatWithCharacter = { onOpenNormalChat() },
                    showHeader = false,
                )
            }
            composable("create") {
                RoleplayCreateScreen(
                    viewModel = viewModel,
                    selectedImageUrl = selectedImageUrl,
                    onSearchImage = { navController.navigate("safebooru") },
                    onPublished = {
                        navController.navigate("home") {
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
    onOpenRightDrawer: () -> Unit = {},
    onOpenNormalChat: () -> Unit = {},
    startTab: String = "home",
) =
    RoleplayScreen(viewModel, onOpenDrawer, onOpenRightDrawer, onOpenNormalChat, startTab)
