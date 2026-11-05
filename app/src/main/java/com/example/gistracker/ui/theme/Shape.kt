package com.example.gistracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// GeoTrack Safe Telemetry Modern Soft-Precision Curvature Hierarchy
val GeoTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // Hairline tags & badges
    small = RoundedCornerShape(8.dp),        // Buttons, filter chips, input fields
    medium = RoundedCornerShape(12.dp),      // Floating action buttons, cluster controls
    large = RoundedCornerShape(16.dp),       // Telemetry cards, HUD containers, docks
    extraLarge = RoundedCornerShape(20.dp)   // Bottom sheets, modal dialogs
)
