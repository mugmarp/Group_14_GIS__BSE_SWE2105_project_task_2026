package com.geotrace.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.MapFocusTarget
import com.geotrace.model.MapTileType
import com.geotrace.model.SpeedUnit
import com.geotrace.ui.components.OfflineMapsDialog
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel

@Composable
fun SettingsScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: () -> Unit,
    onJumpToOfflineRegion: ((MapFocusTarget) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val settings by viewModel.settings.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val geofenceApiStatus by viewModel.geofenceApiStatus.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val offlineMapRegions by viewModel.offlineMapRegions.collectAsState()
    val offlineCacheSizeMb by viewModel.offlineCacheSizeMb.collectAsState()
    val currentMapBounds by viewModel.currentMapBoundingBox.collectAsState()
    var showOfflineMapsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshTileCacheSize()
    }

    val settingsBgPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.resyncGeofencingApi()
    }

    val settingsGeoPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.toggleGeofenceNotifications(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            settingsBgPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar / Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("settings_back_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Live Map",
                        tint = colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Telemetry & System Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                    Text(
                        text = "Hardware deadband, sensor units & cartographic overlays",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Units & Formats
        item {
            SettingsCard(
                title = "UNITS & COORDINATE FORMATS",
                icon = { Icon(Icons.Default.Speed, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.primary
            ) {
                // Speed Unit
                Text(
                    text = "Velocity Scalar Unit:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedUnit.values().forEach { unit ->
                        val selected = settings.speedUnit == unit
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateSpeedUnit(unit) },
                            label = { Text(unit.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(4.dp),
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
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Coordinate Format
                Text(
                    text = "Coordinate Presentation:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoordinateFormat.values().forEach { format ->
                        val selected = settings.coordinateFormat == format
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateCoordinateFormat(format) },
                            label = { Text(if (format == CoordinateFormat.DECIMAL) "DD (0.334°)" else "DMS (0°20'N)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(4.dp),
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
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 2: Cartographic Overlays
        item {
            SettingsCard(
                title = "CARTOGRAPHIC VIEWPORT & TILES",
                icon = { Icon(Icons.Default.Layers, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.secondary
            ) {
                Text(
                    text = "Map Tile Layer Source:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapTileType.values().forEach { tile ->
                        val selected = settings.mapTileType == tile
                        Row(
                            modifier = Modifier
                                 .fillMaxWidth()
                                 .background(if (selected) customColors.statusActiveBg else customColors.surfaceContainerLow, RoundedCornerShape(8.dp))
                                 .border(1.dp, if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                 .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tile.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) colorScheme.primary else colorScheme.onSurface
                                )
                                Text(
                                    text = tile.description,
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateMapTileType(tile) },
                                label = { Text(if (selected) "Active" else "Select", fontSize = 10.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary,
                                    selectedLabelColor = colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Offline Maps & Persistent Tile Cache Manager Row
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = customColors.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "Offline Maps",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Offline Maps & Tile Cache",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "${offlineMapRegions.size} saved regions • ${String.format(java.util.Locale.US, "%.1f", offlineCacheSizeMb)} / 512 MB",
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showOfflineMapsDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_open_offline_maps_btn")
                        ) {
                            Text("Manage", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle 1: Accuracy Circle
                SettingToggleRow(
                    title = "Draw Accuracy Confidence Ring",
                    subtitle = "Render transparent circular radius reflecting GPS error bounds",
                    checked = settings.showAccuracyRing,
                    onCheckedChange = { viewModel.toggleAccuracyRing(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle 2: Follow User
                SettingToggleRow(
                    title = "Auto-Center Map on Position Fix",
                    subtitle = "Automatically re-center map when new coordinates arrive",
                    checked = settings.followUserLocation,
                    onCheckedChange = { viewModel.toggleFollowUser(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle 3: Geofence Entry/Exit Notification Alerts (Off by default)
                SettingToggleRow(
                    title = "Geofence Perimeter Alerts",
                    subtitle = geofenceApiStatus.statusMessage,
                    checked = settings.geofenceNotificationEnabled,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            viewModel.toggleGeofenceNotifications(false)
                        } else {
                            val needed = mutableListOf<String>()
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                needed.add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (needed.isNotEmpty()) {
                                settingsGeoPermissionLauncher.launch(needed.toTypedArray())
                            } else {
                                viewModel.toggleGeofenceNotifications(true)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    settingsBgPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }
                            }
                        }
                    }
                )
            }
        }

        // Section 3: Hardware Jitter & Power
        item {
            SettingsCard(
                title = "HARDWARE JITTER & POWER MANAGEMENT",
                icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.tertiary
            ) {
                Text(
                    text = "Stationary Deadband Filter (Jitter Rejection):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Discards micro-movements below threshold when standing still to prevent distance drift.",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val thresholds = listOf(1.5f to "1.5m (Std)", 3.0f to "3.0m (High)", 0.0f to "Off (Raw)")
                    thresholds.forEach { (thresh, label) ->
                        val selected = settings.stationaryJitterThresholdMeters == thresh
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateJitterThreshold(thresh) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(4.dp),
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
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Live Jitter Rejection Metric Well (Academic Verification of Stationary Deadband)
                val jitterCount by viewModel.filteredJitterCount.collectAsState()
                val jitterMeters by viewModel.filteredJitterMeters.collectAsState()

                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = customColors.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ELIMINATED MULTIPATH DRIFT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurfaceVariant)
                            Text(
                                String.format(java.util.Locale.US, "%.1f m fake drift (%d spikes)", jitterMeters, jitterCount),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.tertiary
                            )
                        }
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colorScheme.tertiary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Deadband Active",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Keep Screen On
                SettingToggleRow(
                    title = "Keep Screen Active During Recording",
                    subtitle = "Prevents device sleep while tracking real-time movement outdoors",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
                )
            }
        }

        // Section 4: Cellular Positioning Engine (OpenCellID Uganda Live Tower Pinning)
        item {
            var apiKeyInput by androidx.compose.runtime.remember(settings.openCellIdApiKey) {
                androidx.compose.runtime.mutableStateOf(settings.openCellIdApiKey)
            }

            SettingsCard(
                title = "CELLULAR POSITIONING ENGINE (OPENCELLID)",
                icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.secondary
            ) {
                SettingToggleRow(
                    title = "Cell Tower Pinning on Map",
                    subtitle = "Plot live OpenCellID-resolved serving tower coordinates & coverage range when an API key is configured",
                    checked = settings.showCellTowerPin,
                    onCheckedChange = { viewModel.toggleCellTowerPin(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        viewModel.updateOpenCellIdApiKey(it)
                    },
                    label = { Text("OpenCellID API Key (Required for Live Pin)", fontSize = 11.sp) },
                    placeholder = {
                        Text(
                            if (com.geotrace.BuildConfig.OPENCELLID_API_KEY.isNotBlank()) "CI/CD Secret Key Active (Auto)" else "Enter OpenCellID API key to resolve mast coordinates",
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "GeoTrace reads genuine radio IDs (CID, LAC/TAC, MCC, MNC, dBm) from device hardware TelephonyManager. Configure an OpenCellID API key to plot resolved physical tower coordinates on the map.",
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }

        // Section 5: In-App Updates & CI/CD Engine
        item {
            val updateStatus by viewModel.updateStatus.collectAsState()
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            SettingsCard(
                title = "APPLICATION UPDATES & CI/CD RELEASES",
                icon = { Icon(Icons.Default.Power, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Installed Version: v${com.geotrace.BuildConfig.VERSION_NAME} (${com.geotrace.BuildConfig.VERSION_CODE})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Source: github.com/${com.geotrace.service.AppUpdateManager.GITHUB_REPO}",
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.appUpdateManager.checkForUpdates()
                            }
                        },
                        enabled = updateStatus !is com.geotrace.service.UpdateStatus.Checking,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("check_updates_btn")
                    ) {
                        if (updateStatus is com.geotrace.service.UpdateStatus.Checking) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Check Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Status details card
                when (val status = updateStatus) {
                    is com.geotrace.service.UpdateStatus.UpdateAvailable -> {
                        val sizeMb = if (status.info.apkSizeBytes > 0L) {
                            String.format(java.util.Locale.US, " • %.1f MB", status.info.apkSizeBytes / (1024.0 * 1024.0))
                        } else ""
                        Spacer(modifier = Modifier.height(10.dp))
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.primary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Update Available: ${status.info.tagName}$sizeMb",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = status.info.body,
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                Button(
                                    onClick = {
                                        viewModel.appUpdateManager.startDownload(status.info)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("download_update_btn")
                                ) {
                                    Text("Download & Install (${status.info.apkFileName})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    is com.geotrace.service.UpdateStatus.UpToDate -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (status.latestTag != null) {
                                "GeoTrace is up to date (latest GitHub release: ${status.latestTag})."
                            } else {
                                "GeoTrace is on the latest official release."
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.tertiary
                        )
                    }
                    is com.geotrace.service.UpdateStatus.NoReleasesPublished -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No GitHub releases have been published yet for this repository (running local build v${com.geotrace.BuildConfig.VERSION_NAME}).",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    is com.geotrace.service.UpdateStatus.Downloading -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Downloading release APK via Android DownloadManager...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary
                        )
                    }
                    is com.geotrace.service.UpdateStatus.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.appUpdateManager.installApk(status.apkFile) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("install_apk_btn")
                        ) {
                            Text("Install Downloaded APK Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is com.geotrace.service.UpdateStatus.InstallPermissionRequired -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Allow \"Install unknown apps\" for GeoTrace in Android Settings, then tap Install APK.",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { viewModel.appUpdateManager.openInstallPermissionSettings() },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Open Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.appUpdateManager.installApk(status.apkFile) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Install APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    is com.geotrace.service.UpdateStatus.Error -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = status.message,
                            fontSize = 11.sp,
                            color = colorScheme.error
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { viewModel.appUpdateManager.openReleasesPage() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_releases_page_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open GitHub Releases in Browser",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open GitHub Releases in Browser", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Section 6: Mission Telemetry Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "GEOTRACE SAFE TELEMETRY CORE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                        color = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Positioning Engine: FusedLocationProviderClient (GNSS Multi-Constellation)\n" +
                               "• Cellular Telemetry: TelephonyManager Radio Layer + OpenCellID\n" +
                               "• Release Channel: GitHub Releases (${com.geotrace.service.AppUpdateManager.GITHUB_REPO})\n" +
                               "• Persistence: Local SQLite + Firebase Firestore (Multi-Device Cloud Sync)\n" +
                               "• Active Recording Session: ${activeSession?.name ?: "Idle (Standby Mode)"}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    if (showOfflineMapsDialog) {
        OfflineMapsDialog(
            viewModel = viewModel,
            currentLocation = currentLocation,
            currentMapBounds = currentMapBounds,
            onJumpToRegion = { _, target ->
                viewModel.toggleFollowUser(false)
                onJumpToOfflineRegion?.invoke(target)
                onNavigateBack()
            },
            onDismiss = { showOfflineMapsDialog = false }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: @Composable () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(accentColor))
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                        color = colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.onPrimary,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.outline,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}
