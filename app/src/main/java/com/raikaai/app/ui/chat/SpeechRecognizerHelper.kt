package com.raikaai.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateOf
import com.raikaai.app.util.KLog
import java.util.Locale

/**
 * On-device speech-to-text with live partial results.
 *
 * Fallback to the system dialog only happens when the recognizer service is
 * missing or broken, never for silence or "no match": those just end the
 * session quietly (the user can tap again).
 */
class SpeechRecognizerHelper(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    var isListening = mutableStateOf(false)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * [onPartial] receives the text heard so far (replace, don't append);
     * [onResult] the final text.
     */
    fun startListening(
        onResult: (String) -> Unit,
        onFallbackToIntent: (Intent) -> Unit,
        onPartial: (String) -> Unit = {},
    ) {
        handler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onFallbackToIntent(dialogIntent())
                    return@post
                }
                // Synchronous: a posted destroy would run after the new
                // recognizer starts and kill it at once.
                releaseNow()

                val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = newRecognizer
                var lastPartial = ""
                newRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { isListening.value = true }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        isListening.value = false
                        KLog.w("STT", "SpeechRecognizer error: $error")
                        when (error) {
                            // Nothing heard: keep what was recognized, no dialog.
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            -> if (lastPartial.isNotBlank()) onResult(lastPartial)
                            // Service missing or broken: use the system dialog.
                            SpeechRecognizer.ERROR_SERVER,
                            SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                            -> onFallbackToIntent(dialogIntent())
                            else -> if (lastPartial.isNotBlank()) onResult(lastPartial)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening.value = false
                        val text =
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.trim().orEmpty()
                        val final = text.ifBlank { lastPartial }
                        if (final.isNotBlank()) onResult(final)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text =
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            lastPartial = text
                            onPartial(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                newRecognizer.startListening(
                    baseIntent().apply {
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                        // Allow natural pauses before the recognizer ends.
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                    },
                )
                isListening.value = true
            } catch (t: Throwable) {
                KLog.e("STT", "Failed to start speech recognizer, using intent fallback", t)
                isListening.value = false
                onFallbackToIntent(dialogIntent())
            }
        }
    }

    fun stopListening() {
        handler.post {
            try {
                // stopListening delivers onResults with what was heard.
                recognizer?.stopListening()
            } catch (e: Throwable) {
                KLog.e("STT", "Failed to stop listening", e)
            }
            isListening.value = false
        }
    }

    fun destroy() {
        handler.post { releaseNow() }
    }

    private fun releaseNow() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Throwable) {
            KLog.e("STT", "Failed to destroy recognizer", e)
        }
        recognizer = null
        isListening.value = false
    }

    private fun baseIntent() =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun dialogIntent() =
        baseIntent().apply { putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to RaikaAI…") }
}
