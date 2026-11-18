package com.raikaai.app.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerRuntimeTest {

    private val runtime = ContainerRuntime("127.0.0.1", 8765, "test-token")

    @Test
    fun adapterConfigIsExposedWithoutLeakingToken() {
        assertEquals("127.0.0.1", runtime.host)
        assertEquals(8765, runtime.port)
        assertEquals("test-token", runtime.token)
        assertEquals("http://127.0.0.1:8765", runtime.adapterBaseUrl)
        assertFalse(runtime.adapterBaseUrl.contains(runtime.token))
    }

    @Test
    fun exposesGuestToolsOnly() {
        val tools = runtime.availableTools()
        assertEquals(ContainerRuntime.TOOL_NAMES, tools.map { it.name })
        assertTrue(ContainerRuntime.TOOL_NAMES.containsAll(listOf("read", "bash", "edit", "write")))
        ContainerRuntime.TOOL_NAMES.forEach { assertTrue(runtime.supports(it)) }
        assertFalse(runtime.supports("web_search"))
        assertFalse(runtime.supports("rag_search"))
    }

    @Test
    fun containerToolReturnsUnavailableUntilAdapterLands() = runBlocking {
        val events = mutableListOf<RuntimeEvent>()
        val result = runtime.call(ToolRequest("bash", """{"command":"ls"}""")) { events.add(it) }
        assertTrue(result.isError)
        assertTrue(result.text.contains("unavailable", ignoreCase = true))
        assertTrue(result.text.contains("KazeTerm"))
        assertTrue(result.text.contains("bash"))
        assertTrue(events.isEmpty())
    }
}
