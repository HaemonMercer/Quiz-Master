package com.quizmaster.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    // ── 題庫 ──

    @Query(
        """
        SELECT s.id AS id, s.name AS name, s.description AS description, s.createdAt AS createdAt,
            (SELECT COUNT(*) FROM questions q WHERE q.setId = s.id) AS total,
            (SELECT COUNT(*) FROM questions q WHERE q.setId = s.id AND q.attemptCount > 0) AS done,
            (SELECT COUNT(*) FROM questions q WHERE q.setId = s.id AND q.inWrongBook = 1) AS wrong
        FROM question_sets s
        ORDER BY s.createdAt DESC
        """
    )
    fun observeSetSummaries(): Flow<List<SetSummary>>

    @Query("SELECT * FROM question_sets WHERE id = :id")
    fun observeSet(id: Long): Flow<QuestionSet?>

    @Query("SELECT * FROM question_sets WHERE id = :id")
    suspend fun getSet(id: Long): QuestionSet?

    @Insert
    suspend fun insertSet(set: QuestionSet): Long

    @Update
    suspend fun updateSet(set: QuestionSet)

    @Delete
    suspend fun deleteSet(set: QuestionSet)

    // ── 題目 ──

    @Query("SELECT * FROM questions WHERE setId = :setId ORDER BY position, id")
    fun observeQuestions(setId: Long): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE setId = :setId ORDER BY position, id")
    suspend fun getQuestions(setId: Long): List<Question>

    @Query("SELECT * FROM questions WHERE setId = :setId AND attemptCount = 0 ORDER BY position, id")
    suspend fun getUndoneQuestions(setId: Long): List<Question>

    @Query("SELECT * FROM questions WHERE setId = :setId AND inWrongBook = 1 ORDER BY position, id")
    suspend fun getWrongQuestions(setId: Long): List<Question>

    @Query("SELECT * FROM questions WHERE inWrongBook = 1 ORDER BY setId, position, id")
    suspend fun getAllWrongQuestions(): List<Question>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestion(id: Long): Question?

    @Query("SELECT MAX(position) FROM questions WHERE setId = :setId")
    suspend fun maxPosition(setId: Long): Int?

    @Insert
    suspend fun insertQuestion(question: Question): Long

    @Insert
    suspend fun insertQuestions(questions: List<Question>)

    @Update
    suspend fun updateQuestion(question: Question)

    @Delete
    suspend fun deleteQuestion(question: Question)

    @Query("UPDATE questions SET inWrongBook = :value, correctStreak = 0 WHERE id = :id")
    suspend fun setWrongBook(id: Long, value: Boolean)

    @Query("UPDATE questions SET inWrongBook = 0, correctStreak = 0")
    suspend fun clearAllWrong()

    @Query(
        """
        UPDATE questions SET attemptCount = 0, correctCount = 0, wrongCount = 0,
            lastAnsweredAt = NULL, lastCorrect = NULL, inWrongBook = 0, correctStreak = 0
        WHERE setId = :setId
        """
    )
    suspend fun resetSetStats(setId: Long)

    @Query(
        """
        SELECT q.*, s.name AS setName FROM questions q
        INNER JOIN question_sets s ON s.id = q.setId
        WHERE q.inWrongBook = 1
        ORDER BY q.lastAnsweredAt DESC
        """
    )
    fun observeWrongItems(): Flow<List<WrongItem>>

    // ── 練習紀錄 ──

    @Insert
    suspend fun insertSession(session: QuizSession): Long

    @Update
    suspend fun updateSession(session: QuizSession)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): QuizSession?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<QuizSession?>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<QuizSession>>

    @Delete
    suspend fun deleteSession(session: QuizSession)

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Insert
    suspend fun insertAttempt(attempt: Attempt): Long

    @Transaction
    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY answeredAt, id")
    fun observeAttempts(sessionId: Long): Flow<List<AttemptWithQuestion>>
}
