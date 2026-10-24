package com.raikaai.app.ui.roleplay.pages.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.raikaai.app.data.RoleplayRepository
import com.raikaai.app.ui.roleplay.pages.home.RoleplayCharacter
import com.raikaai.app.ui.roleplay.pages.home.RoleplayHomeViewModel
import kotlinx.coroutines.launch

@Composable
fun RoleplaySearchScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenChat: (RoleplayCharacter) -> Unit = {},
    home: RoleplayHomeViewModel = viewModel(),
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<RoleplayCharacter>()) }
    var loading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        val text = query.trim()
        if (text.isBlank()) return
        scope.launch {
            loading = true
            hasSearched = true
            results = runCatching { RoleplayRepository.searchCharacters(text) }.getOrDefault(emptyList())
            loading = false
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Search characters", style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            placeholder = { Text("find character") },
            trailingIcon = {
                IconButton(onClick = ::search, enabled = !loading) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else if (hasSearched && results.isEmpty()) {
            Text("No characters found", modifier = Modifier.padding(top = 20.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.id }) { character ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            home.incrementCharacterView(character.id)
                            onOpenChat(character)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(RoleplayRepository.toThumbnailUrl(character.avatarUrl))
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = character.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(character.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
