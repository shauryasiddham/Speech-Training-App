package com.simplespeak.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Stores attempts, voice recordings and caregiver photos as JSON files in app storage. */
class ProgressStore(private val context: Context) {
    private val attemptsFile = File(context.filesDir, "attempts.json")
    private val recordingsFile = File(context.filesDir, "recordings.json")
    private val photosFile = File(context.filesDir, "photos.json")
    val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
    private val photosDir = File(context.filesDir, "photos").apply { mkdirs() }

    // ---- Attempts -------------------------------------------------------------

    @Synchronized
    fun attempts(): List<Attempt> = readArray(attemptsFile).mapNotNull { o ->
        runCatching {
            Attempt(
                time = o.getLong("time"),
                type = ExerciseType.valueOf(o.getString("type")),
                level = o.optInt("level", 1),
                prompt = o.optString("prompt"),
                answer = o.optString("answer"),
                correct = o.getBoolean("correct"),
                mistakes = o.optJSONArray("mistakes")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                seconds = o.optInt("seconds", 0),
            )
        }.getOrNull()
    }

    @Synchronized
    fun addAttempt(a: Attempt) {
        val arr = JSONArray(readArray(attemptsFile).takeLast(4999))
        arr.put(
            JSONObject()
                .put("time", a.time)
                .put("type", a.type.name)
                .put("level", a.level)
                .put("prompt", a.prompt)
                .put("answer", a.answer)
                .put("correct", a.correct)
                .put("mistakes", JSONArray(a.mistakes))
                .put("seconds", a.seconds)
        )
        attemptsFile.writeText(arr.toString())
    }

    // ---- Recordings -----------------------------------------------------------

    @Synchronized
    fun recordings(): List<Recording> = readArray(recordingsFile).mapNotNull { o ->
        runCatching {
            Recording(o.getString("id"), o.getLong("time"), o.optString("sentence"), o.getString("path"))
        }.getOrNull()
    }.filter { File(it.path).exists() }.sortedByDescending { it.time }

    fun newRecordingFile(): File = File(recordingsDir, "${System.currentTimeMillis()}.m4a")

    @Synchronized
    fun addRecording(file: File, sentence: String) {
        val arr = JSONArray(readArray(recordingsFile))
        arr.put(
            JSONObject().put("id", UUID.randomUUID().toString()).put("time", System.currentTimeMillis())
                .put("sentence", sentence).put("path", file.absolutePath)
        )
        recordingsFile.writeText(arr.toString())
    }

    @Synchronized
    fun deleteRecording(id: String) {
        val kept = readArray(recordingsFile).filter { o ->
            if (o.optString("id") == id) {
                File(o.optString("path")).delete(); false
            } else true
        }
        recordingsFile.writeText(JSONArray(kept).toString())
    }

    // ---- Photos ---------------------------------------------------------------

    @Synchronized
    fun photos(): List<Photo> = readArray(photosFile).mapNotNull { o ->
        runCatching { Photo(o.getString("id"), o.getString("path"), o.optString("caption")) }.getOrNull()
    }.filter { File(it.path).exists() }

    /** Copies a picked image into app storage, scaled down so it is quick to send to Claude. */
    @Synchronized
    fun importPhoto(uri: Uri, caption: String): Photo? {
        val bitmap = decodeScaled(uri, 1024) ?: return null
        val id = UUID.randomUUID().toString()
        val file = File(photosDir, "$id.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        val arr = JSONArray(readArray(photosFile))
        arr.put(JSONObject().put("id", id).put("path", file.absolutePath).put("caption", caption.trim()))
        photosFile.writeText(arr.toString())
        return Photo(id, file.absolutePath, caption.trim())
    }

    @Synchronized
    fun deletePhoto(id: String) {
        val kept = readArray(photosFile).filter { o ->
            if (o.optString("id") == id) {
                File(o.optString("path")).delete(); false
            } else true
        }
        photosFile.writeText(JSONArray(kept).toString())
    }

    @Synchronized
    fun clearProgress() {
        attemptsFile.delete()
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val scale = maxSide.toFloat() / maxOf(raw.width, raw.height)
        if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
    }.getOrNull()

    private fun readArray(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }
}
