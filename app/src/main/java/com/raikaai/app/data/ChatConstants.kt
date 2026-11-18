package com.raikaai.app.data

/**
 * Centralized constants for role names, model sources, and backend types.
 * Replaces magic strings scattered across the codebase.
 */
object ChatConstants {
    // ── Message Roles ────────────────────────────────────────────────
    const val ROLE_USER = "user"
    const val ROLE_ASSISTANT = "assistant"
    const val ROLE_SYSTEM = "system"
    const val ROLE_COMPACTION = "compaction"

    // ── Model Sources ────────────────────────────────────────────────
    const val SOURCE_LOCAL = "local"
    const val SOURCE_REMOTE = "remote"

    // ── Backend Types ────────────────────────────────────────────────
    // NOTE: values are uppercase "GPU"/"CPU" — this matches what is persisted
    // in DataStore and compared with equals(..., ignoreCase = true) across the
    // codebase. The old lowercase "gpu"/"cpu" values were never referenced.
    const val BACKEND_GPU = "GPU"
    const val BACKEND_CPU = "CPU"

    // ── Theme Modes ──────────────────────────────────────────────────
    const val THEME_SYSTEM = "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
}

/**
 * Single source of truth for sampler defaults. Previously these drifted
 * across files (local temp 0.2 vs remote 0.7, maxTokens 256 vs 512, ...).
 */
object SamplingDefaults {
    const val LOCAL_TEMPERATURE = 0.2f
    const val REMOTE_TEMPERATURE = 0.7f
    const val TOP_K = 40
    const val TOP_P = 0.90f
    const val LOCAL_MAX_TOKENS = 512
    const val BENCHMARK_MAX_TOKENS = 64
    const val SUMMARY_MAX_TOKENS = 256

    fun coerceTemperature(v: Float): Float = v.coerceIn(0f, 2f)
}

/** Single source of truth for size/count caps (DoS guards). */
object AppLimits {
    const val REMOTE_IMAGE_MAX_BYTES = 8_000_000L
    const val TOOL_JSON_MAX_CHARS = 131072
    const val SAFE_FILENAME_CHARS = 80
    const val SUMMARY_MAX_WORDS = 180
    const val PERSONA_IMPORT_MAX_CHARS = 200_000
    const val TEXT_FILE_BUFFER = 8192
}
