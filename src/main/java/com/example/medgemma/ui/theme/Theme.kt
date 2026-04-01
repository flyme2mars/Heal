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

// Premium Monochromatic Palette with subtle Red
private val BrandRed = Color(0xFFFF3131) // Pure, vibrant red
private val PureBlack = Color(0xFF000000)
private val DarkGrey = Color(0xFF121212) // Slightly lighter for subtle elevation
private val MidGrey = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFF5F5F7) // Off-white for readability
private val TextSecondary = Color(0xFFA1A1A6) // iOS-style muted text

private val DarkColorScheme = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2C0000),
    onPrimaryContainer = BrandRed,
    secondary = TextSecondary,
    onSecondary = PureBlack,
    background = PureBlack,
    surface = PureBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = DarkGrey,
    onSurfaceVariant = TextSecondary,
    secondaryContainer = MidGrey,
    onSecondaryContainer = TextPrimary,
    outline = Color(0xFF333333)
)

private val LightColorScheme = DarkColorScheme // Forcing dark mode for premium look

@Composable
fun MedGemmaTheme(
    darkTheme: Boolean = true, // Force dark for premium aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PureBlack.toArgb()
            window.navigationBarColor = PureBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
