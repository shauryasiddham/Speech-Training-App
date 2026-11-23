package com.simplespeak.app.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.simplespeak.app.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * The on-phone speaking coach: Google's Gemma 4 E2B model run with LiteRT-LM.
 * Handles the one-time 2.6 GB download, loading (GPU first, then CPU), and generation.
 * Everything runs on one dedicated thread, as LiteRT-LM requires.
 */
class LocalModel(private val context: Context, private val prefs: Prefs) : LlmBackend {
    override val name = "Gemma (on this phone)"

    sealed interface Status {
        data object Missing : Status
        data class Downloading(val fraction: Float, val note: String) : Status
        data object Downloaded : Status
        data object Loading : Status
        data class Ready(val backend: String) : Status
        data class Error(val message: String) : Status
    }

    var status by mutableStateOf<Status>(Status.Missing)
        private set

    /** Speed of the last reply, for the Caregiver tab. */
    var lastSpeed by mutableStateOf<String?>(null)
        private set

    private val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")
    val modelFile = File(dir, FILE_NAME)
    private val partFile = File(dir, "$FILE_NAME.part")

    val isDownloaded: Boolean get() = modelFile.exists() && modelFile.length() > MIN_VALID_BYTES

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "simplespeak-llm") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null
    private var backendName = ""

    @Volatile private var backgroundConversation: Conversation? = null

    init {
        refresh()
    }

    // ---- Download -------------------------------------------------------------------

    fun startDownload(wifiOnly: Boolean): String? {
        dir.mkdirs()
        val free = dir.usableSpace
        if (free < EXPECTED_BYTES + 300_000_000L) {
            return "Not enough free space. The coach needs about 2.9 GB free (this phone has ${"%.1f".format(free / 1e9)} GB)."
        }
        partFile.delete()
        val dm = context.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(URL))
            .setTitle("SimpleSpeak speaking coach")
            .setDescription("Downloading the on-phone coach (2.6 GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, null, "models/$FILE_NAME.part")
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        prefs.downloadId = dm.enqueue(request)
        status = Status.Downloading(0f, "Starting…")
        return null
    }

    fun cancelDownload() {
        val id = prefs.downloadId
        if (id >= 0) context.getSystemService(DownloadManager::class.java).remove(id)
        prefs.downloadId = -1
        partFile.delete()
        refresh()
    }

    /** Updates [status] from the file and the download manager. Call about once a second while downloading. */
    fun refresh() {
        if (status is Status.Loading || status is Status.Ready) return
        val id = prefs.downloadId
        if (id < 0) {
            status = if (isDownloaded) Status.Downloaded else if (status is Status.Error) status else Status.Missing
            return
        }
        val dm = context.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) {
                prefs.downloadId = -1; status = if (isDownloaded) Status.Downloaded else Status.Missing; return
            }
            val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: EXPECTED_BYTES
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val fraction = (done.toFloat() / total).coerceIn(0f, 1f)
            val gb = "%.2f of %.2f GB".format(done / 1e9, total / 1e9)
            status = when (st) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.downloadId = -1
                    if (partFile.exists() && partFile.length() > MIN_VALID_BYTES) {
                        modelFile.delete()
                        partFile.renameTo(modelFile)
                    }
                    if (isDownloaded) Status.Downloaded else Status.Error("The download finished but the file looks incomplete. Please try again.")
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.downloadId = -1
                    partFile.delete()
                    Status.Error("The download failed (code $reason). Please check the internet connection and try again.")
                }
                DownloadManager.STATUS_PAUSED -> Status.Downloading(
                    fraction,
                    when (reason) {
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi · $gb"
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for internet · $gb"
                        else -> "Paused · $gb"
                    },
                )
                else -> Status.Downloading(fraction, gb)
            }
        } ?: run { status = Status.Error("Could not check the download.") }
    }

    fun deleteModel() {
        closeEngine()
        modelFile.delete()
        partFile.delete()
        status = Status.Missing
    }

    // ---- Engine ---------------------------------------------------------------------

    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun loadEngine(): Engine {
        engine?.let { return it }
        if (!isDownloaded) throw CoachException("The speaking coach is not downloaded yet. A caregiver can download it in the Caregiver tab.")
        status = Status.Loading
        ExperimentalFlags.enableConversationConstrainedDecoding = true
        val wantGpu = when (prefs.modelBackend) {
            "gpu" -> true
            "cpu" -> false
            else -> !prefs.gpuFailed
        }
        if (wantGpu) {
            runCatching { create(Backend.GPU()) }
                .onSuccess { engine = it; backendName = "GPU"; status = Status.Ready("GPU"); return it }
                .onFailure { if (prefs.modelBackend == "auto") prefs.gpuFailed = true }
        }
        return runCatching { create(Backend.CPU()) }
            .onSuccess { engine = it; backendName = "CPU"; status = Status.Ready("CPU") }
            .getOrElse {
                status = Status.Error("The coach could not start on this phone: ${it.message ?: it.javaClass.simpleName}")
                throw CoachException("The speaking coach could not start. Please try again, or check the Caregiver tab.")
            }
    }

    private fun create(backend: Backend): Engine =
        Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                visionBackend = Backend.CPU(),
                maxNumTokens = 4096,
                cacheDir = context.cacheDir.path,
            )
        ).also { it.initialize() }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        if (status is Status.Ready || status is Status.Loading) status = if (isDownloaded) Status.Downloaded else Status.Missing
    }

    /** Frees memory; the model loads again on next use. */
    suspend fun unload() = withContext(dispatcher) { closeEngine() }

    /** Runs [block] with the engine; if the GPU fails on first use, switches to the CPU and tries once more. */
    private fun <T> withEngine(block: (Engine) -> T): T {
        val e = loadEngine()
        return try {
            block(e)
        } catch (t: Throwable) {
            if (t is CancellationException || t is CoachException) throw t
            if (backendName == "GPU" && prefs.modelBackend == "auto") {
                prefs.gpuFailed = true
                closeEngine()
                block(loadEngine())
            } else throw t
        }
    }

    private fun conversationConfig(system: String, maxTokens: Int) = ConversationConfig(
        systemInstruction = Contents.of(system),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.4, seed = 0),
        maxOutputToken = maxTokens,
        thinkingConfig = ThinkingConfig(enableThinking = false),
        enableResponseFormat = true,
    )

    private fun Message.text(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    override suspend fun json(system: String, prompt: String, schema: Map<String, Any>, image: File?, maxTokens: Int, background: Boolean): JSONObject {
        // Anything the learner is waiting for goes first: stop preparing the next exercise.
        if (!background) backgroundConversation?.runCatching { cancelProcess() }
        return withContext(dispatcher) {
            try {
                withEngine { e ->
                    val conv = e.createConversation(conversationConfig(system, maxTokens))
                    if (background) backgroundConversation = conv
                    try {
                        val contents = if (image != null) {
                            Contents.of(Content.ImageFile(image.absolutePath), Content.Text(prompt))
                        } else Contents.of(prompt)
                        val start = System.currentTimeMillis()
                        val msg = conv.sendMessage(contents, responseFormat = ResponseFormat.json(JSONObject(schema).toString()))
                        val text = msg.text()
                        noteSpeed(text, System.currentTimeMillis() - start)
                        parseJsonObject(text)
                    } finally {
                        if (background) backgroundConversation = null
                        runCatching { conv.close() }
                    }
                }
            } catch (t: Throwable) {
                if (t is CoachException || t is CancellationException) throw t
                throw CoachException("The coach had a problem. Please try again.")
            }
        }
    }

    override fun openChat(system: String): ChatChannel = object : ChatChannel {
        private var conv: Conversation? = null

        override suspend fun send(text: String, schema: Map<String, Any>, onPartial: (String) -> Unit): JSONObject {
            backgroundConversation?.runCatching { cancelProcess() }
            return withContext(dispatcher) {
                try {
                    withEngine { e ->
                        val c = conv ?: e.createConversation(conversationConfig(system, 400)).also { conv = it }
                        val sb = StringBuilder()
                        val start = System.currentTimeMillis()
                        kotlinx.coroutines.runBlocking {
                            c.sendMessageAsync(text, responseFormat = ResponseFormat.json(JSONObject(schema).toString())).collect { chunk ->
                                sb.append(chunk.text())
                                partialReply(sb.toString())?.let(onPartial)
                            }
                        }
                        noteSpeed(sb.toString(), System.currentTimeMillis() - start)
                        parseJsonObject(sb.toString())
                    }
                } catch (t: Throwable) {
                    if (t is CoachException || t is CancellationException) throw t
                    throw CoachException("The coach had a problem. Please try again.")
                }
            }
        }

        override fun close() {
            val c = conv ?: return
            conv = null
            c.runCatching { cancelProcess() }
            executor.execute { runCatching { c.close() } }
        }
    }

    private fun noteSpeed(text: String, ms: Long) {
        val words = text.split(Regex("\\s+")).size
        lastSpeed = "Last reply: %.1f s on the $backendName (about %d words)".format(ms / 1000.0, words)
    }

    companion object {
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE_NAME"
        const val EXPECTED_BYTES = 2_588_147_712L
        private const val MIN_VALID_BYTES = 2_000_000_000L
    }
}
