package com.geotrace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// GeoTrack Safe Telemetry Modern Soft-Precision Curvature Hierarchy
val GeoTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),  // Compact indicators & scale badges
    small = RoundedCornerShape(16.dp),       // Input fields, recessed metric wells
    medium = RoundedCornerShape(20.dp),      // Nested & secondary interactive cards
    large = RoundedCornerShape(24.dp),       // Primary cards, HUD containers, docks
    extraLarge = RoundedCornerShape(28.dp)   // Bottom sheets, modal dialogs
)
