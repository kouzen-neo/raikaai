package com.raikaai.app.ui.component.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingRepairTest {

    @Test
    fun `closes dangling bold italic and strike markers`() {
        assertEquals("**hello**", repairStreamingMarkdown("**hello"))
        assertEquals("*hello*", repairStreamingMarkdown("*hello"))
        assertEquals("~~gone~~", repairStreamingMarkdown("~~gone"))
    }

    @Test
    fun `closes dangling inline code span`() {
        assertEquals("`code`", repairStreamingMarkdown("`code"))
    }

    @Test
    fun `holds a marker only line`() {
        assertEquals("", repairStreamingMarkdown("# "))
        assertEquals("", repairStreamingMarkdown("- "))
        assertEquals("", repairStreamingMarkdown("**"))
    }

    @Test
    fun `turns half link into placeholder link`() {
        assertEquals("[label](#)", repairStreamingMarkdown("[label](https://exa"))
    }

    @Test
    fun `holds half image tag`() {
        assertEquals("", repairStreamingMarkdown("![alt](https://ex"))
    }

    @Test
    fun `holds half html tag`() {
        assertEquals("", repairStreamingMarkdown("<div clas"))
    }

    @Test
    fun `leaves balanced markdown unchanged`() {
        val text = "**bold** and *italic* and `code` and [link](https://example.com)"
        assertEquals(text, repairStreamingMarkdown(text))
    }

    @Test
    fun `holds table until delimiter row is complete`() {
        assertEquals("", repairStreamingMarkdown("| A | B |\n|---"))
        assertEquals(
            "| A | B |\n|---|---|",
            repairStreamingMarkdown("| A | B |\n|---|---|"),
        )
    }

    @Test
    fun `code fence body streams as itself`() {
        val text = "```python\nprint(1)\n"
        assertEquals(text, repairStreamingMarkdown(text))
    }

    @Test
    fun `holds a fence opener still typing its language`() {
        assertEquals("", repairStreamingMarkdown("```py"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", repairStreamingMarkdown(""))
    }
}
