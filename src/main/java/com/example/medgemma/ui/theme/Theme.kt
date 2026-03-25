package com.example.medgemma.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Your custom palette
private val BrandRed = Color(0xFFFF3B3B)
private val DeepCharcoal = Color(0xFF11131A)
private val SurfaceGrey = Color(0xFF1C1F26) // Slightly lighter for card depth

private val DarkColorScheme = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8B0000),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF2D3139),
    onSecondary = Color.White,
    background = DeepCharcoal,
    surface = DeepCharcoal,
    onBackground = Color(0xFFE1E3E0),
    onSurface = Color(0xFFE1E3E0),
    secondaryContainer = SurfaceGrey,
    onSecondaryContainer = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775651),
    onSecondary = Color.White,
    background = Color.White,
    surface = Color.White,
    onBackground = DeepCharcoal,
    onSurface = DeepCharcoal,
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = DeepCharcoal
)

@Composable
fun MedGemmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled by default to force your custom Red/Charcoal theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
