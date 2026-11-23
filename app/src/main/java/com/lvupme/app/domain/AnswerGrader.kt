package com.lvupme.app.domain

import kotlin.math.min

data class LocalGradeResult(
    val isCorrect: Boolean,
    val confidence: Float, // 1.0 = exact, 0.85 = typo tolerance, 0.0 = wrong
    val shouldOfferAiRecheck: Boolean,
    val wordCountExceeded: Boolean = false
)

/**
 * Local instant grader for <=3-word short answer questions.
 * - Preserves code/symbol answers (e.g., "()", "!=", ";", "[]") instead of stripping them empty.
 * - Restricts Levenshtein typo tolerance strictly to pure word answers (length >= 5, no digits/code symbols).
 */
object AnswerGrader {
    const val MAX_SHORT_ANSWER_WORDS = 3

    private val PURE_WORD_REGEX = Regex("^[a-z가-힣]+$")

    fun countWords(input: String): Int {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }

    /**
     * Dynamically allows >=3 words if AI ever generated a longer expected answer,
     * preventing UI soft-locks while normally keeping the 3-word cap.
     */
    fun maxAllowedWords(expectedAnswer: String): Int {
        return maxOf(MAX_SHORT_ANSWER_WORDS, countWords(expectedAnswer))
    }

    fun normalize(raw: String): String {
        val collapsed = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return ""
        val stripped = collapsed
            .replace(Regex("[`\"'.,!?;:()\\[\\]]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        // If stripping punctuation wipes out a code/symbol answer (e.g. "()", "!=", ";"), keep symbols
        return stripped.ifEmpty { collapsed }
    }

    fun gradeShortAnswer(
        userInput: String,
        expectedAnswer: String,
        acceptedAnswers: List<String>
    ): LocalGradeResult {
        val normalizedInput = normalize(userInput)
        if (normalizedInput.isEmpty()) {
            return LocalGradeResult(isCorrect = false, confidence = 0f, shouldOfferAiRecheck = false)
        }

        val maxWords = maxAllowedWords(expectedAnswer)
        val wordCountExceeded = countWords(userInput) > maxWords
        val targets = (listOf(expectedAnswer) + acceptedAnswers)
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()

        // 1. Exact match (with or without internal spaces)
        val noSpaceInput = normalizedInput.replace(" ", "")
        for (target in targets) {
            if (normalizedInput == target || noSpaceInput == target.replace(" ", "")) {
                return LocalGradeResult(
                    isCorrect = true,
                    confidence = 1.0f,
                    shouldOfferAiRecheck = false,
                    wordCountExceeded = wordCountExceeded
                )
            }
        }

        // 2. Minor typo tolerance ONLY for pure natural-language words >= 5 chars (no digits/code tokens)
        if (PURE_WORD_REGEX.matches(normalizedInput)) {
            for (target in targets) {
                if (target.length >= 5 && PURE_WORD_REGEX.matches(target) && levenshtein(normalizedInput, target) <= 1) {
                    return LocalGradeResult(
                        isCorrect = true,
                        confidence = 0.85f,
                        shouldOfferAiRecheck = false,
                        wordCountExceeded = wordCountExceeded
                    )
                }
            }
        }

        // 3. Not matched locally -> mark wrong, offer AI recheck if non-trivial input
        return LocalGradeResult(
            isCorrect = false,
            confidence = 0f,
            shouldOfferAiRecheck = normalizedInput.length >= 2,
            wordCountExceeded = wordCountExceeded
        )
    }

    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
