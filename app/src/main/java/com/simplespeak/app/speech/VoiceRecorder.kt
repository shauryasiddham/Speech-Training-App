package com.simplespeak.app.speech

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Records short voice clips (saved for the caregiver) and plays them back. */
class VoiceRecorder(private val context: Context) {
    var recording by mutableStateOf(false)
        private set
    var playingPath by mutableStateOf<String?>(null)
        private set

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var player: MediaPlayer? = null

    fun start(file: File): Boolean {
        stopPlayback()
        return runCatching {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(22_050)
            r.setMaxDuration(20_000)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop()
            }
            r.prepare()
            r.start()
            recorder = r
            currentFile = file
            recording = true
            true
        }.getOrElse {
            recorder?.release(); recorder = null; recording = false
            false
        }
    }

    /** Stops recording and returns the saved file, or null if nothing usable was recorded. */
    fun stop(): File? {
        val r = recorder ?: return null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        recording = false
        val f = currentFile
        currentFile = null
        if (!ok) f?.delete()
        return if (ok) f else null
    }

    fun play(path: String) {
        stopPlayback()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            playingPath = path
        }
    }

    fun stopPlayback() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        playingPath = null
    }

    fun release() {
        stop()
        stopPlayback()
    }
}
