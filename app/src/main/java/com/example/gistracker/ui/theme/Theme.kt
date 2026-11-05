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

// GeoTrack Safe Telemetry Official Light Color Scheme
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
    surface = SurfaceContainerLowest, // #FFFFFF Pure White Elevation 1
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant, // #DAE2FD
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
    primaryContainer = Primary,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = Primary,
    secondary = Color(0xFFB4C5FF),
    onSecondary = Color(0xFF002773),
    secondaryContainer = Secondary,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Color(0xFF68DBA9),
    onTertiary = Color(0xFF003823),
    tertiaryContainer = Tertiary,
    onTertiaryContainer = OnTertiaryContainer,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E131E),
    onBackground = Color(0xFFE2E7FF),
    surface = Color(0xFF131B2E),
    onSurface = Color(0xFFE2E7FF),
    surfaceVariant = Color(0xFF283044),
    onSurfaceVariant = Color(0xFFC0C7D5),
    outline = Color(0xFF8A92A0),
    outlineVariant = Color(0xFF3F4850),
    surfaceTint = Color(0xFF93CCFF),
    inverseSurface = Color(0xFFE2E7FF),
    inverseOnSurface = Color(0xFF131B2E)
)

data class CustomThemeColors(
    val warning: Color,
    val onWarning: Color,
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

val LocalCustomColors = staticCompositionLocalOf {
    CustomThemeColors(
        warning = Warning,
        onWarning = OnWarning,
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
}

val ColorScheme.warningColor: Color
    @Composable
    @ReadOnlyComposable
    get() = Warning

@Composable
fun GISTrackerTheme(
    darkTheme: Boolean = false, // Strictly light theme only for daylight field GIS operations
    content: @Composable () -> Unit
) {
    // Enforce GeoTrack Safe Telemetry Light Color Scheme exclusively
    val colorScheme = GeoTrackLightColorScheme
    val customColors = CustomThemeColors(
        warning = Warning,
        onWarning = OnWarning,
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
