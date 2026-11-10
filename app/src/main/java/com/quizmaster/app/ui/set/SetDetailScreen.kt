package com.quizmaster.app.ui.set

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quizmaster.app.data.Question
import com.quizmaster.app.data.QuizMode
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.BatchCountPicker
import com.quizmaster.app.ui.common.batchLimit
import com.quizmaster.app.ui.common.effectiveCount
import com.quizmaster.app.ui.common.ConfirmDialog
import com.quizmaster.app.ui.common.SetNameDialog
import com.quizmaster.app.ui.common.TagLabel
import com.quizmaster.app.ui.common.TypeTag
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.percent
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BatchSource(
    val mode: QuizMode,
    val label: String,
    val count: Int,
    val hint: String,
    val emptyHint: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    setId: Long,
    onBack: () -> Unit,
    onEditQuestion: (questionId: Long) -> Unit,
    onImport: () -> Unit,
    onStartQuiz: (mode: QuizMode, shuffle: Boolean, limit: Int) -> Unit,
) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val setFlow = remember(setId) { repo.observeSet(setId) }
    val questionsFlow = remember(setId) { repo.observeQuestions(setId) }
    val set by setFlow.collectAsStateWithLifecycle(initialValue = null)
    val questions by questionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var shuffle by rememberSaveable { mutableStateOf(true) }
    var sourceName by rememberSaveable { mutableStateOf(QuizMode.UNDONE.name) }
    var sourcePicked by rememberSaveable { mutableStateOf(false) }
    var batchCount by rememberSaveable { mutableStateOf(10) }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = repo.exportText(setId)
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    }.isSuccess
                }
                Toast.makeText(context, if (ok) "已儲存" else "儲存失敗", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val total = questions.size
    val done = questions.count { it.attemptCount > 0 }
    val wrong = questions.count { it.inWrongBook }
    val undone = total - done
    val attempts = questions.sumOf { it.attemptCount }
    val corrects = questions.sumOf { it.correctCount }

    // 全部都做過了：預設改成「錯過的」或「全部」
    LaunchedEffect(total, undone, wrong) {
        if (!sourcePicked && total > 0 && undone == 0) {
            sourceName = if (wrong > 0) QuizMode.WRONG.name else QuizMode.ALL.name
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = set?.name ?: "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("重新命名") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuOpen = false; showRename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("分享題目文字") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val text = repo.exportText(setId)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, set?.name ?: "題庫")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "分享題庫"))
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("儲存為文字檔（備份）") },
                            leadingIcon = { Icon(Icons.Filled.Save, null) },
                            onClick = { menuOpen = false; saveLauncher.launch("${set?.name ?: "題庫"}.txt") },
                        )
                        DropdownMenuItem(
                            text = { Text("重設作答進度") },
                            leadingIcon = { Icon(Icons.Filled.RestartAlt, null) },
                            onClick = { menuOpen = false; confirmReset = true },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("刪除題庫", color = AnswerColors.wrongFg()) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = AnswerColors.wrongFg()) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!set?.description.isNullOrBlank()) {
                item {
                    Text(
                        set?.description.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("題目", "$total", Modifier.weight(1f))
                        Stat("已做", "$done", Modifier.weight(1f))
                        Stat("錯題", "$wrong", Modifier.weight(1f))
                        Stat("正確率", if (attempts == 0) "—" else "${percent(corrects, attempts)}%", Modifier.weight(1f))
                    }
                }
            }

            item {
                val sources = listOf(
                    BatchSource(QuizMode.UNDONE, "全新", undone, "從沒做過的題目", "這個題庫的題目都做過了"),
                    BatchSource(QuizMode.WRONG, "錯過的", wrong, "目前在錯題本中的題目", "目前沒有錯題"),
                    BatchSource(QuizMode.ALL, "全部", total, "整個題庫的題目", "這個題庫還沒有題目"),
                )
                val current = sources.first { it.mode.name == sourceName }
                val available = current.count

                Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("分批測驗", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "選擇題目來源和題數，每次作答選項順序都會隨機打亂",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))

                        Text("題目來源", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            sources.forEachIndexed { i, src ->
                                SegmentedButton(
                                    selected = src.mode.name == sourceName,
                                    onClick = { sourceName = src.mode.name; sourcePicked = true },
                                    shape = SegmentedButtonDefaults.itemShape(i, sources.size),
                                    icon = {},
                                    label = { Text("${src.label} ${src.count}", maxLines = 1) },
                                )
                            }
                        }
                        Text(
                            if (available > 0) "${current.hint}，共 $available 題" else current.emptyHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (available > 0) MaterialTheme.colorScheme.onSurfaceVariant else AnswerColors.wrongFg(),
                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        )

                        if (available > 0) {
                            BatchCountPicker(
                                available = available,
                                count = batchCount,
                                onCountChange = { batchCount = it },
                                shuffle = shuffle,
                                onShuffleChange = { shuffle = it },
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Button(
                            onClick = { onStartQuiz(current.mode, shuffle, batchLimit(batchCount, available)) },
                            enabled = available > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (available > 0) "開始測驗（${effectiveCount(batchCount, available)} 題）" else "開始測驗"
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        "題目列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onImport) {
                        Icon(Icons.Filled.FileUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("匯入")
                    }
                    TextButton(onClick = { onEditQuestion(-1L) }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新增")
                    }
                }
            }

            if (questions.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "這個題庫還沒有題目",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onImport) { Text("貼上文字匯入") }
                            Button(onClick = { onEditQuestion(-1L) }) { Text("手動新增") }
                        }
                    }
                }
            }

            itemsIndexed(questions, key = { _, q -> q.id }) { index, q ->
                QuestionRow(index = index, question = q, onClick = { onEditQuestion(q.id) })
            }
        }
    }

    if (showRename) {
        set?.let { current ->
            SetNameDialog(
                title = "重新命名",
                initialName = current.name,
                initialDescription = current.description,
                onConfirm = { name, desc ->
                    scope.launch { repo.updateSet(current.copy(name = name, description = desc)) }
                },
                onDismiss = { showRename = false },
            )
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "重設作答進度？",
            message = "會清除這個題庫所有題目的作答次數與錯題標記（練習歷史紀錄會保留）。",
            confirmText = "重設",
            onConfirm = { scope.launch { repo.resetSetProgress(setId) } },
            onDismiss = { confirmReset = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "刪除題庫？",
            message = "「${set?.name.orEmpty()}」和裡面的 $total 道題目、相關作答紀錄都會被刪除，無法復原。",
            confirmText = "刪除",
            onConfirm = {
                val current = set
                if (current != null) {
                    scope.launch {
                        repo.deleteSet(current)
                        onBack()
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuestionRow(index: Int, question: Question, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(onClick = onClick),
        colors = appCardColors(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TypeTag(question.multi)
                if (question.inWrongBook) {
                    TagLabel("錯題本", AnswerColors.wrongBg(), AnswerColors.wrongFg())
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                question.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                statusText(question),
                style = MaterialTheme.typography.bodySmall,
                color = when (question.lastCorrect) {
                    true -> AnswerColors.correctFg()
                    false -> AnswerColors.wrongFg()
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun statusText(q: Question): String =
    if (q.attemptCount == 0) {
        "未做過"
    } else {
        val last = if (q.lastCorrect == true) "上次答對" else "上次答錯"
        "做過 ${q.attemptCount} 次 · 答錯 ${q.wrongCount} 次 · $last"
    }
