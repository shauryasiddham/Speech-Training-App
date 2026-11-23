package com.simplespeak.app.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.unit.dp
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.speech.SpokenAnswer
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.CoachOrb
import com.simplespeak.app.ui.GradientButton
import com.simplespeak.app.ui.HighlightedSentence
import com.simplespeak.app.ui.LocalExtraColors
import com.simplespeak.app.ui.OrbState
import com.simplespeak.app.ui.SectionLabel
import com.simplespeak.app.ui.SoftCard
import com.simplespeak.app.ui.SpeakInput
import kotlinx.coroutines.launch

private data class Topic(val title: String, val emoji: String, val opener: (String) -> String)

private fun topics(interests: String): List<Topic> = buildList {
    add(Topic("Anything you like", "✨") { n -> "Hi${if (n.isNotBlank()) " $n" else ""}. What would you like to talk about today?" })
    add(Topic("Your day", "☀️") { "How has your day been so far? Tell me what you have done." })
    add(Topic("Your weekend", "🎉") { "What did you do last weekend, or what are you planning for the next one?" })
    interests.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(3).forEach { i ->
        add(Topic(i.replaceFirstChar { it.uppercase() }, "⭐") { "I'd like to hear about $i. What do you like most about it?" })
    }
    add(Topic("Food and cooking", "🍝") { "What is a meal you really enjoy? Tell me about it." })
    add(Topic("A film, show or game", "🎬") { "Tell me about a film, show or game you like, and why you like it." })
    add(Topic("Explain how something works", "⚙️") { "Pick something you know well and explain to me how it works." })
    add(Topic("Your opinion", "💭") { "Is there something you have a strong opinion about? Tell me what you think and why." })
    add(Topic("Plans and goals", "🎯") { "What is something you would like to do or get better at in the future?" })
}

private data class ChatMessage(
    val fromCoach: Boolean,
    val text: String,
    val improved: String? = null,
    val tip: String? = null,
    val correct: Boolean? = null,
)

@Composable
fun TalkScreen(vm: AppViewModel) {
    var topic by remember { mutableStateOf<Topic?>(null) }
    if (!vm.aiReady) {
        Page {
            Text("Talk with your coach", style = MaterialTheme.typography.headlineMedium)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CoachOrb(OrbState.Idle, size = 140.dp) }
            Text("The coach needs to be set up first. A caregiver can download it in the Caregiver tab.", style = MaterialTheme.typography.bodyLarge)
            CoachSetupBanner(vm)
        }
        return
    }
    val t = topic
    if (t == null) TopicPicker(vm) { topic = it } else ChatView(vm, t, onEnd = { topic = null })
}

@Composable
private fun TopicPicker(vm: AppViewModel, onPick: (Topic) -> Unit) {
    Page {
        Text("Talk with your coach", style = MaterialTheme.typography.headlineMedium)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CoachOrb(OrbState.Idle, size = 130.dp, reduceMotion = vm.prefs.reduceMotion) }
        Text(
            "Have a real conversation. Take a moment to plan, then speak. After each answer you'll see a clearer way to say it.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionLabel("Choose a topic")
        topics(vm.prefs.interests).forEach { tp ->
            SoftCard(onClick = { onPick(tp) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tp.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(14.dp))
                    Text(tp.title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ChatView(vm: AppViewModel, topic: Topic, onEnd: () -> Unit) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var streaming by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val chat = remember { runCatching { vm.coach.startChat(topic.title) }.getOrNull() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val start = remember { System.currentTimeMillis() }

    DisposableEffect(Unit) { onDispose { chat?.close() } }
    LaunchedEffect(Unit) {
        val opener = topic.opener(vm.prefs.learnerName)
        messages += ChatMessage(true, opener)
        if (vm.prefs.speakReplies) vm.speaker.say(opener)
    }
    LaunchedEffect(messages.size, streaming) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }

    fun send(ans: SpokenAnswer) {
        val c = chat ?: return
        val index = messages.size
        messages += ChatMessage(false, ans.text)
        waiting = true; error = null; streaming = ""
        scope.launch {
            try {
                val turn = c.send(ans.text) { partial -> streaming = partial }
                messages[index] = messages[index].copy(improved = turn.improved, tip = turn.tip, correct = turn.correct)
                messages += ChatMessage(true, turn.reply)
                if (vm.prefs.speakReplies) vm.speaker.say(turn.reply)
                vm.recordAttempt(
                    ExerciseType.TALK, 0, topic.title,
                    Feedback(turn.correct, "", ans.text, turn.improved, turn.tip, if (turn.correct) emptyList() else listOf("other")),
                    ((System.currentTimeMillis() - start) / 1000).toInt().coerceAtMost(120), ans,
                )
                if (c.finished) ended = true
            } catch (e: CoachException) {
                error = e.message
            }
            streaming = null; waiting = false; round++
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoachOrb(if (waiting) OrbState.Thinking else OrbState.Idle, size = 40.dp, reduceMotion = vm.prefs.reduceMotion)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Your coach", style = MaterialTheme.typography.titleSmall)
                Text(topic.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { if (messages.count { !it.fromCoach } > 0) ended = true else onEnd() }) { Text("End") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(messages) { m -> Bubble(vm, m) }
            item {
                streaming?.let { s -> if (s.isNotBlank()) Bubble(vm, ChatMessage(true, s)) else ThinkingBubble(vm) }
            }
        }
        Surface(tonalElevation = 2.dp, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    ended -> ChatSummary(vm, messages, onEnd)
                    waiting -> Text("Your coach is replying…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    chat == null -> Text("The coach could not start. Please try again.", style = MaterialTheme.typography.bodyLarge)
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
private fun Bubble(vm: AppViewModel, m: ChatMessage) {
    val extra = LocalExtraColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromCoach) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.Top) {
        if (m.fromCoach) { CoachOrb(OrbState.Idle, size = 30.dp, reduceMotion = true); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (m.fromCoach) Alignment.Start else Alignment.End) {
            Surface(
                onClick = { vm.speaker.say(m.text) },
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (m.fromCoach) 6.dp else 22.dp, bottomEnd = if (m.fromCoach) 22.dp else 6.dp),
                color = if (m.fromCoach) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                contentColor = if (m.fromCoach) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                shadowElevation = if (m.fromCoach) 2.dp else 0.dp,
            ) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
            when (m.correct) {
                true -> Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = extra.success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear and correct", style = MaterialTheme.typography.labelMedium, color = extra.success)
                }
                false -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(top = 6.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel("Clearer")
                        HighlightedSentence(m.text, m.improved.orEmpty(), MaterialTheme.typography.bodyLarge)
                        if (!m.tip.isNullOrBlank()) Text(m.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                null -> {}
            }
        }
    }
}

@Composable
private fun ThinkingBubble(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoachOrb(OrbState.Thinking, size = 30.dp, reduceMotion = vm.prefs.reduceMotion)
        Spacer(Modifier.width(8.dp))
        Text("…", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatSummary(vm: AppViewModel, messages: List<ChatMessage>, onEnd: () -> Unit) {
    val his = messages.filter { !it.fromCoach && it.correct != null }
    val words = his.sumOf { it.text.split(Regex("\\s+")).size }
    val clear = his.count { it.correct == true }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Great conversation.", style = MaterialTheme.typography.titleLarge)
        Text(
            "You answered ${his.size} ${if (his.size == 1) "time" else "times"} and spoke $words words." +
                if (his.isNotEmpty()) " $clear of your answers were clear and correct." else "",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (his.isNotEmpty()) Text(
            "Try one of these ideas in a real conversation today.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientButton("New conversation", onClick = onEnd, modifier = Modifier.fillMaxWidth())
    }
}
