package com.simplespeak.app

import com.simplespeak.app.ai.ChatChannel
import com.simplespeak.app.ai.Coach
import com.simplespeak.app.ai.LearnerProfile
import com.simplespeak.app.ai.LlmBackend
import com.simplespeak.app.data.AgeGroup
import com.simplespeak.app.data.ErrorInjector
import com.simplespeak.app.data.TextMatch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** A scripted model: returns canned JSON and records the prompts it was given. */
private class FakeBackend(var reply: String) : LlmBackend {
    override val name = "fake"
    val prompts = mutableListOf<String>()
    override suspend fun json(system: String, prompt: String, schema: Map<String, Any>, image: File?, maxTokens: Int, background: Boolean): JSONObject {
        prompts += prompt
        return JSONObject(reply)
    }
    override fun openChat(system: String) = object : ChatChannel {
        override suspend fun send(text: String, schema: Map<String, Any>, onPartial: (String) -> Unit): JSONObject {
            prompts += text
            val j = JSONObject(reply); onPartial(j.optString("reply")); return j
        }
        override fun close() {}
    }
}

class CoachLogicTest {
    private val fake = FakeBackend("{}")
    private val coach = Coach({ fake }, { LearnerProfile("Alex", AgeGroup.TEEN, "trains, cooking", "His answers are longer this week.") })

    // Real output from Gemma 4 E2B during prompt testing: it said "correct" wrongly, so the app decides.
    @Test fun correctnessComesFromTheCorrectionNotTheModel() = runBlocking {
        fake.reply = """{"improved":"yesterday my brother and i went to the shop to buy some milk because we had none","praise":"You clearly explained what you did","grammar_tip":"use the correct past tense for 'go' and 'was'","mistakes":["subject_verb_agreement","verb_tense","verb_tense"],"better_words":[],"organization_tip":"start with the time and then the action","next_level":"Yesterday my brother and I went to the shop to buy milk, because we had run out."}"""
        val fb = coach.feedback("Question: what did you do?", "yesterday me and my brother go to the shop for buy some milk because we was having none")
        assertFalse(fb.correct)
        assertTrue(fb.improved.startsWith("Yesterday")) // sentence case added
        assertEquals(listOf("subject_verb_agreement", "verb_tense"), fb.mistakes) // de-duplicated
        assertTrue(fb.nextLevel.contains("run out"))
        assertTrue(fake.prompts.last().contains("yesterday me and my brother"))
    }

    @Test fun unchangedAnswerIsCorrectAndHasNoTip() = runBlocking {
        fake.reply = """{"improved":"When I finished my homework I went outside and played football with my friends","praise":"You clearly described what you did","grammar_tip":"make sure to use the correct tense","mistakes":["verb_tense"],"better_words":[{"instead_of":"went","try":"went"},{"instead_of":"played","try":"had a game of"}],"organization_tip":"","next_level":"When I finished my homework I went outside and played football with my friends"}"""
        val fb = coach.feedback("Question", "when I finished my homework I went outside and played football with my friends")
        assertTrue(fb.correct)
        assertEquals("", fb.tip)
        assertTrue(fb.mistakes.isEmpty())
        assertEquals(1, fb.betterWords.size) // "went -> went" dropped
        assertEquals("", fb.nextLevel) // same as the answer, so not shown
    }

    @Test fun chatTurnUsesReplyAndFixes() = runBlocking {
        fake.reply = """{"reply":"That sounds like a nice trip. What did you enjoy most?","improved":"On Saturday I went to the train museum with my dad","tip":"Use \"went\", not \"go\", for the past."}"""
        val chat = coach.startChat("Your weekend")
        var partial = ""
        val turn = chat.send("on saturday I go to train museum with my dad") { partial = it }
        assertFalse(turn.correct)
        assertEquals("That sounds like a nice trip. What did you enjoy most?", partial)
        assertTrue(turn.tip.contains("went"))
    }

    @Test fun generatorsParseAndFilter() = runBlocking {
        fake.reply = """{"sentences":["Although I was tired, I kept cooking dinner for my family.","ok","Unless the train is late, we will arrive at noon."]}"""
        assertEquals(2, coach.generateSentences(4).size)
        assertTrue(fake.prompts.last().contains("Level 4 of 6"))
        assertTrue(fake.prompts.last().contains("trains, cooking"))
        fake.reply = """{"title":"Making toast","steps":[{"emoji":"🍞","text":"take the bread"},{"emoji":"🔥","text":"toast it"},{"emoji":"🧈","text":"add butter"},{"emoji":"😋","text":"eat it"}]}"""
        val seq = coach.generateSequence()
        assertNotNull(seq)
        assertEquals("Take the bread", seq!!.steps.first().second)
    }

    @Test fun errorInjectorAlwaysChangesTheSentence() {
        val sentences = listOf(
            "Although I was tired, I kept studying because the test was tomorrow.",
            "She has two children and they go to the same school.",
            "We went to the museum and saw a very old train.",
            "I bought some milk at the shop.",
        )
        repeat(50) { seed ->
            sentences.forEach { s ->
                val b = ErrorInjector.breakSentence(s, 2, Random(seed))
                assertNotNull("could not break: $s", b)
                assertFalse(TextMatch.sameSentence(b!!.text, s))
                assertTrue(b.kinds.isNotEmpty())
            }
        }
    }
}
