package com.simplespeak.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Thrown when the Mac can't be reached at all (off, asleep, or a different network). */
class MacUnreachableException(message: String) : CoachException(message)

/**
 * The speaking coach running on a Mac on the home Wi-Fi, through Ollama's HTTP API.
 * The Mac does the heavy work, so replies are much faster than on the phone.
 */
class OllamaBackend(
    private val urlProvider: () -> String,
    private val modelProvider: () -> String,
) : LlmBackend {
    override val name = "Ollama on the Mac"

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val quick = client.newBuilder().connectTimeout(700, TimeUnit.MILLISECONDS).readTimeout(2, TimeUnit.SECONDS).build()

    val isConfigured: Boolean get() = urlProvider().isNotBlank()

    private fun base(): String = normalizeUrl(urlProvider()).ifBlank {
        throw MacUnreachableException("The Mac's address isn't set yet. A caregiver can add it in the Caregiver tab.")
    }

    private fun body(model: String, messages: JSONArray, schema: Map<String, Any>, stream: Boolean, maxTokens: Int) = JSONObject()
        .put("model", model)
        .put("messages", messages)
        .put("stream", stream)
        .put("format", JSONObject(schema))
        .put("think", false) // faster replies; the prompts don't need step-by-step reasoning
        .put("keep_alive", KEEP_ALIVE)
        .put("options", JSONObject().put("temperature", 0.4).put("num_ctx", 4096).put("num_predict", maxTokens))
        .toString()
        .toRequestBody(JSON)

    private fun message(role: String, content: String, image: File? = null) = JSONObject().put("role", role).put("content", content).apply {
        if (image != null) put("images", JSONArray().put(Base64.getEncoder().encodeToString(image.readBytes())))
    }

    private fun <T> call(request: Request, read: (okhttp3.Response) -> T): T {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw MacUnreachableException("Can't reach the Mac. Check that it is on, awake, and on the same Wi-Fi.")
        }
        response.use { r ->
            if (r.code == 404) throw CoachException("The Mac doesn't have the model “${modelProvider()}”. Check the model in the Caregiver tab.")
            if (!r.isSuccessful) throw CoachException("The Mac's coach had a problem (${r.code}). Please try again.")
            return try { read(r) } catch (e: IOException) {
                throw MacUnreachableException("The connection to the Mac was lost. Please try again.")
            }
        }
    }

    override suspend fun json(system: String, prompt: String, schema: Map<String, Any>, image: File?, maxTokens: Int, background: Boolean): JSONObject =
        withContext(Dispatchers.IO) {
            val messages = JSONArray().put(message("system", system)).put(message("user", prompt, image))
            val req = Request.Builder().url("${base()}/api/chat").post(body(modelProvider(), messages, schema, false, maxTokens)).build()
            val text = call(req) { r -> JSONObject(r.body!!.string()).optJSONObject("message")?.optString("content").orEmpty() }
            parseJsonObject(text)
        }

    override fun openChat(system: String): ChatChannel = object : ChatChannel {
        private val history = JSONArray().put(message("system", system))

        override suspend fun send(text: String, schema: Map<String, Any>, onPartial: (String) -> Unit): JSONObject = withContext(Dispatchers.IO) {
            val messages = JSONArray(history.toString()).put(message("user", text))
            val req = Request.Builder().url("${base()}/api/chat").post(body(modelProvider(), messages, schema, true, 400)).build()
            val content = call(req) { r ->
                val sb = StringBuilder()
                val source = r.body!!.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val chunk = JSONObject(line)
                    sb.append(chunk.optJSONObject("message")?.optString("content").orEmpty())
                    partialReply(sb.toString())?.let(onPartial)
                    if (chunk.optBoolean("done")) break
                }
                sb.toString()
            }
            val json = parseJsonObject(content)
            history.put(message("user", text)).put(message("assistant", content))
            json
        }

        override fun close() {}
    }

    /** Lists the models installed on the Mac (empty if it can't be reached). */
    suspend fun installedModels(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            quick.newCall(Request.Builder().url("${base()}/api/tags").build()).execute().use { r ->
                val arr = JSONObject(r.body!!.string()).optJSONArray("models") ?: JSONArray()
                List(arr.length()) { arr.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    /** True if Ollama answers at the saved address. */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            quick.newCall(Request.Builder().url("${base()}/api/version").build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Looks for a Mac running Ollama on the same Wi-Fi by trying port 11434 on every address
     * in the phone's local network. Returns its address, e.g. "http://192.168.1.20:11434".
     */
    suspend fun findOnNetwork(): String? = withContext(Dispatchers.IO) {
        val own = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress } ?: return@withContext null
        val prefix = own.hostAddress!!.substringBeforeLast('.')
        val probe = client.newBuilder().connectTimeout(400, TimeUnit.MILLISECONDS).readTimeout(800, TimeUnit.MILLISECONDS).build()
        coroutineScope {
            (1..254).map { n ->
                async {
                    val url = "http://$prefix.$n:$PORT"
                    runCatching {
                        probe.newCall(Request.Builder().url("$url/api/version").build()).execute().use { r ->
                            if (r.isSuccessful && r.body!!.string().contains("version")) url else null
                        }
                    }.getOrNull()
                }
            }.awaitAll().firstOrNull { it != null }
        }
    }

    companion object {
        const val PORT = 11434
        const val DEFAULT_MODEL = "gemma4:e4b-it-qat"
        /** The Mac frees the model's memory after 10 idle minutes. */
        const val KEEP_ALIVE = "10m"
        private val JSON = "application/json".toMediaType()

        /** Accepts "192.168.1.20", "192.168.1.20:11434" or a full URL. */
        fun normalizeUrl(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.isBlank()) return ""
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            val hostPart = s.substringAfter("://")
            if (!hostPart.contains(':')) s = "$s:$PORT"
            return s
        }
    }
}

/**
 * Uses the Mac when it can be reached, and Claude when it can't (if a Claude key is set).
 * [onFallback] lets the app show that it is using Claude for now.
 */
class FallbackBackend(
    private val primary: OllamaBackend,
    private val fallback: () -> LlmBackend?,
    private val onReachable: (Boolean) -> Unit,
) : LlmBackend {
    override val name: String get() = primary.name

    override suspend fun json(system: String, prompt: String, schema: Map<String, Any>, image: File?, maxTokens: Int, background: Boolean): JSONObject =
        try {
            primary.json(system, prompt, schema, image, maxTokens, background).also { onReachable(true) }
        } catch (e: MacUnreachableException) {
            onReachable(false)
            val fb = fallback() ?: throw e
            fb.json(system, prompt, schema, image, maxTokens, background)
        }

    override fun openChat(system: String): ChatChannel = object : ChatChannel {
        private var channel: ChatChannel = primary.openChat(system)
        private var onFallback = false

        override suspend fun send(text: String, schema: Map<String, Any>, onPartial: (String) -> Unit): JSONObject =
            try {
                channel.send(text, schema, onPartial).also { if (!onFallback) onReachable(true) }
            } catch (e: MacUnreachableException) {
                onReachable(false)
                val fb = fallback() ?: throw e
                if (!onFallback) { channel = fb.openChat(system); onFallback = true }
                channel.send(text, schema, onPartial)
            }

        override fun close() = channel.close()
    }
}
