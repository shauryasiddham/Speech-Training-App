package com.simplespeak.app.data

enum class ExerciseType(val title: String, val short: String, val emoji: String, val needsAi: Boolean, val spoken: Boolean) {
    ANSWER("Answer the question", "Answer", "💬", true, true),
    FIX("Fix the sentence", "Fix", "🛠️", false, true),
    JOIN("Join the ideas", "Join", "🔗", false, true),
    SEQUENCE("Tell it in order", "Order", "🔢", false, true),
    PICTURE("Describe a picture", "Picture", "🖼️", true, true),
    PLAN("Plan my sentence", "Plan", "🗺️", false, true),
    BUILD("Build a sentence", "Build", "🧩", false, false),
    /** Conversation turns with the coach (not part of sessions). */
    TALK("Conversation", "Talk", "🗣️", true, true),
    ;

    companion object {
        /** The exercises that appear in sessions and on the Home grid. */
        val practice: List<ExerciseType> get() = entries.filter { it != TALK }
    }
}

enum class AgeGroup(val label: String) {
    CHILD("Child"),
    TEEN("Teen"),
    ADULT("Adult"),
}

enum class AiEngine(val label: String, val description: String) {
    LOCAL("On this phone", "Free and private. Uses the Gemma model downloaded to the phone."),
    CLAUDE("Claude (online)", "Needs internet and a Claude API key. Small cost per answer."),
}

/** Grammar areas the coach can tag. Keys are stored; labels are shown. */
object MistakeTypes {
    val labels = linkedMapOf(
        "word_order" to "Word order",
        "verb_tense" to "Verb tense (past / present / future)",
        "subject_verb_agreement" to "Verb agreement (he is / they are)",
        "pronoun" to "Pronouns (I / me / he / him)",
        "missing_word" to "Missing small words (to, the, is)",
        "extra_word" to "Extra words",
        "plural" to "Plurals",
        "article" to "a / an / the",
        "preposition" to "Place and time words (in, on, at)",
        "question_form" to "Asking questions",
        "incomplete_sentence" to "Incomplete sentence",
        "other" to "Other",
    )
    val keys: List<String> get() = labels.keys.toList()
    fun label(key: String) = labels[key] ?: key
}

/** How he spoke, measured from the microphone. */
data class Fluency(
    /** Milliseconds from the microphone opening to his first word. */
    val startMs: Long?,
    /** Milliseconds he spent speaking. */
    val speakMs: Long?,
    val words: Int,
) {
    val wordsPerMinute: Int?
        get() = speakMs?.takeIf { it > 1500 }?.let { (words * 60_000L / it).toInt() }
}

data class Attempt(
    val time: Long,
    val type: ExerciseType,
    val level: Int,
    val prompt: String,
    val answer: String,
    val correct: Boolean,
    val mistakes: List<String>,
    val seconds: Int,
    val fluency: Fluency? = null,
    val audioPath: String? = null,
)

data class Recording(
    val id: String,
    val time: Long,
    val sentence: String,
    val path: String,
    /** true = his own spoken answer, false = him repeating the clear sentence. */
    val answer: Boolean = false,
)

data class Photo(
    val id: String,
    val path: String,
    val caption: String,
)

data class BetterWord(val insteadOf: String, val tryWord: String)

/** Result shown in the feedback card after the learner answers. */
data class Feedback(
    val correct: Boolean,
    val praise: String,
    val learnerText: String,
    val improved: String,
    val tip: String,
    val mistakes: List<String>,
    val betterWords: List<BetterWord> = emptyList(),
    val organization: String = "",
    /** The same idea said at a more advanced level. */
    val nextLevel: String = "",
    val note: String? = null,
)
