package com.geotrace.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.CellTowerInfo
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.IconButton

@Composable
fun CellularDiagnosticScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onOpenMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val cellTowers by viewModel.cellTowers.collectAsState()
    val servingCell = cellTowers.firstOrNull { it.isRegistered } ?: cellTowers.firstOrNull()
    val neighborCells = cellTowers.filter { it != servingCell }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Live Map",
                                tint = colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column {
                        Text(
                            text = "Cellular Telemetry Hub",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = "Hardware TelephonyManager RF base stations",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.refreshCellTowers() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("refresh_cells_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (onOpenMenu != null) {
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier
                                .size(38.dp)
                                .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
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
            }
        }

        // Serving Cell Mast Card (Level 1 Elevation)
        item {
            if (servingCell != null) {
                ServingCellCard(cell = servingCell)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top accent
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 3.dp)
                                .background(customColors.warning, RoundedCornerShape(2.dp))
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
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No active physical cellular signal detected from TelephonyManager. Ensure an active SIM is inserted and Airplane Mode is disabled.",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.refreshCellTowers() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Query Radio Hardware", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (servingCell != null) "Single sector serving (no neighbor base stations in immediate measurement frame)"
                        else "No neighbor cells detected",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(neighborCells) { neighbor ->
                NeighborCellItem(cell = neighbor)
            }
        }

        // Educational Comparison Card: GPS vs Cell Triangulation
        item {
            TriangulationComparisonCard()
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
            .testTag("serving_cell_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column {
            // Top Accent Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colorScheme.primary)
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CellTower,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = customColors.statusActiveBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.statusActiveBorder)
                            ) {
                                Text(
                                    text = "PRIMARY SERVING CELL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.sp,
                                    color = customColors.statusActiveText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cell.operatorDisplayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = customColors.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = cell.type,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cellular Telemetry Grid (Recessed Level 2 Container)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CellMetricBox(label = "MCC (COUNTRY)", value = "${cell.mcc ?: "N/A"}\n(${cell.countryDisplayName})")
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    CellMetricBox(label = "MNC (NETWORK)", value = "${cell.mnc ?: "N/A"}")
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    CellMetricBox(label = "CID (CELL ID)", value = "${cell.cid ?: "N/A"}")
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    CellMetricBox(label = "LAC/TAC", value = "${cell.lac ?: "N/A"}")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Signal Strength & Distance Estimation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Signal Level: ${cell.signalDbm ?: -85} dBm (${cell.signalLevel}/4 bars)",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Est. Mast Radius: ~${String.format("%.2f", cell.estimatedDistanceKm ?: 0.45)} km",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val signalProgress = (((cell.signalDbm ?: -100) + 120) / 70f).coerceIn(0.1f, 1.0f)
                LinearProgressIndicator(
                    progress = { signalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colorScheme.primary,
                    trackColor = customColors.surfaceContainerHigh
                )

                if (cell.timingAdvance != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "LTE Timing Advance (TA): ${cell.timingAdvance} (1 step ≈ 78.12m)",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SignalCellularAlt,
                    contentDescription = null,
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = cell.operatorDisplayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "${cell.type} | CID: ${cell.cid ?: "?"} | LAC: ${cell.lac ?: "?"}",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${cell.signalDbm ?: -95} dBm",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.primary
                )
                Text(
                    text = "~${String.format("%.1f", cell.estimatedDistanceKm ?: 1.2)} km",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CellMetricBox(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TriangulationComparisonCard() {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
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
            Text("🛰️ GPS: $gpsText", fontSize = 11.sp, color = colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("📡 Cell: $cellText", fontSize = 11.sp, color = colorScheme.primary, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(3.dp))
    }
}
