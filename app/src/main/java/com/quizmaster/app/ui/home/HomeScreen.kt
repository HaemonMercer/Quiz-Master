package com.quizmaster.app.ui.home

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quizmaster.app.data.SetSummary
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.SetNameDialog
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenSet: (Long) -> Unit,
    onWrongBook: () -> Unit,
    onHistory: () -> Unit,
) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val flow = remember { repo.observeSetSummaries() }
    val sets by flow.collectAsStateWithLifecycle(initialValue = null)
    var showCreate by remember { mutableStateOf(false) }

    val totalWrong = sets?.sumOf { it.wrong } ?: 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "題庫通",
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Filled.History, contentDescription = "作答紀錄")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新增題庫") },
            )
        },
    ) { padding ->
        val list = sets
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                modifier = Modifier.padding(padding),
                onCreate = { showCreate = true },
                onLoadDemo = { scope.launch { onOpenSet(repo.loadDemoSet()) } },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    WrongBookCard(count = totalWrong, onClick = onWrongBook)
                }
                item {
                    Text(
                        "我的題庫",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { it.id }) { set ->
                    SetCard(set = set, onClick = { onOpenSet(set.id) })
                }
            }
        }
    }

    if (showCreate) {
        SetNameDialog(
            title = "新增題庫",
            onConfirm = { name, desc -> scope.launch { onOpenSet(repo.createSet(name, desc)) } },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun WrongBookCard(count: Int, onClick: () -> Unit) {
    val hasWrong = count > 0
    Card(
        modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (hasWrong) AnswerColors.wrongBg() else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (hasWrong) AnswerColors.wrongFg() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("錯題本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (hasWrong) "共 $count 題待複習" else "目前沒有錯題，繼續保持！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SetCard(set: SetSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(onClick = onClick),
        colors = appCardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                set.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (set.description.isNotBlank()) {
                Text(
                    set.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${set.total} 題 · 已做 ${set.done} 題",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (set.wrong > 0) {
                    Text(
                        "錯題 ${set.wrong}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AnswerColors.wrongFg(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (set.total == 0) 0f else set.done.toFloat() / set.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onCreate: () -> Unit, onLoadDemo: () -> Unit) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Quiz,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("還沒有題庫", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "建立一個題庫，手動新增題目或貼上文字一次匯入。\n也可以先載入示範題庫試試看。",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreate) { Text("新增題庫") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLoadDemo) { Text("載入示範題庫") }
        }
    }
}
