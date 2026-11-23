package com.simplespeak.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.simplespeak.app.ai.ClaudeCoach
import com.simplespeak.app.data.Attempt
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.Prefs
import com.simplespeak.app.data.ProgressStore
import com.simplespeak.app.speech.Speaker
import com.simplespeak.app.speech.SpeechInput
import com.simplespeak.app.speech.VoiceRecorder

sealed interface Screen {
    data object Setup : Screen
    data object Home : Screen
    data object Session : Screen
    data class Practice(val type: ExerciseType) : Screen
    data object Helper : Screen
    data object Caregiver : Screen
    data class Finished(val done: Int, val correct: Int, val stars: Int) : Screen
}

class SessionState(val plan: List<ExerciseType>) {
    var index by mutableIntStateOf(0)
    var correct by mutableIntStateOf(0)
    val startedAt = System.currentTimeMillis()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val store = ProgressStore(app)
    val coach = ClaudeCoach(apiKeyProvider = { prefs.apiKey })
    val speech = SpeechInput(app)
    val speaker = Speaker(app) { prefs.speechRate }
    val recorder = VoiceRecorder(app)

    var screen by mutableStateOf<Screen>(if (prefs.setupDone) Screen.Home else Screen.Setup)
        private set
    var session by mutableStateOf<SessionState?>(null)
        private set

    /** Bumped whenever stored data changes so the Caregiver tab refreshes. */
    var dataVersion by mutableIntStateOf(0)
        private set

    fun navigate(to: Screen) {
        speech.reset()
        speaker.stop()
        recorder.release()
        screen = to
    }

    fun refresh() { dataVersion++ }

    /** Exercise types that can run right now (AI-only ones need a key). */
    fun availableTypes(): List<ExerciseType> =
        ExerciseType.entries.filter { prefs.isEnabled(it) && (!it.needsAi || coach.isConfigured) }
            .ifEmpty { listOf(ExerciseType.BUILD) }

    /** Same predictable order every time: the enabled types repeated in a fixed cycle. */
    fun startSession() {
        val types = availableTypes()
        val plan = List(prefs.sessionLength) { types[it % types.size] }
        session = SessionState(plan)
        navigate(Screen.Session)
    }

    fun completeStep(correct: Boolean) {
        val s = session ?: return
        if (correct) s.correct++
        if (s.index + 1 >= s.plan.size) {
            prefs.markPracticedToday()
            val stars = s.plan.size // one star for each finished exercise; effort counts, not only correct answers
            prefs.totalStars += stars
            session = null
            navigate(Screen.Finished(s.plan.size, s.correct, stars))
        } else {
            speech.reset()
            speaker.stop()
            recorder.release()
            s.index++
        }
    }

    fun quitSession() {
        session = null
        navigate(Screen.Home)
    }

    /** Saves one answer and updates the adaptive level for that exercise. */
    fun recordAttempt(type: ExerciseType, level: Int, prompt: String, feedback: Feedback, seconds: Int) {
        store.addAttempt(
            Attempt(
                time = System.currentTimeMillis(),
                type = type,
                level = level,
                prompt = prompt,
                answer = feedback.learnerText,
                correct = feedback.correct,
                mistakes = feedback.mistakes,
                seconds = seconds,
            )
        )
        prefs.recordResult(type, feedback.correct)
        refresh()
    }

    private val recent = mutableMapOf<ExerciseType, ArrayDeque<String>>()

    /** Picks an item at random, avoiding the ones shown most recently. */
    fun <T> pick(type: ExerciseType, items: List<T>, id: (T) -> String): T {
        val history = recent.getOrPut(type) { ArrayDeque() }
        val fresh = items.filter { id(it) !in history }.ifEmpty { items }
        val chosen = fresh.random()
        history.addLast(id(chosen))
        while (history.size > (items.size / 2).coerceAtLeast(1)) history.removeFirst()
        return chosen
    }

    fun startRecording() {
        recorder.start(store.newRecordingFile())
    }

    fun stopRecording(sentence: String): Boolean {
        val file = recorder.stop() ?: return false
        store.addRecording(file, sentence)
        refresh()
        return true
    }

    override fun onCleared() {
        speech.destroy()
        speaker.shutdown()
        recorder.release()
    }
}
