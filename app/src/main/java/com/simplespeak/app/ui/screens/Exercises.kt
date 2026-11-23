package com.simplespeak.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.ai.JoinTask
import com.simplespeak.app.ai.SequenceTask
import com.simplespeak.app.data.ContentBank
import com.simplespeak.app.data.ErrorInjector
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.TextMatch
import com.simplespeak.app.speech.SpokenAnswer
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.BigButton
import com.simplespeak.app.ui.FeedbackCard
import com.simplespeak.app.ui.GradientButton
import com.simplespeak.app.ui.HighlightedSentence
import com.simplespeak.app.ui.ListenButton
import com.simplespeak.app.ui.LocalExtraColors
import com.simplespeak.app.ui.SectionLabel
import com.simplespeak.app.ui.SoftCard
import com.simplespeak.app.ui.SpeakInput
import com.simplespeak.app.ui.Thinking
import com.simplespeak.app.ui.WordTile
import kotlinx.coroutines.launch
import java.io.File

/** Shows one exercise of the given type. [onNext] gets whether the first answer was right. */
@Composable
fun Exercise(vm: AppViewModel, type: ExerciseType, onNext: (Boolean) -> Unit) {
    when (type) {
        ExerciseType.ANSWER -> AnswerExercise(vm, onNext)
        ExerciseType.FIX -> FixExercise(vm, onNext)
        ExerciseType.JOIN -> JoinExercise(vm, onNext)
        ExerciseType.SEQUENCE -> SequenceExercise(vm, onNext)
        ExerciseType.PICTURE -> PictureExercise(vm, onNext)
        ExerciseType.PLAN -> PlanExercise(vm, onNext)
        ExerciseType.BUILD -> BuildExercise(vm, onNext)
        ExerciseType.TALK -> AnswerExercise(vm, onNext)
    }
}

@Composable
private fun Instruction(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun LevelTag(level: Int) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text("Level $level", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

private fun secondsSince(start: Long) = ((System.currentTimeMillis() - start) / 1000).toInt()

/** Loads an exercise item (which may be written by the AI) and shows a calm placeholder meanwhile. */
@Composable
private fun <T : Any> Loaded(vm: AppViewModel, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var item by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(Unit) { item = load() }
    val it = item
    if (it == null) Thinking(vm, "Preparing your exercise…") else content(it)
}

/**
 * The shared flow for spoken answers: think → speak → coach feedback → try again or next.
 * [localCheck] is used when the coach is not available (or fails); return null if only the coach can judge.
 */
@Composable
private fun SpokenAnswerFlow(
    vm: AppViewModel,
    type: ExerciseType,
    level: Int,
    record: String,
    coachTask: String,
    references: List<String>,
    localCheck: ((String) -> Feedback)?,
    onNext: (Boolean) -> Unit,
    image: File? = null,
    label: String = "Tap and speak",
) {
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var firstResult by remember { mutableStateOf<Boolean?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val start = remember { System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    fun check(ans: SpokenAnswer) {
        checking = true
        error = null
        scope.launch {
            val fb: Feedback? = if (vm.aiReady) {
                try {
                    try {
                        vm.coach.feedback(coachTask, ans.text, references, image)
                    } catch (e: CoachException) {
                        if (image != null) vm.coach.feedback(coachTask, ans.text, references, null) else throw e
                    }
                } catch (e: CoachException) {
                    localCheck?.invoke(ans.text)?.copy(note = "Checked on the phone without the coach. ${e.message}") ?: run { error = e.message; null }
                }
            } else localCheck?.invoke(ans.text) ?: run { error = "This exercise needs the speaking coach. A caregiver can set it up in the Caregiver tab."; null }
            if (fb != null) {
                if (firstResult == null) {
                    firstResult = fb.correct
                    vm.recordAttempt(type, level, record, fb, secondsSince(start), ans)
                }
                feedback = fb
            }
            checking = false
        }
    }

    when {
        checking -> Thinking(vm)
        feedback != null -> FeedbackCard(vm, feedback!!, onTryAgain = { feedback = null; round++ }, onNext = { onNext(firstResult == true) })
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            key(round) {
                SpeakInput(vm, onAnswer = ::check, label = label, thinkFirst = round == 0, saveAnswer = true)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = { onNext(false) }) { Text("Skip this one") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Answer the question: real-time planning. Think, then speak a full answer.
// ---------------------------------------------------------------------------------------------

@Composable
private fun AnswerExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.ANSWER) }
    Loaded(vm, load = { vm.nextQuestion(level) ?: vm.pick(ExerciseType.ANSWER, ContentBank.questionsFor(level)) { it } }) { question ->
        LaunchedEffect(question) { vm.speaker.say(question) }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Instruction("Answer in full sentences.")
                Spacer(Modifier.weight(1f)); LevelTag(level)
            }
            SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(question, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    ListenButton(vm, question, "Hear it again")
                }
            }
            Text(
                if (level >= 4) "Give a reason or an example. Try to use two or three sentences." else "Give a little detail: who, what, where or why.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpokenAnswerFlow(
                vm, ExerciseType.ANSWER, level, question,
                coachTask = "He was asked a question and answered out loud.\nQuestion: \"$question\"",
                references = emptyList(), localCheck = null, onNext = onNext, label = "Tap and answer",
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fix the sentence: AI writes a correct sentence at his level, the app adds realistic mistakes.
// ---------------------------------------------------------------------------------------------

private data class FixTask(val broken: String, val answers: List<String>)

@Composable
private fun FixExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.FIX) }
    Loaded(vm, load = {
        val ai = vm.nextSentence(level)?.let { s -> ErrorInjector.breakSentence(s, if (level >= 4) 2 else 1)?.let { FixTask(it.text, listOf(s)) } }
        ai ?: vm.pick(ExerciseType.FIX, ContentBank.fixFor(level, vm.prefs.ageGroup).ifEmpty { ContentBank.fixItems }) { it.broken }.let { FixTask(it.broken, it.answers) }
    }) { item ->
        var showHint by remember { mutableStateOf(false) }
        fun localCheck(said: String): Feedback {
            val correct = item.answers.any { TextMatch.sameSentence(it, said) }
            return Feedback(
                correct = correct,
                praise = if (correct) "You fixed the sentence." else "You kept the meaning of the sentence.",
                learnerText = said,
                improved = if (correct) said else item.answers.maxBy { TextMatch.similarity(it, said) },
                tip = if (correct) "" else "Look at the highlighted words. Listen, then say it again.",
                mistakes = emptyList(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Instruction("Say it the right way.")
                Spacer(Modifier.weight(1f)); LevelTag(level)
            }
            SoftCard(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("This sentence has a mistake")
                    if (showHint) HighlightedSentence(item.answers.first(), item.broken) else Text(item.broken, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (!showHint) {
                TextButton(onClick = { showHint = true }) {
                    Icon(Icons.Rounded.Lightbulb, null); Spacer(Modifier.width(6.dp)); Text("Show where the mistake is")
                }
            }
            SpokenAnswerFlow(
                vm, ExerciseType.FIX, level, item.broken,
                coachTask = "He was shown a sentence with grammar mistakes and asked to say it correctly.\nSentence with mistakes: \"${item.broken}\"",
                references = item.answers, localCheck = ::localCheck, onNext = onNext, label = "Tap and say it correctly",
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Join the ideas: say short sentences as one linked sentence.
// ---------------------------------------------------------------------------------------------

@Composable
private fun JoinExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.JOIN) }
    Loaded(vm, load = {
        vm.nextJoin(level) ?: vm.pick(ExerciseType.JOIN, ContentBank.joinFor(level)) { it.parts.first() }.let { JoinTask(it.parts, it.answers.first()) }
    }) { item ->
        val answers = remember(item) { listOf(item.joined) + ContentBank.joinItems.firstOrNull { it.parts == item.parts }?.answers.orEmpty() }
        fun localCheck(said: String): Feedback {
            val best = answers.maxBy { TextMatch.similarity(it, said) }
            val correct = TextMatch.similarity(best, said) >= 0.9
            return Feedback(
                correct = correct,
                praise = if (correct) "You joined the ideas into one sentence." else "You used the ideas from the sentences.",
                learnerText = said, improved = if (correct) said else best,
                tip = if (correct) "" else "Use one joining word, like because, so, but or although, instead of stopping between ideas.",
                mistakes = emptyList(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Instruction("Say these as ONE sentence.")
                Spacer(Modifier.weight(1f)); LevelTag(level)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item.parts.forEachIndexed { i, part ->
                    SoftCard { Text(part, style = MaterialTheme.typography.titleMedium) }
                    if (i < item.parts.lastIndex) Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Text("Joining words: because · so · but · although · while · when · which", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SpokenAnswerFlow(
                vm, ExerciseType.JOIN, level, item.parts.joinToString(" "),
                coachTask = "He was asked to join these sentences into ONE natural sentence using linking words:\n" +
                    item.parts.joinToString("\n") { "- $it" } + "\nIf he did not join them into one sentence, \"improved\" should join them.",
                references = answers, localCheck = ::localCheck, onNext = onNext, label = "Tap and say one sentence",
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tell it in order: put steps in order, then tell them as a story with first / then / finally.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SequenceExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.SEQUENCE) }
    Loaded(vm, load = {
        vm.nextSequence() ?: vm.pick(ExerciseType.SEQUENCE, ContentBank.sequences) { it.title }.let { SequenceTask(it.title, it.steps) }
    }) { item ->
        val shuffled = remember(item) { item.steps.indices.shuffled().let { if (it == item.steps.indices.toList()) it.reversed() else it } }
        val chosen = remember { mutableStateListOf<Int>() }
        var orderChecked by remember { mutableStateOf<Boolean?>(null) }
        val model = remember(item) {
            val words = listOf("First", "Then", "After that", "Finally")
            item.steps.mapIndexed { i, s -> "${words.getOrElse(i) { "Then" }}, ${s.second.replaceFirstChar { it.lowercase() }.trimEnd('.')}." }.joinToString(" ")
        }
        fun localCheck(said: String): Feedback {
            val orderWords = listOf("first", "then", "next", "after", "finally", "last")
            val used = orderWords.count { said.lowercase().contains(it) }
            val correct = used >= 2 && TextMatch.similarity(said, model) >= 0.5 && orderChecked == true
            return Feedback(
                correct = correct,
                praise = if (used >= 2) "You used order words to guide the listener." else "You told the steps of the story.",
                learnerText = said, improved = model,
                tip = if (correct) "" else "Start each step with an order word: first, then, after that, finally.",
                mistakes = emptyList(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Instruction(item.title)
                Spacer(Modifier.weight(1f)); LevelTag(level)
            }
            if (orderChecked == null) {
                Text("Tap the steps in the order they happen.", style = MaterialTheme.typography.bodyLarge)
                shuffled.forEach { idx ->
                    val pos = chosen.indexOf(idx)
                    Surface(
                        onClick = { if (pos >= 0) chosen.remove(idx) else chosen.add(idx) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (pos >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shadowElevation = if (pos >= 0) 0.dp else 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (pos >= 0) "${pos + 1}" else "", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.width(12.dp))
                            Text(item.steps[idx].first, fontSize = 28.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(item.steps[idx].second, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                GradientButton(
                    "Check the order", enabled = chosen.size == item.steps.size, modifier = Modifier.fillMaxWidth(),
                    onClick = { orderChecked = chosen.toList() == item.steps.indices.toList() },
                )
            } else {
                SoftCard(color = if (orderChecked == true) LocalExtraColors.current.successContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (orderChecked == true) "The order is right." else "Here is the right order.", style = MaterialTheme.typography.titleMedium)
                        item.steps.forEachIndexed { i, s -> Text("${i + 1}.  ${s.first}  ${s.second}", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                Text("Now tell it as a story. Use first, then, after that and finally.", style = MaterialTheme.typography.titleMedium)
                SpokenAnswerFlow(
                    vm, ExerciseType.SEQUENCE, level, item.title,
                    coachTask = "He told a short story out loud, in time order, from these steps:\n" +
                        item.steps.mapIndexed { i, s -> "${i + 1}. ${s.second}" }.joinToString("\n") +
                        "\nCheck his grammar and whether he used order words like first, then, after that and finally.",
                    references = emptyList(), localCheck = ::localCheck,
                    onNext = { ok -> onNext(ok && orderChecked == true) }, label = "Tap and tell the story",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Describe a picture: built-in scenes or the caregiver's photos (the coach can look at photos).
// ---------------------------------------------------------------------------------------------

@Composable
private fun PictureExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.PICTURE) }
    val photos = remember { vm.store.photos() }
    val photo = remember { if (photos.isNotEmpty() && Math.random() < 0.6) vm.pick(ExerciseType.PICTURE, photos) { it.id } else null }
    val scene = remember { if (photo == null) vm.pick(ExerciseType.PICTURE, ContentBank.scenesFor(level.coerceAtMost(3))) { it.id } else null }
    val bitmap = remember { photo?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Instruction("Describe the picture.")
            Spacer(Modifier.weight(1f)); LevelTag(level)
        }
        SoftCard(color = MaterialTheme.colorScheme.surfaceVariant) {
            if (bitmap != null) {
                Image(bitmap, photo?.caption, Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
            } else {
                Text(scene?.emoji ?: "🖼️", fontSize = 72.sp, lineHeight = 90.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        Text(
            if (level >= 4) "Say what is happening, and what might happen next." else "Say who is there, what they are doing and where.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SpokenAnswerFlow(
            vm, ExerciseType.PICTURE, level, scene?.description ?: photo?.caption.orEmpty().ifBlank { "Photo" },
            coachTask = buildString {
                append("He looked at a picture and described it out loud.\n")
                scene?.let { append("The picture shows: \"${it.description}\"\n") }
                photo?.let { append("The picture is attached." + if (it.caption.isNotBlank()) " A note about it: \"${it.caption}\"\n" else "\n") }
                append("Small details do not need to match. Judge his grammar and whether his sentences are clear and fit the picture.")
            },
            references = emptyList(), localCheck = null, onNext = onNext,
            image = photo?.let { File(it.path) }, label = "Tap and describe it",
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Plan my sentence: Who / Did what / Where / When / Why, then say the full sentence.
// ---------------------------------------------------------------------------------------------

private data class PlanField(val key: String, val question: String, val emoji: String, val optional: Boolean, val quick: List<String>)

private val planFields = listOf(
    PlanField("Who", "Who?", "🧑", false, listOf("I", "We", "My family")),
    PlanField("Did what", "Did what? / Does what?", "🏃", false, emptyList()),
    PlanField("Where", "Where?", "📍", true, listOf("At home", "At school", "Outside")),
    PlanField("When", "When?", "🕒", true, listOf("Today", "Yesterday", "Tomorrow")),
    PlanField("Why", "Why?", "❓", true, emptyList()),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.PLAN) }
    val topic = remember { vm.pick(ExerciseType.PLAN, ContentBank.planTopics) { it } }
    val values = remember { mutableStateMapOf<String, String>() }
    var active by remember { mutableStateOf("Who") }
    var sentence by remember { mutableStateOf<String?>(null) }
    var making by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun localSentence(): String {
        val parts = listOf("Who", "Did what", "Where", "When").mapNotNull { values[it]?.trim()?.takeIf(String::isNotBlank) }
        val why = values["Why"]?.trim()?.takeIf(String::isNotBlank)
        return (parts.joinToString(" ") + (if (why != null) " because $why" else "")).replaceFirstChar { it.uppercase() }.trimEnd('.') + "."
    }

    fun makeSentence() {
        making = true; note = null
        scope.launch {
            sentence = if (vm.aiReady) {
                try { vm.coach.sentenceFromPlan(values.toMap()) } catch (e: CoachException) { note = "Made on the phone. ${e.message}"; localSentence() }
            } else localSentence()
            making = false
            sentence?.let { vm.speaker.say(it) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Instruction("Plan a sentence about:")
        SoftCard(color = MaterialTheme.colorScheme.primaryContainer) { Text(topic, style = MaterialTheme.typography.titleLarge) }

        if (sentence == null) {
            planFields.forEach { f ->
                val isActive = active == f.key
                Surface(
                    onClick = { vm.speech.stop(); active = f.key },
                    shape = RoundedCornerShape(22.dp),
                    color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    shadowElevation = if (isActive) 0.dp else 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.emoji, fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.question + if (f.optional) "  (optional)" else "", style = MaterialTheme.typography.titleSmall)
                                val v = values[f.key]
                                Text(
                                    if (v.isNullOrBlank()) "Tap to answer" else v, style = MaterialTheme.typography.bodyLarge,
                                    color = if (v.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (!values[f.key].isNullOrBlank()) Icon(Icons.Rounded.Edit, contentDescription = "Change")
                        }
                        if (isActive) {
                            if (f.quick.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    f.quick.forEach { q ->
                                        AssistChip(onClick = {
                                            values[f.key] = q
                                            active = planFields.getOrNull(planFields.indexOf(f) + 1)?.key ?: f.key
                                        }, label = { Text(q, style = MaterialTheme.typography.bodyMedium) })
                                    }
                                }
                            }
                            key(f.key) {
                                SpeakInput(vm, label = "Tap and answer", onAnswer = { ans ->
                                    values[f.key] = ans.text
                                    active = planFields.getOrNull(planFields.indexOf(f) + 1)?.key ?: f.key
                                })
                            }
                        }
                    }
                }
            }
            if (making) Thinking(vm, "Making your sentence…")
            else GradientButton(
                "Make my sentence", enabled = !values["Who"].isNullOrBlank() && !values["Did what"].isNullOrBlank(),
                onClick = ::makeSentence, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val s = sentence!!
            SectionLabel("Your sentence")
            SoftCard(color = LocalExtraColors.current.successContainer) { Text(s, style = MaterialTheme.typography.headlineSmall) }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ListenButton(vm, s)
                TextButton(onClick = { sentence = null }) { Text("Change my answers") }
            }
            HorizontalDivider()
            Text("Now say the whole sentence", style = MaterialTheme.typography.titleLarge)
            SpokenAnswerFlow(
                vm, ExerciseType.PLAN, level, topic,
                coachTask = "He planned this sentence and then said it out loud from memory: \"$s\"",
                references = listOf(s),
                localCheck = { said ->
                    val ok = TextMatch.similarity(said, s) >= 0.8
                    Feedback(ok, if (ok) "You said the whole sentence clearly." else "You said many of the words.", said, s,
                        if (ok) "" else "Listen to the sentence, then say it again slowly, one part at a time.", emptyList())
                },
                onNext = onNext, label = "Tap and say your sentence",
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Build a sentence: word tiles (a warm-up). Higher levels use AI-written sentences.
// ---------------------------------------------------------------------------------------------

private data class Tile(val id: Int, val word: String)
private data class BuildTask(val sentence: String, val hint: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.BUILD) }
    Loaded(vm, load = {
        vm.nextSentence(level)?.let { BuildTask(it, "🧩") }
            ?: vm.pick(ExerciseType.BUILD, ContentBank.buildFor(level, vm.prefs.ageGroup).ifEmpty { ContentBank.buildItems }) { it.sentence }.let { BuildTask(it.sentence, it.hint) }
    }) { item ->
        val words = remember(item) { TextMatch.words(item.sentence) }
        val tiles = remember(item) {
            val t = words.mapIndexed { i, w -> Tile(i, w) }
            var s = t.shuffled(); var tries = 0
            while (s == t && t.size > 1 && tries++ < 10) s = t.shuffled()
            s
        }
        val placed = remember { mutableStateListOf<Tile>() }
        var feedback by remember { mutableStateOf<Feedback?>(null) }
        var firstResult by remember { mutableStateOf<Boolean?>(null) }
        val start = remember { System.currentTimeMillis() }
        val punct = TextMatch.endPunctuation(item.sentence)

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Instruction("Put the words in order.")
                Spacer(Modifier.weight(1f)); LevelTag(level)
            }
            Text(item.hint, fontSize = 56.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 90.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)).padding(12.dp)
            ) {
                if (placed.isEmpty()) {
                    Text("Tap the words below in the right order.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    placed.forEach { tile -> WordTile(tile.word, onClick = { if (feedback == null) placed.remove(tile) }, placed = true) }
                    if (placed.size == tiles.size) Text(punct, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                }
            }
            if (feedback == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.filter { it !in placed }.forEach { tile -> WordTile(tile.word, onClick = { placed.add(tile) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton("Start over", onClick = { placed.clear() }, tonal = true, enabled = placed.isNotEmpty(), modifier = Modifier.weight(1f))
                    GradientButton("Check", enabled = placed.size == tiles.size, modifier = Modifier.weight(1f), onClick = {
                        val answer = placed.joinToString(" ") { it.word } + punct
                        val correct = TextMatch.sameSentence(answer, item.sentence)
                        val fb = Feedback(
                            correct, if (correct) "Every word is in the right place." else "You used all of the words.", answer, item.sentence,
                            if (correct) "" else "The highlighted words are in a different place. Listen, then try again.",
                            if (correct) emptyList() else listOf("word_order"),
                        )
                        if (firstResult == null) { firstResult = correct; vm.recordAttempt(ExerciseType.BUILD, level, item.sentence, fb, secondsSince(start), null) }
                        feedback = fb
                    })
                }
            } else {
                FeedbackCard(vm, feedback!!, onTryAgain = { placed.clear(); feedback = null }, onNext = { onNext(firstResult == true) })
                SayItOutLoud(vm, item.sentence)
            }
        }
    }
}

/** Speaking practice after a tile puzzle. Checked on the phone. */
@Composable
private fun SayItOutLoud(vm: AppViewModel, target: String) {
    var result by remember(target) { mutableStateOf<String?>(null) }
    HorizontalDivider()
    Text("Now say it out loud", style = MaterialTheme.typography.titleLarge)
    SpeakInput(vm, label = "Tap and say the sentence", onAnswer = { ans ->
        val score = TextMatch.similarity(ans.text, target)
        result = when {
            score >= 0.9 -> "That matches the sentence. Well done."
            score >= 0.6 -> "Very close. You said: “${ans.text}”. Listen once more and try again."
            else -> "I heard: “${ans.text}”. Listen to the sentence and try again."
        }
    })
    result?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
}
