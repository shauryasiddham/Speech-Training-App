package com.simplespeak.app.data

/** Helpers to compare what the learner said with an expected sentence, without AI. */
object TextMatch {
    private val contractions = mapOf(
        "don't" to "do not", "doesn't" to "does not", "didn't" to "did not", "can't" to "can not",
        "cannot" to "can not", "won't" to "will not", "isn't" to "is not", "aren't" to "are not",
        "wasn't" to "was not", "weren't" to "were not", "i'm" to "i am", "you're" to "you are",
        "we're" to "we are", "they're" to "they are", "he's" to "he is", "she's" to "she is",
        "it's" to "it is", "i'll" to "i will", "we'll" to "we will", "you'll" to "you will",
        "they'll" to "they will", "i've" to "i have", "we've" to "we have", "let's" to "let us",
        "i'd" to "i would", "couldn't" to "could not", "shouldn't" to "should not", "wouldn't" to "would not",
        "haven't" to "have not", "hasn't" to "has not",
    )

    /** Display words of a sentence, without the final punctuation. */
    fun words(sentence: String): List<String> =
        sentence.trim().trimEnd('.', '?', '!').split(Regex("\\s+")).filter { it.isNotBlank() }

    fun endPunctuation(sentence: String): String = sentence.trim().lastOrNull()?.takeIf { it in ".?!" }?.toString() ?: "."

    /** Lower-case words with punctuation removed and contractions expanded. */
    fun normalize(text: String): List<String> {
        val cleaned = text.lowercase().replace('’', '\'').replace(Regex("[^a-z0-9' ]"), " ")
        return cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
            .flatMap { (contractions[it] ?: it).split(" ") }
    }

    fun sameSentence(a: String, b: String) = normalize(a) == normalize(b)

    /** 0..1 similarity based on the longest common word sequence. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 2.0 * lcs(x, y).size / (x.size + y.size)
    }

    /**
     * Marks which words of [target] are changed compared with [said].
     * Returns pairs of (word, changed?) for highlighting the correction.
     */
    fun highlightChanges(said: String, target: String): List<Pair<String, Boolean>> {
        val targetWords = target.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val saidNorm = said.lowercase().replace(Regex("[^a-z0-9' ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val targetNorm = targetWords.map { it.lowercase().replace(Regex("[^a-z0-9']"), "") }
        val keep = lcsIndices(saidNorm, targetNorm)
        return targetWords.mapIndexed { i, w -> w to (i !in keep && targetNorm[i].isNotEmpty()) }
    }

    private fun lcs(x: List<String>, y: List<String>): List<String> {
        val idx = lcsIndices(x, y)
        return y.filterIndexed { i, _ -> i in idx }
    }

    /** Indices in [y] that are part of a longest common subsequence with [x]. */
    private fun lcsIndices(x: List<String>, y: List<String>): Set<Int> {
        val dp = Array(x.size + 1) { IntArray(y.size + 1) }
        for (i in x.indices.reversed()) for (j in y.indices.reversed()) {
            dp[i][j] = if (x[i] == y[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val result = mutableSetOf<Int>()
        var i = 0
        var j = 0
        while (i < x.size && j < y.size) {
            when {
                x[i] == y[j] -> { result += j; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return result
    }
}
