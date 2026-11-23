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
import com.lvupme.app.data.remote.ChatMessageDto
import com.lvupme.app.data.remote.LevelEvaluationDto
import com.lvupme.app.data.remote.LevelTestQuestionDto
import com.lvupme.app.domain.AnswerGrader
import com.lvupme.app.domain.ScheduledQuestion
import com.lvupme.app.ui.onboarding.OnboardingProposal
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
    initialPinnedCardId: Long?
) : ViewModel() {
    private val repo = container.learningRepository
    private val json = Json { ignoreUnknownKeys = true }

    var activeUnit by mutableStateOf<CurriculumUnitEntity?>(null)
        private set
    var unitReadiness by mutableStateOf(0f)
        private set
    var allUnitsCompleted by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(true)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    var currentScheduled by mutableStateOf<ScheduledQuestion?>(null)
        private set
    var comboStreak by mutableIntStateOf(0)
        private set

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
    private var activeCommitJob: Job? = null
    private var loadJob: Job? = null
    private var lastAppliedPinnedId: Long? = initialPinnedCardId

    init {
        loadNextQuestion(prefetchIfMissing = false)
    }

    fun applyPinnedCard(cardId: Long?) {
        if (cardId == null || cardId <= 0L) return
        if (cardId == lastAppliedPinnedId) return
        if (currentScheduled?.card?.id == cardId && !answered) {
            lastAppliedPinnedId = cardId
            return
        }
        lastAppliedPinnedId = cardId
        nextPinnedCardId = cardId
        loadNextQuestion(prefetchIfMissing = false)
    }

    fun toggleLessonCard() {
        showLessonCard = !showLessonCard
    }

    /**
     * Commits deferred study result on appScope (IO) and updates Compose state strictly on Main (viewModelScope).
     */
    private suspend fun ensurePendingCommitted() {
        val pending = pendingCommit ?: return
        pendingCommit = null
        val deferred = container.appScope.async {
            val generated = repo.recordStudyAnswer(pending.scheduled, pending.isCorrect, pending.userInput)
            val readiness = repo.getUnitReadiness(subjectId)
            generated to readiness
        }
        activeCommitJob = deferred
        val (generated, readiness) = deferred.await()
        if (generated) {
            reinforcementCreated = true
        }
        activeUnit = readiness.unit
        unitReadiness = readiness.readiness
        allUnitsCompleted = readiness.allUnitsCompleted
    }

    fun loadNextQuestion(prefetchIfMissing: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Ensure any deferred/in-flight answer commit finishes BEFORE selecting next card
            ensurePendingCommitted()
            activeCommitJob?.join()

            isLoading = true
            errorMsg = null
            answered = false
            selectedChoice = null
            shortInput = ""
            feedbackReason = ""
            offerAiRecheck = false
            reinforcementCreated = false
            showLessonCard = false

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

                if (prefetchIfMissing) {
                    repo.ensureActiveUnitPrefetched(subjectId)
                }

                val readiness = repo.getUnitReadiness(subjectId)
                activeUnit = readiness.unit
                unitReadiness = readiness.readiness
                allUnitsCompleted = readiness.allUnitsCompleted

                var next = repo.nextStudyQuestion(
                    subjectId = subjectId,
                    sessionIndex = sessionIndex,
                    recentCardIds = recentCardIds,
                    recentQuestionTypes = recentTypes,
                    recentPastCardIds = recentPastCardIds,
                    pinnedCardId = nextPinnedCardId
                )
                if (next == null) {
                    repo.ensureActiveUnitPrefetched(subjectId)
                    next = repo.nextStudyQuestion(
                        subjectId = subjectId,
                        sessionIndex = sessionIndex,
                        recentCardIds = recentCardIds,
                        recentQuestionTypes = recentTypes,
                        recentPastCardIds = recentPastCardIds,
                        pinnedCardId = nextPinnedCardId
                    )
                }
                nextPinnedCardId = null
                next
            }.onSuccess { next ->
                currentScheduled = next
                if (next != null) {
                    val choices = runCatching {
                        json.decodeFromString<List<String>>(next.question.choicesJson)
                    }.getOrDefault(emptyList())
                    shuffledChoices = choices.shuffled()

                    val sub = container.database.subjectDao().getById(subjectId)
                    val settings = container.settingsRepository.getSettings()
                    val speakText = next.card.ttsText.ifBlank { next.card.title }
                    if (sub != null && sub.ttsEnabled && settings.ttsAutoPlayDefault && speakText.isNotBlank()) {
                        container.ttsHelper.speak(speakText, sub.ttsLocale, settings.ttsSpeechRate)
                    }
                } else {
                    errorMsg = "표시할 학습 카드가 없습니다. 상단 새로고침 버튼으로 카드를 생성해 주세요."
                }
                isLoading = false
            }.onFailure { e ->
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
        comboStreak = if (correct) comboStreak + 1 else 0
        feedbackReason = reason
        showLessonCard = true
        sessionIndex++
        recentCardIds.add(scheduled.card.id)
        if (scheduled.isFromPastReview) {
            recentPastCardIds.add(scheduled.card.id)
        }
        recentTypes.add(scheduled.question.type)

        if (deferDbCommit) {
            pendingCommit = PendingStudyCommit(scheduled, correct, userInput)
        } else {
            pendingCommit = null
            activeCommitJob = container.appScope.launch {
                val generated = repo.recordStudyAnswer(scheduled, correct, userInput)
                viewModelScope.launch {
                    if (generated) reinforcementCreated = true
                    val readiness = repo.getUnitReadiness(subjectId)
                    activeUnit = readiness.unit
                    unitReadiness = readiness.readiness
                    allUnitsCompleted = readiness.allUnitsCompleted
                }
            }
        }
    }

    fun requestAiRecheck(subjectName: String) {
        val scheduled = currentScheduled ?: return
        if (isAiRechecking || !offerAiRecheck) return
        isAiRechecking = true
        val inputSnapshot = shortInput

        viewModelScope.launch {
            runCatching {
                container.geminiClient.gradeShortAnswer(
                    subject = subjectName,
                    prompt = scheduled.question.prompt,
                    expectedAnswer = scheduled.question.answerText,
                    userAnswer = inputSnapshot
                )
            }.onSuccess { aiGrade ->
                isCorrect = aiGrade.isCorrect
                feedbackReason = aiGrade.explanation
                offerAiRecheck = false
                if (aiGrade.isCorrect) {
                    comboStreak++
                }
                // Now commit the FINAL authoritative AI grade to Room DB
                pendingCommit = PendingStudyCommit(scheduled, aiGrade.isCorrect, inputSnapshot)
                ensurePendingCommitted()
            }.onFailure { e ->
                feedbackReason = "AI 재채점 실패: ${e.message}"
            }
            isAiRechecking = false
        }
    }

    override fun onCleared() {
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
    val subjectId: Long
) : ViewModel() {
    private val repo = container.learningRepository

    var activeUnit by mutableStateOf<CurriculumUnitEntity?>(null)
        private set
    var allUnitsCompleted by mutableStateOf(false)
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

    init {
        refreshUnitInfo()
    }

    fun refreshUnitInfo() {
        viewModelScope.launch {
            val readiness = repo.getUnitReadiness(subjectId)
            activeUnit = readiness.unit
            allUnitsCompleted = readiness.allUnitsCompleted
        }
    }

    fun startCheckpointExam() {
        val unit = activeUnit ?: return
        isLoading = true
        errorMsg = null
        finishedResult = null
        wrongPrompts.clear()
        correctCount = 0
        currentIndex = 0
        shortInput = ""
        viewModelScope.launch {
            runCatching {
                repo.ensureActiveUnitPrefetched(subjectId)
                repo.buildUnitCheckpointQuestions(subjectId, unit.id)
            }.onSuccess { qs ->
                if (qs.isEmpty()) {
                    errorMsg = "출제할 단원 문제가 아직 없습니다."
                } else {
                    activeExamType = ExamType.UNIT_CHECKPOINT
                    activeExamTitle = "${unit.orderIndex}단원 체크포인트 시험"
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
            val unitId = if (type == ExamType.UNIT_CHECKPOINT) activeUnit?.id else null
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
                    val readiness = repo.getUnitReadiness(subjectId)
                    result to readiness
                }
                viewModelScope.launch {
                    outcome.onSuccess { (result, readiness) ->
                        finishedResult = result
                        activeExamType = null
                        questions = emptyList()
                        activeUnit = readiness.unit
                        allUnitsCompleted = readiness.allUnitsCompleted
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
