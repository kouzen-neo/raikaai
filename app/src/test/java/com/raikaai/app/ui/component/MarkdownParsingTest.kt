package com.raikaai.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParsingTest {

    @Test
    fun thinkingBlocksStayInOrder() {
        val raw = "<think>plan A</think>\n\nAnswer one.\n\n_tool ran_\n\n<think>plan B</think>\n\nAnswer two."
        val seg = splitThinkingSegmentsForTest(raw, enableThinking = true)
        assertEquals(
            listOf("think" to "plan A", "body" to "Answer one.\n\n_tool ran_", "think" to "plan B", "body" to "Answer two."),
            seg,
        )
    }

    @Test
    fun onlyLastThoughtCanBeOpen() {
        val seg = splitThinkingSegmentsForTest("<think>a</think>x<think>still going", enableThinking = true)
        assertEquals(listOf("think" to "a", "body" to "x", "think…" to "still going"), seg)
    }

    @Test
    fun strayCloseTagStartsWithThought() {
        val seg = splitThinkingSegmentsForTest("pre-opened thought</think>Final", enableThinking = true)
        assertEquals(listOf("think" to "pre-opened thought", "body" to "Final"), seg)
    }

    @Test
    fun thinkingDisabledDropsThoughts() {
        val seg = splitThinkingSegmentsForTest("<think>hidden</think>Visible", enableThinking = false)
        assertEquals(listOf("body" to "Visible"), seg)
    }

    @Test
    fun consecutiveLinesFormOneParagraph() {
        val blocks = parseBlockTypesForTest("line one\nline two\n\nnext para")
        assertEquals(listOf("PARAGRAPH:0:line one\nline two", "PARAGRAPH:0:next para"), blocks)
    }

    @Test
    fun nestedListsAndExtendedSyntax() {
        val blocks = parseBlockTypesForTest("- a\n  - b\n1) one\n- [x] done\n---\n#### Small")
        assertEquals(
            listOf("BULLET:0:a", "BULLET:1:b", "NUMBERED:0:one", "TASK:0:done", "RULE:0:", "HEADER_3:0:Small"),
            blocks,
        )
    }

    @Test
    fun containerResultBecomesToolCard() {
        val md = "Let me check.\n`ls /workspace` [auto]\n```\na.txt\nexit=0\n```\nDone."
        val blocks = parseBlockTypesForTest(md)
        assertEquals("PARAGRAPH:0:Let me check.", blocks[0])
        assertEquals("TOOL:0:ls /workspace", blocks[1])
        assertEquals("PARAGRAPH:0:Done.", blocks[2])
    }

    @Test
    fun plainCodeBlockIsNotAToolCard() {
        val blocks = parseBlockTypesForTest("`x`\n```kotlin\nval a = 1\n```")
        assertTrue(blocks.any { it.startsWith("CODE_BLOCK") })
    }
}
