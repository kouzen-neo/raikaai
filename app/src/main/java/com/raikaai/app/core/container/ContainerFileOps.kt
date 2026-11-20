package com.raikaai.app.core.container

import java.io.File
import java.io.FileNotFoundException

data class FileWriteResult(
    val path: String,
    val bytes: Long,
    val lines: Int,
)

data class FileEditResult(
    val path: String,
    val occurrences: Int,
)

data class FileReadResult(
    val path: String,
    val content: String,
    val totalLines: Int,
    val startLine: Int,
    val endLine: Int,
    val truncated: Boolean,
)

object ContainerFileOps {

    /**
     * Resolves [relativePath] within [baseDir], preventing path traversal.
     * Strips leading `/workspace/`, `workspace/`, or `/`.
     */
    fun resolveWorkspaceFile(relativePath: String, baseDir: File): File {
        var clean = relativePath.trim()
        while (clean.startsWith("/workspace/") || clean.startsWith("workspace/") ||
            clean.startsWith("./") || clean.startsWith("/")
        ) {
            clean = clean.removePrefix("/workspace/")
                .removePrefix("workspace/")
                .removePrefix("./")
                .removePrefix("/")
                .trim()
        }
        if (clean.isBlank()) throw IllegalArgumentException("Empty file path")
        baseDir.mkdirs()
        val target = File(baseDir, clean).canonicalFile
        val base = baseDir.canonicalFile
        // Compare on a path-segment boundary: a bare prefix check also accepted
        // siblings such as "workspace2/" or "workspace_old/". The workspace
        // root itself is not a valid file target either.
        if (!target.path.startsWith(base.path + File.separator)) {
            throw SecurityException("Access denied: path escapes /workspace: $relativePath")
        }
        return target
    }

    /**
     * Writes [content] to [relativePath] under [baseDir], creating any
     * missing parent folders automatically. Overwrites existing files.
     */
    fun writeFile(
        relativePath: String,
        content: String,
        baseDir: File,
    ): FileWriteResult {
        val target = resolveWorkspaceFile(relativePath, baseDir)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        val relPath = target.relativeTo(baseDir).path
        val lineCount = if (content.isEmpty()) 0 else content.lines().size
        return FileWriteResult(relPath, target.length(), lineCount)
    }

    /**
     * Performs exact text replacement in [relativePath] under [baseDir].
     * If [replaceAll] is false, requires [oldString] to occur exactly once.
     */
    fun editFile(
        relativePath: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false,
        baseDir: File,
    ): FileEditResult {
        if (oldString == newString) {
            throw IllegalArgumentException("old_string and new_string must differ")
        }
        val target = resolveWorkspaceFile(relativePath, baseDir)
        if (!target.exists() || !target.isFile) {
            throw FileNotFoundException("File not found: $relativePath")
        }
        val original = target.readText(Charsets.UTF_8)
        if (!original.contains(oldString)) {
            throw IllegalArgumentException("old_string not found in $relativePath. Ensure exact indentation and whitespace match.")
        }
        var count = 0
        var idx = 0
        while (idx <= original.length - oldString.length) {
            val found = original.indexOf(oldString, idx)
            if (found < 0) break
            count++
            idx = found + oldString.length
        }
        if (count > 1 && !replaceAll) {
            throw IllegalArgumentException("old_string occurs $count times in $relativePath. Add more surrounding context to disambiguate or set replace_all: true.")
        }
        val updated = if (replaceAll) {
            original.replace(oldString, newString)
        } else {
            val pos = original.indexOf(oldString)
            original.substring(0, pos) + newString + original.substring(pos + oldString.length)
        }
        target.writeText(updated, Charsets.UTF_8)
        val relPath = target.relativeTo(baseDir).path
        return FileEditResult(relPath, count)
    }

    /**
     * Applies several (oldText -> newText) replacements in one call, pi-style:
     * every oldText is matched against the ORIGINAL file, must occur exactly
     * once, and must not overlap another edit. All or nothing.
     */
    fun multiEdit(
        relativePath: String,
        edits: List<Pair<String, String>>,
        baseDir: File,
    ): FileEditResult {
        if (edits.isEmpty()) throw IllegalArgumentException("edits[] is empty")
        val target = resolveWorkspaceFile(relativePath, baseDir)
        if (!target.exists() || !target.isFile) {
            throw FileNotFoundException("File not found: $relativePath")
        }
        val original = target.readText(Charsets.UTF_8)
        val located = edits.mapIndexed { i, (old, new) ->
            if (old.isEmpty()) throw IllegalArgumentException("edits[$i].oldText is empty")
            val at = original.indexOf(old)
            if (at < 0) {
                throw IllegalArgumentException("edits[$i].oldText not found in $relativePath. Ensure exact indentation and whitespace match.")
            }
            if (original.indexOf(old, at + 1) >= 0) {
                throw IllegalArgumentException("edits[$i].oldText occurs more than once in $relativePath. Add surrounding context to make it unique.")
            }
            Triple(at, at + old.length, new)
        }.sortedBy { it.first }
        for (k in 1 until located.size) {
            if (located[k].first < located[k - 1].second) {
                throw IllegalArgumentException("edits overlap in $relativePath. Merge nearby changes into one edit.")
            }
        }
        val sb = StringBuilder()
        var pos = 0
        for ((start, end, new) in located) {
            sb.append(original, pos, start).append(new)
            pos = end
        }
        sb.append(original, pos, original.length)
        target.writeText(sb.toString(), Charsets.UTF_8)
        return FileEditResult(target.relativeTo(baseDir).path, located.size)
    }

    /**
     * Reads lines from [relativePath] under [baseDir] with 1-based line
     * number prefixes.
     */
    fun readFile(
        relativePath: String,
        offset: Int = 1,
        limit: Int = 400,
        baseDir: File,
    ): FileReadResult {
        val target = resolveWorkspaceFile(relativePath, baseDir)
        if (!target.exists()) {
            throw FileNotFoundException("File not found: $relativePath")
        }
        if (target.isDirectory) {
            throw IllegalArgumentException("Path is a directory, not a file: $relativePath (use `ls` to list directory)")
        }
        val lines = target.readLines(Charsets.UTF_8)
        val relPath = target.relativeTo(baseDir).path
        if (lines.isEmpty()) {
            return FileReadResult(relPath, "(empty file)", 0, 0, 0, false)
        }
        val safeOffset = offset.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 2000)
        val startIdx = (safeOffset - 1).coerceAtMost(lines.size)
        val slice = lines.drop(startIdx).take(safeLimit)
        val sb = StringBuilder()
        slice.forEachIndexed { i, line ->
            sb.append("${startIdx + i + 1}: $line\n")
        }
        val endLine = startIdx + slice.size
        val truncated = endLine < lines.size
        return FileReadResult(
            path = relPath,
            content = sb.toString().trimEnd(),
            totalLines = lines.size,
            startLine = startIdx + 1,
            endLine = endLine,
            truncated = truncated,
        )
    }
}
