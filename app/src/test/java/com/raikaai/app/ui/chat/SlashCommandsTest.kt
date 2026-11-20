package com.raikaai.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {

    @Test
    fun roleplayHidesDeveloperCommands() {
        val names = availableSlashCommands(isRoleplay = true, linuxInstalled = true).map { it.command }
        assertTrue("/continue" in names)
        assertTrue("/ooc" in names)
        assertFalse("/run" in names)
        assertFalse("/summarize" in names)
    }

    @Test
    fun linuxCommandsNeedInstalledEnvironment() {
        val without = availableSlashCommands(isRoleplay = false, linuxInstalled = false).map { it.command }
        assertFalse("/run" in without)
        assertTrue("/env" in without)
        val with = availableSlashCommands(isRoleplay = false, linuxInstalled = true).map { it.command }
        assertTrue("/run" in with)
        assertFalse("/continue" in with)
    }

    @Test
    fun prefixMatchesComeFirstAndArgumentsCloseMenu() {
        val all = availableSlashCommands(isRoleplay = false, linuxInstalled = true)
        assertEquals("/retry", matchSlashCommands(all, "/ret").first().command)
        assertTrue(matchSlashCommands(all, "/run ls").isEmpty())
    }

    @Test
    fun commandNamesAreUnique() {
        val names = DEFAULT_SLASH_COMMANDS.map { it.command }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun btwIsOfferedAndPermissionReplacesYolo() {
        val chat = availableSlashCommands(isRoleplay = false, linuxInstalled = true).map { it.command }
        assertTrue("/btw" in chat)
        assertTrue("/permission" in chat)
        assertFalse("/yolo" in chat)
        assertTrue("/btw" in availableSlashCommands(isRoleplay = true, linuxInstalled = false).map { it.command })
    }
}
