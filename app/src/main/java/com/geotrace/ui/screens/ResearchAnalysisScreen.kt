package com.geotrace.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.CellTowerLocation
import com.geotrace.model.LocationPoint
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackerSettings
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel
import java.util.Locale

@Composable
fun ResearchAnalysisScreen(
    viewModel: TrackerViewModel? = null,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val currentLocation: LocationPoint? = viewModel?.currentLocation?.collectAsState()?.value
    val trackingMode: TrackingMode = viewModel?.trackingMode?.collectAsState()?.value ?: TrackingMode.HIGH_ACCURACY
    val cellTowerLocation: CellTowerLocation? = viewModel?.cellTowerLocation?.collectAsState()?.value
    val allCellLocations: List<CellTowerLocation> = viewModel?.allCellTowerLocations?.collectAsState()?.value ?: emptyList()
    val rawCellTowers: List<CellTowerInfo> = viewModel?.cellTowers?.collectAsState()?.value ?: emptyList()
    val allSessions: List<TrackingSession> = viewModel?.allSessions?.collectAsState()?.value ?: emptyList()
    val filteredJitterMeters: Double = viewModel?.filteredJitterMeters?.collectAsState()?.value ?: 0.0
    val filteredJitterCount: Int = viewModel?.filteredJitterCount?.collectAsState()?.value ?: 0
    val settings: TrackerSettings = viewModel?.settings?.collectAsState()?.value ?: TrackerSettings()

    var selectedTech by remember { mutableStateOf(TechOption.GPS_FUSED) }
    var selectedScenario by remember { mutableStateOf(FieldScenario.OUTDOOR_SURVEY) }
    var expandOption1 by remember { mutableStateOf(false) }
    var expandOption2 by remember { mutableStateOf(true) }
    var expandLegal by remember { mutableStateOf(false) }

    val servingCellInfo = rawCellTowers.firstOrNull { it.isRegistered } ?: rawCellTowers.firstOrNull()
    val mastOffsetMeters: Double? = remember(currentLocation, cellTowerLocation) {
        if (currentLocation != null && cellTowerLocation != null && viewModel != null) {
            viewModel.calculateDistanceMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                cellTowerLocation.latitude,
                cellTowerLocation.longitude
            )
        } else null
    }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("research_back_btn")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Live Map",
                                tint = colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SWE2105 Research Lab",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "Option 1 (IP) vs Option 2 (GNSS & Cell Telemetry)",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        shareResearchSummary(
                            context = context,
                            trackingMode = trackingMode,
                            currentLocation = currentLocation,
                            cellTowerLocation = cellTowerLocation,
                            servingCellInfo = servingCellInfo,
                            mastOffsetMeters = mastOffsetMeters,
                            filteredJitterMeters = filteredJitterMeters,
                            filteredJitterCount = filteredJitterCount,
                            allSessions = allSessions,
                            settings = settings
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("research_share_btn")
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share Research Benchmark Summary",
                        tint = colorScheme.primary
                    )
                }
            }
        }

        // Card 1: Live Empirical Telemetry Benchmark (GNSS vs Cell Mast)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_benchmark_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(colorScheme.tertiary)
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LIVE EMPIRICAL TELEMETRY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.sp,
                                    color = colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = trackingMode.title.substringBefore(" (").uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetric(
                                label = "ACTIVE FIX ACCURACY",
                                value = if (currentLocation != null) {
                                    "±${currentLocation.accuracy.toInt()}m (${currentLocation.provider.uppercase()})"
                                } else "Awaiting Fix",
                                color = if ((currentLocation?.accuracy ?: 999f) <= 15f) colorScheme.tertiary else customColors.warning,
                                modifier = Modifier.weight(1f)
                            )
                            TechMetric(
                                label = "GNSS VS CELL OFFSET",
                                value = when {
                                    mastOffsetMeters == null -> if (servingCellInfo != null) "Needs OpenCellID Key" else "No Tower"
                                    mastOffsetMeters >= 1000.0 -> String.format(Locale.US, "%.2f km", mastOffsetMeters / 1000.0)
                                    else -> String.format(Locale.US, "%.0f m", mastOffsetMeters)
                                },
                                color = colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetric(
                                label = "SERVING CELL MAST",
                                value = if (servingCellInfo != null) {
                                    "CID ${servingCellInfo.cid ?: "--"} • ${servingCellInfo.signalDbm ?: 0} dBm"
                                } else "Unavailable",
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TechMetric(
                                label = "NEIGHBORS / RESOLVED",
                                value = "${rawCellTowers.size} visible / ${allCellLocations.size} mapped",
                                color = colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Deadband Filtered: $filteredJitterCount spikes (${String.format(Locale.US, "%.1f", filteredJitterMeters)}m)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Geofence Band: ±${settings.geofenceHysteresisMeters.toInt()}m",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Card 2: Recorded Field Sessions Grouped by Tracking Mode
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_mode_benchmarks_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "RECORDED FIELD TRIALS BY MODE (${allSessions.size} TOTAL)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                        color = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TrackingMode.values().forEachIndexed { idx, mode ->
                        val modeSessions = allSessions.filter { it.trackingMode == mode }
                        val totalDistM = modeSessions.sumOf { it.totalDistanceMeters }
                        val totalPts = modeSessions.sumOf { it.pointCount }
                        val avgSpeedMps = if (modeSessions.isNotEmpty()) {
                            (modeSessions.map { it.avgSpeedKmh }.average() / 3.6)
                        } else 0.0
                        val avgSpeedDisplay = avgSpeedMps * settings.speedUnit.multiplier

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = if (modeSessions.isEmpty()) {
                                        "No field trials recorded yet • Expected ${mode.expectedAccuracy}"
                                    } else {
                                        val distStr = if (totalDistM >= 1000) String.format(Locale.US, "%.2f km", totalDistM / 1000.0)
                                        else String.format(Locale.US, "%.0f m", totalDistM)
                                        "${modeSessions.size} trials • $distStr • $totalPts pts"
                                    },
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (modeSessions.isNotEmpty()) colorScheme.primary.copy(alpha = 0.12f) else customColors.surfaceContainerLow
                            ) {
                                Text(
                                    text = if (modeSessions.isNotEmpty()) {
                                        "${String.format(Locale.US, "%.1f", avgSpeedDisplay)} ${settings.speedUnit.label}"
                                    } else "--",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (modeSessions.isNotEmpty()) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (idx < TrackingMode.values().lastIndex) {
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        }

        // Card 3: Interactive Technology Comparator with Visual Metric Bars
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tech_comparator_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(colorScheme.primary)
                    )

                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Science,
                                contentDescription = null,
                                tint = customColors.warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "INTERACTIVE POSITIONING COMPARATOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.04.sp,
                                color = colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TechOption.values().forEach { tech ->
                                val selected = selectedTech == tech
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedTech = tech },
                                    label = { Text(tech.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(8.dp),
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
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = customColors.surfaceContainerLow,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = selectedTech.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = selectedTech.summary,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricScoreBar("Spatial Precision (${selectedTech.accuracy})", selectedTech.accuracyScore, if (selectedTech.accuracyScore >= 0.7f) colorScheme.tertiary else customColors.warning)
                                    MetricScoreBar("Battery Efficiency (${selectedTech.batteryDrain})", selectedTech.batteryEfficiencyScore, colorScheme.primary)
                                    MetricScoreBar("Indoor Reliability (${selectedTech.indoorRating})", selectedTech.indoorScore, colorScheme.secondary)
                                    MetricScoreBar("Refresh Cadence (${selectedTech.cadenceLabel})", selectedTech.cadenceScore, if (selectedTech.realTimeCapable) colorScheme.tertiary else colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 4: Interactive Field Scenario Recommender (Apply Mode Live)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scenario_recommender_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FIELD SCENARIO ADVISOR (TRY LIVE)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp,
                            color = colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FieldScenario.values().forEach { scenario ->
                            val selected = selectedScenario == scenario
                            FilterChip(
                                selected = selected,
                                onClick = { selectedScenario = scenario },
                                label = { Text(scenario.chipLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.secondary,
                                    selectedLabelColor = colorScheme.onSecondary,
                                    containerColor = customColors.surfaceContainerLow,
                                    labelColor = colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = colorScheme.outlineVariant,
                                    selectedBorderColor = colorScheme.secondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Recommended: ${selectedScenario.recommendedMode.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedScenario.rationale,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Suggested Geofence Hysteresis: ${selectedScenario.suggestedHysteresisLabel}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.tertiary
                            )
                            if (viewModel != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val isAlreadyActive = trackingMode == selectedScenario.recommendedMode
                                OutlinedButton(
                                    onClick = { viewModel.setTrackingMode(selectedScenario.recommendedMode) },
                                    enabled = !isAlreadyActive,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("apply_recommended_mode_btn")
                                ) {
                                    Text(
                                        text = if (isAlreadyActive) {
                                            "Active Right Now (${selectedScenario.recommendedMode.title})"
                                        } else {
                                            "Switch App to ${selectedScenario.recommendedMode.title}"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 5: Option 1 Analysis (Collapsible)
        item {
            CollapsibleResearchCard(
                title = "OPTION 1: WHY IP GEOLOCATION FAILS AS A TRACKER",
                subtitle = "Carrier CGNAT gateways (400+ km error) & VPN spoofing",
                accentColor = colorScheme.error,
                expanded = expandOption1,
                onToggle = { expandOption1 = !expandOption1 }
            ) {
                Text(
                    text = "1. Technical Mechanism: Maps public IPv4/IPv6 addresses to WHOIS RIR blocks and BGP routing tables (MaxMind, IP2Location).\n\n" +
                           "2. Severe CGNAT Error in Uganda: Mobile ISPs (MTN/Airtel) route regional subscribers through centralized Kampala gateways, placing a device in Mbarara or Gulu hundreds of kilometers away.\n\n" +
                           "3. Spoofing & Static Snapshots: VPN tunnels or APN changes displace IP coordinates immediately, and IPs do not update smoothly with physical pedestrian motion.",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // Card 6: Option 2 Architecture & Scientific Comparison (Collapsible)
        item {
            CollapsibleResearchCard(
                title = "OPTION 2: GNSS VS CELL-ID LOOKUP VS CARRIER TRIANGULATION",
                subtitle = "Clarifying on-device TelephonyManager + OpenCellID vs restricted TDOA",
                accentColor = colorScheme.tertiary,
                expanded = expandOption2,
                onToggle = { expandOption2 = !expandOption2 }
            ) {
                Text(
                    text = "• Multi-Constellation GNSS (High Accuracy): Uses L1/E1 satellite signals for 2–5 m outdoor precision and Doppler speed/bearing, at higher radio power (~20–30 mA) and line-of-sight loss indoors.\n\n" +
                           "• Balanced Network (Wi-Fi + Cell Fused): Uses Android Network Location (`LocationManager.NETWORK_PROVIDER`) without locking the active GPS chip—ideal indoors (~10–40 m).\n\n" +
                           "• Cell-ID Centroid Lookup (Power Saver): GeoTrace reads live hardware MCC/MNC/LAC/CID/TA/dBm via TelephonyManager and resolves the tower centroid via OpenCellID (200 m–5 km precision, <2 mA).\n\n" +
                           "• True Carrier Triangulation (TDOA/OTDOA): Computing exact intersection across 3+ synchronized base stations occurs inside telecom core networks and is not publicly exposed to third-party apps.",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // Card 7: Legal & Regulatory Framework in Uganda (Collapsible)
        item {
            CollapsibleResearchCard(
                title = "LEGAL & ETHICAL FRAMEWORK IN UGANDA",
                subtitle = "Interception Act 2010 (Cap 101) & Data Protection Act 2019 (Cap 97)",
                accentColor = colorScheme.secondary,
                icon = {
                    Icon(Icons.Default.Policy, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(18.dp))
                },
                expanded = expandLegal,
                onToggle = { expandLegal = !expandLegal }
            ) {
                Text(
                    text = "• Regulation of Interception of Communications Act 2010 (Cap 101): Under Section 5, carrier-level network triangulation/interception requires a High Court judicial warrant by designated authorities.\n\n" +
                           "• Data Protection and Privacy Act 2019 (Cap 97): Precise GPS tracks and Cell IDs are Personal Data (Section 2). GeoTrace enforces opt-in user authentication, Ghost Mode privacy, and foreground service transparency notifications (Section 7).",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun CollapsibleResearchCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor)
            )
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggle),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) {
                            icon()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.04.sp,
                                color = accentColor
                            )
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse section" else "Expand section",
                        tint = colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricScoreBar(label: String, targetProgress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "metric_score_anim"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun TechMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
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
            color = color
        )
    }
}

private fun shareResearchSummary(
    context: Context,
    trackingMode: TrackingMode,
    currentLocation: LocationPoint?,
    cellTowerLocation: CellTowerLocation?,
    servingCellInfo: CellTowerInfo?,
    mastOffsetMeters: Double?,
    filteredJitterMeters: Double,
    filteredJitterCount: Int,
    allSessions: List<TrackingSession>,
    settings: TrackerSettings
) {
    val sb = StringBuilder()
    sb.appendLine("GeoTrace SWE2105 Option 2 — Empirical Telemetry Benchmark")
    sb.appendLine("Active Mode: ${trackingMode.title}")
    if (currentLocation != null) {
        sb.appendLine(
            "Current Fix: ${String.format(Locale.US, "%.5f", currentLocation.latitude)}, ${String.format(Locale.US, "%.5f", currentLocation.longitude)} | " +
                "Provider: ${currentLocation.provider.uppercase()} | Accuracy: ±${currentLocation.accuracy.toInt()}m"
        )
    }
    if (servingCellInfo != null) {
        sb.appendLine(
            "Serving Cell: ${servingCellInfo.operatorDisplayName} (${servingCellInfo.type}) | " +
                "CID=${servingCellInfo.cid ?: "N/A"}, LAC=${servingCellInfo.lac ?: "N/A"}, ${servingCellInfo.signalDbm ?: 0} dBm"
        )
    }
    if (cellTowerLocation != null && mastOffsetMeters != null) {
        sb.appendLine(
            "GNSS vs Serving Mast Offset: ${String.format(Locale.US, "%.1f", mastOffsetMeters)}m (Tower Range ±${cellTowerLocation.rangeMeters.toInt()}m)"
        )
    }
    sb.appendLine("Deadband Drift Eliminated: $filteredJitterCount spikes (${String.format(Locale.US, "%.1f", filteredJitterMeters)}m)")
    sb.appendLine("Geofence Hysteresis Band: ±${settings.geofenceHysteresisMeters.toInt()}m (Accuracy Gate=${settings.geofenceAccuracyGateEnabled})")
    sb.appendLine("")
    sb.appendLine("Recorded Sessions by Mode (${allSessions.size} total):")
    TrackingMode.values().forEach { mode ->
        val group = allSessions.filter { it.trackingMode == mode }
        val distKm = group.sumOf { it.totalDistanceMeters } / 1000.0
        sb.appendLine("- ${mode.title}: ${group.size} sessions, ${String.format(Locale.US, "%.2f", distKm)} km total")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GeoTrace SWE2105 Field Benchmark Summary")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share SWE2105 Research Summary"))
}

enum class FieldScenario(
    val chipLabel: String,
    val recommendedMode: TrackingMode,
    val suggestedHysteresisLabel: String,
    val rationale: String
) {
    OUTDOOR_SURVEY(
        chipLabel = "Outdoor Walk",
        recommendedMode = TrackingMode.HIGH_ACCURACY,
        suggestedHysteresisLabel = "±5m Tight or ±10m Std",
        rationale = "1 Hz GNSS captures true pedestrian displacement and Doppler speed while dual-radius geofencing stops gate boundary flapping."
    ),
    INDOOR_CAMPUS(
        chipLabel = "Indoor Hall",
        recommendedMode = TrackingMode.BALANCED,
        suggestedHysteresisLabel = "±10m Std + Accuracy Gate",
        rationale = "Concrete roofs attenuate L1 GPS signals; Network/Wi-Fi fusion keeps a stable ±15–40m lock without draining battery searching for satellites."
    ),
    DAILY_TRANSIT(
        chipLabel = "Boda Transit",
        recommendedMode = TrackingMode.BALANCED,
        suggestedHysteresisLabel = "±10m Std",
        rationale = "Balances 5s trajectory updates with moderate battery (~5–10 mA) across urban routes."
    ),
    ANTI_THEFT_STANDBY(
        chipLabel = "24/7 Low Battery",
        recommendedMode = TrackingMode.POWER_SAVER,
        suggestedHysteresisLabel = "±20m Wide + Accuracy Gate",
        rationale = "Uses TelephonyManager cell IDs and OpenCellID mast centroids with <2 mA draw while avoiding accidental geofence triggers from coarse cells."
    )
}

enum class TechOption(
    val shortLabel: String,
    val title: String,
    val summary: String,
    val accuracy: String,
    val batteryDrain: String,
    val indoorRating: String,
    val cadenceLabel: String,
    val accuracyScore: Float,
    val batteryEfficiencyScore: Float,
    val indoorScore: Float,
    val cadenceScore: Float,
    val realTimeCapable: Boolean
) {
    GPS_FUSED(
        shortLabel = "GPS Fused",
        title = "Google Fused Location Provider (GPS + WiFi + Cell)",
        summary = "Adaptively fuses multi-constellation GNSS satellites, ambient WiFi beacons, and cell towers.",
        accuracy = "2 – 5 m",
        batteryDrain = "Adaptive (5-20 mA)",
        indoorRating = "Good (WiFi fallback)",
        cadenceLabel = "Continuous (1 Hz)",
        accuracyScore = 0.95f,
        batteryEfficiencyScore = 0.55f,
        indoorScore = 0.65f,
        cadenceScore = 0.95f,
        realTimeCapable = true
    ),
    GNSS_RAW(
        shortLabel = "Raw GNSS",
        title = "Direct Satellite GNSS (GPS / Galileo / GLONASS)",
        summary = "Direct radio reception from 20,200 km orbit satellites with on-chip trilateration and Doppler velocity.",
        accuracy = "2 – 5 m",
        batteryDrain = "High (25-30 mA)",
        indoorRating = "Poor (attenuated)",
        cadenceLabel = "Continuous (1 Hz)",
        accuracyScore = 0.97f,
        batteryEfficiencyScore = 0.25f,
        indoorScore = 0.20f,
        cadenceScore = 0.95f,
        realTimeCapable = true
    ),
    CELL_ID(
        shortLabel = "Cell ID",
        title = "Cell-ID Lookup (TelephonyManager + OpenCellID)",
        summary = "Reads serving tower MCC/MNC/LAC/CID/TA from Android TelephonyManager and plots tower coverage centroid.",
        accuracy = "200 m – 5 km",
        batteryDrain = "Minimal (< 1 mA)",
        indoorRating = "Excellent",
        cadenceLabel = "Periodic (10–25 s)",
        accuracyScore = 0.35f,
        batteryEfficiencyScore = 0.96f,
        indoorScore = 0.92f,
        cadenceScore = 0.60f,
        realTimeCapable = true
    ),
    CELL_TRIANG(
        shortLabel = "Carrier TDOA",
        title = "True Carrier Triangulation (TDOA / OTDOA / AoA)",
        summary = "Time-Difference-Of-Arrival across 3+ synchronized carrier masts. Restricted to telecom cores under Uganda Cap 101.",
        accuracy = "50 – 300 m",
        batteryDrain = "Low (Network-side)",
        indoorRating = "Very Good",
        cadenceLabel = "On-Demand",
        accuracyScore = 0.60f,
        batteryEfficiencyScore = 0.90f,
        indoorScore = 0.85f,
        cadenceScore = 0.70f,
        realTimeCapable = true
    ),
    WIFI_WPS(
        shortLabel = "WiFi Network",
        title = "WiFi Positioning System (BSSID Network Provider)",
        summary = "Matches visible WiFi AP MAC addresses against regional lookup databases without waking GPS satellites.",
        accuracy = "10 – 40 m",
        batteryDrain = "Low (~2-5 mA)",
        indoorRating = "Excellent",
        cadenceLabel = "Adaptive (5 s)",
        accuracyScore = 0.78f,
        batteryEfficiencyScore = 0.82f,
        indoorScore = 0.95f,
        cadenceScore = 0.80f,
        realTimeCapable = true
    ),
    IP_GEO(
        shortLabel = "Option 1 (IP)",
        title = "Option 1: IP Geolocation (WHOIS / BGP / MaxMind)",
        summary = "Maps public IP addresses via RIR/GeoIP registries. Fails on MTN/Airtel CGNAT gateways and VPNs.",
        accuracy = "50 – 400+ km",
        batteryDrain = "None",
        indoorRating = "N/A (Gateway only)",
        cadenceLabel = "Static Snapshot",
        accuracyScore = 0.08f,
        batteryEfficiencyScore = 1.0f,
        indoorScore = 0.15f,
        cadenceScore = 0.10f,
        realTimeCapable = false
    )
}
