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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geotrace.BuildConfig
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.MapFocusTarget
import com.geotrace.service.LocationForegroundService
import com.geotrace.service.UpdateStatus
import com.geotrace.ui.components.GeoTraceTopBar
import com.geotrace.ui.components.OfflineMapsDialog
import com.geotrace.ui.components.ScreenListContentPadding
import com.geotrace.ui.components.StatusBadgeTone
import com.geotrace.ui.components.adaptiveContentWidth
import com.geotrace.ui.screens.devices.BackgroundLocationSettingsDialog
import com.geotrace.ui.screens.devices.hasBackgroundLocationPermission
import com.geotrace.ui.screens.devices.openAppLocationSettings
import com.geotrace.ui.screens.settings.CellularEngineSettingsPage
import com.geotrace.ui.screens.settings.MapLayersSettingsPage
import com.geotrace.ui.screens.settings.MotionFilteringSettingsPage
import com.geotrace.ui.screens.settings.ProtectionAlertsSettingsPage
import com.geotrace.ui.screens.settings.SettingsCategory
import com.geotrace.ui.screens.settings.SettingsSubgroupHubCard
import com.geotrace.ui.screens.settings.UnitsDisplaySettingsPage
import com.geotrace.ui.screens.settings.UpdatesInfoSettingsPage
import com.geotrace.viewmodel.GroupViewModel
import com.geotrace.viewmodel.TrackerViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: () -> Unit,
    groupViewModel: GroupViewModel? = null,
    onJumpToOfflineRegion: ((MapFocusTarget) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val settings by viewModel.settings.collectAsState()
    val geofenceApiStatus by viewModel.geofenceApiStatus.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val offlineMapRegions by viewModel.offlineMapRegions.collectAsState()
    val offlineCacheSizeMb by viewModel.offlineCacheSizeMb.collectAsState()
    val currentMapBounds by viewModel.currentMapBoundingBox.collectAsState()

    var activeCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var showOfflineMapsDialog by remember { mutableStateOf(false) }
    var showBgSettingsDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (activeCategory != null) {
            activeCategory = null
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshTileCacheSize()
    }

    val settingsBgPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && !hasBackgroundLocationPermission(context)) {
            showBgSettingsDialog = true
        }
        viewModel.resyncGeofencingApi()
    }

    val settingsGeoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.toggleGeofenceNotifications(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasBackgroundLocationPermission(context)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showBgSettingsDialog = true
            } else {
                settingsBgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
                groupViewModel?.ensureProtectionStartedIfEligible(refreshNotification = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val protectionBgPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefreshTick++
        if (!granted && !hasBackgroundLocationPermission(context)) {
            showBgSettingsDialog = true
        }
    }

    val protectionPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionRefreshTick++
        val hasLocation = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) ||
            LocationForegroundService.hasLocationPermission(context)
        if (hasLocation) {
            groupViewModel?.toggleBackgroundProtection(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasBackgroundLocationPermission(context)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    showBgSettingsDialog = true
                } else {
                    protectionBgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            Toast.makeText(
                context,
                "Location permission is required for 24/7 Background Device Protection.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    AnimatedContent(
        targetState = activeCategory,
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
        label = "settings_category_transition"
    ) { selectedCategory ->
        when (selectedCategory) {
            null -> {
                val isProtectionEnabled = groupViewModel?.isBackgroundProtectionEnabled?.collectAsState()?.value ?: false
                val isProtectionPaused = groupViewModel?.isProtectionPaused?.collectAsState()?.value ?: false
                val isProtectionRunning = groupViewModel?.isProtectionServiceRunning?.collectAsState()?.value ?: false
                val updateStatus by viewModel.updateStatus.collectAsState()

                val coordBadge = if (settings.coordinateFormat == CoordinateFormat.DECIMAL) "DD" else "DMS"
                val deadbandBadge = when (settings.stationaryJitterThresholdMeters) {
                    0f -> "Raw"
                    else -> "${settings.stationaryJitterThresholdMeters}m"
                }
                val cellKeyBadge = when {
                    settings.openCellIdApiKey.isNotBlank() -> "Override"
                    BuildConfig.OPENCELLID_API_KEY.isNotBlank() -> "Secret Active"
                    else -> "Fallback"
                }
                val cellKeyTone = when {
                    settings.openCellIdApiKey.isNotBlank() -> StatusBadgeTone.ACTIVE
                    BuildConfig.OPENCELLID_API_KEY.isNotBlank() -> StatusBadgeTone.NOMINAL
                    else -> StatusBadgeTone.WARNING
                }
                val protectionBadge = when {
                    !isProtectionEnabled -> "OFF"
                    isProtectionPaused -> "Paused"
                    isProtectionRunning -> "Active"
                    else -> "Standby"
                }
                val protectionTone = when {
                    !isProtectionEnabled -> StatusBadgeTone.NEUTRAL
                    isProtectionPaused -> StatusBadgeTone.WARNING
                    isProtectionRunning -> StatusBadgeTone.NOMINAL
                    else -> StatusBadgeTone.NEUTRAL
                }
                val updateBadge = when (updateStatus) {
                    is UpdateStatus.UpdateAvailable -> "Update Ready"
                    else -> "v${BuildConfig.VERSION_NAME}"
                }
                val updateTone = when (updateStatus) {
                    is UpdateStatus.UpdateAvailable -> StatusBadgeTone.ACTIVE
                    else -> StatusBadgeTone.NEUTRAL
                }

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
                                title = "Telemetry & System Settings",
                                subtitle = "Choose a category below to configure preferences",
                                backContentDescription = "Back to Live Map",
                                backTestTag = "settings_back_btn",
                                onBack = onNavigateBack
                            )
                        }

                    item {
                        SettingsSubgroupHubCard(
                            title = SettingsCategory.UNITS_DISPLAY.title,
                            subtitle = SettingsCategory.UNITS_DISPLAY.subtitle,
                            badgeText = "${settings.speedUnit.label} • $coordBadge",
                            badgeTone = StatusBadgeTone.ACTIVE,
                            iconTint = colorScheme.primary,
                            icon = Icons.Default.Speed,
                            testTag = "settings_group_units_display",
                            onClick = { activeCategory = SettingsCategory.UNITS_DISPLAY }
                        )
                    }

                    item {
                        SettingsSubgroupHubCard(
                            title = SettingsCategory.MAP_LAYERS.title,
                            subtitle = SettingsCategory.MAP_LAYERS.subtitle,
                            badgeText = "${settings.mapTileType.label.substringBefore(" ")} • ${String.format(Locale.US, "%.1f", offlineCacheSizeMb)} MB",
                            badgeTone = StatusBadgeTone.ACTIVE,
                            iconTint = colorScheme.secondary,
                            icon = Icons.Default.Layers,
                            testTag = "settings_group_map_layers",
                            onClick = { activeCategory = SettingsCategory.MAP_LAYERS }
                        )
                    }

                    item {
                        SettingsSubgroupHubCard(
                            title = SettingsCategory.MOTION_FILTERING.title,
                            subtitle = SettingsCategory.MOTION_FILTERING.subtitle,
                            badgeText = deadbandBadge,
                            badgeTone = if (settings.stationaryJitterThresholdMeters == 0f) StatusBadgeTone.NEUTRAL else StatusBadgeTone.NOMINAL,
                            iconTint = colorScheme.tertiary,
                            icon = Icons.Default.Tune,
                            testTag = "settings_group_motion_filtering",
                            onClick = { activeCategory = SettingsCategory.MOTION_FILTERING }
                        )
                    }

                    item {
                        SettingsSubgroupHubCard(
                            title = SettingsCategory.CELLULAR_ENGINE.title,
                            subtitle = SettingsCategory.CELLULAR_ENGINE.subtitle,
                            badgeText = cellKeyBadge,
                            badgeTone = cellKeyTone,
                            iconTint = colorScheme.secondary,
                            icon = Icons.Default.CellTower,
                            testTag = "settings_group_cellular_engine",
                            onClick = { activeCategory = SettingsCategory.CELLULAR_ENGINE }
                        )
                    }

                    item {
                        SettingsSubgroupHubCard(
                            title = SettingsCategory.PROTECTION_ALERTS.title,
                            subtitle = SettingsCategory.PROTECTION_ALERTS.subtitle,
                            badgeText = protectionBadge,
                            badgeTone = protectionTone,
                            iconTint = colorScheme.tertiary,
                            icon = Icons.Default.Security,
                            testTag = "settings_group_protection_alerts",
                            onClick = { activeCategory = SettingsCategory.PROTECTION_ALERTS }
                        )
                    }

                    item {
                        SettingsSubgroupHubCard(
                            title = SettingsCategory.UPDATES_INFO.title,
                            subtitle = SettingsCategory.UPDATES_INFO.subtitle,
                            badgeText = updateBadge,
                            badgeTone = updateTone,
                            iconTint = colorScheme.primary,
                            icon = Icons.Default.SystemUpdate,
                            testTag = "settings_group_updates_info",
                            onClick = { activeCategory = SettingsCategory.UPDATES_INFO }
                        )
                    }
                    }
                }
            }

            SettingsCategory.UNITS_DISPLAY -> UnitsDisplaySettingsPage(
                viewModel = viewModel,
                settings = settings,
                onBack = { activeCategory = null }
            )

            SettingsCategory.MAP_LAYERS -> MapLayersSettingsPage(
                viewModel = viewModel,
                settings = settings,
                offlineMapRegions = offlineMapRegions,
                offlineCacheSizeMb = offlineCacheSizeMb,
                onOpenOfflineMaps = { showOfflineMapsDialog = true },
                onBack = { activeCategory = null }
            )

            SettingsCategory.MOTION_FILTERING -> MotionFilteringSettingsPage(
                viewModel = viewModel,
                settings = settings,
                onBack = { activeCategory = null }
            )

            SettingsCategory.CELLULAR_ENGINE -> CellularEngineSettingsPage(
                viewModel = viewModel,
                settings = settings,
                onBack = { activeCategory = null }
            )

            SettingsCategory.PROTECTION_ALERTS -> ProtectionAlertsSettingsPage(
                viewModel = viewModel,
                groupViewModel = groupViewModel,
                settings = settings,
                geofenceApiStatus = geofenceApiStatus,
                permissionRefreshTick = permissionRefreshTick,
                onRequestGeofencePermissions = {
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
                        settingsGeoPermissionLauncher.launch(needed.toTypedArray())
                    }
                },
                onRequestProtectionPermissions = {
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
                        protectionPermissionsLauncher.launch(needed.toTypedArray())
                    } else {
                        groupViewModel?.toggleBackgroundProtection(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission(context)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                showBgSettingsDialog = true
                            } else {
                                protectionBgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        }
                    }
                },
                onRequestBgLocationDialog = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        showBgSettingsDialog = true
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        protectionBgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
                onBack = { activeCategory = null }
            )

            SettingsCategory.UPDATES_INFO -> UpdatesInfoSettingsPage(
                viewModel = viewModel,
                onBack = { activeCategory = null }
            )
        }
    }

    if (showOfflineMapsDialog) {
        OfflineMapsDialog(
            viewModel = viewModel,
            currentLocation = currentLocation,
            currentMapBounds = currentMapBounds,
            onJumpToRegion = { _, target ->
                viewModel.clearInspectedTrail()
                onJumpToOfflineRegion?.invoke(target)
                onNavigateBack()
            },
            onDismiss = { showOfflineMapsDialog = false }
        )
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
}
