package com.simplespeak.app

import com.simplespeak.app.data.TextMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test fun ignoresCaseAndPunctuation() {
        assertTrue(TextMatch.sameSentence("i want water", "I want water."))
    }

    @Test fun expandsContractions() {
        assertTrue(TextMatch.sameSentence("She doesn't like carrots", "She does not like carrots."))
    }

    @Test fun detectsWrongOrder() {
        assertFalse(TextMatch.sameSentence("dog the runs", "The dog runs."))
    }

    @Test fun highlightsChangedWords() {
        val parts = TextMatch.highlightChanges("Me want water", "I want water.")
        assertEquals(listOf("I" to true, "want" to false, "water." to false), parts)
    }

    @Test fun similarityIsHighForCloseSentences() {
        assertTrue(TextMatch.similarity("I went to the shop because we need milk", "I went to the shop because we needed milk.") > 0.85)
        assertTrue(TextMatch.similarity("banana", "I went to the shop.") < 0.3)
    }

    @Test fun wordsDropFinalPunctuation() {
        assertEquals(listOf("Can", "I", "have", "some", "water"), TextMatch.words("Can I have some water?"))
        assertEquals("?", TextMatch.endPunctuation("Can I have some water?"))
    }
}
