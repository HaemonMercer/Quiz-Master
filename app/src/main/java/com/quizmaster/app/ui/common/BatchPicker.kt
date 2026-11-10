package com.quizmaster.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val PRESETS = listOf(5, 10, 20, 30, 50)

/** 實際要出的題數：0 或超過可用題數 = 全部 */
fun effectiveCount(count: Int, available: Int): Int =
    if (count <= 0 || count > available) available else count

/** 傳給作答畫面的上限：0 代表不限 */
fun batchLimit(count: Int, available: Int): Int =
    effectiveCount(count, available).let { if (it >= available) 0 else it }

/** 選擇題數（預設選項＋自訂）＋是否隨機抽題 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchCountPicker(
    available: Int,
    count: Int,
    onCountChange: (Int) -> Unit,
    shuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    val effective = effectiveCount(count, available)
    val isAll = effective >= available
    val isCustom = !isAll && effective !in PRESETS

    Column {
        Text("題數", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.filter { it < available }.forEach { n ->
                FilterChip(
                    selected = !isAll && effective == n,
                    onClick = { onCountChange(n) },
                    label = { Text("$n 題") },
                )
            }
            FilterChip(
                selected = isAll,
                onClick = { onCountChange(0) },
                label = { Text("全部 $available 題") },
                enabled = available > 0,
            )
            if (available > 1) {
                FilterChip(
                    selected = isCustom,
                    onClick = { showCustom = true },
                    label = { Text(if (isCustom) "自訂：$effective 題" else "自訂") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("隨機抽題")
                Text(
                    if (shuffle) "隨機抽出題目，順序也打亂" else "依題目列表順序出題",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = shuffle, onCheckedChange = onShuffleChange)
        }
    }

    if (showCustom) {
        var text by remember { mutableStateOf(if (isCustom) effective.toString() else "") }
        val value = text.toIntOrNull()
        val valid = value != null && value in 1..available
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("自訂題數") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(5) },
                    label = { Text("1 – $available 題") },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = { onCountChange(value!!); showCustom = false },
                ) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("取消") } },
        )
    }
}
