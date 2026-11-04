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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
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
import com.example.gistracker.model.CellTowerInfo
import com.example.gistracker.model.GeofenceCategory
import com.example.gistracker.model.GeofenceZone
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
    geofences: List<GeofenceZone>,
    cellTowers: List<CellTowerInfo>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val trailColor = colorScheme.secondary
    val userPinColor = colorScheme.primary
    val safeColor = colorScheme.tertiary
    val hazardColor = colorScheme.error
    val warningColor = customColors.warning

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnFirstFix by remember { mutableStateOf(false) }

    var showCellTowers by remember { mutableStateOf(true) }
    var showGeofences by remember { mutableStateOf(true) }

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
        // Real OpenStreetMap View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK) // Genuine OSM street map tiles
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.0)

                    if (currentLocation != null) {
                        controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    } else {
                        // Default neutral view (Uganda)
                        controller.setCenter(GeoPoint(0.3345, 32.5712))
                    }
                    mapViewRef = this
                }
            },
            update = { map ->
                map.overlays.clear()

                // 1. Draw Real GPS Trail
                if (trailPoints.size >= 2) {
                    val polyline = Polyline(map).apply {
                        outlinePaint.color = trailColor.toArgb()
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        setPoints(trailPoints.map { GeoPoint(it.latitude, it.longitude) })
                    }
                    map.overlays.add(polyline)
                }

                // 2. Draw Geofence Safe Zones
                if (showGeofences) {
                    for (zone in geofences) {
                        val zoneColor = when (zone.category) {
                            GeofenceCategory.SAFE_ZONE, GeofenceCategory.HOME -> safeColor
                            GeofenceCategory.BODA_HOTSPOT -> hazardColor
                            GeofenceCategory.SCHOOL, GeofenceCategory.WORK -> trailColor
                            GeofenceCategory.CUSTOM -> warningColor
                        }

                        val circlePoints = computeCircleGeoPoints(
                            GeoPoint(zone.latitude, zone.longitude),
                            zone.radiusMeters.toDouble()
                        )
                        val polygon = Polygon(map).apply {
                            points = circlePoints
                            fillPaint.color = zoneColor.copy(alpha = if (zone.isInside) 0.32f else 0.14f).toArgb()
                            outlinePaint.color = zoneColor.toArgb()
                            outlinePaint.strokeWidth = 3f
                            title = "🛡️ ${zone.name} (${zone.radiusMeters.toInt()}m radius)"
                        }
                        map.overlays.add(polygon)
                    }
                }

                // 3. Draw Cell Towers
                if (showCellTowers) {
                    for (tower in cellTowers) {
                        val lat = tower.latitude
                        val lon = tower.longitude
                        if (lat != null && lon != null) {
                            val towerMarker = Marker(map).apply {
                                position = GeoPoint(lat, lon)
                                title = "📡 ${tower.operatorDisplayName}\nCID: ${tower.cid ?: "N/A"}"
                                snippet = if (tower.isDemonstrationData) "[Academic Reference Tower]" else "Signal: ${tower.signalDbm ?: 0} dBm"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(towerMarker)
                        }
                    }
                }

                // 4. Draw Current User Location Marker & Accuracy Circle
                if (currentLocation != null) {
                    // Accuracy Circle
                    val accuracyPoints = computeCircleGeoPoints(
                        GeoPoint(currentLocation.latitude, currentLocation.longitude),
                        currentLocation.accuracy.toDouble().coerceAtLeast(10.0)
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

        // Overlay Map Controls (Zoom, Layers, Recenter)
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

            Spacer(modifier = Modifier.size(8.dp))

            Surface(
                shape = CircleShape,
                color = if (showCellTowers) trailColor else colorScheme.surface,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
            ) {
                IconButton(
                    onClick = { showCellTowers = !showCellTowers },
                    modifier = Modifier.testTag("toggle_cell_layer_btn")
                ) {
                    Icon(
                        Icons.Default.CellTower,
                        contentDescription = "Toggle Cell Towers",
                        tint = if (showCellTowers) colorScheme.onSecondary else colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            Surface(
                shape = CircleShape,
                color = if (showGeofences) safeColor else colorScheme.surface,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
            ) {
                IconButton(
                    onClick = { showGeofences = !showGeofences },
                    modifier = Modifier.testTag("toggle_geofence_layer_btn")
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Toggle Geofences",
                        tint = if (showGeofences) colorScheme.onTertiary else colorScheme.onSurfaceVariant
                    )
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
                    .size(48.dp)
                    .testTag("recenter_map_btn"),
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on Location")
            }
        }

        // Bottom Left Coordinates & Satellite Status Badge
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 120.dp),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (currentLocation != null) {
                    Text(
                        text = "GPS: ${String.format("%.5f", currentLocation.latitude)}°N, ${String.format("%.5f", currentLocation.longitude)}°E",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real Acc: ±${currentLocation.accuracy.toInt()}m | Trail Points: ${trailPoints.size}",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Awaiting Real GPS Fix (Walk Outdoors)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }
        }

        // "Awaiting GPS Satellites" Banner if user has not yet acquired location
        AnimatedVisibility(
            visible = currentLocation == null,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.2.dp, colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.GpsNotFixed,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "Acquiring Real GPS Position",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "The app is listening to real device GNSS satellites and FusedLocation.\nNo artificial positions are being plotted.\nOnce satellite lock is achieved, the OpenStreetMap view will center onto your live location automatically.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun computeCircleGeoPoints(center: GeoPoint, radiusMeters: Double): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    for (i in 0 until 36) {
        val angle = Math.toRadians(i * 10.0)
        val latOffset = (radiusMeters / 111320.0) * cos(angle)
        val lonOffset = (radiusMeters / (111320.0 * cos(Math.toRadians(center.latitude)))) * sin(angle)
        points.add(GeoPoint(center.latitude + latOffset, center.longitude + lonOffset))
    }
    return points
}
