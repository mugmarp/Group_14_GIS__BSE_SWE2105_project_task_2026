package com.geotrace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.NotificationsActive
import com.geotrace.ui.screens.CellularDiagnosticScreen
import com.geotrace.ui.screens.GeofenceScreen
import com.geotrace.ui.screens.GroupScreen
import com.geotrace.ui.screens.HistoryScreen
import com.geotrace.ui.screens.MapScreen
import com.geotrace.ui.screens.ResearchAnalysisScreen
import com.geotrace.ui.screens.SettingsScreen
import com.geotrace.ui.theme.GISTrackerTheme
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
        enableEdgeToEdge()

        setContent {
            GISTrackerTheme {
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

    // Handle Back Press: Close menu bottom sheet if open, or return to Map screen
    if (showMenuSheet) {
        BackHandler {
            scope.launch {
                sheetState.hide()
                showMenuSheet = false
            }
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
        if (fineGranted || coarseGranted) {
            viewModel.startStandbyLocationUpdates()
            viewModel.refreshCellTowers()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            if (!viewModel.hasActiveStandbyUpdates()) {
                viewModel.startStandbyLocationUpdates()
            }
            viewModel.refreshCellTowers()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        // Streamlined 3-Pillar Bottom Bar with Top-Only Corner Radius
        bottomBar = {
            if (currentScreen == Screen.MAP || currentScreen == Screen.CELLULAR || currentScreen == Screen.GROUPS) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bottom_nav_bar")
                ) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == Screen.MAP,
                            onClick = { currentScreen = Screen.MAP },
                            icon = { Icon(Icons.Default.Map, contentDescription = "Map", modifier = Modifier.size(20.dp)) },
                            label = { Text("Map", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.sp) },
                            modifier = Modifier.testTag("nav_bottom_map"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colorScheme.onPrimary,
                                selectedTextColor = colorScheme.primary,
                                indicatorColor = colorScheme.primary,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == Screen.CELLULAR,
                            onClick = { currentScreen = Screen.CELLULAR },
                            icon = { Icon(Icons.Default.CellTower, contentDescription = "Cellular Hub", modifier = Modifier.size(20.dp)) },
                            label = { Text("Cellular Hub", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.sp) },
                            modifier = Modifier.testTag("nav_bottom_cellular"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colorScheme.onPrimary,
                                selectedTextColor = colorScheme.primary,
                                indicatorColor = colorScheme.primary,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == Screen.GROUPS,
                            onClick = { currentScreen = Screen.GROUPS },
                            icon = { Icon(Icons.Default.Group, contentDescription = "Devices and Tracking Groups", modifier = Modifier.size(20.dp)) },
                            label = { Text("Devices", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.sp) },
                            modifier = Modifier.testTag("nav_bottom_groups"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colorScheme.onPrimary,
                                selectedTextColor = colorScheme.primary,
                                indicatorColor = colorScheme.primary,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.MAP -> MapScreen(
                    viewModel = viewModel,
                    groupViewModel = groupViewModel,
                    onOpenMenu = {
                        scope.launch {
                            showMenuSheet = true
                            sheetState.show()
                        }
                    },
                    onNavigateToGroups = {
                        currentScreen = Screen.GROUPS
                    }
                )
                Screen.CELLULAR -> CellularDiagnosticScreen(
                    viewModel = viewModel,
                    onNavigateBack = null,
                    onOpenMenu = {
                        scope.launch {
                            showMenuSheet = true
                            sheetState.show()
                        }
                    }
                )
                Screen.GROUPS -> GroupScreen(
                    groupViewModel = groupViewModel,
                    currentLocation = currentLocation,
                    onLocateOnMap = { target ->
                        viewModel.toggleFollowUser(false)
                        groupViewModel.focusMapOnTarget(target)
                        currentScreen = Screen.MAP
                    }
                )
                Screen.GEOFENCE -> GeofenceScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP }
                )
                Screen.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP }
                )
                Screen.RESEARCH -> ResearchAnalysisScreen(
                    onNavigateBack = { currentScreen = Screen.MAP }
                )
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = Screen.MAP },
                    onJumpToOfflineRegion = { target ->
                        groupViewModel.focusMapOnTarget(target)
                    }
                )
            }

            // Urgent Full-Width Overlay when a Find/Ring Alarm is Active on this Device
            if (isRingingAlarm) {
                Surface(
                    color = colorScheme.errorContainer,
                    border = BorderStroke(2.dp, colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
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
                            shape = RoundedCornerShape(10.dp),
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
                                text = "GeoTrace Safe Menu",
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
                                    sheetState.hide()
                                    showMenuSheet = false
                                }
                            },
                            modifier = Modifier.size(48.dp).testTag("close_sheet_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Sheet", tint = colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Status Pill Card in Sheet (12.dp radius)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
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
                            Column {
                                Text(
                                    text = when {
                                        activeSession != null -> "RECORDING IN PROGRESS • ${activeSession?.name}"
                                        currentLocation != null -> "LOCATION LOCKED (±${currentLocation?.accuracy?.toInt()}m)"
                                        else -> "STANDBY • AWAITING LOCATION FIX"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.sp,
                                    color = statusColor
                                )
                                if (currentLocation != null) {
                                    val loc = currentLocation!!
                                    val convertedSpeed = loc.speed * settings.speedUnit.multiplier
                                    val gait = loc.gaitLabel
                                    Text(
                                        text = "${String.format("%.4f", loc.latitude)}°, ${String.format("%.4f", loc.longitude)}° • Speed: ${String.format("%.1f", convertedSpeed)} ${settings.speedUnit.label} ($gait)",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Card 0: Devices & Tracking Groups
                    MenuNavigationCard(
                        title = "Devices & Tracking Groups",
                        subtitle = "Multi-device fleet, remote ring, off-grid P2P & shared groups",
                        badgeText = if (activeGroup != null) activeGroup!!.name else "Setup",
                        badgeColor = if (activeGroup != null) colorScheme.tertiary else colorScheme.primary,
                        icon = { Icon(Icons.Default.Group, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(22.dp)) },
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                showMenuSheet = false
                                currentScreen = Screen.GROUPS
                            }
                        },
                        modifier = Modifier.testTag("menu_nav_groups")
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
                                sheetState.hide()
                                showMenuSheet = false
                                currentScreen = Screen.HISTORY
                            }
                        },
                        modifier = Modifier.testTag("menu_nav_history")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 2: Geofencing & Safe Zones (Phase 3)
                    MenuNavigationCard(
                        title = "Geofencing & Safe Zones",
                        subtitle = "Perimeter surveillance, safety radii & transition alerts",
                        badgeText = "${geofenceZones.size} zones",
                        badgeColor = colorScheme.tertiary,
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(22.dp)) },
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                showMenuSheet = false
                                currentScreen = Screen.GEOFENCE
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
                                sheetState.hide()
                                showMenuSheet = false
                                currentScreen = Screen.RESEARCH
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
                                sheetState.hide()
                                showMenuSheet = false
                                currentScreen = Screen.SETTINGS
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
                            text = "BSE SWE2105 • Project Option 2",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Local + Cloud Sync",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
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
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
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
                    shape = RoundedCornerShape(10.dp),
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
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = customColors.surfaceContainerLow,
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}
