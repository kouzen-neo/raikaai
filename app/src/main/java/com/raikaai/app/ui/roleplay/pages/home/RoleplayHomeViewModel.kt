package com.raikaai.app.ui.roleplay.pages.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.raikaai.app.data.RoleplayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RoleplayCategory(val id: Int, val name: String, val slug: String)
data class RoleplayCharacter(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("slug")
    val slug: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("greeting")
    val greeting: String?,
    @SerializedName("system_prompt") val systemPrompt: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("view")
    val view: Int = 0,
)

data class RoleplayHomeState(
    val categories: List<RoleplayCategory> = listOf(RoleplayCategory(9999, "Discover", "discover")),
    val selectedCategoryId: Int = 9999,
    val characters: List<RoleplayCharacter> = emptyList(),
    val trending: List<RoleplayCharacter> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

private data class RoleplayHomeCache(
    val categories: List<RoleplayCategory> = emptyList(),
    val trending: List<RoleplayCharacter> = emptyList(),
    val pages: Map<Int, List<RoleplayCharacter>> = emptyMap(),
    val selectedCategoryId: Int = 9999,
)

class RoleplayHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val gson = Gson()
    private val pages = mutableMapOf<Int, MutableList<RoleplayCharacter>>()
    private val offsets = mutableMapOf<Int, Int>()
    private val state = MutableStateFlow(RoleplayHomeState())
    val uiState: StateFlow<RoleplayHomeState> = state

    // v2 avoids restoring entries written by the old Gson model, which could
    // contain JVM nulls in Kotlin non-null fields after a release update.
    private val cachePreferences = application.getSharedPreferences("roleplay_home_cache_v2", 0)

    init {
        restoreCache()
        loadCategories()
        loadTrending()
        loadCategory(9999, true)
    }

    private fun restoreCache() {
        val json = cachePreferences.getString("data", null) ?: return
        runCatching {
            gson.fromJson(json, RoleplayHomeCache::class.java)
        }.getOrNull()?.let { cached ->
            pages.clear()
            pages.putAll(cached.pages.mapValues { it.value.toMutableList() })
            offsets.clear()
            cached.pages.forEach { (categoryId, characters) -> offsets[categoryId] = characters.size }
            val cachedCharacters = cached.pages[cached.selectedCategoryId].orEmpty()
            state.value = state.value.copy(
                categories = cached.categories.ifEmpty { state.value.categories },
                trending = cached.trending,
                characters = cachedCharacters,
                selectedCategoryId = cached.selectedCategoryId,
                hasMore = cachedCharacters.size >= 50,
            )
        }
    }

    private fun persistCache() {
        val current = state.value
        val cache = RoleplayHomeCache(
            categories = current.categories,
            trending = current.trending,
            pages = pages.mapValues { it.value.toList() },
            selectedCategoryId = current.selectedCategoryId,
        )
        cachePreferences.edit().putString("data", gson.toJson(cache)).apply()
    }

    private fun loadTrending() = viewModelScope.launch {
        runCatching {
            RoleplayRepository.loadTrending()
        }.onSuccess {
            state.value = state.value.copy(trending = it)
            persistCache()
        }
    }

    fun selectCategory(id: Int) {
        state.value = state.value.copy(selectedCategoryId = id)
        loadCategory(id, reset = true, forceRefresh = id == 9999)
    }

    /** Optimistically increments a character's view count when its card opens. */
    fun incrementCharacterView(characterId: Int) {
        val currentView = sequenceOf(
            state.value.trending.firstOrNull { it.id == characterId }?.view,
            state.value.characters.firstOrNull { it.id == characterId }?.view,
            pages.values.asSequence().flatten().firstOrNull { it.id == characterId }?.view,
        ).filterNotNull().maxOrNull() ?: 0
        val nextView = currentView + 1

        fun update(character: RoleplayCharacter) =
            if (character.id == characterId) character.copy(view = nextView) else character

        pages.replaceAll { _, characters -> characters.map(::update).toMutableList() }
        state.value = state.value.copy(
            characters = state.value.characters.map(::update),
            trending = state.value.trending.map(::update),
        )
        persistCache()

        viewModelScope.launch {
            RoleplayRepository.incrementView(characterId)
        }
    }

    fun loadMore() {
        if (!state.value.loading && !state.value.loadingMore && state.value.hasMore) {
            loadCategory(state.value.selectedCategoryId, false)
        }
    }

    private fun loadCategories() = viewModelScope.launch {
        runCatching {
            RoleplayRepository.loadCategories()
        }.onSuccess { fetched ->
            state.value = state.value.copy(
                categories = (listOf(
                    RoleplayCategory(9999, "Discover", "discover")
                ) + fetched).distinctBy { category -> category.id }
            )
            persistCache()
        }
    }

    private fun loadCategory(categoryId: Int, reset: Boolean, forceRefresh: Boolean = false) =
        viewModelScope.launch {
            if (reset && !forceRefresh && pages.containsKey(categoryId)) {
                state.value = state.value.copy(
                    characters = pages[categoryId].orEmpty(),
                    hasMore = pages[categoryId].orEmpty().size >= 50
                )
                return@launch
            }
            state.value = state.value.copy(loading = reset, loadingMore = !reset, error = null)
            runCatching {
                val offset = if (reset) 0 else offsets[categoryId] ?: 0
                RoleplayRepository.loadCharacters(categoryId, offset)
            }.onSuccess { result ->
                val list =
                    if (reset) result.toMutableList() else (pages[categoryId].orEmpty() + result).toMutableList()
                pages[categoryId] = list
                offsets[categoryId] = list.size
                if (state.value.selectedCategoryId == categoryId) {
                    state.value = state.value.copy(characters = list, hasMore = result.size == 50)
                }
                persistCache()
            }.onFailure {
                if (state.value.selectedCategoryId == categoryId) {
                    state.value = state.value.copy(error = it.message)
                }
            }
            state.value = state.value.copy(loading = false, loadingMore = false)
        }
}
