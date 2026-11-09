package com.quizmaster.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quizmaster.app.data.AttemptWithQuestion
import com.quizmaster.app.data.QuizSession
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.ConfirmDialog
import com.quizmaster.app.ui.common.TagLabel
import com.quizmaster.app.ui.common.TypeTag
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.formatTime
import com.quizmaster.app.ui.common.percent
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val flow = remember { repo.observeSessions() }
    val sessions by flow.collectAsStateWithLifecycle(initialValue = null)
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "作答紀錄",
                onBack = onBack,
                actions = {
                    if (!sessions.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清除所有紀錄")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = sessions ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("還沒有作答紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        val totalAnswered = list.sumOf { it.answered }
        val totalCorrect = list.sumOf { it.correct }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "共 ${list.size} 次練習 · 作答 $totalAnswered 題 · 整體正確率 ${percent(totalCorrect, totalAnswered)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.id }) { s ->
                SessionCard(s, onClick = { onOpenSession(s.id) })
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清除所有作答紀錄？",
            message = "練習歷史會被刪除（題目的作答統計和錯題本不受影響）。",
            confirmText = "清除",
            onConfirm = { scope.launch { repo.clearHistory() } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SessionCard(s: QuizSession, onClick: () -> Unit) {
    val pct = percent(s.correct, s.answered)
    Card(
        colors = appCardColors(),
        modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    formatTime(s.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("答對 ${s.correct} / ${s.answered}（共 ${s.total} 題）", style = MaterialTheme.typography.bodyMedium)
                    if (s.finishedAt == null && s.answered < s.total) {
                        TagLabel("未完成", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "$pct%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (pct >= 80) AnswerColors.correctFg() else if (pct >= 60) MaterialTheme.colorScheme.tertiary else AnswerColors.wrongFg(),
            )
        }
    }
}

@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val sessionFlow = remember(sessionId) { repo.observeSession(sessionId) }
    val attemptsFlow = remember(sessionId) { repo.observeAttempts(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)
    val attempts by attemptsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = session?.title ?: "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "刪除此紀錄")
                    }
                },
            )
        },
    ) { padding ->
        val s = session ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(formatTime(s.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "答對 ${s.correct} / 已答 ${s.answered} 題（共 ${s.total} 題）· 正確率 ${percent(s.correct, s.answered)}%",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            items(attempts, key = { it.attempt.id }) { a ->
                AttemptCard(a)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "刪除這筆紀錄？",
            message = "只會刪除這次練習的歷史，不影響題目和錯題本。",
            confirmText = "刪除",
            onConfirm = {
                session?.let { s ->
                    scope.launch {
                        repo.deleteSession(s)
                        onBack()
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AttemptCard(a: AttemptWithQuestion) {
    val q = a.question
    val ok = a.attempt.wasCorrect
    fun names(indices: List<Int>) = indices.joinToString("、") { q.options.getOrElse(it) { "?" } }

    Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    null,
                    tint = if (ok) AnswerColors.correctFg() else AnswerColors.wrongFg(),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                TypeTag(q.multi)
            }
            Spacer(Modifier.height(6.dp))
            Text(q.text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "你的答案：${names(a.attempt.selected)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) AnswerColors.correctFg() else AnswerColors.wrongFg(),
            )
            if (!ok) {
                Text(
                    "正確答案：${names(q.correct)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnswerColors.correctFg(),
                )
            }
        }
    }
}
