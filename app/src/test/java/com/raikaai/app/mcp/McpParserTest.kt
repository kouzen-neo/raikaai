package com.raikaai.app.mcp

import com.raikaai.app.core.tool.ToolCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpParserTest {

    @Test
    fun testXmlGenericCall() {
        val text = "Let me check.\n<tool_call>{\"name\": \"read_file\", \"arguments\": {\"path\": \"/tmp/a.txt\"}}</tool_call>\nDone."
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("read_file", parsed?.name)
        assertEquals("/tmp/a.txt", parsed?.arguments?.get("path")?.asString)
    }

    @Test
    fun testMarkdownFenceGenericCall() {
        val text = "```tool_call\n{\"name\": \"shell_exec\", \"arguments\": {\"cmd\": \"ls\"}}\n```"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("shell_exec", parsed?.name)
        assertEquals("ls", parsed?.arguments?.get("cmd")?.asString)
    }

    @Test
    fun testBareJsonGenericCall() {
        val text = "I'll call {\"name\": \"get_weather\", \"arguments\": {\"city\": \"Jakarta\"}} now."
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("get_weather", parsed?.name)
        assertEquals("Jakarta", parsed?.arguments?.get("city")?.asString)
    }

    @Test
    fun testStringifiedArgumentsAreParsed() {
        val text = "<tool_call>{\"name\": \"run\", \"arguments\": \"{\\\"x\\\": 1}\"}</tool_call>"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals(1, parsed?.arguments?.get("x")?.asInt)
    }

    @Test
    fun testMissingArgumentsYieldsEmptyObject() {
        val text = "<tool_call>{\"name\": \"ping\"}</tool_call>"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("ping", parsed?.name)
        assertTrue(parsed?.arguments?.size() == 0)
    }

    @Test
    fun testWebSearchIsExcluded() {
        val text = "<tool_call>{\"name\": \"web_search\", \"arguments\": {\"query\": \"x\"}}</tool_call>"
        assertNull(ToolCallParser.parseGenericCall(text))
    }

    @Test
    fun testBlankReturnsNull() {
        assertNull(ToolCallParser.parseGenericCall("   "))
        assertNull(ToolCallParser.parseGenericCall("no tool call here"))
    }

    @Test
    fun testUnterminatedXmlTagStillParses() {
        // Models often stop right after the JSON without a closing tag.
        val text = "<tool_call>{\"name\": \"read_file\", \"arguments\": {\"path\": \"a\"}}"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("read_file", parsed?.name)
    }
}
