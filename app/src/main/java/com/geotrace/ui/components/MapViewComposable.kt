package com.geotrace.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import com.geotrace.data.export.ImportedRoute
import com.geotrace.model.ActiveGuideTarget
import com.geotrace.model.LocationPoint
import com.geotrace.model.ParkedSpot
import com.geotrace.model.SavedPlace
import com.geotrace.navigation.ActiveNavigationState
import com.geotrace.navigation.NavManeuverType
import com.geotrace.navigation.NavPreviewState
import com.geotrace.service.normalizeHeadingDegrees
import com.geotrace.service.rememberCompassReading
import com.geotrace.service.shortestAngleDeltaDegrees
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import androidx.compose.runtime.rememberUpdatedState
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private data class MarkerInspectData(
    val title: String,
    val detail: String,
    val badge: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double = 0.0,
    val photoUri: String = "",
    val savedPlaceId: String? = null
)

private data class GeofenceLayerSig(
    val cachedGeofences: List<CachedGeofenceVisuals>,
    val hysteresisMeters: Float
)

private data class CellLayerSig(
    val showCellTowerPin: Boolean,
    val servingTower: com.geotrace.model.CellTowerLocation?,
    val allTowers: List<com.geotrace.model.CellTowerLocation>,
    val curLat: Double?,
    val curLon: Double?
)

private data class TrailLayerSig(
    val pointsSize: Int,
    val firstLat: Double?,
    val firstLon: Double?,
    val lastLat: Double?,
    val lastLon: Double?,
    val inspectedTrailKey: String?,
    val waypoints: List<com.geotrace.model.TrailWaypoint>,
    val coordinateFormat: com.geotrace.model.CoordinateFormat,
    val trailColorArgb: Int
)

private data class PeersLayerSig(
    val members: List<com.geotrace.model.MemberLocation>,
    val devices: List<com.geotrace.model.PersonalDevice>,
    val peers: List<com.geotrace.model.P2PPeer>,
    val deviceId: String,
    val userUid: String?,
    val speedUnit: com.geotrace.model.SpeedUnit
)

private data class ReferenceRouteLayerSig(
    val routeId: String?,
    val pointsCount: Int,
    val waypointsCount: Int,
    val coordinateFormat: com.geotrace.model.CoordinateFormat
)

private data class NavRouteLayerSig(
    val activeRouteId: String?,
    val nearestPointIndex: Int,
    val isOffRoute: Boolean,
    val currentStepIndex: Int,
    val previewKey: String?,
    val previewSelectedIndex: Int
)

private data class UserTargetLayerSig(
    val currentLocation: LocationPoint?,
    val resolvedBearing: Float,
    val snappedNavLat: Double?,
    val snappedNavLon: Double?,
    val snappedNavBearing: Float?,
    val isOffRoute: Boolean,
    val snappedToProjLat: Double?,
    val snappedToProjLon: Double?,
    val showAccuracyRing: Boolean,
    val speedUnit: com.geotrace.model.SpeedUnit,
    val focusedTarget: com.geotrace.model.MapFocusTarget?,
    val selectedSearchPin: com.geotrace.model.SearchPlaceResult?,
    val droppedInspectPin: GeoPoint?,
    val activeGuideTarget: ActiveGuideTarget?,
    val userPinColorArgb: Int
)

private data class StaticPinsLayerSig(
    val droppedInspectPin: GeoPoint?,
    val parkedSpot: ParkedSpot?,
    val savedPlaces: List<SavedPlace>,
    val selectedSearchPin: com.geotrace.model.SearchPlaceResult?,
    val coordinateFormat: com.geotrace.model.CoordinateFormat
)

private fun normalizeMapOrientationSigned(degrees: Float): Float {
    return shortestAngleDeltaDegrees(0f, degrees)
}

private class GeoTraceRotationOverlay(
    private val onRotationGestureStarted: () -> Unit,
    private val onOrientationChanged: (newOrientation: Float, cumulativeManualDelta: Float) -> Unit
) : Overlay() {
    private var activePointerId0 = MotionEvent.INVALID_POINTER_ID
    private var activePointerId1 = MotionEvent.INVALID_POINTER_ID
    private var lastAngleDeg = 0f
    private var pendingTwistDeg = 0f
    private var totalTwistInGesture = 0f
    private var isTwisting = false

    private fun pointerAngleDeg(event: MotionEvent, idx0: Int, idx1: Int): Float {
        val dx = (event.getX(idx0) - event.getX(idx1)).toDouble()
        val dy = (event.getY(idx0) - event.getY(idx1)).toDouble()
        return Math.toDegrees(atan2(dy, dx)).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId0 = event.getPointerId(0)
                activePointerId1 = MotionEvent.INVALID_POINTER_ID
                isTwisting = false
                pendingTwistDeg = 0f
                totalTwistInGesture = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    activePointerId0 = event.getPointerId(0)
                    activePointerId1 = event.getPointerId(1)
                    lastAngleDeg = pointerAngleDeg(event, 0, 1)
                    pendingTwistDeg = 0f
                    totalTwistInGesture = 0f
                    isTwisting = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2 &&
                    activePointerId0 != MotionEvent.INVALID_POINTER_ID &&
                    activePointerId1 != MotionEvent.INVALID_POINTER_ID
                ) {
                    val idx0 = event.findPointerIndex(activePointerId0)
                    val idx1 = event.findPointerIndex(activePointerId1)
                    if (idx0 >= 0 && idx1 >= 0) {
                        val currentAngle = pointerAngleDeg(event, idx0, idx1)
                        val delta = shortestAngleDeltaDegrees(lastAngleDeg, currentAngle)
                        lastAngleDeg = currentAngle
                        if (!isTwisting) {
                            pendingTwistDeg += delta
                            if (abs(pendingTwistDeg) >= 3.5f) {
                                isTwisting = true
                                onRotationGestureStarted()
                                val applyDelta = pendingTwistDeg
                                pendingTwistDeg = 0f
                                totalTwistInGesture += abs(applyDelta)
                                val nextOrientation = normalizeMapOrientationSigned(mapView.mapOrientation + applyDelta)
                                mapView.setMapOrientation(nextOrientation, true)
                                onOrientationChanged(mapView.mapOrientation, totalTwistInGesture)
                            }
                        } else if (abs(delta) > 0.05f) {
                            totalTwistInGesture += abs(delta)
                            val nextOrientation = normalizeMapOrientationSigned(mapView.mapOrientation + delta)
                            mapView.setMapOrientation(nextOrientation, true)
                            onOrientationChanged(mapView.mapOrientation, totalTwistInGesture)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    activePointerId1 = MotionEvent.INVALID_POINTER_ID
                    isTwisting = false
                    pendingTwistDeg = 0f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId0 = MotionEvent.INVALID_POINTER_ID
                activePointerId1 = MotionEvent.INVALID_POINTER_ID
                isTwisting = false
                pendingTwistDeg = 0f
            }
        }
        return false
    }
}

private data class CachedGeofenceVisuals(
    val zone: com.geotrace.model.GeofenceZone,
    val mainPoints: List<GeoPoint>,
    val outerExitPoints: List<GeoPoint>?,
    val innerEntryPoints: List<GeoPoint>?,
    val fillColor: Int,
    val outlineColor: Int,
    val hystColor: Int,
    val entryR: Double,
    val exitR: Double
)

private fun formatMarkerMode(rawMode: String): String = when (rawMode.uppercase()) {
    "HIGH_ACCURACY" -> "GPS High"
    "BALANCED" -> "Balanced"
    "POWER_SAVER" -> "Cell Only"
    else -> rawMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewComposable(
    currentLocation: LocationPoint?,
    trailPoints: List<LocationPoint>,
    geofenceZones: List<com.geotrace.model.GeofenceZone> = emptyList(),
    cellTowerLocation: com.geotrace.model.CellTowerLocation? = null,
    allCellTowers: List<com.geotrace.model.CellTowerLocation> = emptyList(),
    groupMembers: List<com.geotrace.model.MemberLocation> = emptyList(),
    personalDevices: List<com.geotrace.model.PersonalDevice> = emptyList(),
    currentDeviceId: String = "",
    currentUserUid: String? = null,
    nearbyPeers: List<com.geotrace.model.P2PPeer> = emptyList(),
    focusedTarget: com.geotrace.model.MapFocusTarget? = null,
    isRecording: Boolean = false,
    trailWaypoints: List<com.geotrace.model.TrailWaypoint> = emptyList(),
    inspectedTrailId: String? = null,
    inspectedTrailName: String? = null,
    inspectedTrailSummary: String? = null,
    selectedSearchPin: com.geotrace.model.SearchPlaceResult? = null,
    onClearSearchPin: (() -> Unit)? = null,
    lastHandledFocusMs: Long = 0L,
    onMarkFocusHandled: ((Long) -> Unit)? = null,
    lastHandledSearchMs: Long = 0L,
    onMarkSearchHandled: ((Long) -> Unit)? = null,
    lastHandledInspectedTrail: String? = null,
    onMarkInspectedTrailHandled: ((String) -> Unit)? = null,
    isFollowPaused: Boolean = false,
    onFollowPausedByUser: (() -> Unit)? = null,
    onClearInspectedTrail: (() -> Unit)? = null,
    onClearFocusTarget: (() -> Unit)? = null,
    onRecenterSelf: (() -> Unit)? = null,
    offlineRegionsCount: Int = 0,
    offlineMapRegions: List<com.geotrace.model.OfflineMapRegion> = emptyList(),
    coveredOfflineTileTypes: Set<com.geotrace.model.MapTileType> = emptySet(),
    isNetworkOnline: Boolean = true,
    settings: com.geotrace.model.TrackerSettings = com.geotrace.model.TrackerSettings(),
    onSelectTileLayer: ((com.geotrace.model.MapTileType) -> Unit)? = null,
    onToggleCellTowerPin: ((Boolean) -> Unit)? = null,
    onToggleOfflineRegionsOverlay: ((Boolean) -> Unit)? = null,
    onToggleAutoRotateHeadingUp: ((Boolean) -> Unit)? = null,
    onOpenOfflineMaps: (() -> Unit)? = null,
    onMapBoundsChanged: ((BoundingBox) -> Unit)? = null,
    initialMapCenter: GeoPoint? = null,
    fallbackCachedCenter: GeoPoint? = null,
    initialMapZoom: Double? = null,
    initialMapOrientation: Float = 0f,
    onSaveMapCamera: ((GeoPoint, Double, Float) -> Unit)? = null,
    isMapPickMode: Boolean = false,
    onMapCoordinatePicked: ((Double, Double) -> Unit)? = null,
    onMapSingleTap: (() -> Unit)? = null,
    importedRoute: ImportedRoute? = null,
    lastHandledImportedRouteId: String? = null,
    onMarkImportedRouteHandled: ((String) -> Unit)? = null,
    parkedSpot: ParkedSpot? = null,
    savedPlaces: List<SavedPlace> = emptyList(),
    activeGuideTarget: ActiveGuideTarget? = null,
    onSetGuideTarget: ((String, Double, Double, String) -> Unit)? = null,
    onClearGuideTarget: (() -> Unit)? = null,
    onSaveParkedSpot: ((Double, Double, String) -> Unit)? = null,
    onClearParkedSpot: (() -> Unit)? = null,
    onOpenSavePlaceDialog: ((String, Double, Double, Double) -> Unit)? = null,
    onDeleteSavedPlace: ((String) -> Unit)? = null,
    navPreviewState: NavPreviewState = NavPreviewState.Idle,
    activeNavigation: ActiveNavigationState? = null,
    onSelectPreviewRoute: ((Int) -> Unit)? = null,
    onRequestDirections: ((String, Double, Double) -> Unit)? = null,
    fabExtraBottomPadding: Dp = 0.dp,
    onCompassStateChanged: ((Float?, Boolean) -> Unit)? = null,
    bottomOverlayPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapCoordinatePicked by rememberUpdatedState(onMapCoordinatePicked)
    val currentOnMapSingleTap by rememberUpdatedState(onMapSingleTap)
    val currentIsMapPickMode by rememberUpdatedState(isMapPickMode)
    val currentOnFollowPausedByUser by rememberUpdatedState(onFollowPausedByUser)
    val currentOnSaveMapCamera by rememberUpdatedState(onSaveMapCamera)
    val currentOnSelectPreviewRoute by rememberUpdatedState(onSelectPreviewRoute)
    val currentLocationState by rememberUpdatedState(currentLocation)
    val currentCoordinateFormat by rememberUpdatedState(settings.coordinateFormat)
    val currentAutoRotateHeadingUp by rememberUpdatedState(settings.autoRotateHeadingUp)

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    // GeoTrace Telemetry Vector Colors
    val trailColor = colorScheme.secondary
    val userPinColor = colorScheme.primary

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnFirstFix by remember { mutableStateOf(initialMapCenter != null) }
    var hasCenteredOnCachedFallback by remember { mutableStateOf(initialMapCenter != null || currentLocation != null || fallbackCachedCenter != null) }
    var isShowingNeutralOrigin by remember {
        mutableStateOf(
            initialMapCenter == null &&
                currentLocation == null &&
                fallbackCachedCenter == null &&
                !((focusedTarget != null &&
                    (focusedTarget.latitude != 0.0 || focusedTarget.longitude != 0.0) &&
                    focusedTarget.requestedAtMs > lastHandledFocusMs) ||
                    (selectedSearchPin != null && selectedSearchPin.requestedAtMs > lastHandledSearchMs))
        )
    }
    var hasUserMovedCamera by remember { mutableStateOf(false) }
    var isUserInteractingWithMap by remember { mutableStateOf(false) }
    var liveMapOrientation by remember { mutableStateOf(initialMapOrientation) }
    var liveZoomLevel by remember { mutableStateOf(initialMapZoom ?: 16.5) }
    var liveCenterLat by remember { mutableStateOf(initialMapCenter?.latitude ?: currentLocation?.latitude ?: fallbackCachedCenter?.latitude ?: 0.0) }
    var isHeadingUpPausedByUser by remember { mutableStateOf(false) }
    var isNavOverviewMode by remember { mutableStateOf(false) }
    var lastNavRouteCameraId by remember { mutableStateOf<String?>(null) }
    val orientationAnimatorHolder = remember { arrayOfNulls<ValueAnimator>(1) }
    var inspectCardHeightPx by remember { mutableIntStateOf(0) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var selectedMarkerInfo by remember { mutableStateOf<MarkerInspectData?>(null) }
    var droppedInspectPin by remember { mutableStateOf<GeoPoint?>(null) }
    var inspectCoordsCopied by remember { mutableStateOf(false) }
    var expandedInspectPhotoPath by remember { mutableStateOf<String?>(null) }
    var localHandledFocusMs by remember(lastHandledFocusMs) { mutableStateOf(lastHandledFocusMs) }
    var localHandledSearchMs by remember(lastHandledSearchMs) { mutableStateOf(lastHandledSearchMs) }
    var localHandledTrailKey by remember(lastHandledInspectedTrail) { mutableStateOf(lastHandledInspectedTrail) }
    var localHandledImportedRouteKey by remember(lastHandledImportedRouteId) { mutableStateOf(lastHandledImportedRouteId) }
    LaunchedEffect(inspectCoordsCopied, selectedMarkerInfo, focusedTarget?.id) {
        if (inspectCoordsCopied) {
            kotlinx.coroutines.delay(1600L)
            inspectCoordsCopied = false
        }
    }

    val compassReading by rememberCompassReading(
        enabled = true,
        highPrecision = settings.autoRotateHeadingUp || activeGuideTarget != null,
        latitude = currentLocation?.latitude,
        longitude = currentLocation?.longitude,
        altitudeMeters = currentLocation?.altitude ?: 0.0
    )
    val compassHeading = compassReading.azimuthDegrees
    LaunchedEffect(compassReading.azimuthDegrees, compassReading.isLowAccuracy) {
        onCompassStateChanged?.invoke(compassReading.azimuthDegrees, compassReading.isLowAccuracy)
    }
    val resolvedUserBearing = remember(
        currentLocation?.bearing,
        currentLocation?.speed,
        compassHeading,
        activeNavigation?.isSnappedToRoad,
        activeNavigation?.snappedBearingDegrees
    ) {
        val loc = currentLocation
        val navSnap = activeNavigation?.takeIf { it.isSnappedToRoad }
        when {
            navSnap != null && loc != null && loc.speed >= 1.5f ->
                normalizeHeadingDegrees(navSnap.snappedBearingDegrees)
            loc != null && loc.speed >= 1.5f && loc.bearing.isFinite() -> normalizeHeadingDegrees(loc.bearing)
            compassHeading != null -> compassHeading
            navSnap != null -> normalizeHeadingDegrees(navSnap.snappedBearingDegrees)
            loc != null && loc.bearing.isFinite() -> normalizeHeadingDegrees(loc.bearing)
            else -> 0f
        }
    }
    val currentResolvedBearing by rememberUpdatedState(resolvedUserBearing)

    fun animateMapOrientationTo(targetDeg: Float, durationMs: Long = 280L) {
        val map = mapViewRef ?: return
        orientationAnimatorHolder[0]?.cancel()
        val startDeg = map.mapOrientation
        val normalizedTarget = normalizeMapOrientationSigned(targetDeg)
        val delta = shortestAngleDeltaDegrees(startDeg, normalizedTarget)
        if (abs(delta) < 0.35f) {
            val finalAngle = if (abs(normalizedTarget) < 0.35f) 0f else normalizedTarget
            if (map.mapOrientation != finalAngle) {
                map.setMapOrientation(finalAngle, true)
            }
            liveMapOrientation = map.mapOrientation
            val c = map.mapCenter
            currentOnSaveMapCamera?.invoke(GeoPoint(c.latitude, c.longitude), map.zoomLevelDouble, map.mapOrientation)
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val nextDeg = normalizeMapOrientationSigned(startDeg + delta * fraction)
                map.setMapOrientation(nextDeg, true)
                liveMapOrientation = map.mapOrientation
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val c = map.mapCenter
                    currentOnSaveMapCamera?.invoke(GeoPoint(c.latitude, c.longitude), map.zoomLevelDouble, map.mapOrientation)
                }
            })
        }
        orientationAnimatorHolder[0] = animator
        animator.start()
    }

    LaunchedEffect(settings.autoRotateHeadingUp) {
        if (settings.autoRotateHeadingUp) {
            isHeadingUpPausedByUser = false
        }
    }

    LaunchedEffect(
        resolvedUserBearing,
        settings.autoRotateHeadingUp,
        settings.followUserLocation,
        isFollowPaused,
        isNavOverviewMode,
        isHeadingUpPausedByUser,
        isUserInteractingWithMap,
        focusedTarget,
        selectedSearchPin,
        inspectedTrailName,
        mapViewRef
    ) {
        val shouldAutoRotate = settings.autoRotateHeadingUp &&
            settings.followUserLocation &&
            !isFollowPaused &&
            !isNavOverviewMode &&
            !isHeadingUpPausedByUser &&
            !isUserInteractingWithMap &&
            focusedTarget == null &&
            selectedSearchPin == null &&
            inspectedTrailName == null
        if (shouldAutoRotate) {
            val map = mapViewRef ?: return@LaunchedEffect
            val targetOrientation = normalizeMapOrientationSigned(-resolvedUserBearing)
            if (abs(shortestAngleDeltaDegrees(map.mapOrientation, targetOrientation)) >= 2.0f) {
                animateMapOrientationTo(targetOrientation, 260L)
            }
        }
    }

    val cachedGeofences = remember(geofenceZones, settings.geofenceHysteresisMeters) {
        val deltaHyst = settings.geofenceHysteresisMeters.toDouble().coerceAtLeast(0.0)
        geofenceZones.filter { it.isEnabled }.map { zone ->
            val zoneCenter = GeoPoint(zone.latitude, zone.longitude)
            val mainPoints = computeCircleGeoPoints(zoneCenter, zone.radiusMeters.toDouble())
            val zoneColor = try {
                android.graphics.Color.parseColor(zone.colorHex)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#006948")
            }
            val r = android.graphics.Color.red(zoneColor)
            val g = android.graphics.Color.green(zoneColor)
            val b = android.graphics.Color.blue(zoneColor)
            val entryR = (zone.radiusMeters - deltaHyst).coerceAtLeast(minOf(10.0, zone.radiusMeters * 0.5))
            val exitR = zone.radiusMeters + deltaHyst
            CachedGeofenceVisuals(
                zone = zone,
                mainPoints = mainPoints,
                outerExitPoints = if (deltaHyst > 0.0) computeCircleGeoPoints(zoneCenter, exitR) else null,
                innerEntryPoints = if (deltaHyst > 0.0 && entryR < zone.radiusMeters - 2.0) computeCircleGeoPoints(zoneCenter, entryR) else null,
                fillColor = android.graphics.Color.argb(40, r, g, b),
                outlineColor = android.graphics.Color.argb(210, r, g, b),
                hystColor = android.graphics.Color.argb(115, r, g, b),
                entryR = entryR,
                exitR = exitR
            )
        }
    }

    // Reusable GIS icon drawables (cached once per composition to avoid per-fix Bitmap churn)
    val gpsChevronDrawable = remember(context) {
        createUserChevronDrawable(context, android.graphics.Color.parseColor("#006194"))
    }
    val cellChevronDrawable = remember(context) {
        createUserChevronDrawable(context, android.graphics.Color.parseColor("#D97706"))
    }
    val servingTowerDrawable = remember(context) {
        createCellTowerDrawable(context, isServing = true)
    }
    val neighborTowerDrawable = remember(context) {
        createCellTowerDrawable(context, isServing = false)
    }
    val trailStartDrawable = remember(context) {
        createPinBadgeDrawable(context, "S", android.graphics.Color.parseColor("#059669"))
    }
    val trailEndDrawable = remember(context) {
        createPinBadgeDrawable(context, "E", android.graphics.Color.parseColor("#DC2626"))
    }
    val droppedPinDrawable = remember(context) {
        createPinBadgeDrawable(context, "•", android.graphics.Color.parseColor("#7C3AED"))
    }
    val searchPinDrawable = remember(context) {
        createPinBadgeDrawable(context, "★", android.graphics.Color.parseColor("#0F766E"))
    }
    val parkedPinDrawable = remember(context) {
        createPinBadgeDrawable(context, "P", android.graphics.Color.parseColor("#B45309"))
    }
    val savedPlaceDrawableCache = remember { mutableMapOf<String, android.graphics.drawable.Drawable>() }
    fun savedPlaceDrawableFor(category: String): android.graphics.drawable.Drawable {
        val key = category.trim().lowercase(Locale.US)
        return savedPlaceDrawableCache.getOrPut(key) {
            val (badge, hex) = when (key) {
                "lodge" -> "L" to "#0F766E"
                "camp" -> "C" to "#15803D"
                "water" -> "W" to "#0284C7"
                "medical" -> "M" to "#DC2626"
                "trailhead" -> "T" to "#7C3AED"
                "viewpoint" -> "V" to "#D97706"
                else -> "★" to "#0D9488"
            }
            createPinBadgeDrawable(context, badge, android.graphics.Color.parseColor(hex))
        }
    }
    val routeStartDrawable = remember(context) {
        createPinBadgeDrawable(context, "A", android.graphics.Color.parseColor("#0D9488"))
    }
    val routeEndDrawable = remember(context) {
        createPinBadgeDrawable(context, "B", android.graphics.Color.parseColor("#0F766E"))
    }
    val navTurnDotDrawable = remember(context) {
        createPinBadgeDrawable(context, "•", android.graphics.Color.parseColor("#1D4ED8"))
    }
    val navDestPinDrawable = remember(context) {
        createPinBadgeDrawable(context, "★", android.graphics.Color.parseColor("#DC2626"))
    }

    // Cache member & peer avatar drawables to prevent per-second Bitmap allocations
    val avatarDrawableCache = remember { mutableMapOf<String, android.graphics.drawable.Drawable>() }
    val waypointDrawableCache = remember { mutableMapOf<Int, android.graphics.drawable.Drawable>() }

    // Transparent street/place labels overlay for Satellite Hybrid mode
    val hybridLabelsProvider = remember(context) {
        MapTileProviderBasic(context.applicationContext, MapTileSources.hybridLabelsOverlay)
    }
    val hybridLabelsOverlay = remember(context, hybridLabelsProvider) {
        TilesOverlay(hybridLabelsProvider, context.applicationContext).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            loadingLineColor = android.graphics.Color.TRANSPARENT
        }
    }

    // Update Tile Source & offline data connection state dynamically
    LaunchedEffect(isNetworkOnline, mapViewRef) {
        hybridLabelsProvider.setUseDataConnection(isNetworkOnline)
        mapViewRef?.let { map ->
            map.setUseDataConnection(isNetworkOnline)
            map.invalidate()
        }
    }

    LaunchedEffect(settings.mapTileType, mapViewRef) {
        mapViewRef?.let { map ->
            val newSource = MapTileSources.baseTileSourceFor(settings.mapTileType)
            map.setTileSource(newSource)
            if (map.zoomLevelDouble > newSource.maximumZoomLevel.toDouble()) {
                map.controller.setZoom(newSource.maximumZoomLevel.toDouble())
            }
            if (settings.mapTileType == com.geotrace.model.MapTileType.HYBRID) {
                hybridLabelsProvider.setTileRequestCompleteHandler(map.tileRequestCompleteHandler)
            }
            map.invalidate()
        }
    }

    LaunchedEffect(fallbackCachedCenter, currentLocation, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        val fallback = fallbackCachedCenter ?: return@LaunchedEffect
        if (!hasCenteredOnCachedFallback &&
            !hasCenteredOnFirstFix &&
            !hasUserMovedCamera &&
            isShowingNeutralOrigin &&
            initialMapCenter == null &&
            currentLocation == null &&
            inspectedTrailName == null &&
            focusedTarget == null &&
            selectedSearchPin == null &&
            !isUserInteractingWithMap
        ) {
            map.controller.setCenter(fallback)
            map.controller.setZoom(15.5)
            hasCenteredOnCachedFallback = true
            isShowingNeutralOrigin = false
            currentOnSaveMapCamera?.invoke(fallback, 15.5, map.mapOrientation)
        }
    }

    // Auto-zoom to street level once when active navigation starts on a route
    LaunchedEffect(activeNavigation?.route?.id, mapViewRef) {
        val nav = activeNavigation
        if (nav == null) {
            lastNavRouteCameraId = null
            isNavOverviewMode = false
            return@LaunchedEffect
        }
        val map = mapViewRef ?: return@LaunchedEffect
        if (nav.route.id == lastNavRouteCameraId) return@LaunchedEffect
        lastNavRouteCameraId = nav.route.id
        isNavOverviewMode = false
        isHeadingUpPausedByUser = false
        hasCenteredOnFirstFix = true
        isShowingNeutralOrigin = false
        val targetPt = when {
            nav.isSnappedToRoad -> GeoPoint(nav.snappedLatitude, nav.snappedLongitude)
            currentLocation != null -> GeoPoint(currentLocation.latitude, currentLocation.longitude)
            else -> nav.route.points.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        } ?: return@LaunchedEffect
        map.controller.animateTo(targetPt, 17.5, 800L)
        if (settings.autoRotateHeadingUp) {
            animateMapOrientationTo(-resolvedUserBearing, 300L)
        }
        currentOnSaveMapCamera?.invoke(targetPt, 17.5, map.mapOrientation)
    }

    // Auto-center map ONLY on first genuine fix, or when followUserLocation is enabled AND user hasn't manually panned/zoomed away.
    // Never pass targetZoom on follow ticks (prevents fighting pinch-out / zoom-out gestures).
    LaunchedEffect(
        currentLocation,
        activeNavigation?.snappedLatitude,
        activeNavigation?.snappedLongitude,
        activeNavigation?.isSnappedToRoad,
        navPreviewState,
        settings.followUserLocation,
        isFollowPaused,
        isNavOverviewMode,
        focusedTarget,
        selectedSearchPin,
        inspectedTrailName
    ) {
        val isPreviewActive = navPreviewState !is NavPreviewState.Idle
        if (!isPreviewActive && inspectedTrailName == null && focusedTarget == null && selectedSearchPin == null && currentLocation != null) {
            val map = mapViewRef
            val followLat = activeNavigation?.takeIf { it.isSnappedToRoad }?.snappedLatitude ?: currentLocation.latitude
            val followLon = activeNavigation?.takeIf { it.isSnappedToRoad }?.snappedLongitude ?: currentLocation.longitude
            if (!hasCenteredOnFirstFix) {
                val targetPt = GeoPoint(followLat, followLon)
                map?.controller?.animateTo(
                    targetPt,
                    if (activeNavigation != null) 17.5 else 16.5,
                    800L
                )
                hasCenteredOnFirstFix = true
                isShowingNeutralOrigin = false
                currentOnSaveMapCamera?.invoke(targetPt, if (activeNavigation != null) 17.5 else 16.5, map?.mapOrientation ?: liveMapOrientation)
            } else if (settings.followUserLocation && !isFollowPaused && !isNavOverviewMode && !isUserInteractingWithMap) {
                if (map != null && !map.isAnimating) {
                    isShowingNeutralOrigin = false
                    map.controller.animateTo(
                        GeoPoint(followLat, followLon)
                    )
                }
            }
        }
    }

    var localHandledNavPreviewId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(navPreviewState, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        val previewSessionKey = when (navPreviewState) {
            is NavPreviewState.Ready -> navPreviewState.routes.joinToString("|") { it.id }
            is NavPreviewState.Error -> navPreviewState.cachedFallbackRoute?.id
            else -> null
        }
        if (previewSessionKey == null) {
            localHandledNavPreviewId = null
            return@LaunchedEffect
        }
        if (previewSessionKey == localHandledNavPreviewId) return@LaunchedEffect
        val allPreviewPts = when (navPreviewState) {
            is NavPreviewState.Ready -> navPreviewState.routes.flatMap { it.points }
            is NavPreviewState.Error -> navPreviewState.cachedFallbackRoute?.points.orEmpty()
            else -> emptyList()
        }
        val geoPts = allPreviewPts.map { GeoPoint(it.latitude, it.longitude) }
        if (geoPts.size >= 2) {
            val box = BoundingBox.fromGeoPointsSafe(geoPts)
            map.post {
                try {
                    map.zoomToBoundingBox(box, true, 140)
                } catch (_: Exception) {
                    map.controller.animateTo(geoPts.first(), 14.5, 700L)
                }
            }
            hasCenteredOnFirstFix = true
            isShowingNeutralOrigin = false
            localHandledNavPreviewId = previewSessionKey
        }
    }

    // Zoom to inspected saved trail ONLY when newly requested (not when switching back to Map tab)
    val inspectedTrailKey = inspectedTrailId ?: inspectedTrailName
    LaunchedEffect(inspectedTrailKey, trailPoints.size, trailWaypoints.size, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        val key = inspectedTrailKey ?: return@LaunchedEffect
        if (key == localHandledTrailKey) return@LaunchedEffect
        val geoPts = trailPoints.map { GeoPoint(it.latitude, it.longitude) } +
            trailWaypoints.map { GeoPoint(it.latitude, it.longitude) }
        if (geoPts.isEmpty()) return@LaunchedEffect
        selectedMarkerInfo = null
        droppedInspectPin = null
        if (geoPts.size >= 2) {
            val box = BoundingBox.fromGeoPointsSafe(geoPts)
            map.post {
                try {
                    map.zoomToBoundingBox(box, true, 120)
                } catch (_: Exception) {
                    map.controller.animateTo(geoPts.first(), 16.5, 700L)
                }
            }
        } else {
            map.controller.animateTo(geoPts.first(), 17.0, 700L)
        }
        hasCenteredOnFirstFix = true
        localHandledTrailKey = key
        onMarkInspectedTrailHandled?.invoke(key)
    }

    // Zoom to newly imported GPX/KML/GeoJSON reference route once
    LaunchedEffect(importedRoute?.id, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        val route = importedRoute ?: return@LaunchedEffect
        if (route.id == localHandledImportedRouteKey) return@LaunchedEffect
        val geoPts = route.points.map { GeoPoint(it.latitude, it.longitude) } +
            route.waypoints.map { GeoPoint(it.latitude, it.longitude) }
        if (geoPts.isEmpty()) return@LaunchedEffect
        if (geoPts.size >= 2) {
            val box = BoundingBox.fromGeoPointsSafe(geoPts)
            map.post {
                try {
                    map.zoomToBoundingBox(box, true, 120)
                } catch (_: Exception) {
                    map.controller.animateTo(geoPts.first(), 15.5, 700L)
                }
            }
        } else {
            map.controller.animateTo(geoPts.first(), 16.5, 700L)
        }
        hasCenteredOnFirstFix = true
        localHandledImportedRouteKey = route.id
        onMarkImportedRouteHandled?.invoke(route.id)
    }

    // Pan & zoom smoothly onto a requested remote device, group member, P2P peer, or region bounds (only once per request)
    LaunchedEffect(focusedTarget?.requestedAtMs, mapViewRef) {
        val target = focusedTarget ?: return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        if (target.requestedAtMs <= maxOf(lastHandledFocusMs, localHandledFocusMs)) return@LaunchedEffect
        val n = target.bboxNorth
        val s = target.bboxSouth
        val e = target.bboxEast
        val w = target.bboxWest
        val lonSpan = if (e != null && w != null) {
            if (w <= e) e - w else (180.0 - w) + (e + 180.0)
        } else 0.0
        if (n != null && s != null && e != null && w != null && (n - s) > 0.002 && lonSpan > 0.002 && w <= e) {
            selectedMarkerInfo = null
            droppedInspectPin = null
            val box = BoundingBox(
                n.coerceIn(-85.0, 85.0),
                e.coerceIn(-180.0, 180.0),
                s.coerceIn(-85.0, 85.0),
                w.coerceIn(-180.0, 180.0)
            )
            map.post {
                try {
                    map.zoomToBoundingBox(box, true, 120)
                } catch (_: Exception) {
                    map.controller.animateTo(
                        GeoPoint(target.latitude, target.longitude),
                        target.targetZoom ?: 13.5,
                        750L
                    )
                }
            }
            hasCenteredOnFirstFix = true
            isShowingNeutralOrigin = false
            localHandledFocusMs = target.requestedAtMs
            onMarkFocusHandled?.invoke(target.requestedAtMs)
        } else if (target.latitude != 0.0 || target.longitude != 0.0) {
            selectedMarkerInfo = null
            droppedInspectPin = null
            val fallbackZoom = target.targetZoom ?: if (n != null && s != null && e != null && w != null && w > e) 12.5 else 17.5
            map.controller.animateTo(
                GeoPoint(target.latitude, target.longitude),
                fallbackZoom,
                750L
            )
            hasCenteredOnFirstFix = true
            isShowingNeutralOrigin = false
            localHandledFocusMs = target.requestedAtMs
            onMarkFocusHandled?.invoke(target.requestedAtMs)
        }
    }

    // Pan & zoom onto a searched place, safe zone, offline POI, or coordinate (only once per selection)
    LaunchedEffect(selectedSearchPin?.requestedAtMs, mapViewRef) {
        val place = selectedSearchPin ?: return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        if (place.requestedAtMs <= maxOf(lastHandledSearchMs, localHandledSearchMs)) return@LaunchedEffect
        selectedMarkerInfo = null
        droppedInspectPin = null
        hasCenteredOnFirstFix = true
        isShowingNeutralOrigin = false
        val n = place.bboxNorth
        val s = place.bboxSouth
        val e = place.bboxEast
        val w = place.bboxWest
        val lonSpan = if (e != null && w != null) {
            if (w <= e) e - w else (180.0 - w) + (e + 180.0)
        } else 0.0
        if (n != null && s != null && e != null && w != null && (n - s) > 0.002 && lonSpan > 0.002 && w <= e) {
            val box = BoundingBox(n.coerceIn(-85.0, 85.0), e.coerceIn(-180.0, 180.0), s.coerceIn(-85.0, 85.0), w.coerceIn(-180.0, 180.0))
            map.post {
                try {
                    map.zoomToBoundingBox(box, true, 120)
                } catch (_: Exception) {
                    map.controller.animateTo(GeoPoint(place.latitude, place.longitude), 15.5, 750L)
                }
            }
        } else {
            val targetZoom = when {
                n != null && s != null && e != null && w != null && w > e -> 13.0
                place.source == com.geotrace.model.SearchResultSource.COORDINATE ||
                    place.source == com.geotrace.model.SearchResultSource.SAVED_PLACE ||
                    place.source == com.geotrace.model.SearchResultSource.WAYPOINT ||
                    place.source == com.geotrace.model.SearchResultSource.OFFLINE_POI -> 17.2
                else -> 16.5
            }
            map.controller.animateTo(GeoPoint(place.latitude, place.longitude), targetZoom, 750L)
        }
        localHandledSearchMs = place.requestedAtMs
        onMarkSearchHandled?.invoke(place.requestedAtMs)
    }

    // Persistent differential FolderOverlay layers (zero full overlay clear/recreate churn per tick)
    val offlineRegionsLayer = remember { FolderOverlay() }
    val geofenceLayer = remember { FolderOverlay() }
    val cellTowerLayer = remember { FolderOverlay() }
    val referenceRouteLayer = remember { FolderOverlay() }
    val navRouteLayer = remember { FolderOverlay() }
    val trailLayer = remember { FolderOverlay() }
    val peersLayer = remember { FolderOverlay() }
    val staticPinsLayer = remember { FolderOverlay() }
    val userTargetLayer = remember { FolderOverlay() }

    val lastOfflineRegionsSigHolder = remember { arrayOfNulls<Pair<List<com.geotrace.model.OfflineMapRegion>, Boolean>>(1) }
    val lastGeofenceSigHolder = remember { arrayOfNulls<GeofenceLayerSig>(1) }
    val lastCellSigHolder = remember { arrayOfNulls<CellLayerSig>(1) }
    val lastReferenceRouteSigHolder = remember { arrayOfNulls<ReferenceRouteLayerSig>(1) }
    val lastNavRouteSigHolder = remember { arrayOfNulls<NavRouteLayerSig>(1) }
    val lastTrailSigHolder = remember { arrayOfNulls<TrailLayerSig>(1) }
    val lastPeersSigHolder = remember { arrayOfNulls<PeersLayerSig>(1) }
    val lastStaticPinsSigHolder = remember { arrayOfNulls<StaticPinsLayerSig>(1) }
    val lastUserTargetSigHolder = remember { arrayOfNulls<UserTargetLayerSig>(1) }
    val hasAttachedBaseLayersHolder = remember { booleanArrayOf(false) }

    val persistentEventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                currentOnMapSingleTap?.invoke()
                selectedMarkerInfo = null
                droppedInspectPin = null
                if (currentIsMapPickMode && p != null) {
                    currentOnMapCoordinatePicked?.invoke(p.latitude, p.longitude)
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (currentIsMapPickMode || p == null) return false
                droppedInspectPin = p
                val distStr = currentLocationState?.let { cur ->
                    val d = GeoPoint(cur.latitude, cur.longitude).distanceToAsDouble(p)
                    if (d < 1000.0) " • ${d.toInt()}m away" else String.format(Locale.US, " • %.1fkm away", d / 1000.0)
                } ?: ""
                val coordStr = com.geotrace.ui.screens.formatCoordinates(p.latitude, p.longitude, currentCoordinateFormat)
                selectedMarkerInfo = MarkerInspectData(
                    title = "Dropped Pin",
                    detail = "$coordStr$distStr",
                    badge = "PIN",
                    latitude = p.latitude,
                    longitude = p.longitude
                )
                return true
            }
        })
    }

    val persistentRotationOverlay = remember {
        GeoTraceRotationOverlay(
            onRotationGestureStarted = {
                orientationAnimatorHolder[0]?.cancel()
            },
            onOrientationChanged = { updatedOrientation, cumulativeTwist ->
                liveMapOrientation = updatedOrientation
                if (currentAutoRotateHeadingUp && cumulativeTwist >= 3.0f) {
                    val expectedOrientation = normalizeMapOrientationSigned(-currentResolvedBearing)
                    if (abs(shortestAngleDeltaDegrees(updatedOrientation, expectedOrientation)) >= 3.0f) {
                        isHeadingUpPausedByUser = true
                    }
                }
                mapViewRef?.let { m ->
                    val c = m.mapCenter
                    currentOnSaveMapCamera?.invoke(GeoPoint(c.latitude, c.longitude), m.zoomLevelDouble, updatedOrientation)
                }
            }
        )
    }

    // Manage MapView lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            orientationAnimatorHolder[0]?.cancel()
            orientationAnimatorHolder[0] = null
            mapViewRef?.let { m ->
                if (!isShowingNeutralOrigin || hasUserMovedCamera) {
                    val c = m.mapCenter
                    currentOnSaveMapCamera?.invoke(GeoPoint(c.latitude, c.longitude), m.zoomLevelDouble, m.mapOrientation)
                }
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            hybridLabelsProvider.setTileRequestCompleteHandler(null)
            hybridLabelsProvider.detach()
            avatarDrawableCache.clear()
            waypointDrawableCache.clear()
            savedPlaceDrawableCache.clear()
            mapViewRef?.onDetach()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_canvas_container")
    ) {
        // Genuine OpenStreetMap / Esri Satellite Viewport (Persistent Spatial Foundation)
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(MapTileSources.baseTileSourceFor(settings.mapTileType))
                    setUseDataConnection(isNetworkOnline)
                    hybridLabelsProvider.setUseDataConnection(isNetworkOnline)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    minZoomLevel = 3.0
                    isVerticalMapRepetitionEnabled = false
                    setScrollableAreaLimitLatitude(
                        MapView.getTileSystem().maxLatitude,
                        -MapView.getTileSystem().maxLatitude,
                        0
                    )
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.0)
                    setMapOrientation(initialMapOrientation, false)
                    liveMapOrientation = mapOrientation

                    val hasUnhandledFocus = focusedTarget != null &&
                        (focusedTarget.latitude != 0.0 || focusedTarget.longitude != 0.0) &&
                        focusedTarget.requestedAtMs > maxOf(lastHandledFocusMs, localHandledFocusMs)
                    val hasUnhandledSearch = selectedSearchPin != null &&
                        selectedSearchPin.requestedAtMs > maxOf(lastHandledSearchMs, localHandledSearchMs)

                    if (hasUnhandledSearch && selectedSearchPin != null) {
                        controller.setCenter(GeoPoint(selectedSearchPin.latitude, selectedSearchPin.longitude))
                        controller.setZoom(16.5)
                    } else if (hasUnhandledFocus && focusedTarget != null) {
                        controller.setCenter(GeoPoint(focusedTarget.latitude, focusedTarget.longitude))
                        controller.setZoom(focusedTarget.targetZoom ?: 17.5)
                    } else if (initialMapCenter != null) {
                        controller.setCenter(initialMapCenter)
                        controller.setZoom(initialMapZoom ?: 16.5)
                    } else if (currentLocation != null) {
                        controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                        controller.setZoom(16.5)
                    } else if (fallbackCachedCenter != null) {
                        controller.setCenter(fallbackCachedCenter)
                        controller.setZoom(15.5)
                    } else {
                        // Global neutral view, awaiting genuine GNSS fix
                        controller.setCenter(GeoPoint(0.0, 0.0))
                        controller.setZoom(3.0)
                    }

                    // Detect manual pan vs stationary two-finger pinch/twist with clean pointer handoff
                    val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
                    var downX = 0f
                    var downY = 0f
                    var centroidDownX = 0f
                    var centroidDownY = 0f
                    var hasTriggeredGesturePause = false
                    @SuppressLint("ClickableViewAccessibility")
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                hasTriggeredGesturePause = false
                                isUserInteractingWithMap = true
                                hasUserMovedCamera = true
                                isShowingNeutralOrigin = false
                                orientationAnimatorHolder[0]?.cancel()
                            }
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                isUserInteractingWithMap = true
                                hasUserMovedCamera = true
                                isShowingNeutralOrigin = false
                                orientationAnimatorHolder[0]?.cancel()
                                if (event.pointerCount >= 2) {
                                    centroidDownX = (event.getX(0) + event.getX(1)) * 0.5f
                                    centroidDownY = (event.getY(0) + event.getY(1)) * 0.5f
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!hasTriggeredGesturePause) {
                                    if (event.pointerCount == 1) {
                                        val moved = hypot(event.x - downX, event.y - downY)
                                        if (moved > touchSlop) {
                                            hasTriggeredGesturePause = true
                                            controller.stopAnimation(false)
                                            currentOnFollowPausedByUser?.invoke()
                                        }
                                    } else if (event.pointerCount >= 2) {
                                        val cx = (event.getX(0) + event.getX(1)) * 0.5f
                                        val cy = (event.getY(0) + event.getY(1)) * 0.5f
                                        val centroidShift = hypot(cx - centroidDownX, cy - centroidDownY)
                                        if (centroidShift > touchSlop * 2.5f) {
                                            hasTriggeredGesturePause = true
                                            controller.stopAnimation(false)
                                            currentOnFollowPausedByUser?.invoke()
                                        }
                                    }
                                }
                            }
                            MotionEvent.ACTION_POINTER_UP -> {
                                if (event.pointerCount == 2) {
                                    val liftedIdx = event.actionIndex
                                    val remainingIdx = if (liftedIdx == 0) 1 else 0
                                    downX = event.getX(remainingIdx)
                                    downY = event.getY(remainingIdx)
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isUserInteractingWithMap = false
                                hasTriggeredGesturePause = false
                            }
                        }
                        false
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            onMapBoundsChanged?.invoke(boundingBox)
                            val c = mapCenter
                            liveCenterLat = c.latitude
                            liveZoomLevel = zoomLevelDouble
                            if (!isShowingNeutralOrigin || hasUserMovedCamera) {
                                currentOnSaveMapCamera?.invoke(GeoPoint(c.latitude, c.longitude), zoomLevelDouble, mapOrientation)
                            }
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            onMapBoundsChanged?.invoke(boundingBox)
                            val c = mapCenter
                            liveCenterLat = c.latitude
                            liveZoomLevel = zoomLevelDouble
                            if (!isShowingNeutralOrigin || hasUserMovedCamera) {
                                currentOnSaveMapCamera?.invoke(GeoPoint(c.latitude, c.longitude), zoomLevelDouble, mapOrientation)
                            }
                            return false
                        }
                    })
                    post {
                        liveZoomLevel = zoomLevelDouble
                        liveCenterLat = mapCenter.latitude
                        onMapBoundsChanged?.invoke(boundingBox)
                    }
                    hybridLabelsProvider.setTileRequestCompleteHandler(tileRequestCompleteHandler)
                    mapViewRef = this
                }
            },
            update = { map ->
                if (!hasAttachedBaseLayersHolder[0]) {
                    map.overlays.clear()
                    map.overlays.add(offlineRegionsLayer)
                    map.overlays.add(geofenceLayer)
                    map.overlays.add(cellTowerLayer)
                    map.overlays.add(referenceRouteLayer)
                    map.overlays.add(navRouteLayer)
                    map.overlays.add(trailLayer)
                    map.overlays.add(peersLayer)
                    map.overlays.add(staticPinsLayer)
                    map.overlays.add(userTargetLayer)
                    map.overlays.add(persistentEventsOverlay)
                    map.overlays.add(persistentRotationOverlay)
                    hasAttachedBaseLayersHolder[0] = true
                }

                // 0. Hybrid street & place labels overlay toggled cleanly without resetting other layers
                val shouldShowHybrid = settings.mapTileType == com.geotrace.model.MapTileType.HYBRID
                val hasHybrid = map.overlays.firstOrNull() === hybridLabelsOverlay
                if (shouldShowHybrid && !hasHybrid) {
                    map.overlays.add(0, hybridLabelsOverlay)
                } else if (!shouldShowHybrid && hasHybrid) {
                    map.overlays.removeAt(0)
                }

                var anyLayerChanged = false

                // 0b. Differential Imported GPX/KML/GeoJSON Reference Route Layer
                val refSig = ReferenceRouteLayerSig(
                    routeId = importedRoute?.id,
                    pointsCount = importedRoute?.points?.size ?: 0,
                    waypointsCount = importedRoute?.waypoints?.size ?: 0,
                    coordinateFormat = settings.coordinateFormat
                )
                if (lastReferenceRouteSigHolder[0] != refSig) {
                    lastReferenceRouteSigHolder[0] = refSig
                    anyLayerChanged = true
                    referenceRouteLayer.items.clear()

                    val route = importedRoute
                    if (route != null) {
                        val routeTeal = android.graphics.Color.parseColor("#0D9488")
                        if (route.points.size >= 2) {
                            val currentSeg = mutableListOf<GeoPoint>()
                            for (pt in route.points) {
                                val geoPt = GeoPoint(pt.latitude, pt.longitude)
                                if (currentSeg.isNotEmpty() && currentSeg.last().distanceToAsDouble(geoPt) > 5000.0) {
                                    if (currentSeg.size >= 2) {
                                        referenceRouteLayer.add(Polyline(map).apply {
                                            outlinePaint.color = routeTeal
                                            outlinePaint.strokeWidth = 8f
                                            outlinePaint.strokeCap = Paint.Cap.ROUND
                                            outlinePaint.strokeJoin = Paint.Join.ROUND
                                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                            setPoints(currentSeg.toList())
                                        })
                                    }
                                    currentSeg.clear()
                                }
                                currentSeg.add(geoPt)
                            }
                            if (currentSeg.size >= 2) {
                                referenceRouteLayer.add(Polyline(map).apply {
                                    outlinePaint.color = routeTeal
                                    outlinePaint.strokeWidth = 8f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                    setPoints(currentSeg)
                                })
                            }
                            val first = route.points.first()
                            val last = route.points.last()
                            referenceRouteLayer.add(Marker(map).apply {
                                position = GeoPoint(first.latitude, first.longitude)
                                title = "${route.name} (Start)"
                                snippet = "${route.sourceFormat} • ${route.formattedDistance}"
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = routeStartDrawable
                                setOnMarkerClickListener { m, _ ->
                                    selectedMarkerInfo = MarkerInspectData(
                                        title = m.title ?: route.name,
                                        detail = m.snippet ?: "",
                                        badge = "ROUTE START",
                                        latitude = first.latitude,
                                        longitude = first.longitude,
                                        altitude = first.altitude
                                    )
                                    true
                                }
                            })
                            referenceRouteLayer.add(Marker(map).apply {
                                position = GeoPoint(last.latitude, last.longitude)
                                title = "${route.name} (Finish)"
                                snippet = "${route.sourceFormat} • ${route.formattedDistance}"
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = routeEndDrawable
                                setOnMarkerClickListener { m, _ ->
                                    selectedMarkerInfo = MarkerInspectData(
                                        title = m.title ?: route.name,
                                        detail = m.snippet ?: "",
                                        badge = "ROUTE END",
                                        latitude = last.latitude,
                                        longitude = last.longitude,
                                        altitude = last.altitude
                                    )
                                    true
                                }
                            })
                        }
                        route.waypoints.forEachIndexed { idx, wp ->
                            if (isValidCoordinate(wp.latitude, wp.longitude)) {
                                val badgeIcon = waypointDrawableCache.getOrPut(1000 + idx + 1) {
                                    createPinBadgeDrawable(context, "R${idx + 1}", routeTeal)
                                }
                                referenceRouteLayer.add(Marker(map).apply {
                                    position = GeoPoint(wp.latitude, wp.longitude)
                                    title = wp.name
                                    val coordStr = com.geotrace.ui.screens.formatCoordinates(wp.latitude, wp.longitude, settings.coordinateFormat)
                                    snippet = if (wp.note.isNotBlank()) "${wp.note} • $coordStr" else coordStr
                                    infoWindow = null
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = badgeIcon
                                    setOnMarkerClickListener { m, _ ->
                                        selectedMarkerInfo = MarkerInspectData(
                                            title = m.title ?: wp.name,
                                            detail = m.snippet ?: "",
                                            badge = "ROUTE WPT",
                                            latitude = wp.latitude,
                                            longitude = wp.longitude,
                                            altitude = wp.altitude
                                        )
                                        true
                                    }
                                })
                            }
                        }
                    }
                }

                // 0c. Differential Turn-by-Turn Road Navigation Preview & Active Route Layer
                val previewKeyStr = when (navPreviewState) {
                    is NavPreviewState.Ready -> navPreviewState.routes.joinToString("|") { it.id }
                    is NavPreviewState.Error -> navPreviewState.cachedFallbackRoute?.id
                    else -> null
                }
                val previewSelectedIdx = (navPreviewState as? NavPreviewState.Ready)?.selectedIndex ?: 0
                val navSig = NavRouteLayerSig(
                    activeRouteId = activeNavigation?.route?.id,
                    nearestPointIndex = activeNavigation?.nearestPointIndex ?: 0,
                    isOffRoute = activeNavigation?.isOffRoute == true,
                    currentStepIndex = activeNavigation?.currentStepIndex ?: 0,
                    previewKey = previewKeyStr,
                    previewSelectedIndex = previewSelectedIdx
                )
                if (lastNavRouteSigHolder[0] != navSig) {
                    lastNavRouteSigHolder[0] = navSig
                    anyLayerChanged = true
                    navRouteLayer.items.clear()

                    val navPrimaryBlue = android.graphics.Color.parseColor("#1D4ED8")
                    val navCasingWhite = android.graphics.Color.WHITE
                    val navTraveledGray = android.graphics.Color.argb(175, 148, 163, 184)
                    val navOffRouteAmber = android.graphics.Color.parseColor("#D97706")
                    val navAltSlate = android.graphics.Color.argb(180, 100, 116, 139)

                    if (activeNavigation != null) {
                        val route = activeNavigation.route
                        val pts = route.points
                        if (pts.size >= 2) {
                            val splitIdx = activeNavigation.nearestPointIndex.coerceIn(0, pts.lastIndex)
                            if (splitIdx >= 1) {
                                val passedPts = pts.subList(0, (splitIdx + 1).coerceAtMost(pts.size))
                                    .map { GeoPoint(it.latitude, it.longitude) }
                                if (passedPts.size >= 2) {
                                    navRouteLayer.add(Polyline(map).apply {
                                        infoWindow = null
                                        outlinePaint.color = navTraveledGray
                                        outlinePaint.strokeWidth = 9f
                                        outlinePaint.strokeCap = Paint.Cap.ROUND
                                        outlinePaint.strokeJoin = Paint.Join.ROUND
                                        setPoints(passedPts)
                                    })
                                }
                            }

                            val remPts = pts.subList(splitIdx, pts.size)
                                .map { GeoPoint(it.latitude, it.longitude) }
                            if (remPts.size >= 2) {
                                navRouteLayer.add(Polyline(map).apply {
                                    infoWindow = null
                                    outlinePaint.color = navCasingWhite
                                    outlinePaint.strokeWidth = 17f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    setPoints(remPts)
                                })
                                navRouteLayer.add(Polyline(map).apply {
                                    infoWindow = null
                                    outlinePaint.color = if (activeNavigation.isOffRoute) navOffRouteAmber else navPrimaryBlue
                                    outlinePaint.strokeWidth = 11f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    setPoints(remPts)
                                })
                            }

                            // Upcoming maneuver turn indicators (capped to next 6 turns for map clarity)
                            route.steps.drop((activeNavigation.currentStepIndex + 1).coerceAtMost(route.steps.size))
                                .filter { it.maneuverType != NavManeuverType.START && it.maneuverType != NavManeuverType.ARRIVE }
                                .take(6)
                                .forEach { step ->
                                    val turnPt = pts.getOrNull(step.beginPointIndex.coerceIn(0, pts.lastIndex))
                                    if (turnPt != null) {
                                        navRouteLayer.add(Marker(map).apply {
                                            position = GeoPoint(turnPt.latitude, turnPt.longitude)
                                            infoWindow = null
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                            icon = navTurnDotDrawable
                                            setOnMarkerClickListener { _, _ -> true }
                                        })
                                    }
                                }

                            val endPt = pts.last()
                            navRouteLayer.add(Marker(map).apply {
                                position = GeoPoint(endPt.latitude, endPt.longitude)
                                title = route.destinationName
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = navDestPinDrawable
                                setOnMarkerClickListener { _, _ -> true }
                            })
                        }
                    } else if (navPreviewState is NavPreviewState.Ready) {
                        val routes = navPreviewState.routes
                        routes.forEachIndexed { idx, altRoute ->
                            if (idx != previewSelectedIdx && altRoute.points.size >= 2) {
                                val altPts = altRoute.points.map { GeoPoint(it.latitude, it.longitude) }
                                navRouteLayer.add(Polyline(map).apply {
                                    infoWindow = null
                                    outlinePaint.color = android.graphics.Color.argb(175, 255, 255, 255)
                                    outlinePaint.strokeWidth = 14f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    setPoints(altPts)
                                    setOnClickListener { _, _, _ ->
                                        currentOnSelectPreviewRoute?.invoke(idx)
                                        true
                                    }
                                })
                                navRouteLayer.add(Polyline(map).apply {
                                    infoWindow = null
                                    outlinePaint.color = navAltSlate
                                    outlinePaint.strokeWidth = 9.5f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    setPoints(altPts)
                                    setOnClickListener { _, _, _ ->
                                        currentOnSelectPreviewRoute?.invoke(idx)
                                        true
                                    }
                                })
                            }
                        }
                        val selected = navPreviewState.selectedRoute
                        if (selected != null && selected.points.size >= 2) {
                            val selPts = selected.points.map { GeoPoint(it.latitude, it.longitude) }
                            navRouteLayer.add(Polyline(map).apply {
                                infoWindow = null
                                outlinePaint.color = navCasingWhite
                                outlinePaint.strokeWidth = 17f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                setPoints(selPts)
                            })
                            navRouteLayer.add(Polyline(map).apply {
                                infoWindow = null
                                outlinePaint.color = navPrimaryBlue
                                outlinePaint.strokeWidth = 11f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                setPoints(selPts)
                            })
                            navRouteLayer.add(Marker(map).apply {
                                position = selPts.first()
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = routeStartDrawable
                                setOnMarkerClickListener { _, _ -> true }
                            })
                            navRouteLayer.add(Marker(map).apply {
                                position = selPts.last()
                                title = selected.destinationName
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = navDestPinDrawable
                                setOnMarkerClickListener { _, _ -> true }
                            })
                        }
                    } else if (navPreviewState is NavPreviewState.Error && navPreviewState.cachedFallbackRoute != null) {
                        val cachedRoute = navPreviewState.cachedFallbackRoute
                        if (cachedRoute.points.size >= 2) {
                            val pts = cachedRoute.points.map { GeoPoint(it.latitude, it.longitude) }
                            navRouteLayer.add(Polyline(map).apply {
                                infoWindow = null
                                outlinePaint.color = android.graphics.Color.parseColor("#0D9488")
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(22f, 12f), 0f)
                                setPoints(pts)
                            })
                            navRouteLayer.add(Marker(map).apply {
                                position = pts.last()
                                title = cachedRoute.destinationName
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = navDestPinDrawable
                                setOnMarkerClickListener { _, _ -> true }
                            })
                        }
                    }
                }

                // 1. Differential Trail & Waypoint Layer
                val trailSig = TrailLayerSig(
                    pointsSize = trailPoints.size,
                    firstLat = trailPoints.firstOrNull()?.latitude,
                    firstLon = trailPoints.firstOrNull()?.longitude,
                    lastLat = trailPoints.lastOrNull()?.latitude,
                    lastLon = trailPoints.lastOrNull()?.longitude,
                    inspectedTrailKey = inspectedTrailKey,
                    waypoints = trailWaypoints,
                    coordinateFormat = settings.coordinateFormat,
                    trailColorArgb = trailColor.toArgb()
                )
                if (lastTrailSigHolder[0] != trailSig) {
                    lastTrailSigHolder[0] = trailSig
                    anyLayerChanged = true
                    trailLayer.items.clear()

                    if (trailPoints.size >= 2) {
                        val currentSegment = mutableListOf<GeoPoint>()
                        for (i in trailPoints.indices) {
                            val pt = trailPoints[i]
                            val geoPt = GeoPoint(pt.latitude, pt.longitude)
                            if (currentSegment.isEmpty()) {
                                currentSegment.add(geoPt)
                            } else {
                                val lastGeo = currentSegment.last()
                                val distM = lastGeo.distanceToAsDouble(geoPt)
                                if (distM > 5000.0) {
                                    if (currentSegment.size >= 2) {
                                        val polyline = Polyline(map).apply {
                                            outlinePaint.color = trailColor.toArgb()
                                            outlinePaint.strokeWidth = 9f
                                            outlinePaint.strokeCap = Paint.Cap.ROUND
                                            outlinePaint.strokeJoin = Paint.Join.ROUND
                                            setPoints(currentSegment.toList())
                                        }
                                        trailLayer.add(polyline)
                                    }
                                    currentSegment.clear()
                                }
                                currentSegment.add(geoPt)
                            }
                        }
                        if (currentSegment.size >= 2) {
                            val polyline = Polyline(map).apply {
                                outlinePaint.color = trailColor.toArgb()
                                outlinePaint.strokeWidth = 9f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                setPoints(currentSegment)
                            }
                            trailLayer.add(polyline)
                        }

                        val startPt = trailPoints.first()
                        val endPt = trailPoints.last()
                        trailLayer.add(Marker(map).apply {
                            position = GeoPoint(startPt.latitude, startPt.longitude)
                            title = "Trail Start"
                            snippet = "First logged coordinate"
                            infoWindow = null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = trailStartDrawable
                            setOnMarkerClickListener { m, _ ->
                                selectedMarkerInfo = MarkerInspectData(
                                    title = m.title ?: "Trail Start",
                                    detail = m.snippet ?: "",
                                    badge = "START",
                                    latitude = startPt.latitude,
                                    longitude = startPt.longitude,
                                    altitude = startPt.altitude
                                )
                                true
                            }
                        })

                        trailLayer.add(Marker(map).apply {
                            position = GeoPoint(endPt.latitude, endPt.longitude)
                            title = "Trail End"
                            snippet = "Latest logged waypoint"
                            infoWindow = null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = trailEndDrawable
                            setOnMarkerClickListener { m, _ ->
                                selectedMarkerInfo = MarkerInspectData(
                                    title = m.title ?: "Trail End",
                                    detail = m.snippet ?: "",
                                    badge = "END",
                                    latitude = endPt.latitude,
                                    longitude = endPt.longitude,
                                    altitude = endPt.altitude
                                )
                                true
                            }
                        })
                    }

                    val wpOrange = android.graphics.Color.parseColor("#EA580C")
                    trailWaypoints.forEachIndexed { index, wp ->
                        if (isValidCoordinate(wp.latitude, wp.longitude)) {
                            val iconBadge = waypointDrawableCache.getOrPut(index + 1) {
                                createPinBadgeDrawable(context, "W${index + 1}", wpOrange)
                            }
                            trailLayer.add(Marker(map).apply {
                                position = GeoPoint(wp.latitude, wp.longitude)
                                title = wp.name
                                val notePrefix = if (wp.note.isNotBlank()) "${wp.note} • " else ""
                                val coordStr = com.geotrace.ui.screens.formatCoordinates(wp.latitude, wp.longitude, settings.coordinateFormat)
                                snippet = "${notePrefix}Acc ±${wp.accuracy.toInt()}m • $coordStr"
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = iconBadge
                                setOnMarkerClickListener { m, _ ->
                                    selectedMarkerInfo = MarkerInspectData(
                                        title = m.title ?: wp.name,
                                        detail = m.snippet ?: "",
                                        badge = "WAYPOINT",
                                        latitude = wp.latitude,
                                        longitude = wp.longitude,
                                        altitude = wp.altitude,
                                        photoUri = wp.photoUri
                                    )
                                    true
                                }
                            })
                        }
                    }
                }

                // 1b. Differential Offline Region Boundary Outlines
                val offlineRegionsSig = offlineMapRegions to settings.showOfflineRegionsOverlay
                if (lastOfflineRegionsSigHolder[0] != offlineRegionsSig) {
                    lastOfflineRegionsSigHolder[0] = offlineRegionsSig
                    anyLayerChanged = true
                    offlineRegionsLayer.items.clear()
                    if (settings.showOfflineRegionsOverlay && offlineMapRegions.isNotEmpty()) {
                        val outlineTeal = android.graphics.Color.parseColor("#0D9488")
                        val fillTeal = android.graphics.Color.argb(18, 13, 148, 136)
                        for (region in offlineMapRegions) {
                            val rawBoxes = if (region.isCorridor && region.corridorBoxes.isNotEmpty()) {
                                region.corridorBoxes.map { cb ->
                                    com.geotrace.service.OfflineMapManager.CorridorTileBox(
                                        north = cb.north.coerceIn(-85.0511, 85.0511),
                                        south = cb.south.coerceIn(-85.0511, 85.0511),
                                        east = com.geotrace.service.OfflineMapManager.normalizeLongitude(cb.east),
                                        west = com.geotrace.service.OfflineMapManager.normalizeLongitude(cb.west)
                                    )
                                }
                            } else {
                                listOf(
                                    com.geotrace.service.OfflineMapManager.CorridorTileBox(
                                        north = region.northLat.coerceIn(-85.0511, 85.0511),
                                        south = region.southLat.coerceIn(-85.0511, 85.0511),
                                        east = com.geotrace.service.OfflineMapManager.normalizeLongitude(region.eastLng),
                                        west = com.geotrace.service.OfflineMapManager.normalizeLongitude(region.westLng)
                                    )
                                )
                            }
                            val subBoxes = rawBoxes.flatMap { com.geotrace.service.OfflineMapManager.splitBoxAtAntimeridian(it) }
                            for (sb in subBoxes) {
                                if (sb.north > sb.south && sb.east > sb.west) {
                                    offlineRegionsLayer.add(Polygon(map).apply {
                                        points = listOf(
                                            GeoPoint(sb.north, sb.west),
                                            GeoPoint(sb.north, sb.east),
                                            GeoPoint(sb.south, sb.east),
                                            GeoPoint(sb.south, sb.west)
                                        )
                                        infoWindow = null
                                        fillPaint.color = fillTeal
                                        outlinePaint.color = outlineTeal
                                        outlinePaint.strokeWidth = 3f
                                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                                    })
                                }
                            }
                        }
                    }
                }

                // 2. Differential Geofence Circular Zones Layer
                val geofenceSig = GeofenceLayerSig(cachedGeofences, settings.geofenceHysteresisMeters)
                if (lastGeofenceSigHolder[0] != geofenceSig) {
                    lastGeofenceSigHolder[0] = geofenceSig
                    anyLayerChanged = true
                    geofenceLayer.items.clear()
                    val deltaHyst = settings.geofenceHysteresisMeters.toDouble().coerceAtLeast(0.0)
                    for (cached in cachedGeofences) {
                        val zone = cached.zone
                        cached.outerExitPoints?.let { exitPts ->
                            geofenceLayer.add(Polygon(map).apply {
                                points = exitPts
                                infoWindow = null
                                fillPaint.color = android.graphics.Color.TRANSPARENT
                                outlinePaint.color = cached.hystColor
                                outlinePaint.strokeWidth = 1.5f
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
                            })
                        }
                        cached.innerEntryPoints?.let { entryPts ->
                            geofenceLayer.add(Polygon(map).apply {
                                points = entryPts
                                infoWindow = null
                                fillPaint.color = android.graphics.Color.TRANSPARENT
                                outlinePaint.color = cached.hystColor
                                outlinePaint.strokeWidth = 1.5f
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
                            })
                        }
                        geofenceLayer.add(Polygon(map).apply {
                            points = cached.mainPoints
                            infoWindow = null
                            fillPaint.color = cached.fillColor
                            outlinePaint.color = cached.outlineColor
                            outlinePaint.strokeWidth = 3f
                            title = "${zone.name} (${zone.radiusMeters.toInt()}m)"
                            snippet = if (deltaHyst > 0.0) {
                                "${zone.category.label} • ${if (zone.isInside) "Inside" else "Outside"} • Entry ≤${cached.entryR.toInt()}m, Exit >${cached.exitR.toInt()}m"
                            } else {
                                "${zone.category.label} • ${if (zone.isInside) "Inside Safe Zone" else "Outside"}"
                            }
                        })
                    }
                }

                // 3. Differential Cell Towers Layer
                val towersToDraw = if (settings.showCellTowerPin) {
                    if (allCellTowers.isNotEmpty()) allCellTowers else listOfNotNull(cellTowerLocation)
                } else emptyList()
                val servingTower = if (settings.showCellTowerPin) {
                    cellTowerLocation ?: towersToDraw.firstOrNull { it.isServing }
                } else null
                val cellSig = CellLayerSig(
                    showCellTowerPin = settings.showCellTowerPin,
                    servingTower = servingTower,
                    allTowers = towersToDraw,
                    curLat = if (servingTower != null) currentLocation?.latitude else null,
                    curLon = if (servingTower != null) currentLocation?.longitude else null
                )
                if (lastCellSigHolder[0] != cellSig) {
                    lastCellSigHolder[0] = cellSig
                    anyLayerChanged = true
                    cellTowerLayer.items.clear()
                    if (settings.showCellTowerPin) {
                        for (tower in towersToDraw) {
                            if (tower.cellId != servingTower?.cellId) {
                                val neighborGeoPoint = GeoPoint(tower.latitude, tower.longitude)
                                val neighborCirclePoints = computeCircleGeoPoints(neighborGeoPoint, tower.rangeMeters)
                                cellTowerLayer.add(Polygon(map).apply {
                                    points = neighborCirclePoints
                                    infoWindow = null
                                    fillPaint.color = android.graphics.Color.argb(12, 100, 116, 139)
                                    outlinePaint.color = android.graphics.Color.argb(120, 148, 163, 184)
                                    outlinePaint.strokeWidth = 1.5f
                                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
                                })
                                cellTowerLayer.add(Marker(map).apply {
                                    position = neighborGeoPoint
                                    title = "Neighbor Mast: ${tower.operatorName}"
                                    snippet = "CID: ${tower.cellId} • Standby Sector"
                                    infoWindow = null
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = neighborTowerDrawable
                                    setOnMarkerClickListener { m, _ ->
                                        selectedMarkerInfo = MarkerInspectData(
                                            title = m.title ?: "Neighbor Mast",
                                            detail = m.snippet ?: "",
                                            badge = "RF",
                                            latitude = tower.latitude,
                                            longitude = tower.longitude
                                        )
                                        true
                                    }
                                })
                            }
                        }

                        if (servingTower != null) {
                            val towerGeoPoint = GeoPoint(servingTower.latitude, servingTower.longitude)
                            val dbm = servingTower.signalDbm
                            val signalColor = when {
                                dbm == null -> android.graphics.Color.rgb(217, 119, 6)
                                dbm >= -85 -> android.graphics.Color.rgb(5, 150, 105)
                                dbm >= -102 -> android.graphics.Color.rgb(217, 119, 6)
                                else -> android.graphics.Color.rgb(220, 38, 38)
                            }
                            val signalLabel = when {
                                dbm == null -> "Signal N/A"
                                dbm >= -85 -> "$dbm dBm (Strong)"
                                dbm >= -102 -> "$dbm dBm (Moderate)"
                                else -> "$dbm dBm (Weak)"
                            }

                            val towerCirclePoints = computeCircleGeoPoints(towerGeoPoint, servingTower.rangeMeters)
                            cellTowerLayer.add(Polygon(map).apply {
                                points = towerCirclePoints
                                infoWindow = null
                                fillPaint.color = android.graphics.Color.argb(38, android.graphics.Color.red(signalColor), android.graphics.Color.green(signalColor), android.graphics.Color.blue(signalColor))
                                outlinePaint.color = android.graphics.Color.argb(220, android.graphics.Color.red(signalColor), android.graphics.Color.green(signalColor), android.graphics.Color.blue(signalColor))
                                outlinePaint.strokeWidth = 2.8f
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                            })

                            if (currentLocation != null) {
                                cellTowerLayer.add(Polyline(map).apply {
                                    infoWindow = null
                                    outlinePaint.color = android.graphics.Color.argb(235, android.graphics.Color.red(signalColor), android.graphics.Color.green(signalColor), android.graphics.Color.blue(signalColor))
                                    outlinePaint.strokeWidth = 4.0f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                                    setPoints(listOf(
                                        GeoPoint(currentLocation.latitude, currentLocation.longitude),
                                        towerGeoPoint
                                    ))
                                })
                            }

                            cellTowerLayer.add(Marker(map).apply {
                                position = towerGeoPoint
                                title = "Serving Mast: ${servingTower.operatorName}"
                                snippet = "CID: ${servingTower.cellId} • $signalLabel • ±${servingTower.rangeMeters.toInt()}m"
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = servingTowerDrawable
                                setOnMarkerClickListener { m, _ ->
                                    selectedMarkerInfo = MarkerInspectData(
                                        title = m.title ?: "Serving Mast",
                                        detail = m.snippet ?: "",
                                        badge = "TOWER",
                                        latitude = servingTower.latitude,
                                        longitude = servingTower.longitude
                                    )
                                    true
                                }
                            })
                        }
                    }
                }

                // 4. Differential Group, Personal Fleet & P2P Peers Layer
                val peersSig = PeersLayerSig(
                    members = groupMembers,
                    devices = personalDevices,
                    peers = nearbyPeers,
                    deviceId = currentDeviceId,
                    userUid = currentUserUid,
                    speedUnit = settings.speedUnit
                )
                if (lastPeersSigHolder[0] != peersSig) {
                    lastPeersSigHolder[0] = peersSig
                    anyLayerChanged = true
                    peersLayer.items.clear()

                    val groupDeviceIds = groupMembers.map { it.deviceId }.filter { it.isNotBlank() }.toSet()
                    for (member in groupMembers) {
                        val isSelf = (currentDeviceId.isNotBlank() && member.deviceId == currentDeviceId) ||
                            (currentUserUid != null && member.userId == currentUserUid && member.deviceId.isBlank())
                        if (isSelf) continue
                        if (!isValidCoordinate(member.latitude, member.longitude)) continue
                        val memberGeoPoint = GeoPoint(member.latitude, member.longitude)
                        val memberColor = try {
                            android.graphics.Color.parseColor(member.avatarColor)
                        } catch (_: Exception) {
                            android.graphics.Color.parseColor("#2563EB")
                        }

                        if (member.accuracy > 0f) {
                            val memberAccCircle = computeCircleGeoPoints(memberGeoPoint, member.accuracy.toDouble())
                            peersLayer.add(Polygon(map).apply {
                                points = memberAccCircle
                                infoWindow = null
                                fillPaint.color = android.graphics.Color.argb(25, android.graphics.Color.red(memberColor), android.graphics.Color.green(memberColor), android.graphics.Color.blue(memberColor))
                                outlinePaint.color = android.graphics.Color.argb(160, android.graphics.Color.red(memberColor), android.graphics.Color.green(memberColor), android.graphics.Color.blue(memberColor))
                                outlinePaint.strokeWidth = 1.5f
                            })
                        }

                        val memberKey = "${member.displayName.trim().take(2).uppercase()}:$memberColor"
                        val memberIcon = avatarDrawableCache.getOrPut(memberKey) {
                            createMemberAvatarDrawable(context, member.displayName, memberColor)
                        }
                        peersLayer.add(Marker(map).apply {
                            position = memberGeoPoint
                            title = "${member.displayName} (${member.gait})"
                            val memberSpeed = member.speed * settings.speedUnit.multiplier
                            val modeLabel = formatMarkerMode(member.trackingMode)
                            snippet = "${String.format(Locale.US, "%.1f", memberSpeed)} ${settings.speedUnit.label} • Battery ${member.batteryPercent}% • $modeLabel"
                            infoWindow = null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = memberIcon
                            setOnMarkerClickListener { m, _ ->
                                selectedMarkerInfo = MarkerInspectData(
                                    title = m.title ?: member.displayName,
                                    detail = m.snippet ?: "",
                                    badge = "GROUP",
                                    latitude = member.latitude,
                                    longitude = member.longitude
                                )
                                true
                            }
                        })
                    }

                    val violetColor = android.graphics.Color.parseColor("#7C3AED")
                    for (device in personalDevices) {
                        if (device.deviceId == currentDeviceId || device.id == currentDeviceId) continue
                        if (device.deviceId in groupDeviceIds) continue
                        if (!isValidCoordinate(device.latitude, device.longitude)) continue
                        if (device.latitude == 0.0 && device.longitude == 0.0) continue
                        val devPoint = GeoPoint(device.latitude, device.longitude)
                        val devKey = "${device.deviceName.trim().take(2).uppercase()}:$violetColor"
                        val devIcon = avatarDrawableCache.getOrPut(devKey) {
                            createMemberAvatarDrawable(context, device.deviceName, violetColor)
                        }
                        peersLayer.add(Marker(map).apply {
                            position = devPoint
                            title = device.deviceName
                            snippet = "${device.deviceModel} • Battery ${device.batteryPercent}% • ${formatMarkerMode(device.trackingMode)}"
                            infoWindow = null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = devIcon
                            setOnMarkerClickListener { m, _ ->
                                selectedMarkerInfo = MarkerInspectData(
                                    title = m.title ?: device.deviceName,
                                    detail = m.snippet ?: "",
                                    badge = "DEVICE",
                                    latitude = device.latitude,
                                    longitude = device.longitude
                                )
                                true
                            }
                        })
                    }

                    for (peer in nearbyPeers) {
                        if (!peer.hasValidCoordinates || !isValidCoordinate(peer.latitude, peer.longitude)) continue
                        val peerGeoPoint = GeoPoint(peer.latitude, peer.longitude)
                        val peerColor = android.graphics.Color.parseColor("#059669")
                        val peerKey = "${peer.displayName.trim().take(2).uppercase()}:$peerColor"
                        val peerIcon = avatarDrawableCache.getOrPut(peerKey) {
                            createP2PPeerDrawable(context, peer.displayName)
                        }
                        peersLayer.add(Marker(map).apply {
                            position = peerGeoPoint
                            title = "P2P: ${peer.displayName}"
                            val peerSpeed = peer.speed * settings.speedUnit.multiplier
                            snippet = "${String.format(Locale.US, "%.1f", peerSpeed)} ${settings.speedUnit.label} • Battery ${peer.batteryPercent}% • Direct BLE/Wi-Fi"
                            infoWindow = null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = peerIcon
                            setOnMarkerClickListener { m, _ ->
                                selectedMarkerInfo = MarkerInspectData(
                                    title = m.title ?: peer.displayName,
                                    detail = m.snippet ?: "",
                                    badge = "P2P",
                                    latitude = peer.latitude,
                                    longitude = peer.longitude
                                )
                                true
                            }
                        })
                    }
                }

                // 5a. Differential Static Pins Layer (Dropped, Park/Camp, Saved Places, Search Pin)
                val staticPinsSig = StaticPinsLayerSig(
                    droppedInspectPin = droppedInspectPin,
                    parkedSpot = parkedSpot,
                    savedPlaces = savedPlaces,
                    selectedSearchPin = selectedSearchPin,
                    coordinateFormat = settings.coordinateFormat
                )
                if (lastStaticPinsSigHolder[0] != staticPinsSig) {
                    lastStaticPinsSigHolder[0] = staticPinsSig
                    anyLayerChanged = true
                    staticPinsLayer.items.clear()

                    droppedInspectPin?.let { pinPt ->
                        staticPinsLayer.add(Marker(map).apply {
                            position = pinPt
                            title = "Dropped Pin"
                            infoWindow = null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = droppedPinDrawable
                            setOnMarkerClickListener { _, _ -> true }
                        })
                    }

                    parkedSpot?.let { spot ->
                        if (isValidCoordinate(spot.latitude, spot.longitude)) {
                            staticPinsLayer.add(Marker(map).apply {
                                position = GeoPoint(spot.latitude, spot.longitude)
                                title = spot.name
                                val coordStr = com.geotrace.ui.screens.formatCoordinates(spot.latitude, spot.longitude, settings.coordinateFormat)
                                snippet = coordStr
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = parkedPinDrawable
                                setOnMarkerClickListener { m, _ ->
                                    val distTxt = currentLocationState?.let { cur ->
                                        val d = GeoPoint(cur.latitude, cur.longitude).distanceToAsDouble(GeoPoint(spot.latitude, spot.longitude))
                                        if (d < 1000.0) " • ${d.toInt()}m away" else String.format(Locale.US, " • %.2fkm away", d / 1000.0)
                                    } ?: ""
                                    selectedMarkerInfo = MarkerInspectData(
                                        title = m.title ?: spot.name,
                                        detail = "${coordStr}${distTxt}",
                                        badge = "PARK / CAMP",
                                        latitude = spot.latitude,
                                        longitude = spot.longitude
                                    )
                                    true
                                }
                            })
                        }
                    }

                    savedPlaces.forEach { place ->
                        if (isValidCoordinate(place.latitude, place.longitude)) {
                            staticPinsLayer.add(Marker(map).apply {
                                position = GeoPoint(place.latitude, place.longitude)
                                title = place.name
                                val coordStr = com.geotrace.ui.screens.formatCoordinates(place.latitude, place.longitude, settings.coordinateFormat)
                                val notePrefix = if (place.note.isNotBlank()) "${place.note} • " else ""
                                snippet = "${place.category} • $notePrefix$coordStr"
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = savedPlaceDrawableFor(place.category)
                                setOnMarkerClickListener { m, _ ->
                                    selectedMarkerInfo = MarkerInspectData(
                                        title = m.title ?: place.name,
                                        detail = m.snippet ?: "",
                                        badge = "SAVED PLACE",
                                        latitude = place.latitude,
                                        longitude = place.longitude,
                                        altitude = place.altitude,
                                        photoUri = place.photoUri,
                                        savedPlaceId = place.id
                                    )
                                    true
                                }
                            })
                        }
                    }

                    selectedSearchPin?.let { place ->
                        if (isValidCoordinate(place.latitude, place.longitude)) {
                            staticPinsLayer.add(Marker(map).apply {
                                position = GeoPoint(place.latitude, place.longitude)
                                title = place.name
                                infoWindow = null
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = searchPinDrawable
                                setOnMarkerClickListener { _, _ ->
                                    selectedMarkerInfo = null
                                    droppedInspectPin = null
                                    true
                                }
                            })
                        }
                    }
                }

                // 5b. Differential User Self Chevron & Target Bearing Guide Vector Layer
                val snappedNavPt = activeNavigation?.takeIf { it.isSnappedToRoad }
                val snapProjectionPt = if (activeNavigation?.isOffRoute == true) {
                    activeNavigation.route.points.getOrNull(
                        activeNavigation.nearestPointIndex.coerceIn(0, (activeNavigation.route.points.lastIndex).coerceAtLeast(0))
                    )
                } else null
                val userTargetSig = UserTargetLayerSig(
                    currentLocation = currentLocation,
                    resolvedBearing = resolvedUserBearing,
                    snappedNavLat = snappedNavPt?.snappedLatitude,
                    snappedNavLon = snappedNavPt?.snappedLongitude,
                    snappedNavBearing = snappedNavPt?.snappedBearingDegrees,
                    isOffRoute = activeNavigation?.isOffRoute == true,
                    snappedToProjLat = snapProjectionPt?.latitude,
                    snappedToProjLon = snapProjectionPt?.longitude,
                    showAccuracyRing = settings.showAccuracyRing,
                    speedUnit = settings.speedUnit,
                    focusedTarget = focusedTarget,
                    selectedSearchPin = selectedSearchPin,
                    droppedInspectPin = droppedInspectPin,
                    activeGuideTarget = activeGuideTarget,
                    userPinColorArgb = userPinColor.toArgb()
                )
                if (lastUserTargetSigHolder[0] != userTargetSig) {
                    lastUserTargetSigHolder[0] = userTargetSig
                    anyLayerChanged = true
                    userTargetLayer.items.clear()

                    val selfDrawLat = snappedNavPt?.snappedLatitude ?: currentLocation?.latitude
                    val selfDrawLon = snappedNavPt?.snappedLongitude ?: currentLocation?.longitude

                    // Target bearing guide ray (suppressed during normal on-route road nav; shows off-route return ray when off-route)
                    if (currentLocation != null && selfDrawLat != null && selfDrawLon != null && !isMapPickMode) {
                        val selfGeo = GeoPoint(selfDrawLat, selfDrawLon)
                        val validFocus = focusedTarget?.takeIf {
                            isValidCoordinate(it.latitude, it.longitude) && (it.latitude != 0.0 || it.longitude != 0.0)
                        }
                        val validSearch = selectedSearchPin?.takeIf {
                            isValidCoordinate(it.latitude, it.longitude)
                        }
                        val preferSearchRay = validSearch != null && (validFocus == null || validSearch.requestedAtMs >= validFocus.requestedAtMs)
                        val guideTargetGeo: GeoPoint?
                        val guideRayColor: Int
                        when {
                            activeNavigation != null && activeNavigation.isOffRoute && snapProjectionPt != null -> {
                                guideTargetGeo = GeoPoint(snapProjectionPt.latitude, snapProjectionPt.longitude)
                                guideRayColor = android.graphics.Color.argb(235, 217, 119, 6)
                            }
                            activeNavigation != null -> {
                                guideTargetGeo = null
                                guideRayColor = 0
                            }
                            activeGuideTarget != null &&
                                isValidCoordinate(activeGuideTarget.latitude, activeGuideTarget.longitude) -> {
                                guideTargetGeo = GeoPoint(activeGuideTarget.latitude, activeGuideTarget.longitude)
                                guideRayColor = android.graphics.Color.argb(230, 217, 119, 6)
                            }
                            preferSearchRay && validSearch != null -> {
                                guideTargetGeo = GeoPoint(validSearch.latitude, validSearch.longitude)
                                guideRayColor = android.graphics.Color.argb(215, 15, 118, 110)
                            }
                            validFocus != null -> {
                                guideTargetGeo = GeoPoint(validFocus.latitude, validFocus.longitude)
                                guideRayColor = userPinColor.copy(alpha = 0.85f).toArgb()
                            }
                            droppedInspectPin != null -> {
                                guideTargetGeo = droppedInspectPin
                                guideRayColor = android.graphics.Color.argb(215, 124, 58, 237)
                            }
                            else -> {
                                guideTargetGeo = null
                                guideRayColor = 0
                            }
                        }
                        if (guideTargetGeo != null && selfGeo.distanceToAsDouble(guideTargetGeo) > 3.0) {
                            userTargetLayer.add(Polyline(map).apply {
                                infoWindow = null
                                outlinePaint.color = guideRayColor
                                outlinePaint.strokeWidth = 4.5f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 12f), 0f)
                                setPoints(listOf(selfGeo, guideTargetGeo))
                            })
                        }
                    }

                    if (currentLocation != null && selfDrawLat != null && selfDrawLon != null) {
                        if (settings.showAccuracyRing) {
                            val accuracyPoints = computeCircleGeoPoints(
                                GeoPoint(currentLocation.latitude, currentLocation.longitude),
                                currentLocation.accuracy.toDouble().coerceAtLeast(6.0)
                            )
                            userTargetLayer.add(Polygon(map).apply {
                                points = accuracyPoints
                                infoWindow = null
                                fillPaint.color = userPinColor.copy(alpha = 0.14f).toArgb()
                                outlinePaint.color = userPinColor.copy(alpha = 0.70f).toArgb()
                                outlinePaint.strokeWidth = 2f
                            })
                        }

                        val isCellularFix = currentLocation.provider == "cellular"
                        val chevronRotation = snappedNavPt?.snappedBearingDegrees ?: resolvedUserBearing
                        userTargetLayer.add(Marker(map).apply {
                            position = GeoPoint(selfDrawLat, selfDrawLon)
                            // Flat marker locked to geographic bearing relative to map North;
                            // OSMDroid subtracts mBearing in draw(), so -chevronRotation rotates CW on map canvas.
                            isFlat = true
                            rotation = -chevronRotation
                            title = if (isCellularFix) "This Device (Cellular Fix)" else "This Device (GNSS)"
                            val convertedSpeed = currentLocation.speed * settings.speedUnit.multiplier
                            snippet = "Speed: ${String.format(Locale.US, "%.1f", convertedSpeed)} ${settings.speedUnit.label} (${currentLocation.gaitLabel}) • Acc ±${currentLocation.accuracy.toInt()}m"
                            infoWindow = null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = if (isCellularFix) cellChevronDrawable else gpsChevronDrawable
                            setOnMarkerClickListener { m, _ ->
                                selectedMarkerInfo = MarkerInspectData(
                                    title = m.title ?: "This Device",
                                    detail = m.snippet ?: "",
                                    badge = if (isCellularFix) "CELL" else "GNSS",
                                    latitude = currentLocation.latitude,
                                    longitude = currentLocation.longitude,
                                    altitude = currentLocation.altitude
                                )
                                true
                            }
                        })
                    }
                }

                if (anyLayerChanged) {
                    map.invalidate()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        )

        // Unified Bottom-Start Marker Inspect / Search Pin / Inspected Trail / Target Focus Card (single contextual card, zero overlap)
        val rawFocus = if (!isMapPickMode && selectedMarkerInfo == null) focusedTarget else null
        val rawSearch = if (!isMapPickMode && selectedMarkerInfo == null) selectedSearchPin else null
        val preferSearchOverFocus = rawSearch != null && (rawFocus == null || rawSearch.requestedAtMs >= rawFocus.requestedAtMs)
        val activeSearch = if (preferSearchOverFocus) rawSearch else null
        val activeFocus = if (!preferSearchOverFocus) rawFocus else null
        val matchedSavedPlace = remember(activeSearch, savedPlaces) {
            val s = activeSearch ?: return@remember null
            if (!s.id.startsWith("saved_")) return@remember null
            val targetId = s.id.removePrefix("saved_")
            savedPlaces.firstOrNull { it.id == targetId }
                ?: savedPlaces.firstOrNull {
                    abs(it.latitude - s.latitude) < 1e-5 && abs(it.longitude - s.longitude) < 1e-5
                }
        }
        val matchedSearchWaypoint = remember(activeSearch, trailWaypoints) {
            val s = activeSearch ?: return@remember null
            if (!s.id.startsWith("wpt_")) return@remember null
            val targetId = s.id.removePrefix("wpt_")
            trailWaypoints.firstOrNull { it.id == targetId }
        }
        val isInspectingHistoryTrail = !isMapPickMode && selectedMarkerInfo == null && activeFocus == null && activeSearch == null && inspectedTrailName != null
        val inspectCardTitle = selectedMarkerInfo?.title
            ?: activeFocus?.label
            ?: activeSearch?.name
            ?: if (isInspectingHistoryTrail) inspectedTrailName else null
        val inspectCardDetail = selectedMarkerInfo?.detail
            ?: activeFocus?.subtitle
            ?: activeSearch?.let { p ->
                val coordStr = com.geotrace.ui.screens.formatCoordinates(p.latitude, p.longitude, settings.coordinateFormat)
                if (p.subtitle.isNotBlank()) "$coordStr • ${p.subtitle}" else coordStr
            }
            ?: if (isInspectingHistoryTrail) inspectedTrailSummary else null
        val inspectCardBadge = selectedMarkerInfo?.badge
            ?: if (activeFocus != null) "LOCATING" else activeSearch?.source?.badge ?: if (isInspectingHistoryTrail) "SAVED TRAIL" else null

        val inspectLat = selectedMarkerInfo?.latitude ?: activeFocus?.latitude ?: activeSearch?.latitude
        val inspectLon = selectedMarkerInfo?.longitude ?: activeFocus?.longitude ?: activeSearch?.longitude
        val inspectAlt = selectedMarkerInfo?.altitude
            ?: matchedSavedPlace?.altitude
            ?: matchedSearchWaypoint?.altitude
            ?: 0.0
        val hasInspectCoords = inspectLat != null && inspectLon != null && (inspectLat != 0.0 || inspectLon != 0.0)
        val suppressInspectCardForNav = activeNavigation != null || navPreviewState !is NavPreviewState.Idle

        if (!suppressInspectCardForNav && inspectCardTitle != null && inspectCardBadge != null) {
            val showRecenterAction = selectedMarkerInfo == null && activeFocus != null && onRecenterSelf != null
            val hasActionRow = hasInspectCoords || showRecenterAction
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surface,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 14.dp,
                        end = 80.dp,
                        bottom = (if (isRecording) 70.dp else 16.dp) + bottomOverlayPadding
                    )
                    .widthIn(max = 310.dp)
                    .onSizeChanged { inspectCardHeightPx = it.height }
                    .testTag(if (selectedMarkerInfo != null) "marker_inspect_card" else "map_focus_banner")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 12.dp,
                                top = 8.dp,
                                bottom = if (hasActionRow) 6.dp else 8.dp,
                                end = 4.dp
                            ),
                        verticalAlignment = Alignment.Top
                    ) {
                        val inspectPhotoUri = selectedMarkerInfo?.photoUri?.takeIf { it.isNotBlank() }
                            ?: activeSearch?.photoUri?.takeIf { it.isNotBlank() }
                            ?: matchedSavedPlace?.photoUri?.takeIf { it.isNotBlank() }
                            ?: matchedSearchWaypoint?.photoUri?.takeIf { it.isNotBlank() }
                        if (!inspectPhotoUri.isNullOrBlank()) {
                            val isLocalFile = inspectPhotoUri.startsWith("/")
                            val photoFileAvailable = !isLocalFile || File(inspectPhotoUri).exists()
                            if (photoFileAvailable) {
                                AsyncImage(
                                    model = if (isLocalFile) File(inspectPhotoUri) else inspectPhotoUri,
                                    contentDescription = "Location Photo (tap to enlarge)",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(customColors.surfaceContainerLow)
                                        .clickable { expandedInspectPhotoPath = inspectPhotoUri }
                                        .testTag("inspect_photo_thumbnail")
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = customColors.surfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                        Text(
                                            text = "Photo\nMissing",
                                            fontSize = 8.5.sp,
                                            lineHeight = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = inspectCardBadge,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = inspectCardTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            if (!inspectCardDetail.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = inspectCardDetail,
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                when {
                                    selectedMarkerInfo != null -> {
                                        selectedMarkerInfo = null
                                        droppedInspectPin = null
                                    }
                                    activeFocus != null -> onClearFocusTarget?.invoke()
                                    activeSearch != null -> onClearSearchPin?.invoke()
                                    isInspectingHistoryTrail -> onClearInspectedTrail?.invoke()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Details",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (hasActionRow) {
                        HorizontalDivider(
                            color = colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasInspectCoords) {
                                val targetLat = inspectLat!!
                                val targetLon = inspectLon!!
                                if (onRequestDirections != null && inspectCardBadge != "GNSS" && inspectCardBadge != "CELL") {
                                    Surface(
                                        shape = CircleShape,
                                        color = colorScheme.primary,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val destLabel = if (inspectCardTitle == "Dropped Pin") {
                                                    String.format(Locale.US, "%.4f, %.4f", targetLat, targetLon)
                                                } else {
                                                    inspectCardTitle
                                                }
                                                onRequestDirections(destLabel, targetLat, targetLon)
                                            }
                                            .testTag("inspect_directions_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Directions,
                                                contentDescription = "Get Turn-by-Turn Directions",
                                                tint = colorScheme.onPrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Directions",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }

                                val isGuidingThis = activeGuideTarget != null &&
                                    abs(activeGuideTarget.latitude - targetLat) < 1e-6 &&
                                    abs(activeGuideTarget.longitude - targetLon) < 1e-6
                                if (onSetGuideTarget != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isGuidingThis) customColors.warning.copy(alpha = 0.16f) else colorScheme.tertiary.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isGuidingThis) customColors.warning else colorScheme.tertiary.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (isGuidingThis) {
                                                    onClearGuideTarget?.invoke()
                                                } else {
                                                    onSetGuideTarget(inspectCardTitle, targetLat, targetLon, inspectCardBadge)
                                                }
                                            }
                                            .testTag("inspect_point_target_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Explore,
                                                contentDescription = "Point Compass to Target",
                                                tint = if (isGuidingThis) customColors.warning else colorScheme.tertiary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = if (isGuidingThis) "Guiding" else "Point",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = if (isGuidingThis) customColors.warning else colorScheme.tertiary
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.secondary.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            val encodedLabel = Uri.encode(inspectCardTitle)
                                            val geoUri = Uri.parse("geo:$targetLat,$targetLon?q=$targetLat,$targetLon($encodedLabel)")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                            try {
                                                context.startActivity(mapIntent)
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "No external turn-by-turn navigation app installed.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .testTag("inspect_navigate_ext_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Directions,
                                            contentDescription = "Open in External Maps App",
                                            tint = colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Ext Maps",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = colorScheme.secondary
                                        )
                                    }
                                }

                                val isInspectingParked = inspectCardBadge == "PARK / CAMP" && parkedSpot != null
                                if (isInspectingParked && onClearParkedSpot != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colorScheme.error.copy(alpha = 0.10f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                onClearParkedSpot()
                                                selectedMarkerInfo = null
                                            }
                                            .testTag("inspect_clear_park_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Clear Parked Pin",
                                                tint = colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Clear Park",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = colorScheme.error
                                            )
                                        }
                                    }
                                } else if (onSaveParkedSpot != null && inspectCardBadge != "GNSS" && inspectCardBadge != "CELL") {
                                    Surface(
                                        shape = CircleShape,
                                        color = customColors.warning.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.warning.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                val spotLabel = if (inspectCardTitle == "Dropped Pin") "Parked / Base Camp" else inspectCardTitle
                                                onSaveParkedSpot(targetLat, targetLon, spotLabel)
                                                Toast.makeText(context, "Saved Park/Camp pin", Toast.LENGTH_SHORT).show()
                                            }
                                            .testTag("inspect_save_park_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalParking,
                                                contentDescription = "Save as Parked Vehicle or Base Camp",
                                                tint = customColors.warning,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Park/Camp",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = customColors.warning
                                            )
                                        }
                                    }
                                }

                                val savedPlaceIdToDelete = selectedMarkerInfo?.savedPlaceId ?: matchedSavedPlace?.id
                                if (savedPlaceIdToDelete != null && onDeleteSavedPlace != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colorScheme.error.copy(alpha = 0.10f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                onDeleteSavedPlace(savedPlaceIdToDelete)
                                                selectedMarkerInfo = null
                                                if (matchedSavedPlace != null) {
                                                    onClearSearchPin?.invoke()
                                                }
                                            }
                                            .testTag("inspect_delete_saved_place_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove Saved Place",
                                                tint = colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Remove",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = colorScheme.error
                                            )
                                        }
                                    }
                                } else if (onOpenSavePlaceDialog != null && inspectCardBadge != "GNSS" && inspectCardBadge != "CELL") {
                                    Surface(
                                        shape = CircleShape,
                                        color = colorScheme.primary.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                val defaultLabel = if (inspectCardTitle == "Dropped Pin") "" else inspectCardTitle
                                                onOpenSavePlaceDialog(defaultLabel, targetLat, targetLon, inspectAlt)
                                            }
                                            .testTag("inspect_save_place_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = "Save Place Bookmark",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Save Place",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (inspectCoordsCopied) colorScheme.tertiary.copy(alpha = 0.14f) else colorScheme.primary.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (inspectCoordsCopied) colorScheme.tertiary.copy(alpha = 0.4f) else colorScheme.primary.copy(alpha = 0.28f)
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            com.geotrace.ui.screens.copyCoordinatesWithFeedback(
                                                context = context,
                                                haptic = haptic,
                                                lat = targetLat,
                                                lon = targetLon,
                                                format = settings.coordinateFormat,
                                                label = inspectCardTitle
                                            )
                                            inspectCoordsCopied = true
                                        }
                                        .testTag("inspect_copy_coord_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (inspectCoordsCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy Coordinates",
                                            tint = if (inspectCoordsCopied) colorScheme.tertiary else colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (inspectCoordsCopied) "Copied" else "Copy",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (inspectCoordsCopied) colorScheme.tertiary else colorScheme.primary
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = customColors.surfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            com.geotrace.ui.screens.shareCoordinatesIntent(
                                                context = context,
                                                lat = targetLat,
                                                lon = targetLon,
                                                format = settings.coordinateFormat,
                                                title = inspectCardTitle
                                            )
                                        }
                                        .testTag("inspect_share_coord_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share Coordinates",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Share",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (showRecenterAction) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.secondary.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.28f)),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            droppedInspectPin = null
                                            onRecenterSelf?.invoke()
                                            if (currentLocation != null) {
                                                val curZoom = mapViewRef?.zoomLevelDouble ?: 16.5
                                                val targetZoom = if (curZoom < 10.0) 15.5 else curZoom
                                                mapViewRef?.controller?.animateTo(
                                                    GeoPoint(currentLocation.latitude, currentLocation.longitude),
                                                    targetZoom,
                                                    800L
                                                )
                                            }
                                        }
                                        .testTag("recenter_self_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = "Re-center on Your Location",
                                            tint = colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Re-center",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Calibrated Live Map Scale Bar (stacks cleanly above recording pill / inspect card)
        val scaleBarBottomPadding = (if (isRecording) 70.dp else 16.dp) +
            bottomOverlayPadding +
            fabExtraBottomPadding +
            if (!suppressInspectCardForNav && inspectCardTitle != null && inspectCardBadge != null && inspectCardHeightPx > 0) {
                with(density) { inspectCardHeightPx.toDp() } + 8.dp
            } else {
                0.dp
            }
        if (navPreviewState is NavPreviewState.Idle) {
            MapScaleBarPill(
                mapView = mapViewRef,
                zoomLevel = liveZoomLevel,
                centerLatitude = liveCenterLat,
                speedUnit = settings.speedUnit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = scaleBarBottomPadding)
                    .testTag("map_scale_bar")
            )
        }

        // Overlay Map Controls (Level 2 Elevation - Viewport Controls anchored to Bottom-Right, Strictly 12dp corners)
        val isActivelyFollowing = settings.followUserLocation &&
            !isFollowPaused &&
            !isNavOverviewMode &&
            navPreviewState is NavPreviewState.Idle &&
            focusedTarget == null &&
            selectedSearchPin == null &&
            inspectedTrailName == null
        val isMapRotated = abs(shortestAngleDeltaDegrees(liveMapOrientation, 0f)) >= 1.5f

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp + bottomOverlayPadding + fabExtraBottomPadding, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeNavigation != null) {
                val curNav = activeNavigation
                if (onToggleAutoRotateHeadingUp != null) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val nextEnabled = !settings.autoRotateHeadingUp
                            onToggleAutoRotateHeadingUp(nextEnabled)
                            if (nextEnabled) {
                                isHeadingUpPausedByUser = false
                                isNavOverviewMode = false
                                animateMapOrientationTo(-resolvedUserBearing, 300L)
                            } else {
                                animateMapOrientationTo(0f, 300L)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (settings.autoRotateHeadingUp) {
                                    Modifier.border(1.5.dp, colorScheme.primary, RoundedCornerShape(15.dp))
                                } else Modifier
                            )
                            .testTag("nav_heading_mode_btn"),
                        shape = RoundedCornerShape(15.dp),
                        containerColor = if (settings.autoRotateHeadingUp) colorScheme.primaryContainer else colorScheme.surface,
                        contentColor = if (settings.autoRotateHeadingUp) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (settings.autoRotateHeadingUp) Icons.Default.Navigation else Icons.Default.Explore,
                            contentDescription = if (settings.autoRotateHeadingUp) "Heading-Up Mode (Tap for North-Up)" else "North-Up Mode (Tap for Heading-Up)",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val enterOverview = !isNavOverviewMode
                        isNavOverviewMode = enterOverview
                        if (enterOverview) {
                            animateMapOrientationTo(0f, 260L)
                            val pts = curNav.route.points
                            val split = curNav.nearestPointIndex.coerceIn(0, (pts.size - 1).coerceAtLeast(0))
                            val remGeoPts = pts.subList(split, pts.size)
                                .map { GeoPoint(it.latitude, it.longitude) }
                                .ifEmpty { pts.map { GeoPoint(it.latitude, it.longitude) } }
                            if (remGeoPts.size >= 2) {
                                val box = BoundingBox.fromGeoPointsSafe(remGeoPts)
                                mapViewRef?.post {
                                    try {
                                        mapViewRef?.zoomToBoundingBox(box, true, 120)
                                    } catch (_: Exception) {}
                                }
                            }
                        } else {
                            isHeadingUpPausedByUser = false
                            onRecenterSelf?.invoke()
                            val targetPt = when {
                                curNav.isSnappedToRoad -> GeoPoint(curNav.snappedLatitude, curNav.snappedLongitude)
                                currentLocation != null -> GeoPoint(currentLocation.latitude, currentLocation.longitude)
                                else -> curNav.route.points.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                            }
                            if (targetPt != null) {
                                mapViewRef?.controller?.animateTo(targetPt, 17.5, 650L)
                            }
                            if (settings.autoRotateHeadingUp) {
                                animateMapOrientationTo(-resolvedUserBearing, 300L)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (isNavOverviewMode) {
                                Modifier.border(1.5.dp, colorScheme.primary, RoundedCornerShape(15.dp))
                            } else Modifier
                        )
                        .testTag("nav_overview_toggle_btn"),
                    shape = RoundedCornerShape(15.dp),
                    containerColor = if (isNavOverviewMode) colorScheme.primaryContainer else colorScheme.surface,
                    contentColor = if (isNavOverviewMode) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                ) {
                    Icon(
                        imageVector = if (isNavOverviewMode) Icons.Default.MyLocation else Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = if (isNavOverviewMode) "Resume Turn-by-Turn View" else "Show Route Overview",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Compass Reset FAB (rotates live with map orientation; visible whenever rotated or Heading-Up enabled)
            AnimatedVisibility(
                visible = isMapRotated || settings.autoRotateHeadingUp,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (settings.autoRotateHeadingUp && !isHeadingUpPausedByUser) {
                            isHeadingUpPausedByUser = true
                        }
                        animateMapOrientationTo(0f, 300L)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (settings.autoRotateHeadingUp && !isHeadingUpPausedByUser && isActivelyFollowing) {
                                Modifier.border(1.5.dp, colorScheme.primary, RoundedCornerShape(15.dp))
                            } else Modifier
                        )
                        .testTag("compass_reset_btn"),
                    shape = RoundedCornerShape(15.dp),
                    containerColor = colorScheme.surface,
                    contentColor = colorScheme.onSurface,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                ) {
                    CompassNeedleGlyph(
                        rotationDeg = liveMapOrientation,
                        northColor = colorScheme.error,
                        southColor = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        pivotColor = colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Layer Switcher FAB (opens clean M3 ModalBottomSheet with zero HUD collisions)
            if (onSelectTileLayer != null) {
                FloatingActionButton(
                    onClick = { showLayerMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("layer_switcher_btn"),
                    shape = RoundedCornerShape(15.dp),
                    containerColor = if (showLayerMenu) colorScheme.primaryContainer else colorScheme.surface,
                    contentColor = if (showLayerMenu) colorScheme.onPrimaryContainer else colorScheme.secondary,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Select Map Layer", modifier = Modifier.size(22.dp))
                }
            }

            // Recenter FAB (distinct filled primary when locked-following vs outlined surface when free-roaming/paused)
            FloatingActionButton(
                onClick = {
                    selectedMarkerInfo = null
                    droppedInspectPin = null
                    isHeadingUpPausedByUser = false
                    isNavOverviewMode = false
                    onRecenterSelf?.invoke()
                    val followPt = activeNavigation?.takeIf { it.isSnappedToRoad }?.let {
                        GeoPoint(it.snappedLatitude, it.snappedLongitude)
                    } ?: currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                    if (followPt != null) {
                        val curZoom = mapViewRef?.zoomLevelDouble ?: 16.5
                        val targetZoom = if (activeNavigation != null && curZoom < 15.5) {
                            17.5
                        } else if (curZoom < 10.0) {
                            15.5
                        } else {
                            curZoom
                        }
                        mapViewRef?.controller?.animateTo(
                            followPt,
                            targetZoom,
                            800L
                        )
                    }
                    if (settings.autoRotateHeadingUp) {
                        animateMapOrientationTo(-resolvedUserBearing, 300L)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (!isActivelyFollowing) {
                            Modifier.border(1.5.dp, colorScheme.primary, RoundedCornerShape(15.dp))
                        } else Modifier
                    )
                    .testTag("recenter_map_btn"),
                shape = RoundedCornerShape(15.dp),
                containerColor = if (isActivelyFollowing) colorScheme.primary else colorScheme.surface,
                contentColor = if (isActivelyFollowing) colorScheme.onPrimary else colorScheme.primary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Icon(
                    imageVector = if (isActivelyFollowing) Icons.Default.MyLocation else Icons.Default.GpsNotFixed,
                    contentDescription = if (isActivelyFollowing) "Following Your Location" else "Re-center on Your Location",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Zoom In / Out Cluster (48dp standard touch targets)
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = colorScheme.surface,
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomIn() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                    Box(modifier = Modifier.width(48.dp).height(1.dp).background(colorScheme.outlineVariant.copy(alpha = 0.6f)))
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomOut() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // Modal Bottom Sheet for Map Layers, RF Tower Overlay & Offline Region Caching
        if (showLayerMenu && onSelectTileLayer != null) {
            val layerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showLayerMenu = false },
                sheetState = layerSheetState,
                containerColor = colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.testTag("layer_selection_popup")
            ) {
                Column(
                    modifier = Modifier
                        .adaptiveContentWidth()
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Map Layers & GIS Overlays",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Basemap source & field RF overlays",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showLayerMenu = false },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_layer_sheet_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close map layers",
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "BASEMAP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    TileLayerPickerGrid(
                        selectedTileType = settings.mapTileType,
                        offlineCoveredTypes = coveredOfflineTileTypes,
                        onSelectTileType = { tileType ->
                            onSelectTileLayer(tileType)
                            showLayerMenu = false
                        }
                    )

                    if (onToggleAutoRotateHeadingUp != null || onToggleCellTowerPin != null || onToggleOfflineRegionsOverlay != null || onOpenOfflineMaps != null) {
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text(
                            text = "GIS OVERLAYS & OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    if (onToggleAutoRotateHeadingUp != null) {
                        HeadingUpToggleRow(
                            checked = settings.autoRotateHeadingUp,
                            onCheckedChange = { onToggleAutoRotateHeadingUp(it) },
                            testTag = "heading_up_layer_toggle"
                        )
                    }

                    if (onToggleCellTowerPin != null) {
                        SharedMapOverlayToggleRow(
                            title = "Serving Cell Tower Overlay",
                            description = "Show OpenCellID mast pin & RF signal ray",
                            icon = Icons.Default.SatelliteAlt,
                            checked = settings.showCellTowerPin,
                            onCheckedChange = { onToggleCellTowerPin(it) },
                            testTag = "cell_tower_layer_toggle"
                        )
                    }

                    if (onToggleOfflineRegionsOverlay != null) {
                        SharedMapOverlayToggleRow(
                            title = "Offline Region Outlines",
                            description = "Show boundaries of downloaded offline map areas",
                            icon = Icons.Default.Layers,
                            checked = settings.showOfflineRegionsOverlay,
                            onCheckedChange = { onToggleOfflineRegionsOverlay(it) },
                            testTag = "offline_regions_layer_toggle"
                        )
                    }

                    if (onOpenOfflineMaps != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.tertiary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    showLayerMenu = false
                                    onOpenOfflineMaps()
                                }
                                .testTag("open_offline_maps_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Offline Maps Manager",
                                        tint = colorScheme.tertiary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (offlineRegionsCount > 0) "Offline Maps ($offlineRegionsCount saved)" else "Download Offline Maps",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.tertiary
                                        )
                                        Text(
                                            text = "Cache Satellite, Hybrid or Topo tiles for offline field work",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        if (expandedInspectPhotoPath != null) {
            val photoPath = expandedInspectPhotoPath!!
            val isLocalFile = photoPath.startsWith("/")
            val exists = !isLocalFile || File(photoPath).exists()
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { expandedInspectPhotoPath = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.scrim.copy(alpha = 0.92f)),
                    color = colorScheme.scrim.copy(alpha = 0.92f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (exists) {
                            AsyncImage(
                                model = if (isLocalFile) File(photoPath) else photoPath,
                                contentDescription = "Full screen location photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } else {
                            Text(
                                text = "Photo file is no longer available on device.",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        IconButton(
                            onClick = { expandedInspectPhotoPath = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(48.dp)
                                .background(colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                .testTag("close_inspect_photo_dialog_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close photo preview",
                                tint = colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
    return lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}

@Composable
private fun CompassNeedleGlyph(
    rotationDeg: Float,
    northColor: Color,
    southColor: Color,
    pivotColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotationDeg
        }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val tipR = size.minDimension * 0.44f
        val wingR = size.minDimension * 0.16f

        val northPath = Path().apply {
            moveTo(cx, cy - tipR)
            lineTo(cx + wingR, cy)
            lineTo(cx - wingR, cy)
            close()
        }
        drawPath(path = northPath, color = northColor)

        val southPath = Path().apply {
            moveTo(cx, cy + tipR)
            lineTo(cx + wingR, cy)
            lineTo(cx - wingR, cy)
            close()
        }
        drawPath(path = southPath, color = southColor)

        drawCircle(color = pivotColor, radius = size.minDimension * 0.08f, center = Offset(cx, cy))
    }
}

@Composable
private fun MapScaleBarPill(
    mapView: MapView?,
    zoomLevel: Double,
    centerLatitude: Double,
    speedUnit: com.geotrace.model.SpeedUnit,
    modifier: Modifier = Modifier
) {
    val projection = mapView?.projection ?: return
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val minPx = with(density) { 42.dp.toPx() }
    val maxPx = with(density) { 104.dp.toPx() }
    val targetPx = (minPx + maxPx) * 0.5f

    val candidates = remember(speedUnit) {
        if (speedUnit == com.geotrace.model.SpeedUnit.MPH) {
            listOf(
                105600f * 0.3048f to "20 mi",
                52800f * 0.3048f to "10 mi",
                26400f * 0.3048f to "5 mi",
                10560f * 0.3048f to "2 mi",
                5280f * 0.3048f to "1 mi",
                2640f * 0.3048f to "0.5 mi",
                1000f * 0.3048f to "1000 ft",
                500f * 0.3048f to "500 ft",
                200f * 0.3048f to "200 ft",
                100f * 0.3048f to "100 ft",
                50f * 0.3048f to "50 ft",
                20f * 0.3048f to "20 ft"
            )
        } else {
            listOf(
                50000f to "50 km",
                20000f to "20 km",
                10000f to "10 km",
                5000f to "5 km",
                2000f to "2 km",
                1000f to "1 km",
                500f to "500 m",
                200f to "200 m",
                100f to "100 m",
                50f to "50 m",
                20f to "20 m",
                10f to "10 m",
                5f to "5 m"
            )
        }
    }

    val safeLat = centerLatitude.coerceIn(-85.0, 85.0)
    var bestLabel = candidates.last().second
    var bestPx = minPx
    var bestDiff = Float.MAX_VALUE
    for ((meters, label) in candidates) {
        val px = projection.metersToPixels(meters, safeLat, zoomLevel)
        if (px in minPx..maxPx) {
            val diff = abs(px - targetPx)
            if (diff < bestDiff) {
                bestDiff = diff
                bestPx = px
                bestLabel = label
            }
        }
    }
    if (bestDiff == Float.MAX_VALUE) {
        for ((meters, label) in candidates) {
            val px = projection.metersToPixels(meters, safeLat, zoomLevel)
            if (px > 12f) {
                val diff = abs(px - targetPx)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestPx = px.coerceIn(minPx, maxPx)
                    bestLabel = label
                }
            }
        }
    }

    val barWidthDp = with(density) { bestPx.roundToInt().toDp() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = bestLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            val tickColor = colorScheme.onSurface
            val strokePx = with(density) { 2.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .width(barWidthDp)
                    .height(5.dp)
            ) {
                val bottom = size.height - strokePx * 0.5f
                drawLine(
                    color = tickColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, bottom),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tickColor,
                    start = Offset(0f, bottom),
                    end = Offset(size.width, bottom),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tickColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, bottom),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun computeCircleGeoPoints(center: GeoPoint, radiusMeters: Double, pointsCount: Int = 36): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    val earthRadius = 6378137.0 // Earth radius in meters
    val latRad = Math.toRadians(center.latitude)
    val lonRad = Math.toRadians(center.longitude)
    val dByR = radiusMeters / earthRadius

    for (i in 0 until pointsCount) {
        val bearing = Math.toRadians((i * 360.0) / pointsCount)
        val pointLatRad = kotlin.math.asin(
            sin(latRad) * cos(dByR) + cos(latRad) * sin(dByR) * cos(bearing)
        )
        val pointLonRad = lonRad + kotlin.math.atan2(
            sin(bearing) * sin(dByR) * cos(latRad),
            cos(dByR) - sin(latRad) * sin(pointLatRad)
        )
        points.add(GeoPoint(Math.toDegrees(pointLatRad), Math.toDegrees(pointLonRad)))
    }
    return points
}

private fun createMemberAvatarDrawable(context: Context, name: String, colorInt: Int): android.graphics.drawable.Drawable {
    val size = 96
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 6f
    }
    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = android.graphics.Paint.Style.FILL
    }
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 34f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }

    val radius = (size / 2f) - 6f
    canvas.drawCircle(size / 2f, size / 2f, radius, fillPaint)
    canvas.drawCircle(size / 2f, size / 2f, radius, strokePaint)

    val initials = name.trim().take(2).uppercase().ifBlank { "M" }
    val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
    canvas.drawText(initials, size / 2f, yPos, textPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

private fun createP2PPeerDrawable(context: Context, name: String): android.graphics.drawable.Drawable {
    return createMemberAvatarDrawable(context, name, android.graphics.Color.parseColor("#059669"))
}

private fun createUserChevronDrawable(context: Context, colorInt: Int): android.graphics.drawable.Drawable {
    val size = 84
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(65, android.graphics.Color.red(colorInt), android.graphics.Color.green(colorInt), android.graphics.Color.blue(colorInt))
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, cx - 2f, haloPaint)

    val circleFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = android.graphics.Paint.Style.FILL
    }
    val circleStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(cx, cy, 24f, circleFill)
    canvas.drawCircle(cx, cy, 24f, circleStroke)

    val chevronPath = android.graphics.Path().apply {
        moveTo(cx, cy - 14f)
        lineTo(cx + 11f, cy + 10f)
        lineTo(cx, cy + 4f)
        lineTo(cx - 11f, cy + 10f)
        close()
    }
    val arrowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawPath(chevronPath, arrowPaint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

private fun createCellTowerDrawable(context: Context, isServing: Boolean): android.graphics.drawable.Drawable {
    val size = if (isServing) 82 else 60
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val bgColor = if (isServing) android.graphics.Color.parseColor("#D97706") else android.graphics.Color.parseColor("#64748B")
    val alpha = if (isServing) 255 else 185

    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        this.alpha = alpha
        style = android.graphics.Paint.Style.FILL
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.alpha = alpha
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = if (isServing) 4.5f else 3f
    }
    val r = (size / 2f) - 5f
    canvas.drawCircle(cx, cy, r, fillPaint)
    canvas.drawCircle(cx, cy, r, strokePaint)

    val mastPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.alpha = alpha
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = if (isServing) 4f else 2.8f
        strokeCap = Paint.Cap.ROUND
    }
    val topY = cy - r * 0.48f
    val botY = cy + r * 0.48f
    canvas.drawLine(cx, topY + 4f, cx - r * 0.34f, botY, mastPaint)
    canvas.drawLine(cx, topY + 4f, cx + r * 0.34f, botY, mastPaint)
    canvas.drawLine(cx - r * 0.2f, cy + r * 0.14f, cx + r * 0.2f, cy + r * 0.14f, mastPaint)

    val tipPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.alpha = alpha
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(cx, topY + 3f, if (isServing) 4.5f else 3.2f, tipPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

private fun createPinBadgeDrawable(context: Context, label: String, colorInt: Int): android.graphics.drawable.Drawable {
    val size = 54
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = android.graphics.Paint.Style.FILL
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4f
    }
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val r = (size / 2f) - 4f
    canvas.drawCircle(cx, cy, r, fillPaint)
    canvas.drawCircle(cx, cy, r, strokePaint)
    val yPos = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(label, cx, yPos, textPaint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

