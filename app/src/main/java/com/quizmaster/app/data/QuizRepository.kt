package com.quizmaster.app.data

import androidx.room.withTransaction
import com.quizmaster.app.util.ParsedQuestion
import com.quizmaster.app.util.QuestionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

enum class QuizMode(val label: String) {
    ALL("全部題目"),
    UNDONE("未做過的題目"),
    WRONG("錯題練習"),
    WRONG_ALL("錯題本總複習"),
    RETRY("重做錯題"),
    COLLECTION("錯題集練習"),
}

class QuizRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    /** 用來執行「離開畫面也要完成」的寫入（例如剛答完題就按返回） */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── 觀察資料 ──
    fun observeSetSummaries(): Flow<List<SetSummary>> = dao.observeSetSummaries()
    fun observeSet(id: Long): Flow<QuestionSet?> = dao.observeSet(id)
    fun observeQuestions(setId: Long): Flow<List<Question>> = dao.observeQuestions(setId)
    fun observeWrongItems(): Flow<List<WrongItem>> = dao.observeWrongItems()
    fun observeWrongRecords(): Flow<List<WrongRecord>> = dao.observeWrongRecords()
    fun observeWrongRecordCount(): Flow<Int> = dao.observeWrongRecordCount()
    fun observeSessions(): Flow<List<QuizSession>> = dao.observeSessions()
    fun observeSession(id: Long): Flow<QuizSession?> = dao.observeSession(id)
    fun observeAttempts(sessionId: Long): Flow<List<AttemptWithQuestion>> = dao.observeAttempts(sessionId)

    // ── 題庫 ──
    suspend fun getSet(id: Long): QuestionSet? = dao.getSet(id)

    suspend fun createSet(name: String, description: String = ""): Long =
        dao.insertSet(QuestionSet(name = name.trim(), description = description.trim()))

    suspend fun updateSet(set: QuestionSet) = db.withTransaction {
        dao.updateSet(set)
        dao.renameWrongRecordSet(set.id, set.name)
    }
    suspend fun deleteSet(set: QuestionSet) = dao.deleteSet(set)
    suspend fun resetSetProgress(setId: Long) = dao.resetSetStats(setId)

    // ── 題目 ──
    suspend fun getQuestion(id: Long): Question? = dao.getQuestion(id)

    suspend fun saveQuestion(question: Question): Long =
        if (question.id == 0L) {
            val pos = (dao.maxPosition(question.setId) ?: -1) + 1
            dao.insertQuestion(question.copy(position = pos))
        } else {
            db.withTransaction {
                dao.updateQuestion(question)
                dao.refreshWrongRecordSnapshot(
                    question.id, question.text, question.options, question.correct,
                    question.multi, question.explanation,
                )
            }
            question.id
        }

    suspend fun deleteQuestion(question: Question) = dao.deleteQuestion(question)

    suspend fun setWrongBook(questionId: Long, inWrongBook: Boolean) =
        dao.setWrongBook(questionId, inWrongBook)

    suspend fun clearWrongBook() = dao.clearAllWrong()

    // ── 錯題集：只有使用者手動操作才會刪除 ──
    suspend fun deleteWrongRecord(record: WrongRecord) = dao.deleteWrongRecord(record)
    suspend fun clearWrongRecords() = dao.clearWrongRecords()

    suspend fun importQuestions(setId: Long, parsed: List<ParsedQuestion>): Int =
        db.withTransaction {
            var pos = (dao.maxPosition(setId) ?: -1) + 1
            dao.insertQuestions(parsed.map {
                Question(
                    setId = setId,
                    text = it.text,
                    options = it.options,
                    correct = it.correct,
                    multi = it.multi,
                    explanation = it.explanation,
                    position = pos++,
                )
            })
            parsed.size
        }

    suspend fun exportText(setId: Long): String = QuestionParser.export(dao.getQuestions(setId))

    suspend fun loadDemoSet(): Long {
        val id = createSet("示範題庫", "內建範例，可隨意修改或刪除")
        importQuestions(id, QuestionParser.parse(QuestionParser.SAMPLE).questions)
        return id
    }

    // ── 作答 ──
    suspend fun loadQuizQuestions(mode: QuizMode, setId: Long): List<Question> = when (mode) {
        QuizMode.ALL -> dao.getQuestions(setId)
        QuizMode.UNDONE -> dao.getUndoneQuestions(setId)
        QuizMode.WRONG -> dao.getWrongQuestions(setId)
        QuizMode.WRONG_ALL -> dao.getAllWrongQuestions()
        QuizMode.COLLECTION -> dao.getCollectionQuestions()
        QuizMode.RETRY -> emptyList() // 重做錯題由作答畫面直接帶入題目
    }

    suspend fun startSession(title: String, mode: QuizMode, setId: Long?, total: Int): Long =
        dao.insertSession(QuizSession(setId = setId, title = title, mode = mode.name, total = total))

    suspend fun finishSession(sessionId: Long) {
        dao.getSession(sessionId)?.let {
            dao.updateSession(it.copy(finishedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteSession(session: QuizSession) = dao.deleteSession(session)
    suspend fun clearHistory() = dao.clearSessions()

    /** 記錄一次作答，並更新題目統計、錯題本與本輪成績 */
    suspend fun recordAnswer(sessionId: Long, questionId: Long, selected: List<Int>) {
        db.withTransaction {
            val q = dao.getQuestion(questionId) ?: return@withTransaction
            val ok = selected.toSet() == q.correct.toSet()
            val now = System.currentTimeMillis()

            dao.insertAttempt(
                Attempt(
                    sessionId = sessionId,
                    questionId = questionId,
                    selected = selected,
                    wasCorrect = ok,
                    answeredAt = now,
                )
            )

            val streak = if (ok) q.correctStreak + 1 else 0
            val inWrongBook = when {
                !ok -> true
                q.inWrongBook && streak >= WRONG_BOOK_CLEAR_STREAK -> false
                else -> q.inWrongBook
            }
            dao.updateQuestion(
                q.copy(
                    attemptCount = q.attemptCount + 1,
                    correctCount = q.correctCount + if (ok) 1 else 0,
                    wrongCount = q.wrongCount + if (ok) 0 else 1,
                    lastAnsweredAt = now,
                    lastCorrect = ok,
                    inWrongBook = inWrongBook,
                    correctStreak = streak,
                )
            )

            if (!ok) addToWrongCollection(q, selected, now)

            dao.getSession(sessionId)?.let { s ->
                dao.updateSession(
                    s.copy(answered = s.answered + 1, correct = s.correct + if (ok) 1 else 0)
                )
            }
            Unit
        }
    }

    /** 答錯就收錄到錯題集；已收錄的題目累加次數、更新快照 */
    private suspend fun addToWrongCollection(q: Question, selected: List<Int>, now: Long) {
        val setName = dao.getSet(q.setId)?.name.orEmpty()
        val existing = dao.getWrongRecordByQuestion(q.id)
        if (existing == null) {
            dao.insertWrongRecord(
                WrongRecord(
                    questionId = q.id,
                    setName = setName,
                    text = q.text,
                    options = q.options,
                    correct = q.correct,
                    multi = q.multi,
                    explanation = q.explanation,
                    wrongTimes = 1,
                    firstWrongAt = now,
                    lastWrongAt = now,
                    lastSelected = selected,
                )
            )
        } else {
            dao.updateWrongRecord(
                existing.copy(
                    setName = setName,
                    text = q.text,
                    options = q.options,
                    correct = q.correct,
                    multi = q.multi,
                    explanation = q.explanation,
                    wrongTimes = existing.wrongTimes + 1,
                    lastWrongAt = now,
                    lastSelected = selected,
                )
            )
        }
    }
}
