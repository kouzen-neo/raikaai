package com.raikaai.app.core.translate

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** A language the user can translate messages into. */
data class TranslateLanguage(val tag: String, val englishName: String, val nativeName: String)

/**
 * Where translated messages go. Defaults to Indonesian; [PHONE] follows the
 * phone's language. Stored in plain SharedPreferences like RemoteUsage.
 */
object TranslatePrefs {
    const val PHONE = "phone"
    const val DEFAULT_TAG = "id"

    private var prefs: SharedPreferences? = null
    private val _target = MutableStateFlow(DEFAULT_TAG)
    val target: StateFlow<String> = _target.asStateFlow()
    private val _autoGreeting = MutableStateFlow(false)
    val autoGreeting: StateFlow<Boolean> = _autoGreeting.asStateFlow()

    /** "fast" = on-device ML Kit, "model" = the current chat model. */
    const val ENGINE_FAST = "fast"
    const val ENGINE_MODEL = "model"
    private val _engine = MutableStateFlow(ENGINE_FAST)
    val engine: StateFlow<String> = _engine.asStateFlow()

    fun setEngine(value: String) {
        _engine.value = value
        prefs?.edit()?.putString("engine", value)?.apply()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("translate", Context.MODE_PRIVATE)
        prefs = p
        _target.value = p.getString("target", DEFAULT_TAG) ?: DEFAULT_TAG
        _autoGreeting.value = p.getBoolean("auto_greeting", false)
        _engine.value = p.getString("engine", ENGINE_FAST) ?: ENGINE_FAST
    }

    fun setTarget(tag: String) {
        _target.value = tag
        prefs?.edit()?.putString("target", tag)?.apply()
    }

    fun setAutoGreeting(on: Boolean) {
        _autoGreeting.value = on
        prefs?.edit()?.putBoolean("auto_greeting", on)?.apply()
    }

    /** The concrete language tag, resolving [PHONE] to the system language. */
    fun resolvedTag(tag: String = _target.value): String =
        if (tag == PHONE) Locale.getDefault().language.ifBlank { DEFAULT_TAG } else tag

    /** English name used in the prompt, e.g. "Indonesian". */
    fun englishName(tag: String = _target.value): String =
        Locale.forLanguageTag(resolvedTag(tag)).getDisplayLanguage(Locale.ENGLISH).ifBlank { "Indonesian" }

    /** Name shown in Settings: "Bahasa Indonesia (Indonesian)", or "Phone language (…)". */
    fun label(tag: String = _target.value): String {
        val lang = languageFor(resolvedTag(tag))
        val base = if (lang.nativeName.equals(lang.englishName, ignoreCase = true)) lang.englishName else "${lang.nativeName} (${lang.englishName})"
        return if (tag == PHONE) "Phone language · $base" else base
    }

    fun languageFor(tag: String): TranslateLanguage {
        val l = Locale.forLanguageTag(tag)
        val en = l.getDisplayLanguage(Locale.ENGLISH).ifBlank { tag }
        val native = l.getDisplayLanguage(l).ifBlank { en }.replaceFirstChar { it.titlecase(l) }
        return TranslateLanguage(tag, en, native)
    }

    /** Every language Android knows, one entry per language, sorted by English name. */
    val languages: List<TranslateLanguage> by lazy {
        Locale.getAvailableLocales()
            .map { it.language }
            .filter { it.length in 2..3 }
            .distinct()
            .map { languageFor(it) }
            .filter { it.englishName != it.tag }
            .sortedBy { it.englishName.lowercase() }
    }
}

/** Builds the translation request; kept pure so it can be unit tested. */
object TranslatePrompt {
    const val SYSTEM =
        "You are a translator for a chat app. Translate the user's text faithfully and output ONLY the translation, " +
            "with no notes, quotes around it, or explanations. Do not think or reason first; answer directly."

    fun build(text: String, languageName: String): String =
        buildString {
            appendLine("Translate the text below into $languageName.")
            appendLine("Rules:")
            appendLine("- Keep the formatting exactly: *actions in asterisks*, \"quoted dialogue\", line breaks, markdown and emoji.")
            appendLine("- Do not translate names of people or places, and keep placeholders like {{user}} and {{char}} unchanged.")
            appendLine("- Keep the tone and personality of the speaker.")
            appendLine("- If the text is already in $languageName, return it unchanged.")
            appendLine()
            appendLine("Text:")
            append(text.trim())
        }

    /** Output budget sized to the text: about 2x its tokens, never huge. */
    fun maxTokensFor(text: String, local: Boolean): Int {
        val est = text.length / 3 + 64
        return (est * 2).coerceIn(128, if (local) 768 else 1536)
    }

    /** Removes wrappers some models add despite the instructions. */
    fun clean(output: String): String {
        var s = output.trim()
        s = s.removePrefix("Translation:").removePrefix("Here is the translation:").trim()
        if (s.startsWith("```") && s.endsWith("```")) s = s.removePrefix("```").removeSuffix("```").trim()
        return s
    }
}
