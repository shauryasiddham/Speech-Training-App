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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
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
import com.simplespeak.app.data.ContentBank
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.TextMatch
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.BigButton
import com.simplespeak.app.ui.FeedbackCard
import com.simplespeak.app.ui.HighlightedSentence
import com.simplespeak.app.ui.ListenButton
import com.simplespeak.app.ui.LocalExtraColors
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
        ExerciseType.BUILD -> BuildExercise(vm, onNext)
        ExerciseType.FIX -> FixExercise(vm, onNext)
        ExerciseType.PICTURE -> PictureExercise(vm, onNext)
        ExerciseType.PLAN -> PlanExercise(vm, onNext)
    }
}

@Composable
private fun Instruction(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
}

private fun secondsSince(start: Long) = ((System.currentTimeMillis() - start) / 1000).toInt()

// ---------------------------------------------------------------------------------------------
// Build a sentence: put word tiles in order (works offline).
// ---------------------------------------------------------------------------------------------

private data class Tile(val id: Int, val word: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.BUILD) }
    val item = remember {
        val list = ContentBank.buildFor(level, vm.prefs.ageGroup).ifEmpty { ContentBank.buildItems }
        vm.pick(ExerciseType.BUILD, list) { it.sentence }
    }
    val words = remember { TextMatch.words(item.sentence) }
    val tiles = remember {
        val t = words.mapIndexed { i, w -> Tile(i, w) }
        var s = t.shuffled()
        var tries = 0
        while (s == t && t.size > 1 && tries++ < 10) s = t.shuffled()
        s
    }
    val placed = remember { mutableStateListOf<Tile>() }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var firstResult by remember { mutableStateOf<Boolean?>(null) }
    val start = remember { System.currentTimeMillis() }
    val punct = TextMatch.endPunctuation(item.sentence)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Instruction("Put the words in order to make a sentence.")
        Text(item.hint, fontSize = 64.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        // Answer area
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            if (placed.isEmpty()) {
                Text(
                    "Tap the words below in the right order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                placed.forEach { tile ->
                    WordTile(tile.word, onClick = { if (feedback == null) placed.remove(tile) }, placed = true)
                }
                if (placed.size == tiles.size) Text(punct, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            }
        }

        if (feedback == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.filter { it !in placed }.forEach { tile -> WordTile(tile.word, onClick = { placed.add(tile) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigButton("Start over", onClick = { placed.clear() }, tonal = true, enabled = placed.isNotEmpty(), modifier = Modifier.weight(1f))
                BigButton(
                    "Check",
                    enabled = placed.size == tiles.size,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val answer = placed.joinToString(" ") { it.word } + punct
                        val correct = TextMatch.sameSentence(answer, item.sentence)
                        val fb = Feedback(
                            correct = correct,
                            praise = if (correct) "Every word is in the right place." else "You used all of the words.",
                            learnerText = answer,
                            improved = item.sentence,
                            tip = if (correct) "" else "The highlighted words are in a different place. Listen to the sentence and try again.",
                            mistakes = if (correct) emptyList() else listOf("word_order"),
                        )
                        if (firstResult == null) {
                            firstResult = correct
                            vm.recordAttempt(ExerciseType.BUILD, level, item.sentence, fb, secondsSince(start))
                        }
                        feedback = fb
                    },
                )
            }
        } else {
            FeedbackCard(
                vm, feedback!!,
                onTryAgain = { placed.clear(); feedback = null },
                onNext = { onNext(firstResult == true) },
            )
            SayItOutLoud(vm, item.sentence)
        }
    }
}

/** Optional speaking practice after a sentence is shown. Checked on the phone, no AI needed. */
@Composable
private fun SayItOutLoud(vm: AppViewModel, target: String) {
    var result by remember(target) { mutableStateOf<String?>(null) }
    HorizontalDivider()
    Text("Now say it out loud", style = MaterialTheme.typography.titleLarge)
    SpeakInput(vm, allowTyping = false, label = "Tap and say the sentence", onText = { said ->
        val score = TextMatch.similarity(said, target)
        result = when {
            score >= 0.9 -> "That matches the sentence. Well done."
            score >= 0.6 -> "Very close. You said: \"$said\". Listen once more and try again."
            else -> "I heard: \"$said\". Listen to the sentence and try again."
        }
    })
    result?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

// ---------------------------------------------------------------------------------------------
// Fix the sentence: say a sentence with a mistake the right way.
// ---------------------------------------------------------------------------------------------

@Composable
private fun FixExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.FIX) }
    val item = remember {
        val list = ContentBank.fixFor(level, vm.prefs.ageGroup).ifEmpty { ContentBank.fixItems }
        vm.pick(ExerciseType.FIX, list) { it.broken }
    }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var checking by remember { mutableStateOf(false) }
    var firstResult by remember { mutableStateOf<Boolean?>(null) }
    var showHint by remember { mutableStateOf(false) }
    val start = remember { System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    fun localCheck(said: String, note: String?): Feedback {
        val correct = item.answers.any { TextMatch.sameSentence(it, said) }
        val best = item.answers.maxBy { TextMatch.similarity(it, said) }
        return Feedback(
            correct = correct,
            praise = if (correct) "You fixed the sentence." else "You kept the meaning of the sentence.",
            learnerText = said,
            improved = if (correct) said else best,
            tip = if (correct) "" else "Look at the highlighted words. Listen, then try saying it again.",
            mistakes = emptyList(),
            note = note,
        )
    }

    fun check(said: String) {
        checking = true
        scope.launch {
            val fb = if (vm.coach.isConfigured) {
                try {
                    vm.coach.checkFix(item.broken, item.answers, said, vm.prefs.ageGroup)
                } catch (e: CoachException) {
                    localCheck(said, "Checked on the phone. ${e.message}")
                }
            } else localCheck(said, null)
            if (firstResult == null) {
                firstResult = fb.correct
                vm.recordAttempt(ExerciseType.FIX, level, item.broken, fb, secondsSince(start))
            }
            feedback = fb
            checking = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Instruction("This sentence has a mistake. Say it the right way.")
        SoftCard(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column {
                if (showHint) {
                    // Highlight the words that are different from a correct version.
                    HighlightedSentence(item.answers.first(), item.broken)
                } else {
                    Text(item.broken, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        when {
            checking -> Thinking()
            feedback != null -> FeedbackCard(
                vm, feedback!!,
                onTryAgain = { feedback = null },
                onNext = { onNext(firstResult == true) },
            )
            else -> {
                if (!showHint) {
                    TextButton(onClick = { showHint = true }) {
                        Icon(Icons.Rounded.Lightbulb, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Show me where the mistake is")
                    }
                }
                SpeakInput(vm, onText = ::check, label = "Tap and say it the right way")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Describe a picture: built-in emoji scenes or the caregiver's own photos. Needs Claude.
// ---------------------------------------------------------------------------------------------

@Composable
private fun PictureExercise(vm: AppViewModel, onNext: (Boolean) -> Unit) {
    val level = remember { vm.prefs.level(ExerciseType.PICTURE) }
    val photos = remember { vm.store.photos() }
    val usePhoto = remember { photos.isNotEmpty() && Math.random() < 0.5 }
    val photo = remember { if (usePhoto) vm.pick(ExerciseType.PICTURE, photos) { it.id } else null }
    val scene = remember { if (photo == null) vm.pick(ExerciseType.PICTURE, ContentBank.scenesFor(level)) { it.id } else null }
    val bitmap = remember { photo?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }

    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var firstResult by remember { mutableStateOf<Boolean?>(null) }
    val start = remember { System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    fun check(said: String) {
        checking = true
        error = null
        scope.launch {
            try {
                val fb = vm.coach.checkPicture(
                    sceneDescription = scene?.description,
                    photo = photo?.let { File(it.path) },
                    caption = photo?.caption,
                    said = said,
                    age = vm.prefs.ageGroup,
                )
                if (firstResult == null) {
                    firstResult = fb.correct
                    vm.recordAttempt(ExerciseType.PICTURE, level, scene?.description ?: photo?.caption.orEmpty().ifBlank { "Photo" }, fb, secondsSince(start))
                }
                feedback = fb
            } catch (e: CoachException) {
                error = e.message
            }
            checking = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Instruction("Look at the picture. Tell me about it in a full sentence.")
        SoftCard(color = MaterialTheme.colorScheme.surfaceVariant) {
            if (bitmap != null) {
                Image(
                    bitmap, contentDescription = photo?.caption,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(scene?.emoji ?: "🖼️", fontSize = 72.sp, lineHeight = 90.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        if (!vm.coach.isConfigured) {
            Text(
                "Picture practice uses Claude to check your sentence. A caregiver can turn it on in the Caregiver tab.",
                style = MaterialTheme.typography.bodyLarge,
            )
            BigButton("Skip", onClick = { onNext(false) }, modifier = Modifier.fillMaxWidth())
            return@Column
        }
        when {
            checking -> Thinking()
            feedback != null -> FeedbackCard(
                vm, feedback!!,
                onTryAgain = { feedback = null },
                onNext = { onNext(firstResult == true) },
            )
            else -> {
                SpeakInput(vm, onText = ::check, label = "Tap and describe the picture")
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onNext(false) }) { Text("Skip this picture") }
                }
            }
        }
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
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var firstResult by remember { mutableStateOf<Boolean?>(null) }
    val start = remember { System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    fun localSentence(): String {
        val parts = listOf("Who", "Did what", "Where", "When").mapNotNull { values[it]?.trim()?.takeIf(String::isNotBlank) }
        val why = values["Why"]?.trim()?.takeIf(String::isNotBlank)
        val body = parts.joinToString(" ") + (if (why != null) " because $why" else "")
        return body.replaceFirstChar { it.uppercase() }.trimEnd('.') + "."
    }

    fun makeSentence() {
        making = true
        note = null
        scope.launch {
            sentence = if (vm.coach.isConfigured) {
                try {
                    vm.coach.sentenceFromPlan(values.toMap(), vm.prefs.ageGroup)
                } catch (e: CoachException) {
                    note = "Made on the phone. ${e.message}"
                    localSentence()
                }
            } else localSentence()
            making = false
            sentence?.let { vm.speaker.say(it) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Instruction("Plan a sentence about:")
        SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Text(topic, style = MaterialTheme.typography.titleLarge)
        }

        if (sentence == null) {
            Text("Answer each question with a few words.", style = MaterialTheme.typography.bodyLarge)
            planFields.forEach { f ->
                val isActive = active == f.key
                Surface(
                    onClick = { vm.speech.stop(); active = f.key },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.emoji, fontSize = 28.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.question + if (f.optional) "  (you can skip)" else "", style = MaterialTheme.typography.labelLarge)
                                val v = values[f.key]
                                Text(
                                    if (v.isNullOrBlank()) "Tap to answer" else v,
                                    style = MaterialTheme.typography.bodyLarge,
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
                            SpeakInput(vm, label = "Tap and answer", onText = { said ->
                                values[f.key] = said
                                active = planFields.getOrNull(planFields.indexOf(f) + 1)?.key ?: f.key
                            })
                        }
                    }
                }
            }
            if (making) Thinking("Making your sentence…")
            else BigButton(
                "Make my sentence",
                enabled = !values["Who"].isNullOrBlank() && !values["Did what"].isNullOrBlank(),
                onClick = ::makeSentence,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val s = sentence!!
            Text("Your sentence", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SoftCard(color = LocalExtraColors.current.successContainer) {
                Text(s, style = MaterialTheme.typography.headlineSmall)
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ListenButton(vm, s)
                TextButton(onClick = { sentence = null; feedback = null }) { Text("Change my answers") }
            }
            if (feedback == null) {
                HorizontalDivider()
                Text("Now say the whole sentence", style = MaterialTheme.typography.titleLarge)
                SpeakInput(vm, label = "Tap and say your sentence", onText = { said ->
                    val score = TextMatch.similarity(said, s)
                    val correct = score >= 0.8
                    val fb = Feedback(
                        correct = correct,
                        praise = if (correct) "You said the whole sentence clearly." else "You said many of the words.",
                        learnerText = said,
                        improved = s,
                        tip = if (correct) "" else "Listen to the sentence, then say it again slowly, one part at a time.",
                        mistakes = emptyList(),
                    )
                    if (firstResult == null) {
                        firstResult = correct
                        vm.recordAttempt(ExerciseType.PLAN, level, topic, fb, secondsSince(start))
                    }
                    feedback = fb
                })
            } else {
                FeedbackCard(vm, feedback!!, onTryAgain = { feedback = null }, onNext = { onNext(firstResult == true) })
            }
        }
    }
}
