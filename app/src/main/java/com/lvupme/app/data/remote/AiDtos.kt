package com.lvupme.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String, // "user" or "assistant"
    val content: String
)

@Serializable
data class OnboardingReplyDto(
    val assistantReply: String,
    val readyForLevelTest: Boolean = false,
    val proposedSubject: String = "",
    val proposedSubTopic: String = "",
    val goalSummary: String = "",
    val suggestedTtsEnabled: Boolean = false,
    val suggestedTtsLocale: String = "en-US"
)

@Serializable
data class LevelTestQuestionDto(
    val id: Int = 0,
    val type: String = "MULTIPLE_CHOICE", // MULTIPLE_CHOICE or SHORT_ANSWER
    val prompt: String,
    val choices: List<String> = emptyList(),
    val answerText: String,
    val acceptedAnswers: List<String> = emptyList(),
    val explanation: String = ""
)

@Serializable
data class LevelTestDto(
    val introMessage: String = "",
    val questions: List<LevelTestQuestionDto> = emptyList()
)

@Serializable
data class CurriculumUnitDraftDto(
    val orderIndex: Int,
    val title: String,
    val description: String
)

@Serializable
data class LevelEvaluationDto(
    val assessedLevel: String,
    val feedback: String,
    val units: List<CurriculumUnitDraftDto> = emptyList()
)

@Serializable
data class GeneratedQuizDto(
    val type: String, // MULTIPLE_CHOICE or SHORT_ANSWER
    val prompt: String,
    val choices: List<String> = emptyList(),
    val answerText: String,
    val acceptedAnswers: List<String> = emptyList(),
    val wrongReason: String = ""
)

@Serializable
data class GeneratedCardDto(
    val title: String,
    val microSummary: String,
    val detailExplanation: String,
    val exampleText: String = "",
    val ttsText: String = "",
    val multipleChoice: GeneratedQuizDto,
    val shortAnswer: GeneratedQuizDto
)

@Serializable
data class UnitContentBatchDto(
    val cards: List<GeneratedCardDto> = emptyList()
)

@Serializable
data class ReinforceCardDto(
    val title: String,
    val microSummary: String,
    val detailExplanation: String,
    val exampleText: String = "",
    val ttsText: String = "",
    val multipleChoice: GeneratedQuizDto,
    val shortAnswer: GeneratedQuizDto
)

@Serializable
data class AiGradeDto(
    val isCorrect: Boolean,
    val explanation: String
)

@Serializable
data class MockExamDto(
    val title: String = "AI 종합 모의시험",
    val questions: List<LevelTestQuestionDto> = emptyList()
)

@Serializable
data class ExamFeedbackDto(
    val summary: String,
    val weakTopics: List<String> = emptyList(),
    val nextActionTip: String = ""
)
