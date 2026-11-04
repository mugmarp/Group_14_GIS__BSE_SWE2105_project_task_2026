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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.gistracker.ui.theme.GISTrackerTheme
import com.example.gistracker.viewmodel.TrackerViewModel

enum class Screen(val title: String, val testTag: String) {
    MAP("Live Map", "nav_map"),
    CELLULAR("Cell Signal", "nav_cellular"),
    COMPARE("GPS vs Cell", "nav_compare"),
    HISTORY("Saved Trails", "nav_history")
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

@Composable
fun MainAppScreen(viewModel: TrackerViewModel) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var currentScreen by remember { mutableStateOf(Screen.MAP) }

    // Background Location Permission (Android 10+)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Background location permission processed
    }

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
        bottomBar = {
            NavigationBar(
                containerColor = colorScheme.surface,
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
                        selectedIconColor = colorScheme.onPrimary,
                        selectedTextColor = colorScheme.primary,
                        indicatorColor = colorScheme.primary,
                        unselectedIconColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == Screen.CELLULAR,
                    onClick = { currentScreen = Screen.CELLULAR },
                    icon = { Icon(Icons.Default.CellTower, contentDescription = "Cell Towers") },
                    label = { Text("Cellular", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.CELLULAR.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onPrimary,
                        selectedTextColor = colorScheme.primary,
                        indicatorColor = colorScheme.primary,
                        unselectedIconColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == Screen.COMPARE,
                    onClick = { currentScreen = Screen.COMPARE },
                    icon = { Icon(Icons.Default.CompareArrows, contentDescription = "GPS vs Cell Comparison") },
                    label = { Text("Compare", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.COMPARE.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onPrimary,
                        selectedTextColor = colorScheme.primary,
                        indicatorColor = colorScheme.primary,
                        unselectedIconColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == Screen.HISTORY,
                    onClick = { currentScreen = Screen.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "Saved Trails") },
                    label = { Text("Trails", fontSize = 11.sp) },
                    modifier = Modifier.testTag(Screen.HISTORY.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.onPrimary,
                        selectedTextColor = colorScheme.primary,
                        indicatorColor = colorScheme.primary,
                        unselectedIconColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                Screen.COMPARE -> ResearchAnalysisScreen()
                Screen.HISTORY -> HistoryScreen(viewModel = viewModel)
            }
        }
    }
}
