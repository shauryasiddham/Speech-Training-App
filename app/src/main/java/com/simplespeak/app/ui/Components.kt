package com.simplespeak.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.TextMatch

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tonal: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    val mod = modifier.heightIn(min = 60.dp)
    if (tonal) {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = mod, shape = RoundedCornerShape(18.dp)) { content() }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = mod, shape = RoundedCornerShape(18.dp)) { content() }
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Box(Modifier.padding(20.dp)) { content() }
    }
}

/** A word tile the learner can tap. */
@Composable
fun WordTile(word: String, onClick: () -> Unit, placed: Boolean = false, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (placed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (placed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(word, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Shows progress through a session as numbered steps. */
@Composable
fun StepDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val done = i < current
            val now = i == current
            Box(
                Modifier
                    .size(if (now) 18.dp else 14.dp)
                    .background(
                        when {
                            done -> MaterialTheme.colorScheme.primary
                            now -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    )
                    .then(if (now) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            )
        }
    }
}

/**
 * Microphone button. Asks for permission the first time, shows what is being heard,
 * and offers typing if speech is not available.
 */
@Composable
fun SpeakInput(
    vm: AppViewModel,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Tap and speak",
    enabled: Boolean = true,
    allowTyping: Boolean = true,
) {
    val speech = vm.speech
    val context = LocalContext.current
    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start(onText) else permissionDenied = true
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!typing) {
            val listening = speech.listening
            Surface(
                onClick = {
                    if (!enabled) return@Surface
                    if (listening) {
                        speech.finish()
                    } else {
                        vm.speaker.stop()
                        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (has) speech.start(onText) else launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = enabled,
                shape = CircleShape,
                color = if (listening) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(104.dp)
                    .semantics { contentDescription = if (listening) "Stop listening" else label },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic, null, Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (listening) "Listening… tap when you are finished" else label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (listening && speech.partial.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("\"${speech.partial}\"", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            val err = when {
                permissionDenied -> "The microphone is turned off for this app. You can type instead, or allow the microphone in the phone's Settings."
                !speech.available -> "Speech is not available on this phone. You can type instead."
                else -> speech.error
            }
            if (err != null && !listening) {
                Spacer(Modifier.height(8.dp))
                Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (allowTyping) {
                TextButton(onClick = { speech.stop(); typing = true }) {
                    Icon(Icons.Rounded.Keyboard, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Type instead")
                }
            }
        } else {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text("Type your sentence") },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { typing = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(6.dp)); Text("Speak instead")
                }
                BigButton("Done", onClick = { if (typed.isNotBlank()) { onText(typed.trim()); typed = "" } }, enabled = enabled && typed.isNotBlank())
            }
        }
    }
}

@Composable
fun ListenButton(vm: AppViewModel, text: String, label: String = "Listen") {
    OutlinedButton(onClick = { vm.speaker.say(text) }, modifier = Modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Rounded.VolumeUp, null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
fun Thinking(text: String = "Checking…") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Sentence with the changed words highlighted. */
@Composable
fun HighlightedSentence(said: String, target: String) {
    val extra = LocalExtraColors.current
    val parts = TextMatch.highlightChanges(said, target)
    val text = buildAnnotatedString {
        parts.forEachIndexed { i, (word, changed) ->
            if (changed) {
                withStyle(SpanStyle(background = extra.highlight, color = extra.onHighlight, fontWeight = FontWeight.Bold)) { append(word) }
            } else append(word)
            if (i < parts.lastIndex) append(" ")
        }
    }
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/**
 * Gentle feedback: praise first, then the learner's sentence and a clear version side by side,
 * with changed words highlighted, and one simple tip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackCard(
    vm: AppViewModel,
    feedback: Feedback,
    onTryAgain: (() -> Unit)?,
    onNext: () -> Unit,
    nextLabel: String = "Next",
) {
    val extra = LocalExtraColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SoftCard(color = if (feedback.correct) extra.successContainer else MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (feedback.correct) Icons.Rounded.CheckCircle else Icons.Rounded.Lightbulb,
                    null,
                    tint = if (feedback.correct) extra.success else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (feedback.correct) "That's right." else "Good try. Let's make it clearer.",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(feedback.praise, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (!feedback.correct) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You said", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(feedback.learnerText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("A clear way to say it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HighlightedSentence(feedback.learnerText, feedback.improved)
                    if (feedback.tip.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Tip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(feedback.tip, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else if (!TextMatch.sameSentence(feedback.learnerText, feedback.improved)) {
            SoftCard {
                Column {
                    Text("Another way to say it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(feedback.improved, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        feedback.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListenButton(vm, feedback.improved, "Listen to it")
            RecordVoiceButton(vm, feedback.improved)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (onTryAgain != null && !feedback.correct) {
                BigButton("Try again", onClick = onTryAgain, tonal = true, modifier = Modifier.weight(1f))
            }
            BigButton(nextLabel, onClick = onNext, modifier = Modifier.weight(1f))
        }
    }
}

/** Lets the learner record themselves saying the clear sentence; saved for the caregiver. */
@Composable
fun RecordVoiceButton(vm: AppViewModel, sentence: String) {
    val context = LocalContext.current
    val rec = vm.recorder
    var saved by remember(sentence) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }
    OutlinedButton(
        onClick = {
            if (rec.recording) {
                saved = vm.stopRecording(sentence)
            } else {
                vm.speech.stop()
                vm.speaker.stop()
                val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (has) vm.startRecording() else launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = Modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (rec.recording) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors(),
    ) {
        Icon(if (rec.recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                rec.recording -> "Stop recording"
                saved -> "Saved. Record again"
                else -> "Record my voice"
            }
        )
    }
}
