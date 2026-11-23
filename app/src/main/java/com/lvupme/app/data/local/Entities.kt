package com.lvupme.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subTopic: String,
    val goalSummary: String = "",
    val userLevel: String = "초급",
    val isMainForWidget: Boolean = false,
    val ttsEnabled: Boolean = false,
    val ttsLocale: String = "en-US",
    val createdAt: Long = System.currentTimeMillis()
)

object UnitStatus {
    const val LOCKED = "LOCKED"
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
}

@Serializable
@Entity(
    tableName = "curriculum_units",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class CurriculumUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val orderIndex: Int,
    val title: String,
    val description: String,
    val status: String = UnitStatus.LOCKED,
    val isPrefetched: Boolean = false
)

@Serializable
@Entity(
    tableName = "learning_cards",
    foreignKeys = [
        ForeignKey(
            entity = CurriculumUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unitId"), Index("subjectId")]
)
data class LearningCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val unitId: Long,
    val unitOrderIndex: Int,
    val title: String,
    val microSummary: String,
    val detailExplanation: String,
    val exampleText: String = "",
    val ttsText: String = "",
    val isAiReinforced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object QuestionType {
    const val MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
    const val SHORT_ANSWER = "SHORT_ANSWER"
}

@Serializable
@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = LearningCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId"), Index("subjectId")]
)
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val unitId: Long,
    val cardId: Long,
    val type: String, // MULTIPLE_CHOICE or SHORT_ANSWER
    val prompt: String,
    val choicesJson: String = "[]", // JSON List<String> for MULTIPLE_CHOICE
    val answerText: String,
    val acceptedAnswersJson: String = "[]", // JSON List<String> of synonyms / <=3 word variants
    val wrongReason: String = "",
    val isAiReinforced: Boolean = false
)

@Serializable
@Entity(
    tableName = "review_states",
    foreignKeys = [
        ForeignKey(
            entity = LearningCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId"), Index("unitId"), Index("nextReviewAt")]
)
data class ReviewStateEntity(
    @PrimaryKey val cardId: Long,
    val subjectId: Long,
    val unitId: Long,
    val unitOrderIndex: Int,
    val masteryLevel: Int = 0,          // 0=new, 1=seen MC, 2=ready for ShortAnswer, 3+=mastered
    val totalAttempts: Int = 0,
    val wrongCount: Int = 0,
    val consecutiveCorrect: Int = 0,
    val intervalHours: Double = 4.0,
    val easeFactor: Double = 2.5,
    val lastReviewedAt: Long = 0L,
    val nextReviewAt: Long = 0L,
    val isWeak: Boolean = false,
    val reinforcementGenerated: Boolean = false
)

@Serializable
@Entity(
    tableName = "attempt_logs",
    indices = [Index("subjectId"), Index("answeredAt"), Index("cardId")]
)
data class AttemptLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val unitId: Long,
    val cardId: Long,
    val questionId: Long,
    val questionType: String,
    val isFromPastReview: Boolean,
    val isCorrect: Boolean,
    val userInput: String,
    val answeredAt: Long = System.currentTimeMillis()
)

object ExamType {
    const val LEVEL_TEST = "LEVEL_TEST"
    const val UNIT_CHECKPOINT = "UNIT_CHECKPOINT"
    const val MOCK_EXAM = "MOCK_EXAM"
}

@Serializable
@Entity(
    tableName = "exam_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class ExamSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val unitId: Long? = null,
    val examType: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Int,
    val passed: Boolean,
    val aiFeedback: String = "",
    val takenAt: Long = System.currentTimeMillis()
)
