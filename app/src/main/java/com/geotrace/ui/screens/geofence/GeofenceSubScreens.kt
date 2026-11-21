package com.geotrace.ui.screens.geofence

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.GeofenceAlert
import com.geotrace.model.GeofenceZone
import com.geotrace.model.TrackerSettings
import com.geotrace.service.GeofenceApiStatus
import com.geotrace.ui.components.GeoTraceStatusBadge
import com.geotrace.ui.components.GeoTraceTopBar
import com.geotrace.ui.components.ScreenListContentPadding
import com.geotrace.ui.components.StatusBadgeTone
import com.geotrace.ui.components.adaptiveContentWidth
import com.geotrace.ui.screens.ZonePreset
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GeofenceSubgroup(
    val title: String,
    val subtitle: String
) {
    ALERTS_PRECISION(
        title = "Alerts & Precision Tuning",
        subtitle = "Geofence API alerts, dual-radius hysteresis & accuracy gate"
    ),
    MUST_PRESETS(
        title = "MUST Quick Presets",
        subtitle = "1-tap campus, hostel & library perimeter templates"
    ),
    TRANSITION_HISTORY(
        title = "Zone Transition History",
        subtitle = "Chronological log of safe zone entries & exits"
    )
}

@Composable
internal fun GeofenceSubgroupCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open $title",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun GeofenceAlertsPrecisionSubScreen(
    viewModel: TrackerViewModel,
    zones: List<GeofenceZone>,
    settings: TrackerSettings,
    geofenceApiStatus: GeofenceApiStatus,
    filteredJitterCount: Int,
    filteredJitterMeters: Double,
    onToggleNotifications: (Boolean) -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val delta = settings.geofenceHysteresisMeters

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .adaptiveContentWidth()
                .fillMaxSize(),
            contentPadding = ScreenListContentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GeoTraceTopBar(
                    title = GeofenceSubgroup.ALERTS_PRECISION.title,
                    subtitle = GeofenceSubgroup.ALERTS_PRECISION.subtitle,
                    backContentDescription = "Back to Geofencing",
                    backTestTag = "geofence_sub_back_btn",
                    onBack = onBack
                )
            }

        // Academic Perimeter Alerts Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("geofence_notification_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (settings.geofenceNotificationEnabled) colorScheme.primary.copy(alpha = 0.6f)
                    else colorScheme.outline.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (settings.geofenceNotificationEnabled) colorScheme.primary.copy(alpha = 0.15f)
                                        else customColors.surfaceContainerLow,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (settings.geofenceNotificationEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Academic Perimeter Alerts",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    GeoTraceStatusBadge(
                                        text = when {
                                            !settings.geofenceNotificationEnabled -> "OFF BY DEFAULT"
                                            geofenceApiStatus.isRegisteredWithPlayServices -> "API ACTIVE"
                                            else -> "IN-APP ACTIVE"
                                        },
                                        tone = when {
                                            !settings.geofenceNotificationEnabled -> StatusBadgeTone.NEUTRAL
                                            geofenceApiStatus.isRegisteredWithPlayServices -> StatusBadgeTone.NOMINAL
                                            else -> StatusBadgeTone.ACTIVE
                                        }
                                    )
                                }
                                Text(
                                    text = geofenceApiStatus.statusMessage,
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = settings.geofenceNotificationEnabled,
                            onCheckedChange = onToggleNotifications,
                            colors = SwitchDefaults.colors(checkedThumbColor = colorScheme.primary),
                            modifier = Modifier.testTag("geofence_notification_toggle")
                        )
                    }

                    if (settings.geofenceNotificationEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))

                        if (geofenceApiStatus.errorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = customColors.warning.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, customColors.warning.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = geofenceApiStatus.errorMessage ?: "",
                                        fontSize = 11.sp,
                                        color = customColors.warning,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (geofenceApiStatus.needsBackgroundPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedButton(
                                            onClick = onRequestBackgroundLocation,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Allow Background Location", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val enabledZones = zones.filter { it.isEnabled }
                            val academicEnabled = enabledZones.count { it.category.isAcademicZone }
                            Text(
                                text = "GeofencingClient: ${enabledZones.size} enabled ($academicEnabled academic) / ${zones.size} saved",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val sent = viewModel.sendTestAcademicNotification()
                                    Toast.makeText(
                                        context,
                                        if (sent) "Test perimeter alert dispatched"
                                        else "Notifications blocked — enable Notifications in Android Settings",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("test_geofence_alert_btn")
                            ) {
                                Text("Test Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Academic Precision Adjustment Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colorScheme.tertiary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Academic Precision Adjustment",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = if (delta > 0f) {
                                        "Dual-Radius Hysteresis Active (Entry R−${delta.toInt()}m / Exit R+${delta.toInt()}m)"
                                    } else {
                                        "Raw Single-Radius Mode (No Hysteresis Buffer)"
                                    },
                                    fontSize = 10.sp,
                                    color = if (delta > 0f) colorScheme.tertiary else customColors.warning,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = (if (delta > 0f) colorScheme.tertiary else customColors.warning).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (delta > 0f) "±${delta.toInt()}m BAND" else "0m RAW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (delta > 0f) colorScheme.tertiary else customColors.warning,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Uses a smaller inner radius (R − Δ) to confirm genuine entry and a larger outer radius (R + Δ) before triggering exit—preventing false boundary flapping from GPS drift.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presetsDelta = listOf(0f to "0m Raw", 5f to "±5m Tight", 10f to "±10m Std", 20f to "±20m Wide")
                        presetsDelta.forEach { (value, label) ->
                            val selected = delta == value
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateGeofenceHysteresis(value) },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary,
                                    selectedLabelColor = colorScheme.onPrimary,
                                    containerColor = customColors.surfaceContainerLow,
                                    labelColor = colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = colorScheme.outlineVariant,
                                    selectedBorderColor = colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Confidence Accuracy Gate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ignore boundary flips on coarse Cell-Only or low-confidence fixes",
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = settings.geofenceAccuracyGateEnabled,
                            onCheckedChange = { viewModel.toggleGeofenceAccuracyGate(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = colorScheme.primary),
                            modifier = Modifier.testTag("geofence_accuracy_gate_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stationary Multipath Jitter Filtered:",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$filteredJitterCount spikes (${String.format(Locale.US, "%.1f", filteredJitterMeters)} m noise)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = colorScheme.primary
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun GeofencePresetsSubScreen(
    presets: List<ZonePreset>,
    zones: List<GeofenceZone>,
    onSelectPreset: (ZonePreset) -> Unit,
    onResetSamples: () -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .adaptiveContentWidth()
                .fillMaxSize(),
            contentPadding = ScreenListContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GeoTraceTopBar(
                    title = GeofenceSubgroup.MUST_PRESETS.title,
                    subtitle = "Tap any campus or transit template to customize & add",
                    backContentDescription = "Back to Geofencing",
                    backTestTag = "geofence_sub_back_btn",
                    onBack = onBack,
                    trailingContent = {
                        OutlinedButton(
                            onClick = onResetSamples,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("reset_sample_zones_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

        item {
            Text(
                text = "Tip: Use 'Pick on Map' in any zone editor on Satellite view to align pins with rooftops.",
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }

        items(presets) { preset ->
            val matchingZone = zones.firstOrNull { it.name.equals(preset.name, ignoreCase = true) }
            val alreadyExists = matchingZone != null
            val isZoneEnabled = matchingZone?.isEnabled == true
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectPreset(preset) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    when {
                        isZoneEnabled -> colorScheme.tertiary.copy(alpha = 0.55f)
                        alreadyExists -> colorScheme.primary.copy(alpha = 0.35f)
                        else -> colorScheme.outlineVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(preset.category.iconEmoji, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "${preset.radiusMeters.toInt()}m • ${preset.category.label} • ${preset.description}",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isZoneEnabled) {
                        GeoTraceStatusBadge(text = "Active", tone = StatusBadgeTone.NOMINAL)
                    } else if (alreadyExists) {
                        GeoTraceStatusBadge(text = "Off", tone = StatusBadgeTone.NEUTRAL)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Use",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun GeofenceTransitionsSubScreen(
    recentAlerts: List<GeofenceAlert>,
    onClearAlerts: () -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .adaptiveContentWidth()
                .fillMaxSize(),
            contentPadding = ScreenListContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GeoTraceTopBar(
                    title = GeofenceSubgroup.TRANSITION_HISTORY.title,
                    subtitle = "${recentAlerts.size} recorded boundary event(s)",
                    backContentDescription = "Back to Geofencing",
                    backTestTag = "geofence_sub_back_btn",
                    onBack = onBack,
                    trailingContent = if (recentAlerts.isNotEmpty()) {
                        {
                            TextButton(
                                onClick = onClearAlerts,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("clear_geofence_alerts_btn")
                            ) {
                                Text(
                                    text = "Clear All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.error
                                )
                            }
                        }
                    } else null
                )
            }

        if (recentAlerts.isEmpty()) {
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
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Zone Transitions Logged Yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Entries and exits across active safe zones will appear here chronologically.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentAlerts, key = { it.id }) { alert ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (alert.eventType == "ENTERED") colorScheme.tertiary else colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${alert.eventType}: ${alert.zoneName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                val timeStr = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))
                                Text(
                                    text = "Recorded: $timeStr",
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
