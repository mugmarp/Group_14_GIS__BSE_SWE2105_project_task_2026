package com.geotrace.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// GeoTrace Safe Telemetry Official Light Color Scheme
val GeoTrackLightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceTint = SurfaceTint,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface
)

// High-Acuity Night Operation Palette (Avionics Dark Mode)
val GeoTrackDarkColorScheme = darkColorScheme(
    primary = InversePrimary,
    onPrimary = Color(0xFF003452),
    primaryContainer = Color(0xFF004B73),
    onPrimaryContainer = Color(0xFFCCE5FF),
    inversePrimary = Primary,
    secondary = Color(0xFFB4C5FF),
    onSecondary = Color(0xFF002773),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBE1FF),
    tertiary = Color(0xFF68DBA9),
    onTertiary = Color(0xFF003823),
    tertiaryContainer = Color(0xFF005137),
    onTertiaryContainer = Color(0xFF85F8C4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1320),
    onBackground = Color(0xFFE5EBFA),
    surface = Color(0xFF141C2E),
    onSurface = Color(0xFFE5EBFA),
    surfaceVariant = Color(0xFF263148),
    onSurfaceVariant = Color(0xFFB9C4D6),
    outline = Color(0xFF7D899E),
    outlineVariant = Color(0xFF334158),
    surfaceTint = Color(0xFF93CCFF),
    inverseSurface = Color(0xFFE5EBFA),
    inverseOnSurface = Color(0xFF141C2E)
)

data class CustomThemeColors(
    val warning: Color,
    val onWarning: Color,
    val fleetAccent: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val statusNominalBg: Color,
    val statusNominalText: Color,
    val statusNominalBorder: Color,
    val statusActiveBg: Color,
    val statusActiveText: Color,
    val statusActiveBorder: Color,
    val statusWarningBg: Color,
    val statusWarningText: Color,
    val statusWarningBorder: Color
)

private val LightCustomThemeColors = CustomThemeColors(
    warning = Warning,
    onWarning = OnWarning,
    fleetAccent = Color(0xFF6D28D9),
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    statusNominalBg = StatusNominalBg,
    statusNominalText = StatusNominalText,
    statusNominalBorder = StatusNominalBorder,
    statusActiveBg = StatusActiveBg,
    statusActiveText = StatusActiveText,
    statusActiveBorder = StatusActiveBorder,
    statusWarningBg = StatusWarningBg,
    statusWarningText = StatusWarningText,
    statusWarningBorder = StatusWarningBorder
)

private val DarkCustomThemeColors = CustomThemeColors(
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF451A03),
    fleetAccent = Color(0xFFC4B5FD),
    surfaceContainerLowest = Color(0xFF0F1625),
    surfaceContainerLow = Color(0xFF1B2438),
    surfaceContainer = Color(0xFF222C44),
    surfaceContainerHigh = Color(0xFF2A3652),
    surfaceContainerHighest = Color(0xFF344263),
    statusNominalBg = Color(0xFF063829),
    statusNominalText = Color(0xFF6EE7B7),
    statusNominalBorder = Color(0xFF047857),
    statusActiveBg = Color(0xFF0C2D48),
    statusActiveText = Color(0xFF7DD3FC),
    statusActiveBorder = Color(0xFF0369A1),
    statusWarningBg = Color(0xFF3E2607),
    statusWarningText = Color(0xFFFCD34D),
    statusWarningBorder = Color(0xFFB45309)
)

val LocalCustomColors = staticCompositionLocalOf { LightCustomThemeColors }

val ColorScheme.warningColor: Color
    @Composable
    @ReadOnlyComposable
    get() = Warning

@Composable
fun GISTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GeoTrackDarkColorScheme else GeoTrackLightColorScheme
    val customColors = if (darkTheme) DarkCustomThemeColors else LightCustomThemeColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GeoTrackTypography,
            shapes = GeoTrackShapes,
            content = content
        )
    }
}
