package com.raikaai.app.ui.component.markdown

/**
 * Keeps a reply that is still streaming in from flickering through wrong
 * layouts. Only the unfinished tail is touched, and only in ways the finished
 * text will agree with, so nothing jumps when the closer arrives.
 *
 * Mirrors the portable behavior of DSH Go's `repairStreamingMarkdown`:
 * - `**bold`, `*italic`, `~~strike` and a dangling backtick are closed for the
 *   parser; a marker with nothing after it yet is held back.
 * - `[label](https://exa` renders as the link it is becoming; a half-written
 *   image `![alt](` is held back whole.
 * - A table appears once its delimiter row is complete.
 * - A lone heading/list/rule marker, an unfinished `<tag`, or a fence opener
 *   still typing its language is held back.
 *
 * On balanced, finished text this is a no-op, so it is safe to call on every
 * composition while a reply streams.
 */
fun repairStreamingMarkdown(text: String): String {
    if (text.isEmpty()) return text
    return repairWithScan(text, scanBlocks(text))
}

/** Where the last block starts and, when the reply ends inside a fence, the fence opener line. */
private data class BlockScan(val lastBlockStart: Int, val openFenceLine: Int?)

private val FENCE_OPEN = Regex("""^[ \t]*(?:>[ \t]?)*[ \t]*(`{3,}|~{3,})(?!.*`)""")
private val FENCE_CLOSE = Regex("""^[ \t]*(?:>[ \t]?)*[ \t]*(`{3,}|~{3,})[ \t]*$""")
private val WHITESPACE_OR_QUOTE = Regex("""[\s>]""")

private fun scanBlocks(text: String): BlockScan {
    var lastBlockStart = 0
    var fenceChar: Char? = null
    var fenceLength = 0
    var openFenceLine: Int? = null

    val lines = text.split('\n')
    // "abc\n" is one line, not a line and a blank one.
    val effective = if (lines.size > 1 && lines.last().isEmpty()) lines.dropLast(1) else lines
    var offset = 0
    for (line in effective) {
        val fenceOpen = FENCE_OPEN.find(line)
        if (fenceChar != null) {
            val close = FENCE_CLOSE.find(line)
            if (close != null && close.groupValues[1].isNotEmpty() &&
                close.groupValues[1][0] == fenceChar && close.groupValues[1].length >= fenceLength
            ) {
                fenceChar = null
                lastBlockStart = offset + line.length + 1
            }
        } else if (fenceOpen != null) {
            fenceChar = fenceOpen.groupValues[1][0]
            fenceLength = fenceOpen.groupValues[1].length
            openFenceLine = offset
        } else if (line.trim().isEmpty() || line.replace(WHITESPACE_OR_QUOTE, "").isEmpty()) {
            lastBlockStart = offset + line.length + 1
        }
        offset += line.length + 1
    }
    if (lastBlockStart > text.length) lastBlockStart = text.length
    return BlockScan(lastBlockStart, if (fenceChar == null) null else openFenceLine)
}

private fun repairWithScan(text: String, scan: BlockScan): String {
    val openFence = scan.openFenceLine
    if (openFence != null) {
        // Inside an open fence code streams as itself, except the opener line
        // while its info string is still being typed.
        val newline = text.indexOf('\n', openFence)
        if (newline == -1) return text.substring(0, openFence).trimEnd()
        return text
    }

    val start = scan.lastBlockStart
    val originalTail = text.substring(start)
    var tail = originalTail
    val table = unfinishedTableAt(tail)
    if (table != -1) tail = tail.substring(0, table).trimEnd()
    tail = dropPartialLastLine(tail)
    tail = repairInline(tail)
    if (tail.trim().isEmpty()) return text.substring(0, start).trimEnd()
    if (tail == originalTail) return text
    return text.substring(0, start) + tail
}

private val TABLE_ROW = Regex("""^[ \t]*(?:>[ \t]?)*[ \t]*\|""")
private val PENDING_DELIMITER = Regex("""^[ \t]*(?:>[ \t]?)*[ \t]*:?-""")
private val DELIMITER_CELL = Regex("""^\s*:?-+:?\s*$""")
private val LEADING_QUOTES = Regex("""^(?:>[ \t]?)*""")
private val UNESCAPED_PIPE = Regex("""(?<!\\)\|""")
private val TRAILING_NEWLINES = Regex("""\n+$""")

/** Offset at which a table whose delimiter row is still arriving begins, or -1. */
private fun unfinishedTableAt(tail: String): Int {
    val lines = tail.replace(TRAILING_NEWLINES, "").split('\n')
    var first = lines.size
    while (first > 0 && TABLE_ROW.containsMatchIn(lines[first - 1])) first--
    if (first == lines.size && first > 0 && PENDING_DELIMITER.containsMatchIn(lines.last())) {
        first--
        while (first > 0 && TABLE_ROW.containsMatchIn(lines[first - 1])) first--
    }
    if (first >= lines.size || !TABLE_ROW.containsMatchIn(lines[first])) return -1
    var offset = 0
    for (i in 0 until first) offset += lines[i].length + 1
    if (first + 1 >= lines.size) return offset

    val header = cells(lines[first])
    val delimiter = cells(lines[first + 1])
    if (delimiter.size < header.size || !delimiter.all { DELIMITER_CELL.matches(it) }) return offset
    return -1
}

private fun cells(row: String): List<String> {
    var text = row.trim().replace(LEADING_QUOTES, "").trim()
    if (text.startsWith("|")) text = text.substring(1)
    if (text.endsWith("|") && !text.endsWith("\\|")) text = text.substring(0, text.length - 1)
    if (text.trim().isEmpty()) return emptyList()
    return text.split(UNESCAPED_PIPE)
}

/** A line that is only the start of a construct: a marker with no text yet. */
private val PARTIAL_LINE = Regex(
    """^[ \t]*(?:>[ \t]?)*[ \t]*(?:#{1,6}|[-*+]|\d{1,9}[.)]|=+|-{2}|_{1,2}|\*{2}|`{1,2}|~{1,2}|>|\${'$'}{1,2})?[ \t]*$""",
)

private fun dropPartialLastLine(tail: String): String {
    val cut = tail.lastIndexOf('\n')
    val last = tail.substring(cut + 1)
    if (last.isEmpty() || !PARTIAL_LINE.matches(last)) return tail
    return if (cut == -1) "" else tail.substring(0, cut)
}

private data class Opener(val marker: String, val at: Int)

private val TAG_TAIL = Regex("""<[A-Za-z/!][^<>\n]*$""")

/** One scan of the last block, tracking what the inline parser left open. */
private class InlineScan(val text: String) {
    val openers = mutableListOf<Opener>()
    var openCode: Int? = null
    var codeRun = 0
    var openLinkTarget: Int? = null
    var openImage: Int? = null
    var dangling: Int? = null

    init {
        val brackets = ArrayList<Int>()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < n -> {
                    val next = text[i + 1]
                    // Escaped maths parentheses/brackets are opaque.
                    if (next == '(' || next == '[') {
                        val close = text.indexOf(if (next == '(') "\\)" else "\\]", i + 2)
                        if (close == -1) break
                        i = close + 2
                        continue
                    }
                    i += 2
                    continue
                }
                c == '`' -> {
                    var run = 0
                    while (i + run < n && text[i + run] == '`') run++
                    val close = closingRun(text, i + run, run)
                    if (close == -1) {
                        openCode = i
                        codeRun = run
                        break
                    }
                    i = close + run
                    continue
                }
                c == '*' || c == '_' || c == '~' -> {
                    i = delimiterRun(i, c)
                    continue
                }
                c == '[' -> brackets.add(i)
                c == ']' && brackets.isNotEmpty() -> {
                    val label = brackets.removeAt(brackets.size - 1)
                    if (i + 1 < n && text[i + 1] == '(') {
                        val close = text.indexOfAny(charArrayOf(')', '\n'), i + 2)
                        if (close == -1 || text[close] == '\n') {
                            if (label > 0 && text[label - 1] == '!') openImage = label - 1
                            else openLinkTarget = i + 1
                            break
                        }
                        i = close + 1
                        continue
                    }
                }
            }
            i++
        }
        // An image label still being typed ("![al").
        for (b in brackets) {
            if (b > 0 && text[b - 1] == '!') {
                openImage = if (openImage == null || b - 1 < openImage!!) b - 1 else openImage
            }
        }
    }

    /** Where a half-written image/HTML tag or an empty marker forces a cut. */
    val cut: Int
        get() {
            var at = text.length
            dangling?.let { if (it < at) at = it }
            openImage?.let { if (it < at) at = it }
            val code = openCode
            if (code != null && code < at && text.substring(code + codeRun).trim().isEmpty()) at = code
            val scanned = code ?: openLinkTarget ?: at
            val prefix = text.substring(0, minOf(scanned, at))
            val tag = TAG_TAIL.find(prefix)
            if (tag != null && tag.range.first < at) at = tag.range.first
            for (opener in openers) {
                if (opener.at < at && text.substring(opener.at + opener.marker.length, at).trim().isEmpty()) {
                    at = opener.at
                    break
                }
            }
            return at
        }

    private fun delimiterRun(i: Int, c: Char): Int {
        val n = text.length
        var run = 0
        while (i + run < n && text[i + run] == c) run++
        val before = if (i == 0) '\n' else text[i - 1]
        val after = if (i + run < n) text[i + run] else '\n'
        val leftFlanking = !isSpace(after) && (!isPunct(after) || isSpace(before) || isPunct(before))
        val rightFlanking = !isSpace(before) && (!isPunct(before) || isSpace(after) || isPunct(after))
        var canOpen = leftFlanking
        var canClose = rightFlanking
        if (c == '_') {
            // snake_case is not emphasis.
            canOpen = leftFlanking && (!rightFlanking || isPunct(before))
            canClose = rightFlanking && (!leftFlanking || isPunct(after))
        }
        val markers: List<String> = when {
            c == '~' && run == 2 -> listOf("~~")
            c == '~' -> emptyList()
            run == 1 -> listOf(c.toString())
            run == 2 -> listOf(c.toString().repeat(2))
            run == 3 -> listOf(c.toString().repeat(2), c.toString())
            else -> emptyList()
        }
        var used = false
        for (marker in markers) {
            val open = if (canClose) openers.indexOfLast { it.marker == marker } else -1
            if (open != -1) {
                while (openers.size > open) openers.removeAt(openers.size - 1)
                used = true
            } else if (canOpen) {
                val at = i + if (marker.length < run && marker.length == 1) run - 1 else 0
                openers.add(Opener(marker, at))
                used = true
            }
        }
        if (!used && text.substring(i + run).trim().isEmpty()) dangling = i
        return i + run
    }
}

private fun repairInline(tail: String): String {
    var text = tail
    var scan = InlineScan(text)
    // Each cut can expose another unfinished marker ("**a *" becomes "**a").
    for (guard in 0 until 8) {
        val cut = scan.cut
        if (cut >= text.length) break
        text = text.substring(0, cut).trimEnd()
        scan = InlineScan(text)
    }
    val link = scan.openLinkTarget
    if (link != null) {
        // The label renders as the link it is becoming; "#" opens nothing.
        text = text.substring(0, link) + "(#)"
    }
    val code = scan.openCode
    if (code == null && scan.openers.isEmpty()) return text
    val out = StringBuilder(text.trimEnd())
    if (code != null) out.append("`".repeat(scan.codeRun))
    for (opener in scan.openers.asReversed()) out.append(opener.marker)
    return out.toString()
}

private fun closingRun(text: String, from: Int, run: Int): Int {
    var k = from
    while (true) {
        val close = text.indexOf('`', k)
        if (close == -1) return -1
        var len = 0
        while (close + len < text.length && text[close + len] == '`') len++
        if (len == run) return close
        k = close + len
    }
}

private fun isSpace(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u00a0' || c == '\u3000'

private fun isPunct(c: Char): Boolean {
    val unit = c.code
    if (unit < 0x80) return c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'
    return unit in 0x3000..0x303F || unit in 0xFF00..0xFF65 || unit == 0x2014 || unit == 0x2026
}
