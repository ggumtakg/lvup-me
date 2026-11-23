package com.lvupme.app.data.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.lvupme.app.data.local.AttemptLogEntity
import com.lvupme.app.data.local.CurriculumUnitEntity
import com.lvupme.app.data.local.ExamSessionEntity
import com.lvupme.app.data.local.ExamType
import com.lvupme.app.data.local.LearningCardEntity
import com.lvupme.app.data.local.LvUpDatabase
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.data.local.QuizQuestionEntity
import com.lvupme.app.data.local.ReviewStateEntity
import com.lvupme.app.data.local.SettingsRepository
import com.lvupme.app.data.local.SubjectEntity
import com.lvupme.app.data.local.UnitStatus
import com.lvupme.app.data.remote.GeneratedCardDto
import com.lvupme.app.data.remote.GeminiClient
import com.lvupme.app.data.remote.LevelEvaluationDto
import com.lvupme.app.data.remote.LevelTestQuestionDto
import com.lvupme.app.domain.ScheduledQuestion
import com.lvupme.app.domain.SrsScheduler
import com.lvupme.app.notification.NotificationHelper
import com.lvupme.app.widget.LvUpWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class SubjectDashboardItem(
    val subject: SubjectEntity,
    val activeUnitTitle: String,
    val completedUnits: Int,
    val totalUnits: Int,
    val totalCards: Int,
    val weakCardsCount: Int
)

data class WeakCardItem(
    val subjectName: String,
    val card: LearningCardEntity,
    val state: ReviewStateEntity
)

data class UnitReadiness(
    val unit: CurriculumUnitEntity?,
    val readiness: Float,
    val allUnitsCompleted: Boolean = false
)

data class StudyHistorySnapshot(
    val totalAttempts: Int,
    val recentCardIds: List<Long>,
    val recentPastCardIds: List<Long>,
    val recentQuestionTypes: List<String>
)

class LearningRepository(
    private val context: Context,
    private val db: LvUpDatabase,
    val settingsRepository: SettingsRepository,
    val geminiClient: GeminiClient
) {
    private val scheduler = SrsScheduler()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefetchMutex = Mutex()

    fun observeSubjects(): Flow<List<SubjectEntity>> = db.subjectDao().observeAll()

    fun observeSubject(subjectId: Long): Flow<SubjectEntity?> = db.subjectDao().observeById(subjectId)

    fun observeUnits(subjectId: Long): Flow<List<CurriculumUnitEntity>> =
        db.curriculumDao().observeUnits(subjectId)

    fun observeExams(subjectId: Long): Flow<List<ExamSessionEntity>> =
        db.examDao().observeBySubject(subjectId)

    fun observeAllExams(): Flow<List<ExamSessionEntity>> = db.examDao().observeAll()

    private fun dateChangeFlow(): Flow<LocalDate> = flow {
        while (true) {
            val today = LocalDate.now()
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay()
            val waitMs = Duration.between(LocalDateTime.now(), nextMidnight)
                .toMillis()
                .coerceIn(1_000L, 24 * 3_600_000L)
            delay(waitMs + 500L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayCount(): Flow<Int> = dateChangeFlow().flatMapLatest { today ->
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.attemptDao().observeCountSince(startOfDay)
    }

    fun observeStreakDays(): Flow<Int> = db.attemptDao().observeDistinctStudyDates().map { dateStrings ->
        if (dateStrings.isEmpty()) return@map 0
        val days = dateStrings.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        var streak = 0
        var cursor = LocalDate.now()
        if (cursor !in days) {
            cursor = cursor.minusDays(1)
        }
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        streak
    }

    fun observeSubjectDashboards(): Flow<List<SubjectDashboardItem>> {
        return combine(
            db.subjectDao().observeAll(),
            db.curriculumDao().observeAll(),
            db.cardDao().observeAll(),
            db.reviewDao().observeAll()
        ) { subjects, allUnits, allCards, allStates ->
            val unitsBySubject = allUnits.groupBy { it.subjectId }
            val cardsBySubject = allCards.groupBy { it.subjectId }
            val statesBySubject = allStates.groupBy { it.subjectId }

            subjects.map { subject ->
                val units = unitsBySubject[subject.id].orEmpty()
                val activeUnit = units.firstOrNull { it.status == UnitStatus.ACTIVE }
                    ?: units.lastOrNull()
                val cards = cardsBySubject[subject.id].orEmpty()
                val states = statesBySubject[subject.id].orEmpty()
                val allDone = units.isNotEmpty() && units.all { it.status == UnitStatus.COMPLETED }
                SubjectDashboardItem(
                    subject = subject,
                    activeUnitTitle = when {
                        allDone -> "모든 단원 완료 🎉"
                        activeUnit != null -> activeUnit.title
                        else -> "준비 중"
                    },
                    completedUnits = units.count { it.status == UnitStatus.COMPLETED },
                    totalUnits = units.size,
                    totalCards = cards.size,
                    weakCardsCount = states.count { it.isWeak }
                )
            }
        }
    }

    fun observeWeakCards(): Flow<List<WeakCardItem>> {
        return db.reviewDao().observeWeakCardsJoined().map { rows ->
            rows.map { row ->
                WeakCardItem(
                    subjectName = row.subjectName,
                    card = row.card,
                    state = row.state
                )
            }
        }
    }

    suspend fun getRecentSessionHistory(subjectId: Long): StudyHistorySnapshot {
        val total = db.attemptDao().getCountBySubject(subjectId)
        val recentDesc = db.attemptDao().getRecentBySubject(subjectId, limit = 30)
        val recentAsc = recentDesc.reversed()
        return StudyHistorySnapshot(
            totalAttempts = total,
            recentCardIds = recentAsc.map { it.cardId },
            recentPastCardIds = recentAsc.filter { it.isFromPastReview }.map { it.cardId },
            recentQuestionTypes = recentAsc.map { it.questionType }
        )
    }

    suspend fun createSubjectFromEvaluation(
        name: String,
        subTopic: String,
        goalSummary: String,
        ttsEnabled: Boolean,
        ttsLocale: String,
        evaluation: LevelEvaluationDto,
        levelTestCorrect: Int,
        levelTestTotal: Int
    ): Long = withContext(NonCancellable) {
        val existingSubjects = db.subjectDao().getAll()
        val shouldBeMain = existingSubjects.isEmpty()

        val subjectId = db.withTransaction {
            val sId = db.subjectDao().insert(
                SubjectEntity(
                    name = name,
                    subTopic = subTopic,
                    goalSummary = goalSummary,
                    userLevel = evaluation.assessedLevel,
                    isMainForWidget = shouldBeMain,
                    ttsEnabled = ttsEnabled,
                    ttsLocale = ttsLocale
                )
            )
            val rawUnits = evaluation.units.ifEmpty {
                listOf(
                    com.lvupme.app.data.remote.CurriculumUnitDraftDto(
                        orderIndex = 1,
                        title = "$subTopic 기초 핵심",
                        description = "$name $subTopic 핵심 개념 입문"
                    )
                )
            }
            val unitsToInsert = rawUnits.sortedBy { it.orderIndex }.mapIndexed { idx, u ->
                CurriculumUnitEntity(
                    subjectId = sId,
                    orderIndex = idx + 1,
                    title = u.title,
                    description = u.description,
                    status = if (idx == 0) UnitStatus.ACTIVE else UnitStatus.LOCKED,
                    isPrefetched = false
                )
            }
            db.curriculumDao().insertAll(unitsToInsert)
            val scorePct = if (levelTestTotal > 0) (levelTestCorrect * 100) / levelTestTotal else 0
            db.examDao().insert(
                ExamSessionEntity(
                    subjectId = sId,
                    examType = ExamType.LEVEL_TEST,
                    totalQuestions = levelTestTotal,
                    correctCount = levelTestCorrect,
                    scorePercent = scorePct,
                    passed = true,
                    aiFeedback = evaluation.feedback
                )
            )
            sId
        }

        runCatching { ensureActiveUnitPrefetched(subjectId) }
        refreshWidget()
        subjectId
    }

    suspend fun setMainWidgetSubject(subjectId: Long) {
        db.subjectDao().setMainWidgetSubject(subjectId)
        settingsRepository.updateSettings { it.copy(widgetCardIndex = 0) }
        refreshWidget()
    }

    suspend fun updateSubjectTts(subjectId: Long, enabled: Boolean, locale: String) {
        val subject = db.subjectDao().getById(subjectId) ?: return
        db.subjectDao().update(subject.copy(ttsEnabled = enabled, ttsLocale = locale))
    }

    suspend fun deleteSubject(subject: SubjectEntity) {
        val remainingEmpty = db.withTransaction {
            db.attemptDao().deleteBySubject(subject.id)
            db.subjectDao().delete(subject)
            val remaining = db.subjectDao().getAll()
            if (remaining.isNotEmpty() && remaining.none { it.isMainForWidget }) {
                db.subjectDao().setMainWidgetSubject(remaining.first().id)
            }
            remaining.isEmpty()
        }
        if (remainingEmpty || subject.isMainForWidget) {
            NotificationHelper.cancelLockScreenCard(context)
        }
        refreshWidget()
    }

    suspend fun ensureActiveUnitPrefetched(subjectId: Long): Int = prefetchMutex.withLock {
        val subject = db.subjectDao().getById(subjectId) ?: return@withLock 0
        val activeUnit = db.curriculumDao().getActiveUnit(subjectId) ?: return@withLock 0
        val existingCards = db.cardDao().getByUnit(activeUnit.id)
        if (activeUnit.isPrefetched && existingCards.isNotEmpty()) return@withLock existingCards.size

        val batchSize = settingsRepository.getSettings().prefetchBatchSize
        val batch = geminiClient.generateUnitContentBatch(
            subject = subject.name,
            subTopic = subject.subTopic,
            userLevel = subject.userLevel,
            unitTitle = activeUnit.title,
            unitDescription = activeUnit.description,
            count = batchSize,
            existingTitles = existingCards.map { it.title }
        )

        if (batch.cards.isEmpty()) return@withLock 0
        saveGeneratedCards(subject.id, activeUnit, batch.cards)
        db.curriculumDao().update(activeUnit.copy(isPrefetched = true))
        refreshWidget()
        batch.cards.size
    }

    suspend fun prefetchUpcomingForAllSubjects(): Int = prefetchMutex.withLock {
        var totalGenerated = 0
        val subjects = db.subjectDao().getAll()
        val batchSize = settingsRepository.getSettings().prefetchBatchSize
        for (subject in subjects) {
            val units = db.curriculumDao().getUnits(subject.id)
            val active = units.firstOrNull { it.status == UnitStatus.ACTIVE } ?: continue
            val nextLocked = units.firstOrNull { it.orderIndex == active.orderIndex + 1 }
            for (unit in listOfNotNull(active, nextLocked)) {
                val existing = db.cardDao().getByUnit(unit.id)
                if (unit.isPrefetched && existing.isNotEmpty()) continue
                val batch = geminiClient.generateUnitContentBatch(
                    subject = subject.name,
                    subTopic = subject.subTopic,
                    userLevel = subject.userLevel,
                    unitTitle = unit.title,
                    unitDescription = unit.description,
                    count = batchSize,
                    existingTitles = existing.map { it.title }
                )
                if (batch.cards.isNotEmpty()) {
                    saveGeneratedCards(subject.id, unit, batch.cards)
                    db.curriculumDao().update(unit.copy(isPrefetched = true))
                    totalGenerated += batch.cards.size
                }
            }
        }
        if (totalGenerated > 0) refreshWidget()
        totalGenerated
    }

    private suspend fun saveGeneratedCards(
        subjectId: Long,
        unit: CurriculumUnitEntity,
        cards: List<GeneratedCardDto>
    ) {
        db.withTransaction {
            for (dto in cards) {
                val cardId = db.cardDao().insert(
                    LearningCardEntity(
                        subjectId = subjectId,
                        unitId = unit.id,
                        unitOrderIndex = unit.orderIndex,
                        title = dto.title.trim(),
                        microSummary = dto.microSummary.trim(),
                        detailExplanation = dto.detailExplanation.trim(),
                        exampleText = dto.exampleText.trim(),
                        ttsText = dto.ttsText.trim(),
                        isAiReinforced = false
                    )
                )
                db.reviewDao().upsert(
                    ReviewStateEntity(
                        cardId = cardId,
                        subjectId = subjectId,
                        unitId = unit.id,
                        unitOrderIndex = unit.orderIndex
                    )
                )
                val mcAnswer = dto.multipleChoice.answerText.trim()
                val mcChoices = dto.multipleChoice.choices.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    .let { list -> if (mcAnswer in list) list else (list.take(3) + mcAnswer).distinct() }

                val mc = QuizQuestionEntity(
                    subjectId = subjectId,
                    unitId = unit.id,
                    cardId = cardId,
                    type = QuestionType.MULTIPLE_CHOICE,
                    prompt = dto.multipleChoice.prompt.trim(),
                    choicesJson = json.encodeToString(mcChoices),
                    answerText = mcAnswer,
                    acceptedAnswersJson = json.encodeToString(
                        (dto.multipleChoice.acceptedAnswers + mcAnswer).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    ),
                    wrongReason = dto.multipleChoice.wrongReason.trim()
                )
                val saAnswer = dto.shortAnswer.answerText.trim()
                val sa = QuizQuestionEntity(
                    subjectId = subjectId,
                    unitId = unit.id,
                    cardId = cardId,
                    type = QuestionType.SHORT_ANSWER,
                    prompt = dto.shortAnswer.prompt.trim(),
                    choicesJson = "[]",
                    answerText = saAnswer,
                    acceptedAnswersJson = json.encodeToString(
                        (dto.shortAnswer.acceptedAnswers + saAnswer).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    ),
                    wrongReason = dto.shortAnswer.wrongReason.trim()
                )
                db.quizDao().insertAll(listOf(mc, sa))
            }
        }
    }

    suspend fun nextStudyQuestion(
        subjectId: Long,
        sessionIndex: Int,
        recentCardIds: List<Long>,
        recentQuestionTypes: List<String>,
        recentPastCardIds: List<Long> = emptyList(),
        pinnedCardId: Long? = null
    ): ScheduledQuestion? {
        val units = db.curriculumDao().getUnits(subjectId)
        val activeUnit = units.firstOrNull { it.status == UnitStatus.ACTIVE }
            ?: units.lastOrNull()
            ?: return null

        val cards = db.cardDao().getBySubject(subjectId)
        val questions = db.quizDao().getBySubject(subjectId)
        val states = db.reviewDao().getBySubject(subjectId).associateBy { it.cardId }

        if (pinnedCardId != null) {
            val card = cards.firstOrNull { it.id == pinnedCardId }
            val q = questions.firstOrNull { it.cardId == pinnedCardId }
            if (card != null && q != null) {
                return ScheduledQuestion(
                    card = card,
                    question = q,
                    reviewState = states[card.id] ?: scheduler.defaultReviewState(card),
                    isFromPastReview = card.unitId != activeUnit.id
                )
            }
        }

        val settings = settingsRepository.getSettings()
        return scheduler.selectNextQuestion(
            activeUnitId = activeUnit.id,
            activeUnitOrder = activeUnit.orderIndex,
            cards = cards,
            questions = questions,
            states = states,
            settings = settings,
            sessionIndex = sessionIndex,
            recentCardIds = recentCardIds,
            recentQuestionTypes = recentQuestionTypes,
            recentPastCardIds = recentPastCardIds
        )
    }

    /**
     * Records a finalized quiz answer inside NonCancellable DB transaction,
     * and triggers AI weakness reinforcement if wrongCount reaches threshold.
     */
    suspend fun recordStudyAnswer(
        scheduled: ScheduledQuestion,
        isCorrect: Boolean,
        userInput: String
    ): Boolean {
        val settings = settingsRepository.getSettings()
        val latestState = db.reviewDao().getByCard(scheduled.card.id) ?: scheduled.reviewState
        val updatedState = scheduler.updateReviewState(
            current = latestState,
            isCorrect = isCorrect,
            questionType = scheduled.question.type,
            weakThreshold = settings.weakWrongThreshold
        )

        withContext(NonCancellable) {
            db.withTransaction {
                db.reviewDao().upsert(updatedState)
                db.attemptDao().insert(
                    AttemptLogEntity(
                        subjectId = scheduled.card.subjectId,
                        unitId = scheduled.card.unitId,
                        cardId = scheduled.card.id,
                        questionId = scheduled.question.id,
                        questionType = scheduled.question.type,
                        isFromPastReview = scheduled.isFromPastReview,
                        isCorrect = isCorrect,
                        userInput = userInput
                    )
                )
            }
        }

        if (!isCorrect && updatedState.isWeak && !updatedState.reinforcementGenerated) {
            return runCatching {
                generateAndAttachReinforcement(scheduled.card, updatedState)
            }.getOrDefault(false)
        }
        return false
    }

    private suspend fun generateAndAttachReinforcement(
        card: LearningCardEntity,
        state: ReviewStateEntity
    ): Boolean {
        val subject = db.subjectDao().getById(card.subjectId) ?: return false
        val dto = geminiClient.generateReinforcementCard(
            subject = subject.name,
            subTopic = subject.subTopic,
            userLevel = subject.userLevel,
            originalTitle = card.title,
            originalSummary = card.microSummary,
            wrongCount = state.wrongCount
        )
        withContext(NonCancellable) {
            db.withTransaction {
                val newCardId = db.cardDao().insert(
                    LearningCardEntity(
                        subjectId = card.subjectId,
                        unitId = card.unitId,
                        unitOrderIndex = card.unitOrderIndex,
                        title = dto.title.trim(),
                        microSummary = dto.microSummary.trim(),
                        detailExplanation = dto.detailExplanation.trim(),
                        exampleText = dto.exampleText.trim(),
                        ttsText = dto.ttsText.ifBlank { card.ttsText }.trim(),
                        isAiReinforced = true
                    )
                )
                db.reviewDao().upsert(
                    ReviewStateEntity(
                        cardId = newCardId,
                        subjectId = card.subjectId,
                        unitId = card.unitId,
                        unitOrderIndex = card.unitOrderIndex
                    )
                )
                val mcAns = dto.multipleChoice.answerText.trim()
                val mcChoices = dto.multipleChoice.choices.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    .let { list -> if (mcAns in list) list else (list.take(3) + mcAns).distinct() }
                val saAns = dto.shortAnswer.answerText.trim()
                db.quizDao().insertAll(
                    listOf(
                        QuizQuestionEntity(
                            subjectId = card.subjectId,
                            unitId = card.unitId,
                            cardId = newCardId,
                            type = QuestionType.MULTIPLE_CHOICE,
                            prompt = dto.multipleChoice.prompt.trim(),
                            choicesJson = json.encodeToString(mcChoices),
                            answerText = mcAns,
                            acceptedAnswersJson = json.encodeToString(
                                (dto.multipleChoice.acceptedAnswers + mcAns).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                            ),
                            wrongReason = dto.multipleChoice.wrongReason.trim(),
                            isAiReinforced = true
                        ),
                        QuizQuestionEntity(
                            subjectId = card.subjectId,
                            unitId = card.unitId,
                            cardId = newCardId,
                            type = QuestionType.SHORT_ANSWER,
                            prompt = dto.shortAnswer.prompt.trim(),
                            choicesJson = "[]",
                            answerText = saAns,
                            acceptedAnswersJson = json.encodeToString(
                                (dto.shortAnswer.acceptedAnswers + saAns).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                            ),
                            wrongReason = dto.shortAnswer.wrongReason.trim(),
                            isAiReinforced = true
                        )
                    )
                )
                db.reviewDao().upsert(state.copy(reinforcementGenerated = true))
            }
        }
        return true
    }

    suspend fun getUnitReadiness(subjectId: Long): UnitReadiness {
        val units = db.curriculumDao().getUnits(subjectId)
        if (units.isEmpty()) return UnitReadiness(null, 0f, allUnitsCompleted = false)

        val activeUnit = units.firstOrNull { it.status == UnitStatus.ACTIVE }
        if (activeUnit == null) {
            val allDone = units.all { it.status == UnitStatus.COMPLETED }
            return UnitReadiness(units.lastOrNull(), if (allDone) 1f else 0f, allUnitsCompleted = allDone)
        }

        val states = db.reviewDao().getByUnit(subjectId, activeUnit.id)
        if (states.isEmpty()) return UnitReadiness(activeUnit, 0f, allUnitsCompleted = false)
        val practiced = states.count { it.masteryLevel >= 1 }
        return UnitReadiness(activeUnit, practiced.toFloat() / states.size.toFloat(), allUnitsCompleted = false)
    }

    suspend fun buildUnitCheckpointQuestions(subjectId: Long, unitId: Long): List<LevelTestQuestionDto> {
        val unitQuestions = db.quizDao().getByUnit(unitId)
        val mc = unitQuestions.filter { it.type == QuestionType.MULTIPLE_CHOICE }.shuffled()
        val sa = unitQuestions.filter { it.type == QuestionType.SHORT_ANSWER }.shuffled()
        val targetTotal = minOf(10, unitQuestions.size)
        if (targetTotal == 0) return emptyList()

        val targetMc = (targetTotal * 7) / 10
        val targetSa = targetTotal - targetMc

        val pickedMc = mc.take(targetMc).toMutableList()
        val pickedSa = sa.take(targetSa).toMutableList()
        // Backfill from opposite pool if one type has fewer questions than target
        val shortage = targetTotal - (pickedMc.size + pickedSa.size)
        if (shortage > 0) {
            val extraMc = mc.drop(pickedMc.size).take(shortage)
            pickedMc.addAll(extraMc)
            val remainingShortage = targetTotal - (pickedMc.size + pickedSa.size)
            if (remainingShortage > 0) {
                pickedSa.addAll(sa.drop(pickedSa.size).take(remainingShortage))
            }
        }

        return (pickedMc + pickedSa).shuffled().mapIndexed { idx, q ->
            LevelTestQuestionDto(
                id = idx + 1,
                type = q.type,
                prompt = q.prompt,
                choices = runCatching { json.decodeFromString<List<String>>(q.choicesJson) }.getOrDefault(emptyList()),
                answerText = q.answerText,
                acceptedAnswers = runCatching { json.decodeFromString<List<String>>(q.acceptedAnswersJson) }.getOrDefault(emptyList()),
                explanation = q.wrongReason
            )
        }
    }

    suspend fun generateMockExamQuestions(subjectId: Long): Pair<String, List<LevelTestQuestionDto>> {
        val subject = db.subjectDao().getById(subjectId)
            ?: error("과목을 찾을 수 없습니다.")
        val cards = db.cardDao().getBySubject(subjectId)
        val concepts = cards.map { "${it.title}: ${it.microSummary}" }
            .ifEmpty { listOf("${subject.name} - ${subject.subTopic}") }
        val mock = geminiClient.generateMockExam(
            subject = subject.name,
            subTopic = subject.subTopic,
            userLevel = subject.userLevel,
            coveredConcepts = concepts,
            questionCount = 10
        )
        return mock.title to mock.questions
    }

    suspend fun completeExam(
        subjectId: Long,
        unitId: Long?,
        examType: String,
        totalQuestions: Int,
        correctCount: Int,
        wrongPrompts: List<String>
    ): ExamSessionEntity = withContext(NonCancellable) {
        val settings = settingsRepository.getSettings()
        val scorePercent = if (totalQuestions > 0) (correctCount * 100) / totalQuestions else 0
        val passed = scorePercent >= settings.checkpointPassPercent

        val subject = db.subjectDao().getById(subjectId)
        val aiFeedback = runCatching {
            if (subject != null) {
                val fb = geminiClient.generateExamFeedback(
                    subject = subject.name,
                    examTitle = if (examType == ExamType.UNIT_CHECKPOINT) "단원 체크포인트 시험" else "AI 종합 모의시험",
                    scorePercent = scorePercent,
                    wrongItemsSummary = wrongPrompts.ifEmpty { listOf("없음 (전 문항 정답)") }.joinToString("\n")
                )
                buildString {
                    append(fb.summary)
                    if (fb.weakTopics.isNotEmpty()) {
                        append("\n\n보강 필요: ")
                        append(fb.weakTopics.joinToString(", "))
                    }
                    if (fb.nextActionTip.isNotBlank()) {
                        append("\n\n추천 행동: ")
                        append(fb.nextActionTip)
                    }
                }
            } else ""
        }.getOrElse {
            if (passed) "합격 기준(${settings.checkpointPassPercent}%)을 통과했습니다! 다음 단계로 진행하세요."
            else "합격 기준(${settings.checkpointPassPercent}%)에 조금 못 미쳤습니다. 오답 개념을 복습한 뒤 재응시해 주세요."
        }

        val session = ExamSessionEntity(
            subjectId = subjectId,
            unitId = unitId,
            examType = examType,
            totalQuestions = totalQuestions,
            correctCount = correctCount,
            scorePercent = scorePercent,
            passed = passed,
            aiFeedback = aiFeedback
        )

        db.withTransaction {
            db.examDao().insert(session)
            if (examType == ExamType.UNIT_CHECKPOINT && passed && unitId != null) {
                val units = db.curriculumDao().getUnits(subjectId)
                val current = units.firstOrNull { it.id == unitId }
                if (current != null) {
                    db.curriculumDao().update(current.copy(status = UnitStatus.COMPLETED))
                    val next = units.firstOrNull { it.orderIndex == current.orderIndex + 1 }
                    if (next != null && next.status == UnitStatus.LOCKED) {
                        db.curriculumDao().update(next.copy(status = UnitStatus.ACTIVE))
                    }
                }
            }
        }

        if (examType == ExamType.UNIT_CHECKPOINT && passed) {
            runCatching { ensureActiveUnitPrefetched(subjectId) }
        }
        refreshWidget()
        session
    }

    suspend fun getMainWidgetCard(advanceIndex: Boolean = false): Triple<SubjectEntity, CurriculumUnitEntity?, LearningCardEntity>? {
        val mainSubject = db.subjectDao().getMainWidgetSubject()
            ?: db.subjectDao().getAll().firstOrNull()
            ?: return null
        val units = db.curriculumDao().getUnits(mainSubject.id)
        val activeUnit = units.firstOrNull { it.status == UnitStatus.ACTIVE } ?: units.lastOrNull()
        val unitCards = if (activeUnit != null) {
            db.cardDao().getByUnit(activeUnit.id).ifEmpty { db.cardDao().getBySubject(mainSubject.id) }
        } else {
            db.cardDao().getBySubject(mainSubject.id)
        }
        if (unitCards.isEmpty()) return null

        val idx = if (advanceIndex) {
            settingsRepository.advanceWidgetCardIndex(unitCards.size)
        } else {
            settingsRepository.getSettings().widgetCardIndex % unitCards.size
        }
        return Triple(mainSubject, activeUnit, unitCards[idx])
    }

    suspend fun refreshWidget() {
        runCatching { LvUpWidget().updateAll(context) }
    }
}
