package com.lvupme.app.domain

import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.data.local.LearningCardEntity
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.data.local.QuizQuestionEntity
import com.lvupme.app.data.local.ReviewStateEntity
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class ScheduledQuestion(
    val card: LearningCardEntity,
    val question: QuizQuestionEntity,
    val reviewState: ReviewStateEntity,
    val isFromPastReview: Boolean
)

/**
 * Core scheduling engine implementing:
 * - Configurable 5:1 Current Unit : Past Curriculum mixing
 * - Past review weighting by: error rate x unit recency x SRS due bonus, with dedicated past-card rotation
 * - Anti-loop current-unit rotation: respects nextReviewAt cooldown, excludes recent cards, prioritizes unseen & due cards
 * - Configurable 7:3 Multiple-Choice : Short-Answer (<=3 words) ratio with natural MC -> SA promotion
 * - SM-2 inspired spaced repetition interval updates & weakness flagging
 */
class SrsScheduler {

    fun selectNextQuestion(
        activeUnitId: Long,
        activeUnitOrder: Int,
        cards: List<LearningCardEntity>,
        questions: List<QuizQuestionEntity>,
        states: Map<Long, ReviewStateEntity>,
        settings: AppSettings,
        sessionIndex: Int,
        recentCardIds: List<Long>,
        recentQuestionTypes: List<String>,
        recentPastCardIds: List<Long> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
        focusUnitOnly: Boolean = false,
        reviewAllUnits: Boolean = false,
        sessionCoveredCardIds: Set<Long> = emptySet()
    ): ScheduledQuestion? {
        if (cards.isEmpty() || questions.isEmpty()) return null

        val questionsByCard = questions.groupBy { it.cardId }
        val usableCards = cards.filter { !questionsByCard[it.id].isNullOrEmpty() }
        if (usableCards.isEmpty()) return null

        val currentUnitCards = usableCards.filter { it.unitId == activeUnitId }
        val pastUnitCards = usableCards.filter { it.unitOrderIndex < activeUnitOrder }

        val chosenCard: LearningCardEntity
        val isFromPast: Boolean

        when {
            focusUnitOnly -> {
                if (currentUnitCards.isEmpty()) return null
                chosenCard = pickFocusedUnitCard(
                    unitCards = currentUnitCards,
                    states = states,
                    recentCardIds = recentCardIds,
                    sessionCoveredCardIds = sessionCoveredCardIds,
                    nowMillis = nowMillis
                )
                isFromPast = false
            }

            reviewAllUnits -> {
                chosenCard = pickAllUnitsBalancedCard(
                    allCards = usableCards,
                    states = states,
                    recentCardIds = recentCardIds,
                    nowMillis = nowMillis
                )
                isFromPast = true
            }

            else -> {
                val cycleLength = settings.newToReviewRatio + 1 // default 5 + 1 = 6
                val wantsPastReview = pastUnitCards.isNotEmpty() && ((sessionIndex + 1) % cycleLength == 0)

                if (wantsPastReview) {
                    chosenCard = pickWeightedPastCard(
                        pastCards = pastUnitCards,
                        states = states,
                        activeUnitOrder = activeUnitOrder,
                        recentPastCardIds = recentPastCardIds,
                        nowMillis = nowMillis
                    )
                    isFromPast = true
                } else if (currentUnitCards.isNotEmpty()) {
                    chosenCard = pickCurrentUnitCard(
                        currentCards = currentUnitCards,
                        states = states,
                        recentCardIds = recentCardIds,
                        nowMillis = nowMillis
                    )
                    isFromPast = false
                } else {
                    chosenCard = pickWeightedPastCard(
                        pastCards = usableCards,
                        states = states,
                        activeUnitOrder = activeUnitOrder,
                        recentPastCardIds = recentPastCardIds.ifEmpty { recentCardIds },
                        nowMillis = nowMillis
                    )
                    isFromPast = true
                }
            }
        }

        val state = states[chosenCard.id] ?: defaultReviewState(chosenCard)
        val cardQuestions = questionsByCard[chosenCard.id].orEmpty()
        val chosenQuestion = pickQuestionType(
            cardQuestions = cardQuestions,
            state = state,
            settings = settings,
            recentQuestionTypes = recentQuestionTypes
        )

        return ScheduledQuestion(
            card = chosenCard,
            question = chosenQuestion,
            reviewState = state,
            isFromPastReview = isFromPast
        )
    }

    /**
     * Past curriculum review priority:
     * More recent past units + higher error rate + overdue SRS cards get highest weight.
     */
    fun computePastReviewPriority(
        card: LearningCardEntity,
        state: ReviewStateEntity,
        activeUnitOrder: Int,
        nowMillis: Long
    ): Double {
        val unitDistance = max(1, activeUnitOrder - card.unitOrderIndex)
        val recencyWeight = exp(-0.33 * (unitDistance - 1))

        val errorRate = if (state.totalAttempts == 0) 0.35
        else (state.wrongCount.toDouble() + 0.5) / (state.totalAttempts.toDouble() + 1.0)
        val errorWeight = 1.0 + (errorRate * 3.0) + (min(state.wrongCount, 6) * 0.45)

        val dueWeight = when {
            state.nextReviewAt <= 0L -> 1.3
            nowMillis >= state.nextReviewAt -> {
                val overdueHours = (nowMillis - state.nextReviewAt) / 3_600_000.0
                1.5 + min(overdueHours / 24.0, 2.0)
            }
            else -> 0.55 // Cooldown discount if reviewed recently
        }

        val weakBonus = if (state.isWeak) 1.5 else 1.0
        return recencyWeight * errorWeight * dueWeight * weakBonus
    }

    private fun pickWeightedPastCard(
        pastCards: List<LearningCardEntity>,
        states: Map<Long, ReviewStateEntity>,
        activeUnitOrder: Int,
        recentPastCardIds: List<Long>,
        nowMillis: Long
    ): LearningCardEntity {
        val excludeCount = min(max(0, pastCards.size - 1), 3)
        val excluded = recentPastCardIds.takeLast(excludeCount).toSet()
        val candidates = pastCards.filterNot { it.id in excluded }.ifEmpty { pastCards }

        return candidates.maxWith(
            compareBy<LearningCardEntity> { card ->
                val st = states[card.id] ?: defaultReviewState(card)
                computePastReviewPriority(card, st, activeUnitOrder, nowMillis)
            }.thenBy { card ->
                // Older lastReviewedAt breaks ties so cards rotate cleanly
                -(states[card.id]?.lastReviewedAt ?: 0L)
            }
        )
    }

    /**
     * Focused single-unit review (e.g., revisiting a completed past unit):
     * Strictly prioritizes cards not yet covered in the current review round so the user
     * completes a full pass over the unit cleanly, prioritizing weak & error-prone cards first.
     */
    private fun pickFocusedUnitCard(
        unitCards: List<LearningCardEntity>,
        states: Map<Long, ReviewStateEntity>,
        recentCardIds: List<Long>,
        sessionCoveredCardIds: Set<Long>,
        nowMillis: Long
    ): LearningCardEntity {
        val uncovered = unitCards.filterNot { it.id in sessionCoveredCardIds }
        if (uncovered.isEmpty()) {
            return pickCurrentUnitCard(unitCards, states, recentCardIds, nowMillis)
        }
        return uncovered.maxWith(
            compareBy<LearningCardEntity> { card ->
                val st = states[card.id] ?: defaultReviewState(card)
                var score = 0.0
                if (st.isWeak) score += 4.0
                score += min(st.wrongCount, 6) * 0.75
                if (st.nextReviewAt in 1..nowMillis) score += 2.0
                score += (5 - min(st.masteryLevel, 5)) * 0.5
                score
            }.thenBy { card ->
                -(states[card.id]?.lastReviewedAt ?: 0L)
            }.thenBy { card ->
                -card.id
            }
        )
    }

    /**
     * Balanced review across the entire curriculum after all units are completed:
     * Avoids biasing toward the last unit by weighting purely on weakness, error rate,
     * SRS overdue status, and least-recently-reviewed order.
     */
    private fun pickAllUnitsBalancedCard(
        allCards: List<LearningCardEntity>,
        states: Map<Long, ReviewStateEntity>,
        recentCardIds: List<Long>,
        nowMillis: Long
    ): LearningCardEntity {
        val excludeCount = min(max(0, allCards.size - 1), 4)
        val excluded = recentCardIds.takeLast(excludeCount).toSet()
        val candidates = allCards.filterNot { it.id in excluded }.ifEmpty { allCards }

        return candidates.maxWith(
            compareBy<LearningCardEntity> { card ->
                val st = states[card.id] ?: defaultReviewState(card)
                val errorRate = if (st.totalAttempts == 0) 0.35
                else (st.wrongCount.toDouble() + 0.5) / (st.totalAttempts.toDouble() + 1.0)
                val errorWeight = 1.0 + (errorRate * 3.0) + (min(st.wrongCount, 6) * 0.45)

                val dueWeight = when {
                    st.nextReviewAt <= 0L -> 1.3
                    nowMillis >= st.nextReviewAt -> {
                        val overdueHours = (nowMillis - st.nextReviewAt) / 3_600_000.0
                        1.5 + min(overdueHours / 24.0, 2.0)
                    }
                    else -> 0.55
                }
                val weakBonus = if (st.isWeak) 1.5 else 1.0
                errorWeight * dueWeight * weakBonus
            }.thenBy { card ->
                -(states[card.id]?.lastReviewedAt ?: 0L)
            }.thenBy { card ->
                -card.id
            }
        )
    }

    private fun pickCurrentUnitCard(
        currentCards: List<LearningCardEntity>,
        states: Map<Long, ReviewStateEntity>,
        recentCardIds: List<Long>,
        nowMillis: Long
    ): LearningCardEntity {
        // Dynamically exclude up to 4 most recent cards (capped at size - 1) to prevent 3-card loops
        val excludeCount = min(max(0, currentCards.size - 1), 4)
        val excluded = recentCardIds.takeLast(excludeCount).toSet()
        val candidates = currentCards.filterNot { it.id in excluded }.ifEmpty { currentCards }

        return candidates.maxWith(
            compareBy<LearningCardEntity> { card ->
                val st = states[card.id] ?: defaultReviewState(card)
                val isUnseen = st.totalAttempts == 0
                val isDue = st.nextReviewAt in 1..nowMillis
                val isOnCooldown = st.nextReviewAt > nowMillis

                var score = 1.0
                when {
                    isUnseen -> {
                        // Strongly prioritize unseen new cards & fresh AI reinforcement cards
                        score += 5.5
                        if (card.isAiReinforced) score += 1.5
                    }
                    isDue -> {
                        score += 4.5
                        if (st.isWeak) score += 2.5
                    }
                    isOnCooldown -> {
                        // Cards answered recently get cooldown discount so they don't starve unseen/due cards
                        score += if (st.isWeak) 1.5 else 0.0
                    }
                    else -> {
                        if (st.isWeak) score += 3.0
                    }
                }
                score += (3 - min(st.masteryLevel, 3)) * 0.6
                score += min(st.wrongCount, 5) * 0.35
                score
            }.thenBy { card ->
                // Break ties by least recently reviewed (oldest timestamp wins)
                -(states[card.id]?.lastReviewedAt ?: 0L)
            }.thenBy { card ->
                -card.id
            }
        )
    }

    /**
     * Maintains target MC:ShortAnswer ratio (default 70:30) while promoting familiar cards from MC to SA.
     */
    private fun pickQuestionType(
        cardQuestions: List<QuizQuestionEntity>,
        state: ReviewStateEntity,
        settings: AppSettings,
        recentQuestionTypes: List<String>
    ): QuizQuestionEntity {
        val mcQuestions = cardQuestions.filter { it.type == QuestionType.MULTIPLE_CHOICE }
        val saQuestions = cardQuestions.filter { it.type == QuestionType.SHORT_ANSWER }

        if (saQuestions.isEmpty()) return mcQuestions.first()
        if (mcQuestions.isEmpty()) return saQuestions.first()

        // Start cleanly with Multiple Choice when there is no recent history yet
        if (recentQuestionTypes.isEmpty()) {
            return mcQuestions.last()
        }

        val targetSaRatio = (100 - settings.mcPercent) / 100.0
        val window = recentQuestionTypes.takeLast(10)
        val currentSaRatio = window.count { it == QuestionType.SHORT_ANSWER }.toDouble() / window.size

        val cardReadyForSa = state.masteryLevel >= 1 || state.consecutiveCorrect >= 1
        val needsSaToKeepRatio = currentSaRatio < targetSaRatio

        return if (cardReadyForSa && needsSaToKeepRatio) {
            saQuestions.last()
        } else if (!cardReadyForSa && window.size >= 3 && currentSaRatio < targetSaRatio * 0.5) {
            saQuestions.first()
        } else {
            mcQuestions.last()
        }
    }

    fun updateReviewState(
        current: ReviewStateEntity,
        isCorrect: Boolean,
        questionType: String,
        weakThreshold: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): ReviewStateEntity {
        val newTotal = current.totalAttempts + 1
        return if (isCorrect) {
            val newConsecutive = current.consecutiveCorrect + 1
            val newEase = min(3.0, current.easeFactor + 0.1)
            val newIntervalHours = when (newConsecutive) {
                1 -> 6.0
                2 -> 24.0
                else -> min(24.0 * 60, current.intervalHours * newEase)
            }
            val masteryGain = if (questionType == QuestionType.SHORT_ANSWER) 2 else 1
            val newMastery = min(5, current.masteryLevel + masteryGain)
            val clearedWeak = current.isWeak && newConsecutive >= 2
            current.copy(
                masteryLevel = newMastery,
                totalAttempts = newTotal,
                consecutiveCorrect = newConsecutive,
                intervalHours = newIntervalHours,
                easeFactor = newEase,
                lastReviewedAt = nowMillis,
                nextReviewAt = nowMillis + (newIntervalHours * 3_600_000L).toLong(),
                isWeak = if (clearedWeak) false else current.isWeak
            )
        } else {
            val newWrong = current.wrongCount + 1
            val newEase = max(1.3, current.easeFactor - 0.25)
            val newMastery = max(0, current.masteryLevel - 1)
            val becameWeak = newWrong >= weakThreshold
            current.copy(
                masteryLevel = newMastery,
                totalAttempts = newTotal,
                wrongCount = newWrong,
                consecutiveCorrect = 0,
                intervalHours = 0.5,
                easeFactor = newEase,
                lastReviewedAt = nowMillis,
                nextReviewAt = nowMillis + 1_800_000L,
                isWeak = current.isWeak || becameWeak
            )
        }
    }

    fun defaultReviewState(card: LearningCardEntity): ReviewStateEntity = ReviewStateEntity(
        cardId = card.id,
        subjectId = card.subjectId,
        unitId = card.unitId,
        unitOrderIndex = card.unitOrderIndex
    )
}
