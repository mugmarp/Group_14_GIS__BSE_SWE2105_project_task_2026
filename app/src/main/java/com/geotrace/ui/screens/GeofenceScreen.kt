package com.geotrace.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geotrace.model.GeofenceCategory
import com.geotrace.model.GeofenceZone
import com.geotrace.ui.screens.devices.BackgroundLocationSettingsDialog
import com.geotrace.ui.screens.devices.hasBackgroundLocationPermission
import com.geotrace.ui.screens.devices.openAppLocationSettings
import com.geotrace.ui.screens.geofence.GeofenceAlertsPrecisionSubScreen
import com.geotrace.ui.screens.geofence.GeofencePresetsSubScreen
import com.geotrace.ui.screens.geofence.GeofenceSubgroup
import com.geotrace.ui.screens.geofence.GeofenceSubgroupCard
import com.geotrace.ui.screens.geofence.GeofenceTransitionsSubScreen
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel
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
    onPickOnMap: ((TrackerViewModel.GeofencePickDraft) -> Unit)? = null,
    onLocateOnMap: ((GeofenceZone) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val context = LocalContext.current
    val zones by viewModel.geofenceZones.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val recentAlerts by viewModel.recentAlerts.collectAsState()
    val filteredJitterMeters by viewModel.filteredJitterMeters.collectAsState()
    val filteredJitterCount by viewModel.filteredJitterCount.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val geofenceApiStatus by viewModel.geofenceApiStatus.collectAsState()
    val geofencePickDraft by viewModel.geofencePickDraft.collectAsState()
    val isPickingGeofenceCenter by viewModel.isPickingGeofenceCenter.collectAsState()

    var activeSubgroup by rememberSaveable { mutableStateOf<GeofenceSubgroup?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingZone by remember { mutableStateOf<GeofenceZone?>(null) }
    var preloadedPreset by remember { mutableStateOf<ZonePreset?>(null) }
    var draftOverrides by remember { mutableStateOf<TrackerViewModel.GeofencePickDraft?>(null) }
    var pendingDeleteZone by remember { mutableStateOf<GeofenceZone?>(null) }
    var showBgSettingsDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (activeSubgroup != null) {
            activeSubgroup = null
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(geofencePickDraft, isPickingGeofenceCenter) {
        val draft = geofencePickDraft
        if (!isPickingGeofenceCenter && draft != null) {
            draftOverrides = draft
            preloadedPreset = null
            if (draft.isNew) {
                editingZone = null
                showAddDialog = true
            } else {
                showAddDialog = false
                editingZone = zones.firstOrNull { it.id == draft.zoneId }
                    ?: GeofenceZone(
                        id = draft.zoneId ?: UUID.randomUUID().toString(),
                        name = draft.name,
                        latitude = draft.latitude,
                        longitude = draft.longitude,
                        radiusMeters = draft.radiusMeters,
                        category = draft.category,
                        colorHex = draft.colorHex,
                        alertOnEntry = draft.alertOnEntry,
                        alertOnExit = draft.alertOnExit
                    )
            }
            viewModel.clearGeofencePickDraft()
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && !hasBackgroundLocationPermission(context)) {
            showBgSettingsDialog = true
        }
        viewModel.resyncGeofencingApi()
    }

    val requestBackgroundLocation: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showBgSettingsDialog = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val notificationAndLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.toggleGeofenceNotifications(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasBackgroundLocationPermission(context)
        ) {
            requestBackgroundLocation()
        }
    }

    val handleNotificationToggle: (Boolean) -> Unit = { enable ->
        if (!enable) {
            viewModel.toggleGeofenceNotifications(false)
        } else {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (needed.isNotEmpty()) {
                notificationAndLocationLauncher.launch(needed.toTypedArray())
            } else {
                viewModel.toggleGeofenceNotifications(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !hasBackgroundLocationPermission(context)
                ) {
                    requestBackgroundLocation()
                }
            }
        }
    }

    val presets = remember {
        listOf(
            ZonePreset(
                name = "MUST Kihumuro Campus (FAST)",
                latitude = -0.5947,
                longitude = 30.5998,
                radiusMeters = 350f,
                category = GeofenceCategory.CAMPUS,
                colorHex = "#006948",
                description = "Faculty of Applied Sciences & Tech"
            ),
            ZonePreset(
                name = "MUST Kihumuro Main Library",
                latitude = -0.5945,
                longitude = 30.6002,
                radiusMeters = 100f,
                category = GeofenceCategory.LIBRARY,
                colorHex = "#0051D5",
                description = "Academic Research & Study Zone"
            ),
            ZonePreset(
                name = "MUST Kihumuro Hostels Zone",
                latitude = -0.5955,
                longitude = 30.5980,
                radiusMeters = 250f,
                category = GeofenceCategory.HOSTEL,
                colorHex = "#7C3AED",
                description = "Kihumuro Student Residential Perimeter"
            ),
            ZonePreset(
                name = "MUST Main Town Campus",
                latitude = -0.6155,
                longitude = 30.6564,
                radiusMeters = 300f,
                category = GeofenceCategory.CAMPUS,
                colorHex = "#006948",
                description = "Administration & Medical Faculty"
            ),
            ZonePreset(
                name = "MUST Town Campus Library",
                latitude = -0.6152,
                longitude = 30.6572,
                radiusMeters = 90f,
                category = GeofenceCategory.LIBRARY,
                colorHex = "#0051D5",
                description = "Town Campus Medical Library"
            ),
            ZonePreset(
                name = "MUST Kakoba Student Hostels",
                latitude = -0.6075,
                longitude = 30.6727,
                radiusMeters = 300f,
                category = GeofenceCategory.HOSTEL,
                colorHex = "#7C3AED",
                description = "Kakoba Residential Hostels & Quarters"
            ),
            ZonePreset(
                name = "Wandegeya Boda Boda Stage",
                latitude = 0.3311,
                longitude = 32.5736,
                radiusMeters = 160f,
                category = GeofenceCategory.BODA_HOTSPOT,
                colorHex = "#D97706",
                description = "Cautionary High-Traffic Transit Hub"
            )
        )
    }

    AnimatedContent(
        targetState = activeSubgroup,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(200)) { it / 6 } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(180)) { -it / 8 } + fadeOut(tween(150)))
            } else {
                (slideInHorizontally(tween(200)) { -it / 6 } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(180)) { it / 8 } + fadeOut(tween(150)))
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background),
        label = "geofence_subgroup_transition"
    ) { selectedSubgroup ->
        when (selectedSubgroup) {
            null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .size(48.dp)
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
                                    text = "Perimeters, precision tuning & transition logs",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    preloadedPreset = null
                                    draftOverrides = null
                                    showAddDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("add_geofence_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Zone", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    // Compact 3-Stat Overview Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.35f))
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

                    // Subgroup Cards Header
                    item {
                        Text(
                            text = "Surveillance & Tools",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        val alertsBadge = buildString {
                            append(if (settings.geofenceNotificationEnabled) "ON" else "OFF")
                            append(" • ")
                            append(
                                if (settings.geofenceHysteresisMeters > 0f) {
                                    "±${settings.geofenceHysteresisMeters.toInt()}m"
                                } else {
                                    "Raw"
                                }
                            )
                        }
                        GeofenceSubgroupCard(
                            title = GeofenceSubgroup.ALERTS_PRECISION.title,
                            subtitle = GeofenceSubgroup.ALERTS_PRECISION.subtitle,
                            badgeText = alertsBadge,
                            badgeColor = if (settings.geofenceNotificationEnabled) colorScheme.tertiary else colorScheme.primary,
                            icon = Icons.Default.Security,
                            testTag = "geofence_group_alerts_precision",
                            onClick = { activeSubgroup = GeofenceSubgroup.ALERTS_PRECISION }
                        )
                    }

                    item {
                        GeofenceSubgroupCard(
                            title = GeofenceSubgroup.MUST_PRESETS.title,
                            subtitle = GeofenceSubgroup.MUST_PRESETS.subtitle,
                            badgeText = "${presets.size} presets",
                            badgeColor = colorScheme.primary,
                            icon = Icons.Default.Map,
                            testTag = "geofence_group_presets",
                            onClick = { activeSubgroup = GeofenceSubgroup.MUST_PRESETS }
                        )
                    }

                    item {
                        GeofenceSubgroupCard(
                            title = GeofenceSubgroup.TRANSITION_HISTORY.title,
                            subtitle = GeofenceSubgroup.TRANSITION_HISTORY.subtitle,
                            badgeText = "${recentAlerts.size} alerts",
                            badgeColor = colorScheme.secondary,
                            icon = Icons.Default.History,
                            testTag = "geofence_group_history",
                            onClick = { activeSubgroup = GeofenceSubgroup.TRANSITION_HISTORY }
                        )
                    }

                    // Immediate Active Monitored Perimeters List
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
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

                    if (zones.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Active Perimeters Configured",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add a custom safe zone or load one of the MUST campus presets.",
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { activeSubgroup = GeofenceSubgroup.MUST_PRESETS },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Browse MUST Presets", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(zones, key = { it.id }) { zone ->
                            val distMeters = currentLocation?.let { curr ->
                                viewModel.calculateDistanceMeters(curr.latitude, curr.longitude, zone.latitude, zone.longitude)
                            }

                            GeofenceZoneCard(
                                zone = zone,
                                distanceMeters = distMeters,
                                hysteresisMeters = settings.geofenceHysteresisMeters,
                                onLocate = if (onLocateOnMap != null) {
                                    { onLocateOnMap(zone) }
                                } else null,
                                onEdit = {
                                    draftOverrides = null
                                    editingZone = zone
                                },
                                onDelete = { pendingDeleteZone = zone }
                            )
                        }
                    }
                }
            }

            GeofenceSubgroup.ALERTS_PRECISION -> GeofenceAlertsPrecisionSubScreen(
                viewModel = viewModel,
                zones = zones,
                settings = settings,
                geofenceApiStatus = geofenceApiStatus,
                filteredJitterCount = filteredJitterCount,
                filteredJitterMeters = filteredJitterMeters,
                onToggleNotifications = handleNotificationToggle,
                onRequestBackgroundLocation = requestBackgroundLocation,
                onBack = { activeSubgroup = null }
            )

            GeofenceSubgroup.MUST_PRESETS -> GeofencePresetsSubScreen(
                presets = presets,
                zones = zones,
                onSelectPreset = { preset ->
                    preloadedPreset = preset
                    draftOverrides = null
                    showAddDialog = true
                },
                onResetSamples = { viewModel.resetSampleGeofences() },
                onBack = { activeSubgroup = null }
            )

            GeofenceSubgroup.TRANSITION_HISTORY -> GeofenceTransitionsSubScreen(
                recentAlerts = recentAlerts,
                onClearAlerts = { viewModel.clearGeofenceAlerts() },
                onBack = { activeSubgroup = null }
            )
        }
    }

    if (showBgSettingsDialog) {
        BackgroundLocationSettingsDialog(
            onOpenSettings = {
                showBgSettingsDialog = false
                openAppLocationSettings(context)
            },
            onDismiss = { showBgSettingsDialog = false }
        )
    }

    // Delete Confirmation Dialog
    pendingDeleteZone?.let { zoneToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteZone = null },
            title = {
                Text(
                    text = "Delete Safe Zone?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Remove \"${zoneToDelete.name}\" (${zoneToDelete.radiusMeters.toInt()}m perimeter) from monitored safe zones?",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGeofence(zoneToDelete.id)
                        pendingDeleteZone = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_geofence_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteZone = null }) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant)
                }
            },
            containerColor = colorScheme.surface
        )
    }

    // Dialog for Adding a Zone (with optional preset or map-picked draft)
    if (showAddDialog) {
        val initialPreset = preloadedPreset
        GeofenceEditDialog(
            isNew = true,
            initialZone = null,
            preset = initialPreset,
            draft = draftOverrides,
            currentLocation = currentLocation,
            onPickOnMap = if (onPickOnMap != null) {
                { draft ->
                    showAddDialog = false
                    preloadedPreset = null
                    draftOverrides = null
                    onPickOnMap(draft)
                }
            } else null,
            onDismiss = {
                showAddDialog = false
                preloadedPreset = null
                draftOverrides = null
            },
            onSave = { updated ->
                viewModel.addGeofence(
                    name = updated.name,
                    latitude = updated.latitude,
                    longitude = updated.longitude,
                    radius = updated.radiusMeters,
                    category = updated.category,
                    colorHex = updated.colorHex,
                    alertOnEntry = updated.alertOnEntry,
                    alertOnExit = updated.alertOnExit
                )
                showAddDialog = false
                preloadedPreset = null
                draftOverrides = null
            }
        )
    }

    // Dialog for Editing an Existing Zone
    editingZone?.let { zoneToEdit ->
        GeofenceEditDialog(
            isNew = false,
            initialZone = zoneToEdit,
            preset = null,
            draft = draftOverrides,
            currentLocation = currentLocation,
            onPickOnMap = if (onPickOnMap != null) {
                { draft ->
                    editingZone = null
                    draftOverrides = null
                    onPickOnMap(draft)
                }
            } else null,
            onDismiss = {
                editingZone = null
                draftOverrides = null
            },
            onSave = { updated ->
                viewModel.updateGeofence(updated)
                editingZone = null
                draftOverrides = null
            }
        )
    }
}

@Composable
private fun GeofenceZoneCard(
    zone: GeofenceZone,
    distanceMeters: Double?,
    hysteresisMeters: Float,
    onLocate: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val categoryColor = when (zone.category) {
        GeofenceCategory.CAMPUS, GeofenceCategory.SAFE_ZONE, GeofenceCategory.HOME -> colorScheme.tertiary
        GeofenceCategory.LIBRARY, GeofenceCategory.SCHOOL, GeofenceCategory.WORK -> colorScheme.secondary
        GeofenceCategory.HOSTEL -> colorScheme.primary
        GeofenceCategory.BODA_HOTSPOT -> customColors.warning
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

            // Middle Row: Technical Details & Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val entryR = (zone.radiusMeters - hysteresisMeters).coerceAtLeast(minOf(10f, zone.radiusMeters * 0.5f))
                    val exitR = zone.radiusMeters + hysteresisMeters
                    Text(
                        text = if (hysteresisMeters > 0f) {
                            "Radius: ${zone.radiusMeters.toInt()}m (Entry ≤${entryR.toInt()}m • Exit >${exitR.toInt()}m)"
                        } else {
                            "Radius: ${zone.radiusMeters.toInt()}m • Area: ~${String.format(Locale.US, "%.1f", (Math.PI * zone.radiusMeters * zone.radiusMeters / 10000.0))} ha"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    val latDir = if (zone.latitude >= 0) "N" else "S"
                    val lonDir = if (zone.longitude >= 0) "E" else "W"
                    Text(
                        text = "Coord: ${String.format(Locale.US, "%.4f", kotlin.math.abs(zone.latitude))}°$latDir, ${String.format(Locale.US, "%.4f", kotlin.math.abs(zone.longitude))}°$lonDir",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    val triggersLabel = listOfNotNull(
                        if (zone.alertOnEntry) "Entry Alert" else null,
                        if (zone.alertOnExit) "Exit Alert" else null
                    ).joinToString(" • ").ifBlank { "No alerts enabled" }
                    Text(
                        text = "Triggers: $triggersLabel",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Distance formatting with null-fix awareness and non-hardcoded remote badge
                Column(horizontalAlignment = Alignment.End) {
                    when {
                        distanceMeters == null -> {
                            Text(
                                text = "Awaiting GPS fix",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        distanceMeters > 1_000_000 -> {
                            val isUgandaZone = zone.latitude in -1.6..4.3 && zone.longitude in 29.5..35.1
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isUgandaZone) "Uganda Field Zone" else "Remote Perimeter",
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
                        }
                        distanceMeters >= 1000 -> {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", distanceMeters / 1000)} km away",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                        else -> {
                            Text(
                                text = "${distanceMeters.toInt()} m away",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }

                    // Action Controls: Locate, Edit & Delete Buttons (48dp touch targets)
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onLocate != null) {
                            IconButton(
                                onClick = onLocate,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("locate_geofence_${zone.id}")
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Locate Safe Zone on Map",
                                    tint = colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("edit_geofence_${zone.id}")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Safe Zone",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_geofence_${zone.id}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = colorScheme.error,
                                modifier = Modifier.size(18.dp)
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
    draft: TrackerViewModel.GeofencePickDraft? = null,
    currentLocation: com.geotrace.model.LocationPoint?,
    onPickOnMap: ((TrackerViewModel.GeofencePickDraft) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (GeofenceZone) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val fallbackLat = initialZone?.latitude ?: currentLocation?.latitude ?: -0.5947
    val fallbackLon = initialZone?.longitude ?: currentLocation?.longitude ?: 30.5998

    var name by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableStateOf(
            draft?.name ?: preset?.name ?: initialZone?.name ?: "MUST Safe Perimeter"
        )
    }

    var radius by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableFloatStateOf(
            draft?.radiusMeters ?: preset?.radiusMeters ?: initialZone?.radiusMeters ?: 250f
        )
    }

    var selectedCategory by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableStateOf(
            draft?.category ?: preset?.category ?: initialZone?.category ?: GeofenceCategory.CAMPUS
        )
    }

    var colorHex by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableStateOf(
            draft?.colorHex ?: preset?.colorHex ?: initialZone?.colorHex ?: selectedCategory.defaultColor
        )
    }

    var lat by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableStateOf(
            String.format(Locale.US, "%.5f", draft?.latitude ?: preset?.latitude ?: initialZone?.latitude ?: fallbackLat)
        )
    }

    var lon by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableStateOf(
            String.format(Locale.US, "%.5f", draft?.longitude ?: preset?.longitude ?: initialZone?.longitude ?: fallbackLon)
        )
    }

    var alertOnEntry by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableStateOf(draft?.alertOnEntry ?: initialZone?.alertOnEntry ?: true)
    }

    var alertOnExit by remember(draft?.latitude, draft?.longitude, preset?.name, initialZone?.id, isNew) {
        mutableStateOf(draft?.alertOnExit ?: initialZone?.alertOnExit ?: true)
    }

    val parsedLat = lat.trim().toDoubleOrNull()
    val parsedLon = lon.trim().toDoubleOrNull()
    val isLatValid = parsedLat != null && parsedLat.isFinite() && parsedLat in -90.0..90.0
    val isLonValid = parsedLon != null && parsedLon.isFinite() && parsedLon in -180.0..180.0
    val isNotNullIsland = parsedLat != null && parsedLon != null && (parsedLat != 0.0 || parsedLon != 0.0)
    val areCoordsValid = isLatValid && isLonValid && isNotNullIsland

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

                // Category Selection Chips (all GeofenceCategory values)
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
                        GeofenceCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = cat
                                    colorHex = cat.defaultColor
                                },
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
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.heightIn(min = 34.dp)
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

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (onPickOnMap != null) {
                                    TextButton(
                                        onClick = {
                                            val pickLat = if (isLatValid && parsedLat != null) parsedLat else fallbackLat
                                            val pickLon = if (isLonValid && parsedLon != null) parsedLon else fallbackLon
                                            onPickOnMap(
                                                TrackerViewModel.GeofencePickDraft(
                                                    isNew = isNew,
                                                    zoneId = initialZone?.id,
                                                    name = name,
                                                    radiusMeters = radius,
                                                    category = selectedCategory,
                                                    colorHex = colorHex,
                                                    alertOnEntry = alertOnEntry,
                                                    alertOnExit = alertOnExit,
                                                    latitude = pickLat,
                                                    longitude = pickLon
                                                )
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .heightIn(min = 34.dp)
                                            .testTag("pick_geofence_map_btn")
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Pick on Map", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        val fix = currentLocation
                                        if (fix == null) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Waiting for live GPS fix...",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            lat = String.format(Locale.US, "%.5f", fix.latitude)
                                            lon = String.format(Locale.US, "%.5f", fix.longitude)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.heightIn(min = 34.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Use GPS Fix", fontSize = 10.sp)
                                }
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
                                isError = !isLatValid || !isNotNullIsland,
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lon,
                                onValueChange = { lon = it },
                                label = { Text("Longitude") },
                                isError = !isLonValid || !isNotNullIsland,
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!areCoordsValid) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter valid WGS84 coordinates (Lat -90..90, Lon -180..180, non-zero).",
                                fontSize = 10.sp,
                                color = colorScheme.error
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
                enabled = areCoordsValid,
                onClick = {
                    val safeLat = parsedLat ?: return@Button
                    val safeLon = parsedLon ?: return@Button
                    val zone = GeofenceZone(
                        id = initialZone?.id ?: UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { "Safe Zone" },
                        latitude = safeLat,
                        longitude = safeLon,
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
