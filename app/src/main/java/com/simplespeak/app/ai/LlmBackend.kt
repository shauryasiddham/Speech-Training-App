package com.simplespeak.app.ai

import org.json.JSONObject
import java.io.File

open class CoachException(message: String) : Exception(message)

/** A language model that answers in JSON. Implemented by the Mac (Ollama) and the on-phone model. */
interface LlmBackend {
    val name: String

    /**
     * One request and one JSON reply that follows [schema].
     * [background] requests (preparing the next exercise) give way to anything the learner is waiting for.
     */
    suspend fun json(
        system: String,
        prompt: String,
        schema: Map<String, Any>,
        image: File? = null,
        maxTokens: Int = 500,
        background: Boolean = false,
    ): JSONObject

    /** A multi-turn conversation that remembers earlier turns. */
    fun openChat(system: String): ChatChannel
}

interface ChatChannel {
    /** Sends what he said; [onPartial] receives the reply text so far while it is being written. */
    suspend fun send(text: String, schema: Map<String, Any>, onPartial: (String) -> Unit = {}): JSONObject
    fun close()
}

/** Pulls a JSON object out of model text, even if there are extra words around it. */
internal fun parseJsonObject(text: String): JSONObject {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) throw CoachException("The coach's answer could not be read. Please try again.")
    return runCatching { JSONObject(text.substring(start, end + 1)) }
        .getOrElse { throw CoachException("The coach's answer could not be read. Please try again.") }
}

/** Reads the "reply" field from JSON that is still being written, for live display. */
internal fun partialReply(json: String): String? {
    val m = Regex("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)").find(json) ?: return null
    return m.groupValues[1].replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\")
}
