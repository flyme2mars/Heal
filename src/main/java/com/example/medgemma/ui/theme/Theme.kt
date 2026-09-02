package com.example.medgemma.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HealLightColorScheme = lightColorScheme(
    primary = Color(0xFF1C1B1F),
    onPrimary = Color(0xFFFFFBFF),
    primaryContainer = Color(0xFFE6E0E9),
    onPrimaryContainer = Color(0xFF1C1B1F),
    secondary = Color(0xFF49454F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE6F0),
    onSecondaryContainer = Color(0xFF1C1B1F),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val HealDarkColorScheme = darkColorScheme(
    primary = HealOnSurface,
    onPrimary = HealBlack,
    primaryContainer = HealSurfaceContainer,
    onPrimaryContainer = HealOnSurface,
    secondary = HealOnSurfaceMuted,
    onSecondary = HealBlack,
    secondaryContainer = HealSurfaceHigh,
    onSecondaryContainer = HealOnSurface,
    background = HealBlack,
    onBackground = HealOnSurface,
    surface = HealSurface,
    onSurface = HealOnSurface,
    surfaceContainer = HealSurfaceContainer,
    surfaceContainerHigh = HealSurfaceHigh,
    surfaceContainerHighest = Color(0xFF1F1F1F),
    onSurfaceVariant = HealOnSurfaceMuted,
    outline = HealOutline,
    outlineVariant = Color(0xFF1F1F1F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MedGemmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> HealDarkColorScheme
        else -> HealLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HealTypography,
        shapes = HealShapes,
        content = content
    )
}