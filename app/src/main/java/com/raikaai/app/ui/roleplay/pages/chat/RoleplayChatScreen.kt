package com.raikaai.app.ui.roleplay.pages.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.raikaai.app.ui.chat.ChatScreen
import com.raikaai.app.ui.chat.ChatViewModel
import com.raikaai.app.ui.chat.PersonaPreset
import com.raikaai.app.ui.roleplay.pages.home.RoleplayCharacter

@Composable
fun RoleplayChatScreen(
    character: RoleplayCharacter?,
    conversationId: String? = null,
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    if (character == null && conversationId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Character not found")
        }
        return
    }

    LaunchedEffect(character?.id, conversationId) {
        viewModel.setRoleplayMode(true)
        if (conversationId != null) {
            viewModel.loadConversation(conversationId)
        } else {
            val selected = character ?: return@LaunchedEffect
            val persona = PersonaPreset(
                id = "roleplay_${selected.id}",
                name = selected.name,
                iconType = "roleplay",
                description = selected.description.orEmpty(),
                greeting = selected.greeting.orEmpty(),
                systemPrompt = selected.systemPrompt.orEmpty().ifBlank {
                    "You are ${selected.name}. Stay in character and respond consistently."
                },
                avatarUri = selected.avatarUrl.orEmpty(),
            )
            viewModel.registerRoleplayPersona(persona)
            viewModel.newChat(persona)
        }
    }

    ChatScreen(
        viewModel = viewModel,
        onOpenDrawer = onOpenDrawer,
        onBack = onBack,
        isRolePlay = true
    )
}
