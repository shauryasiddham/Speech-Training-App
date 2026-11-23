package com.simplespeak.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplespeak.app.ai.Coach
import com.simplespeak.app.ai.JoinTask
import com.simplespeak.app.ai.LearnerProfile
import com.simplespeak.app.ai.FallbackBackend
import com.simplespeak.app.ai.LocalModel
import com.simplespeak.app.ai.OllamaBackend
import com.simplespeak.app.ai.SequenceTask
import com.simplespeak.app.data.AiEngine
import com.simplespeak.app.data.Attempt
import com.simplespeak.app.data.ContentBank
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.Prefs
import com.simplespeak.app.data.ProgressStore
import com.simplespeak.app.data.Stats
import com.simplespeak.app.speech.Speaker
import com.simplespeak.app.speech.SpeechInput
import com.simplespeak.app.speech.SpokenAnswer
import com.simplespeak.app.speech.VoiceRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface Screen {
    data object Setup : Screen
    data object Home : Screen
    data object Talk : Screen
    data object Progress : Screen
    data object Caregiver : Screen
    data object Session : Screen
    data class Practice(val type: ExerciseType) : Screen
    data object Helper : Screen
    data class Finished(val done: Int, val correct: Int, val stars: Int, val streak: Int, val streakGrew: Boolean) : Screen
}

class SessionState(val plan: List<ExerciseType>) {
    var index by mutableIntStateOf(0)
    var correct by mutableIntStateOf(0)
    val startedAt = System.currentTimeMillis()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val store = ProgressStore(app)
    val speech = SpeechInput(app)
    val speaker = Speaker(app) { prefs.speechRate }
    val recorder = VoiceRecorder(app)
    val localModel = LocalModel(app, prefs)
    val mac = OllamaBackend(urlProvider = { prefs.macUrl }, modelProvider = { prefs.macModel })

    /** null = not checked yet, true = the Mac answered, false = it couldn't be reached. */
    var macReachable by mutableStateOf<Boolean?>(null)
        private set

    /** The phone's own model, used when the Mac is away (if it is downloaded and allowed). */
    private val phoneFallback: () -> LocalModel? = { localModel.takeIf { prefs.phoneWhenMacAway && it.isDownloaded } }
    private val macWithFallback = FallbackBackend(mac, phoneFallback) { ok ->
        if (macReachable != ok) macReachable = ok
        if (!ok) rediscoverMac()
    }

    val coach = Coach(
        backendProvider = {
            when (prefs.engine) {
                AiEngine.MAC -> if (mac.isConfigured) macWithFallback else phoneFallback()
                AiEngine.LOCAL -> localModel.takeIf { it.isDownloaded }
            }
        },
        profileProvider = {
            LearnerProfile(prefs.learnerName, prefs.ageGroup, prefs.interests, Stats.progressNotes(store.attempts(), prefs.streak))
        },
    )

    var screen by mutableStateOf<Screen>(if (prefs.setupDone) Screen.Home else Screen.Setup)
        private set
    var session by mutableStateOf<SessionState?>(null)
        private set

    /** Bumped whenever stored data or settings change so screens refresh. */
    var dataVersion by mutableIntStateOf(0)
        private set

    private var pollJob: Job? = null

    init {
        speech.onCaptureFailed = { prefs.answerCaptureBroken = true }
        if (localModel.status is LocalModel.Status.Downloading) watchDownload()
    }

    fun navigate(to: Screen) {
        speech.reset()
        speaker.stop()
        recorder.release()
        screen = to
    }

    fun refresh() { dataVersion++ }

    // ---- Mac ------------------------------------------------------------------------------

    /** Checks whether the Mac answers. Screens call this every 20 seconds, and when the app comes back to the front. */
    fun checkMac() {
        if (prefs.engine != AiEngine.MAC || !mac.isConfigured) return
        viewModelScope.launch {
            val ok = mac.ping()
            macReachable = ok
            if (!ok) rediscoverMac()
        }
    }

    private var rediscovering = false
    private var lastRediscover = 0L

    /**
     * The Mac's address can change when the router restarts; look for it again on the Wi-Fi.
     * At most once every 5 minutes, so a Mac that is simply switched off isn't searched for constantly.
     */
    private fun rediscoverMac() {
        val now = System.currentTimeMillis()
        if (rediscovering || now - lastRediscover < 5 * 60_000) return
        lastRediscover = now
        rediscovering = true
        viewModelScope.launch {
            mac.findOnNetwork()?.let { url ->
                if (url != OllamaBackend.normalizeUrl(prefs.macUrl)) { prefs.macUrl = url; refresh() }
                macReachable = mac.ping()
            }
            rediscovering = false
        }
    }

    val aiReady: Boolean get() = coach.isAvailable

    /** Where the coach is running right now, for the badge on Home, Talk and Caregiver. */
    enum class CoachWhere { MAC, MAC_CHECKING, PHONE, PHONE_BECAUSE_MAC_AWAY, OFF }

    val coachWhere: CoachWhere
        get() = when (prefs.engine) {
            AiEngine.LOCAL -> if (localModel.isDownloaded) CoachWhere.PHONE else CoachWhere.OFF
            AiEngine.MAC -> when {
                !mac.isConfigured -> if (phoneFallback() != null) CoachWhere.PHONE_BECAUSE_MAC_AWAY else CoachWhere.OFF
                macReachable == true -> CoachWhere.MAC
                macReachable == null -> CoachWhere.MAC_CHECKING
                phoneFallback() != null -> CoachWhere.PHONE_BECAUSE_MAC_AWAY
                else -> CoachWhere.OFF
            }
        }

    // ---- On-phone model ------------------------------------------------------------------

    fun downloadModel(wifiOnly: Boolean): String? {
        val err = localModel.startDownload(wifiOnly)
        if (err == null) watchDownload()
        return err
    }

    private fun watchDownload() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                localModel.refresh()
                if (localModel.status !is LocalModel.Status.Downloading) break
                delay(1000)
            }
            refresh()
        }
    }

    // ---- Speech ------------------------------------------------------------------------

    /** Opens the microphone. When [saveAnswer] is set and allowed, his spoken answer is kept for the caregiver. */
    fun startListening(saveAnswer: Boolean, onAnswer: (SpokenAnswer) -> Unit) {
        val file = if (saveAnswer && prefs.saveAnswers && !prefs.answerCaptureBroken) store.newRecordingFile("wav") else null
        speech.start(file, onAnswer)
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

    // ---- Sessions -------------------------------------------------------------------------

    /** Exercise types that can run right now (AI-only ones need the coach). */
    fun availableTypes(): List<ExerciseType> =
        ExerciseType.practice.filter { prefs.isEnabled(it) && (!it.needsAi || aiReady) }.ifEmpty { listOf(ExerciseType.FIX) }

    /** Same predictable order every time: the enabled types in a fixed cycle. */
    fun sessionPlan(): List<ExerciseType> {
        val types = availableTypes()
        return List(prefs.sessionLength) { types[it % types.size] }
    }

    fun startSession() {
        session = SessionState(sessionPlan())
        navigate(Screen.Session)
    }

    fun completeStep(correct: Boolean) {
        val s = session ?: return
        if (correct) s.correct++
        if (s.index + 1 >= s.plan.size) {
            val before = prefs.streak
            prefs.markPracticedToday()
            val stars = s.plan.size + s.correct
            prefs.totalStars += stars
            session = null
            navigate(Screen.Finished(s.plan.size, s.correct, stars, prefs.streak, prefs.streak > before))
        } else {
            speech.reset(); speaker.stop(); recorder.release()
            s.index++
        }
    }

    fun quitSession() {
        session = null
        navigate(Screen.Home)
    }

    /** Saves one answer, its fluency and audio, and updates the adaptive level and streak. */
    fun recordAttempt(type: ExerciseType, level: Int, prompt: String, feedback: Feedback, seconds: Int, spoken: SpokenAnswer?) {
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
                fluency = spoken?.fluency,
                audioPath = spoken?.audio?.absolutePath,
            )
        )
        spoken?.audio?.let { store.addRecording(it, feedback.learnerText, answer = true) }
        prefs.recordResult(type, feedback.correct)
        prefs.markPracticedToday()
        refresh()
    }

    // ---- Fresh content from the coach ---------------------------------------------------------
    // Levels 1–3 mix built-in and AI-written items; levels 4–6 are written by the AI.
    // The next item is prepared in the background while he reads his feedback.

    private val sentenceQ = mutableMapOf<Int, ArrayDeque<String>>()
    private val joinQ = mutableMapOf<Int, ArrayDeque<JoinTask>>()
    private val questionQ = mutableMapOf<Int, ArrayDeque<String>>()
    private val sequenceQ = ArrayDeque<SequenceTask>()
    private val refilling = mutableSetOf<String>()

    private fun useAi(level: Int) = aiReady && (level > ContentBank.MAX_BUILT_IN_LEVEL || Random.nextInt(3) > 0)

    private fun prefetch(key: String, needed: () -> Boolean, block: suspend () -> Unit) {
        if (!aiReady || key in refilling || !needed()) return
        refilling += key
        viewModelScope.launch {
            delay(1500) // let the current screen settle first
            runCatching { block() }
            refilling -= key
        }
    }

    /** An AI-written correct sentence at this level, or null to use built-in content. */
    suspend fun nextSentence(level: Int): String? {
        if (!useAi(level)) return null
        val q = sentenceQ.getOrPut(level) { ArrayDeque() }
        if (q.isEmpty()) runCatching { q.addAll(coach.generateSentences(level, 3, background = false)) }
        val s = q.removeFirstOrNull()
        prefetch("s$level", { q.size < 2 }) { q.addAll(coach.generateSentences(level, 3)) }
        return s
    }

    suspend fun nextJoin(level: Int): JoinTask? {
        if (!useAi(level)) return null
        val q = joinQ.getOrPut(level) { ArrayDeque() }
        if (q.isEmpty()) runCatching { q.addAll(coach.generateJoins(level, background = false)) }
        val j = q.removeFirstOrNull()
        prefetch("j$level", { q.isEmpty() }) { q.addAll(coach.generateJoins(level)) }
        return j
    }

    suspend fun nextQuestion(level: Int): String? {
        if (!useAi(level)) return null
        val q = questionQ.getOrPut(level) { ArrayDeque() }
        if (q.isEmpty()) runCatching { q.addAll(coach.generateQuestions(level, background = false)) }
        val s = q.removeFirstOrNull()
        prefetch("q$level", { q.size < 2 }) { q.addAll(coach.generateQuestions(level)) }
        return s
    }

    suspend fun nextSequence(): SequenceTask? {
        if (!aiReady || Random.nextInt(3) == 0) return null
        if (sequenceQ.isEmpty()) runCatching { coach.generateSequence(background = false)?.let { sequenceQ.addLast(it) } }
        val s = sequenceQ.removeFirstOrNull()
        prefetch("seq", { sequenceQ.isEmpty() }) { coach.generateSequence()?.let { sequenceQ.addLast(it) } }
        return s
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

    override fun onCleared() {
        speech.destroy()
        speaker.shutdown()
        recorder.release()
    }
}
