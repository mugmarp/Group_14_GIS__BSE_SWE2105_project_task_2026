package com.example.gistracker.ui.components

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val trailColor = colorScheme.secondary
    val userPinColor = colorScheme.primary

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnFirstFix by remember { mutableStateOf(false) }

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

    // Auto-center map when first genuine location fix arrives
    LaunchedEffect(currentLocation) {
        if (currentLocation != null && !hasCenteredOnFirstFix) {
            mapViewRef?.controller?.animateTo(
                GeoPoint(currentLocation.latitude, currentLocation.longitude),
                16.5,
                1000L
            )
            hasCenteredOnFirstFix = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_canvas_container")
    ) {
        // Genuine OpenStreetMap View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
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

                // 1. Draw Real GPS Trail (segmented to avoid multi-km teleportation jumps)
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
                                        outlinePaint.strokeWidth = 10f
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
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            setPoints(currentSegment)
                        }
                        map.overlays.add(polyline)
                    }
                }

                // 2. Draw Current User Location Marker & Accuracy Circle
                if (currentLocation != null) {
                    // Accuracy Circle
                    val accuracyPoints = computeCircleGeoPoints(
                        GeoPoint(currentLocation.latitude, currentLocation.longitude),
                        currentLocation.accuracy.toDouble().coerceAtLeast(8.0)
                    )
                    val accuracyPolygon = Polygon(map).apply {
                        points = accuracyPoints
                        fillPaint.color = userPinColor.copy(alpha = 0.18f).toArgb()
                        outlinePaint.color = userPinColor.copy(alpha = 0.65f).toArgb()
                        outlinePaint.strokeWidth = 2f
                    }
                    map.overlays.add(accuracyPolygon)

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

        // Overlay Map Controls (Zoom, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomIn() },
                        modifier = Modifier.testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomOut() },
                        modifier = Modifier.testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.size(10.dp))

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
                    .size(48.dp)
                    .testTag("recenter_map_btn"),
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on Location")
            }
        }

        // Prominent "Waiting for GPS satellite signal" State Banner when unacquired
        if (currentLocation == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, customColors.warning)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SatelliteAlt,
                        contentDescription = null,
                        tint = customColors.warning,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Waiting for Satellite Signal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Searching for GNSS fix via FusedLocationProviderClient.\nMove outdoors with a clear view of the sky.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = customColors.warning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Awaiting satellite lock...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        )
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
