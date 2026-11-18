package com.raikaai.app.ui.component.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockSplitTest {

    @Test
    fun `empty text has no blocks`() {
        assertTrue(splitMarkdownBlocks("").isEmpty())
    }

    @Test
    fun `short text stays one block`() {
        val blocks = splitMarkdownBlocks("short reply")
        assertEquals(1, blocks.size)
        assertEquals("short reply", blocks[0].text)
    }

    @Test
    fun `ids are deterministic for the same input`() {
        val text = longText()
        val first = splitMarkdownBlocks(text, minLength = 1000)
        val second = splitMarkdownBlocks(text, minLength = 1000)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `splits long text at blank lines and reproduces source`() {
        val text = longText()
        val blocks = splitMarkdownBlocks(text, minLength = 1000)
        assertTrue("expected several blocks, got ${blocks.size}", blocks.size >= 2)
        assertEquals(text, blocks.joinToString("") { it.text })
        for (block in blocks.dropLast(1)) {
            assertTrue("block shorter than min length: ${block.text.length}", block.text.length >= 1000)
        }
    }

    @Test
    fun `appending keeps earlier block ids and changes only the tail`() {
        val text = longText()
        val before = splitMarkdownBlocks(text, minLength = 1000)
        assertTrue("expected at least two blocks", before.size >= 2)

        val after = splitMarkdownBlocks("$text\n\nappended tail", minLength = 1000)
        assertEquals(before[0].id, after[0].id)
        assertNotEquals(before.last().id, after.last().id)
        assertEquals("$text\n\nappended tail", after.joinToString("") { it.text })
    }

    @Test
    fun `does not split inside a fenced code block`() {
        val text = "```\n" + "x".repeat(3000) + "\n```"
        val blocks = splitMarkdownBlocks(text, minLength = 1000)
        assertEquals(1, blocks.size)
        assertEquals(text, blocks[0].text)
    }

    @Test
    fun `identical block texts at different offsets get different ids`() {
        val paragraph = "a".repeat(700)
        val text = listOf(paragraph, paragraph, paragraph, paragraph).joinToString("\n\n")
        val blocks = splitMarkdownBlocks(text, minLength = 1000)
        assertTrue("expected several blocks", blocks.size >= 2)
        assertEquals(blocks.size, blocks.map { it.id }.toSet().size)
    }

    private fun longText(): String =
        (1..5).joinToString("\n\n") { "Paragraph $it: " + "word ".repeat(140).trim() }
}
