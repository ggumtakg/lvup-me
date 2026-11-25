package com.lvupme.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lvupme.app.AppContainer
import com.lvupme.app.data.local.CurriculumUnitEntity
import com.lvupme.app.data.local.ExamSessionEntity
import com.lvupme.app.data.local.ExamType
import com.lvupme.app.data.local.QuestionType
import com.lvupme.app.data.local.UnitStatus
import com.lvupme.app.data.remote.ChatMessageDto
import com.lvupme.app.data.remote.LevelEvaluationDto
import com.lvupme.app.data.remote.LevelTestQuestionDto
import com.lvupme.app.domain.AnswerGrader
import com.lvupme.app.domain.ScheduledQuestion
import com.lvupme.app.ui.onboarding.OnboardingProposal
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

fun <T : ViewModel> lvUpViewModelFactory(creator: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }
}

data class ChatUiMessage(
    val id: Long,
    val role: String,
    val content: String
)

class OnboardingChatViewModel(private val container: AppContainer) : ViewModel() {
    private var nextMsgId = 1L

    val messages = mutableStateListOf(
        ChatUiMessage(
            id = nextMsgId++,
            role = "assistant",
            content = "안녕하세요! 어떤 과목을 레벨업해 볼까요? ⚡\n예를 들어 '영어 - 비즈니스 영단어', 'SQL - JOIN과 서브쿼리'처럼 관심사를 알려주시면 대화를 통해 상세 분야를 함께 정해드릴게요!"
        )
    )

    var inputText by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set
    var failedMessage by mutableStateOf<String?>(null)
        private set

    var readyProposal by mutableStateOf<OnboardingProposal?>(null)
        private set
    var editSubject by mutableStateOf("")
    var editSubTopic by mutableStateOf("")
    var editTtsEnabled by mutableStateOf(false)
    var editTtsLocale by mutableStateOf("en-US")

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isLoading) return
        messages.add(ChatUiMessage(id = nextMsgId++, role = "user", content = trimmed))
        inputText = ""
        errorMsg = null
        failedMessage = null
        requestReply(trimmed)
    }

    fun retryFailedMessage() {
        val last = failedMessage ?: return
        if (isLoading) return
        errorMsg = null
        failedMessage = null
        requestReply(last)
    }

    private fun requestReply(lastUserText: String) {
        isLoading = true
        viewModelScope.launch {
            runCatching {
                val history = messages.map { ChatMessageDto(role = it.role, content = it.content) }
                container.geminiClient.onboardingChat(history)
            }.onSuccess { reply ->
                messages.add(ChatUiMessage(id = nextMsgId++, role = "assistant", content = reply.assistantReply))
                if (reply.readyForLevelTest && reply.proposedSubject.isNotBlank()) {
                    editSubject = reply.proposedSubject
                    editSubTopic = reply.proposedSubTopic.ifBlank { reply.proposedSubject }
                    editTtsEnabled = reply.suggestedTtsEnabled
                    editTtsLocale = reply.suggestedTtsLocale.ifBlank { "en-US" }
                    readyProposal = OnboardingProposal(
                        subject = editSubject,
                        subTopic = editSubTopic,
                        goalSummary = reply.goalSummary,
                        ttsEnabled = editTtsEnabled,
                        ttsLocale = editTtsLocale
                    )
                } else {
                    readyProposal = null
                }
            }.onFailure { e ->
                errorMsg = e.message ?: "AI 응답 중 오류가 발생했습니다."
                failedMessage = lastUserText
            }
            isLoading = false
        }
    }

    fun buildConfirmedProposal(): OnboardingProposal? {
        val base = readyProposal ?: return null
        return base.copy(
            subject = editSubject.trim().ifBlank { base.subject },
            subTopic = editSubTopic.trim().ifBlank { base.subTopic },
            ttsEnabled = editTtsEnabled,
            ttsLocale = editTtsLocale.trim().ifBlank { "en-US" }
        )
    }
}

class LevelTestViewModel(
    private val container: AppContainer,
    val proposal: OnboardingProposal
) : ViewModel() {
    var isLoading by mutableStateOf(true)
        private set
    var loadingMessage by mutableStateOf("AI가 '${proposal.subject}' 수준 테스트를 만들고 있어요...")
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    var questions by mutableStateOf<List<LevelTestQuestionDto>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var shortInput by mutableStateOf("")
    val answerRecords = mutableStateListOf<Triple<LevelTestQuestionDto, String, Boolean>>()

    var evaluation by mutableStateOf<LevelEvaluationDto?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set

    init {
        loadTest()
    }

    fun loadTest() {
        isLoading = true
        errorMsg = null
        loadingMessage = "AI가 '${proposal.subject} - ${proposal.subTopic}' 수준 테스트를 출제 중입니다..."
        viewModelScope.launch {
            runCatching {
                container.geminiClient.generateLevelTest(
                    subject = proposal.subject,
                    subTopic = proposal.subTopic,
                    goalSummary = proposal.goalSummary
                )
            }.onSuccess { dto ->
                if (dto.questions.isEmpty()) {
                    errorMsg = "AI가 수준 테스트 문항을 생성하지 못했습니다. 다시 시도해 주세요."
                } else {
                    questions = dto.questions
                    currentIndex = 0
                    answerRecords.clear()
                }
                isLoading = false
            }.onFailure { e ->
                errorMsg = e.message ?: "수준 테스트 생성 실패"
                isLoading = false
            }
        }
    }

    fun submitAnswer(q: LevelTestQuestionDto, userAns: String) {
        val correct = if (q.type == QuestionType.SHORT_ANSWER) {
            AnswerGrader.gradeShortAnswer(userAns, q.answerText, q.acceptedAnswers).isCorrect
        } else {
            AnswerGrader.normalize(userAns) == AnswerGrader.normalize(q.answerText)
        }
        answerRecords.add(Triple(q, userAns, correct))
        shortInput = ""

        if (currentIndex + 1 < questions.size) {
            currentIndex++
        } else {
            evaluateAnswers()
        }
    }

    fun evaluateAnswers() {
        isLoading = true
        errorMsg = null
        loadingMessage = "수준을 진단하고 맞춤형 커리큘럼을 생성 중입니다..."
        viewModelScope.launch {
            val summary = answerRecords.mapIndexed { idx, (ques, ans, ok) ->
                "${idx + 1}. [${if (ok) "정답" else "오답"}] ${ques.prompt} (내 답: $ans / 정답: ${ques.answerText})"
            }.joinToString("\n")
            runCatching {
                container.geminiClient.evaluateLevelAndBuildCurriculum(
                    subject = proposal.subject,
                    subTopic = proposal.subTopic,
                    goalSummary = proposal.goalSummary,
                    resultsSummary = summary
                )
            }.onSuccess { eval ->
                evaluation = eval
                isLoading = false
            }.onFailure { e ->
                errorMsg = e.message ?: "수준 평가 중 오류가 발생했습니다."
                isLoading = false
            }
        }
    }

    fun saveCurriculumAndStart(onFinished: (Long) -> Unit) {
        val eval = evaluation ?: return
        if (isSaving) return
        isSaving = true
        val correctCount = answerRecords.count { it.third }
        val total = answerRecords.size
        // Run creation on appScope so screen rotation/back doesn't corrupt partial saves
        container.appScope.launch {
            val subjectId = runCatching {
                container.learningRepository.createSubjectFromEvaluation(
                    name = proposal.subject,
                    subTopic = proposal.subTopic,
                    goalSummary = proposal.goalSummary,
                    ttsEnabled = proposal.ttsEnabled,
                    ttsLocale = proposal.ttsLocale,
                    evaluation = eval,
                    levelTestCorrect = correctCount,
                    levelTestTotal = total
                )
            }.getOrNull()
            viewModelScope.launch {
                isSaving = false
                if (subjectId != null) {
                    onFinished(subjectId)
                } else {
                    errorMsg = "커리큘럼 저장에 실패했습니다. 다시 시도해 주세요."
                }
            }
        }
    }
}

private data class PendingStudyCommit(
    val scheduled: ScheduledQuestion,
    val isCorrect: Boolean,
    val userInput: String
)

class StudyViewModel(
    private val container: AppContainer,
    val subjectId: Long,
    initialPinnedCardId: Long?,
    initialFocusUnitId: Long? = null
) : ViewModel() {
    private val repo = container.learningRepository
    private val json = Json { ignoreUnknownKeys = true }

    var focusUnitId by mutableStateOf(initialFocusUnitId)
        private set
    var isPastUnitReviewMode by mutableStateOf(false)
        private set

    var activeUnit by mutableStateOf<CurriculumUnitEntity?>(null)
        private set
    var unitReadiness by mutableStateOf(0f)
        private set
    var unitPracticedCount by mutableIntStateOf(0)
        private set
    var unitTotalCards by mutableIntStateOf(0)
        private set
    var allUnitsCompleted by mutableStateOf(false)
        private set

    // 100% completion gate for active unit (방식 A)
    var showUnitCompleteGate by mutableStateOf(false)
        private set
    var continueAfterUnitComplete by mutableStateOf(false)
        private set

    // Focused single-round review state for completed past units (방식 1)
    private val reviewRoundCoveredIds = mutableSetOf<Long>()
    var reviewRoundTargetTotal by mutableIntStateOf(0)
        private set
    var reviewRoundCompletedCount by mutableIntStateOf(0)
        private set
    var reviewRoundCorrectCount by mutableIntStateOf(0)
        private set
    var showPastReviewCompleteGate by mutableStateOf(false)
        private set

    val effectiveReviewRoundTotal: Int
        get() = if (reviewRoundTargetTotal > 0) reviewRoundTargetTotal else unitTotalCards

    val isReadyToFinishActiveUnit: Boolean
        get() = !isPastUnitReviewMode &&
            !allUnitsCompleted &&
            unitTotalCards > 0 &&
            unitReadiness >= 1f &&
            !continueAfterUnitComplete

    val isReadyToFinishPastReviewRound: Boolean
        get() = isPastUnitReviewMode &&
            effectiveReviewRoundTotal > 0 &&
            reviewRoundCompletedCount >= effectiveReviewRoundTotal

    var isLoading by mutableStateOf(true)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    var currentScheduled by mutableStateOf<ScheduledQuestion?>(null)
        private set
    var comboStreak by mutableIntStateOf(0)
        private set
    private var comboBeforeAnswer = 0

    private var historyLoaded = false
    private var sessionIndex = 0
    private val recentCardIds = mutableListOf<Long>()
    private val recentPastCardIds = mutableListOf<Long>()
    private val recentTypes = mutableListOf<String>()
    private var nextPinnedCardId: Long? = initialPinnedCardId

    var showLessonCard by mutableStateOf(false)
    var shortInput by mutableStateOf("")
    var selectedChoice by mutableStateOf<String?>(null)
        private set
    var answered by mutableStateOf(false)
        private set
    var isCorrect by mutableStateOf(false)
        private set
    var feedbackReason by mutableStateOf("")
        private set
    var offerAiRecheck by mutableStateOf(false)
        private set
    var isAiRechecking by mutableStateOf(false)
        private set
    var reinforcementCreated by mutableStateOf(false)
        private set
    var shuffledChoices by mutableStateOf<List<String>>(emptyList())
        private set

    private var pendingCommit: PendingStudyCommit? = null
    private var activeCommitDeferred: Deferred<Pair<Boolean, com.lvupme.app.data.repository.UnitReadiness>>? = null
    private var recheckJob: Job? = null
    private var loadJob: Job? = null
    private var questionToken = 0L
    private var modeToken = 0L
    private var lastAppliedPinnedId: Long? = initialPinnedCardId
    private var lastAppliedFocusUnitId: Long? = initialFocusUnitId
    private var observedInitialUnits = false

    init {
        observeCurriculumUnitChanges()
        loadNextQuestion(prefetchIfMissing = false)
    }

    /**
     * Reactively tracks Room unit status updates (e.g., passing a checkpoint exam on ExamScreen
     * even if the user navigates back before completeExam finishes).
     */
    private fun observeCurriculumUnitChanges() {
        viewModelScope.launch {
            repo.observeUnits(subjectId).collect { units ->
                if (units.isEmpty()) return@collect
                val allDone = units.all { it.status == UnitStatus.COMPLETED }
                val latestActive = units.firstOrNull { it.status == UnitStatus.ACTIVE }
                if (!observedInitialUnits) {
                    observedInitialUnits = true
                    return@collect
                }
                val completionChanged = allDone != allUnitsCompleted
                allUnitsCompleted = allDone

                if (!isPastUnitReviewMode && !isLoading) {
                    val activeUnitChanged = latestActive != null && latestActive.id != activeUnit?.id
                    if (activeUnitChanged || completionChanged) {
                        modeToken++
                        continueAfterUnitComplete = false
                        showUnitCompleteGate = false
                        historyLoaded = false
                        loadNextQuestion(prefetchIfMissing = false, autoSpeak = false)
                    }
                }
            }
        }
    }

    private fun resetReviewRoundState() {
        reviewRoundCoveredIds.clear()
        reviewRoundTargetTotal = 0
        reviewRoundCompletedCount = 0
        reviewRoundCorrectCount = 0
        showPastReviewCompleteGate = false
    }

    private fun cancelInFlightRecheck() {
        recheckJob?.cancel()
        recheckJob = null
        isAiRechecking = false
    }

    fun applyPinnedCard(cardId: Long?) {
        if (cardId == null || cardId <= 0L) return
        if (cardId == lastAppliedPinnedId) return
        if (currentScheduled?.card?.id == cardId && !answered) {
            lastAppliedPinnedId = cardId
            return
        }
        cancelInFlightRecheck()
        lastAppliedPinnedId = cardId
        lastAppliedFocusUnitId = null
        modeToken++
        focusUnitId = null
        resetReviewRoundState()
        showUnitCompleteGate = false
        continueAfterUnitComplete = false
        nextPinnedCardId = cardId
        loadNextQuestion(prefetchIfMissing = false)
    }

    fun applyFocusUnitArg(unitId: Long?) {
        if (unitId == lastAppliedFocusUnitId) return
        lastAppliedFocusUnitId = unitId
        switchToUnit(unitId)
    }

    fun switchToUnit(unitId: Long?) {
        cancelInFlightRecheck()
        modeToken++
        focusUnitId = unitId
        lastAppliedFocusUnitId = unitId
        resetReviewRoundState()
        showUnitCompleteGate = false
        continueAfterUnitComplete = false
        nextPinnedCardId = null
        historyLoaded = false
        recentCardIds.clear()
        recentPastCardIds.clear()
        recentTypes.clear()
        loadNextQuestion(prefetchIfMissing = false)
    }

    fun continueExtraPracticeAfterUnitComplete() {
        continueAfterUnitComplete = true
        showUnitCompleteGate = false
        loadNextQuestion(prefetchIfMissing = false)
    }

    fun restartPastReviewRound() {
        resetReviewRoundState()
        recentCardIds.clear()
        loadNextQuestion(prefetchIfMissing = false)
    }

    /**
     * Immediately flushes any deferred short-answer commit on appScope before navigating away
     * (e.g., to ExamScreen while StudyScreen stays on the back stack).
     */
    fun flushPendingCommit() {
        cancelInFlightRecheck()
        val pending = pendingCommit ?: return
        pendingCommit = null
        offerAiRecheck = false
        val targetUnitSnapshot = focusUnitId
        val deferred = container.appScope.async {
            val generated = repo.recordStudyAnswer(pending.scheduled, pending.isCorrect, pending.userInput)
            val readiness = repo.getUnitReadiness(subjectId, targetUnitId = targetUnitSnapshot)
            generated to readiness
        }
        activeCommitDeferred = deferred
    }

    fun toggleLessonCard() {
        showLessonCard = !showLessonCard
    }

    private fun applyReadiness(readiness: com.lvupme.app.data.repository.UnitReadiness) {
        activeUnit = readiness.unit
        unitReadiness = readiness.readiness
        unitPracticedCount = readiness.practicedCards
        unitTotalCards = readiness.totalCards
        allUnitsCompleted = readiness.allUnitsCompleted
        isPastUnitReviewMode = readiness.isFocusUnitReview
        if (focusUnitId != null && (!readiness.isFocusUnitReview || readiness.unit?.id != focusUnitId)) {
            focusUnitId = null
        }
        if (readiness.isFocusUnitReview && reviewRoundTargetTotal == 0 && readiness.totalCards > 0) {
            reviewRoundTargetTotal = readiness.totalCards
        }
    }

    /**
     * Commits deferred study result on appScope (IO) and updates Compose state strictly on Main (viewModelScope).
     */
    private suspend fun ensurePendingCommitted() {
        val pending = pendingCommit ?: return
        pendingCommit = null
        val targetUnitSnapshot = focusUnitId
        val modeSnap = modeToken
        val qSnap = questionToken
        val deferred = container.appScope.async {
            val generated = repo.recordStudyAnswer(pending.scheduled, pending.isCorrect, pending.userInput)
            val readiness = repo.getUnitReadiness(subjectId, targetUnitId = targetUnitSnapshot)
            generated to readiness
        }
        activeCommitDeferred = deferred
        val (generated, readiness) = deferred.await()
        if (activeCommitDeferred === deferred) activeCommitDeferred = null
        if (qSnap == questionToken && generated) {
            reinforcementCreated = true
        }
        if (modeSnap == modeToken) {
            applyReadiness(readiness)
        }
    }

    fun loadNextQuestion(prefetchIfMissing: Boolean = false, autoSpeak: Boolean = true) {
        cancelInFlightRecheck()
        loadJob?.cancel()
        val myQuestionToken = ++questionToken
        val myModeToken = modeToken
        loadJob = viewModelScope.launch {
            // Show loading immediately before awaiting any pending DB/AI reinforcement commit
            isLoading = true
            errorMsg = null
            answered = false
            selectedChoice = null
            shortInput = ""
            feedbackReason = ""
            offerAiRecheck = false
            reinforcementCreated = false
            showLessonCard = false

            // Ensure any deferred/in-flight answer commit finishes BEFORE selecting next card
            ensurePendingCommitted()
            activeCommitDeferred?.let { deferred ->
                runCatching { deferred.await() }
                if (activeCommitDeferred === deferred) activeCommitDeferred = null
            }

            if (myQuestionToken != questionToken || myModeToken != modeToken) return@launch

            runCatching {
                if (!historyLoaded) {
                    val snap = repo.getRecentSessionHistory(subjectId)
                    sessionIndex = snap.totalAttempts
                    recentCardIds.clear()
                    recentCardIds.addAll(snap.recentCardIds)
                    recentPastCardIds.clear()
                    recentPastCardIds.addAll(snap.recentPastCardIds)
                    recentTypes.clear()
                    recentTypes.addAll(snap.recentQuestionTypes)
                    historyLoaded = true
                }

                val targetUnit = focusUnitId
                if (prefetchIfMissing) {
                    if (targetUnit != null) {
                        repo.ensureUnitPrefetched(subjectId, targetUnit)
                    } else {
                        repo.ensureActiveUnitPrefetched(subjectId)
                    }
                }

                var readiness = repo.getUnitReadiness(subjectId, targetUnitId = targetUnit)
                applyReadiness(readiness)

                val pinnedNow = nextPinnedCardId
                val roundTarget = effectiveReviewRoundTotal

                // Gate 1: Focused past-unit review round completed (snapshot target prevents mid-round shift)
                if (isPastUnitReviewMode && roundTarget > 0 && reviewRoundCompletedCount >= roundTarget && pinnedNow == null) {
                    showPastReviewCompleteGate = true
                    showUnitCompleteGate = false
                    currentScheduled = null
                    return@runCatching null to true
                }

                // Gate 2: Active unit reached 100% readiness (방식 A)
                if (!isPastUnitReviewMode &&
                    !allUnitsCompleted &&
                    unitTotalCards > 0 &&
                    unitReadiness >= 1f &&
                    !continueAfterUnitComplete &&
                    pinnedNow == null
                ) {
                    showUnitCompleteGate = true
                    showPastReviewCompleteGate = false
                    currentScheduled = null
                    return@runCatching null to true
                }

                showUnitCompleteGate = false
                showPastReviewCompleteGate = false

                val effectiveFocusId = if (isPastUnitReviewMode) focusUnitId else null
                val coveredSnapshot = if (isPastUnitReviewMode) reviewRoundCoveredIds.toSet() else emptySet()

                var next = repo.nextStudyQuestion(
                    subjectId = subjectId,
                    sessionIndex = sessionIndex,
                    recentCardIds = recentCardIds,
                    recentQuestionTypes = recentTypes,
                    recentPastCardIds = recentPastCardIds,
                    pinnedCardId = pinnedNow,
                    focusUnitId = effectiveFocusId,
                    sessionCoveredCardIds = coveredSnapshot
                )
                if (next == null) {
                    if (effectiveFocusId != null) {
                        repo.ensureUnitPrefetched(subjectId, effectiveFocusId)
                    } else {
                        repo.ensureActiveUnitPrefetched(subjectId)
                    }
                    readiness = repo.getUnitReadiness(subjectId, targetUnitId = focusUnitId)
                    applyReadiness(readiness)
                    next = repo.nextStudyQuestion(
                        subjectId = subjectId,
                        sessionIndex = sessionIndex,
                        recentCardIds = recentCardIds,
                        recentQuestionTypes = recentTypes,
                        recentPastCardIds = recentPastCardIds,
                        pinnedCardId = pinnedNow,
                        focusUnitId = if (isPastUnitReviewMode) focusUnitId else null,
                        sessionCoveredCardIds = if (isPastUnitReviewMode) reviewRoundCoveredIds.toSet() else emptySet()
                    )
                }
                nextPinnedCardId = null
                next to false
            }.onSuccess { (next, gated) ->
                if (myQuestionToken != questionToken || myModeToken != modeToken) return@onSuccess
                if (gated) {
                    isLoading = false
                    return@onSuccess
                }
                currentScheduled = next
                if (next != null) {
                    val choices = runCatching {
                        json.decodeFromString<List<String>>(next.question.choicesJson)
                    }.getOrDefault(emptyList())
                    shuffledChoices = choices.shuffled()

                    val sub = container.database.subjectDao().getById(subjectId)
                    val settings = container.settingsRepository.getSettings()
                    val speakText = next.card.ttsText.ifBlank { next.card.title }
                    if (autoSpeak && sub != null && sub.ttsEnabled && settings.ttsAutoPlayDefault && speakText.isNotBlank()) {
                        container.ttsHelper.speak(speakText, sub.ttsLocale, settings.ttsSpeechRate)
                    }
                } else {
                    errorMsg = "표시할 학습 카드가 없습니다. 상단 새로고침 버튼으로 카드를 생성해 주세요."
                }
                isLoading = false
            }.onFailure { e ->
                if (myQuestionToken != questionToken || myModeToken != modeToken) return@onFailure
                errorMsg = e.message ?: "학습 카드를 불러오지 못했습니다."
                isLoading = false
            }
        }
    }

    fun submitMultipleChoice(choice: String) {
        val scheduled = currentScheduled ?: return
        if (answered) return
        selectedChoice = choice
        val correct = AnswerGrader.normalize(choice) == AnswerGrader.normalize(scheduled.question.answerText)
        val reason = scheduled.question.wrongReason.ifBlank { scheduled.card.microSummary }
        recordUiAndCommit(scheduled, correct, choice, reason, deferDbCommit = false)
    }

    fun submitShortAnswer() {
        val scheduled = currentScheduled ?: return
        if (answered || shortInput.isBlank()) return
        val accepted = runCatching {
            json.decodeFromString<List<String>>(scheduled.question.acceptedAnswersJson)
        }.getOrDefault(emptyList())
        val local = AnswerGrader.gradeShortAnswer(shortInput, scheduled.question.answerText, accepted)
        val reason = scheduled.question.wrongReason.ifBlank { scheduled.card.microSummary }
        val canRecheck = !local.isCorrect && local.shouldOfferAiRecheck
        offerAiRecheck = canRecheck
        // Defer DB commit while AI semantic recheck is offered so a successful recheck saves TRUE cleanly
        recordUiAndCommit(scheduled, local.isCorrect, shortInput, reason, deferDbCommit = canRecheck)
    }

    private fun recordUiAndCommit(
        scheduled: ScheduledQuestion,
        correct: Boolean,
        userInput: String,
        reason: String,
        deferDbCommit: Boolean
    ) {
        answered = true
        isCorrect = correct
        comboBeforeAnswer = comboStreak
        comboStreak = if (correct) comboStreak + 1 else 0
        feedbackReason = reason
        showLessonCard = true
        sessionIndex++
        recentCardIds.add(scheduled.card.id)
        if (scheduled.isFromPastReview) {
            recentPastCardIds.add(scheduled.card.id)
        }
        recentTypes.add(scheduled.question.type)

        if (isPastUnitReviewMode && scheduled.card.unitId == focusUnitId) {
            if (reviewRoundCoveredIds.add(scheduled.card.id)) {
                reviewRoundCompletedCount = reviewRoundCoveredIds.size
                if (correct) {
                    reviewRoundCorrectCount++
                }
            }
        }

        if (deferDbCommit) {
            pendingCommit = PendingStudyCommit(scheduled, correct, userInput)
        } else {
            pendingCommit = null
            val targetUnitSnapshot = focusUnitId
            val modeSnap = modeToken
            val qSnap = questionToken
            val deferred = container.appScope.async {
                val generated = repo.recordStudyAnswer(scheduled, correct, userInput)
                val readiness = repo.getUnitReadiness(subjectId, targetUnitId = targetUnitSnapshot)
                generated to readiness
            }
            activeCommitDeferred = deferred
            viewModelScope.launch {
                val (generated, readiness) = runCatching { deferred.await() }.getOrNull() ?: return@launch
                if (activeCommitDeferred === deferred) activeCommitDeferred = null
                if (qSnap == questionToken && generated) {
                    reinforcementCreated = true
                }
                if (modeSnap == modeToken) {
                    applyReadiness(readiness)
                }
            }
        }
    }

    fun requestAiRecheck(subjectName: String) {
        val scheduled = currentScheduled ?: return
        if (isAiRechecking || !offerAiRecheck) return
        isAiRechecking = true
        val inputSnapshot = shortInput
        val myQuestionToken = questionToken
        val myModeToken = modeToken

        recheckJob?.cancel()
        recheckJob = viewModelScope.launch {
            runCatching {
                container.geminiClient.gradeShortAnswer(
                    subject = subjectName,
                    prompt = scheduled.question.prompt,
                    expectedAnswer = scheduled.question.answerText,
                    userAnswer = inputSnapshot
                )
            }.onSuccess { aiGrade ->
                if (myQuestionToken != questionToken || myModeToken != modeToken) return@onSuccess
                val wasWrong = !isCorrect
                isCorrect = aiGrade.isCorrect
                feedbackReason = aiGrade.explanation
                offerAiRecheck = false
                if (aiGrade.isCorrect) {
                    comboStreak = comboBeforeAnswer + 1
                    if (wasWrong && isPastUnitReviewMode && scheduled.card.unitId == focusUnitId) {
                        reviewRoundCorrectCount++
                    }
                }
                // Now commit the FINAL authoritative AI grade to Room DB
                pendingCommit = PendingStudyCommit(scheduled, aiGrade.isCorrect, inputSnapshot)
                ensurePendingCommitted()
            }.onFailure { e ->
                if (myQuestionToken != questionToken || myModeToken != modeToken) return@onFailure
                feedbackReason = "AI 재채점 실패: ${e.message}"
            }
            if (myQuestionToken == questionToken) {
                isAiRechecking = false
            }
        }
    }

    override fun onCleared() {
        cancelInFlightRecheck()
        // Flush any deferred pending commit on appScope so leaving the screen never drops the attempt
        val pending = pendingCommit
        pendingCommit = null
        if (pending != null) {
            container.appScope.launch {
                repo.recordStudyAnswer(pending.scheduled, pending.isCorrect, pending.userInput)
            }
        }
        super.onCleared()
    }
}

class ExamViewModel(
    private val container: AppContainer,
    val subjectId: Long,
    initialUnitId: Long? = null
) : ViewModel() {
    private val repo = container.learningRepository

    var targetUnitId by mutableStateOf(initialUnitId)
        private set
    var activeUnit by mutableStateOf<CurriculumUnitEntity?>(null)
        private set
    var allUnitsCompleted by mutableStateOf(false)
        private set
    var isRetakeCompletedUnit by mutableStateOf(false)
        private set
    var lastExamWasRetake by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    var activeExamType by mutableStateOf<String?>(null)
        private set
    var activeExamTitle by mutableStateOf("")
        private set
    var questions by mutableStateOf<List<LevelTestQuestionDto>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var shortInput by mutableStateOf("")
    var correctCount by mutableIntStateOf(0)
        private set
    val wrongPrompts = mutableStateListOf<String>()
    var finishedResult by mutableStateOf<ExamSessionEntity?>(null)
        private set

    private var unitInfoJob: Job? = null

    init {
        refreshUnitInfo()
    }

    fun selectExamUnit(unitId: Long?) {
        if (isLoading || activeExamType != null) return
        targetUnitId = unitId
        finishedResult = null
        errorMsg = null
        refreshUnitInfo()
    }

    fun refreshUnitInfo() {
        unitInfoJob?.cancel()
        val requested = targetUnitId
        unitInfoJob = viewModelScope.launch {
            val readiness = repo.getUnitReadiness(subjectId, targetUnitId = requested)
            activeUnit = readiness.unit
            allUnitsCompleted = readiness.allUnitsCompleted
            isRetakeCompletedUnit = readiness.unit?.status == UnitStatus.COMPLETED
            if (requested != null && readiness.unit?.id != requested) {
                targetUnitId = null
            }
        }
    }

    fun startCheckpointExam() {
        if (isLoading) return
        val unit = activeUnit ?: return
        if (unit.status == UnitStatus.LOCKED) {
            errorMsg = "잠긴 단원은 시험에 응시할 수 없습니다."
            return
        }
        isLoading = true
        errorMsg = null
        finishedResult = null
        wrongPrompts.clear()
        correctCount = 0
        currentIndex = 0
        shortInput = ""
        viewModelScope.launch {
            runCatching {
                repo.ensureUnitPrefetched(subjectId, unit.id)
                repo.buildUnitCheckpointQuestions(subjectId, unit.id)
            }.onSuccess { qs ->
                if (qs.isEmpty()) {
                    errorMsg = "출제할 단원 문제가 아직 없습니다."
                } else {
                    activeExamType = ExamType.UNIT_CHECKPOINT
                    activeExamTitle = if (unit.status == UnitStatus.COMPLETED) {
                        "${unit.orderIndex}단원 복습 시험"
                    } else {
                        "${unit.orderIndex}단원 체크포인트 시험"
                    }
                    questions = qs
                }
                isLoading = false
            }.onFailure { e ->
                errorMsg = e.message ?: "단원 시험 생성 실패"
                isLoading = false
            }
        }
    }

    fun startMockExam() {
        if (isLoading) return
        isLoading = true
        errorMsg = null
        finishedResult = null
        wrongPrompts.clear()
        correctCount = 0
        currentIndex = 0
        shortInput = ""
        viewModelScope.launch {
            runCatching {
                repo.generateMockExamQuestions(subjectId)
            }.onSuccess { (title, qs) ->
                if (qs.isEmpty()) {
                    errorMsg = "AI가 모의시험 문항을 생성하지 못했습니다. 다시 시도해 주세요."
                } else {
                    activeExamType = ExamType.MOCK_EXAM
                    activeExamTitle = title
                    questions = qs
                }
                isLoading = false
            }.onFailure { e ->
                errorMsg = e.message ?: "AI 모의시험 생성 실패"
                isLoading = false
            }
        }
    }

    fun submitExamAnswer(q: LevelTestQuestionDto, userAns: String) {
        if (isLoading || activeExamType == null || questions.isEmpty()) return
        val ok = if (q.type == QuestionType.SHORT_ANSWER) {
            AnswerGrader.gradeShortAnswer(userAns, q.answerText, q.acceptedAnswers).isCorrect
        } else {
            AnswerGrader.normalize(userAns) == AnswerGrader.normalize(q.answerText)
        }
        if (ok) correctCount++ else wrongPrompts.add("${q.prompt} (정답: ${q.answerText}, 내 답: $userAns)")
        shortInput = ""

        if (currentIndex + 1 < questions.size) {
            currentIndex++
        } else {
            val type = activeExamType ?: ExamType.MOCK_EXAM
            val examUnit = activeUnit
            val unitId = if (type == ExamType.UNIT_CHECKPOINT) examUnit?.id else null
            val wasRetake = type == ExamType.UNIT_CHECKPOINT && examUnit?.status == UnitStatus.COMPLETED
            val totalQs = questions.size
            val finalCorrect = correctCount
            val wrongCopy = wrongPrompts.toList()
            isLoading = true
            // Complete & persist exam on appScope so rotation/back never drops results or unit unlock
            container.appScope.launch {
                val outcome = runCatching {
                    val result = repo.completeExam(
                        subjectId = subjectId,
                        unitId = unitId,
                        examType = type,
                        totalQuestions = totalQs,
                        correctCount = finalCorrect,
                        wrongPrompts = wrongCopy
                    )
                    // If an active unit passed, advance to the newly unlocked active unit (targetUnitId = null).
                    // If retaking a past completed unit, stay on that unit.
                    val nextTargetId = if (wasRetake) unitId else null
                    val readiness = repo.getUnitReadiness(subjectId, targetUnitId = nextTargetId)
                    Triple(result, readiness, nextTargetId)
                }
                viewModelScope.launch {
                    outcome.onSuccess { (result, readiness, nextTargetId) ->
                        finishedResult = result
                        lastExamWasRetake = wasRetake
                        activeExamType = null
                        questions = emptyList()
                        targetUnitId = nextTargetId
                        activeUnit = readiness.unit
                        allUnitsCompleted = readiness.allUnitsCompleted
                        isRetakeCompletedUnit = readiness.unit?.status == UnitStatus.COMPLETED
                    }.onFailure { e ->
                        errorMsg = e.message ?: "시험 결과 저장 중 오류가 발생했습니다."
                        activeExamType = null
                        questions = emptyList()
                    }
                    isLoading = false
                }
            }
        }
    }
}
