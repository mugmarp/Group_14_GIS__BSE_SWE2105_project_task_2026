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
import com.example.gistracker.ui.theme.MtnYellow

@Composable
fun ResearchAnalysisScreen(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    var selectedTech by remember { mutableStateOf(TechOption.GPS_FUSED) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SWE2105 Research Analysis",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                }
                Text(
                    text = "BSE SWE2105 Project Task Evaluation (Group 14, 2026)",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        // Interactive Technology Simulator Chip Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("tech_comparator_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = customColors.warning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Interactive Tracking Method Comparator",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
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
                                label = { Text(tech.shortLabel, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary,
                                    selectedLabelColor = colorScheme.onPrimary,
                                    containerColor = colorScheme.surfaceVariant,
                                    labelColor = colorScheme.onSurfaceVariant
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
                                label = { Text(tech.shortLabel, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary,
                                    selectedLabelColor = colorScheme.onPrimary,
                                    containerColor = colorScheme.surfaceVariant,
                                    labelColor = colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Selected Tech Telemetry Evaluation
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = selectedTech.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = selectedTech.summary,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TechMetric("ACCURACY", selectedTech.accuracy, if (selectedTech.realTimeCapable) colorScheme.tertiary else colorScheme.error, modifier = Modifier.weight(1f))
                                    TechMetric("BATTERY DRAIN", selectedTech.batteryDrain, customColors.warning, modifier = Modifier.weight(1f))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TechMetric("INDOOR FIX", selectedTech.indoorRating, colorScheme.secondary, modifier = Modifier.weight(1f))
                                    TechMetric("REAL-TIME REFRESH", if (selectedTech.realTimeCapable) "Continuous (1 Hz)" else "Static Snapshot", if (selectedTech.realTimeCapable) colorScheme.tertiary else colorScheme.error, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Option 1 Deep Dive: IP Address Geolocation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Option 1: Laptop Tracking via IP Address",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Why IP Geolocation Is Unsuitable For Real-Time Tracking:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Technical Mechanism: IP geolocation maps public IPv4/IPv6 addresses to geographic locations using RIR registry databases (WHOIS), BGP routing table announcements, latency trilateration, and crowdsourced WiFi BSSID caches (MaxMind, IP2Location).\n\n" +
                               "2. Severe Accuracy Limitations: On dynamic residential ISPs, accuracy is city-level (50–150 km error). On cellular mobile networks (MTN/Airtel CGNAT), IP addresses map to carrier data centers in Kampala regardless of where the device is in Uganda (up to 400+ km error in Gulu/Arua).\n\n" +
                               "3. Fundamental Defeat: A simple VPN, Tor proxy, or carrier APN switch instantly relocates the IP to foreign jurisdictions.\n\n" +
                               "4. Conclusion: IP lookup is a static snapshot, not a continuous real-time tracker.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Option 2 Deep Dive: Mobile Phone Tracking (GPS vs Cell Triangulation)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Option 2: Mobile Phone Tracking (GPS vs Cell Triangulation)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Engineering Pros and Cons Comparison:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• GPS/GNSS Satellites:\n" +
                               "  - Pros: High precision (2–5 m outdoors), global coverage without telecom dependency, exact bearing & speed telemetry.\n" +
                               "  - Cons: High power drain (20–30 mA), 30s cold-start lock, signal blockage inside concrete buildings and urban canyons.\n\n" +
                               "• Cell Tower Triangulation:\n" +
                               "  - Pros: Penetrates concrete structures; consumes near-zero battery since radio is already connected; works in heavy cloud cover.\n" +
                               "  - Cons: Coarse precision (200m–1km in urban Kampala, 5–20km in rural areas); depends on cellular base station density.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Uganda Legal Framework & Constraints
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Policy, contentDescription = null, tint = customColors.warning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Legal Constraints in Uganda (SWE2105 Ethics)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Regulation of Interception of Communications Act 2010 (Cap 101): True multi-tower triangulation (TDOA/AoA) requires direct carrier core access. MTN and Airtel Uganda do not provide public triangulation APIs. Under Section 5, only authorized state security agencies with a judicial warrant issued by a designated judge can compel carrier-level triangulation.\n\n" +
                               "• Data Protection and Privacy Act 2019 (Cap 97): GPS coordinates and Cell IDs constitute Personal Data under Section 2. Collection requires informed opt-in consent (Section 7), strict purpose limitation, and secure local storage with encrypted export.\n\n" +
                               "• Group 14 Architectural Choice: Implemented Google Play Services Fused Location + On-device TelephonyManager Cell ID lookup with OpenCellID open database, respecting all Uganda laws without requiring carrier interception privileges.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TechMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
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
