package com.geotrace.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.ContextCompat
import com.geotrace.BuildConfig
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineMapRegion
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackerSettings
import com.geotrace.service.GeofenceApiStatus
import com.geotrace.service.LocationForegroundService
import com.geotrace.service.UpdateStatus
import com.geotrace.ui.screens.devices.hasBackgroundLocationPermission
import com.geotrace.ui.screens.devices.openAppNotificationSettings
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.GroupViewModel
import com.geotrace.viewmodel.TrackerViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun UnitsDisplaySettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsHeaderBar(
                title = SettingsCategory.UNITS_DISPLAY.title,
                subtitle = SettingsCategory.UNITS_DISPLAY.subtitle,
                backContentDescription = "Back to Settings Hub",
                backTestTag = "settings_sub_back_btn",
                onBack = onBack
            )
        }

        item {
            SettingsSectionCard(
                title = "Units & Coordinate Formats",
                icon = Icons.Default.Speed
            ) {
                Text(
                    text = "Velocity Scalar Unit:",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedUnit.entries.forEach { unit ->
                        val isSelected = settings.speedUnit == unit
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colorScheme.primary else colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.updateSpeedUnit(unit) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    text = unit.label,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Coordinate Presentation:",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoordinateFormat.entries.forEach { fmt ->
                        val isSelected = settings.coordinateFormat == fmt
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colorScheme.primary else colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.updateCoordinateFormat(fmt) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
                                Text(
                                    text = if (fmt == CoordinateFormat.DECIMAL) "DD (0.334°)" else "DMS (0°20'N)",
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Hardware Screen Behavior",
                icon = Icons.Default.Power
            ) {
                SettingToggleRow(
                    title = "Keep Screen Active During Recording",
                    description = "Prevents display sleep during active field tracking sessions",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (settings.keepScreenOn) {
                            "Wake lock active: live HUD stays illuminated while recording a trail."
                        } else {
                            "System timeout active: screen sleeps normally while background GPS continues."
                        },
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun MapLayersSettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    offlineMapRegions: List<OfflineMapRegion>,
    offlineCacheSizeMb: Float,
    onOpenOfflineMaps: () -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsHeaderBar(
                title = SettingsCategory.MAP_LAYERS.title,
                subtitle = SettingsCategory.MAP_LAYERS.subtitle,
                backContentDescription = "Back to Settings Hub",
                backTestTag = "settings_sub_back_btn",
                onBack = onBack
            )
        }

        item {
            SettingsSectionCard(
                title = "Cartographic Tile Layer Sources",
                icon = Icons.Default.Layers
            ) {
                Text(
                    text = "Select the active base map renderer for the live map viewport:",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                MapTileType.entries.forEach { tileType ->
                    val isSelected = settings.mapTileType == tileType
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) colorScheme.primary.copy(alpha = 0.09f) else customColors.surfaceContainerLow,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.updateMapTileType(tileType) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tileType.label,
                                    color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tileType.description,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colorScheme.primary else colorScheme.surface,
                                border = if (isSelected) null else BorderStroke(1.dp, colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = if (isSelected) "Active" else "Select",
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Offline Maps & Viewport Overlays",
                icon = Icons.Default.CloudDownload
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = customColors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Offline Maps & Tile Cache",
                                    color = colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${offlineMapRegions.size} saved regions • ${String.format(Locale.US, "%.1f", offlineCacheSizeMb)} / 512 MB",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onOpenOfflineMaps,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_open_offline_maps_btn")
                        ) {
                            Text("Manage", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SettingToggleRow(
                    title = "Draw Accuracy Confidence Ring",
                    description = "Circular 1-sigma DOP confidence halo around live fix",
                    checked = settings.showAccuracyRing,
                    onCheckedChange = { viewModel.toggleAccuracyRing(it) }
                )

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                SettingToggleRow(
                    title = "Auto-Center Camera on Live Telemetry",
                    description = "Smoothly pans map viewport as device updates position",
                    checked = settings.followUserLocation,
                    onCheckedChange = { viewModel.toggleFollowUser(it) }
                )
            }
        }
    }
}

@Composable
internal fun MotionFilteringSettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val jitterCount by viewModel.filteredJitterCount.collectAsState()
    val jitterMeters by viewModel.filteredJitterMeters.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsHeaderBar(
                title = SettingsCategory.MOTION_FILTERING.title,
                subtitle = SettingsCategory.MOTION_FILTERING.subtitle,
                backContentDescription = "Back to Settings Hub",
                backTestTag = "settings_sub_back_btn",
                onBack = onBack
            )
        }

        item {
            SettingsSectionCard(
                title = "Motion Thresholds & Jitter Filtering",
                icon = Icons.Default.Tune
            ) {
                Text(
                    text = "Stationary Deadband Filter (${settings.stationaryJitterThresholdMeters}m):",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        1.5f to "1.5m (Balanced)",
                        3.0f to "3.0m (Aggressive)",
                        0f to "Off (Raw)"
                    ).forEach { (threshold, label) ->
                        val isSelected = settings.stationaryJitterThresholdMeters == threshold
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colorScheme.primary else colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.updateJitterThreshold(threshold) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = customColors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ELIMINATED MULTIPATH DRIFT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered Stationary Spikes:",
                                fontSize = 12.sp,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "$jitterCount spikes (${String.format(Locale.US, "%.1f", jitterMeters)} m)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Suppresses sub-threshold GPS wandering while stationary indoors or near tall buildings without losing genuine movement.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CellularEngineSettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    var apiKeyInput by remember(settings.openCellIdApiKey) {
        mutableStateOf(settings.openCellIdApiKey)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsHeaderBar(
                title = SettingsCategory.CELLULAR_ENGINE.title,
                subtitle = SettingsCategory.CELLULAR_ENGINE.subtitle,
                backContentDescription = "Back to Settings Hub",
                backTestTag = "settings_sub_back_btn",
                onBack = onBack
            )
        }

        item {
            SettingsSectionCard(
                title = "Cellular Positioning & OpenCellID",
                icon = Icons.Default.CellTower
            ) {
                SettingToggleRow(
                    title = "Show Serving Cell Tower Pin on Map",
                    description = "Renders approximate serving mast and radius ring when GPS lock drops",
                    checked = settings.showCellTowerPin,
                    onCheckedChange = { viewModel.toggleCellTowerPin(it) }
                )

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                Text(
                    text = "OpenCellID API Token (MCC / MNC / LAC / CID Lookup)",
                    color = colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Paste OpenCellID key") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opencellid_api_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.updateOpenCellIdApiKey(apiKeyInput)
                            Toast.makeText(context, "OpenCellID API key saved & refreshing", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_opencellid_key_btn")
                    ) {
                        Text("Save Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            apiKeyInput = ""
                            viewModel.updateOpenCellIdApiKey("")
                            Toast.makeText(context, "Cleared OpenCellID key", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("clear_opencellid_key_btn")
                    ) {
                        Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val statusLabel = when {
                    settings.openCellIdApiKey.isNotBlank() ->
                        "Active source: Custom OpenCellID Key (${settings.openCellIdApiKey.take(6)}…)"
                    BuildConfig.OPENCELLID_API_KEY.isNotBlank() ->
                        "Active source: Build CI Key configured (local TelephonyManager fallback enabled)"
                    else ->
                        "No OpenCellID key set — using on-device TelephonyManager tower IDs & local trilateration fallback."
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProtectionAlertsSettingsPage(
    viewModel: TrackerViewModel,
    groupViewModel: GroupViewModel?,
    settings: TrackerSettings,
    geofenceApiStatus: GeofenceApiStatus,
    permissionRefreshTick: Int,
    onRequestGeofencePermissions: () -> Unit,
    onRequestProtectionPermissions: () -> Unit,
    onRequestBgLocationDialog: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val hasLocPerm = remember(permissionRefreshTick) { LocationForegroundService.hasLocationPermission(context) }
    val hasNotifPerm = remember(permissionRefreshTick) { LocationForegroundService.hasNotificationPermission(context) }
    val hasBgPerm = remember(permissionRefreshTick) { hasBackgroundLocationPermission(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsHeaderBar(
                title = SettingsCategory.PROTECTION_ALERTS.title,
                subtitle = SettingsCategory.PROTECTION_ALERTS.subtitle,
                backContentDescription = "Back to Settings Hub",
                backTestTag = "settings_sub_back_btn",
                onBack = onBack
            )
        }

        item {
            SettingsSectionCard(
                title = "24/7 Background Protection",
                icon = Icons.Default.Security
            ) {
                if (groupViewModel == null) {
                    Text(
                        text = "Sign in on the Devices tab to arm 24/7 anti-theft device sync and remote alarm controls.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    val isProtectionEnabled by groupViewModel.isBackgroundProtectionEnabled.collectAsState()
                    val isProtectionRunning by groupViewModel.isProtectionServiceRunning.collectAsState()
                    val isProtectionPaused by groupViewModel.isProtectionPaused.collectAsState()
                    val currentUser by groupViewModel.currentUser.collectAsState()

                    SettingToggleRow(
                        title = "Enable 24/7 Device Protection",
                        description = when {
                            !isProtectionEnabled -> "Only syncs location & Remote Ring while app is open"
                            currentUser == null -> "Armed — sign in on Devices tab to activate cloud heartbeat"
                            isProtectionPaused -> "Paused — background telemetry stopped until resumed"
                            !isProtectionRunning -> "Standby — tap Start below or grant Location permission"
                            !hasNotifPerm -> "Running — allow Notifications for persistent status bar badge"
                            !hasBgPerm -> "Active — grant 'Allow all the time' location for reboot auto-start"
                            else -> "Active — persistent notification, cloud heartbeat & Remote Ring running"
                        },
                        checked = isProtectionEnabled,
                        onCheckedChange = { enable ->
                            if (enable && !hasLocPerm) {
                                onRequestProtectionPermissions()
                            } else {
                                groupViewModel.toggleBackgroundProtection(enable)
                                if (enable && !hasBgPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    onRequestBgLocationDialog()
                                }
                            }
                        },
                        testTag = "settings_background_protection_switch"
                    )

                    if (isProtectionEnabled && currentUser != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (isProtectionPaused || !isProtectionRunning) {
                            FilledTonalButton(
                                onClick = { groupViewModel.resumeBackgroundProtection() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_resume_protection_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isProtectionPaused) "Resume 24/7 Protection" else "Start 24/7 Service",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { groupViewModel.pauseBackgroundProtection() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_pause_protection_btn")
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause 24/7 Protection", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isProtectionEnabled && !hasNotifPerm) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openAppNotificationSettings(context) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_fix_notif_btn")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow Notifications for Status Badge", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isProtectionEnabled && !hasBgPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRequestBgLocationDialog,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Grant 'Allow All The Time' Location", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Geofence Perimeter Alerts",
                icon = Icons.Default.NotificationsActive
            ) {
                SettingToggleRow(
                    title = "Enable Academic & Safe Zone Alerts",
                    description = geofenceApiStatus.statusMessage,
                    checked = settings.geofenceNotificationEnabled,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            viewModel.toggleGeofenceNotifications(false)
                        } else {
                            val missingFine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                            val missingNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            if (missingFine || missingNotif) {
                                onRequestGeofencePermissions()
                            } else {
                                viewModel.toggleGeofenceNotifications(true)
                                if (!hasBgPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    onRequestBgLocationDialog()
                                }
                            }
                        }
                    }
                )

                geofenceApiStatus.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(customColors.warning.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = err,
                            color = customColors.warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpdatesInfoSettingsPage(
    viewModel: TrackerViewModel,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val updateStatus by viewModel.updateStatus.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsHeaderBar(
                title = SettingsCategory.UPDATES_INFO.title,
                subtitle = SettingsCategory.UPDATES_INFO.subtitle,
                backContentDescription = "Back to Settings Hub",
                backTestTag = "settings_sub_back_btn",
                onBack = onBack
            )
        }

        item {
            SettingsSectionCard(
                title = "App Version & GitHub Releases",
                icon = Icons.Default.SystemUpdate
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed Version",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            color = colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { scope.launch { viewModel.appUpdateManager.checkForUpdates() } },
                        enabled = updateStatus !is UpdateStatus.Checking && updateStatus !is UpdateStatus.Downloading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("check_updates_btn")
                    ) {
                        if (updateStatus is UpdateStatus.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...", fontSize = 12.sp)
                        } else {
                            Text("Check for Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (val status = updateStatus) {
                    is UpdateStatus.Idle -> {
                        Text(
                            text = "Tap Check for Updates to query official GeoTrace GitHub Releases.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateStatus.Checking -> {
                        Text(
                            text = "Querying GitHub release metadata...",
                            fontSize = 11.sp,
                            color = colorScheme.primary
                        )
                    }
                    is UpdateStatus.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.tertiary.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "You are on the latest release (${status.latestTag ?: "v${BuildConfig.VERSION_NAME}"}).",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.tertiary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is UpdateStatus.NoReleasesPublished -> {
                        Text(
                            text = "No public GitHub release tags published yet.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateStatus.UpdateAvailable -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = customColors.surfaceContainerLow),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Update Available: ${status.info.tagName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = status.info.body,
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 4
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.appUpdateManager.startDownload(status.info) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("download_update_btn")
                                ) {
                                    Text("Download APK (${status.info.apkFileName})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading update...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = "${status.progressPercent}%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { status.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.appUpdateManager.cancelDownload() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("cancel_update_download_btn")
                            ) {
                                Text("Cancel Download", fontSize = 11.sp)
                            }
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Button(
                            onClick = { viewModel.appUpdateManager.installApk(status.apkFile) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.tertiary,
                                contentColor = colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("install_apk_btn")
                        ) {
                            Text("Install Downloaded APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateStatus.InstallPermissionRequired -> {
                        Button(
                            onClick = { viewModel.appUpdateManager.openInstallPermissionSettings() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Allow Unknown Apps to Install APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateStatus.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = status.message,
                                fontSize = 11.sp,
                                color = colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { viewModel.appUpdateManager.openReleasesPage() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("open_releases_page_btn")
                ) {
                    Text("Open Releases Page in Browser", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Safe Telemetry Core Diagnostics",
                icon = Icons.Default.Info
            ) {
                Text(
                    text = "Engine: Fused + GNSS + Cellular Fallback (No Mock Injection)\n" +
                        "Offline Storage: SQLite Room Local Ledger + OSMDroid Cache\n" +
                        "Privacy & Security: Google Sign-In Gate + Zero-Trust Cloud Rules",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
