package com.simplespeak.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.ai.LocalModel
import com.simplespeak.app.data.AgeGroup
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Stats
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.CoachOrb
import com.simplespeak.app.ui.GoalRing
import com.simplespeak.app.ui.GradientButton
import com.simplespeak.app.ui.HeroCard
import com.simplespeak.app.ui.ListenButton
import com.simplespeak.app.ui.LocalExtraColors
import com.simplespeak.app.ui.OrbState
import com.simplespeak.app.ui.Screen
import com.simplespeak.app.ui.SectionLabel
import com.simplespeak.app.ui.SoftCard
import com.simplespeak.app.ui.SpeakInput
import com.simplespeak.app.ui.StepBar
import com.simplespeak.app.ui.StreakFlame
import com.simplespeak.app.ui.Thinking
import com.simplespeak.app.ui.WeekStrip
import com.simplespeak.app.ui.streakMilestones
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Standard scrolling page with a soft wash at the top. */
@Composable
fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(LocalExtraColors.current.wash))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun SetupScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.prefs.learnerName) }
    var age by remember { mutableStateOf(vm.prefs.ageGroup) }
    var interests by remember { mutableStateOf(vm.prefs.interests) }
    Page {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CoachOrb(OrbState.Idle, size = 120.dp) }
        Text("Welcome to SimpleSpeak", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "Practise turning your ideas into clear spoken sentences, with a speaking coach that runs on your own Mac or phone.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(name, { name = it }, label = { Text("First name") }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
        SectionLabel("Age group")
        AgePicker(age) { age = it }
        OutlinedTextField(interests, { interests = it }, label = { Text("Things he likes (optional)") }, placeholder = { Text("e.g. trains, football, cooking") },
            textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
        Text(
            "Next, a caregiver can connect the speaking coach (on the Mac or this phone) in the Caregiver tab.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientButton("Get started", onClick = {
            vm.prefs.learnerName = name; vm.prefs.ageGroup = age; vm.prefs.interests = interests; vm.prefs.setupDone = true
            vm.navigate(Screen.Home)
        }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AgePicker(selected: AgeGroup, onSelect: (AgeGroup) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AgeGroup.entries.forEachIndexed { i, a ->
            SegmentedButton(selected = a == selected, onClick = { onSelect(a) }, shape = SegmentedButtonDefaults.itemShape(i, AgeGroup.entries.size)) { Text(a.label) }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(vm: AppViewModel) {
    val version = vm.dataVersion
    val attempts = remember(version) { vm.store.attempts() }
    val today = Stats.answersToday(attempts)
    val goal = vm.prefs.dailyGoal
    val streak = vm.prefs.streak
    val practiced = remember(version) { vm.prefs.practicedDays }
    val name = vm.prefs.learnerName
    val extra = LocalExtraColors.current
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    val plan = remember(version) { vm.sessionPlan() }
    LaunchedEffect(Unit) { while (true) { vm.checkMac(); kotlinx.coroutines.delay(20_000) } }

    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$greeting${if (name.isNotBlank()) ", $name" else ""}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        if (vm.aiReady) com.simplespeak.app.ui.CoachBadge(vm)

        HeroCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreakFlame(streak, Modifier.weight(1f), color = androidx.compose.ui.graphics.Color.White, textColor = extra.heroText)
                    GoalRing(today, goal, onHero = true)
                }
                WeekStrip(practiced, onHero = true)
                Text(
                    when {
                        today >= goal -> "Daily goal done. Brilliant work today."
                        streak > 0 && today == 0 -> "Practise today to keep your $streak-day streak going."
                        else -> "${goal - today} more answers to reach today's goal."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = extra.heroText.copy(alpha = 0.9f),
                )
                Surface(onClick = vm::startSession, shape = RoundedCornerShape(30.dp), color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Start practice · ${plan.size} steps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (!vm.aiReady) CoachSetupBanner(vm)

        SoftCard(onClick = { vm.navigate(Screen.Talk) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoachOrb(OrbState.Idle, size = 64.dp, reduceMotion = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Talk with your coach", style = MaterialTheme.typography.titleMedium)
                    Text("A real conversation, with tips as you go.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        SectionLabel("Practise one skill")
        ExerciseType.practice.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> SkillTile(vm, t, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        SoftCard(onClick = { vm.navigate(Screen.Helper) }, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Help me say it", style = MaterialTheme.typography.titleMedium)
                    Text("Say your idea any way. Get a clear sentence back.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SkillTile(vm: AppViewModel, t: ExerciseType, modifier: Modifier) {
    val locked = t.needsAi && !vm.aiReady
    val level = vm.prefs.level(t)
    SoftCard(modifier = modifier, onClick = { vm.navigate(Screen.Practice(t)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.emoji, fontSize = 30.sp)
            Text(t.title, style = MaterialTheme.typography.titleSmall, minLines = 2)
            if (locked) Text("Needs the coach", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(6) { i ->
                    Box(Modifier.size(width = 14.dp, height = 6.dp).background(
                        if (i < level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

@Composable
fun CoachSetupBanner(vm: AppViewModel) {
    if (vm.prefs.engine == com.simplespeak.app.data.AiEngine.MAC) {
        SoftCard(color = MaterialTheme.colorScheme.tertiaryContainer, onClick = { vm.navigate(Screen.Caregiver) }) {
            Column {
                Text(if (vm.mac.isConfigured) "The Mac is off" else "Connect the speaking coach", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(if (vm.mac.isConfigured) "Turn on the Mac and open Ollama, or download the phone model in the Caregiver tab." else "A caregiver can connect it to the Mac in the Caregiver tab.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        return
    }
    val st = vm.localModel.status
    SoftCard(color = MaterialTheme.colorScheme.tertiaryContainer, onClick = { vm.navigate(Screen.Caregiver) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (st is LocalModel.Status.Downloading) "Downloading the coach… ${(st.fraction * 100).toInt()}%" else "Set up the speaking coach",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Conversations and smart feedback need it. Ask a caregiver.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
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
                Text("${type.emoji}  ${type.title}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                StepBar(s.plan.size, s.index)
            }
            IconButton(onClick = { confirmQuit = true }) { Icon(Icons.Rounded.Close, contentDescription = "Stop practice") }
        }
        Column(Modifier.weight(1f)) {
            key(s.index) { Page { Exercise(vm, type, onNext = vm::completeStep) } }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Stop practice?") },
            text = { Text("Your answers so far are saved, and they count towards today's streak.") },
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
fun FinishedScreen(vm: AppViewModel, done: Int, correct: Int, stars: Int, streak: Int, streakGrew: Boolean) {
    BackHandler { vm.navigate(Screen.Home) }
    val extra = LocalExtraColors.current
    val pop = remember { Animatable(if (vm.prefs.reduceMotion) 1f else 0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val nextMilestone = streakMilestones.firstOrNull { it.days > streak }
    Page {
        Spacer(Modifier.height(16.dp))
        HeroCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.scale(pop.value)) {
                    StreakFlame(streak, color = androidx.compose.ui.graphics.Color.White, textColor = extra.heroText)
                }
                Text(
                    if (streakGrew) "Your streak grew to $streak ${if (streak == 1) "day" else "days"}!" else "Practice finished",
                    style = MaterialTheme.typography.headlineSmall, color = extra.heroText, textAlign = TextAlign.Center,
                )
                nextMilestone?.let {
                    Text("${it.days - streak} more ${if (it.days - streak == 1) "day" else "days"} to unlock “${it.title}”.",
                        style = MaterialTheme.typography.bodyMedium, color = extra.heroText.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                }
            }
        }
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You finished all $done steps.", style = MaterialTheme.typography.titleMedium)
                Text("$correct were right the first time.", style = MaterialTheme.typography.bodyLarge)
                Text("+$stars ⭐   ·   ${vm.prefs.totalStars} stars in total", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GradientButton("Back to home", onClick = { vm.navigate(Screen.Home) }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { vm.navigate(Screen.Talk) }, modifier = Modifier.fillMaxWidth()) { Text("Keep going: talk with your coach") }
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
    var round by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun run(text: String) {
        thought = text; working = true; error = null
        scope.launch {
            try { result = vm.coach.clarify(text) } catch (e: CoachException) { error = e.message }
            working = false
        }
    }

    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Help me say it", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.navigate(Screen.Home) }) { Text("Done") }
        }
        if (!vm.aiReady) {
            Text("This helper needs the speaking coach. A caregiver can set it up in the Caregiver tab.", style = MaterialTheme.typography.bodyLarge)
            return@Page
        }
        Text("Say your idea in any way. It does not need to be in order.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            working -> Thinking(vm, "Making it clear…")
            result != null -> {
                val (clear, short) = result!!
                thought?.let { SectionLabel("You said"); Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                SectionLabel("Clear way to say it")
                SoftCard(color = LocalExtraColors.current.successContainer) { Text(clear, style = MaterialTheme.typography.headlineSmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListenButton(vm, clear)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(clear)) }) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy") }
                }
                if (short != clear) {
                    SectionLabel("Short version")
                    SoftCard { Text(short, style = MaterialTheme.typography.titleLarge) }
                    ListenButton(vm, short, "Listen to short version")
                }
                GradientButton("Say something else", onClick = { result = null; thought = null; round++ }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                key(round) { SpeakInput(vm, onAnswer = { run(it.text) }, label = "Tap and say your idea") }
                error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
