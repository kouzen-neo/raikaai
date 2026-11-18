package com.raikaai.app.ui.component.markdown

/** Default shortest block; a reply shorter than this stays a single block. */
const val DEFAULT_BLOCK_MIN_LENGTH = 2000

/** One render unit of a long reply. [id] is stable for identical text at the same offset. */
data class MarkdownBlock(val id: Long, val text: String)

/**
 * Splits a reply into blocks of roughly [minLength] characters so a streamed
 * reply only re-renders and repaints its last block.
 *
 * Cuts are only made at blank lines outside a fenced code block: earlier
 * blocks keep the same text and the same offset as more tokens arrive, so
 * their [MarkdownBlock.id] stays stable. When in doubt there is no cut and a
 * block simply grows. Concatenating the returned block texts reproduces
 * [text] exactly.
 */
fun splitMarkdownBlocks(text: String, minLength: Int = DEFAULT_BLOCK_MIN_LENGTH): List<MarkdownBlock> {
    if (text.isEmpty()) return emptyList()
    val cuts = findBlockCuts(text, minLength)
    if (cuts.isEmpty()) return listOf(MarkdownBlock(stableBlockId(text, 0), text))

    val blocks = mutableListOf<MarkdownBlock>()
    var start = 0
    for (end in cuts) {
        val chunk = text.substring(start, end)
        if (chunk.isNotEmpty()) blocks.add(MarkdownBlock(stableBlockId(chunk, start), chunk))
        start = end
    }
    if (start < text.length) {
        val chunk = text.substring(start)
        blocks.add(MarkdownBlock(stableBlockId(chunk, start), chunk))
    }
    return blocks
}

private val FENCE_OPEN = Regex("""^[ \t]*(?:>[ \t]?)*[ \t]*(`{3,}|~{3,})(?!.*`)""")
private val FENCE_CLOSE = Regex("""^[ \t]*(?:>[ \t]?)*[ \t]*(`{3,}|~{3,})[ \t]*$""")

private fun findBlockCuts(text: String, minLength: Int): List<Int> {
    val cuts = mutableListOf<Int>()
    var blockStart = 0
    var fenceChar: Char? = null
    var fenceLength = 0
    var i = 0
    val n = text.length
    while (i < n) {
        val newline = text.indexOf('\n', i)
        val lineEnd = if (newline == -1) n else newline + 1
        val line = text.substring(i, if (newline == -1) n else newline)
        val fenceOpen = FENCE_OPEN.find(line)
        when {
            fenceChar != null -> {
                val close = FENCE_CLOSE.find(line)
                if (close != null && close.groupValues[1].isNotEmpty() &&
                    close.groupValues[1][0] == fenceChar && close.groupValues[1].length >= fenceLength
                ) {
                    fenceChar = null
                }
            }
            fenceOpen != null -> {
                fenceChar = fenceOpen.groupValues[1][0]
                fenceLength = fenceOpen.groupValues[1].length
            }
            line.trim().isEmpty() && lineEnd - blockStart >= minLength -> {
                cuts.add(lineEnd)
                blockStart = lineEnd
            }
        }
        if (newline == -1) break
        i = lineEnd
    }
    return cuts
}

/** FNV-1a over text plus offset: deterministic, collision-resistant enough for Compose keys. */
// 64-bit FNV-1a offset basis (0xcbf29ce484222325) in signed two's complement,
// written as small literals so it stays in range on every Kotlin version.
private val FNV_OFFSET: Long = 1469598103934665603L * 10L + 7L
private const val FNV_PRIME = 1099511628211L

private fun stableBlockId(text: String, start: Int): Long {
    var hash = FNV_OFFSET
    for (ch in text) hash = (hash xor ch.code.toLong()) * FNV_PRIME
    hash = (hash xor start.toLong()) * FNV_PRIME
    return hash
}
