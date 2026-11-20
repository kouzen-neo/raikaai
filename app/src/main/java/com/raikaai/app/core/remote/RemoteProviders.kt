package com.raikaai.app.core.remote

/**
 * A remote chat provider preset. Every preset speaks the OpenAI-compatible
 * Chat Completions API (Anthropic and Gemini expose official compatibility
 * endpoints), so one client serves all of them.
 *
 * @property editableUrl true when the user supplies the base URL (LAN Ollama, custom).
 * @property keyUrl where to create an API key; empty when no key is needed.
 */
data class RemoteProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val description: String,
    val keyUrl: String = "",
    val keyPrefix: String = "",
    val needsKey: Boolean = true,
    val editableUrl: Boolean = false,
)

object RemoteProviders {
    const val CUSTOM = "custom"
    const val OLLAMA = "ollama"

    val all: List<RemoteProvider> =
        listOf(
            RemoteProvider(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                description = "GPT models",
                keyUrl = "https://platform.openai.com/api-keys",
                keyPrefix = "sk-",
            ),
            RemoteProvider(
                id = "anthropic",
                name = "Anthropic",
                baseUrl = "https://api.anthropic.com/v1",
                description = "Claude models",
                keyUrl = "https://console.anthropic.com/settings/keys",
                keyPrefix = "sk-ant-",
            ),
            RemoteProvider(
                id = "gemini",
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                description = "Gemini models",
                keyUrl = "https://aistudio.google.com/apikey",
                keyPrefix = "AIza",
            ),
            RemoteProvider(
                id = "openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                description = "Hundreds of models, one key",
                keyUrl = "https://openrouter.ai/keys",
                keyPrefix = "sk-or-",
            ),
            RemoteProvider(
                id = "groq",
                name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                description = "Very fast open models",
                keyUrl = "https://console.groq.com/keys",
                keyPrefix = "gsk_",
            ),
            RemoteProvider(
                id = "deepseek",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1",
                description = "DeepSeek chat and reasoner",
                keyUrl = "https://platform.deepseek.com/api_keys",
                keyPrefix = "sk-",
            ),
            RemoteProvider(
                id = "mistral",
                name = "Mistral",
                baseUrl = "https://api.mistral.ai/v1",
                description = "Mistral and Codestral",
                keyUrl = "https://console.mistral.ai/api-keys",
            ),
            RemoteProvider(
                id = "xai",
                name = "xAI",
                baseUrl = "https://api.x.ai/v1",
                description = "Grok models",
                keyUrl = "https://console.x.ai",
                keyPrefix = "xai-",
            ),
            RemoteProvider(
                id = OLLAMA,
                name = "Ollama (LAN)",
                baseUrl = "http://192.168.1.100:11434",
                description = "Models on a computer in your network",
                needsKey = false,
                editableUrl = true,
            ),
            RemoteProvider(
                id = CUSTOM,
                name = "OpenAI-compatible",
                baseUrl = "",
                description = "Any server with the OpenAI API (LM Studio, vLLM, proxies)",
                needsKey = false,
                editableUrl = true,
            ),
        )

    fun byId(id: String?): RemoteProvider = all.firstOrNull { it.id == id } ?: all.first { it.id == CUSTOM }

    /** Guesses the preset for a saved base URL (for installs from before presets). */
    fun detect(baseUrl: String): RemoteProvider {
        val host = runCatching { java.net.URI(baseUrl.trim()).host.orEmpty().lowercase() }.getOrDefault("")
        all.firstOrNull { !it.editableUrl && host.isNotBlank() && host == runCatching { java.net.URI(it.baseUrl).host }.getOrNull() }
            ?.let { return it }
        if (baseUrl.contains(":11434")) return byId(OLLAMA)
        return byId(CUSTOM)
    }

    /**
     * Full URL for an API [path] such as "chat/completions". A base that
     * already ends in a version ("/v1", "/v1beta/openai", "/openai/v1") is
     * used as is; a bare host gets "/v1" (Ollama, LM Studio).
     */
    fun endpoint(
        baseUrl: String,
        path: String,
    ): String {
        val clean = baseUrl.trim().trimEnd('/')
        val versioned = Regex("/v\\d+[a-z0-9]*(/openai)?$|/openai$", RegexOption.IGNORE_CASE).containsMatchIn(clean)
        return if (versioned) "$clean/$path" else "$clean/v1/$path"
    }

    /** Auth headers for [baseUrl]. Anthropic also gets its native key/version headers. */
    fun authHeaders(
        baseUrl: String,
        apiKey: String,
    ): List<Pair<String, String>> {
        if (apiKey.isBlank()) return emptyList()
        val headers = mutableListOf("Authorization" to "Bearer $apiKey")
        if (baseUrl.contains("api.anthropic.com", ignoreCase = true)) {
            headers += "x-api-key" to apiKey
            headers += "anthropic-version" to "2023-06-01"
        }
        return headers
    }
}
