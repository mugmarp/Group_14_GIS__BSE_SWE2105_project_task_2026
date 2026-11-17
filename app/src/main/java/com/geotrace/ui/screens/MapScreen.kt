package com.geotrace.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.MapFocusTarget
import com.geotrace.model.MapTileType
import com.geotrace.model.SearchPlaceResult
import com.geotrace.model.SearchResultSource
import com.geotrace.model.TrackingMode
import com.geotrace.ui.components.MapViewComposable
import com.geotrace.ui.components.OfflineMapsDialog
import com.geotrace.ui.components.PlaceSearchDialog
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.GroupViewModel
import com.geotrace.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapScreen(
    viewModel: TrackerViewModel,
    groupViewModel: GroupViewModel? = null,
    onOpenMenu: () -> Unit,
    onNavigateToGroups: (() -> Unit)? = null,
    onReturnToGeofence: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val currentLocation by viewModel.currentLocation.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val trailPoints by viewModel.activeTrailPoints.collectAsState()
    val activeWaypoints by viewModel.activeWaypoints.collectAsState()
    val inspectedTrail by viewModel.inspectedTrail.collectAsState()
    val trackingMode by viewModel.trackingMode.collectAsState()
    val geofenceZones by viewModel.geofenceZones.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cellTowerLocation by viewModel.cellTowerLocation.collectAsState()
    val allCellTowers by viewModel.allCellTowerLocations.collectAsState()
    val offlineMapRegions by viewModel.offlineMapRegions.collectAsState()
    val offlineProgress by viewModel.offlineDownloadProgress.collectAsState()
    val currentMapBounds by viewModel.currentMapBoundingBox.collectAsState()
    val isPickingGeofenceCenter by viewModel.isPickingGeofenceCenter.collectAsState()
    val geofencePickDraft by viewModel.geofencePickDraft.collectAsState()
    val isMapFollowPaused by viewModel.isMapFollowPaused.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsState()
    val offlinePlacesCount by viewModel.offlinePlacesCount.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val selectedSearchPin by viewModel.selectedSearchPin.collectAsState()

    val currentUser by (groupViewModel?.currentUser ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val activeGroup by (groupViewModel?.activeGroup ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val groupMembers by (groupViewModel?.groupMembers ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val personalDevices by (groupViewModel?.personalDevices ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val nearbyPeers by (groupViewModel?.nearbyPeers ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val isGhostMode by (groupViewModel?.isGhostMode ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val isP2PEnabled by (groupViewModel?.isP2PEnabled ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val mapFocusTarget by (groupViewModel?.mapFocusTarget ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val currentDeviceId = groupViewModel?.currentDeviceId ?: ""
    val remotePersonalDevices = remember(personalDevices, currentDeviceId) {
        personalDevices.filter { it.deviceId != currentDeviceId && it.id != currentDeviceId }
    }
    val remoteGroupMembers = remember(groupMembers, currentUser?.uid, currentDeviceId) {
        groupMembers.filter { member ->
            val isSelf = currentUser?.uid != null &&
                member.userId == currentUser?.uid &&
                (member.deviceId.isBlank() || member.deviceId == currentDeviceId)
            !isSelf && (currentDeviceId.isBlank() || member.deviceId != currentDeviceId)
        }
    }
    val groupDeviceIds = remember(groupMembers) {
        groupMembers.map { it.deviceId }.filter { it.isNotBlank() }.toSet()
    }
    val deduplicatedPersonalDevices = remember(remotePersonalDevices, groupDeviceIds) {
        remotePersonalDevices.filter { it.deviceId !in groupDeviceIds }
    }
    val hasQuickLocateTargets = remoteGroupMembers.isNotEmpty() ||
        deduplicatedPersonalDevices.isNotEmpty() ||
        nearbyPeers.isNotEmpty()
    val deviceSearchTargets = remember(remoteGroupMembers, deduplicatedPersonalDevices, nearbyPeers) {
        buildList {
            remoteGroupMembers.filter { it.latitude != 0.0 || it.longitude != 0.0 }.forEach { m ->
                add(
                    SearchPlaceResult(
                        id = "grp_${m.id}",
                        name = m.displayName,
                        subtitle = "Group Member • Battery ${m.batteryPercent}%",
                        latitude = m.latitude,
                        longitude = m.longitude,
                        source = SearchResultSource.DEVICE_OR_PEER
                    )
                )
            }
            deduplicatedPersonalDevices.filter { it.latitude != 0.0 || it.longitude != 0.0 }.forEach { d ->
                add(
                    SearchPlaceResult(
                        id = "dev_${d.deviceId}",
                        name = d.deviceName.ifBlank { d.deviceModel },
                        subtitle = "${d.deviceModel} • Fleet Device",
                        latitude = d.latitude,
                        longitude = d.longitude,
                        source = SearchResultSource.DEVICE_OR_PEER
                    )
                )
            }
            nearbyPeers.filter { it.latitude != 0.0 || it.longitude != 0.0 }.forEach { p ->
                add(
                    SearchPlaceResult(
                        id = "p2p_${p.endpointId}",
                        name = p.displayName,
                        subtitle = "P2P Mesh Peer",
                        latitude = p.latitude,
                        longitude = p.longitude,
                        source = SearchResultSource.DEVICE_OR_PEER
                    )
                )
            }
        }
    }

    var showStartDialog by remember { mutableStateOf(false) }
    var showWaypointDialog by remember { mutableStateOf(false) }
    var waypointNameInput by remember { mutableStateOf("") }
    var waypointNoteInput by remember { mutableStateOf("") }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showOfflineMapsDialog by remember { mutableStateOf(false) }
    var showDevicesSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var sessionNameInput by remember { mutableStateOf("") }
    var isHeaderExpanded by remember { mutableStateOf(false) }
    var vectorCoordsCopied by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var liveRecordingNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(vectorCoordsCopied) {
        if (vectorCoordsCopied) {
            kotlinx.coroutines.delay(1600L)
            vectorCoordsCopied = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    androidx.compose.runtime.LaunchedEffect(activeSession?.id, activeSession?.isPaused) {
        if (activeSession != null) {
            liveRecordingNowMs = System.currentTimeMillis()
            while (activeSession != null && activeSession?.isPaused == false) {
                kotlinx.coroutines.delay(1000L)
                liveRecordingNowMs = System.currentTimeMillis()
            }
        }
    }


    // Intercept Back press while picking a Safe Zone center so user returns cleanly to GeofenceScreen
    androidx.activity.compose.BackHandler(enabled = isPickingGeofenceCenter) {
        groupViewModel?.clearMapFocusTarget()
        viewModel.cancelMapPick()
        onReturnToGeofence?.invoke()
    }

    // Keep screen awake only while actively recording (release when paused)
    val activity = context as? Activity
    DisposableEffect(settings.keepScreenOn, activeSession?.id, activeSession?.isPaused) {
        if (settings.keepScreenOn && activeSession != null && activeSession?.isPaused != true) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val resolvedFocusTarget = remember(
        mapFocusTarget,
        remoteGroupMembers,
        deduplicatedPersonalDevices,
        nearbyPeers,
        currentLocation,
        settings.speedUnit,
        nowMs
    ) {
        val target = mapFocusTarget ?: return@remember null
        val matchedMember = remoteGroupMembers.firstOrNull {
            it.id == target.id || it.userId == target.id || it.deviceId == target.id
        }
        if (matchedMember != null && (matchedMember.latitude != 0.0 || matchedMember.longitude != 0.0)) {
            val dist = currentLocation?.let { matchedMember.distanceToMeters(it.latitude, it.longitude) }
            val distStr = dist?.let { if (it < 1000f) "${it.toInt()}m" else String.format(Locale.US, "%.1fkm", it / 1000f) } ?: "--"
            val updatedMs = matchedMember.lastUpdated?.toDate()?.time
            val (freshness, isStale) = com.geotrace.ui.screens.devices.formatFreshness(updatedMs, nowMs, 120_000L)
            val speedVal = String.format(Locale.US, "%.1f", matchedMember.speed * settings.speedUnit.multiplier)
            return@remember target.copy(
                label = matchedMember.displayName,
                latitude = matchedMember.latitude,
                longitude = matchedMember.longitude,
                subtitle = if (isStale) "$freshness • $distStr • ${matchedMember.batteryPercent}%" else "$distStr • $speedVal ${settings.speedUnit.label} • ${matchedMember.batteryPercent}% • $freshness"
            )
        }
        val matchedDevice = deduplicatedPersonalDevices.firstOrNull {
            it.deviceId == target.id || it.id == target.id
        }
        if (matchedDevice != null && (matchedDevice.latitude != 0.0 || matchedDevice.longitude != 0.0)) {
            val dist = currentLocation?.let { matchedDevice.distanceToMeters(it.latitude, it.longitude) }
            val distStr = dist?.let { if (it < 1000f) "${it.toInt()}m" else String.format(Locale.US, "%.1fkm", it / 1000f) } ?: "--"
            val updatedMs = matchedDevice.lastUpdated?.toDate()?.time
            val (freshness, _) = com.geotrace.ui.screens.devices.formatFreshness(updatedMs, nowMs, 90_000L)
            return@remember target.copy(
                label = matchedDevice.deviceName.ifBlank { matchedDevice.deviceModel },
                latitude = matchedDevice.latitude,
                longitude = matchedDevice.longitude,
                subtitle = "$distStr • $freshness • ${matchedDevice.batteryPercent}%"
            )
        }
        val matchedPeer = nearbyPeers.firstOrNull { it.endpointId == target.id }
        if (matchedPeer != null && (matchedPeer.latitude != 0.0 || matchedPeer.longitude != 0.0)) {
            val dist = currentLocation?.let { matchedPeer.distanceToMeters(it.latitude, it.longitude) }
            val distStr = dist?.let { if (it < 1000f) "${it.toInt()}m" else String.format(Locale.US, "%.1fkm", it / 1000f) } ?: "Nearby"
            val speedVal = String.format(Locale.US, "%.1f", matchedPeer.speed * settings.speedUnit.multiplier)
            return@remember target.copy(
                label = matchedPeer.displayName,
                latitude = matchedPeer.latitude,
                longitude = matchedPeer.longitude,
                subtitle = "P2P • $distStr • $speedVal ${settings.speedUnit.label} • ${matchedPeer.batteryPercent}%"
            )
        }
        target
    }

    Box(modifier = modifier.fillMaxSize()) {
        val activeInspect = inspectedTrail
        val displayedTrailPoints = activeInspect?.points ?: trailPoints
        val displayedWaypoints = activeInspect?.waypoints ?: activeWaypoints
        val inspectedSummary = activeInspect?.let {
            "${it.session.formattedDistance} • ${it.session.formattedDuration} • ${it.points.size} pts • ${it.waypoints.size} wpts"
        }

        // Persistent Full-Bleed Spatial Viewport with Google Maps-style Bottom Layer Sheet & Unified Inspect Card
        MapViewComposable(
            currentLocation = currentLocation,
            trailPoints = displayedTrailPoints,
            geofenceZones = geofenceZones,
            cellTowerLocation = cellTowerLocation,
            allCellTowers = allCellTowers,
            groupMembers = groupMembers,
            personalDevices = deduplicatedPersonalDevices,
            currentDeviceId = currentDeviceId,
            currentUserUid = currentUser?.uid,
            nearbyPeers = nearbyPeers,
            focusedTarget = resolvedFocusTarget,
            isRecording = activeSession != null,
            trailWaypoints = displayedWaypoints,
            inspectedTrailId = activeInspect?.session?.id,
            inspectedTrailName = activeInspect?.session?.name,
            inspectedTrailSummary = inspectedSummary,
            selectedSearchPin = selectedSearchPin,
            onClearSearchPin = {
                viewModel.clearSelectedSearchPin()
            },
            lastHandledFocusMs = viewModel.lastHandledFocusRequestMs,
            onMarkFocusHandled = { viewModel.lastHandledFocusRequestMs = it },
            lastHandledSearchMs = viewModel.lastHandledSearchRequestMs,
            onMarkSearchHandled = { viewModel.lastHandledSearchRequestMs = it },
            lastHandledInspectedTrail = viewModel.lastHandledInspectedTrailId,
            onMarkInspectedTrailHandled = { viewModel.lastHandledInspectedTrailId = it },
            isFollowPaused = isMapFollowPaused,
            onFollowPausedByUser = {
                viewModel.setMapFollowPaused(true)
            },
            onClearInspectedTrail = {
                viewModel.clearInspectedTrail()
                if (settings.followUserLocation) viewModel.setMapFollowPaused(true)
            },
            onClearFocusTarget = {
                groupViewModel?.clearMapFocusTarget()
                if (settings.followUserLocation) viewModel.setMapFollowPaused(true)
            },
            onRecenterSelf = {
                groupViewModel?.clearMapFocusTarget()
                viewModel.clearInspectedTrail()
                viewModel.clearSelectedSearchPin()
                viewModel.setMapFollowPaused(false)
            },
            offlineRegionsCount = offlineMapRegions.size,
            settings = settings,
            onSelectTileLayer = { selectedTile ->
                viewModel.updateMapTileType(selectedTile)
            },
            onToggleCellTowerPin = { enabled ->
                viewModel.toggleCellTowerPin(enabled)
            },
            onOpenOfflineMaps = {
                showOfflineMapsDialog = true
            },
            onMapBoundsChanged = { bounds ->
                viewModel.updateMapViewportBounds(bounds)
            },
            initialMapCenter = viewModel.savedMapCenter,
            initialMapZoom = viewModel.savedMapZoom,
            onSaveMapCamera = { center, zoom ->
                viewModel.savedMapCenter = center
                viewModel.savedMapZoom = zoom
            },
            isMapPickMode = isPickingGeofenceCenter,
            onMapCoordinatePicked = { lat, lon ->
                groupViewModel?.clearMapFocusTarget()
                viewModel.completeMapPick(lat, lon)
                onReturnToGeofence?.invoke()
            }
        )

        // Top Instrument & Telemetry HUD Cluster
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .animateContentSize()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                    .testTag("telemetry_hud_card")
            ) {
                Column {
                    // Top Accent Line (Telemetry Tier Indicator)
                    val accentLineColor = when {
                        activeSession?.isPaused == true -> customColors.warning
                        activeSession != null -> colorScheme.error
                        currentLocation != null -> colorScheme.tertiary
                        else -> customColors.warning
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(accentLineColor)
                    )

                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = if (isHeaderExpanded) 12.dp else 7.dp
                        )
                    ) {
                        val compactSpeed = (currentLocation?.speed ?: 0f) * settings.speedUnit.multiplier
                        // Title Bar (single slim bar when collapsed; full header when expanded)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isHeaderExpanded = !isHeaderExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accentLineColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        text = if (isHeaderExpanded) "GeoTrace Live Telemetry" else "GeoTrace",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (!isHeaderExpanded) {
                                            // Collapsed HUD: show live sensor telemetry (speed, accuracy, trackpoints) — never duplicate bottom pill timer/distance
                                            val fixText = if (currentLocation != null) {
                                                "${String.format(Locale.US, "%.1f", compactSpeed)} ${settings.speedUnit.label} • ±${currentLocation?.accuracy?.toInt()}m"
                                            } else {
                                                "Awaiting fix"
                                            }
                                            when {
                                                activeSession?.isPaused == true -> "PAUSED • $fixText"
                                                activeSession != null -> "$fixText • ${trailPoints.size} pts"
                                                else -> fixText
                                            }
                                        } else {
                                            when {
                                                activeSession?.isPaused == true -> "PAUSED • ${activeSession?.name}"
                                                activeSession != null -> "RECORDING • ${activeSession?.name}"
                                                trackingMode == TrackingMode.POWER_SAVER -> "CELLULAR ONLY • BASE STATION TELEMETRY"
                                                currentLocation?.provider == "cellular" -> "CELLULAR FIX • ACCURACY ±${currentLocation?.accuracy?.toInt()}M"
                                                currentLocation != null -> "POSITION LOCKED • ACCURACY ±${currentLocation?.accuracy?.toInt()}M"
                                                else -> "STANDBY • AWAITING LOCATION FIX"
                                            }
                                        },
                                        fontSize = if (isHeaderExpanded) 9.sp else 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = if (isHeaderExpanded) FontFamily.Default else FontFamily.Monospace,
                                        letterSpacing = 0.03.sp,
                                        color = if (trackingMode == TrackingMode.POWER_SAVER && isHeaderExpanded) Color(0xFFD97706) else accentLineColor,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!isHeaderExpanded) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = customColors.surfaceContainerLow,
                                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clickable { isHeaderExpanded = true }
                                            .testTag("toggle_header_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "HUD",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand Telemetry HUD",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Hamburger Menu on the RIGHT (44dp accessible target)
                                IconButton(
                                    onClick = onOpenMenu,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                        .testTag("menu_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = "Open Application Menu",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Animated Collapsible Body
                        AnimatedVisibility(
                            visible = isHeaderExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))

                                // Interactive Vector Coordinates Bar (Tap to copy DD/DMS, Long-Press or Share icon to share)
                                val hasFix = currentLocation != null
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (vectorCoordsCopied) colorScheme.primary.copy(alpha = 0.12f) else customColors.surfaceContainerLow,
                                    border = BorderStroke(
                                        1.dp,
                                        if (vectorCoordsCopied) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(
                                            if (hasFix) {
                                                Modifier.combinedClickable(
                                                    onClick = {
                                                        currentLocation?.let { loc ->
                                                            copyCoordinatesWithFeedback(
                                                                context = context,
                                                                haptic = haptic,
                                                                lat = loc.latitude,
                                                                lon = loc.longitude,
                                                                format = settings.coordinateFormat,
                                                                label = "GeoTrace Vector Coordinates"
                                                            )
                                                            vectorCoordsCopied = true
                                                        }
                                                    },
                                                    onLongClick = {
                                                        currentLocation?.let { loc ->
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            shareCoordinatesIntent(
                                                                context = context,
                                                                lat = loc.latitude,
                                                                lon = loc.longitude,
                                                                format = settings.coordinateFormat,
                                                                title = "GeoTrace Live Position"
                                                            )
                                                        }
                                                    }
                                                )
                                            } else Modifier
                                        )
                                        .testTag("copy_vector_coord_btn")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "VECTOR COORD",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.04.sp,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (currentLocation != null) {
                                                    formatCoordinates(currentLocation!!.latitude, currentLocation!!.longitude, settings.coordinateFormat)
                                                } else {
                                                    "--.-----°, --.-----°"
                                                },
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (vectorCoordsCopied) colorScheme.primary else colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        if (hasFix) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (vectorCoordsCopied) colorScheme.primary else colorScheme.surface
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (vectorCoordsCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                            contentDescription = "Copy Coordinates",
                                                            tint = if (vectorCoordsCopied) colorScheme.onPrimary else colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = if (vectorCoordsCopied) "Copied" else "Copy",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (vectorCoordsCopied) colorScheme.onPrimary else colorScheme.primary
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        currentLocation?.let { loc ->
                                                            shareCoordinatesIntent(
                                                                context = context,
                                                                lat = loc.latitude,
                                                                lon = loc.longitude,
                                                                format = settings.coordinateFormat,
                                                                title = "GeoTrace Live Position"
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .testTag("share_vector_coord_btn")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Share Coordinates",
                                                        tint = colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 4 Tabular Telemetry Scalar Metrics with Responsive Weighted Alignment
                                val convertedSpeed = (currentLocation?.speed ?: 0f) * settings.speedUnit.multiplier
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        TelemetryMetric(
                                            label = "SPEED",
                                            value = String.format(Locale.US, "%.1f", convertedSpeed),
                                            unit = settings.speedUnit.label,
                                            valueColor = colorScheme.primary,
                                            subtitle = currentLocation?.gaitLabel ?: "Stationary"
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        val pace = activeSession?.avgPaceMinPerKm?.takeIf { it != "--:--" }
                                            ?: currentLocation?.paceMinPerKm?.takeIf { it != "--:--" }
                                        TelemetryMetric(
                                            label = "DISTANCE",
                                            value = activeSession?.formattedDistance ?: "0 m",
                                            unit = "",
                                            valueColor = colorScheme.secondary,
                                            subtitle = pace
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        TelemetryMetric(
                                            label = "ACCURACY",
                                            value = if (currentLocation != null) "±${currentLocation?.accuracy?.toInt()}m" else "--",
                                            unit = "",
                                            valueColor = if ((currentLocation?.accuracy ?: 99f) <= 10f) colorScheme.tertiary else customColors.warning,
                                            subtitle = currentLocation?.provider?.uppercase()
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        TelemetryMetric(
                                            label = "TRACKPOINTS",
                                            value = "${trailPoints.size}",
                                            unit = "pts",
                                            valueColor = colorScheme.onSurface,
                                            subtitle = when {
                                                activeWaypoints.isNotEmpty() -> "${activeWaypoints.size} wpts"
                                                activeSession?.isPaused == true -> "Paused"
                                                activeSession?.isRecording == true -> "Auto-log"
                                                else -> "Standby"
                                            }
                                        )
                                    }
                                }

                                if (activeSession != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                waypointNameInput = "Waypoint #${activeWaypoints.size + 1}"
                                                waypointNoteInput = ""
                                                showWaypointDialog = true
                                            },
                                            enabled = currentLocation != null,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .testTag("drop_waypoint_hud_btn")
                                        ) {
                                            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pin Waypoint", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        val isPaused = activeSession?.isPaused == true
                                        FilledTonalButton(
                                            onClick = {
                                                if (isPaused) viewModel.resumeTracking() else viewModel.pauseTracking()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (isPaused) colorScheme.tertiaryContainer else colorScheme.surfaceVariant,
                                                contentColor = if (isPaused) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .testTag("pause_resume_hud_btn")
                                        ) {
                                            Icon(
                                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isPaused) "Resume Trail" else "Pause Trail",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else if (!isPickingGeofenceCenter) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            if (!com.geotrace.service.LocationForegroundService.hasLocationPermission(context)) {
                                                Toast.makeText(context, "Grant Location permission to record a field trail.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                                sessionNameInput = "Field Trail $timeStr"
                                                showStartDialog = true
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .testTag("start_track_hud_btn")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Record Field Trail", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = customColors.surfaceContainerLow,
                                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .clickable { isHeaderExpanded = false }
                                            .testTag("toggle_header_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Collapse Telemetry HUD",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "HIDE HUD",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.06.sp,
                                                color = colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Floating Badges Row (Horizontally scrollable for small screens)
            val badgesScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(badgesScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Place & Coordinate Search Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(
                        1.dp,
                        if (selectedSearchPin != null) colorScheme.primary else colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clickable {
                            viewModel.refreshSearchNetworkStatus()
                            viewModel.updateSearchQuery(searchQuery, deviceSearchTargets)
                            showSearchDialog = true
                        }
                        .testTag("open_search_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search places or coordinates",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = selectedSearchPin?.name ?: "Search",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = if (selectedSearchPin != null) 124.dp else 72.dp)
                        )
                    }
                }

                // Floating Positioning Profile Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .clickable { showProfileDialog = true }
                        .testTag("floating_profile_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val profile = getProfileInfo(trackingMode, colorScheme)
                        Icon(profile.icon, contentDescription = null, tint = profile.badgeColor, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = profile.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = profile.badgeColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = profile.power,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = profile.badgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                // Floating Devices / Tracking Group & Off-Grid P2P Pill (shown only when groups, fleet, or P2P are active — bottom bar already houses Devices)
                if (hasQuickLocateTargets || activeGroup != null || isP2PEnabled) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when {
                            activeGroup != null -> colorScheme.primaryContainer.copy(alpha = 0.5f)
                            deduplicatedPersonalDevices.isNotEmpty() -> colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            isP2PEnabled -> colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                            else -> colorScheme.surface
                        },
                        shadowElevation = 3.dp,
                        border = BorderStroke(
                            1.dp,
                            when {
                                activeGroup != null -> colorScheme.primary
                                deduplicatedPersonalDevices.isNotEmpty() -> colorScheme.secondary
                                isP2PEnabled -> colorScheme.tertiary
                                else -> colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .clickable {
                                if (hasQuickLocateTargets) {
                                    showDevicesSheet = true
                                } else {
                                    onNavigateToGroups?.invoke()
                                }
                            }
                            .testTag("floating_group_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = "Devices and Offline P2P Mesh",
                                tint = when {
                                    activeGroup != null -> colorScheme.primary
                                    deduplicatedPersonalDevices.isNotEmpty() -> colorScheme.secondary
                                    isP2PEnabled -> colorScheme.tertiary
                                    else -> colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val liveGroupCount = groupMembers.count { m ->
                                val ts = m.lastUpdated?.toDate()?.time
                                ts != null && (nowMs - ts) <= 120_000L
                            }
                            val fleetSuffix = if (deduplicatedPersonalDevices.isNotEmpty()) " +${deduplicatedPersonalDevices.size}" else ""
                            val pillLabel = when {
                                activeGroup != null && isP2PEnabled -> "${activeGroup!!.name} ($liveGroupCount/${groupMembers.size})$fleetSuffix • P2P (${nearbyPeers.size})"
                                activeGroup != null -> "${activeGroup!!.name} ($liveGroupCount/${groupMembers.size})$fleetSuffix"
                                deduplicatedPersonalDevices.isNotEmpty() && isP2PEnabled -> "My Devices (${deduplicatedPersonalDevices.size}) • P2P (${nearbyPeers.size})"
                                deduplicatedPersonalDevices.isNotEmpty() -> "My Devices (${deduplicatedPersonalDevices.size})"
                                else -> "P2P Mesh (${nearbyPeers.size})"
                            }
                            Text(
                                text = pillLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            if (isGhostMode) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "GHOST",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }
                    }
                }

                // Option 1 On-Demand Trail Recorder Chip (replaces permanent standby bottom bar)
                if (activeSession == null && !isPickingGeofenceCenter) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.surface,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clickable {
                                if (!com.geotrace.service.LocationForegroundService.hasLocationPermission(context)) {
                                    Toast.makeText(context, "Grant Location permission to record a field trail.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    sessionNameInput = "Field Trail $timeStr"
                                    showStartDialog = true
                                }
                            }
                            .testTag("start_track_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start Trail Recording",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Record Trail",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }

                // Compact Offline Download Progress Chip (shown when dialog is minimized/closed)
                if (offlineProgress.isDownloading && !showOfflineMapsDialog) {
                    val pct = if (offlineProgress.totalTiles > 0) {
                        ((offlineProgress.completedTiles * 100) / offlineProgress.totalTiles).coerceIn(0, 100)
                    } else 0
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, colorScheme.primary),
                        modifier = Modifier
                            .clickable { showOfflineMapsDialog = true }
                            .testTag("offline_caching_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "View Offline Map Download Progress",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Caching $pct%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }

                // Live Geofence Zone Proximity Chip (Active Perimeter Alert)
                val activeZone = geofenceZones.firstOrNull { it.isEnabled && it.isInside }
                if (activeZone != null) {
                    val isHotspot = activeZone.category == com.geotrace.model.GeofenceCategory.BODA_HOTSPOT
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isHotspot) customColors.warning.copy(alpha = 0.14f) else colorScheme.tertiary.copy(alpha = 0.14f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, if (isHotspot) customColors.warning else colorScheme.tertiary),
                        modifier = Modifier.testTag("active_geofence_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isHotspot) customColors.warning else colorScheme.tertiary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeZone.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHotspot) customColors.warning else colorScheme.tertiary
                            )
                        }
                    }
                }

                // Cell Tower Pin Status Chip
                if (settings.showCellTowerPin && cellTowerLocation != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.surface,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clickable {
                                viewModel.toggleCellTowerPin(false)
                                android.widget.Toast.makeText(
                                    context,
                                    "Cell tower pin hidden — restore in Layers",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            .testTag("cell_tower_status_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Tower ±${cellTowerLocation?.rangeMeters?.toInt()}m",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }
            }

            if (isPickingGeofenceCenter) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colorScheme.tertiaryContainer,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.5.dp, colorScheme.tertiary),
                    modifier = Modifier.testTag("map_pick_geofence_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TAP MAP TO SET SAFE ZONE CENTER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Target: ${geofencePickDraft?.name ?: "Safe Zone"} • Switch to Satellite for rooftop precision",
                                fontSize = 10.sp,
                                color = colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        TextButton(
                            onClick = {
                                groupViewModel?.clearMapFocusTarget()
                                viewModel.cancelMapPick()
                                onReturnToGeofence?.invoke()
                            },
                            modifier = Modifier.testTag("cancel_map_pick_btn")
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Slim Single-Row Recording Pill (shown ONLY while actively recording; leaves 78dp right clearance for Zoom FABs)
        if (!isPickingGeofenceCenter && activeSession != null) {
            val session = activeSession!!
            val isPaused = session.isPaused
            val accentPillColor = if (isPaused) customColors.warning else colorScheme.error
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 78.dp, bottom = 14.dp)
                    .testTag("floating_recording_pill"),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.5.dp, accentPillColor.copy(alpha = 0.7f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentPillColor)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "${session.formatDuration(liveRecordingNowMs)} • ${session.formattedDistance}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentPillColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (isPaused) viewModel.resumeTracking() else viewModel.pauseTracking()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("pause_resume_pill_btn")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume Recording" else "Pause Recording",
                            tint = if (isPaused) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Button(
                        onClick = { viewModel.stopTracking() },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("stop_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Stop",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Drop Manual Waypoint (<wpt>) Dialog
    if (showWaypointDialog) {
        val loc = currentLocation
        AlertDialog(
            onDismissRequest = { showWaypointDialog = false },
            title = {
                Text(
                    "Pin Field Waypoint",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (loc != null) {
                            "Marks an individual GIS waypoint (<wpt>) at ${formatCoordinates(loc.latitude, loc.longitude, settings.coordinateFormat)} (±${loc.accuracy.toInt()}m)."
                        } else {
                            "Waiting for location fix..."
                        },
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = waypointNameInput,
                        onValueChange = { waypointNameInput = it },
                        label = { Text("Waypoint Name", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waypoint_name_input")
                    )
                    OutlinedTextField(
                        value = waypointNoteInput,
                        onValueChange = { waypointNoteInput = it },
                        label = { Text("Field Note (Optional)", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waypoint_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dropWaypoint(waypointNameInput, waypointNoteInput) { wp ->
                            if (wp != null) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Pinned ${wp.name}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Cannot pin waypoint: no location fix available.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        showWaypointDialog = false
                    },
                    enabled = loc != null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_drop_waypoint_btn")
                ) {
                    Text("Save Waypoint", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWaypointDialog = false }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colorScheme.surface
        )
    }

    // Positioning Engine & Battery Profile Modal Dialog
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text(
                    text = "Positioning Engine & Battery Profile",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                val dialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select positioning telemetry engine based on mission requirement and battery budget:",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    ProfileOptionCard(
                        title = "GPS Real-Time (1 sec)",
                        subtitle = "High-Accuracy GNSS satellites",
                        accuracy = "±2 – 5m",
                        power = "~25 mA",
                        description = "Continuous GNSS chipset lock. Essential for velocity vector analysis and official trail logging.",
                        isSelected = trackingMode == TrackingMode.HIGH_ACCURACY,
                        color = colorScheme.primary,
                        onClick = {
                            viewModel.setTrackingMode(TrackingMode.HIGH_ACCURACY)
                            showProfileDialog = false
                        }
                    )

                    ProfileOptionCard(
                        title = "Balanced Fused (5 sec)",
                        subtitle = "Hybrid GNSS + Network duty cycling",
                        accuracy = "±5 – 15m",
                        power = "~8 mA",
                        description = "Cycles positioning receiver every 5s. Ideal for conserving battery while logging full trails.",
                        isSelected = trackingMode == TrackingMode.BALANCED,
                        color = colorScheme.secondary,
                        onClick = {
                            viewModel.setTrackingMode(TrackingMode.BALANCED)
                            showProfileDialog = false
                        }
                    )

                    ProfileOptionCard(
                        title = "Cellular Only (RF Masts)",
                        subtitle = "Zero GNSS hardware activation",
                        accuracy = "±200 – 800m",
                        power = "<2 mA",
                        description = "TelephonyManager base station sectoring. Zero satellite drain. Works indoors & in tunnels.",
                        isSelected = trackingMode == TrackingMode.POWER_SAVER,
                        color = colorScheme.tertiary,
                        onClick = {
                            viewModel.setTrackingMode(TrackingMode.POWER_SAVER)
                            showProfileDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showProfileDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface
        )
    }

    // Start Session Dialog (Level 3 Elevation)
    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = {
                Text(
                    "Start Field Trail",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Enter a name for this field tracking session. Coordinates from your active positioning profile (GPS, Wi-Fi & Cellular) will be persisted to SQLite.",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { sessionNameInput = it },
                        label = { Text("Session Identifier", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = sessionNameInput.ifBlank { "Field Trail" }
                        val started = viewModel.startTracking(name, trackingMode)
                        if (!started) {
                            Toast.makeText(context, "Location permission is required to record a trail.", Toast.LENGTH_SHORT).show()
                        }
                        showStartDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_start_btn")
                ) {
                    Text("Start Recording", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colorScheme.surface
        )
    }

    // Offline Maps Manager Dialog
    if (showOfflineMapsDialog) {
        OfflineMapsDialog(
            viewModel = viewModel,
            currentLocation = currentLocation,
            currentMapBounds = currentMapBounds,
            onJumpToRegion = { _, target ->
                viewModel.clearInspectedTrail()
                viewModel.clearSelectedSearchPin()
                groupViewModel?.focusMapOnTarget(target)
            },
            onDismiss = { showOfflineMapsDialog = false }
        )
    }

    if (showSearchDialog) {
        PlaceSearchDialog(
            query = searchQuery,
            results = searchResults,
            isSearchingOnline = isSearchingOnline,
            isNetworkOnline = isNetworkOnline,
            offlinePlacesCount = offlinePlacesCount,
            offlineRegionsCount = offlineMapRegions.size,
            searchError = searchError,
            onQueryChange = { newQ ->
                viewModel.updateSearchQuery(newQ, deviceSearchTargets)
            },
            onSelectResult = { place ->
                groupViewModel?.clearMapFocusTarget()
                viewModel.clearInspectedTrail()
                viewModel.selectSearchResult(place)
                showSearchDialog = false
            },
            onClearRecents = {
                viewModel.clearRecentSearches(deviceSearchTargets)
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    // On-Demand Quick Locate Sheet for Group Devices, Personal Devices & Nearby P2P Peers
    if (showDevicesSheet) {
        val devicesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDevicesSheet = false },
            sheetState = devicesSheetState,
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.testTag("quick_devices_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeGroup?.name ?: "Tracked Devices & Peers",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Tap any device to locate it on the map",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            showDevicesSheet = false
                            onNavigateToGroups?.invoke()
                        },
                        modifier = Modifier.testTag("manage_groups_from_sheet_btn")
                    ) {
                        Text("Manage", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

                for (member in remoteGroupMembers) {
                    val dist = if (currentLocation != null) member.distanceToMeters(currentLocation!!.latitude, currentLocation!!.longitude) else null
                    val distStr = when {
                        dist == null -> "--"
                        dist < 1000 -> "${dist.toInt()}m"
                        else -> String.format(Locale.US, "%.1fkm", dist / 1000f)
                    }
                    val updatedMs = member.lastUpdated?.toDate()?.time
                    val isOffline = updatedMs == null || (nowMs - updatedMs) > 120_000L
                    val memColor = try { Color(android.graphics.Color.parseColor(member.avatarColor)) } catch (e: Exception) { colorScheme.primary }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (isOffline) Color(0xFFD97706).copy(alpha = 0.5f) else colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDevicesSheet = false
                                if (member.latitude != 0.0 || member.longitude != 0.0) {
                                    viewModel.clearInspectedTrail()
                                    viewModel.clearSelectedSearchPin()
                                    val statusNote = if (isOffline) "Offline • $distStr • ${member.batteryPercent}%" else "$distStr • ${member.batteryPercent}%"
                                    groupViewModel?.focusMapOnTarget(
                                        MapFocusTarget(
                                            id = member.id.ifBlank { member.userId },
                                            label = member.displayName,
                                            latitude = member.latitude,
                                            longitude = member.longitude,
                                            subtitle = statusNote
                                        )
                                    )
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Waiting for ${member.displayName}'s location sync...",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .testTag("sheet_member_${member.userId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isOffline) memColor.copy(alpha = 0.5f) else memColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.displayName.trim().take(2).uppercase().ifBlank { "M" },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = if (isOffline) "$distStr • Offline • ${member.batteryPercent}%" else "$distStr • Live • ${member.batteryPercent}%",
                                    fontSize = 11.sp,
                                    color = if (isOffline) Color(0xFFD97706) else colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Locate on Map",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                for (device in deduplicatedPersonalDevices) {
                    val hasCoords = device.latitude != 0.0 || device.longitude != 0.0
                    val dist = if (currentLocation != null && hasCoords) {
                        device.distanceToMeters(currentLocation!!.latitude, currentLocation!!.longitude)
                    } else null
                    val distStr = when {
                        !hasCoords -> "Syncing..."
                        dist == null -> "--"
                        dist < 1000 -> "${dist.toInt()}m"
                        else -> String.format(Locale.US, "%.1fkm", dist / 1000f)
                    }
                    val devUpdatedMs = device.lastUpdated?.toDate()?.time
                    val (devFreshness, devStale) = com.geotrace.ui.screens.devices.formatFreshness(devUpdatedMs, nowMs, 90_000L)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (devStale) Color(0xFFD97706).copy(alpha = 0.5f) else Color(0xFF7C3AED).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDevicesSheet = false
                                if (hasCoords) {
                                    viewModel.clearInspectedTrail()
                                    viewModel.clearSelectedSearchPin()
                                    groupViewModel?.focusMapOnTarget(
                                        MapFocusTarget(
                                            id = device.deviceId,
                                            label = device.deviceName.ifBlank { device.deviceModel },
                                            latitude = device.latitude,
                                            longitude = device.longitude,
                                            subtitle = "$distStr • $devFreshness • ${device.batteryPercent}%"
                                        )
                                    )
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Waiting for ${device.deviceName.ifBlank { "device" }} location sync...",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .testTag("sheet_device_${device.deviceId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF7C3AED)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = device.deviceName.trim().take(2).uppercase().ifBlank { "DV" },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.deviceName.ifBlank { device.deviceModel },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "$distStr • $devFreshness • ${device.batteryPercent}%",
                                    fontSize = 11.sp,
                                    color = if (devStale) Color(0xFFD97706) else colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Locate on Map",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                for (peer in nearbyPeers) {
                    val hasPeerCoords = peer.latitude != 0.0 || peer.longitude != 0.0
                    val distText = if (currentLocation != null && hasPeerCoords) {
                        val d = peer.distanceToMeters(currentLocation!!.latitude, currentLocation!!.longitude)
                        val bearing = peer.bearingFrom(currentLocation!!.latitude, currentLocation!!.longitude)
                        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                        val compass = dirs[(((((bearing % 360f) + 360f) % 360f) + 22.5f) / 45f).toInt() % 8]
                        val distPart = if (d < 1000) "${d.toInt()}m" else String.format(Locale.US, "%.1fkm", d / 1000f)
                        "$distPart $compass"
                    } else if (!hasPeerCoords) {
                        "GPS pending"
                    } else {
                        "Nearby"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDevicesSheet = false
                                if (hasPeerCoords) {
                                    viewModel.clearInspectedTrail()
                                    viewModel.clearSelectedSearchPin()
                                    groupViewModel?.focusMapOnTarget(
                                        MapFocusTarget(
                                            id = peer.endpointId,
                                            label = peer.displayName,
                                            latitude = peer.latitude,
                                            longitude = peer.longitude,
                                            subtitle = "P2P • $distText • ${peer.batteryPercent}%"
                                        )
                                    )
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Waiting for ${peer.displayName}'s GPS fix...",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .testTag("sheet_p2p_${peer.endpointId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.tertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.displayName.trim().take(2).uppercase().ifBlank { "P2" },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${peer.displayName} (P2P)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "$distText • ${peer.batteryPercent}%",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Locate on Map",
                                tint = colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

private data class ProfileInfo(
    val icon: ImageVector,
    val label: String,
    val power: String,
    val badgeColor: Color
)

private fun getProfileInfo(mode: TrackingMode, colorScheme: androidx.compose.material3.ColorScheme): ProfileInfo {
    return when (mode) {
        TrackingMode.HIGH_ACCURACY -> ProfileInfo(Icons.Default.Bolt, "GNSS 1s", "~25mA", colorScheme.primary)
        TrackingMode.BALANCED -> ProfileInfo(Icons.Default.Balance, "Fused 5s", "~8mA", colorScheme.secondary)
        TrackingMode.POWER_SAVER -> ProfileInfo(Icons.Default.CellTower, "Cell Only", "<2mA", colorScheme.tertiary)
    }
}

@Composable
private fun ProfileOptionCard(
    title: String,
    subtitle: String,
    accuracy: String,
    power: String,
    description: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.08f) else colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) color else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = power,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nominal Resolution: $accuracy",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    subtitle: String? = null
) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

fun formatCoordinates(lat: Double, lon: Double, format: CoordinateFormat): String {
    return if (format == CoordinateFormat.DECIMAL) {
        String.format(Locale.US, "%.5f°, %.5f°", lat, lon)
    } else {
        "${toDms(lat, true)}, ${toDms(lon, false)}"
    }
}

fun formatCopyableCoordinates(lat: Double, lon: Double, format: CoordinateFormat): String {
    return if (format == CoordinateFormat.DECIMAL) {
        String.format(Locale.US, "%.6f, %.6f", lat, lon)
    } else {
        "${toDms(lat, true)}, ${toDms(lon, false)}"
    }
}

fun copyCoordinatesWithFeedback(
    context: Context,
    haptic: HapticFeedback,
    lat: Double,
    lon: Double,
    format: CoordinateFormat,
    label: String = "GeoTrace Coordinates"
) {
    val copyText = formatCopyableCoordinates(lat, lon, format)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, copyText))
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    // Android 13 (API 33)+ shows a system clipboard confirmation sheet automatically; show Toast only on <= Android 12
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, "Copied: $copyText", Toast.LENGTH_SHORT).show()
    }
}

fun shareCoordinatesIntent(
    context: Context,
    lat: Double,
    lon: Double,
    format: CoordinateFormat,
    title: String = "GeoTrace Location"
) {
    val formatted = formatCopyableCoordinates(lat, lon, format)
    val ddQuery = String.format(Locale.US, "%.6f,%.6f", lat, lon)
    val shareBody = "$title\nCoordinates: $formatted\nMap: https://maps.google.com/?q=$ddQuery"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    val chooser = Intent.createChooser(sendIntent, "Share Coordinates")
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun toDms(decimal: Double, isLat: Boolean): String {
    val dir = if (isLat) {
        if (decimal >= 0) "N" else "S"
    } else {
        if (decimal >= 0) "E" else "W"
    }
    val abs = kotlin.math.abs(decimal)
    val deg = abs.toInt()
    val minDec = (abs - deg) * 60.0
    val min = minDec.toInt()
    val sec = (minDec - min) * 60.0
    return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, dir)
}
