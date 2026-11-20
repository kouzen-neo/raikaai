package com.raikaai.app.ui.chat

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.raikaai.app.core.engine.LiteRtEngineManager
import com.raikaai.app.core.remote.RemoteAiClient
import com.raikaai.app.core.remote.RemoteModelItem
import com.raikaai.app.core.server.ChatServerService
import com.raikaai.app.data.Settings
import com.raikaai.app.data.SettingsRepository
import com.raikaai.app.data.SamplingDefaults
import com.raikaai.app.data.remoteModelPricingToJson
import com.raikaai.app.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * On-device benchmark runner and remote-model discovery, previously embedded
 * in ChatViewModel. The ViewModel keeps thin delegating accessors so UI call
 * sites are untouched.
 */
class BenchmarkRunner(
    private val app: Application,
    private val settingsRepo: SettingsRepository,
    private val engine: LiteRtEngineManager,
    private val scope: CoroutineScope,
    private val settings: StateFlow<Settings>,
    private val isGenerating: State<Boolean>,
    private val onError: (String) -> Unit,
) {
    private val _isBenchmarking = mutableStateOf(false)
    val isBenchmarking: State<Boolean> = _isBenchmarking

    private val _latestBenchmark = mutableStateOf<BenchmarkResult?>(null)
    val latestBenchmark: State<BenchmarkResult?> = _latestBenchmark

    private val _remoteModels =
        mutableStateOf<List<RemoteModelItem>>(
            com.raikaai.app.data.parseCachedRemoteModels(settings.value.cachedRemoteModelsJson)
        )
    val remoteModels: State<List<RemoteModelItem>> = _remoteModels

    /** Forgets the fetched list (another provider was selected). */
    fun clearRemoteModels() {
        _remoteModels.value = emptyList()
    }

    private val _isFetchingRemoteModels = mutableStateOf(false)
    val isFetchingRemoteModels: State<Boolean> = _isFetchingRemoteModels

    private val _isDetectingContextWindow = mutableStateOf(false)
    val isDetectingContextWindow: State<Boolean> = _isDetectingContextWindow

    init {
        scope.launch {
            // Auto-refresh in background if remote URL is configured
            val s = settings.value
            if (s.remoteBaseUrl.isNotBlank()) {
                fetchRemoteModels(s.remoteBaseUrl, s.remoteApiKey, silent = true)
            }
        }
    }

    /**
     * Best-effort detection of the selected remote model's context window
     * (Ollama `/api/show`, OpenRouter `context_length`). On success the value
     * is stored as a per-model override; the global remote default and the
     * on-device setting are untouched.
     */
    fun detectRemoteContextWindow(modelId: String = settings.value.remoteModelId) {
        val s = settings.value
        if (s.remoteBaseUrl.isBlank() || modelId.isBlank()) {
            onError("Set the Remote Base URL and select a model first.")
            return
        }
        if (_isDetectingContextWindow.value) return
        _isDetectingContextWindow.value = true
        scope.launch {
            try {
                val detected =
                    RemoteAiClient.detectContextWindow(s.remoteBaseUrl, s.remoteApiKey, modelId)
                if (detected != null) {
                    settingsRepo.setRemoteModelContextWindow(modelId, detected)
                } else {
                    onError("This provider doesn't report the context window for $modelId. The default is used; set it manually below if needed.")
                }
            } finally {
                _isDetectingContextWindow.value = false
            }
        }
    }

    fun setRemoteModelId(id: String) {
        scope.launch {
            settingsRepo.setRemoteModelId(id)
            // Auto-detect the context window for a newly selected model, once:
            // skip when a per-model override was already stored.
            if (id.isNotBlank() &&
                com.raikaai.app.data.parseRemoteModelContextWindows(settings.value.remoteModelContextWindowsJson)[id] == null
            ) {
                detectRemoteContextWindow(id)
            }
        }
    }

    fun fetchRemoteModels(baseUrl: String, apiKey: String = "", silent: Boolean = false) {
        if (baseUrl.isBlank()) return
        _isFetchingRemoteModels.value = true
        scope.launch {
            val res = RemoteAiClient.fetchModels(baseUrl, apiKey)
            // The user may have switched provider meanwhile: drop stale lists.
            if (settings.value.remoteBaseUrl.trimEnd('/') != baseUrl.trimEnd('/')) {
                _isFetchingRemoteModels.value = false
                return@launch
            }
            res.onSuccess {
                _remoteModels.value = it
                settingsRepo.setCachedRemoteModelsJson(com.raikaai.app.data.cachedRemoteModelsToJson(it))
                if (it.isNotEmpty() && settings.value.remoteModelId.isBlank()) {
                    setRemoteModelId(it.first().id)
                }
            }.onFailure {
                if (!silent) {
                    onError("Failed to connect to remote server: ${it.message}")
                }
            }
            // Best-effort: cache per-model pricing for cost estimates (OpenRouter only).
            runCatching {
                val pricing = RemoteAiClient.fetchOpenRouterPricing(baseUrl, apiKey)
                if (pricing.isNotEmpty()) {
                    settingsRepo.setRemoteModelPricingJson(remoteModelPricingToJson(pricing))
                }
            }
            _isFetchingRemoteModels.value = false
        }
    }

    // ── LM Studio Benchmark Tool ────────────────────────────────────────

    fun runBenchmark(
        prompt: String = "Explain in 35 words why on-device AI is private and fast.",
        backendOverride: String? = null,
        onComplete: ((BenchmarkResult) -> Unit)? = null,
    ) {
        if (_isBenchmarking.value || isGenerating.value) return
        val s = settings.value
        val modelPath = ChatServerService.resolveModelPath(app, s)
        if (modelPath == null) {
            onError("Please download or select a model first to run the benchmark.")
            return
        }

        val testPrompt = prompt.ifBlank { "Explain in 35 words why on-device AI is private and fast." }
        val chosenBackend = backendOverride ?: s.backend
        val modelName = java.io.File(modelPath).name.removeSuffix(".litertlm")

        _isBenchmarking.value = true
        scope.launch(Dispatchers.Default) {
            try {
                val startTime = System.currentTimeMillis()
                var fullResponse = ""
                var totalTokens = 0

                val params =
                    LiteRtEngineManager.engineParamsFromSettings(
                        s = s,
                        modelPath = modelPath,
                        systemPrompt = "You are a concise AI benchmark responder.",
                        temperature = 0.1f,
                        topK = 20,
                        maxTokens = SamplingDefaults.BENCHMARK_MAX_TOKENS,
                        contextWindow = 2048,
                        backend = chosenBackend,
                    )

                engine.streamResponse(
                    prompt = testPrompt,
                    params = params,
                    onMetrics = { metrics ->
                        val duration = maxOf(0.05f, metrics.durationSec)
                        val rating =
                            when {
                                metrics.tokensPerSec >= 20.0f -> "Blazing Fast"
                                metrics.tokensPerSec >= 12.0f -> "Fast & Smooth"
                                metrics.tokensPerSec >= 6.0f -> "Good"
                                else -> "Moderate"
                            }
                        val result =
                            BenchmarkResult(
                                prompt = testPrompt,
                                modelName = modelName,
                                backend = metrics.backend,
                                tokensGenerated = metrics.totalTokens,
                                ttftMs = metrics.ttftMs,
                                decodeSpeedTokPerSec = metrics.tokensPerSec,
                                totalDurationSec = duration,
                                responseText = fullResponse,
                                rating = rating,
                            )
                        _latestBenchmark.value = result
                        onComplete?.invoke(result)
                    },
                ).collect { delta ->
                    fullResponse += delta
                    totalTokens++
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    KLog.e("BenchmarkRunner", "Benchmark failed", e)
                    withContext(Dispatchers.Main) {
                        onError("Benchmark failed: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            } finally {
                _isBenchmarking.value = false
            }
        }
    }
}
