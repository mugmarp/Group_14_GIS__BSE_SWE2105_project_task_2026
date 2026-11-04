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
        // Real OpenStreetMap
        MapViewComposable(
            currentLocation = currentLocation,
            trailPoints = trailPoints
        )

        // Top Telemetry HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .testTag("telemetry_hud_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            activeSession != null -> colorScheme.error
                                            currentLocation != null -> colorScheme.tertiary
                                            else -> customColors.warning
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    activeSession != null -> "RECORDING TRAIL"
                                    currentLocation != null -> "GPS LOCKED"
                                    else -> "SEARCHING SATELLITES"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    activeSession != null -> colorScheme.error
                                    currentLocation != null -> colorScheme.tertiary
                                    else -> customColors.warning
                                }
                            )
                        }

                        // Live Coordinates badge inside header (eliminates bottom overlapping)
                        Text(
                            text = if (currentLocation != null) {
                                "${String.format("%.4f", currentLocation?.latitude)}°, ${String.format("%.4f", currentLocation?.longitude)}°"
                            } else {
                                "No Fix"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4 Telemetry Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryItem(
                            label = "SPEED",
                            value = "${String.format("%.1f", currentLocation?.speedKmh ?: 0f)}",
                            unit = "km/h",
                            accentColor = colorScheme.primary
                        )
                        TelemetryItem(
                            label = "DISTANCE",
                            value = activeSession?.formattedDistance ?: "0 m",
                            unit = "",
                            accentColor = colorScheme.secondary
                        )
                        TelemetryItem(
                            label = "ACCURACY",
                            value = if (currentLocation != null) "±${currentLocation?.accuracy?.toInt()}m" else "--",
                            unit = "",
                            accentColor = if ((currentLocation?.accuracy ?: 99f) <= 10f) colorScheme.tertiary else customColors.warning
                        )
                        TelemetryItem(
                            label = "WAYPOINTS",
                            value = "${trailPoints.size}",
                            unit = "pts",
                            accentColor = colorScheme.primary
                        )
                    }

                    if (activeSession != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⏱️ Active: ${activeSession?.formattedDuration}",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${activeSession?.name}",
                                fontSize = 11.sp,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls Bar (Recording & GPS Modes)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
                .testTag("bottom_action_card"),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Tracking Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Positioning Profile:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = trackingMode == TrackingMode.HIGH_ACCURACY,
                            onClick = { viewModel.setTrackingMode(TrackingMode.HIGH_ACCURACY) },
                            label = { Text("GPS (1s)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary,
                                selectedLabelColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("mode_high_acc")
                        )
                        FilterChip(
                            selected = trackingMode == TrackingMode.BALANCED,
                            onClick = { viewModel.setTrackingMode(TrackingMode.BALANCED) },
                            label = { Text("Balanced (10s)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary,
                                selectedLabelColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("mode_balanced")
                        )
                        FilterChip(
                            selected = trackingMode == TrackingMode.POWER_SAVER,
                            onClick = { viewModel.setTrackingMode(TrackingMode.POWER_SAVER) },
                            label = { Text("Cell Only", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary,
                                selectedLabelColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("mode_power_save")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Start / Stop Button
                if (activeSession == null) {
                    Button(
                        onClick = {
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            sessionNameInput = "Field Trail $timeStr"
                            showStartDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Recording Real Trail", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopTracking() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("stop_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop & Save Trail (${activeSession?.formattedDuration})", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Start Session Dialog
    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = {
                Text("Start Real GPS Trail", fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            },
            text = {
                Column {
                    Text(
                        "Enter a name for this tracking session to log your live coordinates to local SQLite storage:",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { sessionNameInput = it },
                        label = { Text("Session Name") },
                        singleLine = true,
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
                    modifier = Modifier.testTag("confirm_start_btn")
                ) {
                    Text("Start Recording", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDialog = false }) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant)
                }
            },
            containerColor = colorScheme.surface
        )
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    unit: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
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
