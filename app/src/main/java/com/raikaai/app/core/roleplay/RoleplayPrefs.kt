package com.raikaai.app.core.roleplay

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Roleplay mode: when a character is selected, the chat sends only the
 * character card, your profile and one notice line. No skills, memory files,
 * knowledge, web results or tool catalogs are injected, and tool calls in
 * replies are never run. On by default; turning it off in Settings brings the
 * agent tools back to character chats.
 */
object RoleplayPrefs {
    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roleplay_mode", Context.MODE_PRIVATE)
        prefs = p
        _enabled.value = p.getBoolean("enabled", true)
    }

    fun set(on: Boolean) {
        _enabled.value = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }
}

/** The lean roleplay system prompt; pure so it can be unit tested. */
object RoleplayPrompt {
    const val NOTICE =
        "[Roleplay mode is on] You cannot run code, use tools, browse the web or write programs in this chat. " +
            "If the user asks for that, stay in character and briefly tell them to turn off Roleplay mode in Settings first."

    /** True when a character (anything but the default assistant) is active. */
    fun isCharacter(personaId: String?): Boolean =
        !personaId.isNullOrBlank() && personaId != "general"

    fun build(card: String): String =
        if (card.isBlank()) NOTICE else card.trimEnd() + "\n\n" + NOTICE
}
