package com.lvupme.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerGraderTest {

    @Test
    fun countsWordsAndComputesDynamicMaxAllowed() {
        assertEquals(0, AnswerGrader.countWords("   "))
        assertEquals(1, AnswerGrader.countWords("resilient"))
        assertEquals(2, AnswerGrader.countWords("LEFT JOIN"))
        assertEquals(3, AnswerGrader.countWords("GROUP BY HAVING"))
        assertEquals(4, AnswerGrader.countWords("one two three four"))
        assertEquals(3, AnswerGrader.maxAllowedWords("LEFT JOIN"))
        assertEquals(4, AnswerGrader.maxAllowedWords("SELECT * FROM users"))
    }

    @Test
    fun preservesCodeSymbolAnswersInsteadOfStrippingToEmpty() {
        assertTrue(AnswerGrader.gradeShortAnswer("()", "()", emptyList()).isCorrect)
        assertTrue(AnswerGrader.gradeShortAnswer("!=", "!=", listOf("<>")).isCorrect)
        assertTrue(AnswerGrader.gradeShortAnswer(";", ";", emptyList()).isCorrect)
        assertFalse(AnswerGrader.gradeShortAnswer("[]", "()", emptyList()).isCorrect)
    }

    @Test
    fun preventsFalsePositiveTypoMatchesOnNumbersAndShortKeywords() {
        assertFalse(AnswerGrader.gradeShortAnswer("1998", "1999", emptyList()).isCorrect)
        assertFalse(AnswerGrader.gradeShortAnswer("full", "null", emptyList()).isCorrect)
        assertFalse(AnswerGrader.gradeShortAnswer("coin", "join", emptyList()).isCorrect)
        assertFalse(AnswerGrader.gradeShortAnswer("trie", "true", emptyList()).isCorrect)
    }

    @Test
    fun gradesExactSynonymAndLongWordMinorTypo() {
        val exact = AnswerGrader.gradeShortAnswer("  left join! ", "LEFT JOIN", listOf("레프트 조인"))
        assertTrue(exact.isCorrect)

        val synonym = AnswerGrader.gradeShortAnswer("레프트조인", "LEFT JOIN", listOf("레프트 조인"))
        assertTrue(synonym.isCorrect)

        val typo = AnswerGrader.gradeShortAnswer("resiliant", "resilient", emptyList())
        assertTrue(typo.isCorrect)
        assertEquals(0.85f, typo.confidence)

        val wrong = AnswerGrader.gradeShortAnswer("INNER JOIN", "LEFT JOIN", listOf("레프트 조인"))
        assertFalse(wrong.isCorrect)
        assertTrue(wrong.shouldOfferAiRecheck)

        val singleChar = AnswerGrader.gradeShortAnswer("a", "resilient", emptyList())
        assertFalse(singleChar.isCorrect)
        assertFalse(singleChar.shouldOfferAiRecheck)
    }
}
