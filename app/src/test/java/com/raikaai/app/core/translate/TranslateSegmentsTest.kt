package com.raikaai.app.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateSegmentsTest {
    private val text = "*She smiles.* \"Hi, {{user}}!\"\n\nUse `ls -la` now."

    @Test
    fun joinRestoresOriginal() {
        assertEquals(text, TranslateSegments.join(TranslateSegments.split(text)))
    }

    @Test
    fun markupIsKeptAndWordsTranslated() {
        val segs = TranslateSegments.split(text)
        val translated = segs.filter { it.translate }.map { it.text }
        assertEquals(listOf("She smiles.", "Hi,", "Use", "now."), translated)
        assertTrue(segs.any { !it.translate && it.text == "{{user}}" })
        assertTrue(segs.any { !it.translate && it.text == "`ls -la`" })
    }

    @Test
    fun fakeTranslationKeepsFormatting() {
        val out = TranslateSegments.join(TranslateSegments.split(text).map { if (it.translate) it.copy(text = it.text.uppercase()) else it })
        assertEquals("*SHE SMILES.* \"HI, {{user}}!\"\n\nUSE `ls -la` NOW.", out)
    }

    @Test
    fun outputBudgetScales() {
        assertEquals(128, TranslatePrompt.maxTokensFor("Hi", local = true))
        assertEquals(768, TranslatePrompt.maxTokensFor("x".repeat(5000), local = true))
    }
}
