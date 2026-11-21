package com.geotrace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Group as OutlinedGroup
import androidx.compose.material.icons.outlined.Map as OutlinedMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.NotificationsActive
import com.geotrace.model.AppThemeMode
import com.geotrace.ui.screens.CellularDiagnosticScreen
import com.geotrace.ui.screens.GeofenceScreen
import com.geotrace.ui.screens.GroupScreen
import com.geotrace.ui.screens.HistoryScreen
import com.geotrace.ui.screens.MapScreen
import com.geotrace.ui.screens.ResearchAnalysisScreen
import com.geotrace.ui.screens.SettingsScreen
import com.geotrace.ui.theme.GISTrackerTheme
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.GroupViewModel
import com.geotrace.viewmodel.TrackerViewModel
import kotlinx.coroutines.launch

enum class Screen(val title: String, val testTag: String) {
    MAP("Live Map & Telemetry", "nav_map"),
    CELLULAR("Cellular Signal Hub", "nav_cellular"),
    GROUPS("Devices & Tracking Groups", "nav_groups"),
    GEOFENCE("Safe Zones & Geofencing", "nav_geofence"),
    HISTORY("Saved Trails & Exports", "nav_history"),
    RESEARCH("SWE2105 Research Analysis", "nav_research"),
    SETTINGS("Telemetry Settings", "nav_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: TrackerViewModel by viewModels()
    private val groupViewModel: GroupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                viewModel.setUiForeground(true)
                groupViewModel.setUiForeground(true)
            }

            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                viewModel.setUiForeground(false)
                groupViewModel.setUiForeground(false)
            }
        })
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            val themeSettings by viewModel.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeSettings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDark
            }
            LaunchedEffect(useDarkTheme) {
                val barStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }
            GISTrackerTheme(darkTheme = useDarkTheme) {
                MainAppScreen(viewModel = viewModel, groupViewModel = groupViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: TrackerViewModel,
    groupViewModel: GroupViewModel
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(Screen.MAP) }
    var showMenuSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentLocation by viewModel.currentLocation.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    val cellTowers by viewModel.cellTowers.collectAsState()
    val geofenceZones by viewModel.geofenceZones.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val activeGroup by groupViewModel.activeGroup.collectAsState()
    val isRingingAlarm by groupViewModel.isRingingAlarm.collectAsState()
    val ringCallerName by groupViewModel.ringCallerName.collectAsState()

    // Handle Back Press: Close menu bottom sheet if open, cancel geofence pick if active, or return to Map screen
    val isPickingGeofenceCenter by viewModel.isPickingGeofenceCenter.collectAsState()
    if (showMenuSheet) {
        BackHandler {
            scope.launch {
                try {
                    sheetState.hide()
                } finally {
                    showMenuSheet = false
                }
            }
        }
    } else if (currentScreen == Screen.MAP && isPickingGeofenceCenter) {
        BackHandler {
            groupViewModel.clearMapFocusTarget()
            viewModel.cancelMapPick()
            currentScreen = Screen.GEOFENCE
        }
    } else if (currentScreen != Screen.MAP) {
        BackHandler {
            currentScreen = Screen.MAP
        }
    }

    // Background Location Permission Flow (Android 10+)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Foreground Permission Request Flow
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else false
        val hasAnyLocation = fineGranted || coarseGranted ||
            com.geotrace.service.LocationForegroundService.hasLocationPermission(context)

        if (hasAnyLocation && (fineGranted || coarseGranted || postNotifGranted)) {
            scope.launch {
                viewModel.awaitInitialRestore()
                val restored = viewModel.activeSession.value
                if (restored != null) {
                    viewModel.stopStandbyLocationUpdates()
                    if (!com.geotrace.service.LocationForegroundService.isServiceActive) {
                        if (restored.isPaused) {
                            com.geotrace.service.LocationForegroundService.startServicePaused(context, restored.trackingMode)
                        } else {
                            com.geotrace.service.LocationForegroundService.startService(context, restored.trackingMode)
                        }
                    }
                } else if (!viewModel.hasActiveStandbyUpdates()) {
                    viewModel.startStandbyLocationUpdates()
                }
                if (fineGranted || coarseGranted) {
                    viewModel.refreshCellTowers()
                }
                groupViewModel.ensureProtectionStartedIfEligible(refreshNotification = true)
            }
            if ((fineGranted || coarseGranted) && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            viewModel.awaitInitialRestore()
            val restored = viewModel.activeSession.value
            if (restored != null) {
                viewModel.stopStandbyLocationUpdates()
                if (!com.geotrace.service.LocationForegroundService.isServiceActive) {
                    if (restored.isPaused) {
                        com.geotrace.service.LocationForegroundService.startServicePaused(context, restored.trackingMode)
                    } else {
                        com.geotrace.service.LocationForegroundService.startService(context, restored.trackingMode)
                    }
                }
            } else if (!viewModel.hasActiveStandbyUpdates()) {
                viewModel.startStandbyLocationUpdates()
            }
            viewModel.refreshCellTowers()
            groupViewModel.ensureProtectionStartedIfEligible(refreshNotification = true)
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        // Compact 48dp Bottom Bar (Map & Devices) — height strictly preserved, crisp clipped 24dp top-border radius
        bottomBar = {
            if (currentScreen == Screen.MAP || currentScreen == Screen.GROUPS) {
                val navShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                val navTopBorderColor = colorScheme.outlineVariant.copy(alpha = 0.7f)
                Surface(
                    shape = navShape,
                    color = colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(navShape)
                        .drawWithContent {
                            drawContent()
                            val radiusPx = 24.dp.toPx()
                            val strokePx = 1.dp.toPx()
                            val halfStroke = strokePx / 2f
                            val fadeEndY = radiusPx + 12.dp.toPx()
                            val topBorderPath = Path().apply {
                                moveTo(halfStroke, fadeEndY)
                                lineTo(halfStroke, radiusPx)
                                arcTo(
                                    rect = Rect(
                                        left = halfStroke,
                                        top = halfStroke,
                                        right = radiusPx * 2f - halfStroke,
                                        bottom = radiusPx * 2f - halfStroke
                                    ),
                                    startAngleDegrees = 180f,
                                    sweepAngleDegrees = 90f,
                                    forceMoveTo = false
                                )
                                lineTo(size.width - radiusPx, halfStroke)
                                arcTo(
                                    rect = Rect(
                                        left = size.width - radiusPx * 2f + halfStroke,
                                        top = halfStroke,
                                        right = size.width - halfStroke,
                                        bottom = radiusPx * 2f - halfStroke
                                    ),
                                    startAngleDegrees = 270f,
                                    sweepAngleDegrees = 90f,
                                    forceMoveTo = false
                                )
                                lineTo(size.width - halfStroke, fadeEndY)
                            }
                            drawPath(
                                path = topBorderPath,
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to navTopBorderColor,
                                        (radiusPx / fadeEndY) to navTopBorderColor.copy(alpha = 0.35f),
                                        1.0f to Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = fadeEndY
                                ),
                                style = Stroke(width = strokePx)
                            )
                        }
                        .testTag("bottom_nav_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val navTabs = listOf(
                            Triple(Screen.MAP, "Map", Icons.Default.Map to Icons.Outlined.OutlinedMap),
                            Triple(Screen.GROUPS, "Devices", Icons.Default.Group to Icons.Outlined.OutlinedGroup)
                        )
                        navTabs.forEach { (screen, label, icons) ->
                            val selected = currentScreen == screen
                            val (filledIcon, outlinedIcon) = icons
                            val contentTint = if (selected) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable { currentScreen = screen }
                                    .testTag(if (screen == Screen.MAP) "nav_bottom_map" else "nav_bottom_groups"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 18.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selected) filledIcon else outlinedIcon,
                                        contentDescription = if (screen == Screen.MAP) "Map" else "Devices and Tracking Groups",
                                        tint = contentTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = 0.02.sp,
                                        color = contentTint,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val mapBottomOverlayPadding = innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = if (currentScreen == Screen.MAP) 0.dp else mapBottomOverlayPadding
                )
        ) {
            when (currentScreen) {
                Screen.MAP -> MapScreen(
                    viewModel = viewModel,
                    groupViewModel = groupViewModel,
                    bottomOverlayPadding = mapBottomOverlayPadding,
                    onOpenMenu = {
                        scope.launch {
                            showMenuSheet = true
                            sheetState.show()
                        }
                    },
                    onNavigateToGroups = {
                        currentScreen = Screen.GROUPS
                    },
                    onReturnToGeofence = {
                        currentScreen = Screen.GEOFENCE
                    }
                )
                Screen.CELLULAR -> CellularDiagnosticScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP },
                    onOpenMenu = {
                        scope.launch {
                            showMenuSheet = true
                            sheetState.show()
                        }
                    },
                    onLocateTowerOnMap = { towerLoc ->
                        viewModel.clearInspectedTrail()
                        viewModel.clearSelectedSearchPin()
                        if (!settings.showCellTowerPin) {
                            viewModel.toggleCellTowerPin(true)
                        }
                        groupViewModel.focusMapOnTarget(
                            com.geotrace.model.MapFocusTarget(
                                id = "cell_mast_${towerLoc.cellId}_${towerLoc.lac}",
                                label = "${towerLoc.operatorName} (${towerLoc.radioType} Mast)",
                                latitude = towerLoc.latitude,
                                longitude = towerLoc.longitude,
                                subtitle = "CID ${towerLoc.cellId} • LAC ${towerLoc.lac} • ~${towerLoc.rangeMeters.toInt()}m radius",
                                targetZoom = 15.4
                            )
                        )
                        currentScreen = Screen.MAP
                    },
                    onOpenSettings = {
                        currentScreen = Screen.SETTINGS
                    }
                )
                Screen.GROUPS -> GroupScreen(
                    groupViewModel = groupViewModel,
                    currentLocation = currentLocation,
                    speedUnit = settings.speedUnit,
                    onLocateOnMap = { target ->
                        viewModel.clearInspectedTrail()
                        viewModel.clearSelectedSearchPin()
                        groupViewModel.focusMapOnTarget(target)
                        currentScreen = Screen.MAP
                    }
                )
                Screen.GEOFENCE -> GeofenceScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP },
                    onPickOnMap = { draft ->
                        viewModel.clearInspectedTrail()
                        viewModel.clearSelectedSearchPin()
                        viewModel.beginMapPickForGeofence(draft)
                        groupViewModel.focusMapOnTarget(
                            com.geotrace.model.MapFocusTarget(
                                id = draft.zoneId ?: "draft_pick",
                                label = draft.name,
                                latitude = draft.latitude,
                                longitude = draft.longitude,
                                subtitle = "Tap exact rooftop or gate to update coordinates"
                            )
                        )
                        currentScreen = Screen.MAP
                    },
                    onLocateOnMap = { zone ->
                        viewModel.clearInspectedTrail()
                        viewModel.clearSelectedSearchPin()
                        groupViewModel.focusMapOnTarget(
                            com.geotrace.model.MapFocusTarget(
                                id = zone.id,
                                label = zone.name,
                                latitude = zone.latitude,
                                longitude = zone.longitude,
                                subtitle = "${zone.category.label} • ${zone.radiusMeters.toInt()}m perimeter",
                                targetZoom = if (zone.radiusMeters >= 500f) 15.2 else 16.5
                            )
                        )
                        currentScreen = Screen.MAP
                    }
                )
                Screen.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP },
                    onViewTrailOnMap = { session ->
                        viewModel.inspectSessionOnMap(session) { hasData ->
                            if (hasData) {
                                viewModel.clearSelectedSearchPin()
                                groupViewModel.clearMapFocusTarget()
                                currentScreen = Screen.MAP
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "No coordinates logged in this trail yet.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                Screen.RESEARCH -> ResearchAnalysisScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP }
                )
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP },
                    groupViewModel = groupViewModel,
                    onJumpToOfflineRegion = { target ->
                        viewModel.clearInspectedTrail()
                        viewModel.clearSelectedSearchPin()
                        groupViewModel.focusMapOnTarget(target)
                        currentScreen = Screen.MAP
                    }
                )
            }

            // Urgent Full-Width Overlay when a Find/Ring Alarm is Active on this Device
            if (isRingingAlarm) {
                Surface(
                    color = colorScheme.errorContainer,
                    border = BorderStroke(2.dp, colorScheme.error),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("find_alarm_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Find Device Alarm Active",
                                tint = colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DEVICE FIND ALARM",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.error
                                )
                                Text(
                                    text = "Triggered by ${ringCallerName ?: "Remote Device"}",
                                    fontSize = 11.sp,
                                    color = colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.Button(
                            onClick = { groupViewModel.stopRingingAlarm() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = colorScheme.error,
                                contentColor = colorScheme.onError
                            ),
                            shape = CircleShape,
                            modifier = Modifier.height(48.dp).testTag("stop_find_alarm_btn")
                        ) {
                            Text("Stop Alarm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Auto-Expanded Scrollable Bottom Sheet for Secondary Utilities
        if (showMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMenuSheet = false },
                sheetState = sheetState,
                containerColor = colorScheme.surface,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Surface(
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        color = colorScheme.outlineVariant,
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
                    }
                },
                modifier = Modifier.testTag("geo_trace_menu_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "GeoTrace Menu",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Secondary telemetry & analytical tools",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        sheetState.hide()
                                    } finally {
                                        showMenuSheet = false
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp).testTag("close_sheet_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Sheet", tint = colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Status Pill Card in Sheet (Tap to copy coordinates, Long-Press to share)
                    val menuHaptic = LocalHapticFeedback.current
                    var menuCoordsCopied by remember { mutableStateOf(false) }
                    LaunchedEffect(menuCoordsCopied) {
                        if (menuCoordsCopied) {
                            kotlinx.coroutines.delay(1600L)
                            menuCoordsCopied = false
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (menuCoordsCopied) colorScheme.primary.copy(alpha = 0.10f) else customColors.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (menuCoordsCopied) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                @OptIn(ExperimentalFoundationApi::class)
                                if (currentLocation != null) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            currentLocation?.let { loc ->
                                                com.geotrace.ui.screens.copyCoordinatesWithFeedback(
                                                    context = context,
                                                    haptic = menuHaptic,
                                                    lat = loc.latitude,
                                                    lon = loc.longitude,
                                                    format = settings.coordinateFormat,
                                                    label = "GeoTrace Current Position"
                                                )
                                                menuCoordsCopied = true
                                            }
                                        },
                                        onLongClick = {
                                            currentLocation?.let { loc ->
                                                menuHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                com.geotrace.ui.screens.shareCoordinatesIntent(
                                                    context = context,
                                                    lat = loc.latitude,
                                                    lon = loc.longitude,
                                                    format = settings.coordinateFormat,
                                                    title = "GeoTrace Live Position"
                                                )
                                            }
                                        }
                                    )
                                } else Modifier
                            )
                            .testTag("menu_copy_coord_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusColor = when {
                                activeSession != null -> colorScheme.error
                                currentLocation != null -> colorScheme.tertiary
                                else -> customColors.warning
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        activeSession != null -> "RECORDING IN PROGRESS • ${activeSession?.name}"
                                        currentLocation != null -> "LOCATION LOCKED (±${currentLocation?.accuracy?.toInt()}m)"
                                        else -> "STANDBY • AWAITING LOCATION FIX"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.sp,
                                    color = statusColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentLocation != null) {
                                    val loc = currentLocation!!
                                    val convertedSpeed = loc.speed * settings.speedUnit.multiplier
                                    val gait = loc.gaitLabel
                                    val coordText = com.geotrace.ui.screens.formatCoordinates(loc.latitude, loc.longitude, settings.coordinateFormat)
                                    Text(
                                        text = "$coordText • ${String.format(java.util.Locale.US, "%.1f", convertedSpeed)} ${settings.speedUnit.label} ($gait)",
                                        fontSize = 10.sp,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                            if (currentLocation != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (menuCoordsCopied) colorScheme.primary else colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (menuCoordsCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy Coordinates",
                                            tint = if (menuCoordsCopied) colorScheme.onPrimary else colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        if (menuCoordsCopied) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Copied",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Card 0: Cellular Signal Hub (Secondary RF Telemetry)
                    val servingNetwork = cellTowers.firstOrNull { it.isRegistered }?.type
                    MenuNavigationCard(
                        title = "Cellular Signal Hub",
                        subtitle = "Serving mast telemetry, neighbor PCI/EARFCN & GPS vs Cell",
                        badgeText = servingNetwork ?: "RF Hub",
                        badgeColor = colorScheme.primary,
                        icon = { Icon(Icons.Default.CellTower, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(22.dp)) },
                        onClick = {
                            scope.launch {
                                try {
                                    sheetState.hide()
                                } finally {
                                    showMenuSheet = false
                                    currentScreen = Screen.CELLULAR
                                }
                            }
                        },
                        modifier = Modifier.testTag("menu_nav_cellular")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 1: Saved Trails & Exports
                    MenuNavigationCard(
                        title = "Saved Trails & Exports",
                        subtitle = "Inspect logged tracks, export GPX, KML or CSV files",
                        badgeText = "${allSessions.size} trails",
                        badgeColor = colorScheme.secondary,
                        icon = { Icon(Icons.Default.History, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(22.dp)) },
                        onClick = {
                            scope.launch {
                                try {
                                    sheetState.hide()
                                } finally {
                                    showMenuSheet = false
                                    currentScreen = Screen.HISTORY
                                }
                            }
                        },
                        modifier = Modifier.testTag("menu_nav_history")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 2: Geofencing & Safe Zones (Phase 3)
                    val activeZonesCount = geofenceZones.count { it.isEnabled }
                    MenuNavigationCard(
                        title = "Geofencing & Safe Zones",
                        subtitle = "Perimeter surveillance, safety radii & transition alerts",
                        badgeText = if (activeZonesCount == 0) "Off (${geofenceZones.size})" else "$activeZonesCount/${geofenceZones.size} on",
                        badgeColor = colorScheme.tertiary,
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(22.dp)) },
                        onClick = {
                            scope.launch {
                                try {
                                    sheetState.hide()
                                } finally {
                                    showMenuSheet = false
                                    currentScreen = Screen.GEOFENCE
                                }
                            }
                        },
                        modifier = Modifier.testTag("menu_nav_geofence")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 3: SWE2105 Research Paper
                    MenuNavigationCard(
                        title = "SWE2105 Research & Comparison",
                        subtitle = "Option 1 (IP) vs Option 2 (GPS/Cell) & Uganda legal framework",
                        badgeText = "Report",
                        badgeColor = colorScheme.tertiary,
                        icon = { Icon(Icons.Default.School, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(22.dp)) },
                        onClick = {
                            scope.launch {
                                try {
                                    sheetState.hide()
                                } finally {
                                    showMenuSheet = false
                                    currentScreen = Screen.RESEARCH
                                }
                            }
                        },
                        modifier = Modifier.testTag("menu_nav_research")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 3: Telemetry & System Settings
                    MenuNavigationCard(
                        title = "Telemetry & System Setup",
                        subtitle = "Velocity units, DD/DMS coords, tile sources & jitter filters",
                        badgeText = "Settings",
                        badgeColor = colorScheme.primary,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(22.dp)) },
                        onClick = {
                            scope.launch {
                                try {
                                    sheetState.hide()
                                } finally {
                                    showMenuSheet = false
                                    currentScreen = Screen.SETTINGS
                                }
                            }
                        },
                        modifier = Modifier.testTag("menu_nav_settings")
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Sheet Footer
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BSE Group 14 • GIS Task",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Offline + Cloud Sync",
                            fontSize = 10.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuNavigationCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = customColors.surfaceContainerLow,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = customColors.surfaceContainerLow,
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}
