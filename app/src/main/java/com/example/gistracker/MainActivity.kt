package com.example.gistracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.gistracker.ui.screens.CellularDiagnosticScreen
import com.example.gistracker.ui.screens.GeofenceScreen
import com.example.gistracker.ui.screens.HistoryScreen
import com.example.gistracker.ui.screens.MapScreen
import com.example.gistracker.ui.screens.ResearchAnalysisScreen
import com.example.gistracker.ui.theme.CardSurface
import com.example.gistracker.ui.theme.DarkNavy
import com.example.gistracker.ui.theme.ElectricCyan
import com.example.gistracker.ui.theme.GISTrackerTheme
import com.example.gistracker.viewmodel.TrackerViewModel

enum class Screen(val title: String, val testTag: String) {
    MAP("Live Map", "nav_map"),
    CELLULAR("Cell Towers", "nav_cellular"),
    GEOFENCE("Geofences", "nav_geofence"),
    HISTORY("History", "nav_history"),
    RESEARCH("SWE2105 Docs", "nav_research")
}

class MainActivity : ComponentActivity() {

    private val viewModel: TrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GISTrackerTheme(darkTheme = true) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: TrackerViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.MAP) }

    // Permission Request Flow
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.refreshCellTowers()
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
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = DarkNavy,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentScreen == Screen.MAP,
                    onClick = { currentScreen = Screen.MAP },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Live Map") },
                    label = { Text("Map", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.MAP.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkNavy,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == Screen.CELLULAR,
                    onClick = { currentScreen = Screen.CELLULAR },
                    icon = { Icon(Icons.Default.CellTower, contentDescription = "Cell Towers") },
                    label = { Text("Cellular", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.CELLULAR.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkNavy,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == Screen.GEOFENCE,
                    onClick = { currentScreen = Screen.GEOFENCE },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Geofences") },
                    label = { Text("Safe Zones", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.GEOFENCE.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkNavy,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == Screen.HISTORY,
                    onClick = { currentScreen = Screen.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.HISTORY.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkNavy,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == Screen.RESEARCH,
                    onClick = { currentScreen = Screen.RESEARCH },
                    icon = { Icon(Icons.Default.School, contentDescription = "SWE2105 Research") },
                    label = { Text("Research", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.RESEARCH.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkNavy,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.MAP -> MapScreen(viewModel = viewModel)
                Screen.CELLULAR -> CellularDiagnosticScreen(viewModel = viewModel)
                Screen.GEOFENCE -> GeofenceScreen(viewModel = viewModel)
                Screen.HISTORY -> HistoryScreen(viewModel = viewModel)
                Screen.RESEARCH -> ResearchAnalysisScreen()
            }
        }
    }
}
