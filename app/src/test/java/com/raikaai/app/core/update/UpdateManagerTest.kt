package com.raikaai.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun comparesVersions() {
        assertTrue(UpdateManager.isNewer("v0.4.9", "0.4.8"))
        assertTrue(UpdateManager.isNewer("v0.5.0", "0.4.9"))
        assertTrue(UpdateManager.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateManager.isNewer("v0.4.8", "0.4.8"))
        assertFalse(UpdateManager.isNewer("v0.4.7", "0.4.8"))
    }

    @Test
    fun picksApkForAbi() {
        val names = listOf("checksums-sha256.txt", "RaikaAI-v0.4.9-arm64-v8a.apk")
        assertEquals("RaikaAI-v0.4.9-arm64-v8a.apk", UpdateManager.pickApk(names, listOf("arm64-v8a", "armeabi-v7a")))
        assertNull(UpdateManager.pickApk(names, listOf("x86_64")))
        assertEquals("RaikaAI-v1.apk", UpdateManager.pickApk(listOf("RaikaAI-v1.apk"), listOf("x86_64")))
    }

    @Test
    fun readsChecksumLine() {
        val hash = "a".repeat(64)
        val text = "$hash  RaikaAI-v0.4.9-arm64-v8a.apk\n"
        assertEquals(hash, UpdateManager.expectedSha256(text, "RaikaAI-v0.4.9-arm64-v8a.apk"))
        assertNull(UpdateManager.expectedSha256(text, "other.apk"))
    }

    @Test
    fun checksumFailsClosed() {
        val good = "b".repeat(64)
        val text = "$good  RaikaAI-v0.5.3-arm64-v8a.apk\n"
        assertNull(UpdateManager.checksumProblem(text, "RaikaAI-v0.5.3-arm64-v8a.apk", good.uppercase()))
        assertTrue(UpdateManager.checksumProblem(text, "RaikaAI-v0.5.3-arm64-v8a.apk", "c".repeat(64))!!.contains("doesn't match"))
        // No entry for this APK, or an empty file: refuse instead of installing unverified.
        assertTrue(UpdateManager.checksumProblem(text, "other.apk", good)!!.contains("does not list"))
        assertTrue(UpdateManager.checksumProblem("", "RaikaAI-v0.5.3-arm64-v8a.apk", good) != null)
    }
}
