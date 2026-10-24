package com.raikaai.app.ui.roleplay.pages.safebooru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.raikaai.app.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

private data class SafeBooruTag(val label: String, val value: String)
private data class SafeBooruPost(val id: Int, val previewUrl: String, val sampleUrl: String)

@Composable
fun RoleplaySafeBooruScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSelectImage: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<SafeBooruTag>()) }
    var activeTag by remember { mutableStateOf<SafeBooruTag?>(null) }
    var posts by remember { mutableStateOf(emptyList<SafeBooruPost>()) }
    var loading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(query) {
        val search = query.trim()
        if (search.length < 3) {
            tags = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        tags = requestTags(search)
    }

    fun searchTag(tag: SafeBooruTag) {
        activeTag = tag
        query = ""
        page = 0
        posts = emptyList()
        hasMore = true
    }

    LaunchedEffect(activeTag, page) {
        val tag = activeTag ?: return@LaunchedEffect
        loading = true
        val result = requestPosts(tag.value, page)
        posts = if (page == 0) result else posts + result
        hasMore = result.isNotEmpty()
        loading = false
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !loading && hasMore && posts.isNotEmpty()) {
            page += 1
        }
    }

    Column(modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Search Image", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("tag") },
        )
        tags.forEach { tag ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().clickable { searchTag(tag) }.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(tag.label.substringBeforeLast(" "))
                Text(tag.label.substringAfterLast(" ", ""))
            }
        }
        activeTag?.let { tag ->
            Text("result for :", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text(tag.label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(posts, key = { it.id }) { post ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(post.previewUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build(),
                    contentDescription = "Select image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onSelectImage(post.sampleUrl) },
                )
            }
            if (loading) {
                item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            }
        }
    }
}

private suspend fun requestTags(query: String): List<SafeBooruTag> = withContext(Dispatchers.IO) {
    runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://safebooru.org/autocomplete.php?q=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RaikaAI/1.0")
            .build()
        NetworkUtils.HttpClient.apiClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching emptyList()
            val json = response.body?.string().orEmpty()
            val array = JSONArray(json)
            (0 until minOf(array.length(), 5)).map {
                val obj = array.getJSONObject(it)
                SafeBooruTag(obj.optString("label"), obj.optString("value"))
            }
        }
    }.getOrDefault(emptyList())
}

private suspend fun requestPosts(tag: String, page: Int): List<SafeBooruPost> = withContext(Dispatchers.IO) {
    runCatching {
        val encodedTag = URLEncoder.encode("$tag sort:score:desc", "UTF-8")
        val url = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1&limit=60&pid=$page&tags=$encodedTag"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RaikaAI/1.0")
            .build()
        NetworkUtils.HttpClient.apiClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching emptyList()
            val text = response.body?.string().orEmpty()
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val post = array.optJSONObject(i) ?: return@mapNotNull null
                val id = post.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                val preview = post.optString("preview_url", "")
                val sample = post.optString("sample_url", preview)
                if (preview.isBlank()) return@mapNotNull null
                SafeBooruPost(id, preview, sample)
            }
        }
    }.getOrDefault(emptyList())
}
