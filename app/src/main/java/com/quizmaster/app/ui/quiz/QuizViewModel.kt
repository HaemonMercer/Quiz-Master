package com.quizmaster.app.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quizmaster.app.data.Question
import com.quizmaster.app.data.QuizMode
import com.quizmaster.app.data.QuizRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 一道題目在本輪中的呈現方式。
 * order[畫面上的位置] = 原始選項索引 —— 每輪都重新打亂，所以選項順序隨機。
 */
data class QuizItem(val question: Question, val order: List<Int>)

data class QuizUiState(
    val loading: Boolean = true,
    val title: String = "",
    val items: List<QuizItem> = emptyList(),
    val index: Int = 0,
    /** 目前選取的選項（原始索引） */
    val selected: Set<Int> = emptySet(),
    val submitted: Boolean = false,
    val lastCorrect: Boolean = false,
    val answered: Int = 0,
    val correct: Int = 0,
    val wrong: List<Question> = emptyList(),
    val finished: Boolean = false,
    /** 每批題數；0 = 不分批 */
    val batchSize: Int = 0,
    /** 第幾批；0 = 不顯示（例如重做錯題時） */
    val batchNumber: Int = 0,
    /** 分批模式下，結束後還有多少題可做下一批；null = 尚未計算或不適用 */
    val nextBatchRemaining: Int? = null,
) {
    val current: QuizItem? get() = items.getOrNull(index)
    val isLast: Boolean get() = index >= items.lastIndex
}

class QuizViewModel(
    private val repo: QuizRepository,
    private val mode: QuizMode,
    private val setId: Long,
    private val shuffleQuestions: Boolean,
    /** 每批題數；0 = 全部 */
    private val limit: Int = 0,
) : ViewModel() {

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    /** 一輪練習；第一題作答時才建立資料庫紀錄，避免留下空白紀錄 */
    private class Round(val title: String, val mode: QuizMode, val total: Int) {
        var sessionId: Long = 0L
    }

    private var round: Round? = null
    private var setName: String? = null
    private val writeLock = Mutex()
    private val pendingWrites = mutableListOf<Job>()

    /** 這次分批測驗中已經出過的題目，下一批不會重複 */
    private val given = mutableSetOf<Long>()
    private var batchNumber = 1

    init {
        viewModelScope.launch {
            setName = if (setId > 0) repo.getSet(setId)?.name else null
            begin(pickBatch(candidates()), mode)
        }
    }

    private suspend fun candidates(): List<Question> =
        repo.loadQuizQuestions(mode, setId).filter { it.id !in given }

    /** 從候選題中抽出一批：隨機抽，或依題目順序取前 N 題 */
    private fun pickBatch(list: List<Question>): List<Question> = when {
        limit <= 0 || list.size <= limit -> list
        shuffleQuestions -> list.shuffled().take(limit)
        else -> list.take(limit)
    }

    private fun begin(questions: List<Question>, roundMode: QuizMode) {
        val ordered = if (shuffleQuestions) questions.shuffled() else questions
        val batched = limit > 0 && roundMode != QuizMode.RETRY
        if (roundMode != QuizMode.RETRY) given += questions.map { it.id }

        val title = buildList {
            setName?.let(::add)
            add(roundMode.label)
            if (batched) add("第 $batchNumber 批")
        }.joinToString(" · ")

        round = Round(title, roundMode, ordered.size)
        _state.value = QuizUiState(
            loading = false,
            title = title,
            items = ordered.map { q -> QuizItem(q, q.options.indices.shuffled()) },
            // 重做錯題時也保留分批狀態，做完仍可接著做下一批
            batchSize = if (limit > 0) limit else 0,
            batchNumber = if (batched) batchNumber else 0,
        )
    }

    fun onOptionClick(original: Int) {
        val s = _state.value
        val item = s.current ?: return
        if (s.submitted || s.finished) return
        if (item.question.multi) {
            val next = if (original in s.selected) s.selected - original else s.selected + original
            _state.value = s.copy(selected = next)
        } else {
            // 單選：點一下就作答
            _state.value = s.copy(selected = setOf(original))
            submit()
        }
    }

    fun submit() {
        val s = _state.value
        val item = s.current ?: return
        if (s.submitted || s.finished || s.selected.isEmpty()) return
        val ok = s.selected == item.question.correct.toSet()
        _state.value = s.copy(
            submitted = true,
            lastCorrect = ok,
            answered = s.answered + 1,
            correct = s.correct + if (ok) 1 else 0,
            wrong = if (ok) s.wrong else s.wrong + item.question,
        )
        record(item.question.id, s.selected.sorted())
    }

    fun next() {
        val s = _state.value
        if (!s.submitted) return
        if (s.isLast) {
            finish()
        } else {
            _state.value = s.copy(index = s.index + 1, selected = emptySet(), submitted = false)
        }
    }

    fun finish() {
        if (_state.value.finished) return
        _state.update { it.copy(finished = true) }
        val r = round ?: return
        pendingWrites += repo.appScope.launch {
            writeLock.withLock { if (r.sessionId != 0L) repo.finishSession(r.sessionId) }
        }
        if (_state.value.batchSize > 0) {
            // 等作答紀錄寫完，再算還剩多少題可以做下一批
            viewModelScope.launch {
                pendingWrites.toList().joinAll()
                val remaining = candidates().size
                _state.update { it.copy(nextBatchRemaining = remaining) }
            }
        }
    }

    /** 分批模式：再抽下一批（不會重複這次已出過的題目） */
    fun nextBatch() {
        viewModelScope.launch {
            pendingWrites.toList().joinAll()
            val list = pickBatch(candidates())
            if (list.isNotEmpty()) {
                batchNumber++
                begin(list, mode)
            } else {
                _state.update { it.copy(nextBatchRemaining = 0) }
            }
        }
    }

    /** 只重做本輪答錯的題目（選項會重新打亂） */
    fun retryWrong() {
        val wrong = _state.value.wrong.distinctBy { it.id }
        if (wrong.isNotEmpty()) begin(wrong, QuizMode.RETRY)
    }

    /** 本輪全部題目再做一次 */
    fun restart() {
        begin(_state.value.items.map { it.question }, round?.mode ?: mode)
    }

    private fun record(questionId: Long, selected: List<Int>) {
        val r = round ?: return
        // 用 appScope：就算使用者答完馬上離開畫面，紀錄也會寫完
        pendingWrites += repo.appScope.launch {
            writeLock.withLock {
                if (r.sessionId == 0L) {
                    r.sessionId = repo.startSession(r.title, r.mode, setId.takeIf { it > 0 }, r.total)
                }
                repo.recordAnswer(r.sessionId, questionId, selected)
            }
        }
        pendingWrites.removeAll { it.isCompleted }
    }
}
