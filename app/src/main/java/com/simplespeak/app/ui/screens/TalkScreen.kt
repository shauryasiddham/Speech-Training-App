package com.simplespeak.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simplespeak.app.ai.ChatTurn
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.ai.ReportCard
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.Grade
import com.simplespeak.app.data.TalkContent
import com.simplespeak.app.data.TalkMode
import com.simplespeak.app.data.TextMatch
import com.simplespeak.app.speech.SpokenAnswer
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.CoachBadge
import com.simplespeak.app.ui.CoachOrb
import com.simplespeak.app.ui.GradientButton
import com.simplespeak.app.ui.HeroCard
import com.simplespeak.app.ui.HighlightedSentence
import com.simplespeak.app.ui.ListenButton
import com.simplespeak.app.ui.LocalExtraColors
import com.simplespeak.app.ui.OrbState
import com.simplespeak.app.ui.Screen
import com.simplespeak.app.ui.SectionLabel
import com.simplespeak.app.ui.SoftCard
import com.simplespeak.app.ui.SpeakInput
import com.simplespeak.app.ui.Thinking
import kotlinx.coroutines.launch

internal enum class LineKind {
    /** An ordinary line of the conversation. */
    NORMAL,
    /** The coach teaching the correct way to say his last sentence. */
    TEACH,
    /** His attempt at saying the improved sentence. */
    REPEAT,
}

/** One line of the conversation. For his lines, [turn] holds the grades once they arrive. */
internal data class ChatLine(
    val fromCoach: Boolean,
    val text: String,
    val turn: ChatTurn? = null,
    val kind: LineKind = LineKind.NORMAL,
    val repeatOk: Boolean? = null,
    val target: String? = null,
)

/**
 * Did he say the improved sentence? Every corrected word must be there (so the original mistake never passes),
 * and the rest must match closely. Speech recognition can mishear a word, so after a few tries it allows a little more.
 */
internal fun repeatMatches(attempt: String, target: String, original: String, tries: Int): Boolean {
    if (TextMatch.sameSentence(attempt, target)) return true
    val said = TextMatch.normalize(attempt).toSet()
    val corrected = TextMatch.highlightChanges(original, target).filter { it.second }.flatMap { TextMatch.normalize(it.first) }
    if (!corrected.all { it in said }) return false
    return TextMatch.similarity(attempt, target) >= if (tries >= 3) 0.8 else 0.9
}

@Composable
fun TalkScreen(vm: AppViewModel) {
    var mode by remember { mutableStateOf<TalkMode?>(null) }
    LaunchedEffect(Unit) { while (true) { vm.checkMac(); kotlinx.coroutines.delay(20_000) } }
    if (!vm.aiReady) {
        Page {
            Text("Talk with your coach", style = MaterialTheme.typography.headlineMedium)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CoachOrb(OrbState.Idle, size = 140.dp) }
            Text("The coach isn't available right now.", style = MaterialTheme.typography.bodyLarge)
            CoachSetupBanner(vm)
        }
        return
    }
    val m = mode
    if (m == null) Picker(vm) { mode = it } else ChatView(vm, m, onEnd = { mode = null })
}

// ---- Choosing what to talk about ------------------------------------------------------------

@Composable
private fun Picker(vm: AppViewModel, onPick: (TalkMode) -> Unit) {
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Talk with your coach", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        CoachBadge(vm)
        Text(
            "A real conversation. Every answer is graded, and the coach shows you a better way to say things when it helps.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Conversation")
        TalkContent.freeTopics(vm.prefs.interests).forEach { t -> PickRow(t.emoji, t.title, null) { onPick(t) } }

        SectionLabel("Real-life practice · the coach plays the other person")
        TalkContent.scenarios.groupBy { it.category }.forEach { (category, list) ->
            Text(category, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            list.forEach { s -> PickRow(s.emoji, s.title, s.goal) { onPick(s) } }
        }

        SectionLabel("Explain it · graded on order and clarity")
        TalkContent.explainTopics.forEach { e -> PickRow(e.emoji, e.title, null) { onPick(e) } }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PickRow(emoji: String, title: String, subtitle: String?, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ---- The conversation ------------------------------------------------------------------------

@Composable
private fun ChatView(vm: AppViewModel, mode: TalkMode, onEnd: () -> Unit) {
    val lines = remember { mutableStateListOf<ChatLine>() }
    var streaming by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<ReportCard?>(null) }
    var making by remember { mutableStateOf(false) }
    /** The correction he must say before the conversation continues. */
    var fix by remember { mutableStateOf<ChatTurn?>(null) }
    var fixTries by remember { mutableIntStateOf(0) }
    val chat = remember { runCatching { vm.coach.startChat(mode) }.getOrNull() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val start = remember { System.currentTimeMillis() }

    DisposableEffect(Unit) { onDispose { chat?.close() } }
    LaunchedEffect(Unit) {
        val opener = when (mode) {
            is TalkMode.Free -> mode.opener
            is TalkMode.Scenario -> mode.opener
            is TalkMode.Explain -> mode.opener
        }
        lines += ChatLine(true, opener)
        if (vm.prefs.speakReplies) vm.speaker.say(opener)
    }
    LaunchedEffect(lines.size, streaming) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.size) }

    fun finish() {
        val c = chat ?: return onEnd()
        if (c.turns.isEmpty()) return onEnd()
        making = true
        scope.launch {
            report = c.report()
            making = false
        }
    }

    fun send(ans: SpokenAnswer) {
        val c = chat ?: return
        val index = lines.size
        lines += ChatLine(false, ans.text)
        waiting = true; error = null; streaming = ""
        scope.launch {
            try {
                val turn = c.send(ans.text) { partial -> streaming = partial }
                lines[index] = lines[index].copy(turn = turn)
                if (!turn.correct && !turn.ended) {
                    // Teach first; the coach's reply waits until he has said the improved sentence.
                    fix = turn; fixTries = 0
                    lines += ChatLine(true, turn.improved, turn, LineKind.TEACH)
                    if (vm.prefs.speakReplies) vm.speaker.say(
                        "Let's say that correctly. ${turn.explanation} Please say: ${turn.improved}"
                    )
                } else {
                    lines += ChatLine(true, turn.reply)
                    if (vm.prefs.speakReplies) vm.speaker.say(turn.reply)
                }
                vm.recordAttempt(
                    ExerciseType.TALK, 0, mode.title,
                    Feedback(turn.correct, "", ans.text, turn.improved, turn.explanation, turn.mistakes),
                    ((System.currentTimeMillis() - start) / 1000).toInt().coerceAtMost(120), ans,
                )
                if (turn.ended) finish()
            } catch (e: CoachException) {
                error = e.message
            }
            streaming = null; waiting = false; round++
        }
    }

    /** His attempt at the improved sentence. The conversation continues only when it matches. */
    fun repeat(ans: SpokenAnswer) {
        val f = fix ?: return
        val ok = repeatMatches(ans.text, f.improved, f.said, fixTries)
        lines += ChatLine(false, ans.text, kind = LineKind.REPEAT, repeatOk = ok, target = f.improved)
        if (ok) {
            fix = null
            val reply = "Well said. ${f.reply}"
            lines += ChatLine(true, reply)
            if (vm.prefs.speakReplies) vm.speaker.say(reply)
        } else {
            fixTries++
            if (vm.prefs.speakReplies) vm.speaker.say("Not quite. Listen, then try again. ${f.improved}")
        }
        round++
    }

    report?.let { r -> ReportView(vm, mode, r, chat?.turns.orEmpty(), onNew = onEnd); return }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoachOrb(if (waiting || making) OrbState.Thinking else OrbState.Idle, size = 38.dp, reduceMotion = vm.prefs.reduceMotion)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.title, style = MaterialTheme.typography.titleSmall)
                CoachBadge(vm)
            }
            TextButton(onClick = { finish() }, enabled = !waiting && !making) { Text("End") }
        }
        if (mode is TalkMode.Scenario) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Flag, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Your goal: ${mode.goal}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(lines) { l ->
                when {
                    l.kind == LineKind.TEACH && l.turn != null -> TeachBubble(vm, l.turn)
                    l.kind == LineKind.REPEAT -> RepeatBubble(l)
                    l.fromCoach -> CoachBubble(vm, l.text)
                    else -> LearnerBubble(vm, l)
                }
            }
            item {
                streaming?.let { s -> if (s.isNotBlank()) CoachBubble(vm, s) else Thinking(vm, "Your coach is listening…") }
            }
        }
        Surface(tonalElevation = 2.dp, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    making -> Thinking(vm, "Writing your report card…")
                    waiting -> Text("Your coach is replying…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    chat == null -> Text("The coach could not start. Please try again.", style = MaterialTheme.typography.bodyLarge)
                    fix != null -> RepeatPanel(vm, fix!!, fixTries, round, ::repeat)
                    else -> {
                        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(6.dp)) }
                        key(round) { SpeakInput(vm, onAnswer = ::send, label = "Tap and answer", thinkFirst = true, saveAnswer = true) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoachBubble(vm: AppViewModel, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CoachOrb(OrbState.Idle, size = 30.dp, reduceMotion = true)
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = { vm.speaker.say(text) },
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

@Composable
internal fun LearnerBubble(vm: AppViewModel, line: ChatLine) {
    val turn = line.turn
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(line.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        if (turn != null) {
            Spacer(Modifier.height(6.dp))
            GradeRow(turn)
        }
    }
}

/** The four skills, each with a word grade. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeRow(turn: ChatTurn) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        turn.grades.all.forEach { (skill, grade) -> GradeChip(skill, grade) }
    }
}

@Composable
fun GradeChip(skill: String, grade: Grade) {
    val (bg, fg) = gradeColors(grade)
    Surface(shape = RoundedCornerShape(10.dp), color = bg) {
        Text("$skill · ${grade.label}", style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun gradeColors(grade: Grade): Pair<Color, Color> {
    val extra = LocalExtraColors.current
    val cs = MaterialTheme.colorScheme
    return when (grade) {
        Grade.EXCELLENT -> extra.successContainer to extra.onSuccessContainer
        Grade.VERY_GOOD -> cs.primaryContainer to cs.onPrimaryContainer
        Grade.GOOD -> cs.tertiaryContainer to cs.onTertiaryContainer
        Grade.NEEDS_WORK -> cs.surfaceVariant to cs.onSurfaceVariant
    }
}

/** The coach teaching how to say his sentence correctly. */
@Composable
internal fun TeachBubble(vm: AppViewModel, turn: ChatTurn) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CoachOrb(OrbState.Idle, size = 30.dp, reduceMotion = true)
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.widthIn(max = 310.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Let's say that correctly first.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (turn.explanation.isNotBlank()) {
                    Text(turn.explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                SectionLabel("You said")
                Text(turn.said, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionLabel("The correct way")
                HighlightedSentence(turn.said, turn.improved, MaterialTheme.typography.titleMedium)
                ListenButton(vm, turn.improved)
            }
        }
    }
}

/** His attempt at the improved sentence, marked right or not yet. */
@Composable
internal fun RepeatBubble(line: ChatLine) {
    val extra = LocalExtraColors.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            color = if (line.repeatOk == true) extra.successContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (line.repeatOk == true || line.target == null) {
                    Text(line.text, style = MaterialTheme.typography.bodyLarge, color = if (line.repeatOk == true) extra.onSuccessContainer else MaterialTheme.colorScheme.onSurface)
                } else {
                    // Highlight the words that still differ from the correct sentence.
                    HighlightedSentence(line.target, line.text, MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (line.repeatOk == true) {
                Icon(Icons.Rounded.CheckCircle, null, tint = extra.success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Said correctly", style = MaterialTheme.typography.labelMedium, color = extra.success)
            } else {
                Text("Not quite yet. Try again.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Shown instead of the normal microphone until he says the improved sentence. */
@Composable
private fun RepeatPanel(vm: AppViewModel, turn: ChatTurn, tries: Int, round: Int, onAnswer: (SpokenAnswer) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Say the correct sentence to continue", style = MaterialTheme.typography.titleSmall)
        }
        Text("“${turn.improved}”", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (tries >= 2) {
            Text("Take it slowly. Listen first, then say it one part at a time.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        key(round) { SpeakInput(vm, onAnswer = onAnswer, label = "Tap and say it", saveAnswer = false) }
    }
}

// ---- Report card ------------------------------------------------------------------------------

@Composable
internal fun ReportView(vm: AppViewModel, mode: TalkMode, r: ReportCard, turns: List<ChatTurn>, onNew: () -> Unit) {
    val extra = LocalExtraColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("REPORT CARD", style = MaterialTheme.typography.labelSmall, color = extra.heroText.copy(alpha = 0.8f))
                Text(mode.title, style = MaterialTheme.typography.titleMedium, color = extra.heroText)
                Text("Overall: ${r.grades.overall.label}", style = MaterialTheme.typography.headlineMedium, color = extra.heroText)
                Text("${turns.size} answers · ${turns.count { it.correct }} correct first time · ${turns.count { !it.correct }} corrected and practised", style = MaterialTheme.typography.bodyMedium, color = extra.heroText.copy(alpha = 0.9f))
            }
        }
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                r.grades.all.forEach { (skill, grade) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skill, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        val (bg, fg) = gradeColors(grade)
                        Surface(shape = RoundedCornerShape(10.dp), color = bg) {
                            Text(grade.label, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
        if (r.summary.isNotBlank()) SoftCard { Text(r.summary, style = MaterialTheme.typography.bodyLarge) }
        if (r.strengths.isNotEmpty()) SoftCard(color = extra.successContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("What you did well")
                r.strengths.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, color = extra.onSuccessContainer) }
            }
        }
        if (r.workOn.isNotEmpty()) SoftCard(color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Practise next")
                r.workOn.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
        if (r.practiceSentence.isNotBlank()) SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Say this sentence again")
                Text(r.practiceSentence, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                ListenButton(vm, r.practiceSentence)
            }
        }
        GradientButton("New conversation", onClick = onNew, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { vm.navigate(Screen.Home) }, modifier = Modifier.fillMaxWidth()) { Text("Back to home") }
    }
}
