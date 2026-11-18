package com.quizmaster.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QuestionSet::class, Question::class, QuizSession::class, Attempt::class, WrongRecord::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): QuizDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v2：新增錯題集，並把以前答錯過的題目補進去 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wrong_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `questionId` INTEGER,
                        `setName` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `options` TEXT NOT NULL,
                        `correct` TEXT NOT NULL,
                        `multi` INTEGER NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `wrongTimes` INTEGER NOT NULL,
                        `firstWrongAt` INTEGER NOT NULL,
                        `lastWrongAt` INTEGER NOT NULL,
                        `lastSelected` TEXT NOT NULL,
                        FOREIGN KEY(`questionId`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_wrong_records_questionId` ON `wrong_records` (`questionId`)"
                )
                db.execSQL(
                    """
                    INSERT INTO wrong_records (questionId, setName, text, options, correct, multi, explanation,
                        wrongTimes, firstWrongAt, lastWrongAt, lastSelected)
                    SELECT q.id, s.name, q.text, q.options, q.correct, q.multi, q.explanation, q.wrongCount,
                        COALESCE((SELECT MIN(a.answeredAt) FROM attempts a WHERE a.questionId = q.id AND a.wasCorrect = 0), q.lastAnsweredAt, 0),
                        COALESCE((SELECT MAX(a.answeredAt) FROM attempts a WHERE a.questionId = q.id AND a.wasCorrect = 0), q.lastAnsweredAt, 0),
                        COALESCE((SELECT a.selected FROM attempts a WHERE a.questionId = q.id AND a.wasCorrect = 0
                            ORDER BY a.answeredAt DESC, a.id DESC LIMIT 1), '[]')
                    FROM questions q INNER JOIN question_sets s ON s.id = q.setId
                    WHERE q.wrongCount > 0
                    """.trimIndent()
                )
            }
        }

        /** v3：錯題集記下所屬題庫，才能分題庫查看與練習 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `wrong_records` ADD COLUMN `setId` INTEGER")
                db.execSQL(
                    """
                    UPDATE wrong_records
                    SET setId = (SELECT q.setId FROM questions q WHERE q.id = wrong_records.questionId)
                    WHERE questionId IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quiz.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
