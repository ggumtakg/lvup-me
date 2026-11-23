package com.lvupme.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class WeakCardJoinedRow(
    val subjectName: String,
    @Embedded(prefix = "card_") val card: LearningCardEntity,
    @Embedded(prefix = "state_") val state: ReviewStateEntity
)

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY isMainForWidget DESC, createdAt DESC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY isMainForWidget DESC, createdAt DESC")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE id = :id")
    fun observeById(id: Long): Flow<SubjectEntity?>

    @Query("SELECT * FROM subjects WHERE isMainForWidget = 1 LIMIT 1")
    suspend fun getMainWidgetSubject(): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE isMainForWidget = 1 LIMIT 1")
    fun observeMainWidgetSubject(): Flow<SubjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)

    @Query("UPDATE subjects SET isMainForWidget = CASE WHEN id = :subjectId THEN 1 ELSE 0 END")
    suspend fun setMainWidgetSubject(subjectId: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}

@Dao
interface CurriculumDao {
    @Query("SELECT * FROM curriculum_units WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    fun observeUnits(subjectId: Long): Flow<List<CurriculumUnitEntity>>

    @Query("SELECT * FROM curriculum_units ORDER BY subjectId ASC, orderIndex ASC")
    fun observeAll(): Flow<List<CurriculumUnitEntity>>

    @Query("SELECT * FROM curriculum_units WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    suspend fun getUnits(subjectId: Long): List<CurriculumUnitEntity>

    @Query("SELECT * FROM curriculum_units ORDER BY subjectId ASC, orderIndex ASC")
    suspend fun getAll(): List<CurriculumUnitEntity>

    @Query("SELECT * FROM curriculum_units WHERE subjectId = :subjectId AND status = 'ACTIVE' ORDER BY orderIndex ASC LIMIT 1")
    suspend fun getActiveUnit(subjectId: Long): CurriculumUnitEntity?

    @Query("SELECT * FROM curriculum_units WHERE id = :unitId")
    suspend fun getById(unitId: Long): CurriculumUnitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(units: List<CurriculumUnitEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unit: CurriculumUnitEntity): Long

    @Update
    suspend fun update(unit: CurriculumUnitEntity)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM learning_cards WHERE unitId = :unitId ORDER BY id ASC")
    suspend fun getByUnit(unitId: Long): List<LearningCardEntity>

    @Query("SELECT * FROM learning_cards WHERE subjectId = :subjectId ORDER BY unitOrderIndex ASC, id ASC")
    suspend fun getBySubject(subjectId: Long): List<LearningCardEntity>

    @Query("SELECT * FROM learning_cards WHERE subjectId = :subjectId ORDER BY unitOrderIndex ASC, id ASC")
    fun observeBySubject(subjectId: Long): Flow<List<LearningCardEntity>>

    @Query("SELECT * FROM learning_cards ORDER BY id ASC")
    fun observeAll(): Flow<List<LearningCardEntity>>

    @Query("SELECT * FROM learning_cards WHERE id = :cardId")
    suspend fun getById(cardId: Long): LearningCardEntity?

    @Query("SELECT * FROM learning_cards ORDER BY id ASC")
    suspend fun getAll(): List<LearningCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: LearningCardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<LearningCardEntity>): List<Long>
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM quiz_questions WHERE cardId = :cardId")
    suspend fun getByCard(cardId: Long): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_questions WHERE unitId = :unitId")
    suspend fun getByUnit(unitId: Long): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_questions WHERE subjectId = :subjectId")
    suspend fun getBySubject(subjectId: Long): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_questions ORDER BY id ASC")
    suspend fun getAll(): List<QuizQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuizQuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuizQuestionEntity>): List<Long>
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_states WHERE cardId = :cardId")
    suspend fun getByCard(cardId: Long): ReviewStateEntity?

    @Query("SELECT * FROM review_states WHERE subjectId = :subjectId")
    suspend fun getBySubject(subjectId: Long): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states WHERE subjectId = :subjectId")
    fun observeBySubject(subjectId: Long): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_states ORDER BY cardId ASC")
    fun observeAll(): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_states WHERE isWeak = 1 ORDER BY wrongCount DESC")
    fun observeWeakStates(): Flow<List<ReviewStateEntity>>

    @Query(
        """
        SELECT
            s.name AS subjectName,
            c.id AS card_id, c.subjectId AS card_subjectId, c.unitId AS card_unitId,
            c.unitOrderIndex AS card_unitOrderIndex, c.title AS card_title,
            c.microSummary AS card_microSummary, c.detailExplanation AS card_detailExplanation,
            c.exampleText AS card_exampleText, c.ttsText AS card_ttsText,
            c.isAiReinforced AS card_isAiReinforced, c.createdAt AS card_createdAt,
            r.cardId AS state_cardId, r.subjectId AS state_subjectId, r.unitId AS state_unitId,
            r.unitOrderIndex AS state_unitOrderIndex, r.masteryLevel AS state_masteryLevel,
            r.totalAttempts AS state_totalAttempts, r.wrongCount AS state_wrongCount,
            r.consecutiveCorrect AS state_consecutiveCorrect, r.intervalHours AS state_intervalHours,
            r.easeFactor AS state_easeFactor, r.lastReviewedAt AS state_lastReviewedAt,
            r.nextReviewAt AS state_nextReviewAt, r.isWeak AS state_isWeak,
            r.reinforcementGenerated AS state_reinforcementGenerated
        FROM review_states r
        INNER JOIN learning_cards c ON c.id = r.cardId
        INNER JOIN subjects s ON s.id = r.subjectId
        WHERE r.isWeak = 1
        ORDER BY r.wrongCount DESC, r.lastReviewedAt DESC
        """
    )
    fun observeWeakCardsJoined(): Flow<List<WeakCardJoinedRow>>

    @Query("SELECT * FROM review_states WHERE subjectId = :subjectId AND unitId = :unitId")
    suspend fun getByUnit(subjectId: Long, unitId: Long): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states ORDER BY cardId ASC")
    suspend fun getAll(): List<ReviewStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReviewStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<ReviewStateEntity>)
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(log: AttemptLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AttemptLogEntity>)

    @Query("DELETE FROM attempt_logs WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: Long)

    @Query("SELECT COUNT(*) FROM attempt_logs WHERE answeredAt >= :sinceMillis")
    fun observeCountSince(sinceMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM attempt_logs WHERE answeredAt >= :sinceMillis")
    suspend fun getCountSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM attempt_logs WHERE subjectId = :subjectId")
    suspend fun getCountBySubject(subjectId: Long): Int

    @Query("SELECT * FROM attempt_logs WHERE subjectId = :subjectId ORDER BY answeredAt DESC LIMIT :limit")
    suspend fun getRecentBySubject(subjectId: Long, limit: Int = 30): List<AttemptLogEntity>

    @Query("SELECT DISTINCT date(answeredAt / 1000, 'unixepoch', 'localtime') FROM attempt_logs ORDER BY 1 DESC")
    fun observeDistinctStudyDates(): Flow<List<String>>

    @Query("SELECT * FROM attempt_logs ORDER BY answeredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<AttemptLogEntity>>

    @Query("SELECT * FROM attempt_logs ORDER BY answeredAt ASC")
    suspend fun getAll(): List<AttemptLogEntity>
}

@Dao
interface ExamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ExamSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<ExamSessionEntity>)

    @Query("SELECT * FROM exam_sessions WHERE subjectId = :subjectId ORDER BY takenAt DESC")
    fun observeBySubject(subjectId: Long): Flow<List<ExamSessionEntity>>

    @Query("SELECT * FROM exam_sessions ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<ExamSessionEntity>>

    @Query("SELECT * FROM exam_sessions ORDER BY takenAt ASC")
    suspend fun getAll(): List<ExamSessionEntity>
}
