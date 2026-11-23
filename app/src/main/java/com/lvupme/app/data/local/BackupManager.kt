package com.lvupme.app.data.local

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = SUPPORTED_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings,
    val subjects: List<SubjectEntity>,
    val units: List<CurriculumUnitEntity>,
    val cards: List<LearningCardEntity>,
    val questions: List<QuizQuestionEntity>,
    val reviewStates: List<ReviewStateEntity>,
    val attempts: List<AttemptLogEntity>,
    val exams: List<ExamSessionEntity>
) {
    companion object {
        const val SUPPORTED_VERSION = 1
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024 // 10 MB
    }
}

class BackupManager(
    private val db: LvUpDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportJson(): String {
        val payload = BackupPayload(
            settings = settingsRepository.getSettings(),
            subjects = db.subjectDao().getAll(),
            units = db.curriculumDao().getAll(),
            cards = db.cardDao().getAll(),
            questions = db.quizDao().getAll(),
            reviewStates = db.reviewDao().getAll(),
            attempts = db.attemptDao().getAll(),
            exams = db.examDao().getAll()
        )
        return json.encodeToString(payload)
    }

    fun validatePayload(payload: BackupPayload) {
        require(payload.version == BackupPayload.SUPPORTED_VERSION) {
            "지원하지 않는 백업 버전입니다 (v${payload.version})."
        }
        val subjectIds = payload.subjects.map { it.id }.toSet()
        val unitIds = payload.units.map { it.id }.toSet()
        val cardIds = payload.cards.map { it.id }.toSet()

        require(payload.units.all { it.subjectId in subjectIds }) {
            "손상된 백업 파일입니다: 존재하지 않는 과목을 참조하는 단원이 있습니다."
        }
        require(payload.cards.all { it.subjectId in subjectIds && it.unitId in unitIds }) {
            "손상된 백업 파일입니다: 존재하지 않는 단원을 참조하는 카드가 있습니다."
        }
        require(payload.questions.all { it.cardId in cardIds && it.subjectId in subjectIds }) {
            "손상된 백업 파일입니다: 존재하지 않는 카드를 참조하는 문제가 있습니다."
        }
        require(payload.reviewStates.all { it.cardId in cardIds }) {
            "손상된 백업 파일입니다: 존재하지 않는 카드를 참조하는 복습 상태가 있습니다."
        }
        require(payload.exams.all { it.subjectId in subjectIds }) {
            "손상된 백업 파일입니다: 존재하지 않는 과목을 참조하는 시험 기록이 있습니다."
        }
    }

    suspend fun importJson(rawJson: String): AppSettings {
        require(rawJson.isNotBlank()) { "빈 백업 파일입니다." }
        require(rawJson.toByteArray(Charsets.UTF_8).size <= BackupPayload.MAX_BACKUP_BYTES) {
            "백업 파일 크기가 제한(10MB)을 초과했습니다."
        }
        val payload = try {
            json.decodeFromString<BackupPayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("올바른 LvUp Me JSON 백업 파일이 아닙니다: ${e.message}", e)
        }
        validatePayload(payload)

        val validAttempts = payload.attempts.filter { it.subjectId in payload.subjects.map { s -> s.id }.toSet() }

        db.withTransaction {
            db.clearAllTables()
            payload.subjects.forEach { db.subjectDao().insert(it) }
            if (payload.units.isNotEmpty()) db.curriculumDao().insertAll(payload.units)
            if (payload.cards.isNotEmpty()) db.cardDao().insertAll(payload.cards)
            if (payload.questions.isNotEmpty()) db.quizDao().insertAll(payload.questions)
            if (payload.reviewStates.isNotEmpty()) db.reviewDao().upsertAll(payload.reviewStates)
            if (validAttempts.isNotEmpty()) db.attemptDao().insertAll(validAttempts)
            if (payload.exams.isNotEmpty()) db.examDao().insertAll(payload.exams)
        }
        return settingsRepository.updateSettings { payload.settings }
    }
}
