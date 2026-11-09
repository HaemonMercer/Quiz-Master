package com.quizmaster.app.ui.wrong

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quizmaster.app.data.WRONG_BOOK_CLEAR_STREAK
import com.quizmaster.app.data.WrongItem
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.ConfirmDialog
import com.quizmaster.app.ui.common.TagLabel
import com.quizmaster.app.ui.common.TypeTag
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors
import kotlinx.coroutines.launch

@Composable
fun WrongBookScreen(onBack: () -> Unit, onPracticeAll: (shuffle: Boolean) -> Unit) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val flow = remember { repo.observeWrongItems() }
    val wrongItems by flow.collectAsStateWithLifecycle(initialValue = null)
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "錯題本",
                onBack = onBack,
                actions = {
                    if (!wrongItems.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空錯題本")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = wrongItems ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AnswerColors.correctFg(), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("錯題本是空的", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "答錯的題目會自動加入這裡，\n連續答對 $WRONG_BOOK_CLEAR_STREAK 次後自動移出。",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("共 ${list.size} 題錯題", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "連續答對 $WRONG_BOOK_CLEAR_STREAK 次會自動移出錯題本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onPracticeAll(shuffle) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("練習全部錯題")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("題目順序也隨機", modifier = Modifier.weight(1f))
                            Switch(checked = shuffle, onCheckedChange = { shuffle = it })
                        }
                    }
                }
            }
            item {
                Text(
                    "點題目可查看正確答案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.question.id }) { item ->
                WrongCard(item, onRemove = { scope.launch { repo.setWrongBook(item.question.id, false) } })
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空錯題本？",
            message = "所有題目都會移出錯題本（作答紀錄會保留）。",
            confirmText = "清空",
            onConfirm = { scope.launch { repo.clearWrongBook() } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun WrongCard(item: WrongItem, onRemove: () -> Unit) {
    val q = item.question
    var expanded by rememberSaveable(q.id) { mutableStateOf(false) }
    Card(
        colors = appCardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .clickable { expanded = !expanded }
            .animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeTag(q.multi)
                TagLabel(item.setName, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.weight(1f))
                Text(
                    "錯 ${q.wrongCount} 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = AnswerColors.wrongFg(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                q.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                q.options.forEachIndexed { i, option ->
                    val isCorrect = i in q.correct
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            null,
                            tint = if (isCorrect) AnswerColors.correctFg() else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            option,
                            color = if (isCorrect) AnswerColors.correctFg() else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isCorrect) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                if (q.explanation.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("解析：${q.explanation}", style = MaterialTheme.typography.bodyMedium)
                }
                if (q.correctStreak > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已連續答對 ${q.correctStreak} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnswerColors.correctFg(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRemove) { Text("移出錯題本") }
                }
            }
        }
    }
}
