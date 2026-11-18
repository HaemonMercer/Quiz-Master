package com.quizmaster.app.ui.wrong

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.quizmaster.app.data.FREQUENT_WRONG_TIMES
import com.quizmaster.app.data.QuizMode
import com.quizmaster.app.data.WrongRecord
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.BatchCountPicker
import com.quizmaster.app.ui.common.ConfirmDialog
import com.quizmaster.app.ui.common.TagLabel
import com.quizmaster.app.ui.common.TypeTag
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.batchLimit
import com.quizmaster.app.ui.common.effectiveCount
import com.quizmaster.app.ui.common.formatTime
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors
import kotlinx.coroutines.launch

/**
 * 錯題集：答錯過的題目都會留在這裡，不會因為答對或重設進度而消失，只能手動刪除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongCollectionScreen(
    onBack: () -> Unit,
    /** setId <= 0 代表不分題庫 */
    onPractice: (mode: QuizMode, setId: Long, shuffle: Boolean, limit: Int) -> Unit,
) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val flow = remember { repo.observeWrongRecords() }
    val records by flow.collectAsStateWithLifecycle(initialValue = null)
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var batchCount by rememberSaveable { mutableStateOf(10) }
    var confirmClear by remember { mutableStateOf(false) }
    /** true = 只看常錯題（依答錯次數排序） */
    var frequentOnly by rememberSaveable { mutableStateOf(false) }
    /** 目前選的題庫分組（[SetGroup.key]）；null = 全部題庫 */
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<WrongRecord?>(null) }

    val groups = remember(records) { groupBySet(records.orEmpty()) }
    val group = groups.firstOrNull { it.key == groupKey }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "錯題集",
                onBack = onBack,
                actions = {
                    if (!records.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空錯題集")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = records ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("錯題集是空的", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "答錯的題目會自動收錄到這裡，\n之後答對也不會移除，只能手動刪除。",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        val inGroup = group?.records ?: list
        val frequent = remember(inGroup) {
            inGroup.filter { it.wrongTimes >= FREQUENT_WRONG_TIMES }
                .sortedWith(compareByDescending<WrongRecord> { it.wrongTimes }.thenByDescending { it.lastWrongAt })
        }
        val shown = if (frequentOnly) frequent else inGroup
        val practicable = shown.count { it.questionId != null }
        val practiceMode = if (frequentOnly) QuizMode.FREQUENT else QuizMode.COLLECTION
        val practiceSetId = group?.setId ?: -1L

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "共收錄 ${list.size} 題",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "答對不會移除，只能手動刪除",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (groups.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            Text("題庫", fontWeight = FontWeight.SemiBold)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = group == null,
                                    onClick = { groupKey = null },
                                    label = { Text("全部 ${list.size}") },
                                )
                                groups.forEach { g ->
                                    FilterChip(
                                        selected = g.key == group?.key,
                                        onClick = { groupKey = g.key },
                                        label = { Text("${g.name} ${g.records.size}", maxLines = 1) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !frequentOnly,
                                onClick = { frequentOnly = false },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                icon = {},
                                label = { Text("全部 ${inGroup.size}", maxLines = 1) },
                            )
                            SegmentedButton(
                                selected = frequentOnly,
                                onClick = { frequentOnly = true },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                icon = {},
                                label = { Text("常錯 ${frequent.size}", maxLines = 1) },
                            )
                        }
                        Text(
                            when {
                                !frequentOnly -> "依最近答錯排序"
                                frequent.isEmpty() -> "目前沒有答錯 $FREQUENT_WRONG_TIMES 次以上的題目"
                                else -> "答錯 $FREQUENT_WRONG_TIMES 次以上的題目，錯越多次排越前面"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (practicable > 0) {
                            Spacer(Modifier.height(12.dp))
                            BatchCountPicker(
                                available = practicable,
                                count = batchCount,
                                onCountChange = { batchCount = it },
                                shuffle = shuffle,
                                onShuffleChange = { shuffle = it },
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onPractice(practiceMode, practiceSetId, shuffle, batchLimit(batchCount, practicable))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                val label = if (frequentOnly) "練習常錯題" else "練習錯題集"
                                Text("$label（${effectiveCount(batchCount, practicable)} 題）")
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "點題目可查看正確答案與你上次的選擇",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.id }) { record ->
                RecordCard(record, onDelete = { pendingDelete = record })
            }
        }
    }

    pendingDelete?.let { record ->
        ConfirmDialog(
            title = "刪除這題？",
            message = "會從錯題集移除這題的紀錄，無法復原。",
            confirmText = "刪除",
            onConfirm = { scope.launch { repo.deleteWrongRecord(record) } },
            onDismiss = { pendingDelete = null },
        )
    }

    if (confirmClear) {
        // 有選題庫時只清空該題庫的紀錄
        val target = group
        ConfirmDialog(
            title = if (target == null) "清空錯題集？" else "清空「${target.name}」的錯題？",
            message = if (target == null) {
                "錯題集裡的所有紀錄都會被刪除，無法復原。（錯題本與作答紀錄不受影響）"
            } else {
                "這個題庫在錯題集裡的 ${target.records.size} 筆紀錄會被刪除，無法復原。其他題庫不受影響。"
            },
            confirmText = "清空",
            onConfirm = {
                scope.launch {
                    if (target == null) repo.clearWrongRecords() else repo.deleteWrongRecords(target.records)
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** 錯題集裡同一個題庫的紀錄；題庫已刪除的紀錄仍會依原本的題庫分在一起 */
private data class SetGroup(val key: String, val setId: Long?, val name: String, val records: List<WrongRecord>)

/** 依題庫分組，保持「最近答錯」的先後順序（list 本來就依最近答錯排序） */
private fun groupBySet(list: List<WrongRecord>): List<SetGroup> =
    list.groupBy { r -> r.setId?.let { "id:$it" } ?: "name:${r.setName}" }
        .map { (key, records) ->
            val first = records.first()
            SetGroup(key, first.setId, first.setName.ifBlank { "未分類" }, records)
        }

@Composable
private fun RecordCard(record: WrongRecord, onDelete: () -> Unit) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
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
                TypeTag(record.multi)
                if (record.setName.isNotBlank()) {
                    TagLabel(
                        record.setName,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (record.wrongTimes >= FREQUENT_WRONG_TIMES) {
                    TagLabel("常錯", AnswerColors.wrongBg(), AnswerColors.wrongFg())
                }
                if (record.questionId == null) {
                    TagLabel(
                        "原題已刪除",
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "錯 ${record.wrongTimes} 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = AnswerColors.wrongFg(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                record.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "最後答錯：${formatTime(record.lastWrongAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                record.options.forEachIndexed { i, option ->
                    val isCorrect = i in record.correct
                    val pickedWrong = i in record.lastSelected && !isCorrect
                    val color = when {
                        isCorrect -> AnswerColors.correctFg()
                        pickedWrong -> AnswerColors.wrongFg()
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            when {
                                isCorrect -> Icons.Filled.CheckCircle
                                pickedWrong -> Icons.Filled.Cancel
                                else -> Icons.Filled.RadioButtonUnchecked
                            },
                            null,
                            tint = if (isCorrect || pickedWrong) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            option + if (i in record.lastSelected) "（你的選擇）" else "",
                            color = color,
                            fontWeight = if (isCorrect || pickedWrong) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                if (record.explanation.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("解析：${record.explanation}", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "第一次答錯：${formatTime(record.firstWrongAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刪除")
                    }
                }
            }
        }
    }
}
