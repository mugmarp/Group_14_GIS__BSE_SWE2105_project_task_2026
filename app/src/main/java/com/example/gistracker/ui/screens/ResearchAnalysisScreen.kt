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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gistracker.ui.theme.LocalCustomColors

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton

@Composable
fun ResearchAnalysisScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    var selectedTech by remember { mutableStateOf(TechOption.GPS_FUSED) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SWE2105 Research Analysis",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "BSE SWE2105 Option 2: GPS vs Cellular Triangulation Evaluation",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Interactive Technology Comparator Card (Level 1 Elevation)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tech_comparator_card"),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    // Top Accent Line in Technical Cyan
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
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
                                text = "INTERACTIVE TRACKING METHOD COMPARATOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.04.sp,
                                color = colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TechOption.values().take(3).forEach { tech ->
                                val selected = selectedTech == tech
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedTech = tech },
                                    label = { Text(tech.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(4.dp),
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TechOption.values().drop(3).forEach { tech ->
                                val selected = selectedTech == tech
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedTech = tech },
                                    label = { Text(tech.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(4.dp),
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Selected Tech Telemetry Evaluation (Recessed Well)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = customColors.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                    color = colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TechMetric("ACCURACY", selectedTech.accuracy, if (selectedTech.realTimeCapable) colorScheme.tertiary else colorScheme.error, modifier = Modifier.weight(1f))
                                        TechMetric("BATTERY DRAIN", selectedTech.batteryDrain, customColors.warning, modifier = Modifier.weight(1f))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TechMetric("INDOOR FIX", selectedTech.indoorRating, colorScheme.secondary, modifier = Modifier.weight(1f))
                                        TechMetric("REFRESH CADENCE", if (selectedTech.realTimeCapable) "Continuous (1 Hz)" else "Static Snapshot", if (selectedTech.realTimeCapable) colorScheme.tertiary else colorScheme.error, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Option 1 Analysis Card: IP Geolocation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(colorScheme.error))
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "OPTION 1: LAPTOP TRACKING VIA IP ADDRESS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp,
                            color = colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Why IP Geolocation Fails As A Real-Time Movement Tracker:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Technical Mechanism: IP geolocation maps public IPv4/IPv6 addresses against static RIR databases (WHOIS), BGP routing announcements, and crowdsourced WiFi BSSID tables (MaxMind, IP2Location).\n\n" +
                                   "2. Severe Accuracy Limitations: On dynamic mobile networks (MTN/Airtel CGNAT), IP addresses resolve to carrier data gateways in Kampala, regardless of physical device location across Uganda (error up to 400+ km in Gulu or Mbarara).\n\n" +
                                   "3. Vulnerability to Spoofing: Simple VPN tunnels, proxy chains, or APN modifications instantly displace the reported IP to foreign jurisdictions.\n\n" +
                                   "4. Academic Conclusion: IP lookup provides an intermittent network snapshot, not a continuous real-time movement trajectory.",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Option 2 Analysis Card: Mobile Phone Tracking (GPS vs Cell Triangulation)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(colorScheme.tertiary))
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "OPTION 2: MOBILE TRACKING (GPS VS CELL TRIANGULATION)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp,
                            color = colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Engineering Trade-offs & Scientific Comparison:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• GPS/GNSS Satellites:\n" +
                                   "  - Pros: High spatial accuracy (2–5 m outdoors), global coverage independent of telecom towers, atomic-clock synchronized bearing and speed vectors.\n" +
                                   "  - Cons: Substantial battery drain (20–30 mA active radio), line-of-sight satellite dependency (fails inside concrete basements and dense urban canyons).\n\n" +
                                   "• Cell Tower Triangulation:\n" +
                                   "  - Pros: Radio signals penetrate concrete walls; negligible additional energy draw (< 2 mA) by piggybacking on active cellular connection.\n" +
                                   "  - Cons: Coarse precision (200m–1km in urban Kampala; 5–20km in rural areas); strictly dependent on base station tower density.",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Legal & Regulatory Framework in Uganda
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(colorScheme.secondary))
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Policy, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LEGAL CONSTRAINTS IN UGANDA (SWE2105 ETHICS)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.04.sp,
                                color = colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• Regulation of Interception of Communications Act 2010 (Cap 101): True carrier-level triangulation (TDOA/AoA) requires direct telecom core access. MTN and Airtel Uganda do not expose triangulation endpoints publicly. Under Section 5, only designated security agencies with a judicial warrant issued by a High Court judge can compel carrier-level tracking.\n\n" +
                                   "• Data Protection and Privacy Act 2019 (Cap 97): GPS coordinates and Cell IDs constitute Personal Data under Section 2. Collection requires informed opt-in consent (Section 7), strict purpose limitation, and secure on-device persistence.\n\n" +
                                   "• Architectural Implementation: Our project implements Google Play Services Fused Location + On-device TelephonyManager Cell ID telemetry with OpenStreetMap, complying with Uganda laws without unauthorized carrier interception.",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
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

enum class TechOption(
    val shortLabel: String,
    val title: String,
    val summary: String,
    val accuracy: String,
    val batteryDrain: String,
    val indoorRating: String,
    val realTimeCapable: Boolean
) {
    GPS_FUSED(
        shortLabel = "GPS Fused",
        title = "Google Fused Location Provider (GPS + WiFi + Cell)",
        summary = "Fuses multi-constellation GNSS satellites, ambient WiFi beacons, and cellular base stations adaptively.",
        accuracy = "2 – 5 meters",
        batteryDrain = "Adaptive (5-20 mA)",
        indoorRating = "Good (WiFi fallbk)",
        realTimeCapable = true
    ),
    GNSS_RAW(
        shortLabel = "Raw GPS",
        title = "Direct Satellite GNSS (GPS / Galileo / GLONASS)",
        summary = "Direct radio reception from 20,000 km altitude satellites with trilateration calculation on device chip.",
        accuracy = "2 – 5 meters",
        batteryDrain = "High (25 mA)",
        indoorRating = "Poor (blocked)",
        realTimeCapable = true
    ),
    CELL_ID(
        shortLabel = "Cell ID",
        title = "Cell ID Lookup (TelephonyManager + OpenCellID)",
        summary = "Reads serving cell tower CID and queries base station coordinate database. Shows tower coverage centroid.",
        accuracy = "200m – 5km",
        batteryDrain = "Minimal (< 1 mA)",
        indoorRating = "Excellent",
        realTimeCapable = false
    ),
    CELL_TRIANG(
        shortLabel = "Cell Triang.",
        title = "True Carrier Triangulation (TDOA / AoA / OTDOA)",
        summary = "Time-Difference-Of-Arrival calculated across 3+ synchronized carrier towers. Restricted by Uganda Interception Act 2010.",
        accuracy = "50 – 300 meters",
        batteryDrain = "Low",
        indoorRating = "Very Good",
        realTimeCapable = true
    ),
    WIFI_WPS(
        shortLabel = "WiFi WPS",
        title = "WiFi Positioning System (BSSID Trilateration)",
        summary = "Matches scanned WiFi router MAC addresses against crowdsourced global mapping databases.",
        accuracy = "10 – 20 meters",
        batteryDrain = "Low (~2 mA)",
        indoorRating = "Excellent",
        realTimeCapable = true
    ),
    IP_GEO(
        shortLabel = "Option 1 (IP)",
        title = "Option 1: IP Geolocation (WHOIS / BGP / MaxMind)",
        summary = "Queries public IP address against geo-IP registry. Fails completely on mobile CGNAT and VPNs.",
        accuracy = "50 – 250 km",
        batteryDrain = "None",
        indoorRating = "N/A (Network)",
        realTimeCapable = false
    )
}
