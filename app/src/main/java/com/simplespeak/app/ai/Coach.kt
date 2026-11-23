package com.simplespeak.app.ai

import com.simplespeak.app.data.AgeGroup
import com.simplespeak.app.data.BetterWord
import com.simplespeak.app.data.ContentBank
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.MistakeTypes
import com.simplespeak.app.data.TextMatch
import org.json.JSONObject
import java.io.File

/** Who the coach is talking to. [progressNotes] are true statements about recent improvement. */
data class LearnerProfile(
    val name: String,
    val age: AgeGroup,
    val interests: String,
    val progressNotes: String,
)

data class ChatTurn(val reply: String, val improved: String, val tip: String, val correct: Boolean)
data class JoinTask(val parts: List<String>, val joined: String)
data class SequenceTask(val title: String, val steps: List<Pair<String, String>>)

/**
 * All the coaching logic and prompts. Works with any [LlmBackend] (the on-phone model or Claude).
 * Prompts are kept short and literal because the on-phone model is small; tested on Gemma 4 E2B.
 */
class Coach(
    private val backendProvider: () -> LlmBackend?,
    private val profileProvider: () -> LearnerProfile,
) {
    val isAvailable: Boolean get() = backendProvider() != null

    private fun backend(): LlmBackend =
        backendProvider() ?: throw CoachException("The speaking coach is not set up yet. A caregiver can set it up in the Caregiver tab.")

    private fun who(p: LearnerProfile): String {
        val age = when (p.age) {
            AgeGroup.CHILD -> "a child"
            AgeGroup.TEEN -> "a teenager"
            AgeGroup.ADULT -> "an adult"
        }
        val name = p.name.ifBlank { "The learner" }
        return "$name is $age who is autistic. He thinks clearly but finds it hard to say his ideas in correct, clear English."
    }

    private fun style(p: LearnerProfile) = when (p.age) {
        AgeGroup.CHILD -> "Use very simple words."
        AgeGroup.TEEN -> "Use simple, respectful words. Do not sound childish."
        AgeGroup.ADULT -> "Use simple, respectful adult language. Never sound childish."
    }

    private fun coachSystem(p: LearnerProfile) = """
        You are the speaking coach in SimpleSpeak. ${who(p)}
        His words come from speech recognition: ignore punctuation, capital letters and filler words like "um".
        Rules: calm, kind, literal and specific. Never shame. Keep his meaning and his own words; change only what is wrong. ${style(p)}
        Write with normal capital letters and punctuation.
        ${if (p.progressNotes.isNotBlank()) "What you know about his progress: ${p.progressNotes} Mention a real improvement in your praise when it fits." else ""}
    """.trimIndent()

    // ---- Feedback on a spoken answer ---------------------------------------------------

    suspend fun feedback(task: String, said: String, references: List<String> = emptyList(), image: File? = null): Feedback {
        val p = profileProvider()
        val prompt = """
            $task
            His answer: "$said"
            ${if (references.isNotEmpty()) "Example correct answers: ${references.joinToString(" | ") { "\"$it\"" }}" else ""}

            Reply in JSON:
            - improved: his answer with ONLY the grammar mistakes fixed. If there are no mistakes, copy his answer exactly.
            - praise: one specific thing he did well, starting with "You".
            - grammar_tip: one short rule that explains the main fix, using his words. Empty string if there were no mistakes.
            - mistakes: the kinds of mistakes you fixed. Empty list if none.
            - better_words: up to 2 richer or more precise words he could use next time (vocabulary, not grammar fixes). Empty list if none fit.
            - organization_tip: one short tip to order or link his ideas better. Empty string if already clear.
            - next_level: the same idea said in a more advanced way, with richer words or better linking (one or two sentences).
        """.trimIndent()
        val json = backend().json(coachSystem(p), prompt, FEEDBACK_SCHEMA, image)
        return parseFeedback(json, said, references)
    }

    internal fun parseFeedback(json: JSONObject, said: String, references: List<String>): Feedback {
        val improved = json.optString("improved").trim().ifBlank { said }.let(::sentenceCase)
        // The small model is unreliable at judging "correct", so decide it here from its own correction.
        val correct = TextMatch.sameSentence(said, improved) || references.any { TextMatch.sameSentence(it, said) }
        val better = json.optJSONArray("better_words")?.let { arr ->
            List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { o ->
                val a = o.optString("instead_of").trim()
                val b = o.optString("try").trim()
                if (a.isBlank() || b.isBlank() || TextMatch.sameSentence(a, b)) null else BetterWord(a, b)
            }
        }.orEmpty().take(2)
        val mistakes = json.optJSONArray("mistakes")?.let { arr -> List(arr.length()) { arr.getString(it) } }
            ?.filter { it in MistakeTypes.keys }?.distinct().orEmpty()
        val nextLevel = json.optString("next_level").trim().let(::sentenceCase)
        return Feedback(
            correct = correct,
            praise = json.optString("praise").trim().let(::sentenceCase).ifBlank { if (correct) "That is right." else "Good try." },
            learnerText = said,
            improved = if (correct) sentenceCase(said) else improved,
            tip = if (correct) "" else json.optString("grammar_tip").trim().let(::sentenceCase),
            mistakes = if (correct) emptyList() else mistakes.ifEmpty { listOf("other") },
            betterWords = better,
            organization = json.optString("organization_tip").trim().let(::sentenceCase),
            nextLevel = nextLevel.takeUnless { it.isBlank() || TextMatch.sameSentence(it, improved) }.orEmpty(),
        )
    }

    // ---- Fresh practice content --------------------------------------------------------

    private fun topics(p: LearnerProfile) =
        "everyday life of ${when (p.age) { AgeGroup.CHILD -> "a child"; AgeGroup.TEEN -> "a teenager"; AgeGroup.ADULT -> "an adult" }}" +
            if (p.interests.isNotBlank()) ", sometimes about his interests: ${p.interests}" else ""

    private val writerSystem = "You write English speaking practice for a speech practice app. Every sentence must be grammatically perfect " +
        "and sound like natural spoken English, not formal writing."

    suspend fun generateSentences(level: Int, count: Int = 3, background: Boolean = true): List<String> {
        val p = profileProvider()
        val prompt = "Write $count different practice sentences. Level $level of 6: ${ContentBank.levelDescriptions[level]}. " +
            "Topics: ${topics(p)}. Use different topics for each sentence. JSON only."
        val json = backend().json(writerSystem, prompt, SENTENCES_SCHEMA, background = background)
        return json.optJSONArray("sentences")?.let { a -> List(a.length()) { a.getString(it).trim() } }.orEmpty()
            .filter { it.split(" ").size >= 3 }.map(::sentenceCase)
    }

    suspend fun generateJoins(level: Int, background: Boolean = true): List<JoinTask> {
        val p = profileProvider()
        val n = if (level >= 4) 3 else 2
        val prompt = "Write 2 practice items. In each item, 'parts' are $n short, simple sentences (each under 10 words) that belong together, and 'joined' is ONE natural sentence " +
            "that joins all of them using linking words such as because, so, but, although, while, which or when. " +
            "Level $level of 6: the joined sentence should be ${ContentBank.levelDescriptions[level]}. Topics: ${topics(p)}. JSON only."
        val json = backend().json(writerSystem, prompt, JOIN_SCHEMA, background = background)
        return json.optJSONArray("items")?.let { a ->
            List(a.length()) { a.getJSONObject(it) }.mapNotNull { o ->
                val parts = o.optJSONArray("parts")?.let { pa -> List(pa.length()) { sentenceCase(pa.getString(it).trim()) } }.orEmpty()
                val joined = sentenceCase(o.optString("joined").trim())
                if (parts.size >= 2 && joined.isNotBlank()) JoinTask(parts, joined) else null
            }
        }.orEmpty()
    }

    suspend fun generateQuestions(level: Int, background: Boolean = true): List<String> {
        val p = profileProvider()
        val kind = when {
            level <= 2 -> "simple questions about his life and day"
            level <= 4 -> "open questions that need a reason, a description or a short story"
            else -> "questions that need an opinion with reasons, a comparison, or an explanation of steps"
        }
        val prompt = "Write 3 different $kind for speaking practice. Topics: ${topics(p)}. Each question must be under 20 words and need more than a yes or no answer. JSON only."
        val json = backend().json(writerSystem, prompt, QUESTIONS_SCHEMA, background = background)
        return json.optJSONArray("questions")?.let { a -> List(a.length()) { a.getString(it).trim() } }.orEmpty().filter { it.length > 8 }
    }

    suspend fun generateSequence(background: Boolean = true): SequenceTask? {
        val p = profileProvider()
        val prompt = "Write a short everyday routine or story with exactly 4 steps in time order. Topics: ${topics(p)}. " +
            "Each step: one emoji and a short phrase of 3 to 6 words. 'title' is 2 to 4 words. JSON only."
        val json = backend().json(writerSystem, prompt, SEQUENCE_SCHEMA, background = background)
        val steps = json.optJSONArray("steps")?.let { a ->
            List(a.length()) { a.getJSONObject(it) }.map { it.optString("emoji").trim().ifBlank { "•" } to sentenceCase(it.optString("text").trim()) }
        }.orEmpty().filter { it.second.isNotBlank() }
        return if (steps.size >= 3) SequenceTask(json.optString("title").trim().ifBlank { "A short story" }, steps.take(4)) else null
    }

    // ---- Helpers -------------------------------------------------------------------------

    suspend fun sentenceFromPlan(parts: Map<String, String>): String {
        val p = profileProvider()
        val filled = parts.filterValues { it.isNotBlank() }.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val prompt = "He planned a sentence by answering these questions:\n$filled\n\n" +
            "Write ONE clear, grammatical sentence that uses only his ideas. Use \"I\" if the Who is him. Do not add new facts. JSON only."
        val json = backend().json(coachSystem(p), prompt, SENTENCE_SCHEMA)
        return sentenceCase(json.optString("sentence").trim()).ifBlank { throw CoachException("The coach did not return a sentence.") }
    }

    suspend fun clarify(thought: String): Pair<String, String> {
        val p = profileProvider()
        val prompt = """
            He spoke an idea the way it came to him: "$thought"
            Rewrite it so he can say it clearly to another person. Keep every idea and add nothing new.
            - clear: one to three short, grammatical sentences, using "I".
            - short: the same message in one short sentence.
        """.trimIndent()
        val json = backend().json(coachSystem(p), prompt, CLARIFY_SCHEMA)
        val clear = sentenceCase(json.optString("clear").trim()).ifBlank { throw CoachException("The coach did not return a sentence.") }
        return clear to sentenceCase(json.optString("short").trim()).ifBlank { clear }
    }

    suspend fun testConnection(): String {
        val json = backend().json("You are a helpful assistant.", "Reply with the sentence: The coach is ready.", SENTENCE_SCHEMA, maxTokens = 40)
        return json.optString("sentence", "The coach is ready.")
    }

    // ---- Conversation ----------------------------------------------------------------------

    inner class Chat(val topic: String) {
        private val channel = backend().openChat(chatSystem(topic))
        var turns = 0
            private set

        suspend fun send(said: String, onPartial: (String) -> Unit): ChatTurn {
            turns++
            val wrapUp = turns >= MAX_CHAT_TURNS
            val prompt = """
                He said: "$said"
                Reply in JSON:
                - reply: your next turn (1 or 2 short sentences). React to what he said${if (wrapUp) ", thank him and say goodbye warmly. Do not ask a question." else " and ask ONE open follow-up question."}
                - improved: what he said with only the grammar fixed (copy it exactly if it was correct).
                - tip: if you fixed something, say the main fix in one short sentence, for example: Use "went", not "go", for the past. Empty string if his answer was correct.
            """.trimIndent()
            val json = channel.send(prompt, CHAT_SCHEMA, onPartial)
            val improved = sentenceCase(json.optString("improved").trim()).ifBlank { said }
            val correct = TextMatch.sameSentence(said, improved)
            return ChatTurn(
                reply = sentenceCase(json.optString("reply").trim()).ifBlank { "Tell me more." },
                improved = if (correct) sentenceCase(said) else improved,
                tip = if (correct) "" else sentenceCase(json.optString("tip").trim()),
                correct = correct,
            )
        }

        val finished: Boolean get() = turns >= MAX_CHAT_TURNS

        fun close() = channel.close()
    }

    fun startChat(topic: String) = Chat(topic)

    private fun chatSystem(topic: String): String {
        val p = profileProvider()
        return """
            You are the speaking coach in SimpleSpeak, having a spoken conversation to help him practise. ${who(p)}
            His words come from speech recognition: ignore punctuation, capital letters and filler words.
            Topic: $topic.
            How to talk: warm, encouraging, calm and literal. Natural, clear sentences with normal punctuation. No sarcasm or idioms.
            Show real interest in what he says. Ask one open question at a time that needs more than a yes or no answer, so he practises planning and explaining his ideas.
            When he says something well, or improves, tell him specifically. ${style(p)}
            ${if (p.progressNotes.isNotBlank()) "His recent progress: ${p.progressNotes}" else ""}
            ${if (p.interests.isNotBlank()) "He likes: ${p.interests}." else ""}
        """.trimIndent()
    }

    companion object {
        const val MAX_CHAT_TURNS = 10

        fun sentenceCase(s: String): String = s.trim().replaceFirstChar { it.uppercase() }

        private val STR = mapOf("type" to "string")

        val FEEDBACK_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to linkedMapOf(
                "improved" to STR,
                "praise" to STR,
                "grammar_tip" to STR,
                "mistakes" to mapOf("type" to "array", "items" to mapOf("type" to "string", "enum" to MistakeTypes.keys)),
                "better_words" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to linkedMapOf("instead_of" to STR, "try" to STR),
                        "required" to listOf("instead_of", "try"),
                    ),
                ),
                "organization_tip" to STR,
                "next_level" to STR,
            ),
            "required" to listOf("improved", "praise", "grammar_tip", "mistakes", "better_words", "organization_tip", "next_level"),
        )

        val CHAT_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to linkedMapOf("reply" to STR, "improved" to STR, "tip" to STR),
            "required" to listOf("reply", "improved", "tip"),
        )

        val SENTENCE_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf("sentence" to STR),
            "required" to listOf("sentence"),
        )

        val CLARIFY_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to linkedMapOf("clear" to STR, "short" to STR),
            "required" to listOf("clear", "short"),
        )

        val SENTENCES_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf("sentences" to mapOf("type" to "array", "items" to STR)),
            "required" to listOf("sentences"),
        )

        val QUESTIONS_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf("questions" to mapOf("type" to "array", "items" to STR)),
            "required" to listOf("questions"),
        )

        val JOIN_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "items" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to linkedMapOf("parts" to mapOf("type" to "array", "items" to STR), "joined" to STR),
                        "required" to listOf("parts", "joined"),
                    ),
                ),
            ),
            "required" to listOf("items"),
        )

        val SEQUENCE_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to linkedMapOf(
                "title" to STR,
                "steps" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to linkedMapOf("emoji" to STR, "text" to STR),
                        "required" to listOf("emoji", "text"),
                    ),
                ),
            ),
            "required" to listOf("title", "steps"),
        )
    }
}
