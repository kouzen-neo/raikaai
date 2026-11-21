package com.raikaai.app.core.engine

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.raikaai.app.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton wrapper around Google AI Edge LiteRT-LM.
 *
 * Ported from KZKT's LiteRtInferenceEngine with one key addition: token-level
 * streaming ([streamResponse]) so both the in-app chat UI and the embedded
 * OpenAI-compatible /v1 server can render tokens as they are generated.
 *
 * A single [Mutex] serializes all requests: the LLM cannot truly run in
 * parallel, so concurrent callers (UI + HTTP clients) simply queue up.
 */
class LiteRtEngineManager private constructor(
    private val context: Context,
) {
    companion object {
        @Volatile
        private var instance: LiteRtEngineManager? = null

        fun getInstance(context: Context): LiteRtEngineManager =
            instance ?: synchronized(this) {
                instance ?: LiteRtEngineManager(context.applicationContext).also { instance = it }
            }

        /**
         * Builds generation params from settings in one place, so the nine
         * call sites can't drift (temperature/topK/topP/maxTokens used to be
         * re-typed at each site with slightly different values).
         */
        fun engineParamsFromSettings(
            s: com.raikaai.app.data.Settings,
            modelPath: String,
            systemPrompt: String = "",
            enableThinking: Boolean = false,
            temperature: Float = s.temperature,
            topK: Int = s.topK,
            topP: Float = s.topP,
            maxTokens: Int = com.raikaai.app.data.SamplingDefaults.LOCAL_MAX_TOKENS,
            contextWindow: Int = s.contextWindowTokens,
            backend: String = s.backend,
        ): EngineParams =
            EngineParams(
                modelPath = modelPath,
                temperature = temperature,
                topK = topK,
                topP = topP,
                maxTokens = maxTokens,
                contextWindow = contextWindow,
                backend = backend,
                systemPrompt = systemPrompt,
                enableThinking = enableThinking,
            )

        private val _engineLogs = MutableStateFlow<List<String>>(emptyList())
        val engineLogs: StateFlow<List<String>> = _engineLogs

        private val logScope = CoroutineScope(Dispatchers.IO)
        private var logFile: File? = null

        fun initDiskLogging(context: Context) {
            if (logFile == null) {
                val dir = File(context.cacheDir, "litert_logs").apply { mkdirs() }
                logFile = File(dir, "litert_runtime.log")
            }
        }

        private const val MAX_LOG_FILE_BYTES = 2 * 1024 * 1024L

        fun logEngine(message: String) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val formatted = "[$timestamp] $message"
            _engineLogs.update { prev ->
                val trimmed = if (prev.size >= 300) prev.drop(prev.size - 299) else prev
                trimmed + formatted
            }

            logScope.launch {
                try {
                    val f = logFile ?: return@launch
                    if (f.exists() && f.length() > MAX_LOG_FILE_BYTES) {
                        f.writeText("")
                    }
                    f.appendText("$formatted\n")
                } catch (_: Throwable) {
                }
            }
        }

        fun getFullLogs(context: Context): String {
            val dir = File(context.cacheDir, "litert_logs")
            val file = File(dir, "litert_runtime.log")
            return if (file.exists()) {
                try {
                    file.readText()
                } catch (_: Exception) {
                    _engineLogs.value.joinToString("\n")
                }
            } else {
                _engineLogs.value.joinToString("\n")
            }
        }

        fun clearLogs() {
            _engineLogs.value = emptyList()
            logScope.launch {
                try {
                    logFile?.writeText("")
                } catch (_: Throwable) {
                }
            }
        }

        fun getSystemSpecs(context: Context): SystemSpecs {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
            val availRamGb = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
            val cores = Runtime.getRuntime().availableProcessors()
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            val hardware =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.os.Build.SOC_MODEL
                } else {
                    android.os.Build.HARDWARE
                }
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val osVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
            return SystemSpecs(
                deviceModel = "$manufacturer $model",
                chipset = hardware,
                cores = cores,
                abi = abi,
                osVersion = osVersion,
                availRamGb = availRamGb,
                totalRamGb = totalRamGb,
                isLowMemory = memInfo.lowMemory,
            )
        }

        data class SystemSpecs(
            val deviceModel: String,
            val chipset: String,
            val cores: Int,
            val abi: String,
            val osVersion: String,
            val availRamGb: Double,
            val totalRamGb: Double,
            val isLowMemory: Boolean,
        )
    }

    data class EngineParams(
        val modelPath: String,
        val temperature: Float = 0.2f,
        val topK: Int = 40,
        val topP: Float = 0.90f,
        val maxTokens: Int = 512,
        val contextWindow: Int = 2048,
        val backend: String = "GPU", // "GPU" | "CPU"
        val systemPrompt: String = "",
        val enableThinking: Boolean = false,
    )

    init {
        initDiskLogging(context)
    }

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var activeModelPath: String? = null
    private var activeBackend: String? = null

    /** Backend the caller asked for (activeBackend may differ after a fallback). */
    private var activeRequestedBackend: String? = null
    private var activeTokenBudget: Int = 0
    private var activeVisionSupported: Boolean = false
    private var activeAudioSupported: Boolean = false

    val isAudioSupported: Boolean
        get() = activeAudioSupported || (activeModelPath?.let { isAudioCandidate(it) } ?: false)

    val isVisionSupported: Boolean
        get() = activeVisionSupported || (activeModelPath?.let { isVisionCandidate(it) } ?: false)

    /** Conversation of the in-flight generation, so [cancelGeneration] can abort it natively. */
    @Volatile
    private var activeConversation: com.google.ai.edge.litertlm.Conversation? = null

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded

    private val _isLoadingModel = MutableStateFlow(false)
    val isLoadingModel: StateFlow<Boolean> = _isLoadingModel

    private val _loadedModelPath = MutableStateFlow<String?>(null)
    val loadedModelPath: StateFlow<String?> = _loadedModelPath

    private val _activeBackendState = MutableStateFlow<String?>(null)
    val activeBackendState: StateFlow<String?> = _activeBackendState

    /** True while a generation request (chat or /v1) is being executed. */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    /**
     * Explicitly loads the model into memory/GPU. Returns true on success.
     * Guaranteed to run on Dispatchers.IO to never block the Android UI Main Thread.
     */
    suspend fun load(params: EngineParams): Boolean =
        withContext(Dispatchers.IO) {
            _isLoadingModel.value = true
            try {
                getOrCreateEngine(params)
                _isModelLoaded.value
            } finally {
                _isLoadingModel.value = false
            }
        }

    suspend fun unload() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                unloadLocked()
            }
        }

    data class InferenceMetrics(
        val tokensPerSec: Float,
        val ttftMs: Long,
        val totalTokens: Int,
        val durationSec: Float,
        val backend: String,
    ) {
        val displayBadge: String
            get() = "%.1f tok/s · %d tokens · %s".format(Locale.US, tokensPerSec, totalTokens, backend)
    }

    /**
     * Streaming generation. Emits response deltas as they are produced by the
     * model. Initializes the engine on demand (with GPU -> CPU fallback).
     */
    fun streamResponse(
        prompt: String,
        params: EngineParams,
        imagePath: String? = null,
        imageBytes: ByteArray? = null,
        audioPath: String? = null,
        audioBytes: ByteArray? = null,
        onMetrics: ((InferenceMetrics) -> Unit)? = null,
    ): Flow<String> =
        flow {
            val previousPriority =
                try {
                    val prev = android.os.Process.getThreadPriority(android.os.Process.myTid())
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                    prev
                } catch (e: Throwable) {
                    KLog.e("LiteRT", "Failed to set thread priority: ${e.message}")
                    null
                }

            val startTime = System.currentTimeMillis()
            var firstTokenTime = 0L
            val inputTokens = estimateTokenCount(prompt)

            _isGenerating.value = true
            try {
                val hasAudio = !audioPath.isNullOrBlank() || (audioBytes != null && audioBytes.isNotEmpty())
                val hasImage = !imagePath.isNullOrBlank() || (imageBytes != null && imageBytes.isNotEmpty())
                var backendName = params.backend
                val modelName = File(params.modelPath).name

                var outputText = ""
                var tokenCount = 0
                var inThinkingBlock = false
                var thoughtSeen = ""

                val processChunk: suspend (Message) -> Unit = { chunk ->
                    val textDelta =
                        chunk.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                    val rawThought = chunk.channels.values.joinToString("")
                    var thoughtDelta = ""
                    if (rawThought.isNotEmpty()) {
                        thoughtDelta =
                            if (rawThought.startsWith(thoughtSeen)) {
                                rawThought.substring(thoughtSeen.length)
                            } else {
                                rawThought
                            }
                        thoughtSeen =
                            if (rawThought.startsWith(thoughtSeen)) rawThought else thoughtSeen + rawThought
                    }

                    var out = ""
                    if (thoughtDelta.isNotEmpty()) {
                        if (!inThinkingBlock) {
                            out += "<think>"
                            inThinkingBlock = true
                        }
                        out += thoughtDelta
                    }
                    if (textDelta.isNotEmpty()) {
                        if (inThinkingBlock) {
                            out += "</think>\n\n"
                            inThinkingBlock = false
                        }
                        out += textDelta
                    }
                    if (out.isNotEmpty()) {
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.currentTimeMillis()
                        }
                        tokenCount++
                        outputText += out
                        emit(out)
                    }
                }

                mutex.withLock {
                    // Acquire the engine under the SAME lock as the generation:
                    // acquiring it in a separate critical section let a
                    // concurrent request (e.g. a /v1 client with different
                    // params) reload and close it in between, so this request
                    // would then use a closed native engine.
                    val activeEngine = withContext(Dispatchers.IO) { getOrCreateEngineLocked(params) }
                    backendName = activeBackend ?: params.backend
                    val imgTag = if (!imagePath.isNullOrBlank()) " + image ($imagePath)" else if (hasImage) " + image (${imageBytes?.size?.div(1024) ?: 0} KB)" else ""
                    val audioTag = if (!audioPath.isNullOrBlank()) " + audio ($audioPath)" else if (hasAudio) " + audio (${audioBytes?.size?.div(1024) ?: 0} KB)" else ""
                    logEngine("[LiteRT Request] Stream on $backendName ($modelName)$imgTag$audioTag")
                    logEngine("  ├── Input: ~$inputTokens tokens (${prompt.length} chars) · temp=${params.temperature}, topK=${params.topK}")

                    val sampler =
                        SamplerConfig(
                            topK = params.topK,
                            topP = params.topP.toDouble(),
                            temperature = params.temperature.toDouble(),
                        )
                    val conv = activeEngine.createConversation(ConversationConfig(samplerConfig = sampler))
                    activeConversation = conv
                    val thinkingConfig = ThinkingConfig(enableThinking = params.enableThinking)

                    val validImageFile =
                        if (!imagePath.isNullOrBlank()) {
                            File(imagePath).takeIf { it.exists() }
                        } else {
                            null
                        }

                    val validAudioFile =
                        if (!audioPath.isNullOrBlank()) {
                            File(audioPath).takeIf { it.exists() }
                        } else {
                            null
                        }

                    try {
                        val contentList = mutableListOf<Content>()

                        // 1. Add Image attachment if available and supported
                        val imgData = imageBytes ?: validImageFile?.let { runCatching { it.readBytes() }.getOrNull() }
                        if (imgData != null && imgData.isNotEmpty() && activeVisionSupported) {
                            contentList.add(Content.ImageBytes(imgData))
                        } else if (validImageFile != null && activeVisionSupported) {
                            contentList.add(Content.ImageFile(validImageFile.absolutePath))
                        }

                        // 2. Add Audio attachment if available and supported (Gemma 4 / Gemma 3n)
                        val audioData = audioBytes ?: validAudioFile?.let { runCatching { it.readBytes() }.getOrNull() }
                        if (audioData != null && audioData.isNotEmpty() && activeAudioSupported) {
                            contentList.add(Content.AudioBytes(audioData))
                        } else if (validAudioFile != null && activeAudioSupported) {
                            contentList.add(Content.AudioFile(validAudioFile.absolutePath))
                        }

                        // 3. Add text prompt after media attachments (ensures correct autoregressive ordering)
                        if (prompt.isNotBlank() || contentList.isEmpty()) {
                            contentList.add(Content.Text(prompt.ifBlank { "Please listen to this audio clip and respond." }))
                        }

                        val stream = if (contentList.any { it !is Content.Text }) {
                            try {
                                conv.sendMessageAsync(Contents.of(contentList), thinkingConfig = thinkingConfig)
                            } catch (e: Throwable) {
                                logEngine("[WARN] Multimodal input rejected (${e.message}). Falling back to text prompt.")
                                conv.sendMessageAsync(prompt.ifBlank { "Please respond to this audio clip." }, thinkingConfig = thinkingConfig)
                            }
                        } else {
                            if (validImageFile != null && !activeVisionSupported) {
                                logEngine("[WARN] Active model has no vision encoder. Prompting with text only.")
                            }
                            if ((validAudioFile != null || (audioBytes != null && audioBytes.isNotEmpty())) && !activeAudioSupported) {
                                logEngine("[WARN] Active model has no audio encoder on this backend. Prompting with text only.")
                            }
                            conv.sendMessageAsync(prompt, thinkingConfig = thinkingConfig)
                        }

                        try {
                            stream.collect(processChunk)
                        } catch (t: Throwable) {
                            if ((validImageFile != null || validAudioFile != null) && outputText.isBlank()) {
                                logEngine("[WARN] Multimodal streaming encountered error (${t.message}), retrying with text prompt fallback...")
                                val fallbackStream = conv.sendMessageAsync(prompt.ifBlank { "Please respond." }, thinkingConfig = thinkingConfig)
                                fallbackStream.collect(processChunk)
                            } else {
                                throw t
                            }
                        }
                    } finally {
                        activeConversation = null
                        try {
                            conv.close()
                        } catch (_: Exception) {
                        }
                    }
                }

                if (inThinkingBlock) {
                    outputText += "</think>"
                    emit("</think>")
                }

                val now = System.currentTimeMillis()
                val totalDurationSec = maxOf(0.01f, (now - startTime) / 1000.0f)
                val decodeDurationSec = if (firstTokenTime > 0L) maxOf(0.01f, (now - firstTokenTime) / 1000.0f) else totalDurationSec
                val outputTokens = maxOf(tokenCount, estimateTokenCount(outputText))
                val tokPerSec = outputTokens / decodeDurationSec
                val ttft = if (firstTokenTime > 0L) (firstTokenTime - startTime) else 0L

                logEngine("[LiteRT Response] Generated $outputTokens tokens in %.2fs (%.1f tok/s, TTFT: ${ttft}ms)".format(Locale.US, decodeDurationSec, tokPerSec))

                if (outputText.isBlank()) {
                    logEngine("[ERROR] Inference finished but output was EMPTY.")
                    KLog.e("LiteRT", "Empty model response")
                    throw IllegalStateException("Model response was empty")
                }

                val metrics =
                    InferenceMetrics(
                        tokensPerSec = tokPerSec,
                        ttftMs = ttft,
                        totalTokens = outputTokens,
                        durationSec = totalDurationSec,
                        backend = backendName,
                    )
                onMetrics?.invoke(metrics)
            } finally {
                _isGenerating.value = false
                if (previousPriority != null) {
                    try {
                        android.os.Process.setThreadPriority(previousPriority)
                    } catch (_: Throwable) {
                    }
                }
            }
        }.flowOn(Dispatchers.Default)

    /**
     * Non-streaming generation (accumulates [streamResponse]).
     */
    suspend fun generateResponse(
        prompt: String,
        params: EngineParams,
        imagePath: String? = null,
        imageBytes: ByteArray? = null,
        audioPath: String? = null,
        audioBytes: ByteArray? = null,
    ): String {
        val sb = StringBuilder()
        streamResponse(
            prompt = prompt,
            params = params,
            imagePath = imagePath,
            imageBytes = imageBytes,
            audioPath = audioPath,
            audioBytes = audioBytes,
        ).collect { sb.append(it) }
        return sb.toString().trim()
    }

    /**
     * Aborts the in-flight generation natively (no-op if none is running).
     * The streaming collector receives a CancellationException and the
     * conversation is closed by [streamResponse]'s finally block.
     */
    fun cancelGeneration() {
        try {
            activeConversation?.cancelProcess()
        } catch (_: Exception) {
        }
    }

    /**
     * Initializes or retrieves an active LiteRT-LM Engine.
     * Falls back from GPU (OpenCL) to CPU (XNNPack) when initialization fails.
     * Fully runs on Dispatchers.IO to guarantee zero blocking of Android main thread.
     */
    suspend fun getOrCreateEngine(params: EngineParams): Engine =
        withContext(Dispatchers.IO) {
            mutex.withLock { getOrCreateEngineLocked(params) }
        }

    /**
     * Must be called with [mutex] held (the mutex is not reentrant).
     * Modalities are not a reload trigger: initialization already tries every
     * modality the model is a candidate for, so reloading for a missing one
     * would only repeat the same failed attempt on every request.
     */
    private fun getOrCreateEngineLocked(params: EngineParams): Engine {
            val file = File(params.modelPath)
            if (!file.exists() || file.length() == 0L) {
                val err = "Model file not found at: ${params.modelPath}"
                logEngine("[ERROR] $err")
                KLog.e("LiteRT", err)
                throw IllegalStateException(err)
            }

            val requestedBudget = minOf(maxOf(1024, maxOf(params.contextWindow, params.maxTokens)), 8192)
            // Grow-only: a loaded engine with a larger budget serves smaller
            // requests fine. Reloading on every difference made /v1 clients
            // with different max_tokens reload the multi-GB model per request.
            val needsBudgetReload = activeTokenBudget > 0 && requestedBudget > activeTokenBudget
            val tokenBudget = if (needsBudgetReload || activeTokenBudget == 0) requestedBudget else activeTokenBudget

            val needsReload =
                engine == null ||
                    activeModelPath != params.modelPath ||
                    // Compare against the backend that was REQUESTED, not the
                    // one that ended up active: after a GPU -> CPU fallback the
                    // active backend is "CPU (Fallback)", which never matches
                    // "GPU" and forced a full reload on every message.
                    (activeRequestedBackend != null && !activeRequestedBackend.equals(params.backend, ignoreCase = true)) ||
                    needsBudgetReload

            if (needsReload) {
                unloadLocked()
                val specs = getSystemSpecs(context)
                val fileSizeStr = formatBytes(file.length())

                logEngine("[LiteRT Device] ${specs.deviceModel} (${specs.chipset}) · ${specs.cores} Cores · ${specs.abi}")
                logEngine(
                    String.format(
                        Locale.US,
                        "[LiteRT Device] RAM: %.2f GB Free / %.2f GB Total (Low Memory: %b)",
                        specs.availRamGb,
                        specs.totalRamGb,
                        specs.isLowMemory,
                    ),
                )
                logEngine("[LiteRT Engine] Model: ${file.name} ($fileSizeStr) · Target: ${params.backend} · Budget: $tokenBudget tokens")

                val startInit = System.currentTimeMillis()
                val candidateForVision = isVisionCandidate(params.modelPath)
                val candidateForAudio = isAudioCandidate(params.modelPath)

                try {
                    val targetBackend =
                        if (params.backend.equals("CPU", ignoreCase = true)) Backend.CPU() else Backend.GPU()
                    val initTarget = if (targetBackend is Backend.GPU) "OpenCL GPU shader cache" else "CPU XNNPack engine"
                    logEngine("[LiteRT Engine] Initializing $initTarget...")

                    val initResult =
                        initEngineInstance(
                            modelPath = params.modelPath,
                            targetBackend = targetBackend,
                            tokenBudget = tokenBudget,
                            tryVision = candidateForVision,
                            tryAudio = candidateForAudio,
                        )
                    engine = initResult.engine
                    activeBackend = params.backend
                    activeRequestedBackend = params.backend
                    activeTokenBudget = tokenBudget
                    activeVisionSupported = initResult.visionSupported
                    activeAudioSupported = initResult.audioSupported
                    val initDuration = System.currentTimeMillis() - startInit
                    val modalityBadges = buildString {
                        if (initResult.visionSupported) append(" + Vision")
                        if (initResult.audioSupported) append(" + Audio")
                        if (!initResult.visionSupported && !initResult.audioSupported) append(" (Text Only)")
                    }
                    logEngine("[LiteRT Engine] ${params.backend}$modalityBadges Initialization Successful (${initDuration}ms)")
                } catch (e: Exception) {
                    if (!params.backend.equals("CPU", ignoreCase = true)) {
                        logEngine("[WARN] GPU OpenCL rejected (${e.message ?: e.javaClass.simpleName}). Falling back to CPU (XNNPack)...")
                        KLog.w("LiteRT", "GPU OpenCL rejected, falling back to CPU: ${e.message}")
                        try {
                            val initResult =
                                initEngineInstance(
                                    modelPath = params.modelPath,
                                    targetBackend = Backend.CPU(),
                                    tokenBudget = tokenBudget,
                                    tryVision = candidateForVision,
                                    tryAudio = candidateForAudio,
                                )
                            engine = initResult.engine
                            activeBackend = "CPU (Fallback)"
                            activeRequestedBackend = params.backend
                            activeTokenBudget = tokenBudget
                            activeVisionSupported = initResult.visionSupported
                            activeAudioSupported = initResult.audioSupported
                            val fallbackDuration = System.currentTimeMillis() - startInit
                            val modalityBadges = buildString {
                                if (initResult.visionSupported) append(" + Vision")
                                if (initResult.audioSupported) append(" + Audio")
                                if (!initResult.visionSupported && !initResult.audioSupported) append(" (Text Only)")
                            }
                            logEngine("[LiteRT Engine] CPU Fallback$modalityBadges Successful (${fallbackDuration}ms)")
                        } catch (cpuEx: Exception) {
                            val failMsg = "GPU and CPU initialization failed: ${cpuEx.message ?: cpuEx.javaClass.simpleName}"
                            logEngine("[ERROR] $failMsg")
                            KLog.e("LiteRT", failMsg, cpuEx)
                            throw IllegalStateException("Failed to load LiteRT model ($failMsg)", cpuEx)
                        }
                    } else {
                        val failMsg = "CPU initialization failed: ${e.message ?: e.javaClass.simpleName}"
                        logEngine("[ERROR] $failMsg")
                        KLog.e("LiteRT", failMsg, e)
                        throw IllegalStateException("Failed to load LiteRT model ($failMsg)", e)
                    }
                }
                activeModelPath = params.modelPath
                _activeBackendState.value = activeBackend
                _isModelLoaded.value = true
                _loadedModelPath.value = params.modelPath
            }

            return engine ?: throw IllegalStateException("Google AI Edge LiteRT-LM engine initialization failed.")
    }

    private data class EngineInitResult(
        val engine: Engine,
        val visionSupported: Boolean,
        val audioSupported: Boolean,
    )

    fun isVisionCandidate(modelPath: String): Boolean {
        val name = File(modelPath).name.lowercase()
        return name.contains("e2b") ||
            name.contains("e4b") ||
            name.contains("paligemma") ||
            name.contains("vision") ||
            name.contains("vl-") ||
            name.contains("-vl") ||
            name.contains("multimodal")
    }

    fun isAudioCandidate(modelPath: String): Boolean {
        val name = File(modelPath).name.lowercase()
        return name.contains("e2b") ||
            name.contains("e4b") ||
            name.contains("gemma-4") ||
            name.contains("gemma4") ||
            name.contains("gemma-3n") ||
            name.contains("gemma3n") ||
            name.contains("audio") ||
            name.contains("omni") ||
            name.contains("multimodal")
    }

    private fun initEngineInstance(
        modelPath: String,
        targetBackend: Backend,
        tokenBudget: Int,
        tryVision: Boolean,
        tryAudio: Boolean,
    ): EngineInitResult {
        fun tryConfig(enableVision: Boolean, enableAudio: Boolean): Engine? {
            return try {
                logEngine("[LiteRT Engine] Attempting config: Vision=$enableVision, Audio=$enableAudio on $targetBackend (budget=$tokenBudget tokens)...")
                val config =
                    EngineConfig(
                        modelPath = modelPath,
                        backend = targetBackend,
                        visionBackend = if (enableVision) targetBackend else null,
                        audioBackend = if (enableAudio) Backend.CPU() else null,
                        maxNumTokens = tokenBudget,
                        cacheDir = if (modelPath.startsWith("/data/local/tmp")) context.getExternalFilesDir(null)?.absolutePath else null,
                    )
                val eng = Engine(config)
                eng.initialize()
                try {
                    val testConv = eng.createConversation(
                        ConversationConfig(
                            samplerConfig = SamplerConfig(
                                topK = 40,
                                topP = 0.95,
                                temperature = 0.7,
                            ),
                        ),
                    )
                    testConv.close()
                } catch (convEx: Throwable) {
                    logEngine("[WARN] Conversation test creation failed (vision=$enableVision, audio=$enableAudio): ${convEx.message}")
                    try { eng.close() } catch (_: Exception) {}
                    return null
                }
                logEngine("[LiteRT Engine] Validated config: Vision=$enableVision, Audio=$enableAudio successfully.")
                eng
            } catch (t: Throwable) {
                logEngine("[WARN] Config (Vision=$enableVision, Audio=$enableAudio) rejected: ${t.message ?: t.javaClass.simpleName}")
                KLog.e("LiteRT", "tryConfig rejected for vision=$enableVision, audio=$enableAudio", t)
                null
            }
        }

        // 1. Try full multimodal (Vision + Audio if requested)
        if (tryVision && tryAudio) {
            val eng = tryConfig(enableVision = true, enableAudio = true)
            if (eng != null) {
                return EngineInitResult(eng, visionSupported = true, audioSupported = true)
            }
            logEngine("[INFO] Combined Vision+Audio rejected. Trying individual modalities...")
        }

        // 2. Try Audio only if requested
        if (tryAudio) {
            val eng = tryConfig(enableVision = false, enableAudio = true)
            if (eng != null) {
                return EngineInitResult(eng, visionSupported = false, audioSupported = true)
            }
        }

        // 3. Try Vision only if requested
        if (tryVision) {
            val eng = tryConfig(enableVision = true, enableAudio = false)
            if (eng != null) {
                return EngineInitResult(eng, visionSupported = true, audioSupported = false)
            }
        }

        // 4. Fallback to Text-only engine
        logEngine("[INFO] Initializing as text LLM...")
        val textEng =
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = targetBackend,
                    visionBackend = null,
                    audioBackend = null,
                    maxNumTokens = tokenBudget,
                    cacheDir = if (modelPath.startsWith("/data/local/tmp")) context.getExternalFilesDir(null)?.absolutePath else null,
                ),
            )
        textEng.initialize()
        return EngineInitResult(textEng, visionSupported = false, audioSupported = false)
    }

    private fun unloadLocked() {
        try {
            engine?.close()
            logEngine("[LiteRT Engine] Model unloaded from memory successfully.")
        } catch (e: Exception) {
            logEngine("[WARN] Error while unloading engine: ${e.message}")
        } finally {
            engine = null
            activeModelPath = null
            activeBackend = null
            activeRequestedBackend = null
            activeTokenBudget = 0
            activeVisionSupported = false
            activeAudioSupported = false
            _activeBackendState.value = null
            _isModelLoaded.value = false
            _loadedModelPath.value = null
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        return maxOf(1, Math.round(text.length / 3.8).toInt())
    }
}
