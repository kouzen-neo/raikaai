package com.raikaai.app.artifacts

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raikaai.app.util.CodeArtifacts
import com.raikaai.app.util.KLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Artifact(
    val id: String = "",
    val projectId: String = "",
    val fileName: String = "",
    val language: String = "",
    val charCount: Int = 0,
    val version: Int = 1,
    val chatId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Repairs JVM nulls when Gson deserializes legacy/corrupt JSON. */
    @Suppress("USELESS_ELVIS")
    fun normalize(): Artifact =
        Artifact(
            id = (id ?: "").trim(),
            projectId = (projectId ?: "").ifBlank { "project" },
            fileName = (fileName ?: "").ifBlank { "file.txt" },
            language = (language ?: ""),
            charCount = charCount.coerceAtLeast(0),
            version = version.coerceAtLeast(1),
            chatId = chatId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

/**
 * Internal store for code artifacts generated from assistant answers.
 * Metadata lives in artifacts.json; content in <id>.txt next to it.
 * Re-capturing the same project+file overwrites content and bumps version.
 */
class ArtifactStore private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: ArtifactStore? = null

        fun getInstance(context: Context): ArtifactStore =
            instance ?: synchronized(this) {
                instance ?: ArtifactStore(context.applicationContext).also { instance = it }
            }
    }

    private val gson = Gson()
    private val dir = File(context.filesDir, "artifacts").apply { if (!exists()) mkdirs() }
    private val metaFile get() = File(dir, "artifacts.json")

    private val artifactIdPattern = Regex("^[A-Za-z0-9-]+$")

    /**
     * Serializes capture()/delete()/deleteProject(): without it, two
     * concurrent captures read the same snapshot and the first one's entries
     * vanish from the rewritten metadata (orphaned content files) (B3).
     */
    private val mutationMutex = Mutex()

    /** Artifact ids are embedded verbatim in "<id>.txt" file names (B28). */
    private fun contentFileFor(id: String): File {
        require(artifactIdPattern.matches(id)) { "Invalid artifact id: $id" }
        return File(dir, "$id.txt")
    }

    private val _artifacts = MutableStateFlow<List<Artifact>>(emptyList())
    val artifacts: StateFlow<List<Artifact>> = _artifacts.asStateFlow()

    /**
     * Initial metadata load must not block the constructing thread (B21):
     * getInstance() can be reached from the main thread, so the file read
     * runs on IO and the StateFlow updates when it completes.
     *
     * [initGate] prevents a capture()/delete() that wins the race against
     * the initial load from rewriting artifacts.json from a still-empty
     * snapshot and wiping existing metadata.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initGate = CompletableDeferred<Unit>()

    init {
        ioScope.launch {
            try {
                reload()
            } finally {
                initGate.complete(Unit)
            }
        }
    }

    private fun reload() {
        val parsed: List<Artifact>? =
            try {
                if (metaFile.exists()) {
                    val raw: List<Artifact>? =
                        gson.fromJson(metaFile.readText(), object : TypeToken<List<Artifact>>() {}.type)
                    raw.orEmpty().map { it.normalize() }.filter { artifactIdPattern.matches(it.id) }
                } else {
                    emptyList()
                }
            } catch (t: Throwable) {
                KLog.w("Artifacts", "Meta reload failed, keeping last good data: ${t.message}")
                null // signal: keep _artifacts.value unchanged
            }
        // A corrupt artifacts.json must never wipe the in-memory metadata:
        // the next save would otherwise persist an empty list and lose every
        // entry permanently (B3).
        if (parsed != null) _artifacts.value = parsed
    }

    /** Metadata is rewritten wholesale, so it goes through tmp+rename like everything else (B3). */
    private fun saveMeta(list: List<Artifact>) {
        try {
            val tmp = File(dir, "artifacts.json.${System.nanoTime()}.tmp")
            tmp.writeText(gson.toJson(list))
            if (!tmp.renameTo(metaFile)) {
                tmp.copyTo(metaFile, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            KLog.w("Artifacts", "Meta save failed: ${t.message}")
        }
    }

    /**
     * Captures generated project files. Existing entries with the same
     * projectId+fileName are overwritten with version bump.
     */
    suspend fun capture(
        projectId: String,
        files: List<CodeArtifacts.ProjectFile>,
        chatId: String? = null,
    ): Int =
        withContext(Dispatchers.IO) {
            initGate.await()
            if (files.isEmpty()) return@withContext 0
            mutationMutex.withLock {
                try {
                    val current = _artifacts.value.toMutableList()
                    var written = 0
                    files.forEach { f ->
                        val existingIdx =
                            current.indexOfFirst { it.projectId == projectId && it.fileName == f.fileName }
                        val now = System.currentTimeMillis()
                        val previous = if (existingIdx >= 0) current[existingIdx] else null
                        val artifact =
                            if (previous != null) {
                                Artifact(
                                    id = previous.id,
                                    projectId = projectId,
                                    fileName = f.fileName,
                                    language = f.language,
                                    charCount = f.content.length,
                                    version = previous.version + 1,
                                    chatId = previous.chatId ?: chatId,
                                    createdAt = previous.createdAt,
                                    updatedAt = now,
                                ).also { current[existingIdx] = it }
                            } else {
                                Artifact(
                                    id = UUID.randomUUID().toString(),
                                    projectId = projectId,
                                    fileName = f.fileName,
                                    language = f.language,
                                    charCount = f.content.length,
                                    chatId = chatId,
                                ).also { current.add(it) }
                            }
                        contentFileFor(artifact.id).writeText(f.content)
                        written++
                    }
                    saveMeta(current.sortedBy { it.createdAt })
                    reload()
                    written
                } catch (t: Throwable) {
                    KLog.w("Artifacts", "Capture failed: ${t.message}")
                    0
                }
            }
        }

    suspend fun readContent(artifact: Artifact): String? =
        withContext(Dispatchers.IO) {
            try {
                contentFileFor(artifact.id).readText()
            } catch (_: Throwable) {
                null
            }
        }

    suspend fun delete(id: String) =
        withContext(Dispatchers.IO) {
            initGate.await()
            mutationMutex.withLock {
                try {
                    contentFileFor(id).delete()
                    saveMeta(_artifacts.value.filterNot { it.id == id })
                    reload()
                } catch (t: Throwable) {
                    KLog.w("Artifacts", "Delete failed: ${t.message}")
                }
            }
        }

    suspend fun deleteProject(projectId: String) =
        withContext(Dispatchers.IO) {
            initGate.await()
            mutationMutex.withLock {
                try {
                    _artifacts.value.filter { it.projectId == projectId }.forEach {
                        contentFileFor(it.id).delete()
                    }
                    saveMeta(_artifacts.value.filterNot { it.projectId == projectId })
                    reload()
                } catch (t: Throwable) {
                    KLog.w("Artifacts", "Delete project failed: ${t.message}")
                }
            }
        }
}
