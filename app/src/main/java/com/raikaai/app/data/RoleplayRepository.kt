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

    /**
     * Sharp versions of a Safebooru avatar, best first: the sample (about 850 px,
     * light) when the post has one, then the original file (its extension is not
     * stored, so each is tried), and the thumbnail last. Other hosts return the
     * URL unchanged. Callers move to the next entry when one fails to load.
     */
    private val anySafebooruRegex = Regex(
        """safebooru\.org/(?:images|samples|thumbnails)/(\d+)/(?:sample_|thumbnail_)?([a-f0-9]+)\.(?:jpg|png|jpeg|webp)""",
        RegexOption.IGNORE_CASE,
    )

    fun highResUrls(url: String?): List<String> {
        val raw = url.orEmpty().trim()
        if (raw.isBlank()) return emptyList()
        val m = anySafebooruRegex.find(raw) ?: return listOf(raw)
        val dir = m.groupValues[1]
        val hash = m.groupValues[2]
        return listOf(
            "https://safebooru.org/samples/$dir/sample_$hash.jpg",
            "https://safebooru.org/images/$dir/$hash.jpg",
            "https://safebooru.org/images/$dir/$hash.png",
            "https://safebooru.org/images/$dir/$hash.jpeg",
            "https://safebooru.org/thumbnails/$dir/thumbnail_$hash.jpg",
        )
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
            // Submissions go through submit_character(): it rate-limits and
            // stores the character hidden until the owner approves it.
            val payload = gson.toJson(
                mapOf(
                    "p_name" to name.trim(),
                    "p_description" to description.trim(),
                    "p_greeting" to greeting.trim(),
                    "p_system_prompt" to systemPrompt.trim(),
                    "p_avatar_url" to avatarUrl.trim().ifBlank { null },
                ),
            )
            val request = Request.Builder()
                .url("${RolePlayConstants.BASE_URL}rpc/submit_character")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("apikey", RolePlayConstants.PUBLIC_KEY)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject.get("message")?.asString }.getOrNull()
                    error(msg ?: "HTTP ${response.code}")
                }
                val o = com.google.gson.JsonParser.parseString(body).asJsonObject
                RoleplayCharacter(
                    id = o.get("id").asInt,
                    name = name.trim(),
                    slug = o.get("slug").asString,
                    description = description.trim(),
                    greeting = greeting.trim(),
                    systemPrompt = systemPrompt.trim(),
                    avatarUrl = avatarUrl.trim().ifBlank { null },
                    categoryId = o.get("category_id").asInt,
                )
            }
        }.onFailure { lastPublishError = it.message }.getOrNull()
    }

    /** Why the last publish failed (rate limit, validation), for the UI. */
    @Volatile var lastPublishError: String? = null
}
