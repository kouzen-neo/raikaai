package com.raikaai.app.ui.component.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTextClipTest {

    @Test
    fun `short text is not clipped`() {
        val text = "one\ntwo"
        val clip = clipLongText(text)
        assertFalse(clip.clipped)
        assertEquals(text, clip.head)
        assertNull(clip.tail)
        assertEquals(2, clip.totalLines)
        assertEquals(0, clip.hiddenLines)
    }

    @Test
    fun `clips by line budget with sixty head and one hundred forty tail lines`() {
        val text = (1..300).joinToString("\n") { "line $it" }
        val clip = clipLongText(text)

        assertTrue(clip.clipped)
        assertEquals(300, clip.totalLines)
        assertEquals(60, clip.head.lines().size)
        assertEquals(140, clip.tail!!.lines().size)
        assertEquals(100, clip.hiddenLines)
        assertTrue(clip.hiddenChars > 0)
    }

    @Test
    fun `long single line is clipped by character budget`() {
        val clip = clipLongText("x".repeat(20000))

        assertTrue(clip.clipped)
        assertEquals(0, clip.hiddenLines)
        assertEquals(4000, clip.hiddenChars)
        assertEquals(4800, clip.head.length)
        assertEquals(11200, clip.tail!!.length)
    }

    @Test
    fun `line hidden marker names the hidden line count`() {
        val marker = hiddenLinesMarker(100, 0)
        assertTrue(marker.contains("100 lines hidden"))
    }

    @Test
    fun `characters hidden marker is used when no whole lines are hidden`() {
        val marker = hiddenLinesMarker(0, 4000)
        assertTrue(marker.contains("4000 characters hidden"))
        assertFalse(marker.contains("lines hidden"))
    }

    @Test
    fun `respects custom budgets`() {
        val text = (1..10).joinToString("\n") { "line$it" }
        val clip = clipLongText(text, maxChars = 100, headLines = 2, tailLines = 2)

        assertTrue(clip.clipped)
        assertEquals(10, clip.totalLines)
        assertEquals(2, clip.head.lines().size)
        assertEquals(2, clip.tail!!.lines().size)
        assertEquals(6, clip.hiddenLines)
    }

    @Test
    fun `head cut does not split a surrogate pair`() {
        val emoji = "\uD83D\uDE00"
        val text = "a".repeat(5) + emoji + "b".repeat(30)
        val clip = clipLongText(text, maxChars = 20, headLines = 1, tailLines = 1)

        assertTrue(clip.clipped)
        assertEquals(5, clip.head.length)
        assertEquals("aaaaa", clip.head)
        assertFalse((clip.head.last().code and 0xFC00) == 0xD800)
    }

    @Test
    fun `empty text is not clipped`() {
        val clip = clipLongText("")
        assertFalse(clip.clipped)
        assertEquals("", clip.head)
        assertEquals(1, clip.totalLines)
    }
}
