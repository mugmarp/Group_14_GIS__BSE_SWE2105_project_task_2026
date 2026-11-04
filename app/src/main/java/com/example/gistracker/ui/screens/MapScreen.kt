package com.example.gistracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gistracker.model.TrackingMode
import com.example.gistracker.ui.components.MapViewComposable
import com.example.gistracker.ui.theme.LocalCustomColors
import com.example.gistracker.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val currentLocation by viewModel.currentLocation.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val trailPoints by viewModel.activeTrailPoints.collectAsState()
    val trackingMode by viewModel.trackingMode.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }
    var sessionNameInput by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        // Persistent Full-Bleed Spatial Viewport
        MapViewComposable(
            currentLocation = currentLocation,
            trailPoints = trailPoints
        )

        // Top Telemetry HUD Card (Level 1 Elevation - Avionic HUD)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .testTag("telemetry_hud_card")
            ) {
                Column {
                    // Top Accent Line (Telemetry Tier Indicator)
                    val accentLineColor = when {
                        activeSession != null -> colorScheme.error
                        currentLocation != null -> colorScheme.tertiary
                        else -> customColors.warning
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(accentLineColor)
                    )

                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status Pill
                            val (badgeBg, badgeText, badgeBorder, statusLabel) = when {
                                activeSession != null -> Quad(
                                    Color(0xFFFFDAD6),
                                    Color(0xFF93000A),
                                    Color(0xFFFFB4AB),
                                    "RECORDING TRAIL"
                                )
                                currentLocation != null -> Quad(
                                    customColors.statusNominalBg,
                                    customColors.statusNominalText,
                                    customColors.statusNominalBorder,
                                    "GNSS LOCKED (RTK)"
                                )
                                else -> Quad(
                                    customColors.statusWarningBg,
                                    customColors.statusWarningText,
                                    customColors.statusWarningBorder,
                                    "AWAITING FIX"
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = badgeBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(badgeText)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.04.sp,
                                        color = badgeText
                                    )
                                }
                            }

                            // Coordinate Vector Readout (Tabular Monospace)
                            Text(
                                text = if (currentLocation != null) {
                                    "${String.format("%.5f", currentLocation?.latitude)}°, ${String.format("%.5f", currentLocation?.longitude)}°"
                                } else {
                                    "--.-----°, --.-----°"
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4 Tabular Telemetry Scalar Metrics
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(customColors.surfaceContainerLow, RoundedCornerShape(4.dp))
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TelemetryMetric(
                                label = "SPEED",
                                value = "${String.format("%.1f", currentLocation?.speedKmh ?: 0f)}",
                                unit = "km/h",
                                valueColor = colorScheme.primary
                            )
                            Box(modifier = Modifier.width(1.dp).height(28.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                            TelemetryMetric(
                                label = "DISTANCE",
                                value = activeSession?.formattedDistance ?: "0 m",
                                unit = "",
                                valueColor = colorScheme.secondary
                            )
                            Box(modifier = Modifier.width(1.dp).height(28.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                            TelemetryMetric(
                                label = "ACCURACY",
                                value = if (currentLocation != null) "±${currentLocation?.accuracy?.toInt()}m" else "--",
                                unit = "",
                                valueColor = if ((currentLocation?.accuracy ?: 99f) <= 10f) colorScheme.tertiary else customColors.warning
                            )
                            Box(modifier = Modifier.width(1.dp).height(28.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                            TelemetryMetric(
                                label = "WAYPOINTS",
                                value = "${trailPoints.size}",
                                unit = "pts",
                                valueColor = colorScheme.onSurface
                            )
                        }

                        if (activeSession != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⏱️ ELAPSED: ${activeSession?.formattedDuration}",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${activeSession?.name}",
                                    fontSize = 11.sp,
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Docked Controls Sheet (Level 1 Elevation - Tactical Controls)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
                .testTag("bottom_action_card"),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surface,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Tracking Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SENSOR PROFILE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = trackingMode == TrackingMode.HIGH_ACCURACY,
                            onClick = { viewModel.setTrackingMode(TrackingMode.HIGH_ACCURACY) },
                            label = { Text("GPS (1s)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary,
                                selectedLabelColor = colorScheme.onPrimary,
                                containerColor = customColors.surfaceContainerLow,
                                labelColor = colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = trackingMode == TrackingMode.HIGH_ACCURACY,
                                borderColor = colorScheme.outlineVariant,
                                selectedBorderColor = colorScheme.primary
                            ),
                            modifier = Modifier.testTag("mode_high_acc")
                        )
                        FilterChip(
                            selected = trackingMode == TrackingMode.BALANCED,
                            onClick = { viewModel.setTrackingMode(TrackingMode.BALANCED) },
                            label = { Text("Balanced (10s)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary,
                                selectedLabelColor = colorScheme.onPrimary,
                                containerColor = customColors.surfaceContainerLow,
                                labelColor = colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = trackingMode == TrackingMode.BALANCED,
                                borderColor = colorScheme.outlineVariant,
                                selectedBorderColor = colorScheme.primary
                            ),
                            modifier = Modifier.testTag("mode_balanced")
                        )
                        FilterChip(
                            selected = trackingMode == TrackingMode.POWER_SAVER,
                            onClick = { viewModel.setTrackingMode(TrackingMode.POWER_SAVER) },
                            label = { Text("Cell Only", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary,
                                selectedLabelColor = colorScheme.onPrimary,
                                containerColor = customColors.surfaceContainerLow,
                                labelColor = colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = trackingMode == TrackingMode.POWER_SAVER,
                                borderColor = colorScheme.outlineVariant,
                                selectedBorderColor = colorScheme.primary
                            ),
                            modifier = Modifier.testTag("mode_power_save")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Start / Stop Recording Button
                if (activeSession == null) {
                    Button(
                        onClick = {
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            sessionNameInput = "Field Trail $timeStr"
                            showStartDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("start_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "START RECORDING REAL TRAIL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopTracking() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("stop_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "STOP & SAVE TRAIL (${activeSession?.formattedDuration})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp
                        )
                    }
                }
            }
        }
    }

    // Start Session Dialog (Level 3 Elevation)
    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = {
                Text(
                    "Start Real GPS Trail",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Enter a name for this field tracking session. Real coordinates from FusedLocationProviderClient will be persisted to SQLite.",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { sessionNameInput = it },
                        label = { Text("Session Identifier", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = sessionNameInput.ifBlank { "Field Trail" }
                        viewModel.startTracking(name, trackingMode)
                        showStartDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("confirm_start_btn")
                ) {
                    Text("Start Recording", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartDialog = false },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = colorScheme.surface
        )
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    value: String,
    unit: String,
    valueColor: Color
) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontFamily = FontFamily.Monospace
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
