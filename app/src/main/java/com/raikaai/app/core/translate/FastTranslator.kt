package com.raikaai.app.core.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage as MlLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A piece of a message: text to translate, or markup to keep as-is. */
data class Segment(val text: String, val translate: Boolean)

/**
 * Splits a message so a sentence translator never sees the markup roleplay
 * relies on: `*` and `**` markers, `"` quotes, `{{user}}` style placeholders,
 * code spans and line breaks stay untouched; the words between them are
 * translated. Pure, unit tested.
 */
object TranslateSegments {
    private val KEEP = Regex("""(```[\s\S]*?```|`[^`\n]*`|\{\{[^}]+\}\}|\*+|_{2,}|["“”]|\n+|^\s*(?:[-•>]|\d+\.)\s+)""", RegexOption.MULTILINE)
    private val LETTER = Regex("""\p{L}""")

    fun split(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var last = 0
        fun addText(t: String) {
            if (t.isEmpty()) return
            // Keep the outer spaces outside the translated part.
            val lead = t.takeWhile { it.isWhitespace() }
            val trail = t.drop(lead.length).takeLastWhile { it.isWhitespace() }
            val core = t.substring(lead.length, t.length - trail.length)
            if (lead.isNotEmpty()) out += Segment(lead, false)
            if (core.isNotEmpty()) out += Segment(core, LETTER.containsMatchIn(core))
            if (trail.isNotEmpty()) out += Segment(trail, false)
        }
        KEEP.findAll(text).forEach { m ->
            addText(text.substring(last, m.range.first))
            out += Segment(m.value, false)
            last = m.range.last + 1
        }
        addText(text.substring(last))
        return out
    }

    fun join(segments: List<Segment>): String = segments.joinToString("") { it.text }
}

/**
 * On-device translation with Google ML Kit: a dedicated translation model,
 * not an LLM, so a reply comes back in well under a second once the
 * language pack is on the phone.
 */
object FastTranslator {
    class Unsupported(lang: String) : Exception("$lang isn't available in Fast translation")

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
    }
    private val translators = mutableMapOf<String, Translator>()

    fun supports(tag: String): Boolean = MlLanguage.fromLanguageTag(tag) != null

    /** Translates [text] into [targetTag]; [onStatus] reports a first-time pack download. */
    suspend fun translate(text: String, targetTag: String, onStatus: (String) -> Unit = {}): String {
        val target = MlLanguage.fromLanguageTag(targetTag) ?: throw Unsupported(TranslatePrefs.languageFor(targetTag).englishName)
        val key = "$target\u0000$text"
        synchronized(cache) { cache[key] }?.let { return it }

        val detected = runCatching {
            LanguageIdentification.getClient().identifyLanguage(text.take(1000)).await()
        }.getOrDefault("und")
        val source = MlLanguage.fromLanguageTag(detected) ?: MlLanguage.ENGLISH
        if (source == target) return text

        val translator = synchronized(translators) {
            translators.getOrPut("$source>$target") {
                Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
            }
        }
        onStatus("Getting the ${TranslatePrefs.languageFor(targetTag).englishName} pack (first time only)…")
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()

        val segments = TranslateSegments.split(text).map { seg ->
            if (seg.translate) seg.copy(text = translator.translate(seg.text).await()) else seg
        }
        val result = TranslateSegments.join(segments)
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
