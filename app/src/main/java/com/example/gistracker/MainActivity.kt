package com.example.gistracker

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
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
import com.example.gistracker.ui.screens.CellularDiagnosticScreen
import com.example.gistracker.ui.screens.GeofenceScreen
import com.example.gistracker.ui.screens.HistoryScreen
import com.example.gistracker.ui.screens.MapScreen
import com.example.gistracker.ui.screens.ResearchAnalysisScreen
import com.example.gistracker.ui.screens.SettingsScreen
import com.example.gistracker.ui.theme.GISTrackerTheme
import com.example.gistracker.ui.theme.LocalCustomColors
import com.example.gistracker.viewmodel.TrackerViewModel
import kotlinx.coroutines.launch

enum class Screen(val title: String, val testTag: String) {
    MAP("Live Map & Telemetry", "nav_map"),
    CELLULAR("Cellular Signal Hub", "nav_cellular"),
    GEOFENCE("Safe Zones & Geofencing", "nav_geofence"),
    HISTORY("Saved Trails & Exports", "nav_history"),
    RESEARCH("SWE2105 Research Analysis", "nav_research"),
    SETTINGS("Telemetry Settings", "nav_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: TrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GISTrackerTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: TrackerViewModel) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(Screen.MAP) }
    var showMenuSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val currentLocation by viewModel.currentLocation.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    val cellTowers by viewModel.cellTowers.collectAsState()
    val geofenceZones by viewModel.geofenceZones.collectAsState()

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
            viewModel.startStandbyLocationUpdates()
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
        // Streamlined 2-Pillar Bottom Bar with Top-Only Corner Radius
        bottomBar = {
            if (currentScreen == Screen.MAP || currentScreen == Screen.CELLULAR) {
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
                            icon = { Icon(Icons.Default.Map, contentDescription = "Live Map", modifier = Modifier.size(20.dp)) },
                            label = { Text("GPS Map", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.sp) },
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
                    onOpenMenu = {
                        scope.launch {
                            showMenuSheet = true
                            sheetState.show()
                        }
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
                    onNavigateBack = { currentScreen = Screen.MAP }
                )
            }
        }

        // Partial Bottom Sheet for Secondary Utilities (occupies ~50-55% of the screen)
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
                modifier = Modifier.testTag("partial_menu_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                text = "GeoTrack Safe Menu",
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
                            modifier = Modifier.size(32.dp).testTag("close_sheet_btn")
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
                                        currentLocation != null -> "GNSS SATELLITES LOCKED (±${currentLocation?.accuracy?.toInt()}m)"
                                        else -> "STANDBY • AWAITING SATELLITE FIX"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.sp,
                                    color = statusColor
                                )
                                if (currentLocation != null) {
                                    Text(
                                        text = "${String.format("%.4f", currentLocation?.latitude)}°, ${String.format("%.4f", currentLocation?.longitude)}° • Speed: ${String.format("%.1f", currentLocation?.speedKmh ?: 0f)} km/h",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                            text = "Cap 97 Local SQLite",
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
