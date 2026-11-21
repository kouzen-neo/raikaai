package com.raikaai.app.core.agent

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Raika's own configuration, in the style of modern agent harnesses
 * (Claude Code, pi, Codex): plain Markdown files the user can read and edit.
 *
 * - SOUL.md   who Raika is: identity, tone, values. Used when the "Raika AI"
 *             persona is active (a character persona replaces it).
 * - AGENTS.md standing instructions for every chat (like a global AGENTS.md).
 *             /workspace/AGENTS.md stays the project file for terminal work.
 * - MEMORY.md durable facts about the user, written by the `remember` tool
 *             or edited by hand.
 *
 * Files live in filesDir/agent; nothing leaves the device.
 */
class AgentConfig private constructor(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "agent")
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Materialize default SOUL/AGENTS off the main thread so Raika sees
        // them at /root/.raika in Linux without blocking UI initialization.
        ioScope.launch {
            runCatching {
                dir.mkdirs()
                listOf(Doc.SOUL, Doc.AGENTS).forEach { d ->
                    val f = file(d)
                    if (!f.exists()) writeAtomically(f, defaultFor(d))
                }
            }
        }
    }

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    enum class Doc(val fileName: String, val title: String, val help: String) {
        SOUL("SOUL.md", "Soul", "Who Raika is: identity, tone and values. Used with the Raika AI persona."),
        AGENTS("AGENTS.md", "Instructions", "Standing instructions for every chat, like a global AGENTS.md."),
        MEMORY("MEMORY.md", "Memory", "Facts Raika keeps about you. Raika adds to it with the remember tool."),
    }

    fun file(doc: Doc): File = File(dir, doc.fileName)

    fun read(doc: Doc): String {
        val f = file(doc)
        if (!f.isFile) return defaultFor(doc)
        return runCatching { f.readText() }.getOrDefault("")
    }

    fun write(doc: Doc, text: String) {
        writeAtomically(file(doc), text)
    }

    fun resetToDefault(doc: Doc) {
        if (doc == Doc.MEMORY) file(doc).delete() else writeAtomically(file(doc), defaultFor(doc))
    }

    /** Appends one line to MEMORY.md; returns the stored line. */
    @Synchronized
    fun remember(fact: String): String {
        val clean = fact.replace(Regex("\\s+"), " ").trim().take(MAX_FACT_CHARS)
        require(clean.isNotEmpty()) { "Nothing to remember" }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val current = read(Doc.MEMORY).trimEnd()
        val line = "- $clean ($date)"
        if (current.lines().any { it.substringBeforeLast(" (").trim() == "- $clean" }) return line
        val base = if (current.isBlank()) MEMORY_HEADER else current
        write(Doc.MEMORY, "$base\n$line\n")
        return line
    }

    /**
     * The agent section of the system prompt. [compact] is for on-device
     * LiteRT models, whose context is small: every part is capped hard and
     * the capability list stays one line.
     */
    fun promptBlock(
        includeSoul: Boolean,
        roleplay: Boolean,
        compact: Boolean,
        capabilities: List<String>,
    ): String {
        val soulCap = if (compact) 700 else 4000
        val agentsCap = if (compact) 600 else 6000
        val memoryCap = if (compact) 500 else 4000
        return buildString {
            if (!roleplay) {
                if (includeSoul) meaningful(read(Doc.SOUL), soulCap)?.let { append(it).append("\n\n") }
                meaningful(read(Doc.AGENTS), agentsCap)?.let { append("<instructions>\n").append(it).append("\n</instructions>\n\n") }
            }
            // Roleplay keeps only a short memory so the character knows the user.
            meaningful(read(Doc.MEMORY), if (roleplay) 400 else memoryCap, keepTail = true)?.let {
                append("<memory about=\"the user\">\n").append(it).append("\n</memory>\n\n")
            }
            if (!roleplay && capabilities.isNotEmpty()) {
                if (compact) {
                    append("Available: ").append(capabilities.joinToString("; ")).append(".\n")
                } else {
                    append("<capabilities>\n")
                    capabilities.forEach { append("- ").append(it).append('\n') }
                    append("</capabilities>\n")
                }
            }
        }.trim()
    }

    /** Text without Markdown comments and placeholder lines; null when empty. */
    private fun meaningful(
        text: String,
        cap: Int,
        keepTail: Boolean = false,
    ): String? {
        val body =
            text.replace(Regex("<!--[\\s\\S]*?-->"), "")
                .trim()
        val stripped = body.lines().filterNot { it.trim().startsWith("#") }.joinToString("\n").trim()
        if (stripped.isEmpty()) return null
        if (body.length <= cap) return body
        return if (keepTail) "…\n" + body.takeLast(cap).substringAfter('\n') else body.take(cap).substringBeforeLast('\n') + "\n…"
    }

    private fun defaultFor(doc: Doc): String =
        when (doc) {
            Doc.SOUL -> DEFAULT_SOUL
            Doc.AGENTS -> DEFAULT_AGENTS
            Doc.MEMORY -> ""
        }

    companion object {
        private const val MAX_FACT_CHARS = 300
        private const val MEMORY_HEADER = "# Memory"

        val DEFAULT_SOUL =
            """
            # Raika
            You are Raika (RaikaAI), the user's assistant on their Android phone.
            - Warm, direct and practical. Reply in the user's language.
            - Do the work instead of describing it: use your tools (web search, the Linux workspace, MCP servers) when they help.
            - Never invent tool output, files or results; say what you ran and what happened.
            - Short answers by default; go deep when asked.
            - When you learn a lasting fact or preference about the user, save it with the remember tool.
            """.trimIndent()

        val DEFAULT_AGENTS =
            """
            # Instructions
            <!-- Instructions here apply to every chat with Raika.
                 Example: "Answer in Indonesian. Prefer TypeScript for web code." -->
            """.trimIndent()

        @Volatile
        private var instance: AgentConfig? = null

        fun get(context: Context): AgentConfig =
            instance ?: synchronized(this) { instance ?: AgentConfig(context).also { instance = it } }
    }
}
