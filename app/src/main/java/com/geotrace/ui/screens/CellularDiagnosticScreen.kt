package com.geotrace.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.CellTowerLocation
import com.geotrace.service.OpenCellIdLookupState
import com.geotrace.service.OpenCellIdStatus
import com.geotrace.ui.components.GeoTraceStatusBadge
import com.geotrace.ui.components.GeoTraceTopBar
import com.geotrace.ui.components.ScreenListContentPadding
import com.geotrace.ui.components.StatusBadgeTone
import com.geotrace.ui.components.adaptiveContentWidth
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.ui.theme.TelemetryHeaderLabelStyle
import com.geotrace.viewmodel.TrackerViewModel
import java.util.Locale

@Composable
fun CellularDiagnosticScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onOpenMenu: (() -> Unit)? = null,
    onLocateTowerOnMap: ((CellTowerLocation) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasLocationPermission by remember { mutableStateOf(viewModel.hasCellLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            viewModel.refreshCellTowers()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = viewModel.hasCellLocationPermission()
                if (nowGranted && !hasLocationPermission) {
                    viewModel.refreshCellTowers()
                }
                hasLocationPermission = nowGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cellTowers by viewModel.cellTowers.collectAsState()
    val cellTowerLocation by viewModel.cellTowerLocation.collectAsState()
    val allCellTowerLocations by viewModel.allCellTowerLocations.collectAsState()
    val openCellIdStatus by viewModel.openCellIdStatus.collectAsState()
    val isRefreshingCells by viewModel.isRefreshingCells.collectAsState()

    val servingCell = cellTowers.firstOrNull { it.isRegistered } ?: cellTowers.firstOrNull()
    val neighborCells = cellTowers.filter { it != servingCell }

    Box(
        modifier = modifier
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
            // Header
            item {
                GeoTraceTopBar(
                    title = "Cellular Telemetry Hub",
                    subtitle = "TelephonyManager RF base stations",
                    onBack = onNavigateBack,
                    backContentDescription = "Back to Live Map",
                    trailingContent = {
                        Button(
                            onClick = {
                                if (hasLocationPermission) {
                                    viewModel.refreshCellTowers()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            enabled = !isRefreshingCells,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("refresh_cells_btn")
                        ) {
                            if (isRefreshingCells) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isRefreshingCells) "Scanning" else "Refresh",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        if (onOpenMenu != null) {
                            IconButton(
                                onClick = onOpenMenu,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(customColors.surfaceContainerLow, RoundedCornerShape(14.dp))
                                    .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                    .testTag("cellular_menu_btn")
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open Menu",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }

        // Serving Cell Mast Card / Permission Prompt / Empty State
        item {
            if (!hasLocationPermission) {
                CellPermissionRequiredCard(
                    onGrantPermission = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
            } else if (servingCell != null) {
                ServingCellCard(cell = servingCell)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 3.dp)
                                .background(customColors.warning, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Icon(
                            Icons.Default.CellTower,
                            contentDescription = null,
                            tint = customColors.warning,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "NO CELLULAR BASE STATION DETECTED",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp,
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No active physical cellular signal detected from TelephonyManager. Ensure an active SIM is inserted and Airplane Mode is disabled.",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.refreshCellTowers() },
                            enabled = !isRefreshingCells,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Query Radio Hardware", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // OpenCellID Geolocation & Mast Map Status Card
        if (hasLocationPermission && (servingCell != null || openCellIdStatus.state != OpenCellIdLookupState.IDLE)) {
            item {
                OpenCellIdStatusCard(
                    status = openCellIdStatus,
                    servingTowerLocation = cellTowerLocation,
                    resolvedCount = allCellTowerLocations.size,
                    onLocateTowerOnMap = onLocateTowerOnMap,
                    onOpenSettings = onOpenSettings
                )
            }
        }

        // Neighboring Base Stations
        item {
            Text(
                text = "DETECTED NEIGHBOR MASTS (${neighborCells.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        if (neighborCells.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = when {
                            !hasLocationPermission -> "Grant Precise Location permission above to scan neighbor RF sectors."
                            servingCell != null -> "Single sector serving (no neighbor base stations in immediate measurement frame)"
                            else -> "No neighbor cells detected"
                        },
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = neighborCells,
                key = { idx, cell ->
                    "${idx}_${cell.type}_${cell.cid}_${cell.lac}_${cell.pci}_${cell.earfcn}"
                }
            ) { _, neighbor ->
                NeighborCellItem(cell = neighbor)
            }
        }

        // Educational Comparison Card: GPS vs Cell Triangulation
        item {
            TriangulationComparisonCard()
        }
        }
    }
}

@Composable
private fun CellPermissionRequiredCard(
    onGrantPermission: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cell_permission_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 3.dp)
                    .background(customColors.warning, CircleShape)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Icon(
                Icons.Default.LocationOff,
                contentDescription = null,
                tint = customColors.warning,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "PRECISE LOCATION REQUIRED FOR RF SCAN",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Android blocks TelephonyManager cell tower IDs (MCC, MNC, LAC/TAC, CID) until Precise Location permission is granted.",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("grant_cell_permission_btn")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Grant Location Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OpenCellIdStatusCard(
    status: OpenCellIdStatus,
    servingTowerLocation: CellTowerLocation?,
    resolvedCount: Int,
    onLocateTowerOnMap: ((CellTowerLocation) -> Unit)?,
    onOpenSettings: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val (badgeLabel, badgeTone) = when (status.state) {
        OpenCellIdLookupState.RESOLVED -> "MAST RESOLVED" to StatusBadgeTone.NOMINAL
        OpenCellIdLookupState.NO_API_KEY -> "LOCAL FALLBACK" to StatusBadgeTone.NEUTRAL
        OpenCellIdLookupState.AUTH_ERROR -> "API AUTH ERROR" to StatusBadgeTone.ERROR
        OpenCellIdLookupState.RATE_LIMITED, OpenCellIdLookupState.NETWORK_ERROR -> "LOOKUP DEGRADED" to StatusBadgeTone.WARNING
        OpenCellIdLookupState.NOT_FOUND -> "UNINDEXED SECTOR" to StatusBadgeTone.NEUTRAL
        OpenCellIdLookupState.IDLE -> "OPENCELLID STANDBY" to StatusBadgeTone.NEUTRAL
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("opencellid_status_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OPENCELLID MAST GEOLOCATION",
                    style = TelemetryHeaderLabelStyle,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                GeoTraceStatusBadge(
                    text = badgeLabel,
                    tone = badgeTone
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = status.message,
                fontSize = 12.sp,
                color = colorScheme.onSurface
            )

            if (servingTowerLocation != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "Coordinates: %.5f, %.5f • Coverage ~%d m • %d mast(s) pinned",
                        servingTowerLocation.latitude,
                        servingTowerLocation.longitude,
                        servingTowerLocation.rangeMeters.toInt(),
                        resolvedCount.coerceAtLeast(1)
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colorScheme.onSurfaceVariant
                )
                if (onLocateTowerOnMap != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onLocateTowerOnMap(servingTowerLocation) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("locate_cell_mast_btn")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Locate Serving Mast on Map", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if ((status.state == OpenCellIdLookupState.NO_API_KEY || status.state == OpenCellIdLookupState.AUTH_ERROR) && onOpenSettings != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("configure_opencellid_key_btn")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Cellular Engine Settings (Override)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ServingCellCard(cell: CellTowerInfo) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .testTag("serving_cell_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column {
            // Top Accent Line (clipped cleanly by Card shape)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colorScheme.primary)
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CellTower,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Surface(
                                shape = CircleShape,
                                color = customColors.statusActiveBg,
                                border = BorderStroke(1.dp, customColors.statusActiveBorder)
                            ) {
                                Text(
                                    text = "PRIMARY SERVING CELL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.sp,
                                    color = customColors.statusActiveText,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cell.operatorDisplayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = cell.type,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cellular Telemetry Grid (Recessed Level 2 Container with Weighted Columns)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.surfaceContainerLow, RoundedCornerShape(14.dp))
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1.35f).padding(end = 6.dp)) {
                        CellMetricBox(
                            label = "MCC",
                            value = "${cell.mcc ?: "N/A"}",
                            subtext = cell.countryDisplayName
                        )
                    }
                    Box(modifier = Modifier.width(1.dp).height(34.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.weight(0.9f).padding(horizontal = 6.dp)) {
                        CellMetricBox(label = "MNC", value = "${cell.mnc ?: "N/A"}")
                    }
                    Box(modifier = Modifier.width(1.dp).height(34.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.weight(1.05f).padding(horizontal = 6.dp)) {
                        CellMetricBox(label = "CID", value = "${cell.cid ?: "N/A"}")
                    }
                    Box(modifier = Modifier.width(1.dp).height(34.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.weight(0.95f).padding(start = 6.dp)) {
                        CellMetricBox(label = "LAC/TAC", value = "${cell.lac ?: "N/A"}")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Signal Strength & Distance Estimation (Non-overlapping weighted layout)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "SIGNAL LEVEL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        val signalLabel = cell.signalDbm?.let { "$it dBm (${cell.signalLevel}/4 bars)" } ?: "Signal N/A"
                        Text(
                            text = signalLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (cell.signalDbm != null) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "EST. MAST RADIUS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        val radiusLabel = cell.estimatedDistanceKm?.let {
                            "~${String.format(Locale.US, "%.2f", it)} km"
                        } ?: "--"
                        Text(
                            text = radiusLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (cell.estimatedDistanceKm != null) colorScheme.primary else colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val signalProgress = cell.signalDbm?.let {
                    ((it + 120) / 70f).coerceIn(0.06f, 1.0f)
                } ?: 0f
                LinearProgressIndicator(
                    progress = { signalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = colorScheme.primary,
                    trackColor = colorScheme.onSurface.copy(alpha = 0.14f),
                    drawStopIndicator = {}
                )

                val extraMeta = buildList {
                    cell.timingAdvance?.let {
                        val radioLabel = if (cell.type.contains("NR")) "NR" else "LTE"
                        add("$radioLabel TA: $it (1 step ≈ 78.12m)")
                    }
                    cell.pci?.let { add("PCI: $it") }
                    cell.earfcn?.let { add("Ch: $it") }
                }
                if (extraMeta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = extraMeta.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum"
                        ),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NeighborCellItem(cell: CellTowerInfo) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SignalCellularAlt,
                    contentDescription = null,
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cell.operatorDisplayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val radioDetails = buildString {
                        append(cell.type)
                        if (cell.cid != null) {
                            append(" | CID: ${cell.cid}")
                            if (cell.lac != null) append(" | LAC: ${cell.lac}")
                        } else if (cell.pci != null) {
                            val pciLabel = when {
                                cell.type.contains("WCDMA") -> "PSC"
                                cell.type.contains("GSM") -> "BSIC"
                                else -> "PCI"
                            }
                            append(" | $pciLabel: ${cell.pci}")
                            if (cell.earfcn != null) append(" | Ch: ${cell.earfcn}")
                        } else {
                            append(" | Handover candidate")
                        }
                    }
                    Text(
                        text = radioDetails,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum"
                        ),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = cell.signalDbm?.let { "$it dBm" } ?: "Signal N/A",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (cell.signalDbm != null) colorScheme.primary else colorScheme.onSurfaceVariant
                )
                Text(
                    text = cell.estimatedDistanceKm?.let { "~${String.format(Locale.US, "%.1f", it)} km" } ?: "--",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CellMetricBox(label: String, value: String, subtext: String? = null) {
    Column {
        Text(
            text = label,
            style = TelemetryHeaderLabelStyle.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtext.isNullOrBlank()) {
            Text(
                text = subtext,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TriangulationComparisonCard() {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GPS VS CELL POSITIONING COMPARISON",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.04.sp,
                    color = colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            ComparisonRow("Outdoor Accuracy", "2 – 5 meters (GNSS satellites)", "50 – 500m (Urban) / 5–20km (Rural)")
            ComparisonRow("Indoor Penetration", "Fails / Blocked by concrete", "High (Cellular RF penetrates walls)")
            ComparisonRow("Active Power", "20 – 30 mA (high battery drain)", "< 2 mA (piggybacks on radio)")
            ComparisonRow("Global Coverage", "Works anywhere worldwide", "Requires cellular infrastructure")
            ComparisonRow("Fix Time (TTFF)", "1 – 30s (needs satellite lock)", "< 1s (instant network lookup)")
        }
    }
}

@Composable
private fun ComparisonRow(criterion: String, gpsText: String, cellText: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(criterion, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.secondary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GPS: $gpsText", fontSize = 11.sp, color = colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cell: $cellText", fontSize = 11.sp, color = colorScheme.primary, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(3.dp))
    }
}
