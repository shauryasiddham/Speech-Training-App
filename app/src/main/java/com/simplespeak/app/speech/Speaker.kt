package com.simplespeak.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads sentences aloud with Android's built-in text-to-speech. */
class Speaker(context: Context, private val rate: () -> Float) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val locale = Locale.getDefault().takeIf { it.language == "en" } ?: Locale.US
            tts.setLanguage(locale)
        }
    }

    fun say(text: String) {
        if (!ready || text.isBlank()) return
        tts.setSpeechRate(rate())
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "simplespeak")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()
}
