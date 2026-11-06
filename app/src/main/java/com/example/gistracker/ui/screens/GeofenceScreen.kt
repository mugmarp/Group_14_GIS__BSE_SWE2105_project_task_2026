package com.example.gistracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.util.UUID

data class ZonePreset(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val category: GeofenceCategory,
    val colorHex: String,
    val description: String
)

@Composable
fun GeofenceScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val zones by viewModel.geofenceZones.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val recentAlerts by viewModel.recentAlerts.collectAsState()
    val filteredJitterMeters by viewModel.filteredJitterMeters.collectAsState()
    val filteredJitterCount by viewModel.filteredJitterCount.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingZone by remember { mutableStateOf<GeofenceZone?>(null) }
    var preloadedPreset by remember { mutableStateOf<ZonePreset?>(null) }

    val presets = remember {
        listOf(
            ZonePreset(
                name = "MUST Kihumuro Campus (FAST)",
                latitude = -0.5982,
                longitude = 30.6178,
                radiusMeters = 350f,
                category = GeofenceCategory.CAMPUS,
                colorHex = "#006948",
                description = "Faculty of Applied Sciences & Tech"
            ),
            ZonePreset(
                name = "MUST Kihumuro Main Library",
                latitude = -0.5976,
                longitude = 30.6185,
                radiusMeters = 100f,
                category = GeofenceCategory.LIBRARY,
                colorHex = "#0051D5",
                description = "Academic Research & Study Zone"
            ),
            ZonePreset(
                name = "MUST Kihumuro Hostels Zone",
                latitude = -0.5995,
                longitude = 30.6155,
                radiusMeters = 250f,
                category = GeofenceCategory.HOSTEL,
                colorHex = "#7C3AED",
                description = "Kihumuro Student Residential Perimeter"
            ),
            ZonePreset(
                name = "MUST Main Town Campus",
                latitude = -0.6163,
                longitude = 30.6568,
                radiusMeters = 300f,
                category = GeofenceCategory.CAMPUS,
                colorHex = "#006948",
                description = "Administration & Medical Faculty"
            ),
            ZonePreset(
                name = "MUST Town Campus Library",
                latitude = -0.6158,
                longitude = 30.6575,
                radiusMeters = 90f,
                category = GeofenceCategory.LIBRARY,
                colorHex = "#0051D5",
                description = "Town Campus Medical Library"
            ),
            ZonePreset(
                name = "MUST Kakoba Student Hostels",
                latitude = -0.6120,
                longitude = 30.6620,
                radiusMeters = 250f,
                category = GeofenceCategory.HOSTEL,
                colorHex = "#7C3AED",
                description = "Kakoba Residential Hostels & Quarters"
            ),
            ZonePreset(
                name = "Wandegeya Boda Boda Stage",
                latitude = 0.3308,
                longitude = 32.5705,
                radiusMeters = 160f,
                category = GeofenceCategory.BODA_HOTSPOT,
                colorHex = "#D97706",
                description = "Cautionary High-Traffic Transit Hub"
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Enhanced Clean Header without crowding
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(42.dp)
                            .background(customColors.surfaceContainerLow, RoundedCornerShape(12.dp))
                            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .testTag("geofence_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Map",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Geofencing & Safe Zones",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = "MUST Kihumuro, Hostels, Libraries & Alerts",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            preloadedPreset = null
                            showAddDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_geofence_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Zone", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            // Safe Zone Integrity & Anti-Flapping Engine Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.35f))
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
                                        .size(30.dp)
                                        .background(colorScheme.tertiary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = colorScheme.tertiary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Perimeter Integrity & Anti-Flapping",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Hysteresis Boundary Filtering Active",
                                        fontSize = 10.sp,
                                        color = colorScheme.tertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colorScheme.tertiary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "15m DEADBAND",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To eliminate boundary oscillation (flapping) near hostel gates caused by multipath GPS noise, an exit hysteresis offset of 15.0m is applied before triggering state transitions.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stationary Multipath Jitter Filtered:",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$filteredJitterCount spikes (${String.format(Locale.US, "%.1f", filteredJitterMeters)} m noise)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Summary Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val insideCount = zones.count { it.isInside }
                        StatBox("TOTAL ZONES", "${zones.size}", colorScheme.primary)
                        StatBox(
                            "INSIDE SAFE ZONE",
                            "$insideCount",
                            if (insideCount > 0) colorScheme.tertiary else colorScheme.onSurfaceVariant
                        )
                        StatBox("ALERTS LOGGED", "${recentAlerts.size}", colorScheme.secondary)
                    }
                }
            }

            // MUST Academic & Demonstration Quick Presets
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MUST Quick Presets (1-Tap Setup)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        Text(
                            text = "Tap to configure",
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val alreadyExists = zones.any { it.name.contains(preset.name.split(" ").first()) }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (alreadyExists) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.clickable {
                                    preloadedPreset = preset
                                    showAddDialog = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(preset.category.iconEmoji, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = preset.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${preset.radiusMeters.toInt()}m • ${preset.category.label}",
                                            fontSize = 9.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Alerts Banner
            if (recentAlerts.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Zone Transitions (${recentAlerts.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.secondary
                    )
                }

                items(recentAlerts.take(3)) { alert ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.35f)),
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

            // Geofence Zones List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Monitored Perimeters (${zones.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                    Text(
                        text = "Editable Radius & Security",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            // Zone Cards
            items(zones, key = { it.id }) { zone ->
                val distMeters = if (currentLocation != null) {
                    viewModel.currentLocation.value?.let { curr ->
                        viewModel.calculateDistanceMeters(curr.latitude, curr.longitude, zone.latitude, zone.longitude)
                    } ?: 0.0
                } else 0.0

                GeofenceZoneCard(
                    zone = zone,
                    distanceMeters = distMeters,
                    onEdit = { editingZone = zone },
                    onDelete = { viewModel.deleteGeofence(zone.id) }
                )
            }
        }
    }

    // Dialog for Adding a Zone (with optional preset)
    if (showAddDialog) {
        val initialPreset = preloadedPreset
        GeofenceEditDialog(
            isNew = true,
            initialZone = null,
            preset = initialPreset,
            currentLat = currentLocation?.latitude ?: -0.5982,
            currentLon = currentLocation?.longitude ?: 30.6178,
            onDismiss = {
                showAddDialog = false
                preloadedPreset = null
            },
            onSave = { updated ->
                viewModel.addGeofence(
                    name = updated.name,
                    latitude = updated.latitude,
                    longitude = updated.longitude,
                    radius = updated.radiusMeters,
                    category = updated.category,
                    colorHex = updated.colorHex
                )
                showAddDialog = false
                preloadedPreset = null
            }
        )
    }

    // Dialog for Editing an Existing Zone
    editingZone?.let { zoneToEdit ->
        GeofenceEditDialog(
            isNew = false,
            initialZone = zoneToEdit,
            preset = null,
            currentLat = currentLocation?.latitude ?: zoneToEdit.latitude,
            currentLon = currentLocation?.longitude ?: zoneToEdit.longitude,
            onDismiss = { editingZone = null },
            onSave = { updated ->
                viewModel.updateGeofence(updated)
                editingZone = null
            }
        )
    }
}

@Composable
private fun GeofenceZoneCard(
    zone: GeofenceZone,
    distanceMeters: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val categoryColor = when (zone.category) {
        GeofenceCategory.CAMPUS, GeofenceCategory.SAFE_ZONE, GeofenceCategory.HOME -> colorScheme.tertiary
        GeofenceCategory.LIBRARY, GeofenceCategory.SCHOOL, GeofenceCategory.WORK -> colorScheme.secondary
        GeofenceCategory.HOSTEL -> Color(0xFF7C3AED)
        GeofenceCategory.BODA_HOTSPOT -> colorScheme.error
        GeofenceCategory.CUSTOM -> colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("geofence_card_${zone.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            if (zone.isInside) colorScheme.tertiary else colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Category Icon + Zone Name + Inside/Outside State
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(categoryColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(zone.category.iconEmoji, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = zone.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = zone.category.label,
                            fontSize = 10.sp,
                            color = categoryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (zone.isInside) colorScheme.tertiary.copy(alpha = 0.15f) else colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (zone.isInside) "🟢 INSIDE ZONE" else "OUTSIDE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (zone.isInside) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle Row: Technical Details & Distance (Fixed distance display flaw)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Radius: ${zone.radiusMeters.toInt()}m • Area: ~${String.format(Locale.US, "%.1f", (Math.PI * zone.radiusMeters * zone.radiusMeters / 10000.0))} ha",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Coord: ${String.format(Locale.US, "%.4f", zone.latitude)}°N, ${String.format(Locale.US, "%.4f", zone.longitude)}°E",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Triggers: ${if (zone.alertOnEntry) "Entry Alert" else ""}${if (zone.alertOnEntry && zone.alertOnExit) " • " else ""}${if (zone.alertOnExit) "Exit Alert" else ""}",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Distance formatting with smart threshold to eliminate emulator distance glitch
                Column(horizontalAlignment = Alignment.End) {
                    if (distanceMeters > 1_000_000) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Uganda Field Zone",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "~${(distanceMeters / 1000).toInt()} km away",
                            fontSize = 10.sp,
                            color = colorScheme.outline
                        )
                    } else if (distanceMeters >= 1000) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", distanceMeters / 1000)} km away",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "${distanceMeters.toInt()} m away",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }

                    // Action Controls: Edit & Delete Buttons
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("edit_geofence_${zone.id}")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Safe Zone",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("delete_geofence_${zone.id}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeofenceEditDialog(
    isNew: Boolean,
    initialZone: GeofenceZone?,
    preset: ZonePreset?,
    currentLat: Double,
    currentLon: Double,
    onDismiss: () -> Unit,
    onSave: (GeofenceZone) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    var name by remember {
        mutableStateOf(
            preset?.name ?: initialZone?.name ?: "MUST Safe Perimeter"
        )
    }

    var radius by remember {
        mutableFloatStateOf(
            preset?.radiusMeters ?: initialZone?.radiusMeters ?: 250f
        )
    }

    var selectedCategory by remember {
        mutableStateOf(
            preset?.category ?: initialZone?.category ?: GeofenceCategory.CAMPUS
        )
    }

    var lat by remember {
        mutableStateOf(
            String.format(Locale.US, "%.5f", preset?.latitude ?: initialZone?.latitude ?: currentLat)
        )
    }

    var lon by remember {
        mutableStateOf(
            String.format(Locale.US, "%.5f", preset?.longitude ?: initialZone?.longitude ?: currentLon)
        )
    }

    var alertOnEntry by remember {
        mutableStateOf(initialZone?.alertOnEntry ?: true)
    }

    var alertOnExit by remember {
        mutableStateOf(initialZone?.alertOnExit ?: true)
    }

    val colorHex = preset?.colorHex ?: initialZone?.colorHex ?: selectedCategory.defaultColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Add Monitored Safe Zone" else "Edit Safe Zone Parameters",
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Zone Name
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Zone Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("geofence_name_input")
                    )
                }

                // Category Selection Chips
                item {
                    Text(
                        text = "Category & Icon:",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            GeofenceCategory.CAMPUS,
                            GeofenceCategory.LIBRARY,
                            GeofenceCategory.HOSTEL,
                            GeofenceCategory.SAFE_ZONE,
                            GeofenceCategory.SCHOOL,
                            GeofenceCategory.BODA_HOTSPOT,
                            GeofenceCategory.HOME
                        ).forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text("${cat.iconEmoji} ${cat.label}", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // Editable Radius Slider & Live Calculations
                item {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Perimeter Radius:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "${radius.toInt()} meters",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.primary
                                )
                            }

                            Slider(
                                value = radius,
                                onValueChange = { radius = it },
                                valueRange = 30f..1500f,
                                colors = SliderDefaults.colors(
                                    thumbColor = colorScheme.primary,
                                    activeTrackColor = colorScheme.primary
                                ),
                                modifier = Modifier.testTag("radius_slider")
                            )

                            // Quick radius increment buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(50f, 100f, 250f, 500f).forEach { r ->
                                    OutlinedButton(
                                        onClick = { radius = r },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("${r.toInt()}m", fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            val areaHa = (Math.PI * radius * radius / 10000.0)
                            Text(
                                text = "Enclosed Area: ~${String.format(Locale.US, "%.2f", areaHa)} ha (Diameter: ${(radius * 2).toInt()}m)",
                                fontSize = 10.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Center Coordinates
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Center Coordinates:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = {
                                    lat = String.format(Locale.US, "%.5f", currentLat)
                                    lon = String.format(Locale.US, "%.5f", currentLon)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Use GPS Fix", fontSize = 10.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = lat,
                                onValueChange = { lat = it },
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lon,
                                onValueChange = { lon = it },
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Notification Alert Triggers
                item {
                    Column {
                        Text(
                            text = "Alert Triggers:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = alertOnEntry,
                                onCheckedChange = { alertOnEntry = it },
                                colors = CheckboxDefaults.colors(checkedColor = colorScheme.primary)
                            )
                            Text("Notify on Perimeter Entry", fontSize = 11.sp, color = colorScheme.onSurface)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = alertOnExit,
                                onCheckedChange = { alertOnExit = it },
                                colors = CheckboxDefaults.colors(checkedColor = colorScheme.primary)
                            )
                            Text("Notify on Perimeter Exit", fontSize = 11.sp, color = colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedLat = lat.toDoubleOrNull() ?: currentLat
                    val parsedLon = lon.toDoubleOrNull() ?: currentLon
                    val zone = GeofenceZone(
                        id = initialZone?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { "Safe Zone" },
                        latitude = parsedLat,
                        longitude = parsedLon,
                        radiusMeters = radius,
                        category = selectedCategory,
                        colorHex = colorHex,
                        alertOnEntry = alertOnEntry,
                        alertOnExit = alertOnExit,
                        isInside = initialZone?.isInside ?: false,
                        lastTransitionTime = initialZone?.lastTransitionTime
                    )
                    onSave(zone)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_save_geofence_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isNew) "Save Zone" else "Update Zone", fontWeight = FontWeight.Bold)
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
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}
