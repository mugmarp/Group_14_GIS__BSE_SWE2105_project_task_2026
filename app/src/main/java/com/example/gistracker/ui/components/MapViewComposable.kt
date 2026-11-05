package com.example.gistracker.ui.components

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gistracker.model.LocationPoint
import com.example.gistracker.ui.theme.LocalCustomColors
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapViewComposable(
    currentLocation: LocationPoint?,
    trailPoints: List<LocationPoint>,
    settings: com.example.gistracker.model.TrackerSettings = com.example.gistracker.model.TrackerSettings(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    // GeoTrack Safe Telemetry Vector Colors
    val trailColor = colorScheme.secondary // Deep Mission-Critical Cobalt #0051D5
    val userPinColor = colorScheme.primary // Technical Cyan #006194

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnFirstFix by remember { mutableStateOf(false) }

    // Update Tile Source dynamically when user switches in Settings
    LaunchedEffect(settings.mapTileType) {
        mapViewRef?.let { map ->
            val tileSource = when (settings.mapTileType) {
                com.example.gistracker.model.MapTileType.STANDARD -> TileSourceFactory.MAPNIK
                com.example.gistracker.model.MapTileType.TOPO -> TileSourceFactory.OpenTopo
                com.example.gistracker.model.MapTileType.HUMANITARIAN -> TileSourceFactory.DEFAULT_TILE_SOURCE
            }
            map.setTileSource(tileSource)
            map.invalidate()
        }
    }

    // Auto-center map when first genuine location fix arrives, or if followUserLocation is enabled
    LaunchedEffect(currentLocation, settings.followUserLocation) {
        if (currentLocation != null) {
            if (!hasCenteredOnFirstFix || settings.followUserLocation) {
                mapViewRef?.controller?.animateTo(
                    GeoPoint(currentLocation.latitude, currentLocation.longitude),
                    16.5,
                    800L
                )
                hasCenteredOnFirstFix = true
            }
        }
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
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_canvas_container")
    ) {
        // Genuine OpenStreetMap Viewport (Persistent Spatial Foundation)
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    val initialTile = when (settings.mapTileType) {
                        com.example.gistracker.model.MapTileType.STANDARD -> TileSourceFactory.MAPNIK
                        com.example.gistracker.model.MapTileType.TOPO -> TileSourceFactory.OpenTopo
                        com.example.gistracker.model.MapTileType.HUMANITARIAN -> TileSourceFactory.DEFAULT_TILE_SOURCE
                    }
                    setTileSource(initialTile)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.0)

                    if (currentLocation != null) {
                        controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    } else {
                        // Global neutral view, awaiting genuine GNSS fix
                        controller.setCenter(GeoPoint(0.0, 0.0))
                        controller.setZoom(3.0)
                    }
                    mapViewRef = this
                }
            },
            update = { map ->
                map.overlays.clear()

                // 1. Draw Real GPS Trail (segmented to prevent teleportation jumps)
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
                                    map.overlays.add(polyline)
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
                        map.overlays.add(polyline)
                    }
                }

                // 2. Draw Current User Location Marker & Accuracy Circle
                if (currentLocation != null) {
                    // Accuracy Circle with Calibrated Precision (if enabled in Settings)
                    if (settings.showAccuracyRing) {
                        val accuracyPoints = computeCircleGeoPoints(
                            GeoPoint(currentLocation.latitude, currentLocation.longitude),
                            currentLocation.accuracy.toDouble().coerceAtLeast(6.0)
                        )
                        val accuracyPolygon = Polygon(map).apply {
                            points = accuracyPoints
                            fillPaint.color = userPinColor.copy(alpha = 0.14f).toArgb()
                            outlinePaint.color = userPinColor.copy(alpha = 0.70f).toArgb()
                            outlinePaint.strokeWidth = 2f
                        }
                        map.overlays.add(accuracyPolygon)
                    }

                    // User Marker
                    val userMarker = Marker(map).apply {
                        position = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                        rotation = currentLocation.bearing
                        title = "Current Position"
                        snippet = "Speed: ${String.format("%.1f", currentLocation.speedKmh)} km/h | Acc: ±${currentLocation.accuracy.toInt()}m"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(userMarker)
                }

                map.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Map Controls (Level 2 Elevation - Instrument Cluster)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 114.dp, end = 14.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surface,
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomIn() },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.width(42.dp).height(1.dp).background(colorScheme.outlineVariant.copy(alpha = 0.6f)))
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomOut() },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            FloatingActionButton(
                onClick = {
                    if (currentLocation != null) {
                        mapViewRef?.controller?.animateTo(
                            GeoPoint(currentLocation.latitude, currentLocation.longitude),
                            17.0,
                            800L
                        )
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("recenter_map_btn"),
                shape = RoundedCornerShape(8.dp),
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on Location", modifier = Modifier.size(20.dp))
            }
        }

        // Prominent "Waiting for Satellite Signal" State Banner (Awaiting GNSS Lock)
        if (currentLocation == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp),
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surface,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.statusWarningBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Accent indicator
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 3.dp)
                            .background(customColors.warning, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Icon(
                        Icons.Default.SatelliteAlt,
                        contentDescription = null,
                        tint = customColors.warning,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "WAITING FOR GNSS SIGNAL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Acquiring live satellite constellation fix.\nEnsure an unobstructed view of the sky.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = customColors.statusWarningBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.statusWarningBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = customColors.warning
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Awaiting FusedLocationProviderClient lock...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = customColors.statusWarningText
                            )
                        }
                    }
                }
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
