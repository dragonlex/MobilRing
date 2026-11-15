package com.example.MobilRing24KRSK.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Схема Material3, собранная из нашей палитры.
 * Приложение всегда светлое, поэтому тёмная тема не подключается.
 */
private val BrandColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = BrandBlue,
    onPrimaryContainer = Ink,
    secondary = BrandBluePressed,
    onSecondary = Ink,
    tertiary = BrandBlue,
    onTertiary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = BrandBlue,
    onSurfaceVariant = Ink,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    outline = Ink,
    outlineVariant = Muted
)

/**
 * Корневая тема: оборачивает каждый экран, чтобы Material-компоненты
 * (поля ввода, диалоги, карточки) брали наши цвета и шрифты.
 */
@Composable
fun LostAndFindTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BrandColorScheme,
        typography = Typography,
        content = content
    )
}
