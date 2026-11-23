package com.simplespeak.app.data

import kotlin.random.Random

/**
 * Makes "fix the sentence" puzzles from correct sentences by adding realistic mistakes.
 * Small language models are unreliable at adding mistakes on purpose, so this is done with rules.
 */
object ErrorInjector {
    private val pastToBase = mapOf(
        "went" to "go", "was" to "is", "had" to "have", "saw" to "see", "did" to "do", "made" to "make",
        "took" to "take", "felt" to "feel", "came" to "come", "got" to "get", "ate" to "eat", "gave" to "give",
        "told" to "tell", "said" to "say", "bought" to "buy", "thought" to "think", "found" to "find",
        "left" to "leave", "kept" to "keep", "began" to "begin", "wrote" to "write", "ran" to "run",
        "met" to "meet", "brought" to "bring", "knew" to "know", "spent" to "spend", "watched" to "watch",
        "played" to "play", "wanted" to "want", "finished" to "finish", "decided" to "decide", "started" to "start",
        "walked" to "walk", "visited" to "visit", "asked" to "ask", "needed" to "need", "stayed" to "stay",
    )
    private val wrongPast = mapOf("went" to "goed", "ate" to "eated", "bought" to "buyed", "ran" to "runned", "thought" to "thinked", "caught" to "catched")
    private val agreement = mapOf(
        "is" to "are", "are" to "is", "was" to "were", "were" to "was", "has" to "have", "have" to "has",
        "does" to "do", "doesn't" to "don't", "don't" to "doesn't",
    )
    private val subjectToObject = mapOf("I" to "Me", "He" to "Him", "She" to "Her", "We" to "Us", "They" to "Them")
    private val droppable = setOf("the", "a", "an", "to", "is", "are", "was", "of")
    private val irregularPlural = mapOf("children" to "childs", "people" to "persons", "men" to "mans", "women" to "womans", "feet" to "foots", "teeth" to "tooths")

    data class Broken(val text: String, val kinds: List<String>)

    /** Adds up to [count] mistakes. Returns null if the sentence could not be changed. */
    fun breakSentence(sentence: String, count: Int = 2, random: Random = Random.Default): Broken? {
        val words = sentence.trim().split(Regex("\\s+")).toMutableList()
        if (words.size < 3) return null
        val used = mutableSetOf<Int>()
        val kinds = mutableListOf<String>()

        fun core(w: String) = w.trimEnd('.', ',', '?', '!', ';', ':')
        fun suffix(w: String) = w.substring(core(w).length)
        fun replace(i: Int, newCore: String) {
            val w = words[i]
            val cased = if (core(w).firstOrNull()?.isUpperCase() == true) newCore.replaceFirstChar { it.uppercase() } else newCore
            words[i] = cased + suffix(w)
            used += i
        }

        val rules: List<() -> Boolean> = listOf(
            { // past tense -> base form, or a wrong past form
                val i = words.indices.filter { it !in used && core(words[it]).lowercase() in pastToBase }.randomOrNull(random)
                if (i == null) false else {
                    val c = core(words[i]).lowercase()
                    replace(i, if (c in wrongPast && random.nextBoolean()) wrongPast.getValue(c) else pastToBase.getValue(c))
                    kinds += "verb_tense"; true
                }
            },
            { // subject-verb agreement
                val i = words.indices.filter { it !in used && core(words[it]).lowercase() in agreement }.randomOrNull(random)
                if (i == null) false else {
                    replace(i, agreement.getValue(core(words[i]).lowercase())); kinds += "subject_verb_agreement"; true
                }
            },
            { // subject pronoun -> object pronoun at the start
                val c = core(words[0])
                if (0 in used || c !in subjectToObject) false else {
                    words[0] = subjectToObject.getValue(c) + suffix(words[0]); used += 0; kinds += "pronoun"; true
                }
            },
            { // drop a small word
                val i = words.indices.filter { it !in used && it > 0 && core(words[it]).lowercase() in droppable && suffix(words[it]).isEmpty() }.randomOrNull(random)
                if (i == null || words.size < 5) false else {
                    words.removeAt(i)
                    // shift used indices after the removed word
                    val shifted = used.map { if (it > i) it - 1 else it }; used.clear(); used += shifted
                    kinds += "missing_word"; true
                }
            },
            { // irregular plurals
                val i = words.indices.filter { it !in used && core(words[it]).lowercase() in irregularPlural }.randomOrNull(random)
                if (i == null) false else {
                    replace(i, irregularPlural.getValue(core(words[i]).lowercase())); kinds += "plural"; true
                }
            },
        )

        var tries = 0
        while (kinds.size < count && tries < 12) {
            rules.random(random).invoke()
            tries++
        }
        if (kinds.isEmpty()) return null
        val text = words.joinToString(" ")
        return if (TextMatch.sameSentence(text, sentence)) null else Broken(text, kinds.distinct())
    }
}
