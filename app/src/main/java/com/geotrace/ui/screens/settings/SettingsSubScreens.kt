package com.geotrace.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geotrace.BuildConfig
import com.geotrace.model.AppThemeMode
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.OfflineMapRegion
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackerSettings
import com.geotrace.service.GeofenceApiStatus
import com.geotrace.service.LocationForegroundService
import com.geotrace.service.OfflineMapManager
import com.geotrace.service.UpdateStatus
import com.geotrace.ui.components.GeoTraceTopBar
import com.geotrace.ui.components.HeadingUpToggleRow
import com.geotrace.ui.components.ScreenListContentPadding
import com.geotrace.ui.components.TileLayerPickerList
import com.geotrace.ui.components.adaptiveContentWidth
import com.geotrace.ui.screens.devices.hasBackgroundLocationPermission
import com.geotrace.ui.screens.devices.isIgnoringBatteryOptimizations
import com.geotrace.ui.screens.devices.openAppNotificationSettings
import com.geotrace.ui.screens.devices.openBatteryOptimizationSettings
import com.geotrace.ui.screens.formatCoordinates
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.GroupViewModel
import com.geotrace.viewmodel.TrackerViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnitsDisplaySettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Box(
        modifier = Modifier
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
            item {
                GeoTraceTopBar(
                    title = SettingsCategory.UNITS_DISPLAY.title,
                    subtitle = SettingsCategory.UNITS_DISPLAY.subtitle,
                    backContentDescription = "Back to Settings Hub",
                    backTestTag = "settings_sub_back_btn",
                    onBack = onBack
                )
            }

            item {
                SettingsSectionCard(
                    title = "Units & Coordinate Formats",
                    icon = Icons.Default.Speed
                ) {
                Text(
                    text = "Velocity Scalar Unit",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val units = SpeedUnit.entries
                    units.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = settings.speedUnit == unit,
                            onClick = { viewModel.updateSpeedUnit(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = unit.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Coordinate Presentation",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val formats = CoordinateFormat.entries
                    formats.forEachIndexed { index, fmt ->
                        SegmentedButton(
                            selected = settings.coordinateFormat == fmt,
                            onClick = { viewModel.updateCoordinateFormat(fmt) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (fmt == CoordinateFormat.DECIMAL) "Decimal (DD)" else "DMS (Deg/Min)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sample: ${formatCoordinates(0.33400, 32.56750, settings.coordinateFormat)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsSectionCard(
                title = "Appearance & Hardware Screen",
                icon = Icons.Default.BrightnessHigh
            ) {
                Text(
                    text = "Display Theme",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = AppThemeMode.entries
                    modes.forEachIndexed { index, mode ->
                        val label = when (mode) {
                            AppThemeMode.LIGHT -> "Daylight"
                            AppThemeMode.DARK -> "Night"
                            AppThemeMode.SYSTEM -> "System"
                        }
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("theme_mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingToggleRow(
                    title = "Keep Screen Active During Recording",
                    description = "Prevents display sleep during active field tracking sessions",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
                )
            }
        }
        }
    }
}

@Composable
internal fun MapLayersSettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    offlineMapRegions: List<OfflineMapRegion>,
    offlineCacheSizeMb: Float,
    onOpenOfflineMaps: () -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Box(
        modifier = Modifier
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
            item {
                GeoTraceTopBar(
                    title = SettingsCategory.MAP_LAYERS.title,
                    subtitle = SettingsCategory.MAP_LAYERS.subtitle,
                    backContentDescription = "Back to Settings Hub",
                    backTestTag = "settings_sub_back_btn",
                    onBack = onBack
                )
            }

            item {
                SettingsSectionCard(
                    title = "Cartographic Tile Layer Sources",
                    icon = Icons.Default.Layers
                ) {
                Text(
                    text = "Select the active base map renderer for the live map viewport",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                TileLayerPickerList(
                    selectedTileType = settings.mapTileType,
                    onSelectTileType = { viewModel.updateMapTileType(it) }
                )
            }
        }

        item {
            SettingsSectionCard(
                title = "Offline Maps & Viewport Overlays",
                icon = Icons.Default.CloudDownload
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = customColors.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Offline Maps & Tile Cache",
                                    color = colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${offlineMapRegions.size} saved regions • ${String.format(Locale.US, "%.1f", offlineCacheSizeMb)} / ${OfflineMapManager.MAX_CACHE_BYTES_MB} MB",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onOpenOfflineMaps,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_open_offline_maps_btn")
                        ) {
                            Text("Manage", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SettingToggleRow(
                    title = "Draw Accuracy Confidence Ring",
                    description = "Circular 1-sigma DOP confidence halo around live fix",
                    checked = settings.showAccuracyRing,
                    onCheckedChange = { viewModel.toggleAccuracyRing(it) }
                )

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                SettingToggleRow(
                    title = "Auto-Center Camera on Live Telemetry",
                    description = "Smoothly pans map viewport as device updates position",
                    checked = settings.followUserLocation,
                    onCheckedChange = { viewModel.toggleFollowUser(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                HeadingUpToggleRow(
                    checked = settings.autoRotateHeadingUp,
                    onCheckedChange = { viewModel.toggleAutoRotateHeadingUp(it) },
                    testTag = "settings_heading_up_switch"
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MotionFilteringSettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val jitterCount by viewModel.filteredJitterCount.collectAsState()
    val jitterMeters by viewModel.filteredJitterMeters.collectAsState()

    Box(
        modifier = Modifier
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
            item {
                GeoTraceTopBar(
                    title = SettingsCategory.MOTION_FILTERING.title,
                    subtitle = SettingsCategory.MOTION_FILTERING.subtitle,
                    backContentDescription = "Back to Settings Hub",
                    backTestTag = "settings_sub_back_btn",
                    onBack = onBack
                )
            }

            item {
                SettingsSectionCard(
                    title = "Motion Thresholds & Jitter Filtering",
                    icon = Icons.Default.Tune
                ) {
                Text(
                    text = "Stationary Deadband Filter (${settings.stationaryJitterThresholdMeters}m)",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val deadbandOptions = listOf(
                        1.5f to "1.5m Balanced",
                        3.0f to "3.0m Strict",
                        0f to "Off (Raw)"
                    )
                    deadbandOptions.forEachIndexed { index, (threshold, label) ->
                        SegmentedButton(
                            selected = settings.stationaryJitterThresholdMeters == threshold,
                            onClick = { viewModel.updateJitterThreshold(threshold) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = deadbandOptions.size),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = customColors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ELIMINATED MULTIPATH DRIFT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered Stationary Spikes",
                                fontSize = 12.sp,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "$jitterCount spikes (${String.format(Locale.US, "%.1f", jitterMeters)} m)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Suppresses sub-threshold GPS wandering while stationary indoors or near tall buildings without losing genuine movement.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun CellularEngineSettingsPage(
    viewModel: TrackerViewModel,
    settings: TrackerSettings,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val hasDefaultSecret = BuildConfig.OPENCELLID_API_KEY.isNotBlank()
    val hasCustomOverride = settings.openCellIdApiKey.isNotBlank()
    val cachedCellTowerCount by viewModel.cachedCellTowerCount.collectAsState()
    val cachedLocationFixCount by viewModel.cachedLocationFixCount.collectAsState()
    var apiKeyInput by rememberSaveable(settings.openCellIdApiKey) {
        mutableStateOf(settings.openCellIdApiKey)
    }
    var overrideExpanded by rememberSaveable(hasCustomOverride) {
        mutableStateOf(hasCustomOverride)
    }

    Box(
        modifier = Modifier
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
            item {
                GeoTraceTopBar(
                    title = SettingsCategory.CELLULAR_ENGINE.title,
                    subtitle = SettingsCategory.CELLULAR_ENGINE.subtitle,
                    backContentDescription = "Back to Settings Hub",
                    backTestTag = "settings_sub_back_btn",
                    onBack = onBack
                )
            }

            item {
                SettingsSectionCard(
                    title = "Cellular Positioning & OpenCellID",
                    icon = Icons.Default.CellTower
                ) {
                SettingToggleRow(
                    title = "Show Serving Cell Tower Pin on Map",
                    description = "Renders approximate serving mast and radius ring when GPS lock drops",
                    checked = settings.showCellTowerPin,
                    onCheckedChange = { viewModel.toggleCellTowerPin(it) }
                )

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                val statusTitle = when {
                    hasCustomOverride -> "Custom Key Override Active"
                    hasDefaultSecret -> "GitHub CI Secret Active"
                    else -> "On-Device Fallback Mode"
                }
                val statusBadge = when {
                    hasCustomOverride -> "OVERRIDE"
                    hasDefaultSecret -> "CI SECRET SET"
                    else -> "LOCAL FALLBACK"
                }
                val statusColor = when {
                    hasCustomOverride -> colorScheme.secondary
                    hasDefaultSecret -> colorScheme.tertiary
                    else -> colorScheme.onSurfaceVariant
                }
                val statusDescription = when {
                    hasCustomOverride ->
                        "Temporarily overriding default with custom token (${settings.openCellIdApiKey.take(6)}…). Clearing this override restores ${if (hasDefaultSecret) "your GitHub CI secret default." else "on-device fallback."}"
                    hasDefaultSecret ->
                        "OpenCellID token is bundled automatically from your GitHub CI build secret. No manual entry required."
                    else ->
                        "This preview/local build has no injected CI secret — using on-device TelephonyManager tower IDs & local trilateration. GitHub Actions CI builds include your OPENCELLID_API_KEY secret automatically, or you can set a temporary override below."
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = customColors.surfaceContainerLow,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusTitle,
                                color = colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = statusColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = statusBadge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = statusDescription,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { overrideExpanded = !overrideExpanded }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("toggle_opencellid_override_btn"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom API Key Override (Optional)",
                                color = colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    hasCustomOverride -> "Override active — tap to edit or revert to default"
                                    hasDefaultSecret -> "Leave collapsed to keep using your GitHub CI secret"
                                    else -> "Optional temporary token override for preview testing"
                                },
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (overrideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (overrideExpanded) "Collapse override" else "Expand override",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = overrideExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("Custom Override Key (Optional)") },
                                placeholder = { Text("Enter temporary OpenCellID key") },
                                supportingText = {
                                    Text(
                                        text = if (apiKeyInput.isBlank()) {
                                            "Enter your personal OpenCellID access token"
                                        } else {
                                            "Token length: ${apiKeyInput.trim().length} characters"
                                        },
                                        fontSize = 11.sp
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        if (apiKeyInput.trim().isNotBlank()) {
                                            viewModel.updateOpenCellIdApiKey(apiKeyInput)
                                            Toast.makeText(
                                                context,
                                                "OpenCellID override saved & refreshing",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("opencellid_api_key_input")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (hasDefaultSecret) {
                                    "Only overrides your GitHub CI secret on this device. Clearing removes only this override and leaves the CI secret default active."
                                } else {
                                    "Stored locally as a temporary override. Clearing removes the override and returns to on-device fallback."
                                },
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = apiKeyInput.trim().isNotBlank(),
                                    onClick = {
                                        viewModel.updateOpenCellIdApiKey(apiKeyInput)
                                        Toast.makeText(
                                            context,
                                            "OpenCellID override saved & refreshing",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("save_opencellid_key_btn")
                                ) {
                                    Text("Save Override", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    enabled = hasCustomOverride || apiKeyInput.isNotBlank(),
                                    onClick = {
                                        apiKeyInput = ""
                                        viewModel.updateOpenCellIdApiKey("")
                                        Toast.makeText(
                                            context,
                                            if (hasDefaultSecret) "Override cleared — using GitHub CI secret default"
                                            else "Override cleared",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("clear_opencellid_key_btn")
                                ) {
                                    Text(
                                        text = if (hasDefaultSecret) "Use Default" else "Clear Override",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            item {
                SettingsSectionCard(
                    title = "Offline Telemetry Cache (Room)",
                    icon = Icons.Default.Storage
                ) {
                    Text(
                        text = "Persists resolved cell tower coordinates ($cachedCellTowerCount) and recent GPS/cell fixes ($cachedLocationFixCount) locally via Room so positioning, mast lookup, and SOS fallback stay available during connectivity loss.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        enabled = cachedCellTowerCount > 0 || cachedLocationFixCount > 0,
                        onClick = {
                            viewModel.clearOfflineTelemetryCache()
                            Toast.makeText(context, "Offline GPS & cell tower cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("clear_room_offline_cache_btn")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear Offline Cache ($cachedCellTowerCount towers • $cachedLocationFixCount fixes)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProtectionAlertsSettingsPage(
    viewModel: TrackerViewModel,
    groupViewModel: GroupViewModel?,
    settings: TrackerSettings,
    geofenceApiStatus: GeofenceApiStatus,
    permissionRefreshTick: Int,
    onRequestGeofencePermissions: () -> Unit,
    onRequestProtectionPermissions: () -> Unit,
    onRequestProtectionSignIn: () -> Unit,
    onRequestBgLocationDialog: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val hasLocPerm = remember(permissionRefreshTick) { LocationForegroundService.hasLocationPermission(context) }
    val hasNotifPerm = remember(permissionRefreshTick) { LocationForegroundService.hasNotificationPermission(context) }
    val hasBgPerm = remember(permissionRefreshTick) { hasBackgroundLocationPermission(context) }
    val isBatteryUnrestricted = remember(permissionRefreshTick) { isIgnoringBatteryOptimizations(context) }

    Box(
        modifier = Modifier
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
            item {
                GeoTraceTopBar(
                    title = SettingsCategory.PROTECTION_ALERTS.title,
                    subtitle = SettingsCategory.PROTECTION_ALERTS.subtitle,
                    backContentDescription = "Back to Settings Hub",
                    backTestTag = "settings_sub_back_btn",
                    onBack = onBack
                )
            }

            item {
                SettingsSectionCard(
                    title = "24/7 Background Protection",
                    icon = Icons.Default.Security
                ) {
                if (groupViewModel == null) {
                    Text(
                        text = "Sign in on the Devices tab to arm 24/7 anti-theft device sync and remote alarm controls.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    val isProtectionEnabled by groupViewModel.isBackgroundProtectionEnabled.collectAsState()
                    val isProtectionRunning by groupViewModel.isProtectionServiceRunning.collectAsState()
                    val isProtectionPaused by groupViewModel.isProtectionPaused.collectAsState()
                    val currentUser by groupViewModel.currentUser.collectAsState()

                    SettingToggleRow(
                        title = "Enable 24/7 Device Protection",
                        description = when {
                            currentUser == null -> "Sign in with Google to activate cloud heartbeat & Remote Ring"
                            !isProtectionEnabled -> "Only syncs location & Remote Ring while app is open"
                            isProtectionPaused -> "Paused — background telemetry stopped until resumed"
                            !isProtectionRunning -> "Standby — tap Start below or grant Location permission"
                            !hasNotifPerm -> "Running — allow Notifications for persistent status bar badge"
                            !hasBgPerm -> "Active — grant 'Allow all the time' location for reboot auto-start"
                            else -> "Active — persistent notification, cloud heartbeat & Remote Ring running"
                        },
                        checked = isProtectionEnabled && currentUser != null,
                        onCheckedChange = { enable ->
                            if (currentUser == null) {
                                onRequestProtectionSignIn()
                            } else if (enable && !hasLocPerm) {
                                onRequestProtectionPermissions()
                            } else {
                                groupViewModel.toggleBackgroundProtection(enable)
                                if (enable && !hasBgPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    onRequestBgLocationDialog()
                                }
                            }
                        },
                        testTag = "settings_background_protection_switch"
                    )

                    if (currentUser == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = onRequestProtectionSignIn,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_protection_sign_in_btn")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sign in with Google to Activate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isProtectionEnabled && currentUser != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (isProtectionPaused || !isProtectionRunning) {
                            FilledTonalButton(
                                onClick = { groupViewModel.resumeBackgroundProtection() },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_resume_protection_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isProtectionPaused) "Resume 24/7 Protection" else "Start 24/7 Service",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { groupViewModel.pauseBackgroundProtection() },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_pause_protection_btn")
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause 24/7 Protection", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isProtectionEnabled && currentUser != null && !hasNotifPerm) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openAppNotificationSettings(context) },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_fix_notif_btn")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow Notifications for Status Badge", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isProtectionEnabled && currentUser != null && !hasBgPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRequestBgLocationDialog,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Grant 'Allow All The Time' Location", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isProtectionEnabled && currentUser != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = customColors.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isBatteryUnrestricted) colorScheme.outlineVariant else customColors.warning.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBatteryUnrestricted) {
                                        "Battery Policy: Unrestricted (Doze Exempt)"
                                    } else {
                                        "Battery Policy: OS Optimized (May Throttle 24/7 Sync)"
                                    },
                                    color = if (isBatteryUnrestricted) colorScheme.onSurface else customColors.warning,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBatteryUnrestricted) {
                                        "Android allows background 24/7 protection heartbeats without aggressive Doze suspension."
                                    } else {
                                        "Some OEMs restrict background services in Doze mode. Set GeoTrace to Unrestricted in Android Battery Optimization settings for reliable 24/7 protection."
                                    },
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                                if (!isBatteryUnrestricted) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { openBatteryOptimizationSettings(context) },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("settings_battery_optimization_btn")
                                    ) {
                                        Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Review Battery Optimization Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Geofence Perimeter Alerts",
                icon = Icons.Default.NotificationsActive
            ) {
                SettingToggleRow(
                    title = "Enable Academic & Safe Zone Alerts",
                    description = geofenceApiStatus.statusMessage,
                    checked = settings.geofenceNotificationEnabled,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            viewModel.toggleGeofenceNotifications(false)
                        } else {
                            val missingFine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                            val missingNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            if (missingFine || missingNotif) {
                                onRequestGeofencePermissions()
                            } else {
                                viewModel.toggleGeofenceNotifications(true)
                                if (!hasBgPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    onRequestBgLocationDialog()
                                }
                            }
                        }
                    }
                )

                geofenceApiStatus.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(customColors.warning.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = err,
                            color = customColors.warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun UpdatesInfoSettingsPage(
    viewModel: TrackerViewModel,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val updateStatus by viewModel.updateStatus.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
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
            item {
                GeoTraceTopBar(
                    title = SettingsCategory.UPDATES_INFO.title,
                    subtitle = SettingsCategory.UPDATES_INFO.subtitle,
                    backContentDescription = "Back to Settings Hub",
                    backTestTag = "settings_sub_back_btn",
                    onBack = onBack
                )
            }

            item {
                SettingsSectionCard(
                    title = "App Version & GitHub Releases",
                    icon = Icons.Default.SystemUpdate
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed Version",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum"
                            ),
                            color = colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = { scope.launch { viewModel.appUpdateManager.checkForUpdates() } },
                        enabled = updateStatus !is UpdateStatus.Checking && updateStatus !is UpdateStatus.Downloading,
                        shape = CircleShape,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("check_updates_btn")
                    ) {
                        if (updateStatus is UpdateStatus.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...", fontSize = 12.sp)
                        } else {
                            Text("Check for Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (val status = updateStatus) {
                    is UpdateStatus.Idle -> {
                        Text(
                            text = "Tap Check for Updates to query official GeoTrace GitHub Releases.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateStatus.Checking -> {
                        Text(
                            text = "Querying GitHub release metadata...",
                            fontSize = 11.sp,
                            color = colorScheme.primary
                        )
                    }
                    is UpdateStatus.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.tertiary.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "You are on the latest release (${status.latestTag ?: "v${BuildConfig.VERSION_NAME}"}).",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.tertiary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is UpdateStatus.NoReleasesPublished -> {
                        Text(
                            text = "No public GitHub release tags published yet.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateStatus.UpdateAvailable -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = customColors.surfaceContainerLow),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Update Available: ${status.info.tagName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = status.info.body,
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 4
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.appUpdateManager.startDownload(status.info) },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("download_update_btn")
                                ) {
                                    Text("Download APK (${status.info.apkFileName})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading update...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = "${status.progressPercent}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { status.progressPercent / 100f },
                                color = colorScheme.primary,
                                trackColor = colorScheme.onSurface.copy(alpha = 0.14f),
                                drawStopIndicator = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.appUpdateManager.cancelDownload() },
                                shape = CircleShape,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("cancel_update_download_btn")
                            ) {
                                Text("Cancel Download", fontSize = 11.sp)
                            }
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Button(
                            onClick = { viewModel.appUpdateManager.installApk(status.apkFile) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.tertiary,
                                contentColor = colorScheme.onTertiary
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("install_apk_btn")
                        ) {
                            Text("Install Downloaded APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateStatus.InstallPermissionRequired -> {
                        Button(
                            onClick = { viewModel.appUpdateManager.openInstallPermissionSettings() },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Allow Unknown Apps to Install APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateStatus.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = status.message,
                                fontSize = 11.sp,
                                color = colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { viewModel.appUpdateManager.openReleasesPage() },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("open_releases_page_btn")
                ) {
                    Text("Open Releases Page in Browser", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Safe Telemetry Core Diagnostics",
                icon = Icons.Default.Info
            ) {
                Text(
                    text = "Engine: Fused + GNSS + Cellular Fallback (No Mock Injection)\n" +
                        "Offline Storage: SQLite Room Local Ledger + OSMDroid Cache\n" +
                        "Privacy & Security: Google Sign-In Gate + Zero-Trust Cloud Rules",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
        }
    }
}
