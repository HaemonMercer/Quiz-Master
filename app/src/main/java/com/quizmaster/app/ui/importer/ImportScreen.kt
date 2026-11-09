package com.quizmaster.app.ui.importer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quizmaster.app.ui.common.AppTopBar
import com.quizmaster.app.ui.common.appCardColors
import com.quizmaster.app.ui.common.rememberRepository
import com.quizmaster.app.ui.theme.AnswerColors
import com.quizmaster.app.util.QuestionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(setId: Long, onBack: () -> Unit) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val result = remember(text) { QuestionParser.parse(text) }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (content == null) {
                    Toast.makeText(context, "無法讀取檔案", Toast.LENGTH_SHORT).show()
                } else {
                    text = content.removePrefix("﻿")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "匯入題目", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Icon(Icons.Filled.Lightbulb, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text("格式說明", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(QuestionParser.FORMAT_HELP, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. [多選] 下列哪些是質數？\nA. 9\n*B. 13\n*C. 29\n解析: 9 = 3×3",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openLauncher.launch(arrayOf("text/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("開啟文字檔")
                }
                OutlinedButton(onClick = { text = QuestionParser.SAMPLE }, modifier = Modifier.weight(1f)) {
                    Text("填入範例")
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("在這裡貼上題目") },
                minLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            if (text.isNotBlank()) {
                val singles = result.questions.count { !it.multi }
                val multis = result.questions.size - singles
                Text(
                    "已辨識 ${result.questions.size} 題（單選 $singles、多選 $multis）",
                    fontWeight = FontWeight.SemiBold,
                    color = if (result.questions.isNotEmpty()) AnswerColors.correctFg() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.errors.isNotEmpty()) {
                    Text(
                        "有 ${result.errors.size} 段無法辨識，匯入時會略過：",
                        color = AnswerColors.wrongFg(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    result.errors.take(10).forEach {
                        Text("• $it", color = AnswerColors.wrongFg(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (result.errors.size > 10) {
                        Text("…還有 ${result.errors.size - 10} 個", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = {
                    importing = true
                    scope.launch {
                        val count = repo.importQuestions(setId, result.questions)
                        Toast.makeText(context, "已匯入 $count 題", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                enabled = result.questions.isNotEmpty() && !importing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (result.questions.isEmpty()) "匯入" else "匯入 ${result.questions.size} 題")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
