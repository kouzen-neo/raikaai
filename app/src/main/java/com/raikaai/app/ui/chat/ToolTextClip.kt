package com.raikaai.app.ui.chat

/**
 * Pure result of [clipToolText]. When [tail] is null the text was not clipped
 * and [head] is the whole input. Otherwise only [head] and [tail] should be
 * laid out; the middle [hiddenLines] lines (or [hiddenChars] characters for a
 * single over-long line) are reachable through the "View all" callback.
 */
data class ClippedText(
    val head: String,
    val tail: String?,
    val totalLines: Int,
    val hiddenLines: Int = 0,
    val hiddenChars: Int = 0,
) {
    val clipped: Boolean get() = tail != null
}

const val TOOL_CLIP_MAX_CHARS = 16_000
const val TOOL_CLIP_HEAD_LINES = 60
const val TOOL_CLIP_TAIL_LINES = 140

private fun countNewlines(
    text: String,
    start: Int,
    end: Int,
): Int {
    var count = 0
    var i = text.indexOf('\n', start)
    while (i >= 0 && i < end) {
        count++
        i = text.indexOf('\n', i + 1)
    }
    return count
}

/** True when cutting at [index] would split a UTF-16 surrogate pair. */
private fun splitsSurrogate(
    text: String,
    index: Int,
): Boolean =
    index > 0 &&
        index < text.length &&
        Character.isHighSurrogate(text[index - 1]) &&
        Character.isLowSurrogate(text[index])

private fun cutBefore(
    text: String,
    index: Int,
): Int = if (splitsSurrogate(text, index)) index - 1 else index

private fun cutAfter(
    text: String,
    index: Int,
): Int = if (splitsSurrogate(text, index)) index + 1 else index

/**
 * Keeps only the head and tail of a long tool output, mirroring DSH Go's
 * `clipLongText`: head/tail are whole lines when possible, the character
 * budget favours the tail (60/40 split) because errors and conclusions usually
 * land at the end. A single line that exceeds the budget is cut by character,
 * never breaking a surrogate pair.
 *
 * This is a pure function with no Android/Compose dependency so it can be
 * unit-tested on the JVM.
 */
fun clipToolText(
    text: String,
    maxChars: Int = TOOL_CLIP_MAX_CHARS,
    headLines: Int = TOOL_CLIP_HEAD_LINES,
    tailLines: Int = TOOL_CLIP_TAIL_LINES,
): ClippedText {
    if (maxChars <= 0) return ClippedText(head = "", tail = null, totalLines = 0)

    val totalLines = countNewlines(text, 0, text.length) + 1
    if (text.length <= maxChars && totalLines <= headLines + tailLines) {
        return ClippedText(head = text, tail = null, totalLines = totalLines)
    }

    val headBudget = maxChars * 3 / 10
    val tailBudget = maxChars - headBudget

    var headEnd = 0
    repeat(headLines) {
        val nl = text.indexOf('\n', headEnd)
        if (nl < 0 || nl >= headBudget) return@repeat
        headEnd = nl + 1
    }
    if (headEnd == 0) headEnd = cutBefore(text, headBudget.coerceIn(0, text.length))

    var tailStart = text.length
    repeat(tailLines) {
        if (tailStart <= 0) return@repeat
        val start = if (tailStart < 2) 0 else text.lastIndexOf('\n', tailStart - 2) + 1
        if (text.length - start > tailBudget) return@repeat
        tailStart = start
    }
    if (tailStart == text.length) {
        tailStart = cutAfter(text, (text.length - tailBudget).coerceIn(0, text.length))
    }

    // Head and tail overlapped (e.g. a pathological line budget); show all.
    if (headEnd >= tailStart) return ClippedText(head = text, tail = null, totalLines = totalLines)

    val rawHead = text.substring(0, headEnd)
    val head = if (rawHead.endsWith('\n')) rawHead.dropLast(1) else rawHead
    return ClippedText(
        head = head,
        tail = text.substring(tailStart),
        totalLines = totalLines,
        hiddenLines = countNewlines(text, headEnd, tailStart),
        hiddenChars = tailStart - headEnd,
    )
}
