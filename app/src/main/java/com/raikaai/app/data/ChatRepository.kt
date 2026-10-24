package com.raikaai.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raikaai.app.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Attachment kind. Stored on disk as [ChatAttachment.type] strings ("image" | "file") — never rename those. */
enum class AttachmentType {
    IMAGE,
    FILE,
}

/** A file attached to a message: an image for vision, or a document whose text is injected. */
data class ChatAttachment(
    val type: String = "image", // "image" | "file" (persisted schema; compare via [kind])
    val path: String = "", // absolute path inside app storage
    val name: String = "",
    val mimeType: String? = null,
) {
    /** Typed view of [type]; unknown values safely fall back to FILE. */
    val kind: AttachmentType get() = if (type == "image") AttachmentType.IMAGE else AttachmentType.FILE
}

data class ChatMessageEntry(
    val role: String, // "user" | "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val stats: String? = null, // e.g. "18.2 tok/s · 154 tokens · GPU"
    val variants: List<String> = emptyList(),
    val selectedVariant: Int = 0,
    val imagePath: String? = null, // legacy single-image attachment (kept for old conversations)
    val audioPath: String? = null, // legacy single-audio attachment (kept for old conversations)
    val attachments: List<ChatAttachment> = emptyList(), // multi-attachment (images + files)
    val id: String? = UUID.randomUUID().toString(), // stable node id for conversation branching
    val parentId: String? = null, // previous node in the tree; null = root child
    val sources: List<String>? = null, // RAG citation labels e.g. ["notes.txt #2"]
)

data class Conversation(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var messages: List<ChatMessageEntry> = emptyList(), // flat tree nodes
    var branchActive: MutableMap<String, String>? = null, // parentKey ("root"|nodeId) -> active childId
    var summary: String? = null, // rolling compressed-memory summary (context compression)
    var summarizedUntilId: String? = null, // id of the last message folded into the summary
    var personaId: String? = null, // id of the PersonaPreset this chat belongs to (null = legacy / General)
    var personaAvatarUri: String = "", // snapshot used by roleplay history thumbnails
    var personaName: String = "",
    var personaDescription: String = "",
    var personaGreeting: String = "",
    var personaSystemPrompt: String = "",
    var pinnedModelSource: String? = null, // "local" | "remote" — null = follow global setting
    var pinnedModelId: String? = null, // local model id or remote model id pinned to this conversation
    var folder: String = "", // user folder name; "" = no folder
    var tags: List<String> = emptyList(), // user tags; a conversation can carry several (unlike folder)
    var isRoleplay: Boolean = false,
)

/** Lightweight conversation metadata for fast listings without parsing message bodies (P6). */
data class ConversationHeader(
    val id: String = "",
    val title: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val messageCount: Int = 0,
    val lastMessagePreview: String = "",
    val folder: String = "",
    val hasPinnedModel: Boolean = false,
    val tags: List<String> = emptyList(),
    val personaId: String? = null,
    val personaAvatarUri: String = "",
    val isRoleplay: Boolean = false,
)

private val conversationIdPattern = Regex("^[A-Za-z0-9-]+$")

/** Builds the lightweight list header from a full conversation (P6). */
internal fun Conversation.toHeader(): ConversationHeader {
    val last = messages.lastOrNull()?.content?.trim().orEmpty()
    val preview = if (last.length > 120) last.take(120) + "…" else last
    return ConversationHeader(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messages.size,
        lastMessagePreview = preview,
        folder = (folder as String?).orEmpty(),
        hasPinnedModel = !pinnedModelId.isNullOrBlank(),
        tags = sanitizeTags(tags),
        personaId = personaId,
        personaAvatarUri = personaAvatarUri,
        isRoleplay = isRoleplay,
    )
}

/** Canonical tag form: trimmed, non-blank, capped in count and length. */
internal fun sanitizeTags(tags: List<String>?): List<String> =
    tags.orEmpty()
        .map { it.trim().take(24) }
        .filter { it.isNotBlank() }
        .distinct()
        .take(10)

/** True when [id] is safe to embed verbatim in a file name (B28). */
internal fun isValidConversationId(id: String): Boolean = conversationIdPattern.matches(id)

/**
 * Striped per-id locks: concurrent writes to *different* conversations never
 * block each other, while writes to the *same* conversation are serialized so
 * a read-modify-write (rename) can never silently overwrite a newer save (B6).
 */
internal class StripedWriteLocks {
    private val guard = Any()
    private val locks = HashMap<String, Mutex>()

    fun forId(id: String): Mutex =
        synchronized(guard) { locks.getOrPut(id) { Mutex() } }
}

/**
 * Tmp sibling name unique per write call. Two concurrent saves of the same
 * conversation previously shared one "<id>.json.tmp" and could delete or
 * overwrite each other's tmp file mid-write, corrupting the conversation (B2).
 */
internal fun uniqueTmpFileFor(target: File): File =
    File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")

private const val STALE_TMP_AGE_MS = 60_000L

/**
 * Writes [text] to [file] atomically: content goes to a uniquely-named temp
 * sibling file first, then is renamed over the target. A crash mid-write can
 * never leave a half-written conversation file behind.
 *
 * @return true on success, false on failure (also logged via KLog).
 */
internal fun writeAtomically(
    file: File,
    text: String,
): Boolean =
    try {
        // Best-effort cleanup of stale tmp siblings left behind by crashed
        // writes. Only files older than STALE_TMP_AGE_MS are removed, so a
        // concurrent writer's fresh tmp file is never touched.
        file.parentFile?.listFiles { f ->
            f.isFile &&
                f.name.startsWith("${file.name}.") &&
                f.name.endsWith(".tmp") &&
                System.currentTimeMillis() - f.lastModified() > STALE_TMP_AGE_MS
        }?.forEach { it.delete() }
        val tmp = uniqueTmpFileFor(file)
        tmp.writeText(text)
        if (tmp.renameTo(file)) {
            true
        } else {
            // renameTo can fail across filesystems; fall back to copy+delete.
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
            true
        }
    } catch (e: Exception) {
        KLog.e("ChatRepository", "Atomic write failed for ${file.name}", e)
        false
    }

/**
 * File-based conversation persistence: one JSON file per conversation under
 * filesDir/chats/. Asynchronous file I/O on Dispatchers.IO.
 */
class ChatRepository(
    context: Context,
    storageDirectory: String = "chats",
) {
    private val gson = Gson()
    private val dir: File =
        File(context.filesDir, storageDirectory).apply { if (!exists()) mkdirs() }

    /** Serializes all writes (save/rename/delete) per conversation id (B2/B6). */
    private val writeLocks = StripedWriteLocks()

    /** Guards the lightweight conversation index (P6). */
    private val indexMutex = Mutex()
    private fun indexFile() = File(dir, "index.json")

    private fun fileFor(id: String): File {
        // Conversation ids are embedded verbatim in file names; reject anything
        // outside the safe alphabet instead of risking path traversal (B28).
        require(isValidConversationId(id)) { "Invalid conversation id: $id" }
        return File(dir, "$id.json")
    }

    /**
     * Conversation files only. The index file and tmp leftovers must never be
     * treated as conversations.
     */
    private fun conversationFiles(): List<File> =
        dir.listFiles { f ->
            f.isFile && f.name.endsWith(".json") && f.name != "index.json" && !f.name.endsWith(".tmp")
        }?.toList() ?: emptyList()

    suspend fun listConversations(): List<Conversation> =
        withContext(Dispatchers.IO) {
            try {
                conversationFiles()
                    .mapNotNull { f ->
                        try {
                            normalize(gson.fromJson(f.readText(), object : TypeToken<Conversation>() {}.type))
                        } catch (e: Exception) {
                            KLog.w("ChatRepository", "Skipping unreadable conversation file ${f.name}: ${e.message}")
                            null
                        }
                    }.sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                KLog.e("ChatRepository", "Failed to list conversations", e)
                emptyList()
            }
        }

    /**
     * Lightweight listing (P6): returns only id/title/timestamps by reading the
     * small index file instead of fully parsing every conversation JSON.
     *
     * The index is maintained on every save/rename/delete; files without an
     * index entry (legacy installs, crashes) are parsed once and folded in.
     *
     * NOTE: the history search UI and the JSON export need full message bodies,
     * so [listConversations] keeps its full-parse behavior. Moving the startup
     * path to this API requires UI changes outside this file.
     */
    suspend fun listConversationHeaders(): List<ConversationHeader> =
        withContext(Dispatchers.IO) {
            indexMutex.withLock {
                val filesById = conversationFiles().associateBy { it.name.removeSuffix(".json") }
                val previous = readIndex().associateBy { it.id }
                val merged = ArrayList<ConversationHeader>(filesById.size)
                var changed = false
                for ((id, file) in filesById) {
                    val known = previous[id]
                    if (known != null) {
                        merged.add(known)
                    } else {
                        try {
                            // Parse as full Conversation so messageCount/preview stay correct
                            // even for files predating the index (Gson ignores unknown fields
                            // when parsing as header, which would zero the new fields).
                            val full = gson.fromJson(file.readText(), Conversation::class.java)
                            merged.add(normalize(full).toHeader().copy(id = id))
                            changed = true
                        } catch (e: Exception) {
                            KLog.w("ChatRepository", "Skipping unreadable conversation file ${file.name}: ${e.message}")
                        }
                    }
                }
                if (changed || merged.size != previous.size) writeIndex(merged)
                merged.sortedByDescending { it.updatedAt }
            }
        }

    private fun readIndex(): List<ConversationHeader> =
        try {
            val f = indexFile()
            if (f.exists()) {
                val parsed: List<ConversationHeader> =
                    gson.fromJson(f.readText(), object : TypeToken<List<ConversationHeader>>() {}.type)
                        ?: emptyList()
                // Legacy index files predate folder/tags: Gson leaves them as
                // JVM nulls, which would NPE on first access.
                parsed.map { normalizeHeader(it) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            KLog.w("ChatRepository", "Failed to read conversation index: ${e.message}")
            emptyList()
        }

    /**
     * Repairs a header parsed from a legacy index file. Fields added after the
     * index was written arrive as JVM nulls via Gson.
     */
    private fun normalizeHeader(h: ConversationHeader): ConversationHeader {
        @Suppress("SENSELESS_COMPARISON")
        val folderNull = h.folder == null
        @Suppress("SENSELESS_COMPARISON", "UNCHECKED_CAST")
        val tagsNull = (h.tags as List<String>?) == null
        if (!folderNull && !tagsNull) return h
        return h.copy(
            folder = (h.folder as String?).orEmpty(),
            tags = sanitizeTags(h.tags),
        )
    }

    private fun writeIndex(entries: List<ConversationHeader>) {
        if (!writeAtomically(indexFile(), gson.toJson(entries))) {
            KLog.e("ChatRepository", "Failed to persist conversation index")
        }
    }

    /** Must be called with [indexMutex] held. */
    private fun updateIndexEntry(conv: Conversation) {
        val entries = readIndex().toMutableList()
        val header = conv.toHeader()
        val i = entries.indexOfFirst { it.id == conv.id }
        if (i >= 0) entries[i] = header else entries.add(header)
        writeIndex(entries)
    }

    /** Must be called with [indexMutex] held. */
    private fun removeIndexEntry(id: String) {
        writeIndex(readIndex().filterNot { it.id == id })
    }

    suspend fun get(id: String): Conversation? =
        withContext(Dispatchers.IO) {
            try {
                val f = fileFor(id)
                if (f.exists()) {
                    normalize(gson.fromJson(f.readText(), object : TypeToken<Conversation>() {}.type))
                } else {
                    null
                }
            } catch (e: Exception) {
                KLog.w("ChatRepository", "Failed to read conversation $id: ${e.message}")
                null
            }
        }

    /**
     * Persists one conversation. Race-hardened: UI streaming mutates message
     * lists on Main while Gson serializes them here on IO, so a torn
     * iteration throws ConcurrentModificationException. Retried (the next
     * persist covers the same content); after retries the save is skipped
     * with a log instead of crashing the app.
     */
    suspend fun save(conversation: Conversation): Conversation {
        var attempt = 0
        while (true) {
            try {
                return saveOnce(conversation)
            } catch (e: ConcurrentModificationException) {
                if (++attempt >= 3) {
                    KLog.e("ChatRepository", "save() racing stream, skipping this round for ${conversation.id}")
                    return conversation
                }
                delay(50L * attempt)
            }
        }
    }

    private suspend fun saveOnce(conversation: Conversation): Conversation =
        withContext(Dispatchers.IO) {
            // All writes for one conversation id are serialized: concurrent
            // save() calls (e.g. summary compression racing a normal persist)
            // previously interleaved through a single shared tmp file (B2),
            // and rename()'s read-modify-write could clobber a newer save (B6).
            writeLocks.forId(conversation.id).withLock {
                conversation.updatedAt = System.currentTimeMillis()
                if (conversation.title.isBlank()) {
                    conversation.title =
                        conversation.messages.firstOrNull { it.role == ChatConstants.ROLE_USER }
                            ?.content?.take(48)?.replace("\n", " ") ?: "New Chat"
                }
                if (writeAtomically(fileFor(conversation.id), gson.toJson(conversation))) {
                    indexMutex.withLock { updateIndexEntry(conversation) }
                } else {
                    KLog.e("ChatRepository", "save() failed for conversation ${conversation.id}")
                }
                conversation
            }
        }

    suspend fun rename(id: String, newTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            writeLocks.forId(id).withLock {
                val conv = get(id) ?: return@withLock false
                conv.title = newTitle.trim().ifBlank { "Untitled Chat" }
                conv.updatedAt = System.currentTimeMillis()
                val ok = writeAtomically(fileFor(conv.id), gson.toJson(conv))
                if (ok) {
                    indexMutex.withLock { updateIndexEntry(conv) }
                }
                ok
            }
        }

    suspend fun delete(id: String): Boolean =
        withContext(Dispatchers.IO) {
            writeLocks.forId(id).withLock {
                val target = fileFor(id)
                val deleted = target.exists() && target.delete()
                if (deleted) {
                    // Drop tmp siblings left by crashed writes while we hold
                    // the per-id lock, so no concurrent writer is active.
                    target.parentFile
                        ?.listFiles { f ->
                            f.isFile && f.name.startsWith("${target.name}.") && f.name.endsWith(".tmp")
                        }?.forEach { it.delete() }
                    indexMutex.withLock { removeIndexEntry(id) }
                }
                deleted
            }
        }

    /** Whether the conversation already has a file on disk. */
    suspend fun exists(id: String): Boolean =
        withContext(Dispatchers.IO) { fileFor(id).exists() }

    suspend fun deleteAll(): Int =
        withContext(Dispatchers.IO) {
            // indexMutex only (never an id lock): save()/rename()/delete() take
            // id lock -> index lock, so this order can never deadlock.
            indexMutex.withLock {
                var n = 0
                dir.listFiles()?.forEach { f ->
                    if (!f.isFile || f.name == "index.json") return@forEach
                    if (f.name.endsWith(".json") && f.delete()) n++
                    else if (f.name.endsWith(".tmp")) f.delete() // crash leftovers, not counted
                }
                writeIndex(emptyList())
                n
            }
        }

    /**
     * Ensures every node has a stable id and a linear parent chain for legacy
     * conversations saved before branching existed. Idempotent.
     */
    fun normalize(conv: Conversation): Conversation = Companion.normalize(conv)

    /** Walks the tree from root following [Conversation.branchActive] to build the visible path. */
    fun resolveActivePath(conv: Conversation): List<ChatMessageEntry> = Companion.resolveActivePath(conv)

    /** Sibling branches of a node (same parent), ordered by creation time. */
    fun siblingsOf(
        conv: Conversation,
        msg: ChatMessageEntry,
    ): List<ChatMessageEntry> = Companion.siblingsOf(conv, msg)

    companion object {
        const val ROOT_KEY = "root"

        @Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK")
        fun normalize(conv: Conversation): Conversation {
            // Gson bypasses the Kotlin constructor for legacy JSON: fields added
            // later (folder, tags) arrive as JVM nulls. Repair before use.
            @Suppress("SENSELESS_COMPARISON")
            if (conv.folder == null) conv.folder = ""
            conv.tags = sanitizeTags(conv.tags)
            @Suppress("SENSELESS_COMPARISON")
            if ((conv.title as String?) == null) conv.title = ""
            @Suppress("SENSELESS_COMPARISON")
            if ((conv.id as String?).isNullOrBlank()) conv.id = UUID.randomUUID().toString()
            val msgs = (conv.messages as List<ChatMessageEntry>?) ?: run {
                conv.messages = emptyList()
                return conv
            }
            if (msgs.isEmpty()) return conv
            var changed = false
            val out = ArrayList<ChatMessageEntry>(msgs.size)
            var prevId: String? = null
            for (m in msgs) {
                var id = m.id
                if (id.isNullOrBlank()) {
                    id = UUID.randomUUID().toString()
                    changed = true
                }
                var parent = m.parentId
                if (parent.isNullOrBlank() && prevId != null) {
                    parent = prevId
                    changed = true
                }
                // Rebuild via the constructor (not copy()): legacy JSON parsed by
                // Gson bypasses the Kotlin constructor and leaves non-null fields
                // like `variants` as JVM nulls, which copy()'s intrinsic checks reject.
                if (id.isNullOrBlank() || m.variants == null || m.attachments == null) changed = true
                val needsSanitize =
                    id != m.id ||
                        parent != m.parentId ||
                        m.variants == null ||
                        m.attachments == null ||
                        (m.role as String?) == null ||
                        (m.content as String?) == null
                out.add(
                    if (needsSanitize) {
                        ChatMessageEntry(
                            role = (m.role as String?).orEmpty(),
                            content = (m.content as String?).orEmpty(),
                            timestamp = m.timestamp,
                            stats = m.stats,
                            variants = m.variants.orEmpty(),
                            selectedVariant = m.selectedVariant,
                            imagePath = m.imagePath,
                            audioPath = m.audioPath,
                            attachments = m.attachments.orEmpty(),
                            id = id,
                            parentId = parent,
                            sources = m.sources,
                        )
                    } else {
                        m
                    },
                )
                prevId = id
            }
            if (changed) conv.messages = out
            return conv
        }

        fun resolveActivePath(conv: Conversation): List<ChatMessageEntry> {
            val nodes = conv.messages
            if (nodes.isEmpty()) return emptyList()
            val byParent = nodes.groupBy { it.parentId ?: ROOT_KEY }
            val active = conv.branchActive
            val path = ArrayList<ChatMessageEntry>(nodes.size)
            val visited = HashSet<String>()
            var key = ROOT_KEY
            while (true) {
                if (!visited.add(key)) break // cycle guard against corrupted data
                val kids = byParent[key]?.sortedBy { it.timestamp } ?: break
                if (kids.isEmpty()) break
                val chosenId = active?.get(key)
                val next = kids.firstOrNull { it.id == chosenId && it.id != null } ?: kids.first()
                path.add(next)
                val nextId = next.id ?: break
                key = nextId
            }
            return path
        }

        fun siblingsOf(
            conv: Conversation,
            msg: ChatMessageEntry,
        ): List<ChatMessageEntry> =
            conv.messages
                .filter { (it.parentId ?: ROOT_KEY) == (msg.parentId ?: ROOT_KEY) }
                .sortedBy { it.timestamp }
    }

    fun exportToMarkdown(conversation: Conversation): String =
        buildString {
            appendLine("# ${conversation.title.ifBlank { "RaikaAI Conversation" }}")
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(conversation.createdAt))
            appendLine("*Exported: $dateStr*")
            appendLine()
            resolveActivePath(conversation).forEach { msg ->
                val speaker = if (msg.role.equals(ChatConstants.ROLE_USER, ignoreCase = true)) "**User**" else "**Assistant**"
                appendLine("### $speaker")
                appendLine(msg.content.trim())
                if (!msg.stats.isNullOrBlank()) {
                    appendLine()
                    appendLine("_${msg.stats}_")
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
}
