package com.geotrace.ui.components

import android.content.Context
import android.graphics.Paint
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.Layers
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
import com.geotrace.model.LocationPoint
import com.geotrace.ui.theme.LocalCustomColors
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
    geofenceZones: List<com.geotrace.model.GeofenceZone> = emptyList(),
    cellTowerLocation: com.geotrace.model.CellTowerLocation? = null,
    allCellTowers: List<com.geotrace.model.CellTowerLocation> = emptyList(),
    groupMembers: List<com.geotrace.model.MemberLocation> = emptyList(),
    nearbyPeers: List<com.geotrace.model.P2PPeer> = emptyList(),
    settings: com.geotrace.model.TrackerSettings = com.geotrace.model.TrackerSettings(),
    onSelectTileLayer: ((com.geotrace.model.MapTileType) -> Unit)? = null,
    onToggleCellTowerPin: ((Boolean) -> Unit)? = null,
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
    var showLayerMenu by remember { mutableStateOf(false) }

    // Update Tile Source dynamically when user switches in Settings
    LaunchedEffect(settings.mapTileType) {
        mapViewRef?.let { map ->
            val tileSource = when (settings.mapTileType) {
                com.geotrace.model.MapTileType.STANDARD -> TileSourceFactory.MAPNIK
                com.geotrace.model.MapTileType.TOPO -> TileSourceFactory.OpenTopo
                com.geotrace.model.MapTileType.HUMANITARIAN -> TileSourceFactory.DEFAULT_TILE_SOURCE
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
                        com.geotrace.model.MapTileType.STANDARD -> TileSourceFactory.MAPNIK
                        com.geotrace.model.MapTileType.TOPO -> TileSourceFactory.OpenTopo
                        com.geotrace.model.MapTileType.HUMANITARIAN -> TileSourceFactory.DEFAULT_TILE_SOURCE
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
                        val convertedSpeed = currentLocation.speed * settings.speedUnit.multiplier
                        snippet = "Speed: ${String.format("%.1f", convertedSpeed)} ${settings.speedUnit.label} (${currentLocation.gaitLabel}) | Acc: ±${currentLocation.accuracy.toInt()}m"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(userMarker)
                }

                // 3. Draw Geofence Circular Zones (Safe Zones & Boda Warning Perimeters)
                for (zone in geofenceZones) {
                    val zoneCenter = GeoPoint(zone.latitude, zone.longitude)
                    val circlePoints = computeCircleGeoPoints(zoneCenter, zone.radiusMeters.toDouble())
                    val zoneColor = try {
                        android.graphics.Color.parseColor(zone.colorHex)
                    } catch (e: Exception) {
                        android.graphics.Color.parseColor("#006948")
                    }
                    val fillColor = android.graphics.Color.argb(
                        40,
                        android.graphics.Color.red(zoneColor),
                        android.graphics.Color.green(zoneColor),
                        android.graphics.Color.blue(zoneColor)
                    )
                    val outlineColor = android.graphics.Color.argb(
                        210,
                        android.graphics.Color.red(zoneColor),
                        android.graphics.Color.green(zoneColor),
                        android.graphics.Color.blue(zoneColor)
                    )

                    val zonePolygon = Polygon(map).apply {
                        points = circlePoints
                        fillPaint.color = fillColor
                        outlinePaint.color = outlineColor
                        outlinePaint.strokeWidth = 3f
                        title = "${zone.category.iconEmoji} ${zone.name} (${zone.radiusMeters.toInt()}m)"
                        snippet = "Category: ${zone.category.label} | Status: ${if (zone.isInside) "INSIDE SAFE ZONE" else "OUTSIDE"}"
                    }
                    map.overlays.add(zonePolygon)
                }

                // 4. Draw Cell Towers (Option 2 requirement: Distinguish Active Serving Mast vs Neighbor/Idle Masts)
                if (settings.showCellTowerPin) {
                    val towersToDraw = if (allCellTowers.isNotEmpty()) allCellTowers else listOfNotNull(cellTowerLocation)
                    val servingTower = cellTowerLocation ?: towersToDraw.firstOrNull { it.isServing }

                    // First pass: Draw Neighbor / Standby Towers (Muted Gray, Not in Use)
                    for (tower in towersToDraw) {
                        if (tower.cellId != servingTower?.cellId) {
                            val neighborGeoPoint = GeoPoint(tower.latitude, tower.longitude)

                            // Subtle dashed outline for neighbor footprint
                            val neighborCirclePoints = computeCircleGeoPoints(neighborGeoPoint, tower.rangeMeters)
                            val neighborPolygon = Polygon(map).apply {
                                points = neighborCirclePoints
                                fillPaint.color = android.graphics.Color.argb(12, 100, 116, 139) // Slate gray faint
                                outlinePaint.color = android.graphics.Color.argb(120, 148, 163, 184)
                                outlinePaint.strokeWidth = 1.5f
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
                                title = "🗼 Neighbor/Idle Mast (${tower.operatorName})"
                                snippet = "CID: ${tower.cellId} | Status: STANDBY (Available for Handover)"
                            }
                            map.overlays.add(neighborPolygon)

                            // Muted neighbor marker
                            val neighborMarker = Marker(map).apply {
                                position = neighborGeoPoint
                                title = "🗼 Idle/Neighbor Tower: ${tower.operatorName}"
                                snippet = "CID: ${tower.cellId} | Standby Sector (Not In Use)"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(neighborMarker)
                        }
                    }

                    // Second pass: Draw Active Serving Tower (Vibrant Amber/Gold, In Use)
                    if (servingTower != null) {
                        val towerGeoPoint = GeoPoint(servingTower.latitude, servingTower.longitude)

                        // Serving Cell Tower Coverage / Uncertainty Radius Circle
                        val towerCirclePoints = computeCircleGeoPoints(towerGeoPoint, servingTower.rangeMeters)
                        val towerCoveragePolygon = Polygon(map).apply {
                            points = towerCirclePoints
                            fillPaint.color = android.graphics.Color.argb(40, 217, 119, 6) // Amber/Gold translucent
                            outlinePaint.color = android.graphics.Color.argb(220, 217, 119, 6)
                            outlinePaint.strokeWidth = 2.8f
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                            title = "📡 ACTIVE SERVING BASE STATION: ${servingTower.operatorName}"
                            snippet = "CID: ${servingTower.cellId} | LAC: ${servingTower.lac} | Coverage: ±${servingTower.rangeMeters.toInt()}m"
                        }
                        map.overlays.add(towerCoveragePolygon)

                        // Connecting Vector Ray (from Phone location to Active Serving Tower)
                        if (currentLocation != null) {
                            val vectorRay = Polyline(map).apply {
                                outlinePaint.color = android.graphics.Color.argb(230, 217, 119, 6)
                                outlinePaint.strokeWidth = 3.5f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                                title = "Active Radio Link (Phone ↔ Serving Tower)"
                                setPoints(listOf(
                                    GeoPoint(currentLocation.latitude, currentLocation.longitude),
                                    towerGeoPoint
                                ))
                            }
                            map.overlays.add(vectorRay)
                        }

                        // Serving Cell Tower Marker
                        val towerMarker = Marker(map).apply {
                            position = towerGeoPoint
                            title = "📡 ACTIVE SERVING MAST: ${servingTower.operatorName}"
                            snippet = "${servingTower.addressEstimate ?: "Active Sector"} | CID: ${servingTower.cellId} | Range: ±${servingTower.rangeMeters.toInt()}m"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(towerMarker)
                    }
                }

                // 5. Draw Multi-User Group Members (Family / Field Team)
                for (member in groupMembers) {
                    val memberGeoPoint = GeoPoint(member.latitude, member.longitude)
                    val memberColor = try {
                        android.graphics.Color.parseColor(member.avatarColor)
                    } catch (e: Exception) {
                        android.graphics.Color.parseColor("#2563EB")
                    }

                    // Accuracy Circle for member
                    if (member.accuracy > 0f) {
                        val memberAccCircle = computeCircleGeoPoints(memberGeoPoint, member.accuracy.toDouble())
                        val memberPolygon = Polygon(map).apply {
                            points = memberAccCircle
                            fillPaint.color = android.graphics.Color.argb(25, android.graphics.Color.red(memberColor), android.graphics.Color.green(memberColor), android.graphics.Color.blue(memberColor))
                            outlinePaint.color = android.graphics.Color.argb(160, android.graphics.Color.red(memberColor), android.graphics.Color.green(memberColor), android.graphics.Color.blue(memberColor))
                            outlinePaint.strokeWidth = 1.5f
                        }
                        map.overlays.add(memberPolygon)
                    }

                    // Custom avatar pin
                    val memberMarker = Marker(map).apply {
                        position = memberGeoPoint
                        title = "👤 ${member.displayName} (${member.gait})"
                        snippet = "Speed: ${String.format(Locale.US, "%.1f", member.speed * 3.6f)} km/h | Battery: ${member.batteryPercent}% (${member.trackingMode})"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createMemberAvatarDrawable(context, member.displayName, memberColor)
                    }
                    map.overlays.add(memberMarker)
                }

                // 6. Draw P2P Wilderness Peers (Phase 2 Nearby Connections)
                for (peer in nearbyPeers) {
                    val peerGeoPoint = GeoPoint(peer.latitude, peer.longitude)
                    val peerMarker = Marker(map).apply {
                        position = peerGeoPoint
                        title = "📶 P2P Peer: ${peer.displayName}"
                        snippet = "Speed: ${String.format(Locale.US, "%.1f", peer.speed * 3.6f)} km/h | Battery: ${peer.batteryPercent}% (Direct BLE/Wi-Fi)"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createP2PPeerDrawable(context, peer.displayName)
                    }
                    map.overlays.add(peerMarker)
                }

                map.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Map Controls (Level 2 Elevation - Viewport Controls anchored to Bottom-Right, Strictly 12dp corners)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Layer Options Popup (Integrated into bottom Layer FAB)
            if (showLayerMenu && onSelectTileLayer != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surface,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.padding(bottom = 4.dp).testTag("layer_selection_popup")
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MAP LAYER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant,
                            letterSpacing = 0.04.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        com.geotrace.model.MapTileType.values().forEach { tileType ->
                            val isSelected = settings.mapTileType == tileType
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                modifier = Modifier
                                    .clickable {
                                        onSelectTileLayer(tileType)
                                        showLayerMenu = false
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Layers else Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = tileType.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                                        )
                                        Text(
                                            text = tileType.description,
                                            fontSize = 9.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (onToggleCellTowerPin != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (settings.showCellTowerPin) colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                modifier = Modifier
                                    .clickable {
                                        onToggleCellTowerPin(!settings.showCellTowerPin)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SatelliteAlt,
                                        contentDescription = null,
                                        tint = if (settings.showCellTowerPin) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (settings.showCellTowerPin) "Serving Cell Tower: ON" else "Serving Cell Tower: OFF",
                                            fontSize = 11.sp,
                                            fontWeight = if (settings.showCellTowerPin) FontWeight.Bold else FontWeight.Medium,
                                            color = if (settings.showCellTowerPin) colorScheme.primary else colorScheme.onSurface
                                        )
                                        Text(
                                            text = "OpenCellID Uganda Pin & Ray",
                                            fontSize = 9.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Layer Switcher FAB (Google Maps style, 12dp preserved, integrated menu)
            if (onSelectTileLayer != null) {
                FloatingActionButton(
                    onClick = { showLayerMenu = !showLayerMenu },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("layer_switcher_btn"),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = if (showLayerMenu) colorScheme.primaryContainer else colorScheme.surface,
                    contentColor = if (showLayerMenu) colorScheme.primary else colorScheme.secondary,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Select Map Layer", modifier = Modifier.size(20.dp))
                }
            }

            // Recenter FAB (48dp standard touch target)
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
                shape = RoundedCornerShape(12.dp),
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on Location", modifier = Modifier.size(22.dp))
            }

            // Zoom In / Out Cluster (48dp standard touch targets)
            Surface(
                shape = RoundedCornerShape(12.dp),
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

