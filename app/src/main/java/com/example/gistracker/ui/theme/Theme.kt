package com.example.gistracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = Color(0xFF004F4D),
    onPrimaryContainer = Color(0xFF6FF7ED),
    secondary = SecondaryDark,
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF0D47A1),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = TertiaryDark,
    onTertiary = Color(0xFF003913),
    tertiaryContainer = Color(0xFF00531E),
    onTertiaryContainer = Color(0xFFA5F29A),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF243046),
    onSurfaceVariant = Color(0xFFC4C7C5),
    outline = OutlineDark,
    outlineVariant = Color(0xFF2E384D),
    error = ErrorDark,
    onError = Color(0xFF690005)
)

val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = Color(0xFFA0F0EA),
    onPrimaryContainer = Color(0xFF002020),
    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E4FF),
    onSecondaryContainer = Color(0xFF001D36),
    tertiary = TertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA5F29A),
    onTertiaryContainer = Color(0xFF002207),
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF44474E),
    outline = OutlineLight,
    outlineVariant = Color(0xFFE0E2E0),
    error = ErrorLight,
    onError = Color.White
)

data class CustomThemeColors(
    val warning: Color,
    val onWarning: Color
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomThemeColors(
        warning = WarningDark,
        onWarning = Color(0xFF2A1C00)
    )
}

val ColorScheme.warningColor: Color
    @Composable
    @ReadOnlyComposable
    get() = if (this == DarkColorScheme) WarningDark else WarningLight

@Composable
fun GISTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) {
        CustomThemeColors(warning = WarningDark, onWarning = Color(0xFF2A1C00))
    } else {
        CustomThemeColors(warning = WarningLight, onWarning = Color(0xFF1F1500))
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
