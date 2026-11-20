package com.raikaai.app.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatePromptTest {
    @Test
    fun promptNamesLanguageAndKeepsText() {
        val p = TranslatePrompt.build("  *She smiles.* \"Hi, {{user}}!\"  ", "Indonesian")
        assertTrue(p.contains("into Indonesian"))
        assertTrue(p.contains("{{user}}"))
        assertTrue(p.endsWith("*She smiles.* \"Hi, {{user}}!\""))
    }

    @Test
    fun cleanStripsCommonWrappers() {
        assertEquals("Halo", TranslatePrompt.clean("Translation: Halo"))
        assertEquals("Halo", TranslatePrompt.clean("```\nHalo\n```"))
        assertEquals("*Dia tersenyum.*", TranslatePrompt.clean("  *Dia tersenyum.*  "))
    }
}
