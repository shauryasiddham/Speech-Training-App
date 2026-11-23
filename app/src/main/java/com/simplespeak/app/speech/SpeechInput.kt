package com.simplespeak.app.speech

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.simplespeak.app.data.Fluency
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/** What the learner said, plus how he said it. */
data class SpokenAnswer(val text: String, val fluency: Fluency, val audio: File?)

/**
 * Android's built-in speech recognizer with simple Compose state.
 *
 * Also measures fluency (time to first word, time spent speaking) and, on Android 13 and newer,
 * can keep a copy of the audio: the app records the microphone itself and feeds the same audio
 * to the recognizer, so the learner's real answer can be played back later. If the phone's speech
 * service does not accept shared audio, [onCaptureFailed] is called and normal listening is used.
 *
 * Must be used from the main thread.
 */
class SpeechInput(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Microphone loudness 0..1, for the animated orb. */
    var level by mutableFloatStateOf(0f)
        private set
    /** Mic failures in a row; the UI offers typing after two. */
    var failures by mutableStateOf(0)
        private set

    var onCaptureFailed: (() -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var onFinal: ((SpokenAnswer) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    private var openedAt = 0L
    private var speechBeganAt = 0L
    private var speechEndedAt = 0L
    private var cancelled = false

    // Shared-audio capture
    private var capture: Capture? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Starts listening. If [saveAudioTo] is set (and the phone supports it) the answer's audio is saved there as WAV.
     */
    fun start(saveAudioTo: File? = null, onResult: (SpokenAnswer) -> Unit) {
        stop()
        cancelled = false
        error = null
        partial = ""
        level = 0f
        onFinal = onResult
        openedAt = System.currentTimeMillis()
        speechBeganAt = 0L
        speechEndedAt = 0L

        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(listener)
        val lang = Locale.getDefault().takeIf { it.language == "en" }?.toLanguageTag() ?: "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Extra time: he may pause while planning the next part of his answer.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        }
        if (saveAudioTo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasPermission) {
            val c = runCatching { Capture(saveAudioTo) }.getOrNull()
            if (c != null) {
                capture = c
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, c.readSide)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
                c.begin()
            }
        }
        listening = true
        r.startListening(intent)
    }

    /** Stop listening; whatever was heard so far is still delivered. */
    fun finish() {
        capture?.endInput()
        recognizer?.stopListening()
    }

    fun stop() {
        cancelled = true
        recognizer?.cancel()
        capture?.abort()
        capture = null
        listening = false
        level = 0f
    }

    /** Stop and forget any old text or message (used when changing screens). */
    fun reset() {
        stop()
        partial = ""
        error = null
        onFinal = null
        failures = 0
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun deliver(text: String) {
        val now = System.currentTimeMillis()
        val began = speechBeganAt.takeIf { it > 0 } ?: capture?.firstSoundAt?.takeIf { it > 0 }
        val ended = speechEndedAt.takeIf { it > 0 } ?: now
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val fluency = Fluency(
            startMs = began?.let { (it - openedAt).coerceAtLeast(0) },
            speakMs = began?.let { (ended - it).coerceAtLeast(0) },
            words = words,
        )
        val audio = capture?.finishAndSave()
        capture = null
        failures = 0
        partial = text
        onFinal?.invoke(SpokenAnswer(text, fluency, audio))
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            if (speechBeganAt == 0L) speechBeganAt = System.currentTimeMillis()
        }
        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            speechEndedAt = System.currentTimeMillis()
            capture?.endInput()
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                if (it.isNotBlank() && speechBeganAt == 0L) speechBeganAt = capture?.firstSoundAt?.takeIf { t -> t > 0 } ?: System.currentTimeMillis()
                partial = it
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            level = 0f
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { partial }
            if (text.isBlank()) {
                capture?.abort(); capture = null
                failures++
                error = "I did not hear any words. Tap the microphone and try again."
            } else deliver(text)
        }

        override fun onError(code: Int) {
            listening = false
            level = 0f
            if (cancelled && code == SpeechRecognizer.ERROR_CLIENT) return // after cancel(); not a real problem
            if (partial.isNotBlank() && (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                deliver(partial)
                return
            }
            val hadCapture = capture != null
            capture?.abort()
            capture = null
            // The phone's speech service did not accept shared audio: fall back to normal listening next time.
            if (hadCapture && speechBeganAt == 0L && code in listOf(SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_AUDIO, 13 /* ERROR_LANGUAGE_NOT_SUPPORTED */, 14 /* ERROR_CANNOT_CHECK_SUPPORT */)) {
                onCaptureFailed?.invoke()
                error = "Please tap the microphone again."
                return
            }
            failures++
            error = when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear any words. Tap the microphone and try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "The app needs permission to use the microphone."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech needs the internet right now. Please check the connection."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Please wait a moment and try again."
                else -> "Something went wrong with listening. Please try again."
            }
        }
    }

    /**
     * Records the microphone, passes the audio to the recognizer through a pipe, and keeps a copy.
     * Stops after 2.5 s of quiet once he has started, or after 60 s.
     */
    @SuppressLint("MissingPermission")
    private inner class Capture(private val file: File) {
        private val pipe = ParcelFileDescriptor.createPipe()
        val readSide: ParcelFileDescriptor = pipe[0]
        private val writeSide = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        private val record: AudioRecord
        private val copy = ByteArrayOutputStream()
        @Volatile private var running = true
        @Volatile var firstSoundAt = 0L
        private var thread: Thread? = null

        init {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096) * 2)
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release(); readSide.close(); writeSide.close()
                throw IOException("Microphone not available")
            }
        }

        fun begin() {
            record.startRecording()
            thread = Thread {
                val buf = ShortArray(1600) // 0.1 s
                val bytes = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                var lastLoud = 0L
                val started = System.currentTimeMillis()
                try {
                    while (running) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        bytes.clear()
                        var sum = 0.0
                        for (i in 0 until n) { bytes.putShort(buf[i]); sum += buf[i] * buf[i].toDouble() }
                        val rms = sqrt(sum / n)
                        val now = System.currentTimeMillis()
                        if (rms > 900) { lastLoud = now; if (firstSoundAt == 0L) firstSoundAt = now }
                        main.post { level = (rms / 6000.0).toFloat().coerceIn(0f, 1f) }
                        writeSide.write(bytes.array(), 0, n * 2)
                        if (copy.size() < RATE * 2 * 60) copy.write(bytes.array(), 0, n * 2)
                        val quietTooLong = firstSoundAt > 0 && now - lastLoud > 2500
                        if (quietTooLong || now - started > 60_000) break
                    }
                } catch (_: IOException) {
                    // The recognizer closed its end; that's fine.
                }
                runCatching { record.stop() }
                runCatching { writeSide.close() }
            }.apply { start() }
        }

        /** No more audio: lets the recognizer finish. */
        fun endInput() { running = false }

        fun abort() {
            running = false
            thread?.join(500)
            runCatching { record.release() }
            runCatching { readSide.close() }
        }

        fun finishAndSave(): File? {
            abort()
            val pcm = copy.toByteArray()
            if (pcm.size < RATE) return null // under half a second
            return runCatching {
                FileOutputStream(file).use { out ->
                    out.write(wavHeader(pcm.size))
                    out.write(pcm)
                }
                file
            }.getOrNull()
        }

        private fun wavHeader(dataLen: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataLen)
        }.array()
    }

    companion object {
        const val RATE = 16_000
    }
}
