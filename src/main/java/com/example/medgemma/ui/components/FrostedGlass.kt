package com.example.medgemma.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared monochromatic glass-or-surface tokens.
 * Primary/brand color is intentionally not used here.
 */
object GlassStyle {
    /** Hairline edge on glass surfaces. */
    @Composable
    fun border(alpha: Float = 0.14f): BorderStroke =
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))

    @Composable
    fun userBubble(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    @Composable
    fun assistantBubble(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    @Composable
    fun inset(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    @Composable
    fun field(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    @Composable
    fun fieldDisabled(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    @Composable
    fun iconWell(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
}
