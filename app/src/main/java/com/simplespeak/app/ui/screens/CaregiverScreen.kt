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
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.simplespeak.app.data.Attempt
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.MistakeTypes
import com.simplespeak.app.data.RewardStyle
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.BigButton
import com.simplespeak.app.ui.SoftCard
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
        Text(
            "See progress and change settings. Everything is stored only on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProgressSection(vm, attempts)
        RecordingsSection(vm)
        SectionTitle("Learner")
        LearnerSection(vm)
        SectionTitle("Practice settings")
        PracticeSettings(vm)
        SectionTitle("Claude (smart feedback)")
        ClaudeSection(vm)
        SectionTitle("Photos for \"Describe a picture\"")
        PhotosSection(vm)
        SectionTitle("Reset")
        ResetSection(vm)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider()
    Text(text, style = MaterialTheme.typography.titleLarge)
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private fun Attempt.day(): LocalDate = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
private fun pct(part: Int, whole: Int) = if (whole == 0) "–" else "${(100.0 * part / whole).roundToInt()}%"

// ---------------------------------------------------------------------------------------------

@Composable
private fun ProgressSection(vm: AppViewModel, attempts: List<Attempt>) {
    val today = LocalDate.now()
    val week = attempts.filter { !it.day().isBefore(today.minusDays(6)) }
    val month = attempts.filter { !it.day().isBefore(today.minusDays(29)) }

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            if (week.isEmpty()) {
                Text("No practice yet this week.", style = MaterialTheme.typography.bodyLarge)
            } else {
                val minutes = (week.sumOf { it.seconds } / 60.0).roundToInt().coerceAtLeast(1)
                Text("Practice time: about $minutes minute${if (minutes == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
                Text("Answers: ${week.size}", style = MaterialTheme.typography.bodyLarge)
                Text("Right the first time: ${pct(week.count { it.correct }, week.size)}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Each exercise (last 7 days)", style = MaterialTheme.typography.titleMedium)
            ExerciseType.entries.forEach { t ->
                val list = week.filter { it.type == t }
                Row(Modifier.fillMaxWidth()) {
                    Text("${t.emoji} ${t.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "Level ${vm.prefs.level(t)} · ${if (list.isEmpty()) "not practised" else "${pct(list.count { it.correct }, list.size)} of ${list.size}"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val mistakes = month.flatMap { it.mistakes }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Grammar to work on (last 30 days)", style = MaterialTheme.typography.titleMedium)
            if (mistakes.isEmpty()) {
                Text(
                    if (vm.coach.isConfigured) "No common mistakes found yet." else "Turn on Claude below to see which grammar areas need practice.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                mistakes.forEach { (key, count) ->
                    Text("• ${MistakeTypes.label(key)}: $count time${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (week.isNotEmpty()) {
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Day by day", style = MaterialTheme.typography.titleMedium)
                (0..6).map { today.minusDays(it.toLong()) }.forEach { d ->
                    val list = week.filter { it.day() == d }
                    Row(Modifier.fillMaxWidth()) {
                        Text(if (d == today) "Today" else d.format(dayFormat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (list.isEmpty()) "—" else "${list.size} answers, ${list.count { it.correct }} right",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recent answers", style = MaterialTheme.typography.titleMedium)
                attempts.takeLast(10).reversed().forEach { a ->
                    Column {
                        Text("${a.type.emoji} ${a.prompt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${if (a.correct) "✓" else "○"}  \"${a.answer}\"", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun RecordingsSection(vm: AppViewModel) {
    val recordings = remember(vm.dataVersion) { vm.store.recordings() }
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voice recordings", style = MaterialTheme.typography.titleMedium)
            if (recordings.isEmpty()) {
                Text(
                    "When the learner taps \"Record my voice\" after an exercise, the clip is saved here so you can hear progress over time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            recordings.take(30).forEach { r ->
                val playing = vm.recorder.playingPath == r.path
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (playing) vm.recorder.stopPlayback() else vm.recorder.play(r.path) }) {
                        Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Stop" else "Play")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.sentence, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Instant.ofEpochMilli(r.time).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFormat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.recorder.stopPlayback(); vm.store.deleteRecording(r.id); vm.refresh() }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete recording")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun LearnerSection(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.prefs.learnerName) }
    var age by remember { mutableStateOf(vm.prefs.ageGroup) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it; vm.prefs.learnerName = it },
        label = { Text("First name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Age group (changes the sentences and wording)", style = MaterialTheme.typography.bodyMedium)
    AgePicker(age) { age = it; vm.prefs.ageGroup = it }
}

@Composable
private fun PracticeSettings(vm: AppViewModel) {
    var length by remember { mutableFloatStateOf(vm.prefs.sessionLength.toFloat()) }
    var rate by remember { mutableFloatStateOf(vm.prefs.speechRate) }
    var reward by remember { mutableStateOf(vm.prefs.rewardStyle) }
    var enabled by remember { mutableStateOf(ExerciseType.entries.associateWith { vm.prefs.isEnabled(it) }) }

    Text("Session length: ${length.roundToInt()} exercises (about ${(length * 1.5).roundToInt()} minutes)", style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = length,
        onValueChange = { length = it },
        onValueChangeFinished = { vm.prefs.sessionLength = length.roundToInt() },
        valueRange = 3f..15f,
        steps = 11,
    )

    Text("Exercises in a session", style = MaterialTheme.typography.bodyLarge)
    ExerciseType.entries.forEach { t ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${t.emoji} ${t.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = enabled[t] == true, onCheckedChange = { on ->
                // Keep at least one exercise turned on.
                if (on || enabled.count { it.value } > 1) {
                    vm.prefs.setEnabled(t, on)
                    enabled = enabled + (t to on)
                }
            })
        }
    }
    Text(
        "Exercises that need Claude are left out of sessions until a key is added.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Rewards", style = MaterialTheme.typography.bodyLarge)
    RewardStyle.entries.forEach { r ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = reward == r, role = Role.RadioButton, onClick = { reward = r; vm.prefs.rewardStyle = r })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = reward == r, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(r.label, style = MaterialTheme.typography.bodyLarge)
                Text(r.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Text("Reading voice speed", style = MaterialTheme.typography.bodyLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Slow", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = rate,
            onValueChange = { rate = it },
            onValueChangeFinished = { vm.prefs.speechRate = rate; vm.speaker.say("This is how fast I will read.") },
            valueRange = 0.5f..1.2f,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text("Fast", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ClaudeSection(vm: AppViewModel) {
    var key by remember { mutableStateOf(vm.prefs.apiKey) }
    var show by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(
        "Claude checks spoken sentences, describes pictures and powers \"Help me say it\". " +
            "Create an API key at console.anthropic.com and paste it here. Use costs a few cents per session " +
            "and is billed to that Anthropic account. The learner's sentences (and photos used in picture practice) " +
            "are sent to Anthropic to be checked.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it; status = null },
        label = { Text("Claude API key") },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = if (show) "Hide key" else "Show key")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton("Save", onClick = { vm.prefs.apiKey = key; status = if (key.isBlank()) "Key removed." else "Saved."; vm.refresh() })
        BigButton("Test", tonal = true, enabled = !testing && key.isNotBlank(), onClick = {
            vm.prefs.apiKey = key
            vm.refresh()
            testing = true
            status = "Testing…"
            scope.launch {
                status = try {
                    vm.coach.testConnection(); "It works. Smart feedback is on."
                } catch (e: CoachException) {
                    e.message
                }
                testing = false
            }
        })
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun PhotosSection(vm: AppViewModel) {
    val photos = remember(vm.dataVersion) { vm.store.photos() }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { pending = uri; caption = "" }
    }

    Text(
        "Add photos of familiar people, places and things. Personal photos make practice more meaningful. " +
            "Add a short note so the feedback understands the photo (for example \"Grandma in her garden\").",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
        Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Add a photo")
    }
    photos.forEach { p ->
        val bmp = remember(p.path) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(p.path, opts)?.asImageBitmap()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bmp != null) {
                Image(bmp, null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(12.dp))
            Text(p.caption.ifBlank { "(no note)" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.store.deletePhoto(p.id); vm.refresh() }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete photo") }
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Add a note") },
            text = {
                OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("What is in this photo?") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pending
                    pending = null
                    if (uri != null) {
                        vm.store.importPhoto(uri, caption)
                        vm.refresh()
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResetSection(vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }) { Text("Clear progress and levels") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear progress?") },
            text = { Text("This removes the answer history, stars, streak and levels. Recordings, photos and settings are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.store.clearProgress(); vm.prefs.resetProgress(); vm.refresh(); confirm = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
