package com.raikaai.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.raikaai.app.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raikaai_settings")

/** Default system prompt used for both in-app chat and the /v1 endpoint. */
const val DEFAULT_SYSTEM_PROMPT =
    "You are a helpful assistant running fully offline on the user's phone. " +
        "Answer concisely and accurately."

data class Settings(
    // Appearance
    val themeMode: String = ChatConstants.THEME_SYSTEM, // "system" | "dark" | "light"
    val themeColor: Long = 0xFF00897BL,
    val pureBlack: Boolean = false,
    // LiteRT engine parameters
    val activeModelId: String = "",
    val customModelPath: String = "",
    val temperature: Float = SamplingDefaults.LOCAL_TEMPERATURE,
    val topK: Int = SamplingDefaults.TOP_K,
    val topP: Float = SamplingDefaults.TOP_P,
    val maxTokens: Int = SamplingDefaults.LOCAL_MAX_TOKENS,
    val contextWindowTokens: Int = 2048, // 1024 | 2048 | 4096
    val promptTemplateFormat: String = "auto", // "auto" | "gemma" | "chatml" | "llama3" | "raw"
    val backend: String = ChatConstants.BACKEND_GPU, // "GPU" | "CPU"
    val verboseLogs: Boolean = false,
    val perModelParamsJson: String = "{}",
    // Model source & Remote backend (Ollama / OpenAI API)
    val modelSource: String = ChatConstants.SOURCE_LOCAL, // "local" | "remote"
    val remoteBaseUrl: String = "http://192.168.1.100:11434",
    val remoteApiKey: String = "",
    val remoteModelId: String = "",
    val remoteImageModelId: String = "", // image-generation model for /v1/images/generations
    /**
     * Remote context window. Independent from the on-device
     * [contextWindowTokens] so switching back to local restores the local
     * value untouched. Per-model overrides live in
     * [remoteModelContextWindowsJson].
     */
    val remoteContextWindowTokens: Int = 8192,
    /** JSON object: remote model id → context window tokens override. */
    val remoteModelContextWindowsJson: String = "{}",
    /** JSON object: remote model id → {"p": prompt $/token, "c": completion $/token}. */
    val remoteModelPricingJson: String = "{}",
    /** Cumulative estimated remote API spend, in USD micro-units (1e-6). */
    val remoteSpendMicros: Long = 0L,
    /** JSON array of cached [com.raikaai.app.core.remote.RemoteModelItem]. */
    val cachedRemoteModelsJson: String = "[]",
    // Compare mode: run each prompt on the active model (A) and a challenger (B)
    /** Master switch for compare mode. */
    val compareMode: Boolean = false,
    /** Challenger model source: "local" | "remote" | "" (unset). */
    val compareSource: String = "",
    /**
     * Challenger model id: remote model id, or absolute local model path
     * ("" = active model of [compareSource]).
     */
    val compareModelId: String = "",
    // MCP (Model Context Protocol) client
    /** Master switch for MCP tool use in chat. Default off: tools only run with explicit setup. */
    val mcpEnabled: Boolean = false,
    /** JSON array of [com.raikaai.app.mcp.McpServerConfig]. */
    val mcpServersJson: String = "[]",
    // Model downloads
    val huggingFaceToken: String = "",
    // Embedded OpenAI-compatible server
    val serverPort: Int = 8080,
    val serverBindAll: Boolean = false,
    val serverAuthToken: String = "",
    // Chat behavior & UI
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val customPersonasJson: String = "",
    val enableThinking: Boolean = true,
    val showTokensPerSec: Boolean = true,
    val autoScroll: Boolean = true,
    val sendOnEnter: Boolean = false,
    val keepAwake: Boolean = false,
    // On-device RAG (knowledge documents)
    val ragEnabled: Boolean = true,
    val useChatMemory: Boolean = true,
    // Context & capability flags
    val autoCompress: Boolean = true,
    val captureArtifacts: Boolean = true,
    val enableVision: Boolean = true,
    val enableWebSearch: Boolean = false,
    // Linux container (Alpine via PRoot) — Fase 0: status only
    val alpineInstalled: Boolean = false,
    val alpineVersion: String = "",
    /** YOLO mode: auto-approve ASK-policy sandbox commands (DENY still blocked). */
    val containerYolo: Boolean = false,
    /** JSON array of normalized commands always auto-approved (ASK-policy). */
    val containerAlwaysAllowJson: String = "[]",
    // Runtime boundary: on-device only, or KazeTerm guest hosting Pi.
    /** One of the RuntimeMode names. Defaults to the container-free app. */
    val runtimeMode: String = "ON_DEVICE",
    /** Host of the Pi adapter running inside the KazeTerm guest. */
    val piAdapterHost: String = "127.0.0.1",
    /** Port of the Pi adapter running inside the KazeTerm guest. */
    val piAdapterPort: Int = 8787,
    /** Bearer token for the Pi adapter, read from encrypted preferences. */
    val piAdapterToken: String = "",
)

/**
 * Parses the per-remote-model context window overrides JSON
 * (`{ "<modelId>": <tokens>, ... }`). Malformed entries are ignored.
 */
fun parseRemoteModelContextWindows(json: String): Map<String, Int> {
    return try {
        val obj = JSONObject(json)
        val out = mutableMapOf<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optInt(k, -1)
            if (k.isNotBlank() && v > 0) out[k] = v
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }
}

/** Serializes per-remote-model context window overrides back to JSON. */
fun remoteModelContextWindowsToJson(map: Map<String, Int>): String {
    return JSONObject().apply {
        for ((k, v) in map) put(k, v)
    }.toString()
}

/**
 * Parses cached per-model pricing JSON
 * (`{ "<modelId>": {"p": <$/token prompt>, "c": <$/token completion>}, ... }`).
 * Malformed entries are ignored.
 */
fun parseRemoteModelPricing(json: String): Map<String, com.raikaai.app.core.remote.ModelPricing> {
    return try {
        val obj = JSONObject(json)
        val out = mutableMapOf<String, com.raikaai.app.core.remote.ModelPricing>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val entry = obj.optJSONObject(k) ?: continue
            val p = entry.optDouble("p", -1.0)
            val c = entry.optDouble("c", -1.0)
            if (k.isNotBlank() && p >= 0 && c >= 0 && (p > 0 || c > 0)) {
                out[k] = com.raikaai.app.core.remote.ModelPricing(p, c)
            }
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }
}

/** Serializes the per-model pricing map back to JSON. */
fun remoteModelPricingToJson(map: Map<String, com.raikaai.app.core.remote.ModelPricing>): String {
    return JSONObject().apply {
        for ((k, v) in map) {
            put(k, JSONObject().put("p", v.promptPerToken).put("c", v.completionPerToken))
        }
    }.toString()
}

/** Parses cached remote models JSON array. */
fun parseCachedRemoteModels(json: String): List<com.raikaai.app.core.remote.RemoteModelItem> {
    if (json.isBlank() || json == "[]") return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        val list = mutableListOf<com.raikaai.app.core.remote.RemoteModelItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.optString("id", "")
            val name = obj.optString("name", id)
            val provider = obj.optString("provider", "Remote")
            val desc = obj.optString("description", "")
            if (id.isNotBlank()) {
                list.add(com.raikaai.app.core.remote.RemoteModelItem(id = id, name = name, provider = provider, description = desc))
            }
        }
        list
    }.getOrDefault(emptyList())
}

/** Serializes remote model items to JSON array. */
fun cachedRemoteModelsToJson(list: List<com.raikaai.app.core.remote.RemoteModelItem>): String {
    val arr = JSONArray()
    list.forEach { item ->
        val obj = JSONObject().apply {
            put("id", item.id)
            put("name", item.name)
            put("provider", item.provider)
            put("description", item.description)
        }
        arr.put(obj)
    }
    return arr.toString()
}

/**
 * Effective context window for a remote model: per-model override wins,
 * otherwise the global remote default. Never confused with the on-device
 * [Settings.contextWindowTokens].
 */
fun Settings.effectiveRemoteContextWindow(modelId: String): Int {
    val override = parseRemoteModelContextWindows(remoteModelContextWindowsJson)[modelId]
    return (override ?: remoteContextWindowTokens).coerceAtLeast(1024)
}

class SettingsRepository(
    private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_COLOR = longPreferencesKey("theme_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val ACTIVE_MODEL_ID = stringPreferencesKey("litert_active_model_id")
        val CUSTOM_MODEL_PATH = stringPreferencesKey("litert_custom_model_path")
        val TEMPERATURE = floatPreferencesKey("litert_temperature")
        val TOP_K = intPreferencesKey("litert_top_k")
        val TOP_P = floatPreferencesKey("litert_top_p")
        val MAX_TOKENS = intPreferencesKey("litert_max_tokens")
        val CONTEXT_WINDOW_TOKENS = intPreferencesKey("litert_context_window_tokens")
        val PROMPT_TEMPLATE_FORMAT = stringPreferencesKey("litert_prompt_template_format")
        val BACKEND = stringPreferencesKey("litert_backend")
        val VERBOSE_LOGS = booleanPreferencesKey("litert_verbose")
        val PER_MODEL_PARAMS = stringPreferencesKey("litert_per_model_params_json")
        val MODEL_SOURCE = stringPreferencesKey("model_source")
        val REMOTE_BASE_URL = stringPreferencesKey("remote_base_url")
        val REMOTE_MODEL_ID = stringPreferencesKey("remote_model_id")
        val REMOTE_IMAGE_MODEL_ID = stringPreferencesKey("remote_image_model_id")
        val REMOTE_CONTEXT_WINDOW_TOKENS = intPreferencesKey("remote_context_window_tokens")
        val REMOTE_MODEL_CONTEXT_WINDOWS = stringPreferencesKey("remote_model_context_windows_json")
        val REMOTE_MODEL_PRICING = stringPreferencesKey("remote_model_pricing_json")
        val REMOTE_SPEND_MICROS = longPreferencesKey("remote_spend_micros")
        val CACHED_REMOTE_MODELS = stringPreferencesKey("cached_remote_models_json")
        val COMPARE_MODE = booleanPreferencesKey("compare_mode")
        val COMPARE_SOURCE = stringPreferencesKey("compare_source")
        val COMPARE_MODEL_ID = stringPreferencesKey("compare_model_id")
        val MCP_ENABLED = booleanPreferencesKey("mcp_enabled")
        val MCP_SERVERS_JSON = stringPreferencesKey("mcp_servers_json")
        val SERVER_PORT = intPreferencesKey("server_port")
        val SERVER_BIND_ALL = booleanPreferencesKey("server_bind_all")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val CUSTOM_PERSONAS = stringPreferencesKey("custom_personas_json")
        val ENABLE_THINKING = booleanPreferencesKey("enable_thinking")
        val SHOW_TPS = booleanPreferencesKey("show_tokens_per_sec")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val SEND_ON_ENTER = booleanPreferencesKey("send_on_enter")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val RAG_ENABLED = booleanPreferencesKey("rag_enabled")
        val USE_CHAT_MEMORY = booleanPreferencesKey("use_chat_memory")
        val AUTO_COMPRESS = booleanPreferencesKey("auto_compress")
        val CAPTURE_ARTIFACTS = booleanPreferencesKey("capture_artifacts")
        val ENABLE_VISION = booleanPreferencesKey("enable_vision")
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        val ALPINE_INSTALLED = booleanPreferencesKey("alpine_installed")
        val ALPINE_VERSION = stringPreferencesKey("alpine_version")
        val CONTAINER_YOLO = booleanPreferencesKey("container_yolo")
        val CONTAINER_ALWAYS_ALLOW = stringPreferencesKey("container_always_allow_json")
        val RUNTIME_MODE = stringPreferencesKey("runtime_mode")
        val PI_ADAPTER_HOST = stringPreferencesKey("pi_adapter_host")
        val PI_ADAPTER_PORT = intPreferencesKey("pi_adapter_port")

        // Legacy DataStore keys for sensitive tokens. Tokens used to live here in
        // plaintext; they are migrated to EncryptedSharedPreferences on first read
        // and then removed. Do NOT write to these keys anymore.
        val LEGACY_REMOTE_API_KEY = stringPreferencesKey("remote_api_key")
        val LEGACY_HF_TOKEN = stringPreferencesKey("huggingface_token")
        val LEGACY_SERVER_AUTH_TOKEN = stringPreferencesKey("server_auth_token")
    }

    companion object {
        private const val TAG = "SettingsRepository"

        /** Emits when any secure token changed, so other repository instances can refresh. */
        private val _secureTokenChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val secureTokenChanges: Flow<Unit> get() = _secureTokenChanges
        private const val SECURE_PREFS_NAME = "raikaai_secrets"
        private const val SEC_REMOTE_API_KEY = "remote_api_key"
        private const val SEC_HF_TOKEN = "huggingface_token"
        private const val SEC_SERVER_AUTH_TOKEN = "server_auth_token"

        private const val SEC_PI_ADAPTER_TOKEN = "pi_adapter_token"

        /** Set in the plaintext fallback prefs once the fallback has been used (B17/S5). */
        private const val FALLBACK_USED_FLAG = "fallback_used"

        private val SEC_TOKEN_KEYS = listOf(SEC_REMOTE_API_KEY, SEC_HF_TOKEN, SEC_SERVER_AUTH_TOKEN, SEC_PI_ADAPTER_TOKEN)
    }

    /**
     * Runs a one-time migration exactly once *until it reports full success*.
     * [block] must return true only when every step succeeded; a false return
     * or a thrown exception leaves the tracker incomplete so a later call
     * retries instead of silently dropping the migration (B5).
     *
     * Pure logic, no Android dependencies — covered by unit tests.
     */
    internal class MigrationTracker {
        private val mutex = Mutex()

        @Volatile
        var completed = false
            private set

        suspend fun runIfNeeded(block: suspend () -> Boolean) {
            if (completed) return
            mutex.withLock {
                if (completed) return
                val ok =
                    try {
                        block()
                    } catch (t: Throwable) {
                        KLog.e("SettingsRepository", "Token migration failed", t)
                        false
                    }
                if (ok) completed = true
            }
        }
    }

    /**
     * Encrypted storage for sensitive tokens (remote API key, Hugging Face token,
     * embedded-server bearer token). Falls back to plain SharedPreferences only if
     * the Android Keystore is unavailable, so the app keeps working on odd devices.
     *
     * Kept behind a volatile cache (not `by lazy`) so a later successful
     * Keystore can replace the plaintext fallback (B17/S5).
     */
    @Volatile
    private var securePrefsCache: SharedPreferences? = null
    private val securePrefsInitLock = Any()

    private fun securePrefs(): SharedPreferences =
        securePrefsCache ?: synchronized(securePrefsInitLock) {
            securePrefsCache ?: createTokenStore().also { securePrefsCache = it }
        }

    private fun fallbackPrefs(): SharedPreferences =
        context.applicationContext.getSharedPreferences("${SECURE_PREFS_NAME}_fallback", Context.MODE_PRIVATE)

    private fun createEncryptedPrefs(): SharedPreferences {
        val appContext = context.applicationContext
        // MasterKeys (plural) is the stable API in security-crypto 1.0.0;
        // the singular MasterKey builder only exists in 1.1.0-alpha+.
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        // NOTE: in security-crypto 1.0.0 the String-alias overload takes
        // (fileName, masterKeyAlias, context) — NOT (context, fileName, ...).
        return EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun createTokenStore(): SharedPreferences =
        try {
            createEncryptedPrefs()
        } catch (t: Throwable) {
            KLog.w(TAG, "EncryptedSharedPreferences unavailable, using plain fallback: ${t.message}")
            fallbackPrefs().edit().putBoolean(FALLBACK_USED_FLAG, true).apply()
            fallbackPrefs()
        }

    /**
     * If tokens are sitting in the plaintext fallback (the Keystore was broken
     * at some point), retry the encrypted store on every start. When the
     * Keystore works again, move the tokens over and wipe the fallback (B17/S5).
     */
    private val fallbackUpgradeMutex = Mutex()

    private suspend fun tryUpgradeFallbackStore() {
        val fb = fallbackPrefs()
        if (!fb.getBoolean(FALLBACK_USED_FLAG, false)) return
        fallbackUpgradeMutex.withLock {
            if (!fb.getBoolean(FALLBACK_USED_FLAG, false)) return
            val encrypted =
                try {
                    createEncryptedPrefs()
                } catch (t: Throwable) {
                    KLog.w(TAG, "Encrypted store still unavailable; keeping plaintext fallback: ${t.message}")
                    return
                }
            val editor = encrypted.edit()
            var movedAny = false
            for (key in SEC_TOKEN_KEYS) {
                val value = fb.getString(key, null)
                if (!value.isNullOrEmpty()) {
                    editor.putString(key, value)
                    movedAny = true
                }
            }
            // Synchronous commit: the fallback is wiped right after, so the
            // copy must be durable first.
            if (movedAny && !editor.commit()) {
                KLog.e(TAG, "Fallback token re-migration commit failed; will retry next start")
                return
            }
            fb.edit().clear().commit()
            synchronized(securePrefsInitLock) { securePrefsCache = encrypted }
            KLog.d(TAG, "Migrated tokens from plaintext fallback into encrypted storage")
        }
    }

    // Bumped every time a token is written so settingsFlow re-emits even though
    // the DataStore itself did not change.
    private val tokenBump = MutableStateFlow(0)
    private val tokenMigration = MigrationTracker()

    /**
     * One-time migration: copy any tokens still sitting in plaintext DataStore
     * into the encrypted store, then delete them from DataStore.
     *
     * Runs eagerly via [runMigrationsEagerly], NEVER from inside a
     * `dataStore.data` collector: calling `edit()` while collecting is
     * re-entrant and can stall DataStore.
     */
    private suspend fun migrateTokensIfNeeded() {
        // Opportunistically recover from the plaintext fallback first (B17/S5).
        tryUpgradeFallbackStore()
        tokenMigration.runIfNeeded {
            val prefs = context.dataStore.data.first()
            // NOTE: use the Pair() constructor, not the `to` infix — DataStore
            // defines its own `Key<T>.to(value)` returning Preferences.Pair,
            // which mapOf() cannot infer K,V from.
            val legacy: Map<Preferences.Key<String>, String> =
                mapOf(
                    Pair(Keys.LEGACY_REMOTE_API_KEY, SEC_REMOTE_API_KEY),
                    Pair(Keys.LEGACY_HF_TOKEN, SEC_HF_TOKEN),
                    Pair(Keys.LEGACY_SERVER_AUTH_TOKEN, SEC_SERVER_AUTH_TOKEN),
                )
            val editor = securePrefs().edit()
            var movedAny = false
            for ((oldKey, newKey) in legacy) {
                val value = prefs[oldKey]
                if (!value.isNullOrEmpty()) {
                    editor.putString(newKey, value)
                    movedAny = true
                }
            }
            // Synchronous commit (not apply()): the DataStore source keys are
            // removed right after, so the copy must be durable first (B16).
            // A failed commit returns false so the next start retries (B5).
            if (movedAny && !editor.commit()) {
                KLog.e(TAG, "Token migration commit failed; will retry next start")
                return@runIfNeeded false
            }
            if (movedAny) {
                KLog.d(TAG, "Migrated sensitive tokens from DataStore to encrypted storage")
            }
            if (legacy.keys.any { prefs.contains(it) }) {
                context.dataStore.edit {
                    it.remove(Keys.LEGACY_REMOTE_API_KEY)
                    it.remove(Keys.LEGACY_HF_TOKEN)
                    it.remove(Keys.LEGACY_SERVER_AUTH_TOKEN)
                }
            }
            true
        }
    }

    /**
     * Runs the one-time token migrations eagerly on a background thread.
     * Call once at startup (before the settings flows are collected) — the
     * flows themselves must never trigger `edit()`, which would re-enter
     * DataStore collection and risk stalling it.
     */
    fun runMigrationsEagerly(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            migrateTokensIfNeeded()
            mcpTokenMigration.runIfNeeded { migrateMcpTokensFromJson() }
        }
    }

    val settingsFlow: Flow<Settings> =
        combine(context.dataStore.data, tokenBump) { prefs, _ ->
            withContext(Dispatchers.IO) {
                Settings(
                    themeMode = prefs[Keys.THEME_MODE] ?: ChatConstants.THEME_SYSTEM,
                    themeColor = prefs[Keys.THEME_COLOR] ?: 0xFF00897BL,
                    pureBlack = prefs[Keys.PURE_BLACK] ?: false,
                    activeModelId = prefs[Keys.ACTIVE_MODEL_ID] ?: "",
                    customModelPath = prefs[Keys.CUSTOM_MODEL_PATH] ?: "",
                    temperature = (prefs[Keys.TEMPERATURE] ?: SamplingDefaults.LOCAL_TEMPERATURE).coerceIn(0f, 2f),
                    topK = prefs[Keys.TOP_K] ?: SamplingDefaults.TOP_K,
                    topP = prefs[Keys.TOP_P] ?: SamplingDefaults.TOP_P,
                    maxTokens = prefs[Keys.MAX_TOKENS] ?: SamplingDefaults.LOCAL_MAX_TOKENS,
                    contextWindowTokens = prefs[Keys.CONTEXT_WINDOW_TOKENS] ?: 2048,
                    promptTemplateFormat = prefs[Keys.PROMPT_TEMPLATE_FORMAT] ?: "auto",
                    backend = prefs[Keys.BACKEND] ?: ChatConstants.BACKEND_GPU,
                    verboseLogs = prefs[Keys.VERBOSE_LOGS] ?: false,
                    perModelParamsJson = prefs[Keys.PER_MODEL_PARAMS] ?: "{}",
                    modelSource = prefs[Keys.MODEL_SOURCE] ?: ChatConstants.SOURCE_LOCAL,
                    remoteBaseUrl = prefs[Keys.REMOTE_BASE_URL] ?: "http://192.168.1.100:11434",
                    remoteApiKey = securePrefs().getString(SEC_REMOTE_API_KEY, "") ?: "",
                    remoteModelId = prefs[Keys.REMOTE_MODEL_ID] ?: "",
                    remoteImageModelId = prefs[Keys.REMOTE_IMAGE_MODEL_ID] ?: "",
                    remoteContextWindowTokens = prefs[Keys.REMOTE_CONTEXT_WINDOW_TOKENS] ?: 8192,
                    remoteModelContextWindowsJson = prefs[Keys.REMOTE_MODEL_CONTEXT_WINDOWS] ?: "{}",
                    remoteModelPricingJson = prefs[Keys.REMOTE_MODEL_PRICING] ?: "{}",
                    remoteSpendMicros = prefs[Keys.REMOTE_SPEND_MICROS] ?: 0L,
                    cachedRemoteModelsJson = prefs[Keys.CACHED_REMOTE_MODELS] ?: "[]",
                    compareMode = prefs[Keys.COMPARE_MODE] ?: false,
                    compareSource = prefs[Keys.COMPARE_SOURCE] ?: "",
                    compareModelId = prefs[Keys.COMPARE_MODEL_ID] ?: "",
                    mcpEnabled = prefs[Keys.MCP_ENABLED] ?: false,
                    mcpServersJson = prefs[Keys.MCP_SERVERS_JSON] ?: "[]",
                    huggingFaceToken = securePrefs().getString(SEC_HF_TOKEN, "") ?: "",
                    serverPort = (prefs[Keys.SERVER_PORT] ?: 8080).coerceIn(1024, 65535),
                    serverBindAll = prefs[Keys.SERVER_BIND_ALL] ?: false,
                    serverAuthToken = securePrefs().getString(SEC_SERVER_AUTH_TOKEN, "") ?: "",
                    systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
                    customPersonasJson = prefs[Keys.CUSTOM_PERSONAS] ?: "",
                    enableThinking = prefs[Keys.ENABLE_THINKING] ?: true,
                    showTokensPerSec = prefs[Keys.SHOW_TPS] ?: true,
                    autoScroll = prefs[Keys.AUTO_SCROLL] ?: true,
                    sendOnEnter = prefs[Keys.SEND_ON_ENTER] ?: false,
                    keepAwake = prefs[Keys.KEEP_AWAKE] ?: false,
                    ragEnabled = prefs[Keys.RAG_ENABLED] ?: true,
                    useChatMemory = prefs[Keys.USE_CHAT_MEMORY] ?: true,
                    autoCompress = prefs[Keys.AUTO_COMPRESS] ?: true,
                    captureArtifacts = prefs[Keys.CAPTURE_ARTIFACTS] ?: true,
                    enableVision = prefs[Keys.ENABLE_VISION] ?: true,
                    enableWebSearch = prefs[Keys.ENABLE_WEB_SEARCH] ?: false,
                    alpineInstalled = prefs[Keys.ALPINE_INSTALLED] ?: false,
                    alpineVersion = prefs[Keys.ALPINE_VERSION] ?: "",
                    containerYolo = prefs[Keys.CONTAINER_YOLO] ?: false,
                    containerAlwaysAllowJson = prefs[Keys.CONTAINER_ALWAYS_ALLOW] ?: "[]",
                    runtimeMode = prefs[Keys.RUNTIME_MODE] ?: "ON_DEVICE",
                    piAdapterHost = prefs[Keys.PI_ADAPTER_HOST] ?: "127.0.0.1",
                    piAdapterPort = (prefs[Keys.PI_ADAPTER_PORT] ?: 8787).coerceIn(1024, 65535),
                    piAdapterToken = securePrefs().getString(SEC_PI_ADAPTER_TOKEN, "") ?: "",
                )
            }
        }

    private fun putSecureToken(
        key: String,
        value: String,
    ) {
        try {
            securePrefs().edit().putString(key, value).apply()
        } catch (t: Throwable) {
            KLog.e(TAG, "Failed to store token in encrypted storage", t)
            // Don't bump: collectors must not re-read a token that was never stored (B30).
            return
        }
        // Re-emit settingsFlow so collectors observe the new token value.
        // update{} is atomic; the old read-modify-write could lose increments (B30).
        tokenBump.update { it + 1 }
        _secureTokenChanges.tryEmit(Unit)
    }

    suspend fun setCustomPersonasJson(value: String) = edit { it[Keys.CUSTOM_PERSONAS] = value }

    suspend fun setThemeMode(value: String) = edit { it[Keys.THEME_MODE] = value }

    suspend fun setThemeColor(value: Long) = edit { it[Keys.THEME_COLOR] = value }

    suspend fun setPureBlack(value: Boolean) = edit { it[Keys.PURE_BLACK] = value }

    suspend fun setActiveModelId(value: String) = edit { it[Keys.ACTIVE_MODEL_ID] = value }

    suspend fun setCustomModelPath(value: String) = edit { it[Keys.CUSTOM_MODEL_PATH] = value }

    suspend fun setTemperature(value: Float) = edit { it[Keys.TEMPERATURE] = value.coerceIn(0f, 2f) }

    suspend fun setTopK(value: Int) = edit { it[Keys.TOP_K] = value }

    suspend fun setTopP(value: Float) = edit { it[Keys.TOP_P] = value }

    suspend fun setMaxTokens(value: Int) = edit { it[Keys.MAX_TOKENS] = value }

    suspend fun setContextWindowTokens(value: Int) = edit {
        it[Keys.CONTEXT_WINDOW_TOKENS] = value
        it[Keys.REMOTE_CONTEXT_WINDOW_TOKENS] = value
    }

    suspend fun setPromptTemplateFormat(value: String) = edit { it[Keys.PROMPT_TEMPLATE_FORMAT] = value }

    suspend fun setBackend(value: String) = edit { it[Keys.BACKEND] = value }

    suspend fun setVerboseLogs(value: Boolean) = edit { it[Keys.VERBOSE_LOGS] = value }

    suspend fun setPerModelParamsJson(value: String) = edit { it[Keys.PER_MODEL_PARAMS] = value }

    suspend fun setModelSource(value: String) = edit { it[Keys.MODEL_SOURCE] = value }

    suspend fun setRemoteBaseUrl(value: String) = edit { it[Keys.REMOTE_BASE_URL] = value }

    suspend fun setRemoteApiKey(value: String) = putSecureToken(SEC_REMOTE_API_KEY, value)

    suspend fun setRemoteModelId(value: String) = edit { it[Keys.REMOTE_MODEL_ID] = value }

    suspend fun setRemoteImageModelId(value: String) = edit { it[Keys.REMOTE_IMAGE_MODEL_ID] = value }

    suspend fun setRemoteContextWindowTokens(value: Int) =
        edit { it[Keys.REMOTE_CONTEXT_WINDOW_TOKENS] = value.coerceAtLeast(1024) }

    /** Stores/updates the per-model context window override for a remote model. */
    suspend fun setRemoteModelContextWindow(modelId: String, tokens: Int) {
        if (modelId.isBlank()) return
        edit { prefs ->
            val map = parseRemoteModelContextWindows(prefs[Keys.REMOTE_MODEL_CONTEXT_WINDOWS] ?: "{}").toMutableMap()
            map[modelId] = tokens.coerceAtLeast(1024)
            prefs[Keys.REMOTE_MODEL_CONTEXT_WINDOWS] = remoteModelContextWindowsToJson(map)
        }
    }

    /** Replaces the cached per-model pricing map (model id → per-token USD). */
    suspend fun setRemoteModelPricingJson(json: String) =
        edit { it[Keys.REMOTE_MODEL_PRICING] = json.take(200_000) }

    /** Replaces the cached remote models list. */
    suspend fun setCachedRemoteModelsJson(json: String) =
        edit { it[Keys.CACHED_REMOTE_MODELS] = json.take(500_000) }

    /** Adds to the cumulative estimated remote spend (USD micro-units). */
    suspend fun addRemoteSpendMicros(deltaMicros: Long) {
        if (deltaMicros <= 0) return
        edit { prefs ->
            val cur = prefs[Keys.REMOTE_SPEND_MICROS] ?: 0L
            prefs[Keys.REMOTE_SPEND_MICROS] = (cur + deltaMicros).coerceAtMost(Long.MAX_VALUE / 2)
        }
    }

    /** Resets the cumulative estimated remote spend to zero. */
    suspend fun resetRemoteSpend() = edit { it[Keys.REMOTE_SPEND_MICROS] = 0L }

    /** Master switch for compare mode (each prompt runs on two models). */
    suspend fun setCompareMode(enabled: Boolean) = edit { it[Keys.COMPARE_MODE] = enabled }

    /** Sets the challenger model for compare mode. */
    suspend fun setCompareChallenger(source: String, modelId: String) =
        edit {
            it[Keys.COMPARE_SOURCE] = source
            it[Keys.COMPARE_MODEL_ID] = modelId
        }

    /** Master switch for MCP tool use in chat. */
    suspend fun setMcpEnabled(enabled: Boolean) = edit { it[Keys.MCP_ENABLED] = enabled }

    /** Persists the MCP server list. Prefer the typed helpers below. */
    suspend fun setMcpServersJson(json: String) = edit { it[Keys.MCP_SERVERS_JSON] = json }

    /** Adds or replaces an MCP server config (matched by id).
     *
     * The bearer token is split out of the persisted JSON and kept in
     * EncryptedSharedPreferences (same pattern as the remote API key):
     * - [authToken] == null → leave any stored token untouched (edit dialog
     *   left the field blank while a token was already saved),
     * - blank → delete the stored token,
     * - otherwise → store the new token.
     */
    suspend fun upsertMcpServer(
        server: com.raikaai.app.mcp.McpServerConfig,
        authToken: String? = null,
    ) {
        if (authToken != null) {
            val key = mcpTokenKey(server.id)
            if (authToken.isBlank()) {
                try {
                    securePrefs().edit().remove(key).apply()
                } catch (t: Throwable) {
                    KLog.e(TAG, "Failed to clear MCP token", t)
                }
            } else {
                putSecureToken(key, authToken.trim())
            }
        }
        val servers = currentMcpServers().toMutableList()
        val stripped = server.copy(authToken = "", hasToken = false)
        val idx = servers.indexOfFirst { it.id == stripped.id }
        if (idx >= 0) servers[idx] = stripped else servers.add(stripped)
        setMcpServersJson(com.raikaai.app.mcp.McpManager.serversToJson(servers))
    }

    /** Removes an MCP server config by id, including its stored token. */
    suspend fun removeMcpServer(serverId: String) {
        try {
            securePrefs().edit().remove(mcpTokenKey(serverId)).apply()
        } catch (t: Throwable) {
            KLog.e(TAG, "Failed to clear MCP token", t)
        }
        tokenBump.update { it + 1 }
        val servers = currentMcpServers().filter { it.id != serverId }
        setMcpServersJson(com.raikaai.app.mcp.McpManager.serversToJson(servers))
    }

    /** Encrypted-storage key for one MCP server's bearer token. */
    private fun mcpTokenKey(serverId: String) = "mcp_token_$serverId"

    private val mcpTokenMigration = MigrationTracker()

    /**
     * One-time migration: tokens that were briefly persisted inside the
     * plaintext MCP JSON move to EncryptedSharedPreferences and are stripped
     * from the JSON (same "migrate then delete" pattern as other tokens).
     */
    private suspend fun migrateMcpTokensFromJson(): Boolean {
        val prefs = context.dataStore.data.first()
        val servers =
            com.raikaai.app.mcp.McpManager.serversFromJson(
                prefs[Keys.MCP_SERVERS_JSON] ?: "[]",
            )
        val withTokens = servers.filter { it.authToken.isNotBlank() }
        if (withTokens.isEmpty()) return true
        val editor = securePrefs().edit()
        withTokens.forEach { editor.putString(mcpTokenKey(it.id), it.authToken) }
        // Synchronous commit: the JSON source is stripped right after, so the
        // copy must be durable first.
        if (!editor.commit()) {
            KLog.e(TAG, "MCP token migration commit failed; will retry next start")
            return false
        }
        val stripped = servers.map { it.copy(authToken = "") }
        edit { it[Keys.MCP_SERVERS_JSON] = com.raikaai.app.mcp.McpManager.serversToJson(stripped) }
        KLog.d(TAG, "Migrated ${withTokens.size} MCP token(s) into encrypted storage")
        return true
    }

    /**
     * MCP server configs with bearer tokens re-hydrated from encrypted
     * storage. This is the flow the chat layer consumes; the raw
     * [Settings.mcpServersJson] never carries tokens.
     */
    val mcpServersFlow: Flow<List<com.raikaai.app.mcp.McpServerConfig>> =
        combine(context.dataStore.data, tokenBump) { prefs, _ ->
            val json = prefs[Keys.MCP_SERVERS_JSON] ?: "[]"
            com.raikaai.app.mcp.McpManager.serversFromJson(json).map { server ->
                val token =
                    try {
                        securePrefs().getString(mcpTokenKey(server.id), "").orEmpty()
                    } catch (t: Throwable) {
                        KLog.w(TAG, "Failed to read MCP token: ${t.message}")
                        ""
                    }
                server.copy(authToken = token, hasToken = token.isNotBlank())
            }
        }.distinctUntilChanged()

    /** Reads the currently persisted MCP server list (suspend helper for callers without a Flow). */
    suspend fun currentMcpServers(): List<com.raikaai.app.mcp.McpServerConfig> =
        com.raikaai.app.mcp.McpManager.serversFromJson(
            context.dataStore.data.first()[Keys.MCP_SERVERS_JSON] ?: "[]",
        )

    /** Removes the per-model override so the global remote default applies again. */
    suspend fun clearRemoteModelContextWindow(modelId: String) {
        if (modelId.isBlank()) return
        edit { prefs ->
            val map = parseRemoteModelContextWindows(prefs[Keys.REMOTE_MODEL_CONTEXT_WINDOWS] ?: "{}").toMutableMap()
            map.remove(modelId)
            prefs[Keys.REMOTE_MODEL_CONTEXT_WINDOWS] = remoteModelContextWindowsToJson(map)
        }
    }

    suspend fun setHuggingFaceToken(value: String) = putSecureToken(SEC_HF_TOKEN, value)

    suspend fun setServerPort(value: Int) = edit { it[Keys.SERVER_PORT] = value.coerceIn(1024, 65535) }

    suspend fun setServerBindAll(value: Boolean) = edit { it[Keys.SERVER_BIND_ALL] = value }

    suspend fun setServerAuthToken(value: String) = putSecureToken(SEC_SERVER_AUTH_TOKEN, value)

    suspend fun setRuntimeMode(value: String) = edit { it[Keys.RUNTIME_MODE] = value }

    suspend fun setPiAdapterHost(value: String) = edit { it[Keys.PI_ADAPTER_HOST] = value }

    suspend fun setPiAdapterPort(value: Int) = edit { it[Keys.PI_ADAPTER_PORT] = value.coerceIn(1024, 65535) }

    suspend fun setPiAdapterToken(value: String) = putSecureToken(SEC_PI_ADAPTER_TOKEN, value)

    suspend fun setSystemPrompt(value: String) = edit { it[Keys.SYSTEM_PROMPT] = value }

    suspend fun setEnableThinking(value: Boolean) = edit { it[Keys.ENABLE_THINKING] = value }

    suspend fun setShowTokensPerSec(value: Boolean) = edit { it[Keys.SHOW_TPS] = value }

    suspend fun setAutoScroll(value: Boolean) = edit { it[Keys.AUTO_SCROLL] = value }

    suspend fun setSendOnEnter(value: Boolean) = edit { it[Keys.SEND_ON_ENTER] = value }

    suspend fun setKeepAwake(value: Boolean) = edit { it[Keys.KEEP_AWAKE] = value }

    suspend fun setRagEnabled(value: Boolean) = edit { it[Keys.RAG_ENABLED] = value }

    suspend fun setUseChatMemory(value: Boolean) = edit { it[Keys.USE_CHAT_MEMORY] = value }

    suspend fun setAutoCompress(value: Boolean) = edit { it[Keys.AUTO_COMPRESS] = value }

    suspend fun setCaptureArtifacts(value: Boolean) = edit { it[Keys.CAPTURE_ARTIFACTS] = value }

    suspend fun setEnableVision(value: Boolean) = edit { it[Keys.ENABLE_VISION] = value }

    suspend fun setEnableWebSearch(value: Boolean) = edit { it[Keys.ENABLE_WEB_SEARCH] = value }

    /** Fase 0: container install status flag (filesystem check lands in Fase 1). */
    suspend fun setAlpineInstalled(value: Boolean) = edit { it[Keys.ALPINE_INSTALLED] = value }

    suspend fun setAlpineVersion(value: String) = edit { it[Keys.ALPINE_VERSION] = value.take(32) }

    /** YOLO mode: auto-approve ASK-policy sandbox commands. DENY-policy stays blocked. */
    suspend fun setContainerYolo(value: Boolean) = edit { it[Keys.CONTAINER_YOLO] = value }

    /** Replaces the always-approve command list (normalized commands JSON array). */
    suspend fun setContainerAlwaysAllowJson(json: String) =
        edit { it[Keys.CONTAINER_ALWAYS_ALLOW] = json.take(50_000) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
