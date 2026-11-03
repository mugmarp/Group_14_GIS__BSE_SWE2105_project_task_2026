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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gistracker.model.GeofenceCategory
import com.example.gistracker.model.GeofenceZone
import com.example.gistracker.ui.theme.LocalCustomColors
import com.example.gistracker.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GeofenceScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val zones by viewModel.geofenceZones.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val recentAlerts by viewModel.recentAlerts.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Geofencing & Safe Zones",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = "Perimeter surveillance & boda hotspot warnings",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_geofence_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Zone", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Summary Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val insideCount = zones.count { it.isInside }
                        StatBox("TOTAL ZONES", "${zones.size}", colorScheme.primary)
                        StatBox("CURRENTLY INSIDE", "$insideCount", if (insideCount > 0) colorScheme.tertiary else colorScheme.onSurfaceVariant)
                        StatBox("ALERTS LOGGED", "${recentAlerts.size}", colorScheme.secondary)
                    }
                }
            }

            // Recent Alerts Banner
            if (recentAlerts.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Zone Transitions",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.secondary
                    )
                }

                items(recentAlerts.take(3)) { alert ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (alert.eventType == "ENTERED") colorScheme.tertiary else colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${alert.eventType}: ${alert.zoneName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))
                                    Text(
                                        text = "Time: $timeStr",
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Geofence Zones List
            item {
                Text(
                    text = "Monitored Geofence Perimeters (${zones.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
            }

            items(zones) { zone ->
                val distMeters = if (currentLocation != null) {
                    viewModel.currentLocation.value?.let { curr ->
                        val earthRadius = 6371000.0
                        val dLat = Math.toRadians(zone.latitude - curr.latitude)
                        val dLon = Math.toRadians(zone.longitude - curr.longitude)
                        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                Math.cos(Math.toRadians(curr.latitude)) * Math.cos(Math.toRadians(zone.latitude)) *
                                Math.sin(dLon / 2) * Math.sin(dLon / 2)
                        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                        earthRadius * c
                    } ?: 0.0
                } else 0.0

                GeofenceZoneCard(
                    zone = zone,
                    distanceMeters = distMeters,
                    onDelete = { viewModel.deleteGeofence(zone.id) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddGeofenceDialog(
            currentLat = currentLocation?.latitude ?: 0.3345,
            currentLon = currentLocation?.longitude ?: 32.5712,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, lat, lon, radius, category, colorHex ->
                viewModel.addGeofence(name, lat, lon, radius, category, colorHex)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun GeofenceZoneCard(
    zone: GeofenceZone,
    distanceMeters: Double,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val zoneAccent = when (zone.category) {
        GeofenceCategory.SAFE_ZONE, GeofenceCategory.HOME -> colorScheme.tertiary
        GeofenceCategory.BODA_HOTSPOT -> colorScheme.error
        GeofenceCategory.SCHOOL, GeofenceCategory.WORK -> colorScheme.secondary
        GeofenceCategory.CUSTOM -> customColors.warning
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("geofence_card_${zone.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, if (zone.isInside) colorScheme.tertiary else colorScheme.outline.copy(alpha = 0.5f))
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
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(zoneAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = zone.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (zone.isInside) colorScheme.tertiary.copy(alpha = 0.2f) else colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (zone.isInside) "INSIDE ZONE" else "OUTSIDE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (zone.isInside) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Radius: ${zone.radiusMeters.toInt()} meters | Cat: ${zone.category.name}",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Coord: ${String.format("%.4f", zone.latitude)}°N, ${String.format("%.4f", zone.longitude)}°E",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (distanceMeters >= 1000) "${String.format("%.1f", distanceMeters / 1000)} km away" else "${distanceMeters.toInt()} m away",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddGeofenceDialog(
    currentLat: Double,
    currentLon: Double,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Float, GeofenceCategory, String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("New Safe Perimeter") }
    var radius by remember { mutableFloatStateOf(300f) }
    var selectedCategory by remember { mutableStateOf(GeofenceCategory.SAFE_ZONE) }
    val colorHex = when (selectedCategory) {
        GeofenceCategory.SAFE_ZONE -> "#2E7D32"
        GeofenceCategory.SCHOOL -> "#1B4F72"
        GeofenceCategory.HOME -> "#4DB6AC"
        GeofenceCategory.WORK -> "#64B5F6"
        GeofenceCategory.BODA_HOTSPOT -> "#C62828"
        GeofenceCategory.CUSTOM -> "#F9A825"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Geofence Zone", color = colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Zone Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("geofence_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Perimeter Radius: ${radius.toInt()} meters",
                    color = colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 50f..1500f,
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Category:", color = colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(GeofenceCategory.SAFE_ZONE, GeofenceCategory.SCHOOL, GeofenceCategory.BODA_HOTSPOT).forEach { cat ->
                        TextButton(
                            onClick = { selectedCategory = cat },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selectedCategory == cat) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(cat.name.replace("_", " "), fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Center: Current GPS (${String.format("%.4f", currentLat)}, ${String.format("%.4f", currentLon)})",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, currentLat, currentLon, radius, selectedCategory, colorHex)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("confirm_add_geofence_btn")
            ) {
                Text("Create Zone", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colorScheme.onSurfaceVariant)
            }
        },
        containerColor = colorScheme.surface
    )
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}
