package com.simplespeak.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PaymentRequiredException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.util.Base64

/** Optional online engine: Claude through the official Anthropic SDK. */
class ClaudeBackend(
    private val apiKeyProvider: () -> String,
    private val baseUrl: String? = null, // only set in tests
) : LlmBackend {
    override val name = "Claude"

    private var cachedKey: String? = null
    private var client: AnthropicClient? = null

    val isConfigured: Boolean get() = apiKeyProvider().isNotBlank()

    @Synchronized
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

    override suspend fun json(system: String, prompt: String, schema: Map<String, Any>, image: File?, maxTokens: Int, background: Boolean): JSONObject {
        val content = buildList {
            if (image != null) {
                add(
                    BetaContentBlockParam.ofImage(
                        BetaBase64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(image.readBytes()))
                            .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                            .build()
                    )
                )
            }
            add(BetaContentBlockParam.ofText(prompt))
        }
        val params = base(system, schema).addUserMessageOfContentBlockParams(content).build()
        return call(params)
    }

    override fun openChat(system: String): ChatChannel = object : ChatChannel {
        private val history = mutableListOf<Pair<Boolean, String>>() // (fromUser, text)

        override suspend fun send(text: String, schema: Map<String, Any>, onPartial: (String) -> Unit): JSONObject {
            val b = base(system, schema)
            history.forEach { (user, t) -> if (user) b.addUserMessage(t) else b.addAssistantMessage(t) }
            b.addUserMessage(text)
            val json = call(b.build())
            history += true to text
            history += false to json.toString()
            json.optString("reply").takeIf { it.isNotBlank() }?.let(onPartial)
            return json
        }

        override fun close() = history.clear()
    }

    private fun base(system: String, schema: Map<String, Any>): MessageCreateParams.Builder =
        MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4000L)
            .system(system)
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(BetaOutputConfig.Effort.LOW)
                    .format(
                        BetaJsonOutputFormat.builder()
                            .schema(
                                BetaJsonOutputFormat.Schema.builder()
                                    .putAllAdditionalProperties(strictSchema(schema).mapValues { JsonValue.from(it.value) })
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            // If Claude declines a request, let the API retry it on a suitable fallback model.
            .addBeta("server-side-fallback-2026-07-01")
            .fallbacksDefault()

    private suspend fun call(params: MessageCreateParams): JSONObject = withContext(Dispatchers.IO) {
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
        parseJsonObject(text)
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        /** Claude's structured outputs need every object closed with additionalProperties = false. */
        @Suppress("UNCHECKED_CAST")
        fun strictSchema(schema: Map<String, Any>): Map<String, Any> {
            val out = schema.mapValues { (k, v) ->
                when {
                    k == "properties" && v is Map<*, *> -> (v as Map<String, Any>).mapValues { strictSchema(it.value as Map<String, Any>) }
                    k == "items" && v is Map<*, *> -> strictSchema(v as Map<String, Any>)
                    else -> v
                }
            }.toMutableMap()
            if (out["type"] == "object") out["additionalProperties"] = false
            return out
        }
    }
}
