package com.simplespeak.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.data.AgeGroup
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.RewardStyle
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.BigButton
import com.simplespeak.app.ui.ListenButton
import com.simplespeak.app.ui.LocalExtraColors
import com.simplespeak.app.ui.Screen
import com.simplespeak.app.ui.SoftCard
import com.simplespeak.app.ui.SpeakInput
import com.simplespeak.app.ui.StepDots
import com.simplespeak.app.ui.Thinking
import kotlinx.coroutines.launch

/** Standard scrolling page with padding. */
@Composable
fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) { content() }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun SetupScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.prefs.learnerName) }
    var age by remember { mutableStateOf(vm.prefs.ageGroup) }
    Page {
        Spacer(Modifier.height(24.dp))
        Text("Welcome to SimpleSpeak", style = MaterialTheme.typography.headlineMedium)
        Text(
            "SimpleSpeak helps you practise putting your ideas into clear sentences. A caregiver can help with this first step.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Learner's first name") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Age group", style = MaterialTheme.typography.titleMedium)
        AgePicker(age) { age = it }
        Text(
            "Smart feedback uses Claude. A caregiver can add a Claude API key later in the Caregiver tab. Word-tile practice works without it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton("Start", onClick = {
            vm.prefs.learnerName = name
            vm.prefs.ageGroup = age
            vm.prefs.setupDone = true
            vm.navigate(Screen.Home)
        }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AgePicker(selected: AgeGroup, onSelect: (AgeGroup) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AgeGroup.entries.forEachIndexed { i, a ->
            SegmentedButton(
                selected = a == selected,
                onClick = { onSelect(a) },
                shape = SegmentedButtonDefaults.itemShape(i, AgeGroup.entries.size),
            ) { Text(a.label) }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val name = vm.prefs.learnerName
    val types = vm.availableTypes()
    val plan = List(vm.prefs.sessionLength) { types[it % types.size] }
    Page {
        Text(if (name.isBlank()) "Hello" else "Hello, $name", style = MaterialTheme.typography.headlineMedium)
        if (vm.prefs.rewardStyle == RewardStyle.STARS) {
            val streak = vm.prefs.streak
            Text(
                "⭐ ${vm.prefs.totalStars} stars" + if (streak > 0) "   ·   $streak day${if (streak == 1) "" else "s"} in a row" else "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today's practice", style = MaterialTheme.typography.titleLarge)
                Text("${plan.size} short exercises, in this order:", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.forEachIndexed { i, t ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                            Text("${i + 1}. ${t.emoji}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                BigButton("Start practice", icon = Icons.Rounded.PlayArrow, onClick = vm::startSession, modifier = Modifier.fillMaxWidth())
            }
        }

        Text("Practise one thing", style = MaterialTheme.typography.titleLarge)
        ExerciseType.entries.forEach { t ->
            val locked = t.needsAi && !vm.coach.isConfigured
            Surface(
                onClick = { vm.navigate(Screen.Practice(t)) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, fontSize = 34.sp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (locked) "Needs Claude (Caregiver tab)" else "Level ${vm.prefs.level(t)} of 3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Surface(
            onClick = { vm.navigate(Screen.Helper) },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(34.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Help me say it", style = MaterialTheme.typography.titleMedium)
                    Text("Say your idea in any way. Get a clear sentence back.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun SessionScreen(vm: AppViewModel) {
    val s = vm.session ?: return
    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = true }
    val type = s.plan[s.index]

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Step ${s.index + 1} of ${s.plan.size}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                if (vm.prefs.rewardStyle == RewardStyle.PROGRESS || s.plan.size > 12) {
                    LinearProgressIndicator(progress = { s.index / s.plan.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp))
                } else {
                    StepDots(s.plan.size, s.index)
                }
            }
            IconButton(onClick = { confirmQuit = true }) { Icon(Icons.Rounded.Close, contentDescription = "Stop practice") }
        }
        Text(
            "${type.emoji}  ${type.title}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Column(Modifier.weight(1f)) {
            key(s.index) {
                Page { Exercise(vm, type, onNext = vm::completeStep) }
            }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Stop practice?") },
            text = { Text("You can start again any time.") },
            confirmButton = { TextButton(onClick = { confirmQuit = false; vm.quitSession() }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep going") } },
        )
    }
}

@Composable
fun PracticeScreen(vm: AppViewModel, type: ExerciseType) {
    var round by remember { mutableIntStateOf(0) }
    var done by remember { mutableIntStateOf(0) }
    BackHandler { vm.navigate(Screen.Home) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${type.emoji}  ${type.title}", style = MaterialTheme.typography.titleMedium)
                Text("Done: $done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { vm.navigate(Screen.Home) }) { Text("Finish") }
        }
        Column(Modifier.weight(1f)) {
            key(round) {
                Page {
                    Exercise(vm, type, onNext = {
                        vm.speech.reset(); vm.speaker.stop(); vm.recorder.release()
                        done++; round++
                    })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun FinishedScreen(vm: AppViewModel, done: Int, correct: Int, stars: Int) {
    BackHandler { vm.navigate(Screen.Home) }
    Page {
        Spacer(Modifier.height(32.dp))
        Text("✅", fontSize = 72.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Practice finished", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        SoftCard(color = LocalExtraColors.current.successContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You finished all $done exercises.", style = MaterialTheme.typography.titleMedium)
                Text("$correct were right the first time.", style = MaterialTheme.typography.bodyLarge)
                when (vm.prefs.rewardStyle) {
                    RewardStyle.STARS -> {
                        Text("You earned $stars ⭐", style = MaterialTheme.typography.titleLarge)
                        val streak = vm.prefs.streak
                        if (streak > 1) Text("You have practised $streak days in a row.", style = MaterialTheme.typography.bodyLarge)
                    }
                    RewardStyle.PROGRESS -> LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth().height(10.dp))
                    RewardStyle.NONE -> {}
                }
            }
        }
        BigButton("Back to home", onClick = { vm.navigate(Screen.Home) }, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------------------------

/** "Help me say it": speak a messy thought, get a clear sentence to read or say. */
@Composable
fun HelperScreen(vm: AppViewModel) {
    BackHandler { vm.navigate(Screen.Home) }
    var thought by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<String, String>?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun run(text: String) {
        thought = text
        working = true
        error = null
        scope.launch {
            try {
                result = vm.coach.clarify(text, vm.prefs.ageGroup)
            } catch (e: CoachException) {
                error = e.message
            }
            working = false
        }
    }

    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Help me say it", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.navigate(Screen.Home) }) { Text("Done") }
        }
        if (!vm.coach.isConfigured) {
            Text("This helper uses Claude. A caregiver can turn it on in the Caregiver tab.", style = MaterialTheme.typography.bodyLarge)
            return@Page
        }
        Text("Say your idea in any way. It does not need to be in order. You will get a clear sentence back.", style = MaterialTheme.typography.bodyLarge)

        when {
            working -> Thinking("Making it clear…")
            result != null -> {
                val (clear, short) = result!!
                thought?.let {
                    Text("You said", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                Text("Clear way to say it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SoftCard(color = LocalExtraColors.current.successContainer) {
                    Text(clear, style = MaterialTheme.typography.headlineSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListenButton(vm, clear)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(clear)) }) {
                        Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy")
                    }
                }
                if (short != clear) {
                    Text("Short version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SoftCard { Text(short, style = MaterialTheme.typography.titleLarge) }
                    ListenButton(vm, short, "Listen to short version")
                }
                BigButton("Say something else", onClick = { result = null; thought = null }, tonal = true, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                SpeakInput(vm, onText = ::run, label = "Tap and say your idea")
                error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
