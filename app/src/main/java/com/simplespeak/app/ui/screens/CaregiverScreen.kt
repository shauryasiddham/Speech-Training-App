package com.simplespeak.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.ai.LocalModel
import com.simplespeak.app.data.AiEngine
import com.simplespeak.app.data.Attempt
import com.simplespeak.app.data.ContentBank
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.MistakeTypes
import com.simplespeak.app.data.Stats
import com.simplespeak.app.data.Stats.day
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.BigButton
import com.simplespeak.app.ui.GradientButton
import com.simplespeak.app.ui.SectionLabel
import com.simplespeak.app.ui.SoftCard
import com.simplespeak.app.ui.TrendRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun CaregiverScreen(vm: AppViewModel) {
    val version = vm.dataVersion
    val attempts = remember(version) { vm.store.attempts() }
    Page {
        Text("Caregiver", style = MaterialTheme.typography.headlineMedium)
        Text("Progress and settings. Everything is stored only on this phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CoachSection(vm)
        SectionTitle("Progress")
        ProgressSection(vm, attempts)
        RecordingsSection(vm)
        SectionTitle("Learner")
        LearnerSection(vm)
        SectionTitle("Practice settings")
        PracticeSettings(vm)
        SectionTitle("Photos for “Describe a picture”")
        PhotosSection(vm)
        SectionTitle("Reset")
        ResetSection(vm)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(text, style = MaterialTheme.typography.titleLarge)
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private fun pct(part: Int, whole: Int) = if (whole == 0) "–" else "${(100.0 * part / whole).roundToInt()}%"

// ---- Speaking coach ------------------------------------------------------------------------

@Composable
private fun CoachSection(vm: AppViewModel) {
    val model = vm.localModel
    val st = model.status
    var engine by remember { mutableStateOf(vm.prefs.engine) }
    var wifiOnly by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var backend by remember { mutableStateOf(vm.prefs.modelBackend) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Speaking coach", style = MaterialTheme.typography.titleLarge)
            AiEngine.entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().selectable(engine == e, role = Role.RadioButton) { engine = e; vm.prefs.engine = e; vm.refresh() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(engine == e, null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(e.label, style = MaterialTheme.typography.titleSmall)
                        Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (engine == AiEngine.MAC) {
                HorizontalDivider()
                MacSettings(vm) { message = it }
            } else if (engine == AiEngine.LOCAL) {
                HorizontalDivider()
                Text("Model: Gemma 4 E2B by Google (2.6 GB). Runs fully on the phone, works offline once downloaded.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (st) {
                    is LocalModel.Status.Missing, is LocalModel.Status.Error -> {
                        if (st is LocalModel.Status.Error) Text(st.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(wifiOnly, { wifiOnly = it })
                        }
                        GradientButton("Download the coach", onClick = { message = vm.downloadModel(wifiOnly) }, modifier = Modifier.fillMaxWidth())
                    }
                    is LocalModel.Status.Downloading -> {
                        LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
                        Text("${(st.fraction * 100).roundToInt()}% · ${st.note}", style = MaterialTheme.typography.bodyMedium)
                        Text("You can leave the app; the download continues in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { model.cancelDownload(); vm.refresh() }) { Text("Cancel download") }
                    }
                    else -> {
                        Text(
                            when (st) {
                                is LocalModel.Status.Ready -> "Ready · running on the ${st.backend}"
                                LocalModel.Status.Loading -> "Starting up…"
                                else -> "Downloaded · starts when first used"
                            },
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        )
                        model.lastSpeed?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        SectionLabel("Processor")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto" to "Automatic", "gpu" to "GPU", "cpu" to "CPU").forEach { (k, label) ->
                                FilterChip(backend == k, onClick = {
                                    backend = k; vm.prefs.modelBackend = k; vm.prefs.gpuFailed = false
                                    scope.launch { model.unload() }
                                }, label = { Text(label) })
                            }
                        }
                        Text("Automatic tries the faster GPU first and switches to the CPU if this phone doesn't support it.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BigButton("Test the coach", tonal = true, enabled = !testing, onClick = {
                                testing = true; message = "Testing… the first start can take up to a minute."
                                scope.launch {
                                    message = try { vm.coach.testConnection(); "It works. ${model.lastSpeed ?: ""}" } catch (e: CoachException) { e.message }
                                    testing = false
                                }
                            })
                            TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                        }
                    }
                }
            } else {
                ClaudeKey(vm) { message = it }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the coach?") },
            text = { Text("This frees 2.6 GB. Conversations and smart feedback stop working until it is downloaded again. Progress is kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; model.deleteModel(); vm.refresh() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MacSettings(vm: AppViewModel, onMessage: (String?) -> Unit) {
    var url by remember { mutableStateOf(vm.prefs.macUrl) }
    var model by remember { mutableStateOf(vm.prefs.macModel) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf(vm.prefs.fallbackToClaude) }
    val scope = rememberCoroutineScope()
    val reachable = vm.macReachable

    fun loadModels() = scope.launch {
        models = vm.mac.installedModels()
        if (models.isNotEmpty() && model !in models) { model = models.first(); vm.prefs.macModel = model }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.checkMac(); if (vm.mac.isConfigured) loadModels() }

    Text(
        when (reachable) {
            true -> "● Connected to the Mac"
            false -> "● Can't reach the Mac" + if (vm.prefs.fallbackToClaude && vm.prefs.apiKey.isNotBlank()) " · using Claude for now" else ""
            null -> if (url.isBlank()) "Not set up yet" else "Checking…"
        },
        style = MaterialTheme.typography.titleSmall,
        color = when (reachable) { true -> com.simplespeak.app.ui.LocalExtraColors.current.success; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant },
    )
    Text("Set up the Mac first with the guide in the project (mac-setup/README.md). It prints the address to type here.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        url, { url = it }, label = { Text("Mac address") }, placeholder = { Text("e.g. 192.168.1.20") }, singleLine = true,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigButton("Find my Mac", tonal = true, enabled = !busy, onClick = {
            busy = true; onMessage("Looking for the Mac on this Wi-Fi…")
            scope.launch {
                val found = vm.mac.findOnNetwork()
                if (found != null) { url = found; vm.prefs.macUrl = found; vm.refresh(); vm.checkMac(); loadModels(); onMessage("Found the Mac at $found.") }
                else onMessage("No Mac found. Check that Ollama is open on the Mac, that it allows network connections, and that both are on the same Wi-Fi.")
                busy = false
            }
        })
        BigButton("Save & test", tonal = true, enabled = !busy && url.isNotBlank(), onClick = {
            vm.prefs.macUrl = com.simplespeak.app.ai.OllamaBackend.normalizeUrl(url); url = vm.prefs.macUrl; vm.refresh()
            busy = true; onMessage("Testing… the first answer can take a few seconds while the Mac loads the model.")
            scope.launch {
                loadModels()
                onMessage(
                    if (!vm.mac.ping()) "Can't reach the Mac at that address."
                    else try { vm.coach.testConnection(); "It works. The coach on the Mac is ready." } catch (e: CoachException) { e.message }
                )
                vm.checkMac(); busy = false
            }
        })
    }
    if (models.isNotEmpty()) {
        SectionLabel("Model on the Mac")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            models.forEach { m -> FilterChip(model == m, { model = m; vm.prefs.macModel = m }, label = { Text(m) }) }
        }
    }
    SwitchRow2("Use Claude when the Mac is off", "Needs a Claude key below. Otherwise only the built-in exercises work while the Mac is away.", fallback) {
        fallback = it; vm.prefs.fallbackToClaude = it
    }
    if (fallback) ClaudeKey(vm, onMessage)
}

@Composable
private fun SwitchRow2(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ClaudeKey(vm: AppViewModel, onMessage: (String?) -> Unit) {
    var key by remember { mutableStateOf(vm.prefs.apiKey) }
    var show by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("Paste an API key from console.anthropic.com. Answers (and photos used in picture practice) are sent to Anthropic, with a small cost per answer.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        key, { key = it }, label = { Text("Claude API key") }, singleLine = true, shape = RoundedCornerShape(16.dp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide key" else "Show key") } },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton("Save", tonal = true, onClick = { vm.prefs.apiKey = key; vm.refresh(); onMessage(if (key.isBlank()) "Key removed." else "Saved.") })
        BigButton("Test", tonal = true, enabled = !testing && key.isNotBlank(), onClick = {
            vm.prefs.apiKey = key; vm.refresh(); testing = true; onMessage("Testing…")
            scope.launch {
                onMessage(try { vm.coach.testConnection(); "It works." } catch (e: CoachException) { e.message })
                testing = false
            }
        })
    }
}

// ---- Progress ------------------------------------------------------------------------------

@Composable
private fun ProgressSection(vm: AppViewModel, attempts: List<Attempt>) {
    val now = Stats.week(attempts, 0)
    val before = Stats.week(attempts, 1)
    val today = LocalDate.now()

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("This week vs last week", style = MaterialTheme.typography.titleMedium)
            if (now.answers == 0 && before.answers == 0) Text("No practice yet.", style = MaterialTheme.typography.bodyLarge)
            else {
                TrendRow("Answers", "${now.answers}", "${before.answers}", null)
                TrendRow("Practice time", "${now.minutes} min", "${before.minutes} min", null)
                TrendRow("Right first time", now.correctPct?.let { "$it%" } ?: "–", before.correctPct?.let { "$it%" }, null)
                TrendRow("Words per spoken answer", now.wordsPerAnswer?.let { "%.1f".format(it) } ?: "–", before.wordsPerAnswer?.let { "%.1f".format(it) }, null)
                TrendRow("Seconds before he starts", now.startSeconds?.let { "%.1f".format(it) } ?: "–", before.startSeconds?.let { "%.1f".format(it) }, null)
                TrendRow("Speaking speed (words/min)", now.wordsPerMinute?.toString() ?: "–", before.wordsPerMinute?.toString(), null)
                Text("Fluency numbers come from the microphone: how quickly he starts, how much he says, and how fast he speaks.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Each exercise (last 7 days)", style = MaterialTheme.typography.titleMedium)
            ExerciseType.practice.forEach { t ->
                val list = attempts.filter { it.type == t && !it.day().isBefore(today.minusDays(6)) }
                Row(Modifier.fillMaxWidth()) {
                    Text("${t.emoji} ${t.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("L${vm.prefs.level(t)} · ${if (list.isEmpty()) "–" else "${pct(list.count { it.correct }, list.size)} of ${list.size}"}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    val month = attempts.filter { !it.day().isBefore(today.minusDays(29)) }
    val mistakes = month.flatMap { it.mistakes }.filter { it != "other" }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Grammar to work on (last 30 days)", style = MaterialTheme.typography.titleMedium)
            if (mistakes.isEmpty()) Text("No common mistakes found yet.", style = MaterialTheme.typography.bodyMedium)
            mistakes.forEach { (k, n) -> Text("• ${MistakeTypes.label(k)}: $n", style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (attempts.isNotEmpty()) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recent answers", style = MaterialTheme.typography.titleMedium)
                attempts.takeLast(12).reversed().forEach { a ->
                    Column {
                        Text("${a.type.emoji} ${a.prompt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        Text("${if (a.correct) "✓" else "○"}  “${a.answer}”", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingsSection(vm: AppViewModel) {
    val all = remember(vm.dataVersion) { vm.store.recordings() }
    var answers by remember { mutableStateOf(true) }
    val list = all.filter { it.answer == answers }
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voice recordings", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(answers, { answers = true }, label = { Text("His answers") })
                FilterChip(!answers, { answers = false }, label = { Text("Practice repeats") })
            }
            if (list.isEmpty()) {
                Text(
                    if (answers) {
                        if (vm.prefs.answerCaptureBroken) "This phone's speech service can't share the microphone, so his answers can't be saved while he speaks. He can still use “Record myself” after each exercise."
                        else "His spoken answers are saved here, so you can hear how he improves."
                    } else "When he taps “Record myself” after an exercise, the clip is saved here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            list.take(40).forEach { r ->
                val playing = vm.recorder.playingPath == r.path
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (playing) vm.recorder.stopPlayback() else vm.recorder.play(r.path) }) {
                        Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (playing) "Stop" else "Play")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.sentence, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                        Text(Instant.ofEpochMilli(r.time).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFormat),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.recorder.stopPlayback(); vm.store.deleteRecording(r.id); vm.refresh() }) {
                        Icon(Icons.Rounded.Delete, "Delete recording")
                    }
                }
            }
        }
    }
}

// ---- Settings --------------------------------------------------------------------------------

@Composable
private fun LearnerSection(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.prefs.learnerName) }
    var age by remember { mutableStateOf(vm.prefs.ageGroup) }
    var interests by remember { mutableStateOf(vm.prefs.interests) }
    OutlinedTextField(name, { name = it; vm.prefs.learnerName = it }, label = { Text("First name") }, singleLine = true,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
    AgePicker(age) { age = it; vm.prefs.ageGroup = it }
    OutlinedTextField(interests, { interests = it; vm.prefs.interests = it }, label = { Text("Things he likes (comma separated)") },
        placeholder = { Text("e.g. trains, football, cooking") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
    Text("Interests make practice sentences, questions and conversation topics more personal.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun PracticeSettings(vm: AppViewModel) {
    var length by remember { mutableFloatStateOf(vm.prefs.sessionLength.toFloat()) }
    var think by remember { mutableFloatStateOf(vm.prefs.thinkSeconds.toFloat()) }
    var goal by remember { mutableFloatStateOf(vm.prefs.dailyGoal.toFloat()) }
    var rate by remember { mutableFloatStateOf(vm.prefs.speechRate) }
    var enabled by remember { mutableStateOf(ExerciseType.practice.associateWith { vm.prefs.isEnabled(it) }) }
    var levels by remember { mutableStateOf(ExerciseType.practice.associateWith { vm.prefs.level(it) }) }
    var saveAnswers by remember { mutableStateOf(vm.prefs.saveAnswers) }
    var speakReplies by remember { mutableStateOf(vm.prefs.speakReplies) }
    var reduceMotion by remember { mutableStateOf(vm.prefs.reduceMotion) }
    var captureBroken by remember { mutableIntStateOf(if (vm.prefs.answerCaptureBroken) 1 else 0) }

    Text("Session length: ${length.roundToInt()} steps", style = MaterialTheme.typography.bodyLarge)
    Slider(length, { length = it }, onValueChangeFinished = { vm.prefs.sessionLength = length.roundToInt(); vm.refresh() }, valueRange = 3f..15f, steps = 11)

    Text("Think time before speaking: ${if (think.roundToInt() == 0) "off" else "${think.roundToInt()} seconds"}", style = MaterialTheme.typography.bodyLarge)
    Slider(think, { think = it }, onValueChangeFinished = { vm.prefs.thinkSeconds = think.roundToInt() }, valueRange = 0f..15f, steps = 14)
    Text("A short countdown to plan, then the microphone opens by itself. This builds planning in real time. Lower it as he gets faster.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Text("Daily goal: ${goal.roundToInt()} answers", style = MaterialTheme.typography.bodyLarge)
    Slider(goal, { goal = it }, onValueChangeFinished = { vm.prefs.dailyGoal = goal.roundToInt(); vm.refresh() }, valueRange = 5f..40f, steps = 6)

    SectionLabel("Exercises and levels")
    Text("Levels change automatically. You can also set a starting level (1–6). Levels 4–6 use the coach to write new practice.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ExerciseType.practice.forEach { t ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${t.emoji} ${t.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { val l = (levels.getValue(t) - 1).coerceAtLeast(1); vm.prefs.setLevel(t, l); levels = levels + (t to l) }) { Icon(Icons.Rounded.Remove, "Lower level") }
            Text("L${levels.getValue(t)}", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { val l = (levels.getValue(t) + 1).coerceAtMost(ContentBank.MAX_LEVEL); vm.prefs.setLevel(t, l); levels = levels + (t to l) }) { Icon(Icons.Rounded.Add, "Raise level") }
            Switch(enabled[t] == true, { on ->
                if (on || enabled.count { it.value } > 1) { vm.prefs.setEnabled(t, on); enabled = enabled + (t to on); vm.refresh() }
            })
        }
    }

    SectionLabel("Voice and display")
    SwitchRow("Save his spoken answers", "So you can hear his progress in Voice recordings.", saveAnswers) { saveAnswers = it; vm.prefs.saveAnswers = it }
    if (captureBroken == 1) {
        Text("This phone's speech service did not accept shared audio last time, so answers aren't being saved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { vm.prefs.answerCaptureBroken = false; captureBroken = 0 }) { Text("Try again") }
    }
    SwitchRow("Read the coach's replies aloud", null, speakReplies) { speakReplies = it; vm.prefs.speakReplies = it }
    SwitchRow("Reduce motion", "Stops the coach's breathing animation.", reduceMotion) { reduceMotion = it; vm.prefs.reduceMotion = it; vm.refresh() }
    Text("Reading voice speed", style = MaterialTheme.typography.bodyLarge)
    Slider(rate, { rate = it }, onValueChangeFinished = { vm.prefs.speechRate = rate; vm.speaker.say("This is how fast I will read.") }, valueRange = 0.5f..1.3f)
}

@Composable
private fun PhotosSection(vm: AppViewModel) {
    val photos = remember(vm.dataVersion) { vm.store.photos() }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { pending = uri; caption = "" } }

    Text("Add photos of familiar people, places and things. The coach can look at the photo to check his description.",
        style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = RoundedCornerShape(24.dp)) {
        Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Add a photo")
    }
    photos.forEach { p ->
        val bmp = remember(p.path) { BitmapFactory.decodeFile(p.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bmp != null) Image(bmp, null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Text(p.caption.ifBlank { "(no note)" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.store.deletePhoto(p.id); vm.refresh() }) { Icon(Icons.Rounded.Delete, "Delete photo") }
        }
    }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Add a note") },
            text = { OutlinedTextField(caption, { caption = it }, label = { Text("What is in this photo?") }) },
            confirmButton = { TextButton(onClick = { val u = pending; pending = null; if (u != null) { vm.store.importPhoto(u, caption); vm.refresh() } }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResetSection(vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }, shape = RoundedCornerShape(24.dp)) { Text("Clear progress and levels") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear progress?") },
            text = { Text("This removes the answer history, stars, streak and levels. Recordings, photos, settings and the coach are kept.") },
            confirmButton = { TextButton(onClick = { vm.store.clearProgress(); vm.prefs.resetProgress(); vm.refresh(); confirm = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
