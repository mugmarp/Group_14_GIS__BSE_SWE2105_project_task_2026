package com.geotrace.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.MapTileType
import com.geotrace.model.TrackingMode
import com.geotrace.ui.components.MapViewComposable
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: TrackerViewModel,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val currentLocation by viewModel.currentLocation.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val trailPoints by viewModel.activeTrailPoints.collectAsState()
    val trackingMode by viewModel.trackingMode.collectAsState()
    val geofenceZones by viewModel.geofenceZones.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cellTowerLocation by viewModel.cellTowerLocation.collectAsState()
    val allCellTowers by viewModel.allCellTowerLocations.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var sessionNameInput by remember { mutableStateOf("") }
    var isHeaderExpanded by remember { mutableStateOf(true) }


    // Keep screen awake while recording if enabled in settings
    val activity = context as? Activity
    DisposableEffect(settings.keepScreenOn, activeSession) {
        if (settings.keepScreenOn && activeSession != null) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Persistent Full-Bleed Spatial Viewport with Google Maps-style Integrated Bottom Layer Selection & Geofence Perimeters
        MapViewComposable(
            currentLocation = currentLocation,
            trailPoints = trailPoints,
            geofenceZones = geofenceZones,
            cellTowerLocation = cellTowerLocation,
            allCellTowers = allCellTowers,
            settings = settings,
            onSelectTileLayer = { selectedTile ->
                viewModel.updateMapTileType(selectedTile)
            },
            onToggleCellTowerPin = { enabled ->
                viewModel.toggleCellTowerPin(enabled)
            }
        )

        // Top Instrument & Telemetry HUD Cluster
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(22.dp))
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
                            .height(3.dp)
                            .background(accentLineColor)
                    )

                    Column(modifier = Modifier.padding(14.dp)) {
                        // Title Bar with Right-Aligned ☰ Hamburger Menu
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accentLineColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "GeoTrace Safe Telemetry",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = when {
                                             activeSession != null -> "RECORDING • ${activeSession?.name}"
                                             trackingMode == TrackingMode.POWER_SAVER -> "CELLULAR ONLY • SECTOR TELEMETRY (MTN/AIRTEL)"
                                             currentLocation?.provider == "cellular" -> "CELLULAR FIX • ACCURACY ±${currentLocation?.accuracy?.toInt()}M"
                                             currentLocation != null -> "GNSS LOCKED • ACCURACY ±${currentLocation?.accuracy?.toInt()}M"
                                             else -> "STANDBY • AWAITING GNSS SATELLITE FIX"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.04.sp,
                                        color = if (trackingMode == TrackingMode.POWER_SAVER) Color(0xFFD97706) else accentLineColor
                                    )
                                }
                            }

                            // ☰ Hamburger Menu on the RIGHT
                            IconButton(
                                onClick = onOpenMenu,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(customColors.surfaceContainerLow, RoundedCornerShape(12.dp))
                                    .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .testTag("menu_btn")
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open Application Menu",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Animated Collapsible Body
                        AnimatedVisibility(
                            visible = isHeaderExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))

                                // Vector Coordinates Bar (Respects CoordinateFormat: DD vs DMS)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "VECTOR COORD:",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.04.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (currentLocation != null) {
                                            formatCoordinates(currentLocation!!.latitude, currentLocation!!.longitude, settings.coordinateFormat)
                                        } else {
                                            "--.-----°, --.-----°"
                                        },
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 4 Tabular Telemetry Scalar Metrics with Responsive Weighted Alignment
                                val convertedSpeed = (currentLocation?.speed ?: 0f) * settings.speedUnit.multiplier
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        TelemetryMetric(
                                            label = "SPEED",
                                            value = String.format(Locale.US, "%.1f", convertedSpeed),
                                            unit = settings.speedUnit.label,
                                            valueColor = colorScheme.primary,
                                            subtitle = currentLocation?.gaitLabel ?: "Stationary"
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        val pace = activeSession?.avgPaceMinPerKm?.takeIf { it != "--:--" }
                                            ?: currentLocation?.paceMinPerKm?.takeIf { it != "--:--" }
                                        TelemetryMetric(
                                            label = "DISTANCE",
                                            value = activeSession?.formattedDistance ?: "0 m",
                                            unit = "",
                                            valueColor = colorScheme.secondary,
                                            subtitle = pace
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        TelemetryMetric(
                                            label = "ACCURACY",
                                            value = if (currentLocation != null) "±${currentLocation?.accuracy?.toInt()}m" else "--",
                                            unit = "",
                                            valueColor = if ((currentLocation?.accuracy ?: 99f) <= 10f) colorScheme.tertiary else customColors.warning,
                                            subtitle = currentLocation?.provider?.uppercase()
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        TelemetryMetric(
                                            label = "WAYPOINTS",
                                            value = "${trailPoints.size}",
                                            unit = "pts",
                                            valueColor = colorScheme.onSurface,
                                            subtitle = if (activeSession?.isRecording == true) "Recording" else "Standby"
                                        )
                                    }
                                }

                                if (activeSession != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "⏱️ ACTIVE SESSION: ${activeSession?.formattedDuration}",
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

                        // Centered Sleek Collapsible Pull-Tab Handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (isHeaderExpanded) 8.dp else 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = customColors.surfaceContainerLow,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clickable { isHeaderExpanded = !isHeaderExpanded }
                                    .testTag("toggle_header_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isHeaderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isHeaderExpanded) "Collapse Telemetry HUD" else "Expand Telemetry HUD",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isHeaderExpanded) "HIDE HUD" else "SHOW HUD",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.06.sp,
                                        color = colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Floating Badges Row (Horizontally scrollable for small screens)
            val badgesScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(badgesScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floating Positioning Profile Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .clickable { showProfileDialog = true }
                        .testTag("floating_profile_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val profile = getProfileInfo(trackingMode, colorScheme)
                        Icon(profile.icon, contentDescription = null, tint = profile.badgeColor, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = profile.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = profile.badgeColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = profile.power,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = profile.badgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                // Live Geofence Zone Proximity Chip (Active Perimeter Alert)
                val activeZone = geofenceZones.firstOrNull { it.isInside }
                if (activeZone != null) {
                    val isHotspot = activeZone.category == com.geotrace.model.GeofenceCategory.BODA_HOTSPOT
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isHotspot) customColors.warning.copy(alpha = 0.14f) else colorScheme.tertiary.copy(alpha = 0.14f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, if (isHotspot) customColors.warning else colorScheme.tertiary),
                        modifier = Modifier.testTag("active_geofence_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isHotspot) customColors.warning else colorScheme.tertiary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHotspot) "⚠️ ${activeZone.name}" else "🛡️ ${activeZone.name}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHotspot) customColors.warning else colorScheme.tertiary
                            )
                        }
                    }
                }

                // Cell Tower Pin Status Chip
                if (settings.showCellTowerPin && cellTowerLocation != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFD97706).copy(alpha = 0.12f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable {
                                viewModel.toggleCellTowerPin(!settings.showCellTowerPin)
                            }
                            .testTag("cell_tower_status_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "📡 Tower: ±${cellTowerLocation?.rangeMeters?.toInt()}m",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }
            }
        }

        // Floating Recording Action Pill (Centered, Compact, Clean Spatial Viewport)
        if (activeSession == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .testTag("floating_recording_pill"),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surface,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (currentLocation != null) colorScheme.tertiary else customColors.warning)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentLocation != null) "Ready" else "Standby",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            sessionNameInput = "Field Trail $timeStr"
                            showStartDialog = true
                        },
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("start_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(19.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "START RECORDING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.03.sp
                        )
                    }
                }
            }
        } else {
            // Floating Recording Action Pill (Active Recording State)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .testTag("floating_recording_pill"),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.5.dp, colorScheme.error.copy(alpha = 0.7f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "REC • ${activeSession?.formattedDuration}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.error
                        )
                        Text(
                            text = activeSession?.formattedDistance ?: "0 m",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.stopTracking() },
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("stop_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError
                        ),
                        shape = RoundedCornerShape(19.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "STOP & SAVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Positioning Engine & Battery Profile Modal Dialog
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text(
                    text = "Positioning Engine & Battery Profile",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                val dialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select positioning telemetry engine based on mission requirement and battery budget:",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    ProfileOptionCard(
                        title = "GPS Real-Time (1 sec)",
                        subtitle = "High-Accuracy GNSS satellites",
                        accuracy = "±2 – 5m",
                        power = "~25 mA",
                        description = "Continuous GNSS chipset lock. Essential for velocity vector analysis and official trail logging.",
                        isSelected = trackingMode == TrackingMode.HIGH_ACCURACY,
                        color = colorScheme.primary,
                        onClick = {
                            viewModel.setTrackingMode(TrackingMode.HIGH_ACCURACY)
                            showProfileDialog = false
                        }
                    )

                    ProfileOptionCard(
                        title = "Balanced Fused (5 sec)",
                        subtitle = "Hybrid GNSS + Network duty cycling (Testing default)",
                        accuracy = "±5 – 15m",
                        power = "~8 mA",
                        description = "Cycles positioning receiver every 5s. Ideal for testing and conserving battery while logging full trails.",
                        isSelected = trackingMode == TrackingMode.BALANCED,
                        color = colorScheme.secondary,
                        onClick = {
                            viewModel.setTrackingMode(TrackingMode.BALANCED)
                            showProfileDialog = false
                        }
                    )

                    ProfileOptionCard(
                        title = "Cellular Only (RF Masts)",
                        subtitle = "Zero GNSS hardware activation",
                        accuracy = "±200 – 800m",
                        power = "<2 mA",
                        description = "TelephonyManager base station sectoring. Zero satellite drain. Works indoors & in tunnels.",
                        isSelected = trackingMode == TrackingMode.POWER_SAVER,
                        color = colorScheme.tertiary,
                        onClick = {
                            viewModel.setTrackingMode(TrackingMode.POWER_SAVER)
                            showProfileDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showProfileDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface
        )
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
                        shape = RoundedCornerShape(8.dp),
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_start_btn")
                ) {
                    Text("Start Recording", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colorScheme.surface
        )
    }
}

private data class ProfileInfo(
    val icon: ImageVector,
    val label: String,
    val power: String,
    val badgeColor: Color
)

private fun getProfileInfo(mode: TrackingMode, colorScheme: androidx.compose.material3.ColorScheme): ProfileInfo {
    return when (mode) {
        TrackingMode.HIGH_ACCURACY -> ProfileInfo(Icons.Default.Bolt, "GPS Real-Time (1s)", "~25mA", colorScheme.primary)
        TrackingMode.BALANCED -> ProfileInfo(Icons.Default.Balance, "Balanced (10s)", "~8mA", colorScheme.secondary)
        TrackingMode.POWER_SAVER -> ProfileInfo(Icons.Default.CellTower, "Cell Only", "<2mA", colorScheme.tertiary)
    }
}

@Composable
private fun ProfileOptionCard(
    title: String,
    subtitle: String,
    accuracy: String,
    power: String,
    description: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.08f) else colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) color else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = power,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nominal Resolution: $accuracy",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    subtitle: String? = null
) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatCoordinates(lat: Double, lon: Double, format: CoordinateFormat): String {
    return if (format == CoordinateFormat.DECIMAL) {
        String.format(Locale.US, "%.5f°, %.5f°", lat, lon)
    } else {
        "${toDms(lat, true)}, ${toDms(lon, false)}"
    }
}

private fun toDms(decimal: Double, isLat: Boolean): String {
    val dir = if (isLat) {
        if (decimal >= 0) "N" else "S"
    } else {
        if (decimal >= 0) "E" else "W"
    }
    val abs = kotlin.math.abs(decimal)
    val deg = abs.toInt()
    val minDec = (abs - deg) * 60.0
    val min = minDec.toInt()
    val sec = (minDec - min) * 60.0
    return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, dir)
}
