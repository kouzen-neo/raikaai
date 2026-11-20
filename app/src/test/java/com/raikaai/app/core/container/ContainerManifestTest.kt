package com.raikaai.app.core.container

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerManifestTest {

    @Test
    fun defaultManifestPointsToAlpine321() {
        val m = ContainerManifest()
        assertEquals("3.21", m.alpineVersion)
        assertTrue(m.rootfsUrl.contains("alpine-minirootfs-3.21"))
        assertTrue(m.rootfsUrl.startsWith("https://"))
    }

    @Test
    fun bundledProotResolvesPerAbiWithPinnedHashes() {
        val arm64 = bundledProotForAbi("arm64-v8a")!!
        assertEquals("arm64-v8a", arm64.abi)
        assertEquals(5, arm64.libs.size)
        assertTrue(arm64.allHashesValid())
        assertTrue(arm64.libs.any { it.fileName == "libproot.so" })
        val x86 = bundledProotForAbi("x86_64")!!
        assertTrue(x86.allHashesValid())
        assertEquals(null, bundledProotForAbi("armeabi-v7a"))
    }

    @Test
    fun sha256FormatCheck() {
        assertTrue(isValidSha256Hex("a".repeat(64)))
        assertTrue(isValidSha256Hex("ABCDEF0123456789".repeat(4)))
        assertFalse(isValidSha256Hex(""))
        assertFalse(isValidSha256Hex("abc"))
        assertFalse(isValidSha256Hex("z".repeat(64)))
    }

    @Test
    fun servePortValidation() {
        assertFalse(isValidServePort(80))
        assertFalse(isValidServePort(8080)) // taken by OpenAI server
        assertTrue(isValidServePort(8081))
        assertTrue(isValidServePort(3000))
        assertFalse(isValidServePort(70000))
    }

    @Test
    fun installPolicyGate() {
        assertEquals(InstallPolicy.ALLOW, installPolicyFor("apk add --no-cache ffmpeg"))
        assertEquals(InstallPolicy.ALLOW, installPolicyFor("apk add curl"))
        assertEquals(InstallPolicy.ALLOW, installPolicyFor("fastfetch"))
        assertEquals(InstallPolicy.ALLOW, installPolicyFor("ls -la /workspace"))
        assertEquals(InstallPolicy.ALLOW, installPolicyFor("cat /etc/os-release"))
        assertEquals(InstallPolicy.ASK, installPolicyFor("echo hi > out.txt"))
        assertEquals(InstallPolicy.ASK, installPolicyFor("ls /tmp; echo done"))
        assertEquals(InstallPolicy.DENY, installPolicyFor("ls; rm -rf /tmp/x"))
        assertEquals(InstallPolicy.ASK, installPolicyFor("apk add --no-cache some-unknown-pkg"))
        assertEquals(InstallPolicy.ASK, installPolicyFor("pip install requests"))
        assertEquals(InstallPolicy.ASK, installPolicyFor("npm install -g server"))
        assertEquals(InstallPolicy.DENY, installPolicyFor("rm -rf /"))
        assertEquals(InstallPolicy.DENY, installPolicyFor("apk add --allow-untrusted foo"))
    }

    @Test
    fun joinBrokenPathsOnlyInsideQuotes() {
        assertEquals(
            "cat \"/workspace/kzkt/x.zip\"",
            joinBrokenPaths("cat \"/workspace/\nkzkt/x.zip\""),
        )
        assertEquals(
            "cat '/workspace/kzkt/x.zip'",
            joinBrokenPaths("cat '/workspace/  \n  kzkt/x.zip'"),
        )
        // Real separators outside quotes are untouched.
        assertEquals("cd /tmp\nls", joinBrokenPaths("cd /tmp\nls"))
        assertEquals("echo hi", joinBrokenPaths("echo hi"))
        // Other wraps inside quotes join with a space.
        assertEquals("echo \"a b\"", joinBrokenPaths("echo \"a\nb\""))
        // Pretty-printer wraps after slashes even outside quotes.
        assertEquals(
            "cat /workspace/kzkt_app/kzkt-license-web-main/web_server.py",
            joinBrokenPaths("cat /workspace/kzkt_app/\nkzkt-license-web-main/web_server.py"),
        )
    }

    @Test
    fun backgroundOpDetection() {
        assertTrue(containsBackgroundOp("python3 server.py --port 8080 &"))
        assertTrue(containsBackgroundOp("sleep 100 &"))
        assertEquals(false, containsBackgroundOp("apk update && apk add python3"))
        assertEquals(false, containsBackgroundOp("echo \"&\""))
        assertEquals(false, containsBackgroundOp("ls -la /workspace"))
    }

    @Test
    fun serverCommandDetection() {
        // Real servers (any of these would be killed by a foreground run).
        assertTrue(looksLikeServerCommand("npm run dev"))
        assertTrue(looksLikeServerCommand("npm run dev -- --host 0.0.0.0"))
        assertTrue(looksLikeServerCommand("cd /workspace/app && npm start"))
        assertTrue(looksLikeServerCommand("vite --host"))
        assertTrue(looksLikeServerCommand("npx vite"))
        assertTrue(looksLikeServerCommand("python3 -m http.server 8000"))
        assertTrue(looksLikeServerCommand("php -S 127.0.0.1:8000"))
        assertTrue(looksLikeServerCommand("node server.js"))
        assertTrue(looksLikeServerCommand("busybox httpd -f -p 127.0.0.1:8081 -h /workspace"))
        assertTrue(looksLikeServerCommand("npm install && npm run dev"))

        // Finite commands must NOT be detached (output would be lost).
        assertEquals(false, looksLikeServerCommand("npm install"))
        assertEquals(false, looksLikeServerCommand("npm run build"))
        assertEquals(false, looksLikeServerCommand("vite build"))
        assertEquals(false, looksLikeServerCommand("apk add httpd"))
        assertEquals(false, looksLikeServerCommand("apk add --no-cache nodejs npm"))
        assertEquals(false, looksLikeServerCommand("ls -la /workspace"))
        assertEquals(false, looksLikeServerCommand("git clone https://example.com/x.git"))
        assertEquals(false, looksLikeServerCommand("npm test"))
    }

    @Test
    fun announcedPortParsing() {
        assertEquals(5173, parseAnnouncedPort("  ➜  Local:   http://localhost:5173/"))
        assertEquals(5173, parseAnnouncedPort("  ➜  Network: http://192.168.1.5:5173/"))
        assertEquals(3000, parseAnnouncedPort("ready - started server on 0.0.0.0:3000"))
        assertEquals(8081, parseAnnouncedPort("Listening on http://127.0.0.1:8081"))
        assertEquals(null, parseAnnouncedPort("no port here"))
        assertEquals(null, parseAnnouncedPort(""))
    }

    @Test
    fun dedupKeyIgnoresHeadTailWindows() {
        val a = dedupKeyOf("unzip -p 'f.zip' doc.md | head -n 45")
        val b = dedupKeyOf("unzip -p 'f.zip' doc.md | head -n 100")
        assertEquals(a, b)
        assertEquals(
            dedupKeyOf("unzip -p 'f.zip' doc.md"),
            dedupKeyOf("unzip  -p  'f.zip'   doc.md"),
        )
        assertTrue(dedupKeyOf("ls /a") != dedupKeyOf("ls /b"))
    }

    @Test
    fun redirectionsAreNotBackgrounding() {
        assertFalse(containsBackgroundOp("curl -Iv https://github.com 2>&1 | head -n 30"))
        assertFalse(containsBackgroundOp("echo hi >&2"))
        assertFalse(containsBackgroundOp("make &>build.log"))
        assertFalse(containsBackgroundOp("cmd |& tee log"))
        assertTrue(containsBackgroundOp("python3 -m http.server 8000 &"))
        assertTrue(containsBackgroundOp("npm run dev & sleep 2"))
    }
}
