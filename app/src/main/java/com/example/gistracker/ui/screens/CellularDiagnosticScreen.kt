package com.example.gistracker.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.gistracker.model.CellTowerInfo
import com.example.gistracker.ui.theme.AirtelRed
import com.example.gistracker.ui.theme.CardSurface
import com.example.gistracker.ui.theme.DarkNavy
import com.example.gistracker.ui.theme.ElectricCyan
import com.example.gistracker.ui.theme.MtnYellow
import com.example.gistracker.ui.theme.SafetyAmber
import com.example.gistracker.ui.theme.SuccessGreen
import com.example.gistracker.viewmodel.TrackerViewModel

@Composable
fun CellularDiagnosticScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val cellTowers by viewModel.cellTowers.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val servingCell = cellTowers.firstOrNull { it.isRegistered } ?: cellTowers.firstOrNull()
    val neighborCells = cellTowers.filter { it != servingCell }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cellular Diagnostic Hub",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "TelephonyManager Tower Triangulation & Telemetry",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Button(
                    onClick = { viewModel.refreshCellTowers() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("refresh_cells_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = DarkNavy, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", color = DarkNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Serving Cell Mast Card
        item {
            if (servingCell != null) {
                ServingCellCard(cell = servingCell)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        "Searching for serving cell tower...",
                        color = Color.LightGray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Neighboring Base Stations
        item {
            Text(
                text = "Detected Neighbor Masts (${neighborCells.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan
            )
        }

        if (neighborCells.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        "No neighbor cells in immediate range (single sector serving)",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            items(neighborCells) { neighbor ->
                NeighborCellItem(cell = neighbor)
            }
        }

        // Educational Comparison Card: GPS vs Cell Triangulation vs Cell ID Lookup
        item {
            TriangulationComparisonCard()
        }

        // Uganda Legal & Telecom Framework
        item {
            UgandaTelecomContextCard()
        }
    }
}

@Composable
private fun ServingCellCard(cell: CellTowerInfo) {
    val carrierColor = when {
        cell.mcc == 641 && cell.mnc == 10 -> MtnYellow
        cell.mcc == 641 && cell.mnc == 22 -> AirtelRed
        else -> ElectricCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("serving_cell_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, carrierColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CellTower,
                        contentDescription = null,
                        tint = carrierColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PRIMARY SERVING CELL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = carrierColor
                        )
                        Text(
                            text = cell.operatorDisplayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = carrierColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = cell.type,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = carrierColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cellular Telemetry Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CellMetricBox(label = "MCC (Country)", value = "${cell.mcc ?: 641} (UG)")
                CellMetricBox(label = "MNC (Network)", value = "${cell.mnc ?: 10}")
                CellMetricBox(label = "CID (Cell ID)", value = "${cell.cid ?: "N/A"}")
                CellMetricBox(label = "LAC/TAC", value = "${cell.lac ?: "N/A"}")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Signal Strength and Distance Estimation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Signal Level: ${cell.signalDbm ?: -85} dBm (${cell.signalLevel}/4 bars)",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                Text(
                    text = "Est. Mast Distance: ~${String.format("%.2f", cell.estimatedDistanceKm ?: 0.45)} km",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val signalProgress = (((cell.signalDbm ?: -100) + 120) / 70f).coerceIn(0.1f, 1.0f)
            LinearProgressIndicator(
                progress = { signalProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = carrierColor,
                trackColor = Color(0xFF273552)
            )

            if (cell.timingAdvance != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "LTE Timing Advance (TA): ${cell.timingAdvance} (1 TA step ≈ 78.12m round-trip)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun NeighborCellItem(cell: CellTowerInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.7f))
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
                    tint = Color(0xFF7C4DFF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = cell.operatorDisplayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "${cell.type} | CID: ${cell.cid ?: "?"} | LAC: ${cell.lac ?: "?"}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${cell.signalDbm ?: -95} dBm",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SafetyAmber
                )
                Text(
                    text = "~${String.format("%.1f", cell.estimatedDistanceKm ?: 1.2)} km",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun CellMetricBox(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun TriangulationComparisonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cell Triangulation vs GPS: Engineering Trade-offs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ComparisonRow("Outdoor Accuracy", "2 – 5 meters (GNSS satellites)", "50 – 500m (Urban) / 5–20km (Rural)")
            ComparisonRow("Indoor Penetration", "Fails / Blocked by concrete", "Strong cellular RF penetration")
            ComparisonRow("Active Power", "20 – 30 mA (high drain)", "< 2 mA (piggybacks on radio)")
            ComparisonRow("Offline Capability", "Works anywhere worldwide", "Requires cellular infrastructure")
            ComparisonRow("Refresh Latency", "1 Hz (real-time continuous)", "Minutes (sector handover rate)")
        }
    }
}

@Composable
private fun ComparisonRow(criterion: String, gpsText: String, cellText: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(criterion, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafetyAmber)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🛰️ GPS: $gpsText", fontSize = 11.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("📡 Cell: $cellText", fontSize = 11.sp, color = Color(0xFFB39DDB), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun UgandaTelecomContextCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🇺🇬 Uganda Telecom & Legal Findings",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MtnYellow
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• True Triangulation (TDOA/AoA from 3+ synchronized carrier towers) is restricted to internal network measurement systems. In Uganda, under the Regulation of Interception of Communications Act 2010 (Cap 101), only law enforcement with a judicial warrant can compel telecom carriers to triangulate devices.",
                fontSize = 11.sp,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "• Mobile App Approach: Applications obtain on-device Cell ID (CID), Location Area Code (LAC), and RSSI via TelephonyManager. Combined with crowdsourced OpenCellID data (MCC 641 for Uganda, MNC 10 MTN, MNC 22 Airtel), the app identifies serving tower coordinates with ~200m–1km accuracy in Kampala.",
                fontSize = 11.sp,
                color = Color.LightGray
            )
        }
    }
}
