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
    MAC("Mac", "Fast. Runs on the Mac with Ollama, over the home Wi-Fi."),
    LOCAL("Phone", "Slower, but works anywhere. Runs on this phone."),
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

/** Word grades for each speaking skill (no numbers shown to the learner). */
enum class Grade(val key: String, val label: String, val points: Int) {
    EXCELLENT("excellent", "Excellent", 4),
    VERY_GOOD("very_good", "Very good", 3),
    GOOD("good", "Good", 2),
    NEEDS_WORK("needs_work", "Needs work", 1);

    companion object {
        fun of(key: String?): Grade = entries.firstOrNull { it.key == key } ?: GOOD
        /** The grade closest to an average of points. */
        fun average(grades: List<Grade>): Grade {
            if (grades.isEmpty()) return GOOD
            val avg = grades.sumOf { it.points } / grades.size.toDouble()
            return entries.minBy { kotlin.math.abs(it.points - avg) }
        }
    }
}

data class SkillGrades(val grammar: Grade, val structure: Grade, val vocabulary: Grade, val clarity: Grade) {
    val all: List<Pair<String, Grade>> get() = listOf("Grammar" to grammar, "Sentence structure" to structure, "Vocabulary" to vocabulary, "Clarity" to clarity)
    val overall: Grade get() = Grade.average(listOf(grammar, structure, vocabulary, clarity))
}
