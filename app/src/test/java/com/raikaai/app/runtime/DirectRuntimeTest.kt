package com.raikaai.app.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectRuntimeTest {

    private val runtime = DirectRuntime()

    @Test
    fun exposesOnlyLinuxFreeTools() {
        val tools = runtime.availableTools()
        assertEquals(DirectRuntime.TOOL_NAMES, tools.map { it.name })
        assertTrue(
            DirectRuntime.TOOL_NAMES.containsAll(
                listOf("web_search", "rag_search", "generate_image", "http_mcp_call"),
            ),
        )
        assertFalse(DirectRuntime.TOOL_NAMES.contains("bash"))
        tools.forEach {
            assertTrue(it.description.isNotBlank())
            assertTrue(it.parametersJson.contains("\"type\""))
        }
    }

    @Test
    fun supportsMatchesAvailableToolsCaseSensitively() {
        DirectRuntime.TOOL_NAMES.forEach { assertTrue(runtime.supports(it)) }
        assertFalse(runtime.supports("bash"))
        assertFalse(runtime.supports("find"))
        assertFalse(runtime.supports(""))
        assertFalse(runtime.supports("WEB_SEARCH"))
    }

    @Test
    fun callReportsThatExecutionIsNotConnected() = runBlocking {
        val result = runtime.call(ToolRequest("web_search", """{"query":"hello"}""")) {}
        assertTrue(result.isError)
        assertTrue(result.text.contains("not connected"))
        assertTrue(result.text.contains("web_search"))
    }
}
