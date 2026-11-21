package com.raikaai.app.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.raikaai.app.data.ChatAttachment
import com.raikaai.app.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A not-yet-processed attachment picked by the user.
 */
data class PendingAttachment(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val isImage: Boolean,
)

/**
 * A persisted attachment ready to send, plus any text extracted from it
 * (documents) that should be injected into the prompt.
 */
data class PreparedAttachment(
    val attachment: ChatAttachment,
    val extractedText: String?,
)

/**
 * Turns user-picked URIs into app-private files:
 * - Images are downscaled to JPEG (max 1024px) for vision.
 * - Text-like files (.txt/.md/.json/.csv/.log/code) are read as text (capped).
 * - PDFs are rendered to page images (capped) so vision models can read them.
 * - Anything else is copied verbatim with a "preview unsupported" note.
 *
 * All heavy work runs on Dispatchers.IO.
 */
object AttachmentProcessor {
    const val MAX_IMAGE_DIM = 1024
    const val MAX_TEXT_CHARS_PER_FILE = 1_000_000
    const val MAX_TOTAL_TEXT_CHARS = 2_000_000
    const val MAX_PDF_PAGES = 5
    const val PDF_RENDER_WIDTH = 1024
    /** Hard cap per decompressed FlateDecode PDF stream to prevent zip-bomb OOMs. */
    const val MAX_PDF_DECOMPRESSED_BYTES = 4_000_000

    private val TEXT_EXTENSIONS =
        setOf(
            "txt", "md", "markdown", "json", "csv", "tsv", "log",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx",
            "html", "htm", "xml", "css", "c", "h", "cpp", "rs",
            "go", "sh", "yaml", "yml", "toml", "ini", "cfg", "sql",
        )

    fun resolveDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        }
    }

    fun buildPending(context: Context, uri: Uri, forceImage: Boolean = false): PendingAttachment {
        val mime = context.contentResolver.getType(uri)
        val name = resolveDisplayName(context, uri)
        val isImage = forceImage || (mime?.startsWith("image/") == true)
        return PendingAttachment(uri, name, mime, isImage)
    }

    suspend fun prepare(context: Context, pending: List<PendingAttachment>): List<PreparedAttachment> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<PreparedAttachment>()
            var textBudget = MAX_TOTAL_TEXT_CHARS
            for (p in pending) {
                try {
                    when {
                        p.isImage -> prepareImage(context, p)?.let { out += it }
                        p.mime == "application/pdf" || p.name.endsWith(".pdf", ignoreCase = true) -> {
                            val pdfItems = preparePdf(context, p, textBudget)
                            pdfItems.forEach { item ->
                                item.extractedText?.let { textBudget = (textBudget - it.length).coerceAtLeast(0) }
                            }
                            out += pdfItems
                        }
                        isTextLike(p) -> {
                            val text = readTextCapped(context, p.uri, MAX_TEXT_CHARS_PER_FILE)
                            if (text != null) {
                                val file = copyToFilesDir(context, p)
                                val allowed = text.take(textBudget)
                                textBudget -= allowed.length
                                out +=
                                    PreparedAttachment(
                                        ChatAttachment("file", file.absolutePath, p.name, p.mime),
                                        allowed.ifBlank { null },
                                    )
                            }
                        }
                        else -> {
                            val file = copyToFilesDir(context, p)
                            out +=
                                PreparedAttachment(
                                    ChatAttachment("file", file.absolutePath, p.name, p.mime),
                                    null,
                                )
                        }
                    }
                } catch (t: Throwable) {
                    KLog.e("Attachments", "Failed to prepare ${p.name}", t)
                }
            }
            out
        }

    private fun isTextLike(p: PendingAttachment): Boolean {
        if (p.mime?.startsWith("text/") == true) return true
        val ext = p.name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private fun imagesDir(context: Context): File =
        File(context.filesDir, "chat_images").apply { if (!exists()) mkdirs() }

    private fun filesDir(context: Context): File =
        File(context.filesDir, "chat_files").apply { if (!exists()) mkdirs() }

    private fun workspaceUploadsDir(context: Context): File =
        File(context.filesDir, "workspace/chat_uploads").apply { if (!exists()) mkdirs() }

    /**
     * Mirrors the ORIGINAL attachment bytes into /workspace/chat_uploads with
     * the original filename, so the Linux sandbox and AI tools can use chat
     * attachments directly. Never breaks the send flow: failures only log.
     */
    private fun mirrorToWorkspace(context: Context, p: PendingAttachment) {
        runCatching {
            val safe = p.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(com.raikaai.app.data.AppLimits.SAFE_FILENAME_CHARS).ifBlank { "file" }
            val dir = workspaceUploadsDir(context)
            var dest = File(dir, safe)
            if (dest.exists()) dest = File(dir, "${System.currentTimeMillis()}_$safe")
            context.contentResolver.openInputStream(p.uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }.onFailure { KLog.w("Attachments", "Workspace mirror skipped for ${p.name}: ${it.message}") }
    }

    private fun prepareImage(context: Context, p: PendingAttachment): PreparedAttachment? {
        mirrorToWorkspace(context, p)
        val dest = File(imagesDir(context), "img_${System.currentTimeMillis()}_${p.name.hashCode().toString(16)}.jpg")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(p.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        var sampleSize = 1
        val maxOriginal = maxOf(options.outWidth, options.outHeight)
        while ((maxOriginal / (sampleSize * 2)) >= MAX_IMAGE_DIM) sampleSize *= 2
        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        val bitmap =
            context.contentResolver.openInputStream(p.uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null
        val finalBitmap =
            if (bitmap.width > MAX_IMAGE_DIM || bitmap.height > MAX_IMAGE_DIM) {
                val ratio = MAX_IMAGE_DIM.toFloat() / maxOf(bitmap.width, bitmap.height)
                val scaled =
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt().coerceAtLeast(1),
                        (bitmap.height * ratio).toInt().coerceAtLeast(1),
                        true,
                    )
                if (scaled != bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        return try {
            dest.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            finalBitmap.recycle()
            PreparedAttachment(ChatAttachment("image", dest.absolutePath, p.name, "image/jpeg"), null)
        } catch (t: Throwable) {
            KLog.e("Attachments", "Failed to persist image ${p.name}", t)
            finalBitmap.recycle()
            null
        }
    }

    private fun preparePdf(context: Context, p: PendingAttachment, remainingBudget: Int): List<PreparedAttachment> {
        val out = mutableListOf<PreparedAttachment>()
        val savedPdfFile = copyToFilesDir(context, p)
        val cap = minOf(MAX_TEXT_CHARS_PER_FILE, remainingBudget.coerceAtLeast(0))
        val extractedText = if (cap > 0) extractPdfText(savedPdfFile, cap) else null

        // 1. Always retain the original document attachment so it is never dropped
        out +=
            PreparedAttachment(
                ChatAttachment("file", savedPdfFile.absolutePath, p.name, "application/pdf"),
                extractedText?.ifBlank { null },
            )

        // 2. Render up to MAX_PDF_PAGES from seekable local file descriptor for vision models / previews
        try {
            val pfd = ParcelFileDescriptor.open(savedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                try {
                    val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (i in 0 until pages) {
                        renderer.openPage(i).use { page ->
                            val ratio = PDF_RENDER_WIDTH.toFloat() / page.width
                            val h = (page.height * ratio).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(PDF_RENDER_WIDTH, h, Bitmap.Config.RGB_565)
                            // White background: PDFs can have transparency.
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val dest =
                                File(
                                    imagesDir(context),
                                    "pdf_${System.currentTimeMillis()}_p${i + 1}.jpg",
                                )
                            dest.outputStream().use { out2 ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out2)
                            }
                            bitmap.recycle()
                            out +=
                                PreparedAttachment(
                                    ChatAttachment("image", dest.absolutePath, "${p.name} (p${i + 1})", "image/jpeg"),
                                    null,
                                )
                        }
                    }
                } finally {
                    renderer.close()
                }
            }
        } catch (t: Throwable) {
            KLog.w("Attachments", "Visual rendering of PDF ${p.name} skipped: ${t.message}")
        }
        return out
    }

    fun extractPdfText(file: File, maxChars: Int): String? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val maxScanBytes = minOf(file.length(), 32 * 1024 * 1024L).toInt()
            val bytes = ByteArray(maxScanBytes)
            val readCount = file.inputStream().use { it.read(bytes) }
            if (readCount <= 0) return null
            val actualBytes = if (readCount < bytes.size) bytes.copyOf(readCount) else bytes
            val sb = StringBuilder()

            var idx = 0
            val streamMarker = "stream".toByteArray(Charsets.ISO_8859_1)
            val endStreamMarker = "endstream".toByteArray(Charsets.ISO_8859_1)

            while (idx < actualBytes.size && sb.length < maxChars) {
                val streamStart = findBytes(actualBytes, streamMarker, idx)
                if (streamStart < 0) break

                var contentStart = streamStart + 6
                if (contentStart < actualBytes.size && actualBytes[contentStart] == '\r'.code.toByte()) contentStart++
                if (contentStart < actualBytes.size && actualBytes[contentStart] == '\n'.code.toByte()) contentStart++

                val streamEnd = findBytes(actualBytes, endStreamMarker, contentStart)
                if (streamEnd < 0) break

                val headerStart = maxOf(0, streamStart - 400)
                val header = String(actualBytes, headerStart, streamStart - headerStart, Charsets.ISO_8859_1)
                val isFlate = header.contains("/FlateDecode") || header.contains("/Fl")

                val streamLen = streamEnd - contentStart
                if (streamLen in 1..2_000_000) {
                    val streamData = actualBytes.copyOfRange(contentStart, streamEnd)
                    val decompressed: ByteArray? =
                        if (isFlate) {
                            inflateBounded(streamData, MAX_PDF_DECOMPRESSED_BYTES)
                        } else {
                            streamData
                        }

                    if (decompressed != null && decompressed.isNotEmpty()) {
                        val streamStr = String(decompressed, Charsets.ISO_8859_1)
                        if (streamStr.contains("BT") && streamStr.contains("ET")) {
                            extractTextFromPdfStream(streamStr, sb, maxChars)
                        }
                    }
                }

                idx = streamEnd + 9
            }

            val text = sb.toString().trim()
            if (text.isNotBlank()) text.take(maxChars) else null
        } catch (t: Throwable) {
            KLog.w("Attachments", "PDF text extraction failed: ${t.message}")
            null
        }
    }

    /**
     * Inflates a FlateDecode stream with a strict upper bound on output bytes
     * so a high-ratio compressed stream (PDF decompression bomb) cannot OOM.
     */
    internal fun inflateBounded(streamData: ByteArray, maxBytes: Int): ByteArray? {
        fun tryInflate(nowrap: Boolean): ByteArray? {
            val inf = java.util.zip.Inflater(nowrap)
            return try {
                inf.setInput(streamData)
                val out = ByteArrayOutputStream(minOf(streamData.size * 2 + 256, maxBytes))
                val buf = ByteArray(16_384)
                var total = 0
                while (!inf.finished() && total < maxBytes) {
                    val want = minOf(buf.size, maxBytes - total)
                    val n = inf.inflate(buf, 0, want)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        total += n
                    } else if (inf.needsInput() || inf.needsDictionary()) {
                        break
                    }
                }
                if (total > 0) out.toByteArray() else null
            } catch (_: Exception) {
                null
            } finally {
                inf.end()
            }
        }
        return tryInflate(nowrap = false) ?: tryInflate(nowrap = true)
    }

    private fun findBytes(source: ByteArray, target: ByteArray, start: Int = 0): Int {
        if (target.isEmpty() || start < 0 || start >= source.size) return -1
        val limit = source.size - target.size
        for (i in start..limit) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun extractTextFromPdfStream(stream: String, sb: StringBuilder, maxChars: Int) {
        val btEtRegex = Regex("""BT\b([\s\S]*?)\bET""")
        for (btMatch in btEtRegex.findAll(stream)) {
            if (sb.length >= maxChars) break
            val block = btMatch.groupValues[1]
            var pos = 0
            while (pos < block.length && sb.length < maxChars) {
                if (block.startsWith("T*", pos) || block.startsWith("Td", pos) || block.startsWith("TD", pos)) {
                    if (sb.isNotEmpty() && !sb.endsWith("\n") && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                }

                if (block[pos] == '[') {
                    val endBracket = block.indexOf(']', pos)
                    if (endBracket > pos) {
                        val arrayContent = block.substring(pos + 1, endBracket)
                        val tokens = Regex("""\(((?:[^\\)]|\\.)*)\)|(-?\d+(?:\.\d+)?)""").findAll(arrayContent)
                        val line = StringBuilder()
                        for (token in tokens) {
                            val strVal = token.groups[1]?.value
                            val numVal = token.groups[2]?.value
                            if (strVal != null) {
                                line.append(unescapePdfString(strVal))
                            } else if (numVal != null) {
                                val num = numVal.toFloatOrNull() ?: 0f
                                if (num < -100f && line.isNotEmpty() && !line.endsWith(" ")) {
                                    line.append(" ")
                                }
                            }
                        }
                        val str = line.toString().trim()
                        if (str.isNotBlank()) {
                            if (sb.isNotEmpty() && !sb.endsWith("\n") && !sb.endsWith(" ")) sb.append(" ")
                            sb.append(str)
                        }
                        pos = endBracket + 1
                        continue
                    }
                }

                if (block[pos] == '(') {
                    var pEnd = pos + 1
                    var depth = 1
                    while (pEnd < block.length) {
                        if (block[pEnd] == '\\') {
                            pEnd += 2
                            continue
                        }
                        if (block[pEnd] == '(') depth++
                        else if (block[pEnd] == ')') {
                            depth--
                            if (depth == 0) break
                        }
                        pEnd++
                    }
                    if (pEnd < block.length && depth == 0) {
                        val rawStr = block.substring(pos + 1, pEnd)
                        val after = block.substring(pEnd + 1).trimStart()
                        if (after.startsWith("Tj") || after.startsWith("'") || after.startsWith("\"")) {
                            val unescaped = unescapePdfString(rawStr)
                            if (unescaped.isNotBlank()) {
                                if (sb.isNotEmpty() && !sb.endsWith("\n") && !sb.endsWith(" ")) sb.append(" ")
                                sb.append(unescaped.trim())
                            }
                        }
                        pos = pEnd + 1
                        continue
                    }
                }
                pos++
            }
            if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                sb.append("\n")
            }
        }
    }

    private fun unescapePdfString(input: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                val next = input[i + 1]
                when (next) {
                    'n' -> { out.append('\n'); i += 2 }
                    'r' -> { out.append('\r'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'b' -> { out.append('\b'); i += 2 }
                    'f' -> { out.append('\u000C'); i += 2 }
                    '(' -> { out.append('('); i += 2 }
                    ')' -> { out.append(')'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    in '0'..'7' -> {
                        var octal = ""
                        var j = i + 1
                        while (j < input.length && j < i + 4 && input[j] in '0'..'7') {
                            octal += input[j]
                            j++
                        }
                        val code = octal.toIntOrNull(8) ?: 32
                        out.append(code.toChar())
                        i = j
                    }
                    else -> { out.append(next); i += 2 }
                }
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun readTextCapped(context: Context, uri: Uri, maxChars: Int): String? {
        // Bounded read: never pull more than ~maxChars (+ one buffer) from the
        // provider into memory. A multi-GB mislabeled file must not OOM first
        // and get truncated after (previous code called readBytes() unbounded).
        val maxBytes = maxChars + com.raikaai.app.data.AppLimits.TEXT_FILE_BUFFER
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(com.raikaai.app.data.AppLimits.TEXT_FILE_BUFFER)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) {
                        KLog.w("Attachments", "Text file exceeds cap; truncating")
                        break
                    }
                    out.write(buf, 0, n)
                }
                val bytes = out.toByteArray()
                // Strict UTF-8 check first so invalid byte sequences genuinely
                // fall back to ISO-8859-1 instead of silently inserting U+FFFD.
                val text =
                    try {
                        Charsets.UTF_8.newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(bytes))
                            .toString()
                    } catch (_: java.nio.charset.CharacterCodingException) {
                        String(bytes, Charsets.ISO_8859_1)
                    }
                // Strip NULs/control garbage that sometimes appears in mislabeled files.
                text.replace("\u0000", "").take(maxChars)
            }
        } catch (t: Throwable) {
            KLog.e("Attachments", "Failed to read text file", t)
            null
        }
    }

    private fun copyToFilesDir(context: Context, p: PendingAttachment): File {
        val safeName = p.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(com.raikaai.app.data.AppLimits.SAFE_FILENAME_CHARS).ifBlank { "file" }
        val dest = File(filesDir(context), "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(p.uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        // Mirror from the already-copied app file instead of re-reading the SAF stream.
        runCatching {
            val dir = workspaceUploadsDir(context)
            var mirror = File(dir, safeName)
            if (mirror.exists()) mirror = File(dir, "${System.currentTimeMillis()}_$safeName")
            dest.inputStream().use { input -> mirror.outputStream().use { input.copyTo(it) } }
        }.onFailure { KLog.w("Attachments", "Workspace mirror skipped for ${p.name}: ${it.message}") }
        return dest
    }
}
