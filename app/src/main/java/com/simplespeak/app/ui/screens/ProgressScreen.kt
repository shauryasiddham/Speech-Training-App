package com.simplespeak.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Stats
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.GoalRing
import com.simplespeak.app.ui.HeroCard
import com.simplespeak.app.ui.LocalExtraColors
import com.simplespeak.app.ui.MilestoneBadge
import com.simplespeak.app.ui.SectionLabel
import com.simplespeak.app.ui.SoftCard
import com.simplespeak.app.ui.StreakFlame
import com.simplespeak.app.ui.TrendRow
import com.simplespeak.app.ui.WeekStrip
import com.simplespeak.app.ui.streakMilestones
import kotlin.math.roundToInt

/** The learner's own progress page: streak, badges and how his speaking is growing. */
@Composable
fun ProgressScreen(vm: AppViewModel) {
    val version = vm.dataVersion
    val attempts = remember(version) { vm.store.attempts() }
    val now = remember(version) { Stats.week(attempts, 0) }
    val before = remember(version) { Stats.week(attempts, 1) }
    val streak = vm.prefs.streak
    val best = maxOf(vm.prefs.bestStreak, streak)
    val extra = LocalExtraColors.current

    Page {
        Text("Your progress", style = MaterialTheme.typography.headlineMedium)
        HeroCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreakFlame(streak, Modifier.weight(1f), color = Color.White, textColor = extra.heroText)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Best: $best", style = MaterialTheme.typography.titleMedium, color = extra.heroText)
                        Text("⭐ ${vm.prefs.totalStars} stars", style = MaterialTheme.typography.bodyMedium, color = extra.heroText.copy(alpha = 0.85f))
                    }
                }
                WeekStrip(remember(version) { vm.prefs.practicedDays }, onHero = true)
            }
        }

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(Stats.answersToday(attempts), vm.prefs.dailyGoal)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Today's goal", style = MaterialTheme.typography.titleMedium)
                    Text("Answer ${vm.prefs.dailyGoal} times each day to keep growing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionLabel("Streak badges")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            streakMilestones.forEach { MilestoneBadge(it, best >= it.days) }
        }

        SectionLabel("How your speaking is growing")
        SoftCard {
            Column {
                Text("This week", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                if (now.answers == 0) {
                    Text("Answer some questions to see your numbers.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    TrendRow("Words in each answer", now.wordsPerAnswer?.roundToInt()?.toString() ?: "–",
                        before.wordsPerAnswer?.roundToInt()?.toString(), compare(now.wordsPerAnswer, before.wordsPerAnswer, higherIsBetter = true))
                    TrendRow("Seconds before you start", now.startSeconds?.let { "%.1f".format(it) } ?: "–",
                        before.startSeconds?.let { "%.1f".format(it) }, compare(now.startSeconds, before.startSeconds, higherIsBetter = false))
                    TrendRow("Right first time", now.correctPct?.let { "$it%" } ?: "–",
                        before.correctPct?.let { "$it%" }, compare(now.correctPct?.toDouble(), before.correctPct?.toDouble(), higherIsBetter = true))
                    TrendRow("Answers", "${now.answers}", if (before.answers > 0) "${before.answers}" else null,
                        compare(now.answers.toDouble(), before.answers.toDouble().takeIf { it > 0 }, higherIsBetter = true))
                }
            }
        }

        SectionLabel("Your levels")
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExerciseType.practice.forEach { t ->
                    val level = vm.prefs.level(t)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${t.emoji}  ${t.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(6) { i ->
                                Box(Modifier.size(width = 16.dp, height = 8.dp).background(
                                    if (i < level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** true = better than last week, false = not better, null = can't compare. Small changes count as no change. */
private fun compare(now: Double?, before: Double?, higherIsBetter: Boolean): Boolean? {
    if (now == null || before == null || before == 0.0) return null
    val change = (now - before) / before
    if (kotlin.math.abs(change) < 0.05) return null
    return if (higherIsBetter) change > 0 else change < 0
}
