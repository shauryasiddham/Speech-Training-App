package com.simplespeak.app

import com.simplespeak.app.ai.ChatChannel
import com.simplespeak.app.ai.Coach
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.ai.FallbackBackend
import com.simplespeak.app.ai.LearnerProfile
import com.simplespeak.app.ai.LlmBackend
import com.simplespeak.app.ai.MacUnreachableException
import com.simplespeak.app.ai.OllamaBackend
import com.simplespeak.app.data.AgeGroup
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Runs the Mac (Ollama) engine against a fake Ollama server. */
class OllamaBackendTest {
    private lateinit var server: MockWebServer
    private var lastBody: JSONObject? = null
    private var content = ""
    private var status = 200

    @Before fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path == "/api/version") return MockResponse().setBody("""{"version":"0.40.17"}""")
                if (path == "/api/tags") return MockResponse().setBody("""{"models":[{"name":"gemma4:e4b-it-qat"},{"name":"qwen3.5:4b"}]}""")
                lastBody = JSONObject(request.body.readUtf8())
                if (status != 200) return MockResponse().setResponseCode(status).setBody("""{"error":"model not found"}""")
                return if (lastBody!!.getBoolean("stream")) {
                    // Ollama streams one JSON object per line.
                    val pieces = content.chunked(7)
                    val lines = pieces.joinToString("\n") { JSONObject().put("message", JSONObject().put("role", "assistant").put("content", it)).put("done", false).toString() } +
                        "\n" + JSONObject().put("message", JSONObject().put("role", "assistant").put("content", "")).put("done", true).toString() + "\n"
                    MockResponse().setBody(lines)
                } else {
                    MockResponse().setBody(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content)).put("done", true).toString())
                }
            }
        }
        server.start()
    }

    @After fun stop() = server.shutdown()

    private fun backend(url: String = server.url("/").toString()) = OllamaBackend({ url }, { "gemma4:e4b-it-qat" })
    private fun coach(b: LlmBackend) = Coach({ b }, { LearnerProfile("Alex", AgeGroup.TEEN, "", "") })

    @Test fun feedbackRequestUsesSchemaAndNoThinking() = runBlocking {
        content = """{"improved":"I went to the shop.","praise":"You said where you went.","grammar_tip":"Use went for the past.","mistakes":["verb_tense"],"better_words":[],"organization_tip":"","next_level":"I popped to the shop."}"""
        val img = File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val fb = coach(backend()).feedback("Describe the photo.", "I go to the shop", image = img)
        assertFalse(fb.correct)
        assertEquals("I went to the shop.", fb.improved)
        val body = lastBody!!
        assertEquals("gemma4:e4b-it-qat", body.getString("model"))
        assertEquals(false, body.getBoolean("think"))
        assertEquals(false, body.getBoolean("stream"))
        assertEquals("10m", body.getString("keep_alive"))
        assertTrue(body.getJSONObject("format").getJSONObject("properties").has("improved"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(1, messages.getJSONObject(1).getJSONArray("images").length())
    }

    @Test fun chatStreamsTheReplyAndKeepsHistory() = runBlocking {
        content = """{"reply":"That sounds like a fun day. What did you see first?","improved":"I went to the museum.","tip":"Use went for the past."}"""
        val chat = coach(backend()).startChat("Your weekend")
        val partials = mutableListOf<String>()
        val turn = chat.send("I go to the museum") { partials += it }
        assertEquals("That sounds like a fun day. What did you see first?", turn.reply)
        assertTrue(partials.size > 2) // reply shown while it streams
        assertTrue(partials.first().length < turn.reply.length)
        chat.send("I see the trains") { }
        // second request carries system + first exchange + new message
        assertEquals(4, lastBody!!.getJSONArray("messages").length())
        assertEquals(true, lastBody!!.getBoolean("stream"))
    }

    @Test fun listsModelsAndPings() = runBlocking {
        assertEquals(listOf("gemma4:e4b-it-qat", "qwen3.5:4b"), backend().installedModels())
        assertTrue(backend().ping())
        assertFalse(backend("http://127.0.0.1:1").ping())
    }

    @Test fun missingModelGivesClearMessage() = runBlocking {
        status = 404
        val e = runCatching { backend().json("s", "p", Coach.SENTENCE_SCHEMA) }.exceptionOrNull()
        assertTrue(e is CoachException && e !is MacUnreachableException)
        assertTrue(e!!.message!!.contains("doesn't have the model"))
    }

    @Test fun fallsBackWhenTheMacIsOff() = runBlocking {
        val off = backend("http://127.0.0.1:1")
        var reachable: Boolean? = null
        val phoneStandIn = object : LlmBackend {
            override val name = "fallback"
            override suspend fun json(system: String, prompt: String, schema: Map<String, Any>, image: File?, maxTokens: Int, background: Boolean) =
                JSONObject("""{"sentence":"From the fallback."}""")
            override fun openChat(system: String) = object : ChatChannel {
                override suspend fun send(text: String, schema: Map<String, Any>, onPartial: (String) -> Unit) = JSONObject("""{"reply":"Hi","improved":"$text","tip":""}""")
                override fun close() {}
            }
        }
        val fb = FallbackBackend(off, { phoneStandIn }) { reachable = it }
        assertEquals("From the fallback.", fb.json("s", "p", Coach.SENTENCE_SCHEMA).getString("sentence"))
        assertEquals(false, reachable)
        val noFallback = FallbackBackend(off, { null }) { }
        assertTrue(runCatching { noFallback.json("s", "p", Coach.SENTENCE_SCHEMA) }.exceptionOrNull() is MacUnreachableException)
    }

    @Test fun normalizesAddresses() {
        assertEquals("http://192.168.1.20:11434", OllamaBackend.normalizeUrl("192.168.1.20"))
        assertEquals("http://192.168.1.20:11434", OllamaBackend.normalizeUrl(" http://192.168.1.20:11434/ "))
        assertEquals("http://mac.local:9000", OllamaBackend.normalizeUrl("mac.local:9000"))
    }
}
