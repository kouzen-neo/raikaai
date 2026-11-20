package com.raikaai.app.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProvidersTest {
    @Test
    fun endpointsMatchEachProvider() {
        fun chat(id: String) = RemoteProviders.endpoint(RemoteProviders.byId(id).baseUrl, "chat/completions")
        assertEquals("https://api.openai.com/v1/chat/completions", chat("openai"))
        assertEquals("https://api.anthropic.com/v1/chat/completions", chat("anthropic"))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", chat("gemini"))
        assertEquals("https://api.groq.com/openai/v1/chat/completions", chat("groq"))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", chat("openrouter"))
        // Bare self-hosted host gets /v1.
        assertEquals("http://192.168.1.100:11434/v1/models", RemoteProviders.endpoint("http://192.168.1.100:11434/", "models"))
    }

    @Test
    fun detectsPresetFromSavedUrl() {
        assertEquals("gemini", RemoteProviders.detect("https://generativelanguage.googleapis.com/v1beta/openai").id)
        assertEquals(RemoteProviders.OLLAMA, RemoteProviders.detect("http://10.0.0.5:11434").id)
        assertEquals(RemoteProviders.CUSTOM, RemoteProviders.detect("http://localhost:20128").id)
    }

    @Test
    fun anthropicGetsNativeHeaders() {
        val h = RemoteProviders.authHeaders("https://api.anthropic.com/v1/models", "k").toMap()
        assertEquals("k", h["x-api-key"])
        assertTrue(h.containsKey("anthropic-version"))
        assertTrue(RemoteProviders.authHeaders("https://api.openai.com/v1", "").isEmpty())
    }
}
