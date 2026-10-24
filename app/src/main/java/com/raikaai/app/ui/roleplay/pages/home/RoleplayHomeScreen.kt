package com.raikaai.app.ui.roleplay.pages.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.raikaai.app.data.RoleplayRepository
import com.raikaai.app.util.orFallback

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoleplayHomeScreen(
    modifier: Modifier = Modifier,
    onOpenChat: (RoleplayCharacter) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    home: RoleplayHomeViewModel = viewModel(),
) {
    fun openCharacter(character: RoleplayCharacter) {
        home.incrementCharacterView(character.id)
        onOpenChat(character)
    }

    val state by home.uiState.collectAsState()
    val listState = rememberLazyListState()
    val characters = state.characters

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.loading && !state.loadingMore) {
            home.loadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onOpenSearch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("find character", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            Spacer(Modifier.height(14.dp))
            if (state.trending.isNotEmpty()) {
                Text("Trending", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.trending, key = { it.id }) { character ->
                        CharacterCard(character, Modifier.width(155.dp), ::openCharacter)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        stickyHeader {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { home.selectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }
            }
        }

        items(characters.chunked(2), key = { it.firstOrNull()?.id ?: 0 }) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                row.forEach { character ->
                    Box(Modifier.weight(1f)) {
                        CharacterCard(character, Modifier.fillMaxWidth(), ::openCharacter)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (state.loading || state.loadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        state.error?.let { err ->
            item {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: RoleplayCharacter,
    modifier: Modifier = Modifier,
    onOpenChat: (RoleplayCharacter) -> Unit = {},
) {
    Card(
        onClick = { onOpenChat(character) },
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            val thumbUrl = remember(character.avatarUrl) {
                RoleplayRepository.toThumbnailUrl(character.avatarUrl)
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbUrl)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = character.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = character.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = character.description.orFallback(character.greeting.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
