package com.example.medgemma.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.frostedGlassBar(hazeState: HazeState): Modifier {
    val style = HazeMaterials.thin(MaterialTheme.colorScheme.surface)
    return hazeEffect(state = hazeState, style = style)
}