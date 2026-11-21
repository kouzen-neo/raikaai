package com.raikaai.app.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class McpClientParsingTest {

    private val client = McpClient(McpServerConfig(name = "t", url = "http://127.0.0.1:9/mcp"))

    @Test
    fun testPlainJsonEnvelope() {
        val body = """{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}"""
        val env = client.extractJsonRpcBody(body)
        assertNotNull(env)
        assertEquals("2.0", env?.get("jsonrpc")?.asString)
    }

    @Test
    fun testSseEnvelopeTakesLastData() {
        val body =
            """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18"}}

            data: {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"ping"}]}}
            """.trimIndent()
        val env = client.extractJsonRpcBody(body)
        assertNotNull(env)
        assertEquals("ping", env?.getAsJsonObject("result")?.getAsJsonArray("tools")?.get(0)?.asJsonObject?.get("name")?.asString)
    }

    @Test
    fun testGarbageReturnsNull() {
        assertNull(client.extractJsonRpcBody(""))
        assertNull(client.extractJsonRpcBody("<html>nope</html>"))
        assertNull(client.extractJsonRpcBody("data: not json"))
    }

    @Test
    fun testSseIgnoresNonDataLines() {
        val body = ": keep-alive\n\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}\n"
        assertNotNull(client.extractJsonRpcBody(body))
    }

    @Test
    fun testConcurrentStdioCallsDoNotStealResponses() {
        val clientToServer = java.io.PipedOutputStream()
        val serverIn = java.io.PipedInputStream(clientToServer)
        val serverToClient = java.io.PipedOutputStream()
        val clientIn = java.io.PipedInputStream(serverToClient)
        val emptyErr = java.io.ByteArrayInputStream(ByteArray(0))
        val alive = java.util.concurrent.atomic.AtomicBoolean(true)

        val fakeProcess = object : Process() {
            override fun getOutputStream() = clientToServer
            override fun getInputStream() = clientIn
            override fun getErrorStream() = emptyErr
            override fun waitFor(): Int = 0
            override fun exitValue(): Int = 0
            override fun isAlive(): Boolean = alive.get()
            override fun destroy() {
                alive.set(false)
                runCatching { clientToServer.close() }
                runCatching { serverToClient.close() }
            }
        }

        val serverThread = Thread {
            val reader = serverIn.bufferedReader()
            val writer = serverToClient.bufferedWriter()
            while (alive.get()) {
                val line = runCatching { reader.readLine() }.getOrNull() ?: break
                val req = runCatching { com.google.gson.JsonParser.parseString(line).asJsonObject }.getOrNull() ?: continue
                val id = req.get("id")?.asLong ?: continue
                val method = req.get("method")?.asString
                val response = when (method) {
                    "initialize" -> """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":"2025-06-18"}}"""
                    "tools/call" -> {
                        val tool = req.getAsJsonObject("params").get("name").asString
                        """{"jsonrpc":"2.0","id":$id,"result":{"content":[{"type":"text","text":"ok:$tool"}]}}"""
                    }
                    else -> """{"jsonrpc":"2.0","id":$id,"result":{}}"""
                }
                writer.write(response)
                writer.newLine()
                writer.flush()
            }
        }.apply { isDaemon = true; start() }

        val stdioClient = McpClient(
            config = McpServerConfig(name = "fake", url = "", command = "fake-cmd"),
            stdioLauncher = { fakeProcess },
        )
        try {
            runBlocking {
                val jobs = (1..8).map { i ->
                    async(Dispatchers.IO) {
                        stdioClient.callTool("tool_$i", com.google.gson.JsonObject()).text
                    }
                }
                val results = jobs.map { it.await() }
                for (i in 1..8) {
                    assertEquals("ok:tool_$i", results[i - 1])
                }
            }
        } finally {
            stdioClient.close()
            serverThread.interrupt()
        }
    }
}
