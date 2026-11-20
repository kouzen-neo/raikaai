package com.raikaai.app.core.roleplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayPromptTest {
    @Test
    fun leanPromptIsCardPlusNoticeOnly() {
        val p = RoleplayPrompt.build("You are Asta.\n")
        assertTrue(p.startsWith("You are Asta."))
        assertTrue(p.endsWith(RoleplayPrompt.NOTICE))
        assertFalse(p.contains("tool_call"))
        assertFalse(p.contains("[IMAGE GENERATION TOOL]"))
        assertEquals(RoleplayPrompt.NOTICE, RoleplayPrompt.build(""))
    }

    @Test
    fun noticeTellsUserWhereToSwitch() {
        assertTrue(RoleplayPrompt.NOTICE.contains("Roleplay mode in Settings"))
    }

    @Test
    fun detectsCharacter() {
        assertTrue(RoleplayPrompt.isCharacter("roleplay_42"))
        assertTrue(RoleplayPrompt.isCharacter("custom_asta"))
        assertFalse(RoleplayPrompt.isCharacter("general"))
        assertFalse(RoleplayPrompt.isCharacter(""))
        assertFalse(RoleplayPrompt.isCharacter(null))
    }
}
