package com.geotrace.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geotrace.data.export.TrackImporter
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.MapFocusTarget
import com.geotrace.model.MapTileType
import com.geotrace.model.SearchPlaceResult
import com.geotrace.model.SearchResultSource
import com.geotrace.model.TrackingMode
import com.geotrace.service.ElevationAnalyzer
import com.geotrace.service.SolarCalculator
import com.geotrace.service.formatCompassCardinal
import com.geotrace.service.normalizeHeadingDegrees
import com.geotrace.service.shortestAngleDeltaDegrees
import com.geotrace.ui.components.MapViewComposable
import com.geotrace.ui.components.OfflineMapsDialog
import com.geotrace.ui.components.PlaceSearchDialog
import com.geotrace.ui.components.adaptiveContentWidth
import com.geotrace.ui.theme.InterFontFamily
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.ui.theme.TelemetryHeaderLabelStyle
import com.geotrace.ui.theme.TelemetryScalarStyle
import com.geotrace.viewmodel.GroupViewModel
import com.geotrace.viewmodel.TrackerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapScreen(
    viewModel: TrackerViewModel,
    groupViewModel: GroupViewModel? = null,
    bottomOverlayPadding: Dp = 0.dp,
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
    val lastKnownCachedLocation by viewModel.lastKnownCachedLocation.collectAsState()
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
    val parkedSpot by viewModel.parkedSpot.collectAsState()
    val savedPlaces by viewModel.savedPlaces.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val lastGaspBeacon by viewModel.lastGaspBeacon.collectAsState()
    val importedRoute by viewModel.importedRoute.collectAsState()
    val routeProgress by viewModel.routeProgress.collectAsState()
    val isBacktrackingActiveTrail by viewModel.isBacktrackingActiveTrail.collectAsState()
    val activeGuideTarget by viewModel.activeGuideTarget.collectAsState()

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

    var showStartDialog by rememberSaveable { mutableStateOf(false) }
    var selectedStartTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var showWaypointDialog by rememberSaveable { mutableStateOf(false) }
    var waypointNameInput by rememberSaveable { mutableStateOf("") }
    var waypointNoteInput by rememberSaveable { mutableStateOf("") }
    var waypointPhotoUriInput by rememberSaveable { mutableStateOf<String?>(null) }
    var showSavePlaceDialog by rememberSaveable { mutableStateOf(false) }
    var savePlaceNameInput by rememberSaveable { mutableStateOf("") }
    var savePlaceCategory by rememberSaveable { mutableStateOf("Spot") }
    var savePlaceNoteInput by rememberSaveable { mutableStateOf("") }
    var savePlaceLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var savePlaceLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var savePlaceAlt by rememberSaveable { mutableStateOf(0.0) }
    var savePlaceTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var savePlacePhotoUriInput by rememberSaveable { mutableStateOf<String?>(null) }
    var activePhotoPickerTarget by rememberSaveable { mutableStateOf("waypoint") }
    androidx.compose.runtime.LaunchedEffect(trips) {
        if (selectedStartTripId != null && trips.none { it.id == selectedStartTripId }) {
            selectedStartTripId = null
        }
        if (savePlaceTripId != null && trips.none { it.id == savePlaceTripId }) {
            savePlaceTripId = null
        }
    }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showOfflineMapsDialog by rememberSaveable { mutableStateOf(false) }
    var showDevicesSheet by rememberSaveable { mutableStateOf(false) }
    var showSearchDialog by rememberSaveable { mutableStateOf(false) }
    var sessionNameInput by rememberSaveable { mutableStateOf("") }
    var isHeaderExpanded by rememberSaveable { mutableStateOf(false) }
    var showParkedSpotDialog by rememberSaveable { mutableStateOf(false) }
    var liveCompassAzimuth by remember { mutableStateOf<Float?>(null) }
    var isCompassLowAccuracy by remember { mutableStateOf(false) }
    var vectorCoordsCopied by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var liveRecordingNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val routeImportScope = rememberCoroutineScope()
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            if (activePhotoPickerTarget == "save_place") {
                savePlacePhotoUriInput = uri.toString()
            } else {
                waypointPhotoUriInput = uri.toString()
            }
        }
    }
    val importRouteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            routeImportScope.launch {
                try {
                    val (displayName, content) = withContext(Dispatchers.IO) {
                        var fileName = "Imported Route"
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) fileName = c.getString(idx) ?: fileName
                            }
                        }
                        val text = context.contentResolver.openInputStream(uri)?.use {
                            TrackImporter.readBoundedUtf8(it)
                        } ?: ""
                        fileName to text
                    }
                    val ext = displayName.substringAfterLast('.', "")
                    val baseName = displayName.substringBeforeLast('.').ifBlank { displayName }
                    viewModel.importRouteFromContent(content, baseName, ext) { res ->
                        res.onSuccess { route ->
                            Toast.makeText(
                                context,
                                "Loaded ${route.name} (${route.formattedDistance}, ${route.points.size} pts)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { err ->
                            Toast.makeText(
                                context,
                                err.message ?: "Could not import route file.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Failed to read route file: ${e.localizedMessage ?: "I/O error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
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
        val coveredTileTypes = remember(offlineMapRegions, currentMapBounds) {
            com.geotrace.service.OfflineMapManager.coveredTileTypesForViewport(
                regions = offlineMapRegions,
                viewNorth = currentMapBounds?.latNorth,
                viewSouth = currentMapBounds?.latSouth,
                viewEast = currentMapBounds?.lonEast,
                viewWest = currentMapBounds?.lonWest
            )
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
            offlineMapRegions = offlineMapRegions,
            coveredOfflineTileTypes = coveredTileTypes,
            settings = settings,
            onSelectTileLayer = { selectedTile ->
                viewModel.updateMapTileType(selectedTile)
            },
            onToggleCellTowerPin = { enabled ->
                viewModel.toggleCellTowerPin(enabled)
            },
            onToggleOfflineRegionsOverlay = { enabled ->
                viewModel.toggleOfflineRegionsOverlay(enabled)
            },
            onToggleAutoRotateHeadingUp = { enabled ->
                viewModel.toggleAutoRotateHeadingUp(enabled)
            },
            onOpenOfflineMaps = {
                showOfflineMapsDialog = true
            },
            onMapBoundsChanged = { bounds ->
                viewModel.updateMapViewportBounds(bounds)
            },
            initialMapCenter = viewModel.savedMapCenter,
            fallbackCachedCenter = lastKnownCachedLocation?.let { org.osmdroid.util.GeoPoint(it.latitude, it.longitude) },
            initialMapZoom = viewModel.savedMapZoom,
            initialMapOrientation = viewModel.savedMapOrientation,
            onSaveMapCamera = { center, zoom, orientation ->
                if (viewModel.savedMapCenter == null && center.latitude == 0.0 && center.longitude == 0.0 && zoom <= 3.5) {
                    return@MapViewComposable
                }
                viewModel.savedMapCenter = center
                viewModel.savedMapZoom = zoom
                viewModel.savedMapOrientation = orientation
            },
            isMapPickMode = isPickingGeofenceCenter,
            onMapCoordinatePicked = { lat, lon ->
                groupViewModel?.clearMapFocusTarget()
                viewModel.completeMapPick(lat, lon)
                onReturnToGeofence?.invoke()
            },
            onMapSingleTap = {
                if (isHeaderExpanded) {
                    isHeaderExpanded = false
                }
            },
            importedRoute = importedRoute,
            lastHandledImportedRouteId = viewModel.lastHandledImportedRouteId,
            onMarkImportedRouteHandled = { viewModel.lastHandledImportedRouteId = it },
            parkedSpot = parkedSpot,
            savedPlaces = savedPlaces,
            activeGuideTarget = activeGuideTarget,
            onSetGuideTarget = { label, lat, lon, badge ->
                viewModel.setTargetFinder(label, lat, lon, badge)
            },
            onClearGuideTarget = { viewModel.clearTargetFinder() },
            onSaveParkedSpot = { lat, lon, label ->
                viewModel.saveParkedSpot(lat, lon, label)
            },
            onClearParkedSpot = { viewModel.clearParkedSpot() },
            onOpenSavePlaceDialog = { suggestedName, lat, lon, alt ->
                savePlaceNameInput = suggestedName
                savePlaceCategory = "Spot"
                savePlaceNoteInput = ""
                savePlaceLat = lat
                savePlaceLon = lon
                savePlaceAlt = alt
                savePlaceTripId = null
                savePlacePhotoUriInput = null
                showSavePlaceDialog = true
            },
            onDeleteSavedPlace = { id ->
                viewModel.deleteSavedPlace(id)
                Toast.makeText(context, "Saved place removed", Toast.LENGTH_SHORT).show()
            },
            onCompassStateChanged = { az, lowAcc ->
                liveCompassAzimuth = az
                isCompassLowAccuracy = lowAcc
            },
            bottomOverlayPadding = bottomOverlayPadding
        )

        // Subtle Top Status Edge Scrim for clean legibility over bright map tiles
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(24.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorScheme.surface.copy(alpha = 0.72f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Top Instrument & Telemetry HUD Cluster
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .adaptiveContentWidth()
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .animateContentSize()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .testTag("telemetry_hud_card")
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = if (isHeaderExpanded) 12.dp else 8.dp
                    )
                ) {
                    val accentLineColor = when {
                        activeSession?.isPaused == true -> customColors.warning
                        activeSession != null -> colorScheme.error
                        currentLocation != null -> colorScheme.tertiary
                        else -> customColors.warning
                    }
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
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { isHeaderExpanded = !isHeaderExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(accentLineColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = if (isHeaderExpanded) "GeoTrace Live Telemetry" else "GeoTrace",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = if (!isHeaderExpanded) {
                                        val fixText = when {
                                            currentLocation != null ->
                                                "${String.format(Locale.US, "%.1f", compactSpeed)} ${settings.speedUnit.label} • ±${currentLocation?.accuracy?.toInt()}m"
                                            lastKnownCachedLocation != null -> {
                                                val cached = lastKnownCachedLocation!!
                                                val age = formatCachedAge(cached.timestamp, nowMs)
                                                "Cached $age (±${cached.accuracy.toInt()}m) • Awaiting GNSS"
                                            }
                                            else -> "Awaiting fix"
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
                                            lastKnownCachedLocation != null -> {
                                                val cached = lastKnownCachedLocation!!
                                                val age = formatCachedAge(cached.timestamp, nowMs).uppercase(Locale.US)
                                                "OFFLINE CACHED FIX ($age • ±${cached.accuracy.toInt()}M) • AWAITING GNSS"
                                            }
                                            else -> "STANDBY • AWAITING LOCATION FIX"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (isHeaderExpanded) 9.5.sp else 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = if (isHeaderExpanded) InterFontFamily else JetBrainsMonoFontFamily,
                                        fontFeatureSettings = if (isHeaderExpanded) null else "tnum",
                                        letterSpacing = 0.03.sp
                                    ),
                                    color = if (trackingMode == TrackingMode.POWER_SAVER && isHeaderExpanded) customColors.warning else accentLineColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isHeaderExpanded) colorScheme.primary.copy(alpha = 0.12f) else customColors.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (isHeaderExpanded) colorScheme.primary.copy(alpha = 0.45f)
                                    else colorScheme.outlineVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable { isHeaderExpanded = !isHeaderExpanded }
                                    .testTag("toggle_header_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "HUD",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = if (isHeaderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isHeaderExpanded) "Collapse Telemetry HUD" else "Expand Telemetry HUD",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onOpenMenu,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(customColors.surfaceContainerLow, CircleShape)
                                    .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), CircleShape)
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
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (vectorCoordsCopied) colorScheme.primary.copy(alpha = 0.12f) else customColors.surfaceContainerLow,
                                    border = BorderStroke(
                                        1.dp,
                                        if (vectorCoordsCopied) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
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
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                color = if (vectorCoordsCopied) colorScheme.primary else colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (hasFix) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (vectorCoordsCopied) colorScheme.primary else colorScheme.surface
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                                                        .size(48.dp)
                                                        .testTag("share_vector_coord_btn")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Share Coordinates",
                                                        tint = colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 4 Tabular Telemetry Scalar Metrics with Consistent 3-Row Vertical Alignment
                                val convertedSpeed = (currentLocation?.speed ?: 0f) * settings.speedUnit.multiplier
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(customColors.surfaceContainerLow, RoundedCornerShape(16.dp))
                                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        val pace = activeSession?.avgPaceMinPerKm?.takeIf { it != "--:--" }
                                            ?: currentLocation?.paceMinPerKm?.takeIf { it != "--:--" }
                                        TelemetryMetric(
                                            label = "DISTANCE",
                                            value = activeSession?.formattedDistance ?: "0 m",
                                            unit = "",
                                            valueColor = colorScheme.secondary,
                                            subtitle = pace ?: "No pace"
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        TelemetryMetric(
                                            label = "ACCURACY",
                                            value = if (currentLocation != null) "±${currentLocation?.accuracy?.toInt()}m" else "--",
                                            unit = "",
                                            valueColor = if ((currentLocation?.accuracy ?: 99f) <= 10f) colorScheme.tertiary else customColors.warning,
                                            subtitle = currentLocation?.provider?.uppercase() ?: "Standby"
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
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
                                                waypointPhotoUriInput = null
                                                showWaypointDialog = true
                                            },
                                            enabled = currentLocation != null,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("drop_waypoint_hud_btn")
                                        ) {
                                            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pin Waypoint", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        val isPaused = activeSession?.isPaused == true
                                        FilledTonalButton(
                                            onClick = {
                                                if (isPaused) viewModel.resumeTracking() else viewModel.pauseTracking()
                                            },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (isPaused) colorScheme.tertiaryContainer else colorScheme.surfaceVariant,
                                                contentColor = if (isPaused) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
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

            // Quick Floating Badges Row (Edge-to-edge scroll with clean 14dp leading/trailing gutter; zero clipping)
            val badgesScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(badgesScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(6.dp))

                // Compact Persistent Record Trail Pill (covers minimal horizontal width & stays anchored across HUD states)
                if (activeSession == null && !isPickingGeofenceCenter) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .clip(CircleShape)
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start Trail Recording",
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Record Trail",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Place & Coordinate Search Pill
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(
                        1.dp,
                        if (selectedSearchPin != null) colorScheme.primary else colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .clickable {
                            viewModel.refreshSearchNetworkStatus()
                            viewModel.updateSearchQuery(searchQuery, deviceSearchTargets)
                            showSearchDialog = true
                        }
                        .testTag("open_search_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = if (selectedSearchPin != null) 124.dp else 72.dp)
                        )
                    }
                }

                // Floating Positioning Profile Pill
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .clickable { showProfileDialog = true }
                        .testTag("floating_profile_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                            shape = CircleShape,
                            color = profile.badgeColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = profile.power,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = profile.badgeColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                // Floating Devices / Tracking Group & Off-Grid P2P Pill (opaque composited container over surface)
                if (hasQuickLocateTargets || activeGroup != null || isP2PEnabled) {
                    val chipContainerColor = when {
                        activeGroup != null -> colorScheme.primaryContainer.copy(alpha = 0.42f).compositeOver(colorScheme.surface)
                        deduplicatedPersonalDevices.isNotEmpty() -> colorScheme.secondaryContainer.copy(alpha = 0.42f).compositeOver(colorScheme.surface)
                        isP2PEnabled -> colorScheme.tertiaryContainer.copy(alpha = 0.42f).compositeOver(colorScheme.surface)
                        else -> colorScheme.surface
                    }
                    Surface(
                        shape = CircleShape,
                        color = chipContainerColor,
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
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.warning
                                )
                            }
                        }
                    }
                }

                // Compact Offline Download Progress Chip (shown when dialog is minimized/closed)
                if (offlineProgress.isDownloading && !showOfflineMapsDialog) {
                    val pct = if (offlineProgress.totalTiles > 0) {
                        ((offlineProgress.completedTiles * 100) / offlineProgress.totalTiles).coerceIn(0, 100)
                    } else 0
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer.copy(alpha = 0.65f).compositeOver(colorScheme.surface),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, colorScheme.primary),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .clickable { showOfflineMapsDialog = true }
                            .testTag("offline_caching_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
                    val zoneTint = if (isHotspot) customColors.warning else colorScheme.tertiary
                    Surface(
                        shape = CircleShape,
                        color = zoneTint.copy(alpha = 0.16f).compositeOver(colorScheme.surface),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, zoneTint),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("active_geofence_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(zoneTint)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeZone.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = zoneTint
                            )
                        }
                    }
                }

                // Cell Tower Pin Status Chip
                if (settings.showCellTowerPin && cellTowerLocation != null) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surface,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, customColors.warning.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.toggleCellTowerPin(false)
                                Toast.makeText(
                                    context,
                                    "Cell tower pin hidden — restore in Layers",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .testTag("cell_tower_status_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = null,
                                tint = customColors.warning,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Tower ±${cellTowerLocation?.rangeMeters?.toInt()}m",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = customColors.warning
                            )
                        }
                    }
                }

                // Offline Solar Daylight / Sunset Safety Chip
                val daylightRefLat = currentLocation?.latitude ?: currentMapBounds?.centerLatitude
                val daylightRefLon = currentLocation?.longitude ?: currentMapBounds?.centerLongitude
                val solarInfo = remember(daylightRefLat, daylightRefLon, nowMs / 60_000L) {
                    if (daylightRefLat != null && daylightRefLon != null &&
                        (daylightRefLat != 0.0 || daylightRefLon != 0.0)
                    ) {
                        SolarCalculator.calculateDaylight(daylightRefLat, daylightRefLon, nowMs)
                    } else null
                }
                if (solarInfo != null) {
                    val sunsetWarn = solarInfo.isLowLightSoon
                    val solarTint = when {
                        sunsetWarn -> customColors.warning
                        solarInfo.isDaylight -> colorScheme.tertiary
                        else -> colorScheme.secondary
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (sunsetWarn) customColors.warning.copy(alpha = 0.16f).compositeOver(colorScheme.surface) else colorScheme.surface,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, if (sunsetWarn) customColors.warning else colorScheme.outlineVariant),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("daylight_solar_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (solarInfo.isDaylight) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = "Local Daylight Status",
                                tint = solarTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = solarInfo.formatHudBadge(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sunsetWarn) customColors.warning else colorScheme.onSurface
                            )
                        }
                    }
                }

                // Persistent Park / Base Camp Pin Chip
                Surface(
                    shape = CircleShape,
                    color = if (parkedSpot != null) customColors.warning.copy(alpha = 0.14f).compositeOver(colorScheme.surface) else colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, if (parkedSpot != null) customColors.warning else colorScheme.outlineVariant),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (parkedSpot == null) {
                                val loc = currentLocation
                                if (loc == null) {
                                    Toast.makeText(context, "Waiting for GPS lock to save Park/Camp pin.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.saveParkedSpot(loc.latitude, loc.longitude, "Parked Vehicle / Camp")
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "Saved Park/Camp spot at current position.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                showParkedSpotDialog = true
                            }
                        }
                        .testTag("park_camp_quick_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalParking,
                            contentDescription = "Mark or Guide to Parked Vehicle / Camp",
                            tint = if (parkedSpot != null) customColors.warning else colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        val parkChipText = if (parkedSpot != null) {
                            val spot = parkedSpot!!
                            val dist = currentLocation?.let { cur ->
                                viewModel.calculateDistanceMeters(cur.latitude, cur.longitude, spot.latitude, spot.longitude)
                            }
                            if (dist == null) "Park Saved"
                            else if (dist < 1000.0) "Park ${dist.toInt()}m"
                            else String.format(Locale.US, "Park %.1fkm", dist / 1000.0)
                        } else {
                            "Park/Camp"
                        }
                        Text(
                            text = parkChipText,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }

                // Backtrack Active Trail Chip (shown when recording >= 2 points)
                if (activeSession != null && trailPoints.size >= 2) {
                    Surface(
                        shape = CircleShape,
                        color = if (isBacktrackingActiveTrail) colorScheme.tertiary.copy(alpha = 0.16f).compositeOver(colorScheme.surface) else colorScheme.surface,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, if (isBacktrackingActiveTrail) colorScheme.tertiary else colorScheme.outlineVariant),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (isBacktrackingActiveTrail) {
                                    viewModel.stopBacktrackActiveTrail()
                                } else {
                                    if (viewModel.startBacktrackActiveTrail()) {
                                        Toast.makeText(context, "Backtrack guide active toward trail start", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .testTag("backtrack_trail_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Backtrack Active Trail",
                                tint = if (isBacktrackingActiveTrail) colorScheme.tertiary else colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isBacktrackingActiveTrail) "Backtracking" else "Backtrack",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBacktrackingActiveTrail) colorScheme.tertiary else colorScheme.onSurface
                            )
                        }
                    }
                }

                // Import GPX / KML / GeoJSON Route Chip
                Surface(
                    shape = CircleShape,
                    color = if (importedRoute != null) colorScheme.tertiary.copy(alpha = 0.14f).compositeOver(colorScheme.surface) else colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, if (importedRoute != null) colorScheme.tertiary else colorScheme.outlineVariant),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .clickable {
                            importRouteLauncher.launch(arrayOf("*/*"))
                        }
                        .testTag("import_route_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Import GPX, KML, or GeoJSON Route",
                            tint = if (importedRoute != null) colorScheme.tertiary else colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = importedRoute?.sourceFormat?.let { "$it Route" } ?: "Import Route",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }

                // Standalone Save Place Bookmark Chip
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable {
                            val loc = currentLocation
                            val bounds = currentMapBounds
                            val lat = loc?.latitude ?: bounds?.centerLatitude
                            val lon = loc?.longitude ?: bounds?.centerLongitude
                            if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) {
                                Toast.makeText(context, "Waiting for GPS lock or map center.", Toast.LENGTH_SHORT).show()
                            } else {
                                savePlaceNameInput = ""
                                savePlaceCategory = "Spot"
                                savePlaceNoteInput = ""
                                savePlaceLat = lat
                                savePlaceLon = lon
                                savePlaceAlt = loc?.altitude ?: 0.0
                                savePlaceTripId = null
                                savePlacePhotoUriInput = null
                                showSavePlaceDialog = true
                            }
                        }
                        .testTag("save_spot_quick_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "Save Place Bookmark",
                            tint = colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (savedPlaces.isNotEmpty()) "Save (${savedPlaces.size})" else "Save Spot",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }

                // 1-Tap SOS Emergency Share Chip
                Surface(
                    shape = CircleShape,
                    color = colorScheme.error.copy(alpha = 0.14f).compositeOver(colorScheme.surface),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val sosBody = viewModel.buildCurrentSosMessage()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "GeoTrace SOS Emergency Position")
                                putExtra(Intent.EXTRA_TEXT, sosBody)
                            }
                            val chooser = Intent.createChooser(sendIntent, "Send SOS Coordinates")
                            if (context !is Activity) {
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(chooser)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "No compatible messaging or share app installed.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .testTag("sos_quick_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Share Emergency SOS Coordinates",
                            tint = colorScheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "SOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
            }

            // Line-of-Sight True-North Target Compass Finder Banner
            val guideTarget = activeGuideTarget
            val curLocForGuide = currentLocation
            if (!isPickingGeofenceCenter && guideTarget != null && curLocForGuide != null) {
                val targetDistMeters = remember(
                    curLocForGuide.latitude,
                    curLocForGuide.longitude,
                    guideTarget.latitude,
                    guideTarget.longitude
                ) {
                    ElevationAnalyzer.haversineMeters(
                        curLocForGuide.latitude,
                        curLocForGuide.longitude,
                        guideTarget.latitude,
                        guideTarget.longitude
                    )
                }
                val trueBearingDeg = remember(
                    curLocForGuide.latitude,
                    curLocForGuide.longitude,
                    guideTarget.latitude,
                    guideTarget.longitude
                ) {
                    ElevationAnalyzer.initialBearingDegrees(
                        curLocForGuide.latitude,
                        curLocForGuide.longitude,
                        guideTarget.latitude,
                        guideTarget.longitude
                    )
                }
                val userHeadingDeg = liveCompassAzimuth
                    ?: curLocForGuide.bearing.takeIf { it.isFinite() && curLocForGuide.speed >= 1.5f }
                val arrowRotationDeg = if (userHeadingDeg != null) {
                    shortestAngleDeltaDegrees(userHeadingDeg, trueBearingDeg)
                } else {
                    trueBearingDeg
                }
                val distLabel = if (targetDistMeters < 1000.0) {
                    "${targetDistMeters.toInt()} m"
                } else {
                    String.format(Locale.US, "%.2f km", targetDistMeters / 1000.0)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = BorderStroke(
                        1.5.dp,
                        if (isCompassLowAccuracy) customColors.warning else colorScheme.tertiary
                    ),
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .testTag("compass_target_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colorScheme.tertiary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Target Direction Arrow",
                                tint = colorScheme.tertiary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        rotationZ = arrowRotationDeg
                                    }
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${guideTarget.badge}: ${guideTarget.label}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Text(
                                text = buildString {
                                    append(distLabel)
                                    append(" • ")
                                    append("${trueBearingDeg.toInt()}° ${formatCompassCardinal(trueBearingDeg)}")
                                    if (userHeadingDeg == null) append(" (True N)")
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = colorScheme.tertiary
                            )
                            if (isCompassLowAccuracy) {
                                Text(
                                    text = "Calibrate compass (figure-8) — magnetic interference detected",
                                    fontSize = 9.5.sp,
                                    color = customColors.warning,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.clearTargetFinder()
                                if (isBacktrackingActiveTrail) viewModel.stopBacktrackActiveTrail()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_compass_finder_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Stop Target Guidance",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Active Imported GPX/KML Route or Backtrack Off-Trail Progress Banner
            val activeNavRoute = importedRoute
            val activeNavPoints = remember(activeNavRoute, isBacktrackingActiveTrail, trailPoints) {
                when {
                    activeNavRoute != null -> activeNavRoute.points
                    isBacktrackingActiveTrail && trailPoints.size >= 2 -> trailPoints.reversed()
                    else -> emptyList()
                }
            }
            if (!isPickingGeofenceCenter && activeNavPoints.isNotEmpty()) {
                val routeTitle = activeNavRoute?.name ?: "Backtrack: ${activeSession?.name ?: "Active Trail"}"
                val progressMatch = routeProgress
                val offTrailMeters = progressMatch?.crossTrackDistanceMeters ?: 0.0
                val isOffTrail = progressMatch != null && offTrailMeters > 45.0
                val remDistMeters = progressMatch?.remainingDistanceMeters ?: (activeNavRoute?.totalDistanceMeters ?: 0.0)
                val remLabel = if (remDistMeters < 1000.0) {
                    "${remDistMeters.toInt()} m"
                } else {
                    String.format(Locale.US, "%.2f km", remDistMeters / 1000.0)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isOffTrail) customColors.warning.copy(alpha = 0.14f).compositeOver(colorScheme.surface) else colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.5.dp, if (isOffTrail) customColors.warning else colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .testTag("route_guidance_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOffTrail) Icons.Default.Warning else Icons.Default.Route,
                            contentDescription = null,
                            tint = if (isOffTrail) customColors.warning else colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routeTitle,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append("Left: $remLabel")
                                    if (isOffTrail) {
                                        append(" • OFF ROUTE ${offTrailMeters.toInt()}m")
                                    } else if (activeNavRoute != null && activeNavRoute.elevationGainMeters >= 5.0) {
                                        append(" • +${activeNavRoute.elevationGainMeters.toInt()}m ↑")
                                    } else if (progressMatch != null) {
                                        append(" • On trail (±${offTrailMeters.toInt()}m)")
                                    }
                                },
                                fontSize = 10.5.sp,
                                fontWeight = if (isOffTrail) FontWeight.Bold else FontWeight.Medium,
                                color = if (isOffTrail) customColors.warning else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (activeNavRoute != null && activeNavPoints.isNotEmpty()) {
                            val isGuidingRoute = activeGuideTarget?.badge == "ROUTE"
                            TextButton(
                                onClick = {
                                    if (isGuidingRoute) {
                                        viewModel.clearTargetFinder()
                                    } else {
                                        viewModel.startRouteGuidance()
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("route_guidance_toggle_btn")
                            ) {
                                Text(
                                    text = if (isGuidingRoute) "Guiding" else "Point",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGuidingRoute) customColors.warning else colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (activeNavRoute != null) viewModel.clearImportedRoute()
                                if (isBacktrackingActiveTrail) viewModel.stopBacktrackActiveTrail()
                                if (activeGuideTarget?.badge == "ROUTE") viewModel.clearTargetFinder()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_route_guidance_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Route Overlay",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (!isPickingGeofenceCenter && lastGaspBeacon != null) {
                val beacon = lastGaspBeacon!!
                val beaconTime = remember(beacon.timestamp) {
                    SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(beacon.timestamp))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.errorContainer.copy(alpha = 0.85f).compositeOver(colorScheme.surface),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.5.dp, colorScheme.error.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .testTag("last_gasp_beacon_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = "Low-Battery Last-Gasp Beacon",
                            tint = colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Last-Gasp Beacon • ${beacon.batteryPercent}% batt ($beaconTime)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(formatCoordinates(beacon.latitude, beacon.longitude, settings.coordinateFormat))
                                    if (beacon.cellSummary.isNotBlank()) {
                                        append(" • ${beacon.cellSummary}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.clearInspectedTrail()
                                viewModel.selectSearchResult(
                                    SearchPlaceResult(
                                        id = "last_gasp_${beacon.timestamp}",
                                        name = "Last-Gasp Beacon (${beacon.batteryPercent}%)",
                                        subtitle = "Low-battery fix saved $beaconTime",
                                        latitude = beacon.latitude,
                                        longitude = beacon.longitude,
                                        source = SearchResultSource.SAVED_PLACE
                                    ),
                                    saveRecent = false
                                )
                                viewModel.setTargetFinder(
                                    label = "Last-Gasp (${beacon.batteryPercent}%)",
                                    latitude = beacon.latitude,
                                    longitude = beacon.longitude,
                                    badge = "BEACON"
                                )
                            },
                            shape = CircleShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("focus_last_gasp_btn")
                        ) {
                            Text("Locate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                savePlaceNameInput = "Last-Gasp (${beacon.batteryPercent}%)"
                                savePlaceCategory = "Spot"
                                savePlaceNoteInput = beacon.cellSummary
                                savePlaceLat = beacon.latitude
                                savePlaceLon = beacon.longitude
                                savePlaceAlt = beacon.altitude
                                savePlacePhotoUriInput = null
                                savePlaceTripId = null
                                showSavePlaceDialog = true
                            },
                            shape = CircleShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("save_last_gasp_btn")
                        ) {
                            Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                        }
                        IconButton(
                            onClick = { viewModel.clearLastGaspBeacon() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("dismiss_last_gasp_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Last-Gasp Beacon",
                                tint = colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isPickingGeofenceCenter) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.tertiaryContainer,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.5.dp, colorScheme.tertiary),
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .testTag("map_pick_geofence_banner")
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
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("cancel_map_pick_btn")
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
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
                    .padding(start = 14.dp, end = 78.dp, bottom = 14.dp + bottomOverlayPadding)
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
                        style = TelemetryScalarStyle.copy(
                            fontSize = 12.sp,
                            color = accentPillColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (isPaused) viewModel.resumeTracking() else viewModel.pauseTracking()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("pause_resume_pill_btn")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume Recording" else "Pause Recording",
                            tint = if (isPaused) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Button(
                        onClick = { viewModel.stopTracking() },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("stop_track_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError
                        ),
                        shape = CircleShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
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
        val focusManager = LocalFocusManager.current
        val submitWaypoint = {
            viewModel.dropWaypoint(waypointNameInput, waypointNoteInput, waypointPhotoUriInput) { wp, photoFailed ->
                if (wp != null) {
                    val msg = if (photoFailed) {
                        "Pinned ${wp.name} (photo could not be attached)"
                    } else {
                        "Pinned ${wp.name}"
                    }
                    android.widget.Toast.makeText(
                        context,
                        msg,
                        if (photoFailed) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Cannot pin waypoint: no location fix available.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            waypointPhotoUriInput = null
            showWaypointDialog = false
        }
        AlertDialog(
            onDismissRequest = {
                waypointPhotoUriInput = null
                showWaypointDialog = false
            },
            title = {
                Text(
                    "Pin Field Waypoint",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        supportingText = {
                            Text("Leave blank to auto-name (e.g. WPT-1)", fontSize = 11.sp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waypoint_name_input")
                    )
                    OutlinedTextField(
                        value = waypointNoteInput,
                        onValueChange = { waypointNoteInput = it },
                        label = { Text("Field Note (Optional)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (loc != null) {
                                    submitWaypoint()
                                }
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waypoint_note_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                activePhotoPickerTarget = "waypoint"
                                pickPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("waypoint_attach_photo_btn")
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (waypointPhotoUriInput == null) "Attach Photo (Offline)" else "Change Photo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (waypointPhotoUriInput != null) {
                            IconButton(
                                onClick = { waypointPhotoUriInput = null },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove attached photo",
                                    tint = colorScheme.error
                                )
                            }
                        }
                    }

                    if (!waypointPhotoUriInput.isNullOrBlank()) {
                        AsyncImage(
                            model = waypointPhotoUriInput,
                            contentDescription = "Waypoint attached photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitWaypoint() },
                    enabled = loc != null,
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_drop_waypoint_btn")
                ) {
                    Text("Save Waypoint", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        waypointPhotoUriInput = null
                        showWaypointDialog = false
                    },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
    }

    // Standalone Save Place Bookmark Dialog
    if (showSavePlaceDialog && savePlaceLat != null && savePlaceLon != null) {
        val targetLat = savePlaceLat!!
        val targetLon = savePlaceLon!!
        val categories = remember { listOf("Spot", "Lodge", "Camp", "Trailhead", "Viewpoint", "Water", "Medical") }
        AlertDialog(
            onDismissRequest = {
                savePlacePhotoUriInput = null
                showSavePlaceDialog = false
            },
            title = {
                Text(
                    text = "Save Place Bookmark",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .testTag("save_place_dialog"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatCoordinates(targetLat, targetLon, settings.coordinateFormat),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum"
                        ),
                        color = colorScheme.primary
                    )
                    OutlinedTextField(
                        value = savePlaceNameInput,
                        onValueChange = { savePlaceNameInput = it },
                        label = { Text("Place Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Trailhead Gate or Spring", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_place_name_input")
                    )
                    Text(
                        text = "Category",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (cat in categories) {
                            FilterChip(
                                selected = savePlaceCategory == cat,
                                onClick = { savePlaceCategory = cat },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = savePlaceNoteInput,
                        onValueChange = { savePlaceNoteInput = it },
                        label = { Text("Field Note (Optional)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_place_note_input")
                    )
                    if (trips.isNotEmpty()) {
                        Text(
                            text = "Attach to Trip (Optional)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = savePlaceTripId == null,
                                onClick = { savePlaceTripId = null },
                                label = { Text("Standalone", fontSize = 11.sp) },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            for (trip in trips) {
                                FilterChip(
                                    selected = savePlaceTripId == trip.id,
                                    onClick = { savePlaceTripId = trip.id },
                                    label = { Text(trip.name, fontSize = 11.sp, maxLines = 1) },
                                    shape = CircleShape,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                activePhotoPickerTarget = "save_place"
                                pickPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_place_attach_photo_btn")
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (savePlacePhotoUriInput == null) "Attach Photo" else "Change Photo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (savePlacePhotoUriInput != null) {
                            IconButton(
                                onClick = { savePlacePhotoUriInput = null },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove place photo",
                                    tint = colorScheme.error
                                )
                            }
                        }
                    }
                    if (!savePlacePhotoUriInput.isNullOrBlank()) {
                        AsyncImage(
                            model = savePlacePhotoUriInput,
                            contentDescription = "Saved place attached photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.savePlace(
                            name = savePlaceNameInput,
                            category = savePlaceCategory,
                            note = savePlaceNoteInput,
                            latitude = targetLat,
                            longitude = targetLon,
                            altitude = savePlaceAlt,
                            photoPickerUri = savePlacePhotoUriInput,
                            tripId = savePlaceTripId
                        ) { saved, photoFailed ->
                            val msg = if (photoFailed) {
                                "Saved \"${saved.name}\" (photo could not be attached)"
                            } else {
                                "Saved \"${saved.name}\""
                            }
                            Toast.makeText(
                                context,
                                msg,
                                if (photoFailed) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                            ).show()
                        }
                        savePlacePhotoUriInput = null
                        showSavePlaceDialog = false
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_save_place_btn")
                ) {
                    Text("Save Place", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        savePlacePhotoUriInput = null
                        showSavePlaceDialog = false
                    },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
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
                    shape = CircleShape
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
    }

    // Start Session Dialog (Level 3 Elevation)
    if (showStartDialog) {
        val confirmStartSession = {
            val name = sessionNameInput.ifBlank { "Field Trail" }
            val started = viewModel.startTracking(name, trackingMode, tripId = selectedStartTripId)
            if (!started) {
                Toast.makeText(context, "Location permission is required to record a trail.", Toast.LENGTH_SHORT).show()
            }
            showStartDialog = false
        }
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
                        supportingText = {
                            Text("Defaults to \"Field Trail\" if left blank", fontSize = 11.sp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { confirmStartSession() }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_name_input")
                    )
                    if (trips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Assign to Trip (Optional)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedStartTripId == null,
                                onClick = { selectedStartTripId = null },
                                label = { Text("None", fontSize = 11.sp) },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            for (trip in trips) {
                                FilterChip(
                                    selected = selectedStartTripId == trip.id,
                                    onClick = { selectedStartTripId = trip.id },
                                    label = { Text(trip.name, fontSize = 11.sp, maxLines = 1) },
                                    shape = CircleShape,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmStartSession() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_start_btn")
                ) {
                    Text("Start Recording", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartDialog = false },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
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
                    .adaptiveContentWidth()
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
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
                        shape = CircleShape,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("manage_groups_from_sheet_btn")
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
                        shape = RoundedCornerShape(20.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (isOffline) customColors.warning.copy(alpha = 0.5f) else colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
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
                                    Toast.makeText(
                                        context,
                                        "Waiting for ${member.displayName}'s location sync...",
                                        Toast.LENGTH_SHORT
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
                                    color = if (isOffline) customColors.warning else colorScheme.onSurfaceVariant
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
                        shape = RoundedCornerShape(20.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (devStale) customColors.warning.copy(alpha = 0.5f) else customColors.fleetAccent.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
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
                                    Toast.makeText(
                                        context,
                                        "Waiting for ${device.deviceName.ifBlank { "device" }} location sync...",
                                        Toast.LENGTH_SHORT
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
                                    .background(customColors.fleetAccent),
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
                                    color = if (devStale) customColors.warning else colorScheme.onSurfaceVariant
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
                        shape = RoundedCornerShape(20.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
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

    // Saved Parked Vehicle / Base Camp Dialog
    if (showParkedSpotDialog && parkedSpot != null) {
        val spot = parkedSpot!!
        val savedTimeStr = remember(spot.savedAtMs) {
            SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(spot.savedAtMs))
        }
        val distLabel = currentLocation?.let { cur ->
            val d = viewModel.calculateDistanceMeters(cur.latitude, cur.longitude, spot.latitude, spot.longitude)
            if (d < 1000.0) "${d.toInt()} m away" else String.format(Locale.US, "%.2f km away", d / 1000.0)
        } ?: "Distance pending GPS fix"

        AlertDialog(
            onDismissRequest = { showParkedSpotDialog = false },
            title = {
                Text(
                    text = spot.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${formatCoordinates(spot.latitude, spot.longitude, settings.coordinateFormat)}\n$distLabel • Saved $savedTimeStr",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.setTargetFinder(spot.name, spot.latitude, spot.longitude, "PARK / CAMP")
                                showParkedSpotDialog = false
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("park_dialog_point_btn")
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Point Back", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = {
                                val loc = currentLocation
                                if (loc != null) {
                                    viewModel.saveParkedSpot(loc.latitude, loc.longitude, spot.name)
                                    Toast.makeText(context, "Updated Park/Camp location", Toast.LENGTH_SHORT).show()
                                    showParkedSpotDialog = false
                                } else {
                                    Toast.makeText(context, "Awaiting GPS fix", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("park_dialog_update_btn")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Mark Here", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showParkedSpotDialog = false },
                    shape = CircleShape,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearParkedSpot()
                        showParkedSpotDialog = false
                        Toast.makeText(context, "Cleared Park/Camp pin", Toast.LENGTH_SHORT).show()
                    },
                    shape = CircleShape,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Clear Pin", fontSize = 12.sp, color = colorScheme.error)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
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
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
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
                    shape = CircleShape,
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = power,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
            style = TelemetryHeaderLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = TelemetryScalarStyle.copy(color = valueColor),
                maxLines = 1
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Text(
            text = subtitle?.takeIf { it.isNotBlank() } ?: " ",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

private fun formatCachedAge(timestampMs: Long, nowMs: Long): String {
    val diffSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3600L}h ago"
        else -> "${diffSec / 86_400L}d ago"
    }
}
