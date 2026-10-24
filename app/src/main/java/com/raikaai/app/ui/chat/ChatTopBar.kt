package com.raikaai.app.ui.chat

import com.raikaai.app.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource

/**
 * Chat header: character avatar + name, drawer access, new-chat action.
 * Extracted from the former ChatScreen topBar block.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    activePersona: PersonaPreset,
    onOpenDrawer: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNewChat: () -> Unit,
    onBack: () -> Unit = {},
    isRolePlay: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clickable { onNavigateToModels() }
                            .padding(vertical = 2.dp),
                ) {
                    if (activePersona.avatarUri.isNotBlank()) {
                        PersonaAvatar(
                            avatarUri = activePersona.avatarUri,
                            size = 36.dp,
                            contentDescription = stringResource(R.string.chat_character_photo),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = activePersona.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isRolePlay) {
                            onBack()
                        } else {
                            onOpenDrawer()
                        }
                    },
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Icon(
                        imageVector = if (isRolePlay) {
                            Icons.AutoMirrored.Filled.ArrowBack
                        } else {
                            Icons.Default.Menu
                        },
                        contentDescription = stringResource(R.string.chat_menu),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = {
                IconButton(onClick = onNewChat) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.chat_new_chat),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }
}
