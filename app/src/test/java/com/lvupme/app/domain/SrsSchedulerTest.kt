package com.lvupme.app.domain

import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.LearningCardEntity
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.data.local.QuizQuestionEntity
import com.lvupme.app.data.local.ReviewStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsSchedulerTest {

    private val scheduler = SrsScheduler()

    @Test
    fun fiveToOneRatioSelectsPastReviewOnSixthSlot() {
        val currentCard = LearningCardEntity(id = 10, subjectId = 1, unitId = 2, unitOrderIndex = 2, title = "Unit2", microSummary = "m", detailExplanation = "d")
        val pastCard = LearningCardEntity(id = 1, subjectId = 1, unitId = 1, unitOrderIndex = 1, title = "Unit1", microSummary = "m", detailExplanation = "d")
        val questions = listOf(
            QuizQuestionEntity(id = 101, subjectId = 1, unitId = 2, cardId = 10, type = QuestionType.MULTIPLE_CHOICE, prompt = "Q10", answerText = "A"),
            QuizQuestionEntity(id = 102, subjectId = 1, unitId = 1, cardId = 1, type = QuestionType.MULTIPLE_CHOICE, prompt = "Q1", answerText = "A")
        )
        val settings = AppSettings(newToReviewRatio = 5, mcPercent = 70)

        for (idx in 0..4) {
            val picked = scheduler.selectNextQuestion(
                activeUnitId = 2,
                activeUnitOrder = 2,
                cards = listOf(currentCard, pastCard),
                questions = questions,
                states = emptyMap(),
                settings = settings,
                sessionIndex = idx,
                recentCardIds = emptyList(),
                recentQuestionTypes = emptyList()
            )
            assertNotNull(picked)
            assertFalse("Slot $idx should be current unit", picked!!.isFromPastReview)
        }

        val sixth = scheduler.selectNextQuestion(
            activeUnitId = 2,
            activeUnitOrder = 2,
            cards = listOf(currentCard, pastCard),
            questions = questions,
            states = emptyMap(),
            settings = settings,
            sessionIndex = 5,
            recentCardIds = emptyList(),
            recentQuestionTypes = emptyList()
        )
        assertNotNull(sixth)
        assertTrue("Slot 5 (6th item) should be past review", sixth!!.isFromPastReview)
        assertEquals(1L, sixth.card.id)
    }

    @Test
    fun pastReviewPrioritizesMoreRecentAndHigherErrorRate() {
        val now = 1_000_000_000L
        val oldCard = LearningCardEntity(id = 1, subjectId = 1, unitId = 1, unitOrderIndex = 1, title = "Old", microSummary = "", detailExplanation = "")
        val recentCard = LearningCardEntity(id = 2, subjectId = 1, unitId = 3, unitOrderIndex = 3, title = "Recent", microSummary = "", detailExplanation = "")
        val recentWrongCard = LearningCardEntity(id = 3, subjectId = 1, unitId = 3, unitOrderIndex = 3, title = "RecentWrong", microSummary = "", detailExplanation = "")

        val oldState = ReviewStateEntity(cardId = 1, subjectId = 1, unitId = 1, unitOrderIndex = 1, totalAttempts = 4, wrongCount = 0, nextReviewAt = now)
        val recentState = ReviewStateEntity(cardId = 2, subjectId = 1, unitId = 3, unitOrderIndex = 3, totalAttempts = 4, wrongCount = 0, nextReviewAt = now)
        val recentWrongState = ReviewStateEntity(cardId = 3, subjectId = 1, unitId = 3, unitOrderIndex = 3, totalAttempts = 4, wrongCount = 3, isWeak = true, nextReviewAt = now)

        val pOld = scheduler.computePastReviewPriority(oldCard, oldState, activeUnitOrder = 4, nowMillis = now)
        val pRecent = scheduler.computePastReviewPriority(recentCard, recentState, activeUnitOrder = 4, nowMillis = now)
        val pRecentWrong = scheduler.computePastReviewPriority(recentWrongCard, recentWrongState, activeUnitOrder = 4, nowMillis = now)

        assertTrue("More recent unit should outrank older unit", pRecent > pOld)
        assertTrue("Higher wrong count should strongly outrank low wrong count", pRecentWrong > pRecent)
    }

    @Test
    fun multiTurnSimulationAvoidsLoopsAndRotatesPastAndMaintainsRatios() {
        val now = 2_000_000_000L
        val currentCards = (10L..15L).map { id ->
            LearningCardEntity(id = id, subjectId = 1, unitId = 2, unitOrderIndex = 2, title = "Cur$id", microSummary = "", detailExplanation = "")
        }
        val pastCards = (1L..4L).map { id ->
            LearningCardEntity(id = id, subjectId = 1, unitId = 1, unitOrderIndex = 1, title = "Past$id", microSummary = "", detailExplanation = "")
        }
        val allCards = currentCards + pastCards
        val allQuestions = allCards.flatMap { c ->
            listOf(
                QuizQuestionEntity(id = c.id * 10 + 1, subjectId = 1, unitId = c.unitId, cardId = c.id, type = QuestionType.MULTIPLE_CHOICE, prompt = "MC ${c.id}", answerText = "A"),
                QuizQuestionEntity(id = c.id * 10 + 2, subjectId = 1, unitId = c.unitId, cardId = c.id, type = QuestionType.SHORT_ANSWER, prompt = "SA ${c.id}", answerText = "A")
            )
        }

        // Seed 3 weak cards on cooldown in current unit to verify they do NOT starve unseen cards
        val states = mutableMapOf<Long, ReviewStateEntity>()
        for (weakId in 10L..12L) {
            states[weakId] = ReviewStateEntity(
                cardId = weakId, subjectId = 1, unitId = 2, unitOrderIndex = 2,
                totalAttempts = 3, wrongCount = 2, isWeak = true,
                lastReviewedAt = now - 60_000L, nextReviewAt = now + 1_800_000L
            )
        }

        val settings = AppSettings(newToReviewRatio = 5, mcPercent = 70)
        val recentCards = mutableListOf<Long>()
        val recentPastCards = mutableListOf<Long>()
        val recentTypes = mutableListOf<String>()
        var pastCount = 0
        var saCount = 0
        val seenCurrentIds = mutableSetOf<Long>()
        val seenPastIds = mutableSetOf<Long>()

        for (step in 0 until 24) {
            val picked = scheduler.selectNextQuestion(
                activeUnitId = 2,
                activeUnitOrder = 2,
                cards = allCards,
                questions = allQuestions,
                states = states,
                settings = settings,
                sessionIndex = step,
                recentCardIds = recentCards,
                recentQuestionTypes = recentTypes,
                recentPastCardIds = recentPastCards,
                nowMillis = now + step * 5_000L
            )!!

            if (picked.isFromPastReview) {
                pastCount++
                seenPastIds.add(picked.card.id)
                recentPastCards.add(picked.card.id)
            } else {
                seenCurrentIds.add(picked.card.id)
            }
            if (picked.question.type == QuestionType.SHORT_ANSWER) saCount++
            recentCards.add(picked.card.id)
            recentTypes.add(picked.question.type)

            val updated = scheduler.updateReviewState(
                current = states[picked.card.id] ?: scheduler.defaultReviewState(picked.card),
                isCorrect = true,
                questionType = picked.question.type,
                weakThreshold = 2,
                nowMillis = now + step * 5_000L
            )
            states[picked.card.id] = updated
        }

        assertEquals("24 turns at 5:1 should yield exactly 4 past review turns", 4, pastCount)
        assertTrue("All unseen current cards should be reached (no 3-card weak loop)", seenCurrentIds.containsAll(listOf(13L, 14L, 15L)))
        assertTrue("Past reviews should rotate across multiple past cards (not stuck on 1)", seenPastIds.size >= 3)
        assertTrue("Short answer ratio should stay near 30% (got $saCount/24)", saCount in 5..9)
    }

    @Test
    fun promotesFamiliarCardToShortAnswerToMaintainSevenToThreeRatio() {
        val card = LearningCardEntity(id = 10, subjectId = 1, unitId = 1, unitOrderIndex = 1, title = "JOIN", microSummary = "", detailExplanation = "")
        val mc = QuizQuestionEntity(id = 1, subjectId = 1, unitId = 1, cardId = 10, type = QuestionType.MULTIPLE_CHOICE, prompt = "MC", answerText = "A")
        val sa = QuizQuestionEntity(id = 2, subjectId = 1, unitId = 1, cardId = 10, type = QuestionType.SHORT_ANSWER, prompt = "SA", answerText = "LEFT JOIN")
        val familiarState = ReviewStateEntity(cardId = 10, subjectId = 1, unitId = 1, unitOrderIndex = 1, masteryLevel = 1, consecutiveCorrect = 1)

        val picked = scheduler.selectNextQuestion(
            activeUnitId = 1,
            activeUnitOrder = 1,
            cards = listOf(card),
            questions = listOf(mc, sa),
            states = mapOf(10L to familiarState),
            settings = AppSettings(mcPercent = 70),
            sessionIndex = 0,
            recentCardIds = emptyList(),
            recentQuestionTypes = List(7) { QuestionType.MULTIPLE_CHOICE }
        )
        assertEquals(QuestionType.SHORT_ANSWER, picked?.question?.type)
    }

    @Test
    fun reviewStateBoundsIntervalCapEaseAndClearsWeakAfterTwoCorrect() {
        var state = ReviewStateEntity(cardId = 1, subjectId = 1, unitId = 1, unitOrderIndex = 1)
        state = scheduler.updateReviewState(state, isCorrect = false, questionType = QuestionType.MULTIPLE_CHOICE, weakThreshold = 2)
        assertFalse(state.isWeak)
        state = scheduler.updateReviewState(state, isCorrect = false, questionType = QuestionType.MULTIPLE_CHOICE, weakThreshold = 2)
        assertTrue(state.isWeak)
        assertEquals(2, state.wrongCount)

        // Repeated wrong answers shouldn't push easeFactor below 1.3
        repeat(10) {
            state = scheduler.updateReviewState(state, isCorrect = false, questionType = QuestionType.MULTIPLE_CHOICE, weakThreshold = 2)
        }
        assertTrue(state.easeFactor >= 1.3)

        // Two consecutive correct clears weak flag
        state = scheduler.updateReviewState(state, isCorrect = true, questionType = QuestionType.MULTIPLE_CHOICE, weakThreshold = 2)
        assertTrue(state.isWeak)
        state = scheduler.updateReviewState(state, isCorrect = true, questionType = QuestionType.SHORT_ANSWER, weakThreshold = 2)
        assertFalse(state.isWeak)

        // Many correct answers cap interval at 60 days (1440 hours) and easeFactor at 3.0
        repeat(20) {
            state = scheduler.updateReviewState(state, isCorrect = true, questionType = QuestionType.SHORT_ANSWER, weakThreshold = 2)
        }
        assertTrue(state.intervalHours <= 24.0 * 60.0)
        assertTrue(state.easeFactor <= 3.0)
    }
}
