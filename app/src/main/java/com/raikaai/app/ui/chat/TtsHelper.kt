package com.raikaai.app.ui.chat

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.raikaai.app.util.KLog
import java.util.Locale

/**
 * Helper class for Text-to-Speech functionality.
 * Strips markdown formatting before speaking.
 */
class TtsHelper(private val context: Context) : TextToSpeech.OnInitListener {
    companion object {
        private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```")
        private val THINK_CLOSED_REGEX = Regex("<(?:think|thought|thinking)(?:\\s+[^>]*)?>[\\s\\S]*?</(?:think|thought|thinking)>", RegexOption.IGNORE_CASE)
        private val THINK_OPEN_REGEX = Regex("<(?:think|thought|thinking)(?:\\s+[^>]*)?>[\\s\\S]*", RegexOption.IGNORE_CASE)
        private val TOOL_CALL_REGEX = Regex("<(?:tool_call|action)(?:\\s+[^>]*)?>[\\s\\S]*?</(?:tool_call|action)>", RegexOption.IGNORE_CASE)
        private val INLINE_CODE_REGEX = Regex("`[^`]*`")
        private val MARKDOWN_CHARS_REGEX = Regex("[#*_~>\\[\\]]")
    }
    private var tts: TextToSpeech? = null
    var isInitialized = mutableStateOf(false)
    var currentlySpeakingId = mutableStateOf<String?>(null)

    /** True while an engine is binding; taps then wait instead of starting another one. */
    private var initializing = false
    /** Set when the default engine failed and the Google engine was tried. */
    private var triedFallbackEngine = false
    /** The message tapped before the engine was ready; spoken as soon as it is. */
    private var pending: Pair<String, String>? = null

    init {
        createEngine(null)
    }

    private fun createEngine(enginePackage: String?) {
        initializing = true
        isInitialized.value = false
        try {
            // A fresh listener per instance, so a late callback from an old
            // engine can never mark a newer one as ready.
            var created: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status -> onEngineInit(created, status) }
            created = if (enginePackage == null) {
                TextToSpeech(context.applicationContext, listener)
            } else {
                TextToSpeech(context.applicationContext, listener, enginePackage)
            }
            tts = created
        } catch (e: Throwable) {
            initializing = false
            KLog.e("TTS", "Failed to create TextToSpeech instance", e)
        }
    }

    override fun onInit(status: Int) = onEngineInit(tts, status)

    private fun onEngineInit(engine: TextToSpeech?, status: Int) {
        if (engine != null && engine !== tts) return // superseded instance
        initializing = false
        if (status != TextToSpeech.SUCCESS) {
            isInitialized.value = false
            KLog.w("TTS", "TextToSpeech init failed with status: $status")
            runCatching { engine?.shutdown() }
            tts = null
            if (!triedFallbackEngine) {
                // Some phones bind the default engine slowly or not at all;
                // the Google engine is usually installed and works.
                triedFallbackEngine = true
                createEngine("com.google.android.tts")
            } else if (pending != null) {
                pending = null
                Toast.makeText(context, "Text-to-speech isn't available. Check Settings → Text-to-speech output on your phone.", Toast.LENGTH_LONG).show()
            }
            return
        }
        try {
            var langResult = engine?.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                langResult = engine?.setLanguage(Locale.US)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.setLanguage(Locale.ENGLISH)
                }
            }
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    currentlySpeakingId.value = utteranceId
                }
                override fun onDone(utteranceId: String?) {
                    if (currentlySpeakingId.value == utteranceId) currentlySpeakingId.value = null
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (currentlySpeakingId.value == utteranceId) currentlySpeakingId.value = null
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (currentlySpeakingId.value == utteranceId) currentlySpeakingId.value = null
                }
            })
            isInitialized.value = true
            KLog.d("TTS", "TextToSpeech initialized successfully")
        } catch (e: Throwable) {
            KLog.e("TTS", "Error setting up TextToSpeech", e)
        }
        pending?.let { (id, text) ->
            pending = null
            speakNow(id, text)
        }
    }

    fun speak(id: String, text: String) {
        if (currentlySpeakingId.value == id) {
            stop()
            return
        }
        stop()

        // Strip markdown code blocks, tags, and formatting before speaking
        val cleanText =
            text
                .replace(CODE_BLOCK_REGEX, " Code block omitted. ")
                .replace(THINK_CLOSED_REGEX, "")
                .replace(THINK_OPEN_REGEX, "")
                .replace(TOOL_CALL_REGEX, "")
                .replace(INLINE_CODE_REGEX, "")
                .replace(MARKDOWN_CHARS_REGEX, "")
                .trim()

        if (cleanText.isBlank()) return

        if (tts == null || !isInitialized.value) {
            // Not ready yet: remember the tap and speak it when the engine is
            // up. Only start a new engine when none is loading.
            pending = id to cleanText
            currentlySpeakingId.value = id
            if (!initializing) {
                triedFallbackEngine = false
                createEngine(null)
            }
            return
        }
        speakNow(id, cleanText)
    }

    private fun speakNow(id: String, cleanText: String) {
        try {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
            }
            val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, id)
            if (result == TextToSpeech.SUCCESS) {
                currentlySpeakingId.value = id
            } else {
                KLog.w("TTS", "tts.speak returned non-success code: $result")
                currentlySpeakingId.value = null
                Toast.makeText(context, "Text-to-speech error (code $result)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            KLog.e("TTS", "Failed to speak utterance", e)
            currentlySpeakingId.value = null
        }
    }

    fun stop() {
        pending = null
        try {
            tts?.stop()
        } catch (e: Throwable) {
            KLog.e("TTS", "Failed to stop TTS", e)
        }
        currentlySpeakingId.value = null
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Throwable) {
            KLog.e("TTS", "Failed to shutdown TTS", e)
        }
        tts = null
        pending = null
        initializing = false
        currentlySpeakingId.value = null
        isInitialized.value = false
    }
}
