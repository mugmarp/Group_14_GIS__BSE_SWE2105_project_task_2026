package com.example.gistracker.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gistracker.model.CoordinateFormat
import com.example.gistracker.model.MapTileType
import com.example.gistracker.model.SpeedUnit
import com.example.gistracker.ui.theme.LocalCustomColors
import com.example.gistracker.viewmodel.TrackerViewModel

@Composable
fun SettingsScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val settings by viewModel.settings.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar / Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("settings_back_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Live Map",
                        tint = colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Telemetry & System Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                    Text(
                        text = "Hardware deadband, sensor units & cartographic overlays",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Units & Formats
        item {
            SettingsCard(
                title = "UNITS & COORDINATE FORMATS",
                icon = { Icon(Icons.Default.Speed, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.primary
            ) {
                // Speed Unit
                Text(
                    text = "Velocity Scalar Unit:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedUnit.values().forEach { unit ->
                        val selected = settings.speedUnit == unit
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateSpeedUnit(unit) },
                            label = { Text(unit.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Coordinate Format
                Text(
                    text = "Coordinate Presentation:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoordinateFormat.values().forEach { format ->
                        val selected = settings.coordinateFormat == format
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateCoordinateFormat(format) },
                            label = { Text(if (format == CoordinateFormat.DECIMAL) "DD (0.334°)" else "DMS (0°20'N)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 2: Cartographic Overlays
        item {
            SettingsCard(
                title = "CARTOGRAPHIC VIEWPORT & TILES",
                icon = { Icon(Icons.Default.Layers, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.secondary
            ) {
                Text(
                    text = "Map Tile Layer Source:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapTileType.values().forEach { tile ->
                        val selected = settings.mapTileType == tile
                        Row(
                            modifier = Modifier
                                 .fillMaxWidth()
                                 .background(if (selected) customColors.statusActiveBg else customColors.surfaceContainerLow, RoundedCornerShape(8.dp))
                                 .border(1.dp, if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                 .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tile.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) colorScheme.primary else colorScheme.onSurface
                                )
                                Text(
                                    text = tile.description,
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateMapTileType(tile) },
                                label = { Text(if (selected) "Active" else "Select", fontSize = 10.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary,
                                    selectedLabelColor = colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle 1: Accuracy Circle
                SettingToggleRow(
                    title = "Draw Accuracy Confidence Ring",
                    subtitle = "Render transparent circular radius reflecting GPS error bounds",
                    checked = settings.showAccuracyRing,
                    onCheckedChange = { viewModel.toggleAccuracyRing(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle 2: Follow User
                SettingToggleRow(
                    title = "Auto-Center Map on Position Fix",
                    subtitle = "Automatically re-center map when new coordinates arrive",
                    checked = settings.followUserLocation,
                    onCheckedChange = { viewModel.toggleFollowUser(it) }
                )
            }
        }

        // Section 3: Hardware Jitter & Power
        item {
            SettingsCard(
                title = "HARDWARE JITTER & POWER MANAGEMENT",
                icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(18.dp)) },
                accentColor = colorScheme.tertiary
            ) {
                Text(
                    text = "Stationary Deadband Filter (Jitter Rejection):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Discards micro-movements below threshold when standing still to prevent distance drift.",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val thresholds = listOf(1.5f to "1.5m (Std)", 3.0f to "3.0m (High)", 0.0f to "Off (Raw)")
                    thresholds.forEach { (thresh, label) ->
                        val selected = settings.stationaryJitterThresholdMeters == thresh
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateJitterThreshold(thresh) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Keep Screen On
                SettingToggleRow(
                    title = "Keep Screen Active During Recording",
                    subtitle = "Prevents device sleep while tracking real-time movement outdoors",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
                )
            }
        }

        // Section 4: Mission Telemetry Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "GEOTRACK SAFE TELEMETRY CORE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                        color = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Positioning Engine: FusedLocationProviderClient (GNSS Multi-Constellation)\n" +
                               "• Cellular Telemetry: TelephonyManager Hardware Radio Layer\n" +
                               "• Persistence: SQLite Local (zero-cloud leak, cap 97 compliant)\n" +
                               "• Active Recording Session: ${activeSession?.name ?: "Idle (Standby Mode)"}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: @Composable () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(accentColor))
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                        color = colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.onPrimary,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.outline,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}
