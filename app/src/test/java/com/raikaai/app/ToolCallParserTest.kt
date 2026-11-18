package com.raikaai.app

import com.raikaai.app.core.tool.ToolCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ToolCallParserTest {
    @Test
    fun testXmlToolCall() {
        val text = """
            <think>
            I need to check the scores of the English Premier League.
            </think>
            <tool_call>
            {"name": "web_search", "arguments": {"query": "skor pertandingan liga inggris terbaru"}}
            </tool_call>
        """.trimIndent()

        val parsed = ToolCallParser.parse(text)
        assertNotNull(parsed)
        assertEquals("web_search", parsed?.name)
        assertEquals("skor pertandingan liga inggris terbaru", parsed?.query)
    }

    @Test
    fun testMarkdownToolCall() {
        val text = """
            ```tool_call
            {"name": "web_search", "arguments": {"query": "berita teknologi terbaru"}}
            ```
        """.trimIndent()

        val parsed = ToolCallParser.parse(text)
        assertNotNull(parsed)
        assertEquals("web_search", parsed?.name)
        assertEquals("berita teknologi terbaru", parsed?.query)
    }

    @Test
    fun testFunctionStyleToolCall() {
        val text = "call:web_search(query=\"jadwal liga champions\")"
        val parsed = ToolCallParser.parse(text)
        assertNotNull(parsed)
        assertEquals("web_search", parsed?.name)
        assertEquals("jadwal liga champions", parsed?.query)
    }

    @Test
    fun testIdAndEnToolCalls() {
        val textId = "<tool_call>{\"name\": \"web_search_id\", \"arguments\": {\"query\": \"presiden indonesia 2024\"}}</tool_call>"
        val parsedId = ToolCallParser.parse(textId)
        assertNotNull(parsedId)
        assertEquals("web_search_id", parsedId?.name)
        assertEquals("id", parsedId?.lang)
        assertEquals("presiden indonesia 2024", parsedId?.query)

        val textEn = "<tool_call>{\"name\": \"web_search_en\", \"arguments\": {\"query\": \"latest Premier League results\"}}</tool_call>"
        val parsedEn = ToolCallParser.parse(textEn)
        assertNotNull(parsedEn)
        assertEquals("web_search_en", parsedEn?.name)
        assertEquals("en", parsedEn?.lang)
        assertEquals("latest Premier League results", parsedEn?.query)
    }

    @Test
    fun testGenerateImageGenericCallWithThinking() {
        val text = """
            <think>
            The user wants to change the cat to white. Directly generating a modified image is appropriate.
            <tool_call>{"name": "generate_image", "arguments": {"prompt": "white cat"}}
            </think>
            <tool_call>{"name": "generate_image", "arguments": {"prompt": "A beautiful fluffy pure white cat with bright expressive eyes"}}</tool_call>
        """.trimIndent()
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("generate_image", parsed?.name)
        assertEquals("A beautiful fluffy pure white cat with bright expressive eyes", parsed?.arguments?.get("prompt")?.asString)
    }

    @Test
    fun testXmlShapedContainerCall() {
        val text = "<tool_call><name>execute_in_container</name><arguments><command>fastfetch</command></arguments></tool_call>"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("execute_in_container", parsed?.name)
        assertEquals("fastfetch", parsed?.arguments?.get("command")?.asString)
    }

    @Test
    fun testXmlShapedRunCommandMultiline() {
        val text = """
            <tool_call>
            <name>run_command</name>
            <arguments>
            <command>cat /etc/os-release</command>
            </arguments>
            </tool_call>
        """.trimIndent()
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("run_command", parsed?.name)
        assertEquals("cat /etc/os-release", parsed?.arguments?.get("command")?.asString)
    }

    @Test
    fun testProseWithoutNameTagIsNotAToolCall() {
        val text = "Run <command>ls</command> in your head, then answer."
        assertEquals(null, ToolCallParser.parseGenericCall(text))
    }

    @Test
    fun testCallInsideThinkingStillExecutes() {
        val text = "<think>I'll check the workspace. " +
            "<tool_call>{\"name\": \"execute_in_container\", \"arguments\": {\"command\": \"ls /workspace\"}}</tool_call>" +
            "</think>"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("execute_in_container", parsed?.name)
        assertEquals("ls /workspace", parsed?.arguments?.get("command")?.asString)
    }

    @Test
    fun testMultilineBrokenJsonCommand() {
        val text = "<tool_call>{\"name\": \"execute_in_container\", \"arguments\": " +
            "{\"command\": \"unzip -l \\\"/workspace/\nkzkt-license-web-main.zip\\\"\"}}</tool_call>"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("execute_in_container", parsed?.name)
        val cmd = parsed?.arguments?.get("command")?.asString.orEmpty()
        assert(cmd.contains("unzip -l"))
        assert(cmd.contains("kzkt-license-web-main.zip"))
    }

    @Test
    fun testArgumentsObjectOnNextLine() {
        // Exact on-device shape: "arguments": newline {"command": ...}
        val text = "<tool_call>{\"name\": \"execute_in_container\", \"arguments\":\n" +
            "{\"command\": \"unzip -l '/workspace/\nkzkt-license-web-main.zip'\"}}</tool_call>"
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("execute_in_container", parsed?.name)
        val cmd = parsed?.arguments?.get("command")?.asString.orEmpty()
        assert(cmd.contains("unzip -l"))
        assert(cmd.contains("kzkt-license-web-main.zip"))
    }

    @Test
    fun testWriteFileToolCall() {
        val text = """
            <tool_call>
            {
              "name": "write_file",
              "arguments": {
                "path": "hello.py",
                "content": "print('hello world')\nprint('second line')"
              }
            }
            </tool_call>
        """.trimIndent()
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("write_file", parsed?.name)
        assertEquals("hello.py", parsed?.arguments?.get("path")?.asString)
        assertEquals("print('hello world')\nprint('second line')", parsed?.arguments?.get("content")?.asString)
    }

    @Test
    fun testEditFileToolCall() {
        val text = """
            <tool_call>{"name": "edit_file", "arguments": {"path": "main.py", "old_string": "def foo():\n    pass", "new_string": "def foo():\n    return 42"}}</tool_call>
        """.trimIndent()
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("edit_file", parsed?.name)
        assertEquals("main.py", parsed?.arguments?.get("path")?.asString)
        assertEquals("def foo():\n    pass", parsed?.arguments?.get("old_string")?.asString)
        assertEquals("def foo():\n    return 42", parsed?.arguments?.get("new_string")?.asString)
    }

    @Test
    fun testActionTagToolCall() {
        val text = """
            <action>{"name": "execute_in_container", "arguments": {"command": "ls -la"}}</action>
        """.trimIndent()
        val parsed = ToolCallParser.parseGenericCall(text)
        assertNotNull(parsed)
        assertEquals("execute_in_container", parsed?.name)
        assertEquals("ls -la", parsed?.arguments?.get("command")?.asString)
    }
}


