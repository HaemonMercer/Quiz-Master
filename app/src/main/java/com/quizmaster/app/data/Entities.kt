package com.quizmaster.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** 題庫（例如「生物第三章」） */
@Entity(tableName = "question_sets")
data class QuestionSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 一道選擇題。
 * options 以原始順序儲存，correct 是正確選項在 options 中的索引；
 * 作答時畫面上的順序每次都會重新打亂，但判分永遠以原始索引為準。
 */
@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionSet::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("setId")],
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: Long,
    val text: String,
    val options: List<String>,
    val correct: List<Int>,
    /** true = 多選題；false = 單選題 */
    val multi: Boolean,
    val explanation: String = "",
    val position: Int = 0,
    // ── 作答統計 ──
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastAnsweredAt: Long? = null,
    val lastCorrect: Boolean? = null,
    /** 是否在錯題本中：答錯就加入，連續答對 [WRONG_BOOK_CLEAR_STREAK] 次自動移出 */
    val inWrongBook: Boolean = false,
    val correctStreak: Int = 0,
)

/** 一次練習（一輪作答） */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionSet::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("setId")],
)
data class QuizSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null 代表跨題庫的錯題本練習 */
    val setId: Long?,
    val title: String,
    val mode: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val total: Int,
    val answered: Int = 0,
    val correct: Int = 0,
)

/** 每一次作答的紀錄 */
@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuizSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("questionId")],
)
data class Attempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionId: Long,
    /** 使用者選的選項（原始索引） */
    val selected: List<Int>,
    val wasCorrect: Boolean,
    val answeredAt: Long = System.currentTimeMillis(),
)

// ── 查詢用的組合類別 ──

data class SetSummary(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val total: Int,
    val done: Int,
    val wrong: Int,
)

data class WrongItem(
    @Embedded val question: Question,
    val setName: String,
)

data class AttemptWithQuestion(
    @Embedded val attempt: Attempt,
    @Relation(parentColumn = "questionId", entityColumn = "id")
    val question: Question,
)

/** 連續答對幾次後自動移出錯題本 */
const val WRONG_BOOK_CLEAR_STREAK = 2
