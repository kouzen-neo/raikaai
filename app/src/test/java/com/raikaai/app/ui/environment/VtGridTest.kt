package com.raikaai.app.ui.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VtGridTest {
    private val replies = mutableListOf<String>()
    private fun grid(rows: Int = 5, cols: Int = 20) = VtGrid(rows, cols) { replies += it }

    @Test
    fun absoluteCursorAddressing() {
        val g = grid()
        g.feed("\u001B[2J\u001B[3;5Hhi\u001B[1;1HA")
        assertEquals("A", g.rowText(0))
        assertEquals("    hi", g.rowText(2))
    }

    @Test
    fun deferredWrapAtLastColumn() {
        val g = grid(cols = 10)
        g.feed("0123456789")
        assertEquals(0, g.cursorRow) // no wrap until the next printable
        g.feed("X")
        assertEquals("0123456789", g.rowText(0))
        assertEquals("X", g.rowText(1))
    }

    @Test
    fun scrollRegionKeepsHeaderAndFooter() {
        val g = grid(rows = 5)
        g.feed("\u001B[1;1Htop\u001B[5;1Hbottom\u001B[2;4r\u001B[2;1Ha\r\nb\r\nc\r\nd")
        assertEquals("top", g.rowText(0))
        assertEquals("b", g.rowText(1))
        assertEquals("c", g.rowText(2))
        assertEquals("d", g.rowText(3))
        assertEquals("bottom", g.rowText(4))
    }

    @Test
    fun answersCursorPositionAndDeviceAttributes() {
        val g = grid()
        g.feed("\u001B[3;7H\u001B[6n\u001B[c\u001B]11;?\u0007")
        assertEquals("\u001B[3;7R", replies[0])
        assertEquals("\u001B[?62;22c", replies[1])
        assertTrue(replies[2].startsWith("\u001B]11;rgb:"))
    }

    @Test
    fun truecolorAndLineDrawing() {
        val g = grid()
        g.feed("\u001B[38;2;255;0;0mR\u001B[0m\u001B(0qx\u001B(B")
        assertEquals(VtGrid.TRUECOLOR or 0xFF0000, g.fg[g.cellIndex(0, 0)])
        assertEquals("R─│", g.rowText(0))
    }

    @Test
    fun wideCharactersTakeTwoCells() {
        val g = grid()
        g.feed("漢a")
        assertEquals(0, g.cp[g.cellIndex(0, 1)])
        assertEquals('a'.code, g.cp[g.cellIndex(0, 2)])
    }

    @Test
    fun stopsAtAlternateScreenExit() {
        val g = grid()
        val text = "x\u001B[?1049lafter"
        val n = g.feed(text)
        assertTrue(g.exitRequested)
        assertEquals("after", text.substring(n))
    }

    @Test
    fun lineScreenHandsOverOnAlternateScreen() {
        val s = AnsiScreen(40)
        s.altScreenEnabled = true
        val text = "prompt\r\n\u001B[?1049h\u001B[Hfull"
        val n = s.feed(text)
        assertTrue(s.altRequested)
        assertEquals("\u001B[Hfull", text.substring(n))
        // Pipe mode keeps ignoring it.
        val pipe = AnsiScreen(40)
        assertEquals(text.length, pipe.feed(text))
        assertFalse(pipe.altRequested)
    }

    @Test
    fun capabilityStringsAreNotPrinted() {
        val s = AnsiScreen(40)
        s.feed("a\u001BP+q544e\u001B\\\u001B_Gi=1\u001B\\b")
        assertEquals("ab", s.plainRow(0))
    }

    @Test
    fun mouseEventsFollowRequestedMode() {
        val g = grid()
        assertEquals(null, g.mouseEvent(0, 1, 1, press = true))
        g.feed("\u001B[?1002h\u001B[?1006h")
        assertEquals("\u001B[<0;3;2M", g.mouseEvent(0, 2, 1, press = true))
        assertEquals("\u001B[<0;3;2m", g.mouseEvent(0, 2, 1, press = false))
        assertEquals("\u001B[<32;4;2M", g.mouseEvent(0, 3, 1, press = true, motion = true))
        assertEquals("\u001B[<64;1;1M", g.mouseEvent(64, 0, 0, press = true))
        g.feed("\u001B[?1006l")
        assertEquals("\u001B[M" + (32 + 0).toChar() + (32 + 3).toChar() + (32 + 2).toChar(), g.mouseEvent(0, 2, 1, press = true))
    }

    @Test
    fun osc52CopiesAndBracketedPasteIsTracked() {
        val g = grid()
        var copied: String? = null
        g.onClipboard = { copied = it }
        val b64 = java.util.Base64.getEncoder().encodeToString("halo dunia".toByteArray())
        g.feed("\u001B]52;c;$b64\u0007\u001B[?2004h")
        assertEquals("halo dunia", copied)
        assertTrue(g.bracketedPaste)
    }

    @Test
    fun doneMarkerIsReadFromLineScreen() {
        val s = AnsiScreen(40)
        s.feed("out\r\n\u001B]9998;3\u0007")
        assertEquals(3, s.doneCode)
        assertEquals("out", s.plainRow(0))
    }
}
