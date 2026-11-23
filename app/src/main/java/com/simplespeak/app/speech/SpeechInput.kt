package com.simplespeak.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Wraps Android's built-in speech recognizer and exposes simple Compose state.
 * Must be used from the main thread.
 */
class SpeechInput(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var onFinal: ((String) -> Unit)? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onResult: (String) -> Unit) {
        stop()
        error = null
        partial = ""
        onFinal = onResult
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(listener)
        val lang = Locale.getDefault().takeIf { it.language == "en" }?.toLanguageTag() ?: "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Give extra time: many learners pause while they think.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        }
        listening = true
        r.startListening(intent)
    }

    /** Stop listening; whatever was heard so far is still delivered. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        recognizer?.cancel()
        listening = false
    }

    /** Stop and forget any old text or message (used when changing screens). */
    fun reset() {
        stop()
        partial = ""
        error = null
        onFinal = null
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                .ifBlank { partial }
            if (text.isBlank()) {
                error = "I did not hear any words. Tap the microphone and try again."
            } else {
                partial = text
                onFinal?.invoke(text)
            }
        }

        override fun onError(code: Int) {
            listening = false
            if (code == SpeechRecognizer.ERROR_CLIENT) return // happens after cancel(); not a real problem
            if (partial.isNotBlank() && (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                onFinal?.invoke(partial)
                return
            }
            error = when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "I did not hear any words. Tap the microphone and try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "The app needs permission to use the microphone."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Speech needs the internet right now. Please check the connection."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "The microphone is busy. Please wait a moment and try again."
                else -> "Something went wrong with listening. Please try again."
            }
        }
    }
}
