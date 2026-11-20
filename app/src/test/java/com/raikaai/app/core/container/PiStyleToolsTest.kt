package com.raikaai.app.core.container

import com.google.gson.JsonParser
import com.raikaai.app.ui.chat.parseEditList
import com.raikaai.app.ui.chat.tailForModel
import com.raikaai.app.web.bestPassages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PiStyleToolsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun multiEditAppliesDisjointEditsAgainstOriginal() {
        val dir = tmp.newFolder("workspace")
        java.io.File(dir, "a.txt").writeText("alpha\nbeta\ngamma\n")
        val res = ContainerFileOps.multiEdit("a.txt", listOf("alpha" to "ALPHA", "gamma" to "GAMMA"), dir)
        assertEquals(2, res.occurrences)
        assertEquals("ALPHA\nbeta\nGAMMA\n", java.io.File(dir, "a.txt").readText())
    }

    @Test
    fun multiEditRejectsAmbiguousOrOverlappingEdits() {
        val dir = tmp.newFolder("workspace")
        val f = java.io.File(dir, "a.txt")
        f.writeText("x x\nabcdef\n")
        for (edits in listOf(listOf("x" to "y"), listOf("abcd" to "1", "cdef" to "2"))) {
            try {
                ContainerFileOps.multiEdit("a.txt", edits, dir)
                fail("expected rejection for $edits")
            } catch (_: IllegalArgumentException) {
            }
        }
        // All or nothing: the file is untouched.
        assertEquals("x x\nabcdef\n", f.readText())
    }

    @Test
    fun editListAcceptsArrayStringAndSingleObject() {
        val arr = JsonParser.parseString("""{"edits":[{"oldText":"a","newText":"b"},{"old_string":"c","new_string":"d"}]}""").asJsonObject
        assertEquals(listOf("a" to "b", "c" to "d"), parseEditList(arr))
        val str = JsonParser.parseString("""{"edits":"[{\"oldText\":\"a\",\"newText\":\"b\"}]"}""").asJsonObject
        assertEquals(listOf("a" to "b"), parseEditList(str))
        val single = JsonParser.parseString("""{"edits":{"oldText":"a","newText":"b"}}""").asJsonObject
        assertEquals(listOf("a" to "b"), parseEditList(single))
    }

    @Test
    fun tailForModelKeepsTheEnd() {
        val text = (1..500).joinToString("\n") { "line $it" } + "\nERROR: build failed"
        val out = tailForModel(text, 200)
        assertTrue(out.endsWith("ERROR: build failed"))
        assertTrue(out.startsWith("…("))
        assertEquals("short", tailForModel("short", 200))
    }

    @Test
    fun bestPassagesPicksRelevantSentences() {
        val page = "Welcome to our site. Cookies are used here. " +
            "The Eiffel Tower is 330 metres tall after a new antenna was added. " +
            "Subscribe to our newsletter today for more updates."
        val out = bestPassages("how tall is the eiffel tower", page, 300)
        assertTrue(out.contains("330 metres"))
        assertTrue(!out.contains("newsletter"))
    }
}
