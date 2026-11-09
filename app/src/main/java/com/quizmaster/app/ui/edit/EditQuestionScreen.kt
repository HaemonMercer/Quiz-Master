package com.quizmaster.app.ui.edit

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quizmaster.app.data.Question
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.ConfirmDialog
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors
import kotlinx.coroutines.launch

private data class OptionDraft(val text: String = "", val correct: Boolean = false)

private const val MAX_OPTIONS = 10

@Composable
fun EditQuestionScreen(setId: Long, questionId: Long, onBack: () -> Unit) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isNew = questionId <= 0L

    var original by remember { mutableStateOf<Question?>(null) }
    var loaded by remember { mutableStateOf(isNew) }
    var text by remember { mutableStateOf("") }
    var multi by remember { mutableStateOf(false) }
    val options = remember { mutableStateListOf(OptionDraft(), OptionDraft(), OptionDraft(), OptionDraft()) }
    var explanation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf(0) }

    LaunchedEffect(questionId) {
        if (!isNew) {
            repo.getQuestion(questionId)?.let { q ->
                original = q
                text = q.text
                multi = q.multi
                explanation = q.explanation
                options.clear()
                q.options.forEachIndexed { i, o -> options.add(OptionDraft(o, i in q.correct)) }
            }
            loaded = true
        }
    }

    fun resetForm() {
        text = ""
        explanation = ""
        options.clear()
        repeat(4) { options.add(OptionDraft()) }
        error = null
    }

    fun markCorrect(index: Int) {
        if (multi) {
            options[index] = options[index].copy(correct = !options[index].correct)
        } else {
            for (j in options.indices) options[j] = options[j].copy(correct = j == index)
        }
        error = null
    }

    fun buildQuestion(): Question? {
        val cleaned = options.filter { it.text.isNotBlank() }
        val correct = cleaned.mapIndexedNotNull { i, o -> if (o.correct) i else null }
        error = when {
            text.isBlank() -> "請輸入題目"
            cleaned.size < 2 -> "至少需要 2 個選項"
            correct.isEmpty() -> "請勾選正確答案"
            !multi && correct.size != 1 -> "單選題只能有 1 個正確答案"
            else -> null
        }
        if (error != null) return null
        val base = original ?: Question(setId = setId, text = "", options = emptyList(), correct = emptyList(), multi = false)
        return base.copy(
            text = text.trim(),
            options = cleaned.map { it.text.trim() },
            correct = correct,
            multi = multi,
            explanation = explanation.trim(),
        )
    }

    fun save(continueAdding: Boolean) {
        val q = buildQuestion() ?: return
        scope.launch {
            repo.saveQuestion(q)
            if (continueAdding) {
                savedCount++
                resetForm()
                Toast.makeText(context, "已儲存，繼續新增下一題", Toast.LENGTH_SHORT).show()
            } else {
                onBack()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = if (isNew) "新增題目" else "編輯題目",
                onBack = onBack,
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "刪除題目")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isNew && savedCount > 0) {
                Text(
                    "本次已新增 $savedCount 題",
                    color = AnswerColors.correctFg(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("題目") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // 題型
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("題型", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                ChoiceButton("單選", selected = !multi) {
                    if (multi) {
                        multi = false
                        // 只保留第一個正確答案
                        val first = options.indexOfFirst { it.correct }
                        for (j in options.indices) options[j] = options[j].copy(correct = j == first)
                    }
                }
                ChoiceButton("多選", selected = multi) { multi = true }
            }

            Text(
                if (multi) "勾選所有正確答案（可多個）" else "點選唯一的正確答案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (multi) {
                        Checkbox(checked = option.correct, onCheckedChange = { markCorrect(index) })
                    } else {
                        RadioButton(selected = option.correct, onClick = { markCorrect(index) })
                    }
                    OutlinedTextField(
                        value = option.text,
                        onValueChange = { options[index] = options[index].copy(text = it); error = null },
                        label = { Text("選項 ${'A' + index}") },
                        modifier = Modifier.weight(1f),
                    )
                    if (options.size > 2) {
                        IconButton(onClick = { options.removeAt(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "移除選項")
                        }
                    }
                }
            }
            if (options.size < MAX_OPTIONS) {
                TextButton(onClick = { options.add(OptionDraft()) }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增選項")
                }
            }

            OutlinedTextField(
                value = explanation,
                onValueChange = { explanation = it },
                label = { Text("解析（可留空）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = AnswerColors.wrongFg(), fontWeight = FontWeight.SemiBold)
            }

            Button(onClick = { save(continueAdding = false) }, modifier = Modifier.fillMaxWidth()) {
                Text("儲存")
            }
            if (isNew) {
                OutlinedButton(onClick = { save(continueAdding = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("儲存並新增下一題")
                }
            }

            original?.let { q -> StatsCard(q, onRemoveFromWrongBook = {
                scope.launch {
                    repo.setWrongBook(q.id, false)
                    original = q.copy(inWrongBook = false, correctStreak = 0)
                }
            }) }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "刪除這道題目？",
            message = "題目和它的作答紀錄都會被刪除。",
            confirmText = "刪除",
            onConfirm = {
                original?.let { q ->
                    scope.launch {
                        repo.deleteQuestion(q)
                        onBack()
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun StatsCard(q: Question, onRemoveFromWrongBook: () -> Unit) {
    Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("作答統計", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (q.attemptCount == 0) {
                Text("還沒有作答紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("作答 ${q.attemptCount} 次，答對 ${q.correctCount} 次，答錯 ${q.wrongCount} 次")
            }
            if (q.inWrongBook) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目前在錯題本中", color = AnswerColors.wrongFg(), modifier = Modifier.weight(1f))
                    TextButton(onClick = onRemoveFromWrongBook) { Text("移出錯題本") }
                }
            }
        }
    }
}
