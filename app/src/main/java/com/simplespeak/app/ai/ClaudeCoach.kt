package com.simplespeak.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PaymentRequiredException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.simplespeak.app.data.AgeGroup
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.data.MistakeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.util.Base64

class CoachException(message: String) : Exception(message)

/**
 * Talks to Claude to judge the learner's sentences and to help turn ideas into clear sentences.
 * All calls run off the main thread and return plain Kotlin objects.
 */
class ClaudeCoach(
    private val apiKeyProvider: () -> String,
    private val baseUrl: String? = null, // only set in tests
) {

    private var cachedKey: String? = null
    private var client: AnthropicClient? = null

    val isConfigured: Boolean get() = apiKeyProvider().isNotBlank()

    private fun client(): AnthropicClient {
        val key = apiKeyProvider()
        if (key.isBlank()) throw CoachException("No Claude API key yet. A caregiver can add one in the Caregiver tab.")
        if (client == null || cachedKey != key) {
            client?.close()
            client = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .apply { if (baseUrl != null) baseUrl(baseUrl) }
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build()
            cachedKey = key
        }
        return client!!
    }

    // ---- Public tasks -----------------------------------------------------------

    /** Checks a "fix the sentence" answer. */
    suspend fun checkFix(broken: String, expected: List<String>, said: String, age: AgeGroup): Feedback {
        val prompt = """
            Exercise: the learner was shown a sentence with a grammar mistake and asked to say it correctly.
            Sentence with mistake: "$broken"
            Example correct answers: ${expected.joinToString(" | ") { "\"$it\"" }}
            What the learner said: "$said"

            Mark it correct if the learner's sentence is grammatical and keeps the meaning, even if it is
            worded differently from the examples. "improved" must be a correct version that stays as close
            as possible to what the learner said.
        """.trimIndent()
        return feedback(prompt, said, age, image = null)
    }

    /** Checks a spoken description of a built-in emoji scene or a caregiver photo. */
    suspend fun checkPicture(sceneDescription: String?, photo: File?, caption: String?, said: String, age: AgeGroup): Feedback {
        val prompt = buildString {
            appendLine("Exercise: the learner looked at a picture and described it in their own words.")
            if (sceneDescription != null) appendLine("The picture shows: \"$sceneDescription\"")
            if (photo != null) appendLine("The picture is attached." + if (!caption.isNullOrBlank()) " The caregiver's note about it: \"$caption\"" else "")
            appendLine("What the learner said: \"$said\"")
            appendLine()
            appendLine("Judge the grammar and whether it is a full, clear sentence that fits the picture. Small details")
            appendLine("do not need to match. Mark it correct when the grammar is right and it makes sense for the picture.")
            appendLine("\"improved\" must keep the learner's own ideas and words, fixed into one or two clear sentences.")
        }
        return feedback(prompt, said, age, image = photo)
    }

    /** Turns Who / What / Where / When / Why answers into one clear sentence. */
    suspend fun sentenceFromPlan(parts: Map<String, String>, age: AgeGroup): String {
        val filled = parts.filterValues { it.isNotBlank() }.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val prompt = """
            The learner planned a sentence by answering these questions (from speech recognition, so ignore
            small transcription errors):
            $filled

            Write ONE clear, grammatical sentence that uses only their ideas. Use the first person if the
            "Who" is the learner (for example "me" or "I"). Do not add new facts. Keep it simple and natural.
        """.trimIndent()
        val json = ask(prompt, age, SENTENCE_SCHEMA, image = null)
        return json.optString("sentence").ifBlank { throw CoachException("Claude did not return a sentence.") }
    }

    /** "Help me say it": rewrites a messy spoken thought as clear sentences. */
    suspend fun clarify(thought: String, age: AgeGroup): Pair<String, String> {
        val prompt = """
            The learner spoke an idea the way it came to them (from speech recognition):
            "$thought"

            Rewrite it so they can say it clearly to another person.
            - "clear": one to three short, grammatical sentences in the first person that keep every idea and
              add nothing new.
            - "short": the same message in one short, simple sentence.
            If the idea is unclear, make the most likely reading and keep it neutral.
        """.trimIndent()
        val json = ask(prompt, age, CLARIFY_SCHEMA, image = null)
        val clear = json.optString("clear").ifBlank { throw CoachException("Claude did not return a sentence.") }
        return clear to json.optString("short").ifBlank { clear }
    }

    /** Small request used by the Caregiver tab to check the key works. */
    suspend fun testConnection(): String {
        val json = ask("Reply with the sentence: \"The connection works.\"", AgeGroup.ADULT, SENTENCE_SCHEMA, image = null)
        return json.optString("sentence", "The connection works.")
    }

    // ---- Internals --------------------------------------------------------------

    private suspend fun feedback(prompt: String, said: String, age: AgeGroup, image: File?): Feedback {
        val json = ask(prompt, age, FEEDBACK_SCHEMA, image)
        val mistakes = json.optJSONArray("mistakes")?.let { arr -> List(arr.length()) { arr.getString(it) } }
            ?.filter { it in MistakeTypes.keys } ?: emptyList()
        val correct = json.optBoolean("correct", false)
        return Feedback(
            correct = correct,
            praise = json.optString("praise").ifBlank { if (correct) "That is right." else "Good try." },
            learnerText = said,
            improved = json.optString("improved").ifBlank { said },
            tip = if (correct) "" else json.optString("tip"),
            mistakes = if (correct) emptyList() else mistakes,
        )
    }

    private suspend fun ask(prompt: String, age: AgeGroup, schema: Map<String, Any>, image: File?): JSONObject =
        withContext(Dispatchers.IO) {
            val content = buildList {
                if (image != null) {
                    val b64 = Base64.getEncoder().encodeToString(image.readBytes())
                    add(
                        BetaContentBlockParam.ofImage(
                            BetaBase64ImageSource.builder()
                                .data(b64)
                                .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                                .build()
                        )
                    )
                }
                add(BetaContentBlockParam.ofText(prompt))
            }

            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(4000L)
                .system(systemPrompt(age))
                .addUserMessageOfContentBlockParams(content)
                .outputConfig(
                    BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(
                            BetaJsonOutputFormat.builder()
                                .schema(
                                    BetaJsonOutputFormat.Schema.builder()
                                        .putAllAdditionalProperties(schema.mapValues { JsonValue.from(it.value) })
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                // If Claude declines a request, let the API retry it on a suitable fallback model.
                .addBeta("server-side-fallback-2026-07-01")
                .fallbacksDefault()
                .build()

            val message = try {
                client().beta().messages().create(params)
            } catch (e: UnauthorizedException) {
                throw CoachException("The Claude API key was not accepted. Please check it in the Caregiver tab.")
            } catch (e: PermissionDeniedException) {
                throw CoachException("This Claude API key is not allowed to do this. Please check it in the Caregiver tab.")
            } catch (e: PaymentRequiredException) {
                throw CoachException("The Claude account needs credit. A caregiver can add it at console.anthropic.com.")
            } catch (e: RateLimitException) {
                throw CoachException("Claude is busy right now. Please wait a moment and try again.")
            } catch (e: OverloadedException) {
                throw CoachException("Claude is busy right now. Please wait a moment and try again.")
            } catch (e: AnthropicServiceException) {
                throw CoachException("Claude had a problem (${e.statusCode()}). Please try again.")
            } catch (e: CoachException) {
                throw e
            } catch (e: Exception) {
                throw CoachException("Could not reach Claude. Please check the internet connection.")
            }

            val stop = message.stopReason().orElse(null)
            if (stop == BetaStopReason.REFUSAL) throw CoachException("Claude could not help with that one. Let's try another.")
            val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            if (stop == BetaStopReason.MAX_TOKENS || text.isBlank()) throw CoachException("Claude's answer was cut off. Please try again.")
            runCatching { JSONObject(text) }.getOrElse { throw CoachException("Claude's answer could not be read. Please try again.") }
        }

    private fun systemPrompt(age: AgeGroup): String = """
        You are the feedback engine inside SimpleSpeak, a practice app for autistic people who think clearly
        but find it hard to turn their ideas into correct, clear spoken sentences. The learner is a ${age.label.lowercase()}.
        ${ageGuidance(age)}

        The learner's words come from speech recognition. Ignore capital letters, punctuation, filler words
        ("um", "uh") and likely mis-hearings of a single word. Judge grammar and clarity, not pronunciation.

        How to write:
        - Calm, kind, plain and literal. Short sentences. No idioms, sarcasm, jokes or figures of speech.
        - Never shame or criticise the person. Mistakes are normal practice.
        - "praise" names one specific thing they did well (for example "You used the word 'because' to give a reason.").
        - "tip" gives one concrete grammar rule using their own sentence as the example. One or two sentences.
        - Keep the learner's meaning and their own words wherever possible. Change only what is needed.
        - You are a practice tool, not a friend or a person. Do not talk about yourself or your feelings,
          and do not use pet names.
        - Grammar labels in "mistakes" must come from the allowed list; use an empty list when correct.
    """.trimIndent()

    private fun ageGuidance(age: AgeGroup) = when (age) {
        AgeGroup.CHILD -> "Use very simple words a young child understands."
        AgeGroup.TEEN -> "Use simple, respectful words. Do not sound childish."
        AgeGroup.ADULT -> "Use simple, respectful adult language. Never sound childish or patronising."
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        private val FEEDBACK_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "correct" to mapOf("type" to "boolean"),
                "praise" to mapOf("type" to "string"),
                "improved" to mapOf("type" to "string"),
                "tip" to mapOf("type" to "string"),
                "mistakes" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string", "enum" to MistakeTypes.keys),
                ),
            ),
            "required" to listOf("correct", "praise", "improved", "tip", "mistakes"),
            "additionalProperties" to false,
        )

        private val SENTENCE_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf("sentence" to mapOf("type" to "string")),
            "required" to listOf("sentence"),
            "additionalProperties" to false,
        )

        private val CLARIFY_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "clear" to mapOf("type" to "string"),
                "short" to mapOf("type" to "string"),
            ),
            "required" to listOf("clear", "short"),
            "additionalProperties" to false,
        )
    }
}
