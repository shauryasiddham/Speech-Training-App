package com.simplespeak.app.data

enum class ExerciseType(val title: String, val emoji: String, val needsAi: Boolean) {
    BUILD("Build a sentence", "🧩", false),
    FIX("Fix the sentence", "🛠️", false),
    PICTURE("Describe a picture", "🖼️", true),
    PLAN("Plan my sentence", "🗺️", false),
}

enum class AgeGroup(val label: String) {
    CHILD("Child"),
    TEEN("Teen"),
    ADULT("Adult"),
}

enum class RewardStyle(val label: String, val description: String) {
    STARS("Stars and streak", "A star for each exercise and a count of practice days in a row."),
    PROGRESS("Progress only", "A simple bar that shows how much of the session is done."),
    NONE("No rewards", "Just a calm \"finished\" message at the end."),
}

/** Grammar areas Claude can tag. Keys are stored; labels are shown to the caregiver. */
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

data class Attempt(
    val time: Long,
    val type: ExerciseType,
    val level: Int,
    val prompt: String,
    val answer: String,
    val correct: Boolean,
    val mistakes: List<String>,
    val seconds: Int,
)

data class Recording(
    val id: String,
    val time: Long,
    val sentence: String,
    val path: String,
)

data class Photo(
    val id: String,
    val path: String,
    val caption: String,
)

/** Result shown in the feedback card after the learner answers. */
data class Feedback(
    val correct: Boolean,
    val praise: String,
    val learnerText: String,
    val improved: String,
    val tip: String,
    val mistakes: List<String>,
    val note: String? = null,
)
