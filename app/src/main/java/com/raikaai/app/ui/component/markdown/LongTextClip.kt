package com.raikaai.app.ui.component.markdown

/** Maximum number of characters laid out inline; longer text is clipped. */
const val CLIP_MAX_CHARS = 16000

/** Lines kept at the head. */
const val CLIP_HEAD_LINES = 60

/** Lines kept at the tail: command errors and conclusions usually live here. */
const val CLIP_TAIL_LINES = 140

/** The inline portion of a long text. [tail] is null when nothing was clipped. */
data class ClippedText(
    val head: String,
    val tail: String?,
    val totalLines: Int,
    val hiddenLines: Int = 0,
    val hiddenChars: Int = 0,
) {
    val clipped: Boolean get() = tail != null
}

/**
 * Keeps only the head and tail of [text], whole lines where possible, so a
 * single paragraph never lays out a megabyte of tool output. A text that is
 * one very long line (compacted JSON, for example) is cut by character budget
 * instead, in which case [ClippedText.hiddenLines] is zero and
 * [ClippedText.hiddenChars] carries the omission.
 */
fun clipLongText(
    text: String,
    maxChars: Int = CLIP_MAX_CHARS,
    headLines: Int = CLIP_HEAD_LINES,
    tailLines: Int = CLIP_TAIL_LINES,
): ClippedText {
    val totalLines = countNewlines(text, 0, text.length) + 1
    if (text.length <= maxChars && totalLines <= headLines + tailLines) {
        return ClippedText(head = text, tail = null, totalLines = totalLines)
    }

    val headBudget = maxChars * 3 / 10
    val tailBudget = maxChars - headBudget

    var headEnd = 0
    for (line in 0 until headLines) {
        val newline = text.indexOf('\n', headEnd)
        if (newline < 0 || newline >= headBudget) break
        headEnd = newline + 1
    }
    if (headEnd == 0) headEnd = cutBefore(text, headBudget.coerceIn(0, text.length))

    var tailStart = text.length
    for (line in 0 until tailLines) {
        if (tailStart == 0) break
        val start = if (tailStart < 2) 0 else text.lastIndexOf('\n', tailStart - 2) + 1
        if (text.length - start > tailBudget) break
        tailStart = start
    }
    if (tailStart == text.length) {
        tailStart = cutAfter(text, (text.length - tailBudget).coerceIn(0, text.length))
    }

    if (headEnd >= tailStart) return ClippedText(head = text, tail = null, totalLines = totalLines)

    val head = text.substring(0, headEnd)
    return ClippedText(
        head = if (head.endsWith('\n')) head.substring(0, head.length - 1) else head,
        tail = text.substring(tailStart),
        totalLines = totalLines,
        hiddenLines = countNewlines(text, headEnd, tailStart),
        hiddenChars = tailStart - headEnd,
    )
}

/** Short English label placed between the clipped head and tail. */
fun hiddenLinesMarker(hiddenLines: Int, hiddenChars: Int): String = when {
    hiddenLines > 0 -> "… $hiddenLines lines hidden …"
    hiddenChars > 0 -> "… $hiddenChars characters hidden …"
    else -> ""
}

private fun countNewlines(text: String, start: Int, end: Int): Int {
    var count = 0
    var i = text.indexOf('\n', start)
    while (i >= 0 && i < end) {
        count++
        i = text.indexOf('\n', i + 1)
    }
    return count
}

/** True when [index] splits a UTF-16 surrogate pair. */
private fun splitsSurrogate(text: String, index: Int): Boolean =
    index > 0 && index < text.length && (text[index - 1].code and 0xFC00) == 0xD800

private fun cutBefore(text: String, index: Int): Int =
    if (splitsSurrogate(text, index)) index - 1 else index

private fun cutAfter(text: String, index: Int): Int =
    if (splitsSurrogate(text, index)) index + 1 else index
