package com.quizmaster.app.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quizmaster.app.data.QuizMode
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.ConfirmDialog
import com.quizmaster.app.ui.common.TypeTag
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.percent
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors

private enum class OptionState { NORMAL, SELECTED, CORRECT, MISSED, WRONG }

@Composable
fun QuizScreen(
    mode: QuizMode,
    setId: Long,
    shuffleQuestions: Boolean,
    onBack: () -> Unit,
) {
    val repo = rememberRepository()
    val vm: QuizViewModel = viewModel { QuizViewModel(repo, mode, setId, shuffleQuestions) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }

    val inProgress = !state.loading && !state.finished && state.items.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = state.title,
                onBack = onBack,
                actions = {
                    if (inProgress && state.answered > 0) {
                        TextButton(onClick = { confirmEnd = true }) { Text("結束") }
                    }
                },
            )
        },
        bottomBar = {
            if (inProgress) {
                BottomActions(
                    multi = state.current?.question?.multi == true,
                    submitted = state.submitted,
                    hasSelection = state.selected.isNotEmpty(),
                    isLast = state.isLast,
                    onSubmit = vm::submit,
                    onNext = vm::next,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() -> EmptyQuiz(onBack, Modifier.align(Alignment.Center))
                state.finished -> ResultContent(state, onRetryWrong = vm::retryWrong, onRestart = vm::restart, onBack = onBack)
                else -> QuestionContent(state, onOptionClick = vm::onOptionClick)
            }
        }
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = "結束這次練習？",
            message = "已作答的 ${state.answered} 題會保留紀錄。",
            confirmText = "結束",
            onConfirm = vm::finish,
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun QuestionContent(state: QuizUiState, onOptionClick: (Int) -> Unit) {
    val item = state.current ?: return
    val q = item.question
    val correctSet = q.correct.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        LinearProgressIndicator(
            progress = { (state.index + if (state.submitted) 1 else 0).toFloat() / state.items.size },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${state.index + 1} / ${state.items.size} 題",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            TypeTag(q.multi)
            Spacer(Modifier.weight(1f))
            Text(
                "答對 ${state.correct} / ${state.answered}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
            Text(
                q.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(20.dp),
            )
        }
        if (q.multi && !state.submitted) {
            Text(
                "多選題：選出所有正確答案後按「提交」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        item.order.forEachIndexed { displayIndex, original ->
            val isSelected = original in state.selected
            val isCorrect = original in correctSet
            val optionState = when {
                !state.submitted -> if (isSelected) OptionState.SELECTED else OptionState.NORMAL
                isSelected && isCorrect -> OptionState.CORRECT
                isCorrect -> OptionState.MISSED
                isSelected -> OptionState.WRONG
                else -> OptionState.NORMAL
            }
            OptionCard(
                letter = 'A' + displayIndex,
                text = q.options[original],
                multi = q.multi,
                selected = isSelected,
                state = optionState,
                enabled = !state.submitted,
                onClick = { onOptionClick(original) },
            )
            Spacer(Modifier.height(10.dp))
        }

        if (state.submitted) {
            val correctLetters = item.order
                .mapIndexedNotNull { d, original -> if (original in correctSet) 'A' + d else null }
                .joinToString("、")
            FeedbackCard(correct = state.lastCorrect, correctLetters = correctLetters, explanation = q.explanation)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionCard(
    letter: Char,
    text: String,
    multi: Boolean,
    selected: Boolean,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, border) = when (state) {
        OptionState.NORMAL -> scheme.surfaceContainerLowest to scheme.outline
        OptionState.SELECTED -> scheme.primaryContainer to scheme.primary
        OptionState.CORRECT -> AnswerColors.correctBg() to AnswerColors.correctFg()
        OptionState.MISSED -> scheme.surfaceContainerLowest to AnswerColors.correctFg()
        OptionState.WRONG -> AnswerColors.wrongBg() to AnswerColors.wrongFg()
    }
    val shape = RoundedCornerShape(14.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(if (state == OptionState.NORMAL) 1.dp else 2.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multi) {
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(10.dp))
            } else {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(10.dp))
            }
            Text("$letter.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            when (state) {
                OptionState.CORRECT, OptionState.MISSED ->
                    Icon(Icons.Filled.CheckCircle, "正確答案", tint = AnswerColors.correctFg(), modifier = Modifier.padding(8.dp))
                OptionState.WRONG ->
                    Icon(Icons.Filled.Cancel, "錯誤", tint = AnswerColors.wrongFg(), modifier = Modifier.padding(8.dp))
                else -> Unit
            }
        }
    }
}

@Composable
private fun FeedbackCard(correct: Boolean, correctLetters: String, explanation: String) {
    val fg = if (correct) AnswerColors.correctFg() else AnswerColors.wrongFg()
    val bg = if (correct) AnswerColors.correctBg() else AnswerColors.wrongBg()
    Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = fg)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (correct) "答對了！" else "答錯了，正確答案是 $correctLetters",
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (explanation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("解析：$explanation", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BottomActions(
    multi: Boolean,
    submitted: Boolean,
    hasSelection: Boolean,
    isLast: Boolean,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            when {
                submitted -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (isLast) "查看結果" else "下一題", style = MaterialTheme.typography.titleMedium)
                }
                multi -> Button(
                    onClick = onSubmit,
                    enabled = hasSelection,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("提交", style = MaterialTheme.typography.titleMedium) }
                else -> Text(
                    "點選一個答案",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultContent(
    state: QuizUiState,
    onRetryWrong: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    val pct = percent(state.correct, state.answered)
    val wrong = state.wrong.distinctBy { it.id }
    val color: Color = when {
        pct >= 80 -> AnswerColors.correctFg()
        pct >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> AnswerColors.wrongFg()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                state.answered == 0 -> "沒有作答"
                pct == 100 -> "全對！太厲害了"
                pct >= 80 -> "表現很好！"
                pct >= 60 -> "不錯，再加把勁"
                else -> "多練習幾次就會進步"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text("$pct%", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = color)
        Text(
            "答對 ${state.correct} 題 / 已答 ${state.answered} 題（共 ${state.items.size} 題）",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (wrong.isNotEmpty()) {
            Button(onClick = onRetryWrong, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("重做答錯的 ${wrong.size} 題")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("全部再做一次")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }

        if (wrong.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "本次答錯的題目（已加入錯題本）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            wrong.forEach { q ->
                Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(q.text, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "正確答案：" + q.correct.joinToString("、") { q.options.getOrElse(it) { "?" } },
                            style = MaterialTheme.typography.bodySmall,
                            color = AnswerColors.correctFg(),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyQuiz(onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.CheckCircle,
            null,
            tint = AnswerColors.correctFg(),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("沒有符合條件的題目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}
