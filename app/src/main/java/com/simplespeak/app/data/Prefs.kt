package com.simplespeak.app.data

import android.content.Context
import java.time.LocalDate

/** Small settings store. Everything stays on this device. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("simplespeak", Context.MODE_PRIVATE)

    private fun str(key: String, def: String = "") = sp.getString(key, def) ?: def
    private fun put(key: String, v: String) = sp.edit().putString(key, v).apply()
    private fun putInt(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    private fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = putBool("setup_done", v)

    var learnerName: String
        get() = str("learner_name")
        set(v) = put("learner_name", v.trim())

    var ageGroup: AgeGroup
        get() = runCatching { AgeGroup.valueOf(str("age_group", "TEEN")) }.getOrDefault(AgeGroup.TEEN)
        set(v) = put("age_group", v.name)

    /** Things he likes; used to make practice topics more interesting. */
    var interests: String
        get() = str("interests")
        set(v) = put("interests", v.trim())

    // ---- AI ----------------------------------------------------------------------

    var engine: AiEngine
        get() = runCatching { AiEngine.valueOf(str("engine", "MAC")) }.getOrDefault(AiEngine.MAC)
        set(v) = put("engine", v.name)

    /** Address of the Mac running Ollama, e.g. http://192.168.1.20:11434 */
    var macUrl: String
        get() = str("mac_url")
        set(v) = put("mac_url", v.trim())

    var macModel: String
        get() = str("mac_model", com.simplespeak.app.ai.OllamaBackend.DEFAULT_MODEL)
        set(v) = put("mac_model", v.trim())

    /** When the Mac can't be reached, use the phone's model instead (if it is downloaded). */
    var phoneWhenMacAway: Boolean
        get() = sp.getBoolean("phone_when_mac_away", true)
        set(v) = putBool("phone_when_mac_away", v)

    /** "auto", "gpu" or "cpu". Auto tries the GPU first and remembers if it fails. */
    var modelBackend: String
        get() = str("model_backend", "auto")
        set(v) = put("model_backend", v)

    var gpuFailed: Boolean
        get() = sp.getBoolean("gpu_failed", false)
        set(v) = putBool("gpu_failed", v)

    var downloadId: Long
        get() = sp.getLong("download_id", -1L)
        set(v) = sp.edit().putLong("download_id", v).apply()

    // ---- Practice settings -------------------------------------------------------

    var sessionLength: Int
        get() = sp.getInt("session_length", 8)
        set(v) = putInt("session_length", v.coerceIn(3, 20))

    /** Seconds to think before the microphone opens. 0 = start straight away. */
    var thinkSeconds: Int
        get() = sp.getInt("think_seconds", 5)
        set(v) = putInt("think_seconds", v.coerceIn(0, 15))

    /** Answers per day for the daily goal ring. */
    var dailyGoal: Int
        get() = sp.getInt("daily_goal", 10)
        set(v) = putInt("daily_goal", v.coerceIn(3, 40))

    var saveAnswers: Boolean
        get() = sp.getBoolean("save_answers", true)
        set(v) = putBool("save_answers", v)

    /** Set when the phone's speech service cannot share the microphone audio. */
    var answerCaptureBroken: Boolean
        get() = sp.getBoolean("answer_capture_broken", false)
        set(v) = putBool("answer_capture_broken", v)

    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(v) = putBool("reduce_motion", v)

    var speechRate: Float
        get() = sp.getFloat("speech_rate", 0.95f)
        set(v) = sp.edit().putFloat("speech_rate", v).apply()

    /** Read the coach's chat replies aloud automatically. */
    var speakReplies: Boolean
        get() = sp.getBoolean("speak_replies", true)
        set(v) = putBool("speak_replies", v)

    fun isEnabled(type: ExerciseType): Boolean = sp.getBoolean("enabled_${type.name}", type != ExerciseType.BUILD)
    fun setEnabled(type: ExerciseType, on: Boolean) = putBool("enabled_${type.name}", on)

    // ---- Adaptive difficulty -------------------------------------------------------

    fun level(type: ExerciseType): Int = sp.getInt("level_${type.name}", 2).coerceIn(1, ContentBank.MAX_LEVEL)

    fun setLevel(type: ExerciseType, level: Int) {
        sp.edit().putInt("level_${type.name}", level.coerceIn(1, ContentBank.MAX_LEVEL)).putString("history_${type.name}", "").apply()
    }

    /**
     * Records one result. Moves up after 4 of the last 5 are right,
     * down after 3 of the last 4 are wrong. Returns the new level.
     */
    fun recordResult(type: ExerciseType, correct: Boolean): Int {
        val key = "history_${type.name}"
        val history = (str(key) + if (correct) "1" else "0").takeLast(5)
        var level = level(type)
        var newHistory = history
        val last4 = history.takeLast(4)
        if (history.length == 5 && history.count { it == '1' } >= 4 && level < ContentBank.MAX_LEVEL) {
            level++; newHistory = ""
        } else if (last4.length == 4 && last4.count { it == '0' } >= 3 && level > 1) {
            level--; newHistory = ""
        }
        sp.edit().putString(key, newHistory).putInt("level_${type.name}", level).apply()
        return level
    }

    // ---- Streak and rewards ---------------------------------------------------------

    /** Days with practice, newest last (ISO dates). */
    val practicedDays: Set<LocalDate>
        get() = str("practiced_days").split(",").filter { it.isNotBlank() }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    /** Call whenever he finishes an answer; a day counts once. */
    fun markPracticedToday() {
        val days = (practicedDays + LocalDate.now()).sorted().takeLast(400)
        put("practiced_days", days.joinToString(","))
        val s = streak
        if (s > bestStreak) putInt("best_streak", s)
    }

    /** Practice days in a row, ending today (or yesterday, so the streak isn't lost before he practises today). */
    val streak: Int
        get() {
            val days = practicedDays
            var d = LocalDate.now()
            if (d !in days) d = d.minusDays(1)
            var n = 0
            while (d in days) { n++; d = d.minusDays(1) }
            return n
        }

    val bestStreak: Int get() = sp.getInt("best_streak", 0)

    var totalStars: Int
        get() = sp.getInt("total_stars", 0)
        set(v) = putInt("total_stars", v)

    fun resetProgress() {
        val e = sp.edit()
        ExerciseType.entries.forEach { e.remove("level_${it.name}"); e.remove("history_${it.name}") }
        e.remove("practiced_days").remove("best_streak").remove("total_stars").apply()
    }
}
