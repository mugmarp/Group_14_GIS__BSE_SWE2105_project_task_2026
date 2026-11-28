package com.geotrace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapFocusTarget
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineMapRegion
import com.geotrace.navigation.OfflineRoutingPackManager
import com.geotrace.service.OfflineMapManager
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel
import org.osmdroid.util.BoundingBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class OfflineAreaMode(val label: String) {
    VIEWPORT("Map View"),
    AROUND_2KM("2 km Me"),
    AROUND_5KM("5 km Me"),
    ROUTE_CORRIDOR("Route Strip")
}

private enum class OfflineDetailTier(val label: String, val minZoom: Int, val maxZoom: Int) {
    STANDARD("Std (Z12–16)", 12, 16),
    HIGH("High (Z13–17)", 13, 17)
}

@Composable
fun OfflineMapsDialog(
    viewModel: TrackerViewModel,
    currentLocation: LocationPoint?,
    currentMapBounds: BoundingBox?,
    onJumpToRegion: (OfflineMapRegion, MapFocusTarget) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val settings by viewModel.settings.collectAsState()
    val savedRegions by viewModel.offlineMapRegions.collectAsState()
    val offlinePlacesCountByRegion by viewModel.offlinePlacesCountByRegion.collectAsState()
    val poiIndexingRegionIds by viewModel.poiIndexingRegionIds.collectAsState()
    val offlineRoutingPacks by viewModel.offlineRoutingPacks.collectAsState()
    val routingIndexingRegionIds by viewModel.routingIndexingRegionIds.collectAsState()
    val offlineRoutingSizeMb by viewModel.offlineRoutingSizeMb.collectAsState()
    val offlineRoutingStatus by viewModel.offlineRoutingStatus.collectAsState()
    val routingPackByRegion = remember(offlineRoutingPacks) {
        offlineRoutingPacks.associateBy { it.regionId }
    }
    val progress by viewModel.offlineDownloadProgress.collectAsState()
    val cacheSizeMb by viewModel.offlineCacheSizeMb.collectAsState()
    val offlinePoiStatus by viewModel.offlinePoiStatus.collectAsState()
    val importedRoute by viewModel.importedRoute.collectAsState()
    val inspectedTrail by viewModel.inspectedTrail.collectAsState()
    val activeTrailPoints by viewModel.activeTrailPoints.collectAsState()

    val activeCorridorPoints = remember(importedRoute, inspectedTrail, activeTrailPoints) {
        when {
            (importedRoute?.points?.size ?: 0) >= 2 -> importedRoute!!.points
            (inspectedTrail?.points?.size ?: 0) >= 2 -> inspectedTrail!!.points
            activeTrailPoints.size >= 2 -> activeTrailPoints
            else -> emptyList()
        }
    }
    val activeCorridorLabel = remember(importedRoute, inspectedTrail) {
        importedRoute?.name ?: inspectedTrail?.session?.name ?: "Active Trail"
    }

    val downloadableTypes = remember {
        MapTileType.values().filter { it.supportsOfflineBulkDownload }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedType by rememberSaveable {
        mutableStateOf(
            if (settings.mapTileType.supportsOfflineBulkDownload) settings.mapTileType else MapTileType.HYBRID
        )
    }
    var areaMode by rememberSaveable {
        mutableStateOf(
            when {
                activeCorridorPoints.size >= 2 -> OfflineAreaMode.ROUTE_CORRIDOR
                currentMapBounds != null -> OfflineAreaMode.VIEWPORT
                currentLocation != null -> OfflineAreaMode.AROUND_2KM
                else -> OfflineAreaMode.VIEWPORT
            }
        )
    }
    var detailTier by rememberSaveable { mutableStateOf(OfflineDetailTier.STANDARD) }
    var corridorHalfWidthKm by rememberSaveable { mutableStateOf(1.5) }
    var includeOfflineRoads by rememberSaveable { mutableStateOf(true) }
    var regionName by rememberSaveable { mutableStateOf("") }
    var showClearCacheConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshTileCacheSize()
    }

    LaunchedEffect(progress.isDownloading) {
        if (progress.isDownloading) {
            regionName = ""
        }
    }

    val targetBox: BoundingBox? = remember(areaMode, currentMapBounds, currentLocation) {
        when (areaMode) {
            OfflineAreaMode.VIEWPORT -> currentMapBounds
            OfflineAreaMode.AROUND_2KM -> currentLocation?.let {
                viewModel.computeBoundingBoxAround(it.latitude, it.longitude, 2.0)
            }
            OfflineAreaMode.AROUND_5KM -> currentLocation?.let {
                viewModel.computeBoundingBoxAround(it.latitude, it.longitude, 5.0)
            }
            OfflineAreaMode.ROUTE_CORRIDOR -> null
        }
    }

    val corridorEstimate by produceState<Pair<Int, Float>?>(
        initialValue = null,
        key1 = areaMode,
        key2 = activeCorridorPoints,
        key3 = Triple(corridorHalfWidthKm, selectedType, detailTier)
    ) {
        value = if (areaMode == OfflineAreaMode.ROUTE_CORRIDOR && activeCorridorPoints.size >= 2) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                viewModel.estimateCorridorRegion(
                    points = activeCorridorPoints,
                    halfWidthKm = corridorHalfWidthKm,
                    tileType = selectedType,
                    minZoom = detailTier.minZoom,
                    maxZoom = detailTier.maxZoom
                )
            }
        } else {
            null
        }
    }
    val estimate = remember(areaMode, targetBox, corridorEstimate, selectedType, detailTier) {
        if (areaMode == OfflineAreaMode.ROUTE_CORRIDOR) {
            corridorEstimate
        } else {
            targetBox?.let {
                viewModel.estimateOfflineRegion(it, selectedType, detailTier.minZoom, detailTier.maxZoom)
            }
        }
    }
    val estTiles = estimate?.first ?: 0
    val estMb = estimate?.second ?: 0f
    val exceedsCap = estTiles > OfflineMapManager.MAX_TILES_PER_DOWNLOAD
    val maxRoadSpanDeg = OfflineRoutingPackManager.MAX_REGION_HALF_SPAN_DEG * 2.0
    val isRoadAreaClipped = remember(areaMode, targetBox, includeOfflineRoads) {
        if (includeOfflineRoads && areaMode != OfflineAreaMode.ROUTE_CORRIDOR && targetBox != null) {
            val latSpan = abs(targetBox.latNorth - targetBox.latSouth)
            val lonSpan = if (targetBox.lonWest <= targetBox.lonEast) {
                targetBox.lonEast - targetBox.lonWest
            } else {
                (180.0 - targetBox.lonWest) + (targetBox.lonEast + 180.0)
            }
            latSpan > maxRoadSpanDeg || lonSpan > maxRoadSpanDeg
        } else {
            false
        }
    }

    val suggestedName = remember(areaMode, selectedType, activeCorridorLabel) {
        val areaLabel = when (areaMode) {
            OfflineAreaMode.VIEWPORT -> "Map Area"
            OfflineAreaMode.AROUND_2KM -> "2km Field Zone"
            OfflineAreaMode.AROUND_5KM -> "5km Sector"
            OfflineAreaMode.ROUTE_CORRIDOR -> "$activeCorridorLabel Corridor"
        }
        "$areaLabel (${selectedType.label})"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            val adaptiveMaxHeight = (maxHeight * 0.88f).coerceAtMost(680.dp)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = adaptiveMaxHeight)
                    .testTag("offline_maps_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "Offline Maps",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Offline Maps Manager",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Pre-download Satellite, Hybrid, Topo or HOT areas for off-grid use",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_offline_maps_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Offline Maps")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Active Download Progress Card
                    if (progress.isDownloading) {
                        val pct = if (progress.totalTiles > 0) {
                            (progress.completedTiles.toFloat() / progress.totalTiles.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = customColors.statusActiveBg,
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth().testTag("offline_download_progress_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading '${progress.regionName}'",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    GeoTraceStatusBadge(
                                        text = "${(pct * 100).toInt()}%",
                                        tone = StatusBadgeTone.ACTIVE
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    color = colorScheme.primary,
                                    trackColor = colorScheme.onSurface.copy(alpha = 0.14f),
                                    drawStopIndicator = {},
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${progress.completedTiles} / ${progress.totalTiles} tiles (Zoom ${progress.currentZoom})",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = onDismiss,
                                        shape = CircleShape,
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("hide_offline_download_btn")
                                    ) {
                                        Text("Hide", fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        onClick = { viewModel.cancelOfflineRegionDownload() },
                                        shape = CircleShape,
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_offline_download_btn")
                                    ) {
                                        Text("Cancel", color = colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = "Downloads keep running in the background if you close this window.",
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (progress.errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.errorContainer,
                            border = BorderStroke(1.dp, colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = progress.errorMessage!!,
                                        fontSize = 11.sp,
                                        color = colorScheme.onErrorContainer
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.clearOfflineDownloadError() },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Dismiss", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (offlinePoiStatus != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth().testTag("offline_poi_status_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.OfflinePin, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = offlinePoiStatus!!,
                                        fontSize = 11.sp,
                                        color = colorScheme.onSecondaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.clearOfflinePoiStatus() },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (offlineRoutingStatus != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth().testTag("offline_routing_status_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = offlineRoutingStatus!!,
                                        fontSize = 11.sp,
                                        color = colorScheme.onTertiaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.clearOfflineRoutingStatus() },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // New Region Configurator
                    val offlineChipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary,
                        containerColor = colorScheme.surface,
                        labelColor = colorScheme.onSurface,
                        disabledSelectedContainerColor = colorScheme.primary.copy(alpha = 0.25f),
                        disabledContainerColor = customColors.surfaceContainerLow.copy(alpha = 0.6f),
                        disabledLabelColor = colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "DOWNLOAD NEW REGION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.05.sp,
                                color = colorScheme.primary
                            )

                            // Area Selection
                            Text("1. Target Area", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OfflineAreaMode.values().toList().chunked(2).forEach { rowModes ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowModes.forEach { mode ->
                                            val enabled = when (mode) {
                                                OfflineAreaMode.VIEWPORT -> currentMapBounds != null
                                                OfflineAreaMode.AROUND_2KM, OfflineAreaMode.AROUND_5KM -> currentLocation != null
                                                OfflineAreaMode.ROUTE_CORRIDOR -> activeCorridorPoints.size >= 2
                                            }
                                            val selected = areaMode == mode
                                            FilterChip(
                                                selected = selected,
                                                enabled = enabled && !progress.isDownloading,
                                                onClick = { areaMode = mode },
                                                label = { Text(mode.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                                shape = CircleShape,
                                                colors = offlineChipColors,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("offline_area_${mode.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }
                            if (areaMode == OfflineAreaMode.ROUTE_CORRIDOR && activeCorridorPoints.size >= 2) {
                                Text(
                                    text = "Caches a ±${String.format(Locale.US, "%.1f", corridorHalfWidthKm)} km strip & POIs along \"$activeCorridorLabel\".",
                                    fontSize = 10.sp,
                                    color = colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(1.0 to "±1 km", 1.5 to "±1.5 km", 3.0 to "±3 km").forEach { (widthKm, label) ->
                                        FilterChip(
                                            selected = corridorHalfWidthKm == widthKm,
                                            enabled = !progress.isDownloading,
                                            onClick = { corridorHalfWidthKm = widthKm },
                                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            shape = CircleShape,
                                            colors = offlineChipColors,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        )
                                    }
                                }
                            }

                            // Tile Layer Selection
                            Text("2. Map Layer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                downloadableTypes.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowItems.forEach { type ->
                                            val selected = selectedType == type
                                            FilterChip(
                                                selected = selected,
                                                enabled = !progress.isDownloading,
                                                onClick = { selectedType = type },
                                                label = { Text(type.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                                shape = CircleShape,
                                                colors = offlineChipColors,
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "Note: Standard OSM Streets caches tiles automatically as you pan/zoom online (OSM policy restricts bulk street scraping).",
                                fontSize = 10.sp,
                                color = colorScheme.onSurfaceVariant
                            )

                            // Detail Tier
                            Text("3. Detail Level", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OfflineDetailTier.values().forEach { tier ->
                                    FilterChip(
                                        selected = detailTier == tier,
                                        enabled = !progress.isDownloading,
                                        onClick = { detailTier = tier },
                                        label = { Text(tier.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                        shape = CircleShape,
                                        colors = offlineChipColors,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    )
                                }
                            }

                            FilterChip(
                                selected = includeOfflineRoads,
                                enabled = !progress.isDownloading,
                                onClick = { includeOfflineRoads = !includeOfflineRoads },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (includeOfflineRoads) {
                                            "Include Offline Turn-by-Turn Road Network (ON)"
                                        } else {
                                            "Include Offline Turn-by-Turn Road Network (OFF)"
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                shape = CircleShape,
                                colors = offlineChipColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("toggle_include_offline_roads")
                            )

                            if (isRoadAreaClipped) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("offline_road_clipped_warning")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = colorScheme.secondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Road pack covers center ~40×40 km (0.36° × 0.36°). Use Route Strip for longer trips.",
                                            fontSize = 10.sp,
                                            color = colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = regionName,
                                onValueChange = { regionName = it.take(40) },
                                label = { Text("Region Name (Optional)") },
                                placeholder = { Text(suggestedName) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { keyboardController?.hide() }
                                ),
                                enabled = !progress.isDownloading,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().testTag("offline_region_name_input")
                            )
                        }
                    }

                    // Saved Offline Regions List
                    Text(
                        text = "DOWNLOADED OFFLINE REGIONS (${savedRegions.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    if (savedRegions.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = customColors.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No offline regions downloaded yet. Tiles viewed while online are also cached automatically.",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        savedRegions.forEach { region ->
                            val pack = routingPackByRegion[region.id]
                            OfflineRegionCard(
                                region = region,
                                poiCount = offlinePlacesCountByRegion[region.id] ?: 0,
                                roadPack = pack,
                                isDownloading = progress.isDownloading,
                                isIndexingPois = region.id in poiIndexingRegionIds,
                                isIndexingRoads = region.id in routingIndexingRegionIds,
                                onViewOnMap = {
                                    viewModel.updateMapTileType(region.tileType)
                                    onJumpToRegion(
                                        region,
                                        MapFocusTarget(
                                            id = region.id,
                                            label = region.name,
                                            latitude = region.centerLat,
                                            longitude = region.centerLng,
                                            subtitle = "Offline ${region.tileType.label} (Z${region.minZoom}–${region.maxZoom})",
                                            targetZoom = region.maxZoom.toDouble().coerceIn(13.0, 17.5),
                                            bboxNorth = region.northLat,
                                            bboxSouth = region.southLat,
                                            bboxEast = region.eastLng,
                                            bboxWest = region.westLng
                                        )
                                    )
                                    onDismiss()
                                },
                                onRefreshPois = { viewModel.refreshOfflineRegionPois(region) },
                                onRefreshRoads = { viewModel.refreshOfflineRegionRoads(region) },
                                onCancelRoads = { viewModel.cancelOfflineRegionRoads(region.id) },
                                onDelete = { viewModel.deleteOfflineMapRegion(region.id) }
                            )
                        }
                    }
                }

                // Sticky Primary Action & Estimate Bar (always visible above cache footer)
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (exceedsCap) colorScheme.errorContainer.copy(alpha = 0.6f) else customColors.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (exceedsCap) colorScheme.error else colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val hasValidAreaTarget = if (areaMode == OfflineAreaMode.ROUTE_CORRIDOR) {
                        activeCorridorPoints.size >= 2
                    } else {
                        targetBox != null
                    }
                    val summaryText = when {
                        areaMode == OfflineAreaMode.ROUTE_CORRIDOR && activeCorridorPoints.size < 2 ->
                            "Import, inspect, or record a trail to cache a route corridor."
                        areaMode == OfflineAreaMode.ROUTE_CORRIDOR && corridorEstimate == null ->
                            "Estimating route corridor tiles…"
                        areaMode != OfflineAreaMode.ROUTE_CORRIDOR && targetBox == null ->
                            "Open the Map tab or wait for a GPS fix to compute area bounds."
                        exceedsCap ->
                            "Area too large ($estTiles tiles > ${OfflineMapManager.MAX_TILES_PER_DOWNLOAD} max). Zoom in closer on the map or choose 2 km / Standard."
                        else ->
                            "Estimated download: $estTiles tiles (~${String.format(Locale.US, "%.1f", estMb)} MB)"
                    }
                    Text(
                        text = summaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (exceedsCap) colorScheme.error else colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val canDownload = if (areaMode == OfflineAreaMode.ROUTE_CORRIDOR) {
                    activeCorridorPoints.size >= 2
                } else {
                    targetBox != null
                }

                Button(
                    onClick = {
                        val finalName = regionName.trim().ifBlank { suggestedName }
                        if (areaMode == OfflineAreaMode.ROUTE_CORRIDOR) {
                            if (activeCorridorPoints.size < 2) return@Button
                            viewModel.startCorridorOfflineDownload(
                                name = finalName,
                                points = activeCorridorPoints,
                                halfWidthKm = corridorHalfWidthKm,
                                tileType = selectedType,
                                minZoom = detailTier.minZoom,
                                maxZoom = detailTier.maxZoom,
                                includeRoads = includeOfflineRoads
                            )
                        } else {
                            val box = targetBox ?: return@Button
                            viewModel.startOfflineRegionDownload(
                                name = finalName,
                                box = box,
                                tileType = selectedType,
                                minZoom = detailTier.minZoom,
                                maxZoom = detailTier.maxZoom,
                                includeRoads = includeOfflineRoads
                            )
                        }
                    },
                    enabled = canDownload && !exceedsCap && !progress.isDownloading && estTiles > 0,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_offline_download_btn")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (progress.isDownloading) "Download in Progress..." else "Download Map Region",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (exceedsCap && includeOfflineRoads && canDownload && !progress.isDownloading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FilledTonalButton(
                        onClick = {
                            val finalName = regionName.trim().ifBlank {
                                "${suggestedName.substringBefore(" (")} (Roads)"
                            }
                            if (areaMode == OfflineAreaMode.ROUTE_CORRIDOR) {
                                if (activeCorridorPoints.size < 2) return@FilledTonalButton
                                viewModel.startRoadOnlyRegionDownload(
                                    name = finalName,
                                    box = null,
                                    corridorPoints = activeCorridorPoints,
                                    halfWidthKm = corridorHalfWidthKm,
                                    tileType = selectedType
                                )
                            } else {
                                val box = targetBox ?: return@FilledTonalButton
                                viewModel.startRoadOnlyRegionDownload(
                                    name = finalName,
                                    box = box,
                                    corridorPoints = null,
                                    halfWidthKm = 1.5,
                                    tileType = selectedType
                                )
                            }
                            regionName = ""
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_road_only_download_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Offline Roads Only (Bypass 2,500 Tile Limit)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // Storage Cache Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tiles: ${String.format(Locale.US, "%.1f", cacheSizeMb)} MB • Roads: ${String.format(Locale.US, "%.2f", offlineRoutingSizeMb)} MB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Limit ${OfflineMapManager.MAX_CACHE_BYTES_MB} MB • Private app storage",
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showClearCacheConfirm = true },
                        enabled = !progress.isDownloading && (cacheSizeMb > 0.05f || offlineRoutingSizeMb > 0.01f || savedRegions.isNotEmpty()),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("clear_tile_cache_btn")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Cache", color = colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Clear Offline Cache?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Deletes all cached map tiles (${String.format(Locale.US, "%.1f", cacheSizeMb)} MB), offline routing road packs (${String.format(Locale.US, "%.2f", offlineRoutingSizeMb)} MB), and clears downloaded regions.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTileCache()
                        showClearCacheConfirm = false
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearCacheConfirm = false },
                    shape = CircleShape
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun OfflineRegionCard(
    region: OfflineMapRegion,
    poiCount: Int,
    roadPack: com.geotrace.data.cache.OfflineRoutingPackEntity?,
    isDownloading: Boolean,
    isIndexingPois: Boolean,
    isIndexingRoads: Boolean,
    onViewOnMap: () -> Unit,
    onRefreshPois: () -> Unit,
    onRefreshRoads: () -> Unit,
    onCancelRoads: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val dateStr = remember(region.downloadedAtMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(region.downloadedAtMs))
    }
    val maxRoadSpanDeg = OfflineRoutingPackManager.MAX_REGION_HALF_SPAN_DEG * 2.0
    val isNonCorridorClipped = remember(region) {
        if (region.isCorridor) {
            false
        } else {
            val latSpan = abs(region.northLat - region.southLat)
            val lonSpan = if (region.westLng <= region.eastLng) {
                region.eastLng - region.westLng
            } else {
                (180.0 - region.westLng) + (region.eastLng + 180.0)
            }
            latSpan > maxRoadSpanDeg || lonSpan > maxRoadSpanDeg
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().testTag("offline_region_${region.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (region.isCorridor) Icons.Default.Route else Icons.Default.OfflinePin,
                    contentDescription = if (region.isCorridor) "Saved Offline Corridor" else "Saved Offline Region",
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = region.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (region.isCorridor) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "STRIP",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (region.tileCount == 0) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "ROADS",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (region.tileCount == 0) {
                            "Road Pack Only • $poiCount POIs • $dateStr"
                        } else {
                            "${region.tileType.label} • Z${region.minZoom}–${region.maxZoom} • ~${String.format(Locale.US, "%.1f", region.estimatedSizeMb)} MB • $poiCount POIs • $dateStr"
                        },
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isIndexingRoads -> "Building offline road graph..."
                            roadPack != null -> {
                                val kb = (roadPack.fileSizeBytes / 1024f)
                                val sizeStr = if (kb >= 1024f) {
                                    String.format(Locale.US, "%.2f MB", kb / 1024f)
                                } else {
                                    String.format(Locale.US, "%.0f KB", kb)
                                }
                                if (isNonCorridorClipped) {
                                    "Roads Ready (center ~40×40 km): ${roadPack.edgeCount} segments ($sizeStr)"
                                } else {
                                    "Roads Ready: ${roadPack.edgeCount} segments ($sizeStr)"
                                }
                            }
                            else -> "Roads Not Cached — tap route icon to download offline roads"
                        },
                        fontSize = 10.sp,
                        fontWeight = if (roadPack != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (roadPack != null) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onViewOnMap,
                    modifier = Modifier.size(48.dp).testTag("jump_offline_region_${region.id}")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "View Region on Map", tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { if (isIndexingRoads) onCancelRoads() else onRefreshRoads() },
                    enabled = if (isIndexingRoads) true else !isDownloading,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(if (isIndexingRoads) "cancel_region_roads_${region.id}" else "refresh_region_roads_${region.id}")
                ) {
                    if (isIndexingRoads) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.tertiary
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Road Indexing",
                                tint = colorScheme.error,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = if (roadPack != null) "Refresh Offline Road Pack" else "Download Offline Road Pack",
                            tint = colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshPois,
                    enabled = !isDownloading && !isIndexingPois,
                    modifier = Modifier.size(48.dp).testTag("refresh_region_pois_${region.id}")
                ) {
                    if (isIndexingPois) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.secondary
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Offline POIs", tint = colorScheme.secondary, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp).testTag("delete_offline_region_${region.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove Region Entry", tint = colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
