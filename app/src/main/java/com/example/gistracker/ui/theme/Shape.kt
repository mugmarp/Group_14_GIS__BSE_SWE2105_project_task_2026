package com.example.gistracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// GeoTrack Safe Telemetry Controlled Curvature Profile (Precision Geometry)
val GeoTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),   // 0.125rem - checkboxes, hairline badges
    small = RoundedCornerShape(4.dp),        // 0.25rem - buttons, tool inputs, tabular tags
    medium = RoundedCornerShape(6.dp),       // 0.375rem - action chips, popup anchors
    large = RoundedCornerShape(8.dp),        // 0.5rem - telemetry cards, spatial inspectors, docks
    extraLarge = RoundedCornerShape(12.dp)    // 0.75rem - dialog containers, major bottom sheets
)
