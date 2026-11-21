package com.raikaai.app.skills

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raikaai.app.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class SkillsManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: SkillsManager? = null

        fun getInstance(context: Context): SkillsManager =
            instance ?: synchronized(this) {
                instance ?: SkillsManager(context.applicationContext).also { instance = it }
            }

        /**
         * Max characters for the combined active-skill instruction block that is
         * injected into the system prompt. Small on-device models have tight
         * context windows; without a cap, many enabled skills could silently
         * eat most of the window meant for the conversation.
         */
        private const val MAX_ACTIVE_INSTRUCTIONS_CHARS = 4000

        /**
         * Import size caps (B27). Skill files arrive from outside the app —
         * potentially huge — and are later serialized to JSON / embedded in the
         * prompt. Without bounds, one oversized file can exhaust memory or
         * silently bloat the prompt. Oversized imports are rejected outright,
         * and long instruction bodies are truncated at import time.
         */
        private const val MAX_IMPORT_BYTES = 200_000
        private const val MAX_IMPORT_CHARS = 200_000
        private const val MAX_IMPORTED_INSTRUCTIONS_CHARS = 20_000

        /** Skills in this category apply only in roleplay chats (and only there). */
        const val CATEGORY_ROLEPLAY = "Roleplay"

        /**
         * Bundled skills. Instructions are deliberately short: they are added
         * to the system prompt of small on-device models with a few thousand
         * tokens of context. All start disabled; toggle in the Skills screen
         * or with `/skills <name>` in chat.
         */
        val BUILTIN_SKILLS = listOf(
            Skill(
                id = "builtin_concise",
                name = "Concise",
                description = "Short, direct answers. No filler, no repeated question.",
                category = "Writing",
                iconCategory = "short",
                instructions = "Answer directly in as few words as the question needs. Lead with the answer, then only the details that matter. No greetings, no restating the question, no closing offers.",
                isBuiltIn = true,
            ),
            Skill(
                id = "builtin_bahasa",
                name = "Bahasa Indonesia",
                description = "Always reply in natural, casual Indonesian.",
                category = "Writing",
                iconCategory = "translate",
                instructions = "Always reply in natural, conversational Indonesian (not stiff textbook language), even if the question is in another language. Keep technical terms, code, and names in their original form.",
                isBuiltIn = true,
            ),
            Skill(
                id = "builtin_coder",
                name = "Coding",
                description = "Complete, runnable code with a short explanation.",
                category = "Coding",
                iconCategory = "code",
                instructions = "When writing code: give complete, runnable code with all imports, no placeholders. State assumptions in one line. Explain briefly after the code. When fixing a bug, name the cause first, then show only the changed parts unless asked for the full file.",
                isBuiltIn = true,
            ),
            Skill(
                id = "builtin_reasoning",
                name = "Step by step",
                description = "Works through math and logic problems carefully.",
                category = "Reasoning",
                iconCategory = "math",
                instructions = "For math, logic, and planning questions: work step by step, check each step, and verify the final answer against the question before stating it. Put the final answer on its own line, starting with \"Answer:\".",
                isBuiltIn = true,
            ),
            Skill(
                id = "builtin_tutor",
                name = "Tutor",
                description = "Teaches instead of just answering.",
                category = "General",
                iconCategory = "school",
                instructions = "Act as a patient tutor. Explain the idea with a simple example, then ask one short question to check understanding. If the learner is wrong, give a hint before the answer.",
                isBuiltIn = true,
            ),
            Skill(
                id = "builtin_linux_agent",
                name = "Linux agent",
                description = "Careful use of the Linux environment tools.",
                category = "Coding",
                iconCategory = "terminal",
                instructions = "When using the Linux environment: look before acting (ls / cat), work inside /workspace, prefer small commands, and verify each change by running it. Never repeat a command that already succeeded. Finish with a short summary of what changed.",
                isBuiltIn = true,
            ),
            Skill(
                id = "builtin_rp_immersive",
                name = "Immersive roleplay",
                description = "Vivid, in-character replies that never speak for you.",
                category = CATEGORY_ROLEPLAY,
                iconCategory = "theater",
                instructions = "Stay in character at all times. Write actions in *asterisks* and speech in quotes. Show feelings through actions and senses instead of naming them. Never write the user's actions, words, or thoughts. Keep replies 1-3 paragraphs and end on something the user can react to.",
                isBuiltIn = true,
            ),
            Skill(
                id = "builtin_rp_slowburn",
                name = "Slow-burn story",
                description = "Paces the plot slowly with consistent world details.",
                category = CATEGORY_ROLEPLAY,
                iconCategory = "book",
                instructions = "Pace the story slowly: let scenes breathe, build tension gradually, and keep names, places, and past events consistent. Introduce small complications instead of resolving everything at once.",
                isBuiltIn = true,
            ),
        )
    }

    private val gson = Gson()
    private val dir = File(context.filesDir, "skills").apply { if (!exists()) mkdirs() }
    private val customSkillsFile get() = File(dir, "custom_skills.json")
    private val presetTogglesFile get() = File(dir, "preset_toggles.json")

    /** Serializes read-modify-write cycles on the skill list plus their persistence. */
    private val stateMutex = Mutex()

    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    init {
        loadSkills()
    }

    @Synchronized
    private fun loadSkills() {
        var customCorrupt = false
        val customList: List<Skill> = try {
            if (customSkillsFile.exists()) {
                val parsed: List<Skill>? =
                    gson.fromJson(customSkillsFile.readText(), object : TypeToken<List<Skill>>() {}.type)
                parsed?.map { it.normalize() } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            customCorrupt = true
            emptyList()
        }
        // A corrupt custom_skills.json must never be silently wiped by a
        // later persist: quarantine it to .bak so user data survives.
        if (customCorrupt) {
            try {
                val bak = File(dir, "custom_skills.json.bak.${System.currentTimeMillis()}")
                customSkillsFile.copyTo(bak, overwrite = true)
                KLog.e("Skills", "custom_skills.json corrupt; quarantined to ${bak.name}")
            } catch (_: Exception) {
            }
        }

        val toggles: Map<String, Boolean> = try {
            if (presetTogglesFile.exists()) {
                gson.fromJson(presetTogglesFile.readText(), object : TypeToken<Map<String, Boolean>>() {}.type) ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }

        val builtinWithToggles = BUILTIN_SKILLS.map { preset ->
            if (toggles.containsKey(preset.id)) {
                preset.copy(isEnabled = toggles[preset.id] ?: preset.isEnabled)
            } else {
                preset
            }
        }

        // One-time migration: the Kotlin and Web-Dev presets used to default
        // to enabled; they now default to off, so switch them off once on
        // existing installs (user can re-enable them manually afterwards).
        val builtinOffMarker = File(dir, ".builtin_skills_default_off_v1")
        var builtinChanged = false
        val effectiveBuiltin =
            if (builtinOffMarker.exists()) {
                builtinWithToggles
            } else {
                try {
                    builtinOffMarker.createNewFile()
                } catch (_: Exception) {
                }
                val disabled = builtinWithToggles.map { if (it.isEnabled) it.copy(isEnabled = false) else it }
                builtinChanged = (disabled != builtinWithToggles)
                disabled
            }

        // One-time migration: custom skills used to default to enabled at
        // creation; they now default to off and previously-created ones are
        // switched off once (user can re-enable them manually).
        // v2 (RaikaAI rebrand): run the sweep again so any custom skill left
        // enabled on existing installs is switched off once.
        val migrationMarker = File(dir, ".custom_skills_default_off_v2")
        val effectiveCustom =
            if (migrationMarker.exists()) {
                customList
            } else {
                try {
                    migrationMarker.createNewFile()
                } catch (_: Exception) {
                }
                val disabled = customList.map { if (it.isEnabled) it.copy(isEnabled = false) else it }
                if (disabled != customList) persistCustom(disabled)
                disabled
            }

        _skills.value = effectiveBuiltin + effectiveCustom
        if (builtinChanged) persistPresetToggles()
    }

    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun persistCustom(customSkills: List<Skill>) {
        try {
            writeAtomically(customSkillsFile, gson.toJson(customSkills))
        } catch (e: Exception) {
            KLog.e("Skills", "Failed to persist custom skills: ${e.message}")
        }
    }

    private fun persistPresetToggles() {
        try {
            val map = _skills.value.filter { it.isBuiltIn }.associate { it.id to it.isEnabled }
            writeAtomically(presetTogglesFile, gson.toJson(map))
        } catch (e: Exception) {
            KLog.e("Skills", "Failed to persist preset toggles: ${e.message}")
        }
    }

    suspend fun saveSkill(skill: Skill): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val current = _skills.value.toMutableList()
            val index = current.indexOfFirst { it.id == skill.id }
            if (index >= 0) {
                current[index] = skill
            } else {
                current.add(skill)
            }
            _skills.value = current
            val customOnly = current.filterNot { it.isBuiltIn }
            persistCustom(customOnly)
            if (skill.isBuiltIn) {
                persistPresetToggles()
            }
        }
    }

    suspend fun toggleSkill(id: String, isEnabled: Boolean): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val current = _skills.value.map {
                if (it.id == id) it.copy(isEnabled = isEnabled) else it
            }
            _skills.value = current
            persistCustom(current.filterNot { it.isBuiltIn })
            persistPresetToggles()
        }
    }

    suspend fun deleteSkill(id: String): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val current = _skills.value.filterNot { it.id == id && !it.isBuiltIn }
            _skills.value = current
            persistCustom(current.filterNot { it.isBuiltIn })
        }
    }

    /**
     * Builds the combined instruction block for all enabled skills.
     * The block is capped at [MAX_ACTIVE_INSTRUCTIONS_CHARS]; whole skills
     * that no longer fit are omitted (with a warning log) so the conversation
     * itself keeps most of the context window.
     */
    fun getActivePromptInstructions(roleplay: Boolean = false): String {
        // Roleplay chats get only roleplay skills; normal chats never get them.
        val active = _skills.value.filter {
            it.isEnabled && it.instructions.isNotBlank() &&
                (it.category.equals(CATEGORY_ROLEPLAY, ignoreCase = true) == roleplay)
        }
        if (active.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("\n\n[ACTIVE SKILLS & SPECIALIZED DIRECTIVES]\n")
        val skipped = ArrayList<String>()
        for (skill in active) {
            val block = "### Skill: ${skill.name}\n${skill.instructions.trim()}\n\n"
            if (sb.length + block.length > MAX_ACTIVE_INSTRUCTIONS_CHARS) {
                skipped.add(skill.name)
                continue
            }
            sb.append(block)
        }
        if (skipped.isNotEmpty()) {
            sb.append("[Note: ${skipped.size} skill instruction(s) omitted to fit the model's context window: ${skipped.joinToString(", ").take(200)}]\n")
            KLog.w("Skills", "Omitted ${skipped.size} active skill(s) — combined instructions exceed $MAX_ACTIVE_INSTRUCTIONS_CHARS chars: ${skipped.joinToString()}")
        }
        return sb.toString().trimEnd()
    }

    suspend fun importFromUri(uri: Uri): Skill? = withContext(Dispatchers.IO) {
        try {
            val fileName = queryDisplayName(uri) ?: "imported_skill.md"
            // Bounded read: never pull more than MAX_IMPORT_BYTES from the
            // content provider into memory (B27).
            val text = readBoundedText(uri) ?: return@withContext null
            importFromText(text, fileName)
        } catch (e: Exception) {
            KLog.e("Skills", "Failed to import skill from URI: ${e.message}")
            null
        }
    }

    /** Reads up to [MAX_IMPORT_BYTES] from [uri]; null when over the limit or unreadable. */
    private fun readBoundedText(uri: Uri): String? =
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) {
                        KLog.e("Skills", "Skill file too large (> $MAX_IMPORT_BYTES bytes); import rejected")
                        return null
                    }
                    out.write(buf, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (e: Exception) {
            KLog.e("Skills", "Failed to read skill file: ${e.message}")
            null
        }

    suspend fun importFromText(rawText: String, fileName: String? = null): Skill? = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext null
        // Reject oversized input before parsing/serializing anything (B27).
        if (rawText.length > MAX_IMPORT_CHARS) {
            KLog.e("Skills", "Skill text too large (${rawText.length} chars > $MAX_IMPORT_CHARS); import rejected")
            return@withContext null
        }
        try {
            // Check if JSON format
            if (rawText.trimStart().startsWith("{")) {
                val raw = gson.fromJson(rawText, Skill::class.java)
                // Reject nameless imports on the RAW value: normalize() would
                // fill a fallback name and defeat this check.
                if (raw != null && !(raw.name as String?).isNullOrBlank()) {
                    val parsed = raw.normalize()
                    val skill = parsed.copy(
                        id = UUID.randomUUID().toString(),
                        name = parsed.name.take(80),
                        description = parsed.description.take(200),
                        instructions = truncateInstructions(parsed.instructions),
                        category = parsed.category.take(30),
                        isBuiltIn = false,
                        // B9: imported JSON may claim isEnabled=true; untrusted
                        // prompt text must always start disabled.
                        isEnabled = false,
                        createdAt = System.currentTimeMillis()
                    )
                    saveSkill(skill)
                    return@withContext skill
                }
            }

            // Check if Frontmatter YAML Markdown
            var name = fileName?.substringBeforeLast(".")?.replace("_", " ")?.replace("-", " ")?.capitalizeWords() ?: "Custom Skill"
            var description = "Imported skill instructions."
            var category = "General"
            var iconCategory = "code"
            var instructions = rawText.trim()

            val frontmatter = parseFrontmatter(rawText)
            if (frontmatter != null) {
                val (meta, body) = frontmatter
                instructions = body
                meta["name"]?.let { if (it.isNotBlank()) name = it }
                meta["description"]?.let { if (it.isNotBlank()) description = it }
                meta["category"]?.let { if (it.isNotBlank()) category = it }
                meta["icon"]?.let { if (it.isNotBlank()) iconCategory = it }
            } else {
                val trimmed = rawText.trimStart().removePrefix("\uFEFF")
                if (trimmed.startsWith("# ")) {
                    name = trimmed.lineSequence().first().removePrefix("# ").trim().ifBlank { name }
                    instructions = trimmed.lines().drop(1).joinToString("\n").trim()
                }
            }

            val skill = Skill(
                id = UUID.randomUUID().toString(),
                name = name.take(80),
                description = description.take(200),
                instructions = truncateInstructions(instructions),
                category = category.take(30),
                iconCategory = iconCategory,
                // Imported skills are untrusted prompt text: always default to
                // DISABLED so nothing enters the prompt until the user has
                // reviewed the instructions and explicitly enables it (B9).
                isEnabled = false,
                isBuiltIn = false,
                createdAt = System.currentTimeMillis(),
            )
            saveSkill(skill)
            skill
        } catch (e: Exception) {
            KLog.e("Skills", "Import text parsing error: ${e.message}")
            null
        }
    }

    /**
     * Truncates an imported instruction body to [MAX_IMPORTED_INSTRUCTIONS_CHARS]
     * so one giant skill can't silently bloat the persisted JSON or the prompt
     * block (B27). The cut is logged — the rest of the import still succeeds.
     */
    private fun truncateInstructions(instructions: String): String {
        if (instructions.length <= MAX_IMPORTED_INSTRUCTIONS_CHARS) return instructions
        KLog.w("Skills", "Skill instructions truncated from ${instructions.length} to $MAX_IMPORTED_INSTRUCTIONS_CHARS chars")
        return instructions.take(MAX_IMPORTED_INSTRUCTIONS_CHARS) + "\n…[truncated on import]"
    }

    suspend fun exportSkill(skill: Skill): File? = withContext(Dispatchers.IO) {
        try {
            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RaikaAI/Skills")
            if (!baseDir.exists()) baseDir.mkdirs()
            val safeName = skill.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
            val file = File(baseDir, "$safeName.md")
            val content = skill.toMarkdown()
            file.writeText(content)
            file
        } catch (e: Exception) {
            KLog.e("Skills", "Failed to export skill: ${e.message}")
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Parses a leading YAML frontmatter block delimited by `---` lines.
     * Tolerant of leading whitespace/BOM, extra spaces around the fences,
     * quoted values, and `#` comment lines. Returns the metadata map plus the
     * remaining body, or null when no valid block is present. A `---`
     * horizontal rule inside the body does not confuse it, because the closing
     * fence must sit alone on its own line.
     */
    private fun parseFrontmatter(rawText: String): Pair<Map<String, String>, String>? {
        val text = rawText.trimStart().removePrefix("\uFEFF")
        val lines = text.lines()
        if (lines.isEmpty() || lines.first().trim() != "---") return null
        val closing = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (closing < 0) return null
        val meta = mutableMapOf<String, String>()
        for (line in lines.drop(1).take(closing)) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val colon = t.indexOf(':')
            if (colon <= 0) continue
            val key = t.substring(0, colon).trim().lowercase()
            var value = t.substring(colon + 1).trim()
            if (value.length >= 2 &&
                ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'")))
            ) {
                value = value.substring(1, value.length - 1)
            }
            if (key.isNotEmpty()) meta[key] = value
        }
        val body = lines.drop(closing + 2).joinToString("\n").trim()
        return meta to body
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
}
