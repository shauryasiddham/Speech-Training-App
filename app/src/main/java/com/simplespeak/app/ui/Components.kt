package com.simplespeak.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.TextMatch
import com.simplespeak.app.speech.SpokenAnswer
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ---- Buttons and cards -------------------------------------------------------------------

/** The main call-to-action: a soft gradient pill. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val extra = LocalExtraColors.current
    Box(
        modifier
            .heightIn(min = 60.dp)
            .shadow(if (enabled) 6.dp else 0.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x334D5AA0), spotColor = Color(0x334D5AA0))
            .clip(RoundedCornerShape(30.dp))
            .background(if (enabled) extra.hero else Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = extra.heroText, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = extra.heroText)
        }
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tonal: Boolean = false,
) {
    if (!tonal) {
        GradientButton(text, onClick, modifier, icon, enabled)
        return
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.heightIn(min = 60.dp),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A soft, slightly raised card. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val m = modifier
        .fillMaxWidth()
        .shadow(2.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = m) { Box(Modifier.padding(20.dp)) { content() } }
    } else {
        Surface(shape = shape, color = color, modifier = m) { Box(Modifier.padding(20.dp)) { content() } }
    }
}

/** The signature gradient panel used at the top of Home and on the finish screen. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x334D5AA0), spotColor = Color(0x404D5AA0))
            .clip(RoundedCornerShape(30.dp))
            .background(LocalExtraColors.current.hero)
            .padding(22.dp)
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}

/** A word tile the learner can tap. */
@Composable
fun WordTile(word: String, onClick: () -> Unit, placed: Boolean = false, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (placed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (placed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (placed) 0.dp else 2.dp,
        modifier = Modifier.heightIn(min = 54.dp),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(word, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Progress through a session: a slim segmented bar. */
@Composable
fun StepBar(total: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            i < current -> MaterialTheme.colorScheme.primary
                            i == current -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}

// ---- Coach orb ---------------------------------------------------------------------------

enum class OrbState { Idle, Listening, Thinking, Speaking }

/** The coach: a calm, glowing orb with no face. It breathes slowly and reacts to his voice. */
@Composable
fun CoachOrb(state: OrbState, level: Float = 0f, size: Dp = 140.dp, reduceMotion: Boolean = false) {
    val extra = LocalExtraColors.current
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(
        0.94f, 1.04f,
        infiniteRepeatable(tween(if (state == OrbState.Thinking) 900 else 2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val voice by animateFloatAsState(if (state == OrbState.Listening) level else 0f, tween(120), label = "voice")
    val scale = if (reduceMotion || state == OrbState.Idle) 1f else breathe + voice * 0.12f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().scale(scale)) {
            val r = this.size.minDimension / 2
            val angle = Math.toRadians((if (reduceMotion) 30f else spin).toDouble())
            val shift = Offset((kotlin.math.cos(angle) * r * 0.18).toFloat(), (kotlin.math.sin(angle) * r * 0.18).toFloat())
            // soft halo
            drawCircle(Brush.radialGradient(listOf(extra.orbOuter.copy(alpha = 0.28f), Color.Transparent), center, r), r)
            // body
            drawCircle(
                Brush.radialGradient(listOf(extra.orbInner, extra.orbOuter), center + shift, r * 0.78f),
                r * 0.72f,
            )
            // highlight
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center + Offset(-r * 0.22f, -r * 0.26f), r * 0.32f),
                r * 0.32f, center + Offset(-r * 0.22f, -r * 0.26f),
            )
            if (state == OrbState.Listening) {
                drawCircle(extra.orbInner.copy(alpha = 0.5f), r * (0.8f + voice * 0.18f), style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

// ---- Speaking --------------------------------------------------------------------------

/**
 * Voice-first answer area.
 * - Optional think time, then the microphone opens by itself, to practise planning in real time.
 * - Typing only appears after the microphone fails twice (or speech isn't available).
 */
@Composable
fun SpeakInput(
    vm: AppViewModel,
    onAnswer: (SpokenAnswer) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Tap and speak",
    thinkFirst: Boolean = false,
    saveAnswer: Boolean = false,
    enabled: Boolean = true,
) {
    val speech = vm.speech
    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }
    val thinkSeconds = vm.prefs.thinkSeconds
    var countdown by rememberSaveable { mutableIntStateOf(if (thinkFirst && thinkSeconds > 0) thinkSeconds else 0) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }

    fun begin() = vm.startListening(saveAnswer, onAnswer)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else permissionDenied = true
    }

    fun tapMic() {
        if (!enabled) return
        countdown = 0
        if (speech.listening) speech.finish()
        else {
            vm.speaker.stop()
            if (speech.hasPermission) begin() else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Think-time countdown, then open the microphone automatically.
    LaunchedEffect(countdown, enabled) {
        if (!enabled || typing) return@LaunchedEffect
        if (countdown > 0) {
            delay(1000); countdown--
            if (countdown == 0 && !autoStarted) { autoStarted = true; tapMic() }
        }
    }

    val canType = permissionDenied || !speech.available || speech.failures >= 2
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (typing) {
            OutlinedTextField(
                value = typed, onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(18.dp),
                label = { Text("Type your answer") },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { typing = false }, modifier = Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp)) {
                    Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(6.dp)); Text("Speak")
                }
                GradientButton("Done", onClick = {
                    if (typed.isNotBlank()) {
                        onAnswer(SpokenAnswer(typed.trim(), com.simplespeak.app.data.Fluency(null, null, typed.trim().split(Regex("\\s+")).size), null)); typed = ""
                    }
                }, enabled = enabled && typed.isNotBlank())
            }
            return@Column
        }

        if (countdown > 0) {
            ThinkTimer(countdown, thinkSeconds)
            Spacer(Modifier.height(10.dp))
            Text("Plan what you will say…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { tapMic() }) { Text("I'm ready") }
            return@Column
        }

        val listening = speech.listening
        Box(contentAlignment = Alignment.Center) {
            if (listening) CoachOrb(OrbState.Listening, speech.level, 150.dp, vm.prefs.reduceMotion)
            Surface(
                onClick = { tapMic() },
                enabled = enabled,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(96.dp)
                    .shadow(if (listening) 0.dp else 8.dp, CircleShape, ambientColor = Color(0x334D5AA0), spotColor = Color(0x554D5AA0))
                    .clip(CircleShape)
                    .background(if (listening) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) else LocalExtraColors.current.hero)
                    .semantics { contentDescription = if (listening) "Stop listening" else label },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic, null, Modifier.size(44.dp), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (listening) "Listening… tap when you finish" else label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (listening && speech.partial.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("“${speech.partial}”", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        val err = when {
            permissionDenied -> "The microphone is turned off for this app. Allow it in the phone's Settings, or type for now."
            !speech.available -> "Speech is not available on this phone. You can type instead."
            else -> speech.error
        }
        if (err != null && !listening) {
            Spacer(Modifier.height(8.dp))
            Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (canType && !listening) {
            TextButton(onClick = { speech.stop(); typing = true }) {
                Icon(Icons.Rounded.Keyboard, null); Spacer(Modifier.width(6.dp)); Text("Type instead")
            }
        }
    }
}

@Composable
fun ThinkTimer(remaining: Int, total: Int) {
    val progress by animateFloatAsState(remaining / total.toFloat().coerceAtLeast(1f), tween(900), label = "think")
    val color = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$remaining", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun ListenButton(vm: AppViewModel, text: String, label: String = "Listen") {
    OutlinedButton(onClick = { vm.speaker.say(text) }, modifier = Modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(25.dp)) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Thinking(vm: AppViewModel, text: String = "Your coach is thinking…") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
        CoachOrb(OrbState.Thinking, size = 56.dp, reduceMotion = vm.prefs.reduceMotion)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Sentence with the changed words highlighted. */
@Composable
fun HighlightedSentence(said: String, target: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge) {
    val extra = LocalExtraColors.current
    val parts = TextMatch.highlightChanges(said, target)
    val text = buildAnnotatedString {
        parts.forEachIndexed { i, (word, changed) ->
            if (changed) withStyle(SpanStyle(background = extra.highlight, color = extra.onHighlight, fontWeight = FontWeight.ExtraBold)) { append(word) }
            else append(word)
            if (i < parts.lastIndex) append(" ")
        }
    }
    Text(text, style = style)
}

// ---- Feedback ------------------------------------------------------------------------------

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
                    if (feedback.correct) Icons.Rounded.CheckCircle else Icons.Rounded.Lightbulb, null,
                    tint = if (feedback.correct) extra.success else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (feedback.correct) "That's right." else "Good try. Let's make it clearer.", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(feedback.praise, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (!feedback.correct) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("You said")
                    Text(feedback.learnerText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    SectionLabel("A clear way to say it")
                    HighlightedSentence(feedback.learnerText, feedback.improved)
                    if (feedback.tip.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        SectionLabel("Grammar tip")
                        Text(feedback.tip, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (feedback.betterWords.isNotEmpty() || feedback.organization.isNotBlank()) {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (feedback.betterWords.isNotEmpty()) {
                        SectionLabel("Words to try")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            feedback.betterWords.forEach { w ->
                                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(w.insteadOf) }
                                            append("  →  ")
                                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(w.tryWord) }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (feedback.organization.isNotBlank()) {
                        SectionLabel("Organise your ideas")
                        Text(feedback.organization, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (feedback.nextLevel.isNotBlank()) {
            SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        SectionLabel("Level up: say it like this")
                    }
                    Text(feedback.nextLevel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    ListenButton(vm, feedback.nextLevel)
                }
            }
        }

        feedback.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ListenButton(vm, feedback.improved, "Listen")
            RecordVoiceButton(vm, feedback.improved)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (onTryAgain != null && !feedback.correct) BigButton("Try again", onClick = onTryAgain, tonal = true, modifier = Modifier.weight(1f))
            GradientButton(nextLabel, onClick = onNext, modifier = Modifier.weight(1f))
        }
    }
}

/** Lets him record himself saying the clear sentence; saved for the caregiver. */
@Composable
fun RecordVoiceButton(vm: AppViewModel, sentence: String) {
    val rec = vm.recorder
    var saved by remember(sentence) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startRecording() }
    OutlinedButton(
        onClick = {
            if (rec.recording) saved = vm.stopRecording(sentence)
            else {
                vm.speech.stop(); vm.speaker.stop()
                if (vm.speech.hasPermission) vm.startRecording() else launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = Modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(25.dp),
        colors = if (rec.recording) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors(),
    ) {
        Icon(if (rec.recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                rec.recording -> "Stop recording"
                saved -> "Saved · again"
                else -> "Record myself"
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ---- Motivation ------------------------------------------------------------------------------

@Composable
fun StreakFlame(streak: Int, modifier: Modifier = Modifier, color: Color = LocalExtraColors.current.flame, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = color, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(4.dp))
        Column {
            Text("$streak", style = MaterialTheme.typography.headlineMedium, color = textColor)
            Text("day streak", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.8f))
        }
    }
}

/** The last seven days, with practised days filled in. */
@Composable
fun WeekStrip(practiced: Set<LocalDate>, onHero: Boolean = false) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val fg = if (onHero) LocalExtraColors.current.heroText else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val done = d in practiced
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    d.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg.copy(alpha = if (d == today) 1f else 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> LocalExtraColors.current.flame
                                onHero -> Color.White.copy(alpha = 0.18f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                        .then(if (d == today && !done) Modifier.border(2.dp, fg.copy(alpha = 0.6f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Ring showing today's answers against the daily goal. */
@Composable
fun GoalRing(done: Int, goal: Int, size: Dp = 92.dp, onHero: Boolean = false) {
    val progress by animateFloatAsState((done / goal.toFloat()).coerceIn(0f, 1f), tween(700), label = "goal")
    val fg = if (onHero) LocalExtraColors.current.heroText else MaterialTheme.colorScheme.onSurface
    val track = if (onHero) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant
    val fill = if (onHero) Color.White else MaterialTheme.colorScheme.primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(stroke))
            drawArc(fill, -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$goal", style = MaterialTheme.typography.titleMedium, color = fg)
            Text("today", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
        }
    }
}

data class Milestone(val days: Int, val title: String)

val streakMilestones = listOf(
    Milestone(3, "Getting started"), Milestone(7, "One week"), Milestone(14, "Two weeks"),
    Milestone(30, "One month"), Milestone(60, "Two months"), Milestone(100, "100 days"),
)

@Composable
fun MilestoneBadge(m: Milestone, unlocked: Boolean) {
    val extra = LocalExtraColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (unlocked) extra.hero else Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))),
            contentAlignment = Alignment.Center,
        ) {
            Text("${m.days}", style = MaterialTheme.typography.titleMedium, color = if (unlocked) extra.heroText else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(m.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A compare row: this week vs last week with an arrow. Lower can be better (e.g. start time). */
@Composable
fun TrendRow(label: String, now: String, before: String?, better: Boolean?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(now, style = MaterialTheme.typography.titleSmall)
            if (before != null) {
                Text(
                    (if (better == true) "✓ better · " else "") + "last week $before",
                    style = MaterialTheme.typography.labelMedium,
                    color = when (better) {
                        true -> LocalExtraColors.current.success
                        false -> MaterialTheme.colorScheme.onSurfaceVariant
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun Loading(text: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small, always-visible pill showing where the coach is running right now: the Mac or this phone. */
@Composable
fun CoachBadge(vm: AppViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val tick = vm.dataVersion // refresh when settings change
    val extra = LocalExtraColors.current
    val where = vm.coachWhere
    val (icon, text, dot) = when (where) {
        AppViewModel.CoachWhere.MAC -> Triple(Icons.Rounded.Computer, "Coach: Mac", extra.success)
        AppViewModel.CoachWhere.MAC_CHECKING -> Triple(Icons.Rounded.Computer, "Coach: Mac…", MaterialTheme.colorScheme.outline)
        AppViewModel.CoachWhere.PHONE -> Triple(Icons.Rounded.PhoneAndroid, "Coach: Phone", extra.success)
        AppViewModel.CoachWhere.PHONE_BECAUSE_MAC_AWAY -> Triple(Icons.Rounded.PhoneAndroid, "Coach: Phone · Mac off", extra.flame)
        AppViewModel.CoachWhere.OFF -> Triple(Icons.Rounded.CloudOff, "Coach: off", MaterialTheme.colorScheme.error)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
