package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.ColorPreset
import com.example.data.ThemeMode

val LocalDuoPalette = staticCompositionLocalOf {
    buildDuoPalette(ColorPreset.AZURE_ROSE, isDark = false)
}

private val DuoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DuoLudoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorPreset: ColorPreset = ColorPreset.AZURE_ROSE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val palette = buildDuoPalette(colorPreset, isDark)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.players[0].main,
            onPrimary = Color(0xFF0A1633),
            primaryContainer = palette.players[0].soft,
            onPrimaryContainer = palette.players[0].light,
            secondary = palette.players[1].main,
            onSecondary = Color(0xFF330A1B),
            secondaryContainer = palette.players[1].soft,
            onSecondaryContainer = palette.players[1].light,
            tertiary = palette.bolt,
            background = palette.surfaceBgTop,
            onBackground = palette.ink,
            surface = palette.cardBg,
            onSurface = palette.ink,
            surfaceVariant = palette.segBg,
            onSurfaceVariant = palette.muted,
            outline = palette.hairline,
            outlineVariant = palette.hairline
        )
    } else {
        lightColorScheme(
            primary = palette.players[0].deep,
            onPrimary = Color.White,
            primaryContainer = palette.players[0].soft,
            onPrimaryContainer = palette.players[0].ink,
            secondary = palette.players[1].deep,
            onSecondary = Color.White,
            secondaryContainer = palette.players[1].soft,
            onSecondaryContainer = palette.players[1].ink,
            tertiary = palette.bolt,
            background = palette.surfaceBgTop,
            onBackground = palette.ink,
            surface = Color.White,
            onSurface = palette.ink,
            surfaceVariant = palette.segBg,
            onSurfaceVariant = palette.muted,
            outline = palette.hairline,
            outlineVariant = palette.hairline
        )
    }

    CompositionLocalProvider(LocalDuoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DuoTypography,
            shapes = DuoShapes,
            content = content
        )
    }
}
