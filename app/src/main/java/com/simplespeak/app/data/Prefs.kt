package com.simplespeak.app.data

import android.content.Context
import java.time.LocalDate

/** Small settings store. Everything stays on this device. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("simplespeak", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()

    var learnerName: String
        get() = sp.getString("learner_name", "") ?: ""
        set(v) = sp.edit().putString("learner_name", v.trim()).apply()

    var ageGroup: AgeGroup
        get() = runCatching { AgeGroup.valueOf(sp.getString("age_group", "TEEN")!!) }.getOrDefault(AgeGroup.TEEN)
        set(v) = sp.edit().putString("age_group", v.name).apply()

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var sessionLength: Int
        get() = sp.getInt("session_length", 6)
        set(v) = sp.edit().putInt("session_length", v.coerceIn(2, 20)).apply()

    var rewardStyle: RewardStyle
        get() = runCatching { RewardStyle.valueOf(sp.getString("reward_style", "STARS")!!) }.getOrDefault(RewardStyle.STARS)
        set(v) = sp.edit().putString("reward_style", v.name).apply()

    var speechRate: Float
        get() = sp.getFloat("speech_rate", 0.85f)
        set(v) = sp.edit().putFloat("speech_rate", v).apply()

    fun isEnabled(type: ExerciseType): Boolean = sp.getBoolean("enabled_${type.name}", true)
    fun setEnabled(type: ExerciseType, on: Boolean) = sp.edit().putBoolean("enabled_${type.name}", on).apply()

    // ---- Adaptive difficulty -------------------------------------------------

    fun level(type: ExerciseType): Int = sp.getInt("level_${type.name}", 1)

    /**
     * Records one result and moves the level up after 4 of the last 5 are right,
     * or down after 3 of the last 4 are wrong. Returns the new level.
     */
    fun recordResult(type: ExerciseType, correct: Boolean): Int {
        val key = "history_${type.name}"
        val history = ((sp.getString(key, "") ?: "") + if (correct) "1" else "0").takeLast(5)
        var level = level(type)
        var newHistory = history
        val last4 = history.takeLast(4)
        if (history.length == 5 && history.count { it == '1' } >= 4 && level < ContentBank.MAX_LEVEL) {
            level++
            newHistory = ""
        } else if (last4.length == 4 && last4.count { it == '0' } >= 3 && level > 1) {
            level--
            newHistory = ""
        }
        sp.edit().putString(key, newHistory).putInt("level_${type.name}", level).apply()
        return level
    }

    // ---- Streak ----------------------------------------------------------------

    val streak: Int
        get() {
            val last = sp.getString("streak_last", null)?.let { LocalDate.parse(it) } ?: return 0
            val today = LocalDate.now()
            return if (last == today || last == today.minusDays(1)) sp.getInt("streak_count", 0) else 0
        }

    /** Call when a session is finished. */
    fun markPracticedToday() {
        val today = LocalDate.now()
        val last = sp.getString("streak_last", null)?.let { LocalDate.parse(it) }
        val count = when (last) {
            today -> sp.getInt("streak_count", 1)
            today.minusDays(1) -> sp.getInt("streak_count", 0) + 1
            else -> 1
        }
        sp.edit().putString("streak_last", today.toString()).putInt("streak_count", count).apply()
    }

    var totalStars: Int
        get() = sp.getInt("total_stars", 0)
        set(v) = sp.edit().putInt("total_stars", v).apply()

    fun resetProgress() {
        val e = sp.edit()
        ExerciseType.entries.forEach {
            e.remove("level_${it.name}")
            e.remove("history_${it.name}")
        }
        e.remove("streak_last").remove("streak_count").remove("total_stars").apply()
    }
}
