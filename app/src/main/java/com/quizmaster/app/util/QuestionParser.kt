package com.quizmaster.app.util

import com.quizmaster.app.data.Question

data class ParsedQuestion(
    val text: String,
    val options: List<String>,
    val correct: List<Int>,
    val multi: Boolean,
    val explanation: String,
)

data class ParseResult(
    val questions: List<ParsedQuestion>,
    val errors: List<String>,
)

/**
 * 純文字題目格式（每題之間空一行）：
 *
 *   1. [多選] 題目內容（[多選]/[單選] 可省略，省略時有多個正確答案就自動當多選）
 *   A. 選項一
 *   *B. 正確選項（前面加 *）
 *   C. 選項三
 *   答案: B          ← 也可以不用 *，改寫這一行
 *   解析: 說明文字    ← 可省略
 */
object QuestionParser {

    private val optionRegex = Regex("""^\s*(\*)?\s*([A-Za-z])\s*[.．、)）:：]\s*(.*)$""")
    private val answerRegex = Regex(
        """^\s*(?:答案|正確答案|正确答案|answer|ans)\s*[:：]\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val explainRegex = Regex(
        """^\s*(?:解析|解釋|解释|說明|说明|explanation)\s*[:：]\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val numberPrefix = Regex("""^\s*(?:[Qq]\s*)?(?:第\s*)?\d+\s*(?:題|题)?\s*[.．、)）:：]\s*""")
    private val typeTag = Regex("""^\s*[\[【(（]\s*(單選題?|单选题?|多選題?|多选题?|複選題?|复选题?)\s*[\]】)）]\s*""")
    private val blankLineSplit = Regex("""\n[ \t　]*\n""")
    private val multiBlank = Regex("""\n[ \t　]*\n+""")

    fun parse(input: String): ParseResult {
        val normalized = input.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(blankLineSplit).map { it.trim() }.filter { it.isNotEmpty() }

        val questions = mutableListOf<ParsedQuestion>()
        val errors = mutableListOf<String>()
        blocks.forEachIndexed { index, block ->
            when (val r = parseBlock(block)) {
                is BlockResult.Ok -> questions += r.question
                is BlockResult.Error -> {
                    val head = block.lineSequence().first().take(18)
                    errors += "第 ${index + 1} 段（$head…）：${r.message}"
                }
            }
        }
        return ParseResult(questions, errors)
    }

    private sealed interface BlockResult {
        data class Ok(val question: ParsedQuestion) : BlockResult
        data class Error(val message: String) : BlockResult
    }

    private fun parseBlock(block: String): BlockResult {
        val lines = block.lines()
        val question = StringBuilder()
        val options = mutableListOf<String>()
        val letters = mutableListOf<Char>()
        val starred = mutableListOf<Int>()
        var answerText: String? = null
        val explanation = StringBuilder()
        var inExplanation = false
        var forcedMulti: Boolean? = null

        for ((i, raw) in lines.withIndex()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue

            if (inExplanation) {
                explanation.append('\n').append(line.trim())
                continue
            }

            // 第一行一定是題目
            if (i == 0) {
                var t = line.replaceFirst(numberPrefix, "")
                val tag = typeTag.find(t)
                if (tag != null) {
                    val word = tag.groupValues[1]
                    forcedMulti = word.startsWith("多") || word.startsWith("複") || word.startsWith("复")
                    t = t.substring(tag.range.last + 1)
                }
                question.append(t.trim())
                continue
            }

            val answer = answerRegex.find(line)
            if (answer != null) {
                answerText = answer.groupValues[1]
                continue
            }

            val explain = explainRegex.find(line)
            if (explain != null) {
                explanation.append(explain.groupValues[1].trim())
                inExplanation = true
                continue
            }

            val option = optionRegex.find(line)
            if (option != null) {
                if (option.groupValues[1] == "*") starred += options.size
                letters += option.groupValues[2].first().uppercaseChar()
                options += option.groupValues[3].trim()
                continue
            }

            // 其他行：選項出現前算題目的延續，之後算上一個選項的延續
            if (options.isEmpty()) {
                question.append('\n').append(line.trim())
            } else {
                options[options.lastIndex] = options.last() + "\n" + line.trim()
            }
        }

        val text = question.toString().trim()
        if (text.isEmpty()) return BlockResult.Error("缺少題目內容")
        if (options.size < 2) return BlockResult.Error("選項少於 2 個（選項格式如「A. 內容」）")
        if (options.any { it.isBlank() }) return BlockResult.Error("有空白的選項")

        val correct: List<Int> = if (answerText != null) {
            val wanted = answerText.uppercase().filter { it in 'A'..'Z' }
            val idx = wanted.map { letters.indexOf(it) }
            if (idx.isEmpty() || idx.any { it < 0 }) {
                return BlockResult.Error("「答案」行的字母找不到對應的選項")
            }
            idx.distinct().sorted()
        } else {
            starred.distinct().sorted()
        }

        if (correct.isEmpty()) {
            return BlockResult.Error("沒有標示正確答案（在選項前加 *，或加一行「答案: B」）")
        }
        val multi = forcedMulti ?: (correct.size > 1)
        if (!multi && correct.size > 1) {
            return BlockResult.Error("標示為單選，但有多個正確答案")
        }

        return BlockResult.Ok(
            ParsedQuestion(
                text = text,
                options = options,
                correct = correct,
                multi = multi,
                explanation = explanation.toString().trim(),
            )
        )
    }

    /** 匯出成同樣的格式，可以再匯入（用來備份或分享） */
    fun export(questions: List<Question>): String =
        questions.mapIndexed { i, q ->
            buildString {
                append(i + 1).append(". ")
                if (q.multi) append("[多選] ")
                appendLine(q.text.trim().replace(multiBlank, "\n"))
                q.options.forEachIndexed { j, option ->
                    if (j in q.correct) append('*')
                    append('A' + j).append(". ")
                    appendLine(option.trim().replace(multiBlank, "\n"))
                }
                if (q.explanation.isNotBlank()) {
                    append("解析: ").appendLine(q.explanation.trim().replace(multiBlank, "\n"))
                }
            }.trimEnd()
        }.joinToString("\n\n") + "\n"

    val FORMAT_HELP = """
        每題之間空一行。
        第一行是題目，可在前面加 [多選] 或 [單選]。
        選項用「A.」「B、」「C)」等開頭。
        正確選項前加 *，或另起一行寫「答案: AC」。
        可加一行「解析: …」作為說明。
    """.trimIndent()

    val SAMPLE = """
        1. 台灣最高的山是哪一座？
        A. 雪山
        *B. 玉山
        C. 合歡山
        D. 阿里山
        解析: 玉山主峰海拔約 3,952 公尺，是台灣也是東北亞最高峰。

        2. [多選] 下列哪些是質數？
        *A. 2
        B. 9
        *C. 13
        D. 21
        *E. 29
        解析: 9 = 3×3，21 = 3×7，所以都不是質數。

        3. 水的化學式是？
        A. CO2
        B. O2
        C. H2O2
        D. H2O
        答案: D

        4. [多選] 下列哪些屬於哺乳類動物？
        *A. 鯨魚
        B. 企鵝
        *C. 蝙蝠
        D. 鯊魚
        解析: 鯨魚和蝙蝠都是哺乳類；企鵝是鳥類，鯊魚是魚類。

        5. 光在真空中的速度大約是每秒多少公里？
        A. 3,000
        B. 30,000
        *C. 300,000
        D. 3,000,000

        6. [多選] 下列哪些是開發 Android App 常用的程式語言？
        *A. Kotlin
        *B. Java
        C. Swift
        D. Objective-C
        解析: Swift 和 Objective-C 主要用於 Apple 平台。
    """.trimIndent()
}
