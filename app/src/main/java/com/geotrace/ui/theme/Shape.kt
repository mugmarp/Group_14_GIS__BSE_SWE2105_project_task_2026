package com.geotrace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// GeoTrack Safe Telemetry Modern Soft-Precision Curvature Hierarchy
val GeoTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),  // Tags & compact indicators
    small = RoundedCornerShape(14.dp),       // Buttons, filter chips, input fields
    medium = RoundedCornerShape(16.dp),      // Map viewport controls, cards & interactive rows
    large = RoundedCornerShape(22.dp),       // Telemetry cards, HUD containers, docks
    extraLarge = RoundedCornerShape(28.dp)   // Bottom sheets, modal dialogs
)
