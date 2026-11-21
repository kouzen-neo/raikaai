package com.raikaai.app.localai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Model file representation for installed/downloaded models on device storage.
 */
data class InstalledModel(
    val id: String,
    val fileName: String,
    val displayName: String,
    val sizeBytes: Long,
    val sizeDisplay: String,
    val isPreset: Boolean,
    val presetModel: LocalAiModel? = null,
    val absolutePath: String,
)

/**
 * Storage and file management for On-Device LiteRT LLM models.
 * Operates in app-private external files directory (context.getExternalFilesDir("models"))
 * which is Scoped Storage safe and requires no dangerous runtime permissions.
 * (Ported from KZKT.)
 */
class LocalModelManager(
    private val context: Context,
) {
    companion object {
        /** Model file extensions the app can actually load. */
        val ALLOWED_MODEL_EXTENSIONS = setOf("litertlm", "task", "bin", "tflite")

        /**
         * True for LiteRT runtime compiler caches (MLDrift program cache,
         * vision-encoder cache). These are derived artifacts regenerated on
         * load — never user-selectable models.
         */
        fun isCompilerCacheFile(fileName: String): Boolean {
            val lower = fileName.lowercase()
            return "mldrift" in lower || "vision_encoder" in lower
        }

        /**
         * Sanitizes a user-supplied file name so it can never escape [modelsDir]
         * (path traversal) and always carries an allowed model extension.
         *
         * @return the safe file name, or null when the name has no usable
         * basename or a disallowed extension.
         */
        fun sanitizeModelFileNameOrNull(raw: String): String? {
            // Strip any directory components (both separators) and whitespace.
            val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
            // Keep only a conservative safe charset.
            var name = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
            // Collapse dot runs (".." tricks) and strip leading/trailing dots
            // so names like ".litertlm" or "..." can never slip through.
            while (".." in name) name = name.replace("..", ".")
            name = name.trim('.')
            if (name.length > 128) name = name.take(128)
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return null
            val ext = name.substring(dot + 1).lowercase()
            if (ext !in ALLOWED_MODEL_EXTENSIONS) return null
            return name
        }

        /**
         * Like [sanitizeModelFileNameOrNull] but falls back to [fallback]
         * instead of returning null.
         */
        fun sanitizeModelFileName(
            raw: String,
            fallback: String = "custom_model.litertlm",
        ): String = sanitizeModelFileNameOrNull(raw) ?: fallback
    }

    val modelsDir: File
        get() {
            val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getModelFile(model: LocalAiModel): File = File(modelsDir, model.fileName)

    fun isModelDownloaded(model: LocalAiModel): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() >= 50_000_000L
    }

    fun getDownloadedBytes(model: LocalAiModel): Long {
        val file = getModelFile(model)
        return if (file.exists()) file.length() else 0L
    }

    fun deleteModel(model: LocalAiModel): Boolean = deleteFile(model.fileName)

    fun deleteFile(fileName: String): Boolean {
        val safeName = sanitizeModelFileName(fileName, fallback = "")
        if (safeName.isBlank()) return false
        val file = File(modelsDir, safeName)
        val tmp = File(modelsDir, "$safeName.tmp")
        if (tmp.exists()) tmp.delete()
        // Also remove URL-keyed resume temp files ($safeName.<urlHash>.tmp)
        // created by ModelDownloadService so orphaned multi-GB partials don't leak.
        modelsDir.listFiles { f ->
            f.isFile && f.name.startsWith("$safeName.") && f.name.endsWith(".tmp")
        }?.forEach { runCatching { it.delete() } }
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Scans the models directory and returns all downloaded model files
     * (.litertlm, .task, .bin, .tflite).
     * Automatically maps recognized filenames to PresetModels if available.
     *
     * Runtime-generated compiler caches (e.g. "*_mldrift_program_cache.bin",
     * "*.vision_encoder_*") are excluded: they are derived artifacts, not
     * loadable models, and only clutter the list.
     */
    fun getInstalledModels(): List<InstalledModel> =
        try {
            val files =
                modelsDir.listFiles { f ->
                    f.isFile &&
                        !f.name.endsWith(".tmp", ignoreCase = true) &&
                        !isCompilerCacheFile(f.name) &&
                        f.length() > 0 &&
                        f.name.substringAfterLast('.', "").lowercase() in ALLOWED_MODEL_EXTENSIONS
                } ?: emptyArray()

            files
                .map { file ->
                    val matchingPreset = LocalAiCatalog.PRESET_MODELS.find { it.fileName.equals(file.name, ignoreCase = true) }
                    val sizeBytes = file.length()
                    val sizeMb = sizeBytes.toFloat() / (1024 * 1024)
                    val sizeDisplay =
                        if (sizeMb >= 1024) {
                            String.format(Locale.ROOT, "%.2f GB", sizeMb / 1024)
                        } else {
                            String.format(Locale.ROOT, "%.0f MB", sizeMb)
                        }

                    val cleanName =
                        if (matchingPreset != null) {
                            matchingPreset.name
                        } else {
                            file.name
                                .removeSuffix(".litertlm")
                                .removeSuffix(".task")
                                .removeSuffix(".bin")
                                .removeSuffix(".tflite")
                                .replace("_", " ")
                                .replace("-", " ")
                        }

                    InstalledModel(
                        id = matchingPreset?.id ?: file.name,
                        fileName = file.name,
                        displayName = cleanName,
                        sizeBytes = sizeBytes,
                        sizeDisplay = sizeDisplay,
                        isPreset = matchingPreset != null,
                        presetModel = matchingPreset,
                        absolutePath = file.absolutePath,
                    )
                }.sortedByDescending { it.sizeBytes }
        } catch (_: Exception) {
            emptyList()
        }

    fun getFreeDiskSpace(): Long =
        try {
            modelsDir.freeSpace
        } catch (_: Exception) {
            0L
        }

    /**
     * Imports a user-selected model file (.litertlm, .task, .bin, .tflite) from a
     * Content Uri. The display name is sanitized to block path traversal and
     * restricted to allowed model extensions.
     * Returns the absolute path of the imported model file, or null on failure.
     */
    suspend fun importCustomModel(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                var rawName = "custom_model.litertlm"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        rawName = cursor.getString(nameIndex) ?: "custom_model.litertlm"
                    }
                }
                val fileName = sanitizeModelFileNameOrNull(rawName) ?: return@withContext null

                val targetFile = File(modelsDir, fileName)
                val tempFile = File(modelsDir, "$fileName.import.${System.nanoTime()}.tmp")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: run {
                        tempFile.delete()
                        return@withContext null
                    }
                    if (!tempFile.exists() || tempFile.length() <= 0L) {
                        tempFile.delete()
                        return@withContext null
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    targetFile.absolutePath
                } catch (e: Exception) {
                    runCatching { tempFile.delete() }
                    throw e
                }
            } catch (_: Exception) {
                null
            }
        }
}
