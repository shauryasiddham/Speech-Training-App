package com.simplespeak.app

import com.simplespeak.app.ai.ClaudeCoach
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

/** Runs ClaudeCoach against a local fake API server to check the request it sends and how it reads replies. */
class ClaudeCoachTest {
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
                    .put("model", ClaudeCoach.MODEL)
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

    private fun coach(key: String = "sk-test") = ClaudeCoach({ key }, server.url("/").toString().trimEnd('/'))

    @Test fun checkFixSendsStructuredRequestAndParsesFeedback() = runBlocking {
        replyText = """{"correct":false,"praise":"You kept the meaning.","improved":"I want water.","tip":"Use 'I' at the start.","mistakes":["pronoun","not_a_label"]}"""
        val fb = coach().checkFix("Me want water.", listOf("I want water."), "me want water", AgeGroup.TEEN)

        assertFalse(fb.correct)
        assertEquals("I want water.", fb.improved)
        assertEquals(listOf("pronoun"), fb.mistakes) // unknown labels are dropped
        assertEquals("me want water", fb.learnerText)

        val body = lastBody!!
        assertTrue(lastPath.startsWith("/v1/messages"))
        assertEquals(ClaudeCoach.MODEL, body.getString("model"))
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
        replyText = """{"correct":true,"praise":"You fixed the verb.","improved":"She is tired.","tip":"ignored","mistakes":["verb_tense"]}"""
        val fb = coach().checkFix("She are tired.", listOf("She is tired."), "she is tired", AgeGroup.CHILD)
        assertTrue(fb.correct)
        assertEquals("", fb.tip)
        assertTrue(fb.mistakes.isEmpty())
    }

    @Test fun pictureWithPhotoSendsImageBlock() = runBlocking {
        replyText = """{"correct":true,"praise":"Good.","improved":"Grandma is in the garden.","tip":"","mistakes":[]}"""
        val img = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        coach().checkPicture(null, img, "Grandma in her garden", "grandma is in the garden", AgeGroup.ADULT)
        val content = lastBody!!.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("image/jpeg", content.getJSONObject(0).getJSONObject("source").getString("media_type"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
    }

    @Test fun clarifyAndPlanParse() = runBlocking {
        replyText = """{"clear":"I want to go to the park after lunch.","short":"Park after lunch, please."}"""
        val (clear, short) = coach().clarify("park lunch after go want", AgeGroup.TEEN)
        assertEquals("I want to go to the park after lunch.", clear)
        assertEquals("Park after lunch, please.", short)

        replyText = """{"sentence":"Yesterday I played football at school."}"""
        val s = coach().sentenceFromPlan(mapOf("Who" to "I", "Did what" to "played football", "Where" to "school", "When" to "yesterday"), AgeGroup.CHILD)
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
