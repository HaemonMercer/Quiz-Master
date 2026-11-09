package com.quizmaster.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4255FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFFFFB020),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E3EE),
    onSurfaceVariant = Color(0xFF586380),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEF0F8),
    surfaceContainerHigh = Color(0xFFE8EAF4),
    surfaceContainerHighest = Color(0xFFE2E4EF),
    outline = Color(0xFFD9DDE8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF2E3FD0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF2E2F55),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFC85C),
    background = Color(0xFF0A092D),
    onBackground = Color(0xFFE4E3F0),
    surface = Color(0xFF0A092D),
    onSurface = Color(0xFFE4E3F0),
    surfaceVariant = Color(0xFF2E3856),
    onSurfaceVariant = Color(0xFFAEB4CF),
    surfaceContainerLowest = Color(0xFF14133A),
    surfaceContainerLow = Color(0xFF1C1B46),
    surfaceContainer = Color(0xFF22214E),
    surfaceContainerHigh = Color(0xFF2E3856),
    surfaceContainerHighest = Color(0xFF353F60),
    outline = Color(0xFF3B4466),
)

@Composable
fun QuizTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** 答對／答錯的顏色（跟隨深色模式） */
object AnswerColors {
    @Composable
    fun correctBg(): Color = if (isSystemInDarkTheme()) Color(0xFF173D2A) else Color(0xFFE3F6EA)

    @Composable
    fun correctFg(): Color = if (isSystemInDarkTheme()) Color(0xFF7EE2A8) else Color(0xFF18794E)

    @Composable
    fun wrongBg(): Color = if (isSystemInDarkTheme()) Color(0xFF4A1C24) else Color(0xFFFDE8EA)

    @Composable
    fun wrongFg(): Color = if (isSystemInDarkTheme()) Color(0xFFFF9EA8) else Color(0xFFC62838)
}
