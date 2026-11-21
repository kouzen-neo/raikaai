package com.raikaai.app.core.agent

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mirrors chats into the Alpine environment as Markdown, so Raika (and the
 * user) can grep, read or back them up from the terminal:
 *
 *   ~/.raika/chats/<chat-id>.md            normal chats
 *   ~/.raika/chats/roleplay/<chat-id>.md   roleplay chats
 *
 * The app's own JSON store stays the source of truth; these files are a
 * readable copy, rewritten on every save and removed on delete.
 */
object ChatArchive {
    const val GUEST_DIR = "/root/.raika/chats"

    data class Line(val role: String, val text: String)

    /** [raikaHome] is the host dir mounted at /root/.raika. */
    private fun baseDir(raikaHome: File): File = File(raikaHome, "chats")

    fun write(
        raikaHome: File,
        id: String,
        title: String,
        roleplay: Boolean,
        updatedAt: Long,
        lines: List<Line>,
    ) {
        val base = baseDir(raikaHome)
        val dir = if (roleplay) File(base, "roleplay") else base
        dir.mkdirs()
        val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { return }
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
        val text =
            buildString {
                append("# ").append(title.ifBlank { "Untitled chat" }.replace('\n', ' ')).append('\n')
                append("<!-- id: ").append(id).append(" · updated: ").append(date)
                append(if (roleplay) " · roleplay" else "").append(" -->\n\n")
                lines.forEach { l ->
                    val body = l.text.replace(THINK, "").trim()
                    if (body.isEmpty()) return@forEach
                    append("## ").append(l.role.replaceFirstChar { it.uppercase() }).append("\n\n")
                    append(body).append("\n\n")
                }
            }
        val target = File(dir, "$safeId.md")
        val tmp = File(dir, ".$safeId.md.${System.nanoTime()}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun delete(raikaHome: File, id: String) {
        val base = baseDir(raikaHome)
        val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { return }
        File(base, "$safeId.md").delete()
        File(base, "roleplay/$safeId.md").delete()
    }

    private val THINK = Regex("<think>[\\s\\S]*?(</think>|$)")
}
