package com.raikaai.app.core.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the /v1 server's input validation (B13) and the single
 * context-window source of truth shared by PromptBuilder and EngineParams (B4).
 */
class ServerValidationTest {
    private fun config() =
        OpenAiServer.Config(
            port = 8080,
            bindAll = false,
            authToken = "",
            systemPrompt = "sys",
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            maxTokens = 512,
            backend = "CPU",
            modelPath = "/models/m.litertlm",
            contextWindow = 2048,
        )

    private fun clamp(
        temperature: Float? = null,
        topK: Int? = null,
        topP: Float? = null,
        maxTokens: Int? = null,
        config: OpenAiServer.Config = config(),
    ) = OpenAiServer.clampSampler(temperature, topK, topP, maxTokens, config, "s")

    @Test
    fun `temperature is clamped to 0 to 2`() {
        assertEquals(2f, clamp(temperature = 99f).temperature, 0.0001f)
        assertEquals(0f, clamp(temperature = -1f).temperature, 0.0001f)
        assertEquals(0.7f, clamp().temperature, 0.0001f)
    }

    @Test
    fun `NaN temperature falls back to config`() {
        assertEquals(0.7f, clamp(temperature = Float.NaN).temperature, 0.0001f)
        assertEquals(0.9f, clamp(topP = Float.NaN).topP, 0.0001f)
    }

    @Test
    fun `topK is at least 1`() {
        assertEquals(1, clamp(topK = 0).topK)
        assertEquals(1, clamp(topK = -5).topK)
        assertEquals(40, clamp().topK)
        assertEquals(100, clamp(topK = 100).topK)
    }

    @Test
    fun `topP is clamped to 0 to 1`() {
        assertEquals(1f, clamp(topP = 5f).topP, 0.0001f)
        assertEquals(0f, clamp(topP = -0.5f).topP, 0.0001f)
        assertEquals(0.9f, clamp().topP, 0.0001f)
    }

    @Test
    fun `maxTokens is clamped to 1 to 8192 and never unlimited`() {
        assertEquals(1, clamp(maxTokens = 0).maxTokens)
        assertEquals(1, clamp(maxTokens = -10).maxTokens)
        assertEquals(8192, clamp(maxTokens = 100_000).maxTokens)
        assertEquals(512, clamp().maxTokens)
    }

    @Test
    fun `contextWindow is plumbed from config into engine params`() {
        val params = clamp(config = config().copy(contextWindow = 4096))
        assertEquals(4096, params.contextWindow)
        assertEquals(2048, clamp().contextWindow)
    }

    @Test
    fun `PromptBuilder truncates more aggressively with a smaller contextWindow`() {
        val filler = "x".repeat(1500)
        val messages =
            buildList {
                repeat(10) { i ->
                    add(PromptBuilder.ChatMessage("user", "$i $filler"))
                    add(PromptBuilder.ChatMessage("assistant", "$i ok"))
                }
                add(PromptBuilder.ChatMessage("user", "final"))
            }
        val small = PromptBuilder.build(messages, "", templateFormat = "raw", contextWindow = 1024)
        val large = PromptBuilder.build(messages, "", templateFormat = "raw", contextWindow = 8192)
        // The smaller window must drop more history, but the last turn survives in both.
        assertTrue(small.length < large.length)
        assertTrue(small.contains("final"))
        assertTrue(large.contains("final"))
    }

    @Test
    fun `enableThinking propagates from config into engine params`() {
        assertEquals(false, clamp(config = config().copy(enableThinking = false)).enableThinking)
        assertEquals(true, clamp(config = config().copy(enableThinking = true)).enableThinking)
    }

    @Test
    fun `Gson deserialized ChatMessage with missing fields normalizes safely`() {
        val parsed = com.google.gson.Gson().fromJson("{}", PromptBuilder.ChatMessage::class.java)
        val normalized = parsed.normalize()
        assertEquals("", normalized.role)
        assertEquals("", normalized.content)
    }
}
