package com.lvupme.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        CurriculumUnitEntity::class,
        LearningCardEntity::class,
        QuizQuestionEntity::class,
        ReviewStateEntity::class,
        AttemptLogEntity::class,
        ExamSessionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LvUpDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun curriculumDao(): CurriculumDao
    abstract fun cardDao(): CardDao
    abstract fun quizDao(): QuizDao
    abstract fun reviewDao(): ReviewDao
    abstract fun attemptDao(): AttemptDao
    abstract fun examDao(): ExamDao

    companion object {
        @Volatile
        private var INSTANCE: LvUpDatabase? = null

        fun getInstance(context: Context): LvUpDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LvUpDatabase::class.java,
                    "lvupme.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
