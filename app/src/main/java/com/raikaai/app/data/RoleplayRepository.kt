package com.raikaai.app.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.raikaai.app.ui.roleplay.pages.home.RoleplayCategory
import com.raikaai.app.ui.roleplay.pages.home.RoleplayCharacter
import com.raikaai.app.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object RoleplayRepository {
    private val client get() = NetworkUtils.HttpClient.apiClient
    private val gson = Gson()
    private val safebooruRegex = Regex(
        """safebooru\.org/(?:images|samples)/(\d+)/(?:sample_)?([a-f0-9]+)\.(?:jpg|png|jpeg|webp)""",
        RegexOption.IGNORE_CASE
    )

    fun toThumbnailUrl(url: String?): String {
        val raw = url.orEmpty().trim()
        if (raw.isBlank()) return ""
        return raw.replace(safebooruRegex, "safebooru.org/thumbnails/$1/thumbnail_$2.jpg")
    }

    fun parseCharacters(json: String): List<RoleplayCharacter> = runCatching {
        JsonParser.parseString(json).asJsonArray.mapNotNull { value ->
            val o = value.asJsonObject
            val id = o.get("id")?.asInt ?: return@mapNotNull null
            val name = o.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RoleplayCharacter(
                id = id,
                name = name,
                slug = o.get("slug")?.asString.orEmpty(),
                description = o.get("description")?.takeUnless { it.isJsonNull }?.asString,
                greeting = o.get("greeting")?.takeUnless { it.isJsonNull }?.asString,
                systemPrompt = o.get("system_prompt")?.takeUnless { it.isJsonNull }?.asString,
                avatarUrl = o.get("avatar_url")?.takeUnless { it.isJsonNull }?.asString,
                categoryId = o.get("category_id")?.asInt ?: 9999,
                view = o.get("view")?.asInt ?: 0,
            )
        }
    }.getOrDefault(emptyList())

    suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RolePlayConstants.BASE_URL + path)
            .header("apikey", RolePlayConstants.PUBLIC_KEY)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: $body")
            body
        }
    }

    suspend fun loadTrending(): List<RoleplayCharacter> =
        parseCharacters(get("characters?select=id,name,slug,description,greeting,system_prompt,avatar_url,category_id,view&order=view.desc&limit=4"))

    suspend fun loadCategories(): List<RoleplayCategory> = withContext(Dispatchers.IO) {
        val json = get("categories?select=id,name,slug&order=id.asc")
        JsonParser.parseString(json).asJsonArray.mapNotNull { value ->
            val o = value.asJsonObject
            val id = o.get("id")?.asInt ?: return@mapNotNull null
            val name = o.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RoleplayCategory(id, name, o.get("slug")?.asString.orEmpty())
        }
    }

    suspend fun loadCharacters(categoryId: Int, offset: Int, limit: Int = 50): List<RoleplayCharacter> {
        val filter = if (categoryId == 9999) "" else "&category_id=eq.$categoryId"
        val json = get("characters?select=id,name,slug,description,greeting,system_prompt,avatar_url,category_id,view&order=name.asc&limit=$limit&offset=$offset$filter")
        val result = parseCharacters(json)
        return if (categoryId == 9999) result.shuffled() else result
    }

    suspend fun incrementView(characterId: Int) {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = gson.toJson(mapOf("char_id" to characterId))
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${RolePlayConstants.BASE_URL}rpc/increment_character_view")
                    .post(body)
                    .header("apikey", RolePlayConstants.PUBLIC_KEY)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                }
            }.onFailure {
                println("Roleplay view update failed for $characterId: ${it.message}")
            }
        }
    }

    suspend fun searchCharacters(query: String): List<RoleplayCharacter> = withContext(Dispatchers.IO) {
        val encoded = android.net.Uri.encode("*$query*")
        val path = "characters?select=id,name,slug,description,greeting,system_prompt,avatar_url,category_id,view&name=ilike.$encoded&order=name.asc&limit=50"
        parseCharacters(get(path))
    }

    suspend fun publishCharacter(
        name: String,
        description: String,
        greeting: String,
        systemPrompt: String,
        avatarUrl: String,
    ): RoleplayCharacter? = withContext(Dispatchers.IO) {
        runCatching {
            val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val payload = gson.toJson(
                mapOf(
                    "name" to name.trim(),
                    "slug" to slug,
                    "description" to description.trim(),
                    "greeting" to greeting.trim(),
                    "system_prompt" to systemPrompt.trim(),
                    "avatar_url" to avatarUrl.trim().ifBlank { null },
                ),
            )
            val request = Request.Builder()
                .url("${RolePlayConstants.BASE_URL}characters")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("apikey", RolePlayConstants.PUBLIC_KEY)
                .header("Prefer", "return=representation")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: $body")
                parseCharacters(body).firstOrNull()
            }
        }.getOrNull()
    }
}
