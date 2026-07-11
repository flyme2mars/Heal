package com.example.medgemma.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Shared monochromatic glass tokens — dense frost, low-contrast borders.
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

    /** Heavier surface wash so haze reads milky, not clear. */
    @Composable
    fun barTint(): Color =
        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)

    /** Cheaper wash while streaming / scrolling — less blur cost. */
    @Composable
    fun barTintSoft(): Color =
        MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)

    @Composable
    fun chipTint(): Color =
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
}

/**
 * Top / bottom chrome over scrolling chat.
 * [soft] uses thinner blur (during generation) to cut GPU cost.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.frostedGlassBar(hazeState: HazeState, soft: Boolean = false): Modifier {
    val style = if (soft) {
        HazeMaterials.thin(GlassStyle.barTintSoft())
    } else {
        HazeMaterials.thick(GlassStyle.barTint())
    }
    return hazeEffect(state = hazeState, style = style)
}

/** Compact controls floating over chat (e.g. jump-to-bottom). */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.frostedGlassChip(hazeState: HazeState, soft: Boolean = false): Modifier {
    val style = if (soft) {
        HazeMaterials.thin(GlassStyle.chipTint())
    } else {
        HazeMaterials.regular(GlassStyle.chipTint())
    }
    return hazeEffect(state = hazeState, style = style)
}
