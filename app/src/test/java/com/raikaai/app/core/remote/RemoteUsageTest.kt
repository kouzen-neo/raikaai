package com.raikaai.app.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUsageTest {
    @Test
    fun usageJsonRoundTrips() {
        val m = mapOf("gemini-3.8-flash" to RemoteUsage.ModelUsage(1200, 340, 3, 1))
        assertEquals(m, RemoteUsage.parseUsage(RemoteUsage.usageJson(m)))
    }

    @Test
    fun malformedUsageIsEmpty() {
        assertTrue(RemoteUsage.parseUsage("not json").isEmpty())
        assertTrue(RemoteUsage.parseUsage(null).isEmpty())
    }

    @Test
    fun costUsesPricePerMillion() {
        val u = RemoteUsage.ModelUsage(inputTokens = 2_000_000, outputTokens = 500_000)
        val cost = RemoteUsage.cost(u, RemoteUsage.Price(0.5, 2.0))!!
        assertEquals(2.0, cost, 1e-9)
        assertNull(RemoteUsage.cost(u, null))
    }

    @Test
    fun recordAccumulatesWithoutPrefs() {
        RemoteUsage.reset()
        RemoteUsage.record("m", 100, 20, estimated = false)
        RemoteUsage.record("m", 50, 10, estimated = true)
        RemoteUsage.record("", 50, 10, estimated = true)
        val u = RemoteUsage.usage.value.getValue("m")
        assertEquals(RemoteUsage.ModelUsage(150, 30, 2, 1), u)
        assertEquals(1, RemoteUsage.usage.value.size)
    }
}
