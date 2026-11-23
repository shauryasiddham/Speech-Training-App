package com.simplespeak.app

import com.simplespeak.app.ai.ClaudeBackend
import com.simplespeak.app.ai.Coach
import com.simplespeak.app.ai.LearnerProfile
import com.simplespeak.app.ai.CoachException
import com.simplespeak.app.data.AgeGroup
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Runs the coach with the Claude engine against a local fake API server to check the request it sends and how it reads replies. */
class ClaudeBackendTest {
    private lateinit var server: MockWebServer
    private var lastBody: JSONObject? = null
    private var lastHeaders: Map<String, List<String>> = emptyMap()
    private var lastPath: String = ""
    private var replyStatus = 200
    private var replyText = ""
    private var stopReason = "end_turn"

    @Before fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                lastPath = request.path.orEmpty()
                lastHeaders = request.headers.toMultimap()
                lastBody = JSONObject(request.body.readUtf8())
                val body = if (replyStatus == 200) JSONObject()
                    .put("id", "msg_test").put("type", "message").put("role", "assistant")
                    .put("model", ClaudeBackend.MODEL)
                    .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", replyText)))
                    .put("stop_reason", stopReason).put("stop_sequence", JSONObject.NULL)
                    .put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 10))
                    .toString()
                else JSONObject().put("type", "error")
                    .put("error", JSONObject().put("type", "authentication_error").put("message", "invalid x-api-key")).toString()
                return MockResponse().setResponseCode(replyStatus)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("x-should-retry", "false")
                    .setBody(body)
            }
        }
        server.start()
    }

    @After fun stop() = server.shutdown()

    private fun coach(key: String = "sk-test"): Coach {
        val backend = ClaudeBackend({ key }, server.url("/").toString().trimEnd('/'))
        return Coach({ backend }, { LearnerProfile("Alex", AgeGroup.TEEN, "trains", "") })
    }

    @Test fun checkFixSendsStructuredRequestAndParsesFeedback() = runBlocking {
        replyText = """{"improved":"I want water.","praise":"You kept the meaning.","grammar_tip":"Use 'I' at the start.","mistakes":["pronoun","not_a_label"],"better_words":[],"organization_tip":"","next_level":"I would like some water, please."}"""
        val fb = coach().feedback("Fix this sentence: \"Me want water.\"", "me want water", listOf("I want water."))

        assertFalse(fb.correct)
        assertEquals("I want water.", fb.improved)
        assertEquals(listOf("pronoun"), fb.mistakes) // unknown labels are dropped
        assertEquals("me want water", fb.learnerText)
        assertEquals("Use 'I' at the start.", fb.tip)
        assertEquals("I would like some water, please.", fb.nextLevel)

        val body = lastBody!!
        assertTrue(lastPath.startsWith("/v1/messages"))
        assertEquals(ClaudeBackend.MODEL, body.getString("model"))
        assertEquals("default", body.get("fallbacks"))
        val output = body.getJSONObject("output_config")
        assertEquals("low", output.getString("effort"))
        val schema = output.getJSONObject("format").getJSONObject("schema")
        assertEquals("json_schema", output.getJSONObject("format").getString("type"))
        assertEquals(false, schema.getBoolean("additionalProperties"))
        assertTrue(schema.getJSONObject("properties").has("mistakes"))
        assertTrue(body.getString("system").contains("SimpleSpeak"))
        assertTrue(lastHeaders["anthropic-beta"]!!.joinToString(",").contains("server-side-fallback-2026-07-01"))
        // The SDK sends the key either as x-api-key or as a Bearer token, depending on API version.
        val auth = (lastHeaders["x-api-key"].orEmpty() + lastHeaders["authorization"].orEmpty()).joinToString()
        assertTrue(auth.contains("sk-test"))
    }

    @Test fun correctAnswerHasNoTipOrMistakes() = runBlocking {
        replyText = """{"improved":"She is tired.","praise":"You fixed the verb.","grammar_tip":"ignored","mistakes":["verb_tense"],"better_words":[],"organization_tip":"","next_level":""}"""
        val fb = coach().feedback("Fix: \"She are tired.\"", "she is tired", listOf("She is tired."))
        assertTrue(fb.correct)
        assertEquals("", fb.tip)
        assertTrue(fb.mistakes.isEmpty())
    }

    @Test fun pictureWithPhotoSendsImageBlock() = runBlocking {
        replyText = """{"improved":"Grandma is in the garden.","praise":"Good.","grammar_tip":"","mistakes":[],"better_words":[],"organization_tip":"","next_level":""}"""
        val img = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        coach().feedback("Describe the photo.", "grandma is in the garden", emptyList(), img)
        val content = lastBody!!.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("image/jpeg", content.getJSONObject(0).getJSONObject("source").getString("media_type"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
    }

    @Test fun clarifyAndPlanParse() = runBlocking {
        replyText = """{"clear":"I want to go to the park after lunch.","short":"Park after lunch, please."}"""
        val (clear, short) = coach().clarify("park lunch after go want")
        assertEquals("I want to go to the park after lunch.", clear)
        assertEquals("Park after lunch, please.", short)

        replyText = """{"sentence":"Yesterday I played football at school."}"""
        val s = coach().sentenceFromPlan(mapOf("Who" to "I", "Did what" to "played football", "Where" to "school", "When" to "yesterday"))
        assertEquals("Yesterday I played football at school.", s)
    }

    @Test(expected = CoachException::class)
    fun badKeyGivesFriendlyError(): Unit = runBlocking {
        replyStatus = 401
        coach().testConnection()
    }

    @Test(expected = CoachException::class)
    fun refusalGivesFriendlyError(): Unit = runBlocking {
        replyText = ""
        stopReason = "refusal"
        coach().testConnection()
    }

    @Test(expected = CoachException::class)
    fun missingKeyGivesFriendlyError(): Unit = runBlocking {
        coach(key = "").testConnection()
    }
}
