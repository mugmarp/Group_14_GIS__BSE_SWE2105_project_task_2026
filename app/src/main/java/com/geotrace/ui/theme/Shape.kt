package com.geotrace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// GeoTrack Safe Telemetry Modern Soft-Precision Curvature Hierarchy
val GeoTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // Hairline tags & badges
    small = RoundedCornerShape(10.dp),       // Buttons, filter chips, input fields
    medium = RoundedCornerShape(12.dp),      // Map viewport controls & FABs (Strictly preserved at 12dp)
    large = RoundedCornerShape(22.dp),       // Telemetry cards, HUD containers, docks
    extraLarge = RoundedCornerShape(28.dp)   // Bottom sheets, modal dialogs
)
