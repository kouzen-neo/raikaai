package com.raikaai.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the head/tail clipping used by [ToolCard]'s detail well.
 */
class ToolTextClipTest {

    @Test
    fun `short text is returned whole`() {
        val text = "line1\nline2\nline3"

        val result = clipToolText(text)

        assertFalse(result.clipped)
        assertEquals(text, result.head)
        assertNull(result.tail)
        assertEquals(3, result.totalLines)
        assertEquals(0, result.hiddenLines)
    }

    @Test
    fun `empty text is not clipped`() {
        val result = clipToolText("")

        assertFalse(result.clipped)
        assertEquals("", result.head)
        assertEquals(1, result.totalLines)
    }

    @Test
    fun `text exactly at the character limit is not clipped`() {
        val text = "a".repeat(TOOL_CLIP_MAX_CHARS)

        val result = clipToolText(text)

        assertFalse(result.clipped)
        assertEquals(text, result.head)
    }

    @Test
    fun `text with exactly head plus tail lines is not clipped`() {
        val text = (0 until TOOL_CLIP_HEAD_LINES + TOOL_CLIP_TAIL_LINES).joinToString("\n") { "x" }

        val result = clipToolText(text)

        assertFalse(result.clipped)
        assertEquals(TOOL_CLIP_HEAD_LINES + TOOL_CLIP_TAIL_LINES, result.totalLines)
    }

    @Test
    fun `single long line is clipped by character budget`() {
        val text = "a".repeat(20_000)

        val result = clipToolText(text)

        assertTrue(result.clipped)
        assertEquals(1, result.totalLines)
        assertEquals(0, result.hiddenLines)
        // head 4800 + hidden 4000 + tail 11200 = 20000.
        assertEquals(4_800, result.head.length)
        assertEquals(4_000, result.hiddenChars)
        assertEquals(11_200, result.tail!!.length)
    }

    @Test
    fun `many lines are clipped to the first and last line windows`() {
        val text = (0 until 250).joinToString("\n") { "x" }

        val result = clipToolText(text)

        assertTrue(result.clipped)
        assertEquals(250, result.totalLines)
        // 60 head lines + 50 hidden + 140 tail lines = 250.
        assertEquals(50, result.hiddenLines)
        assertEquals(100, result.hiddenChars)
        assertEquals(text.substring(0, 119), result.head)
        assertEquals(text.substring(220), result.tail)
        assertEquals(TOOL_CLIP_HEAD_LINES, result.head.lines().size)
        assertEquals(TOOL_CLIP_TAIL_LINES, result.tail!!.lines().size)
    }

    @Test
    fun `custom line limits are honored`() {
        val text = (0 until 20).joinToString("\n") { "x" }

        val result = clipToolText(text, maxChars = 1_000, headLines = 2, tailLines = 3)

        assertTrue(result.clipped)
        assertEquals(20, result.totalLines)
        assertEquals("x\nx", result.head)
        assertEquals(15, result.hiddenLines)
        assertEquals(30, result.hiddenChars)
        assertEquals(3, result.tail!!.lines().size)
    }

    @Test
    fun `cut never splits a surrogate pair`() {
        // With maxChars = 100 the head budget is 30; put an emoji so that
        // index 30 is its low surrogate.
        val text = "a".repeat(29) + "😀" + "b".repeat(100)

        val result = clipToolText(text, maxChars = 100)

        assertTrue(result.clipped)
        assertEquals("a".repeat(29), result.head)
        assertFalse(Character.isHighSurrogate(result.head.last()))
        assertFalse(Character.isLowSurrogate(result.head.last()))
        val tail = result.tail!!
        assertFalse(Character.isHighSurrogate(tail.first()))
        assertFalse(Character.isLowSurrogate(tail.first()))
    }
}
