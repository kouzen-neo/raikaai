package com.raikaai.app.ui.environment

import com.raikaai.app.ui.chat.expandToolBlocksForModel
import com.raikaai.app.ui.chat.plainTerminalText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiScreenTest {
    private fun rows(s: AnsiScreen) = (0 until s.rowCount).map { s.plainRow(it) }

    @Test
    fun carriageReturnOverwritesProgress() {
        val s = AnsiScreen(40)
        s.feed("10%\r50%\r100%\ndone\n")
        assertEquals(listOf("100%", "done"), rows(s))
    }

    @Test
    fun cursorUpAndRightPlacesInfoNextToLogo() {
        // fastfetch prints the logo, moves up, then right, then the info.
        val s = AnsiScreen(40)
        s.feed(" /\\\n/  \\\n\u001B[2A\u001B[8Cuser@host\n\u001B[8COS: Alpine\n")
        assertEquals(listOf(" /\\     user@host", "/  \\    OS: Alpine"), rows(s))
    }

    @Test
    fun colorsAreStrippedFromPlainText() {
        val s = AnsiScreen(40)
        s.feed("\u001B[96mroot\u001B[90m@\u001B[94mlocalhost\u001B[0m\n")
        assertEquals(listOf("root@localhost"), rows(s))
        val styled = s.annotatedRow(0, androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Red)
        assertEquals("root@localhost", styled.text)
        assertTrue(styled.spanStyles.isNotEmpty())
    }

    @Test
    fun clearRequestsScreenClear() {
        val s = AnsiScreen(40)
        s.feed("old\n\u001B[H\u001B[J")
        assertTrue(s.clearRequested)
        assertEquals(0, s.rowCount)
    }

    @Test
    fun longLinesWrapAtColumns() {
        val s = AnsiScreen(20)
        s.feed("a".repeat(25))
        assertEquals(listOf("a".repeat(20), "a".repeat(5)), rows(s))
    }

    @Test
    fun plainTerminalTextFlattensEscapes() {
        val out = plainTerminalText("\u001B[90m→\u001B[0m ok\n")
        assertEquals("→ ok\n", out)
        assertEquals("plain\n", plainTerminalText("plain\n"))
    }

    @Test
    fun historyToolBlocksBecomeToolCallAndResult() {
        val msg = "Checking.\n\n`ls -la` [auto]\n```\na.txt\nexit=0\n```\n\nDone."
        val parts = expandToolBlocksForModel(msg)
        assertEquals(listOf("assistant", "user", "assistant"), parts.map { it.first })
        assertTrue(parts[0].second.startsWith("Checking."))
        assertTrue(parts[0].second.contains("<tool_call>{\"name\": \"execute_in_container\""))
        assertTrue(parts[0].second.contains("\"command\":\"ls -la\""))
        assertTrue(parts[1].second.startsWith("[CONTAINER TOOL RESULT]"))
        assertTrue(parts[1].second.contains("a.txt"))
        assertEquals("Done.", parts[2].second)
    }

    @Test
    fun fakeRanLinesAndRunningCardsAreDropped() {
        val parts = expandToolBlocksForModel("x\n[ran `uname -a`: exit 0]\n```raika-tool\nrun\nls\npending\n```\ny")
        assertEquals(1, parts.size)
        assertFalse(parts[0].second.contains("[ran"))
        assertFalse(parts[0].second.contains("raika-tool"))
        assertEquals("x\n\ny", parts[0].second)
    }
}
