package com.example.gistracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.gistracker.ui.theme.CardSurface
import com.example.gistracker.ui.theme.DangerRed
import com.example.gistracker.ui.theme.DarkNavy
import com.example.gistracker.ui.theme.ElectricCyan
import com.example.gistracker.ui.theme.SafetyAmber
import com.example.gistracker.ui.theme.SuccessGreen
import com.example.gistracker.viewmodel.TrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLocation by viewModel.currentLocation.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val trailPoints by viewModel.activeTrailPoints.collectAsState()
    val geofences by viewModel.geofenceZones.collectAsState()
    val cellTowers by viewModel.cellTowers.collectAsState()
    val trackingMode by viewModel.trackingMode.collectAsState()
    val isSimulating by viewModel.isSimulatingMovement.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }
    var sessionNameInput by remember { mutableStateOf("Boda Transit Track") }

    Box(modifier = modifier.fillMaxSize()) {
        // Interactive Map
        MapViewComposable(
            currentLocation = currentLocation,
            trailPoints = trailPoints,
            geofences = geofences,
            cellTowers = cellTowers
        )

        // Top Telemetry HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Live Status Header Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkNavy.copy(alpha = 0.90f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF273552), RoundedCornerShape(16.dp))
                    .testTag("telemetry_hud_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                    .background(if (activeSession != null) DangerRed else SuccessGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeSession != null) "RECORDING: ${activeSession?.name}" else "READY (GPS STANDBY)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeSession != null) DangerRed else SuccessGreen
                            )
                        }

                        // Simulation toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Simulate",
                                fontSize = 11.sp,
                                color = if (isSimulating) SafetyAmber else Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = isSimulating,
                                onCheckedChange = { viewModel.toggleSimulation(it) },
                                modifier = Modifier.testTag("simulate_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SafetyAmber,
                                    checkedTrackColor = SafetyAmber.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4 Telemetry Metrics (Speed, Distance, Altitude, Accuracy)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryItem(
                            label = "SPEED",
                            value = "${String.format("%.1f", currentLocation?.speedKmh ?: 0f)}",
                            unit = "km/h",
                            accentColor = ElectricCyan
                        )
                        TelemetryItem(
                            label = "DISTANCE",
                            value = activeSession?.formattedDistance ?: "0 m",
                            unit = "",
                            accentColor = SafetyAmber
                        )
                        TelemetryItem(
                            label = "ALTITUDE",
                            value = "${String.format("%.0f", currentLocation?.altitude ?: 1190.0)}",
                            unit = "m",
                            accentColor = Color(0xFFB388FF)
                        )
                        TelemetryItem(
                            label = "ACCURACY",
                            value = "±${String.format("%.1f", currentLocation?.accuracy ?: 3.5f)}",
                            unit = "m",
                            accentColor = SuccessGreen
                        )
                    }

                    if (activeSession != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⏱️ Duration: ${activeSession?.formattedDuration} | Mode: ${activeSession?.trackingMode?.name}",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "📍 ${trailPoints.size} waypoints",
                                fontSize = 11.sp,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSimulating) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SafetyAmber.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = DarkNavy, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Simulation Mode Active: Generating realistic Kampala road telemetry",
                            color = DarkNavy,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Controls Bar (Recording & Modes & SOS)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("bottom_action_card"),
            shape = RoundedCornerShape(20.dp),
            color = DarkNavy.copy(alpha = 0.92f),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF273552))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Tracking Mode Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackingMode.values().forEach { mode ->
                        val selected = trackingMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setTrackingMode(mode) },
                            label = {
                                Text(
                                    text = when (mode) {
                                        TrackingMode.HIGH_ACCURACY -> "GPS (1s)"
                                        TrackingMode.BALANCED -> "Balanced (10s)"
                                        TrackingMode.POWER_SAVER -> "Cell Only"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = DarkNavy,
                                containerColor = CardSurface,
                                labelColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("chip_${mode.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start/Stop Primary Button
                    if (activeSession == null) {
                        Button(
                            onClick = { showStartDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_tracking_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkNavy)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Recording", color = DarkNavy, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopTracking() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_tracking_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop & Save Trail", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Emergency SOS Share Button
                    Button(
                        onClick = { viewModel.shareEmergencyAlert(context) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("emergency_sos_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Emergency Share", tint = DangerRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SOS Share", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Start Session Dialog
    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = { Text("Start New Location Trail", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Give this tracking session a label (e.g. Boda trip, child school route, vehicle transit):",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { sessionNameInput = it },
                        label = { Text("Session Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tracking Mode: ${trackingMode.title}",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startTracking(sessionNameInput.ifBlank { "Track ${System.currentTimeMillis() % 1000}" }, trackingMode)
                        showStartDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.testTag("confirm_start_btn")
                ) {
                    Text("Start", color = DarkNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = CardSurface
        )
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    unit: String,
    accentColor: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = unit, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}
