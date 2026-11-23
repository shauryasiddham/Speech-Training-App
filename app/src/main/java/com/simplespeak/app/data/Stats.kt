package com.simplespeak.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Week-by-week numbers used for the progress screens and to tell the coach how he is improving. */
data class WeekStats(
    val answers: Int,
    val correctPct: Int?,
    val wordsPerAnswer: Double?,
    val startSeconds: Double?,
    val wordsPerMinute: Int?,
    val minutes: Int,
    val mistakes: Map<String, Int>,
)

object Stats {
    fun Attempt.day(): LocalDate = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()

    fun week(attempts: List<Attempt>, weeksAgo: Int = 0): WeekStats {
        val today = LocalDate.now()
        val end = today.minusDays(7L * weeksAgo)
        val start = end.minusDays(6)
        val list = attempts.filter { val d = it.day(); !d.isBefore(start) && !d.isAfter(end) }
        val spoken = list.mapNotNull { it.fluency }.filter { it.words > 0 }
        return WeekStats(
            answers = list.size,
            correctPct = if (list.isEmpty()) null else (100.0 * list.count { it.correct } / list.size).roundToInt(),
            wordsPerAnswer = spoken.takeIf { it.isNotEmpty() }?.map { it.words }?.average(),
            startSeconds = spoken.mapNotNull { it.startMs }.takeIf { it.isNotEmpty() }?.average()?.div(1000.0),
            wordsPerMinute = spoken.mapNotNull { it.wordsPerMinute }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            minutes = (list.sumOf { it.seconds } / 60.0).roundToInt(),
            mistakes = list.flatMap { it.mistakes }.groupingBy { it }.eachCount(),
        )
    }

    fun answersToday(attempts: List<Attempt>): Int {
        val today = LocalDate.now()
        return attempts.count { it.day() == today }
    }

    /**
     * Plain-English notes about recent progress, given to the coach so its praise is specific and true.
     * Only states improvements that the numbers actually show.
     */
    fun progressNotes(attempts: List<Attempt>, streak: Int): String {
        val now = week(attempts, 0)
        val before = week(attempts, 1)
        val notes = mutableListOf<String>()
        if (streak >= 2) notes += "He has practised $streak days in a row."
        val wa = now.wordsPerAnswer
        val wb = before.wordsPerAnswer
        if (wa != null && wb != null && wa > wb * 1.1) notes += "His answers are longer this week (about ${wa.roundToInt()} words, up from ${wb.roundToInt()})."
        val sa = now.startSeconds
        val sb = before.startSeconds
        if (sa != null && sb != null && sa < sb * 0.9) notes += "He starts speaking faster this week (%.1f s, down from %.1f s).".format(sa, sb)
        val ca = now.correctPct
        val cb = before.correctPct
        if (ca != null && cb != null && ca >= cb + 5) notes += "More of his sentences are correct this week ($ca%, up from $cb%)."
        before.mistakes.entries.sortedByDescending { it.value }.take(3).forEach { (k, v) ->
            val n = now.mistakes[k] ?: 0
            if (v >= 3 && n <= v / 2) notes += "He makes fewer ${MistakeTypes.label(k).lowercase()} mistakes this week ($n, down from $v)."
        }
        now.mistakes.entries.maxByOrNull { it.value }?.let { (k, v) ->
            if (v >= 3) notes += "His most common mistake this week is ${MistakeTypes.label(k).lowercase()}."
        }
        return notes.joinToString(" ")
    }
}
