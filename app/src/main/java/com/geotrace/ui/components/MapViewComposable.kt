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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
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
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
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
    personalDevices: List<com.geotrace.model.PersonalDevice> = emptyList(),
    currentDeviceId: String = "",
    nearbyPeers: List<com.geotrace.model.P2PPeer> = emptyList(),
    focusedTarget: com.geotrace.model.MapFocusTarget? = null,
    offlineRegionsCount: Int = 0,
    settings: com.geotrace.model.TrackerSettings = com.geotrace.model.TrackerSettings(),
    onSelectTileLayer: ((com.geotrace.model.MapTileType) -> Unit)? = null,
    onToggleCellTowerPin: ((Boolean) -> Unit)? = null,
    onOpenOfflineMaps: (() -> Unit)? = null,
    onMapBoundsChanged: ((BoundingBox) -> Unit)? = null,
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

    // Update Tile Source dynamically when user switches in Settings or Layer Menu
    LaunchedEffect(settings.mapTileType, mapViewRef) {
        mapViewRef?.let { map ->
            map.setTileSource(MapTileSources.baseTileSourceFor(settings.mapTileType))
            map.invalidate()
        }
    }

    // Auto-center map when first genuine location fix arrives, or if followUserLocation is enabled (and not locked on another device)
    LaunchedEffect(currentLocation, settings.followUserLocation, focusedTarget) {
        if (focusedTarget == null && currentLocation != null) {
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

    // Pan & zoom smoothly onto a requested remote device, group member, or P2P peer
    LaunchedEffect(focusedTarget?.requestedAtMs, mapViewRef) {
        val target = focusedTarget ?: return@LaunchedEffect
        if (target.latitude != 0.0 || target.longitude != 0.0) {
            mapViewRef?.controller?.animateTo(
                GeoPoint(target.latitude, target.longitude),
                17.5,
                750L
            )
            hasCenteredOnFirstFix = true
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
            hybridLabelsProvider.detach()
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
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.0)

                    if (focusedTarget != null && (focusedTarget.latitude != 0.0 || focusedTarget.longitude != 0.0)) {
                        controller.setCenter(GeoPoint(focusedTarget.latitude, focusedTarget.longitude))
                        controller.setZoom(17.5)
                    } else if (currentLocation != null) {
                        controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    } else {
                        // Global neutral view, awaiting genuine GNSS fix
                        controller.setCenter(GeoPoint(0.0, 0.0))
                        controller.setZoom(3.0)
                    }
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            onMapBoundsChanged?.invoke(boundingBox)
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            onMapBoundsChanged?.invoke(boundingBox)
                            return false
                        }
                    })
                    post {
                        onMapBoundsChanged?.invoke(boundingBox)
                    }
                    mapViewRef = this
                }
            },
            update = { map ->
                map.overlays.clear()

                // 0. Hybrid street & place labels overlay on top of Esri Satellite imagery
                if (settings.mapTileType == com.geotrace.model.MapTileType.HYBRID) {
                    map.overlays.add(hybridLabelsOverlay)
                }

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

                    // Trail Start & Latest End Badges
                    val startPt = trailPoints.first()
                    val endPt = trailPoints.last()
                    val startMarker = Marker(map).apply {
                        position = GeoPoint(startPt.latitude, startPt.longitude)
                        title = "Trail Start"
                        snippet = "First logged coordinate"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = trailStartDrawable
                    }
                    map.overlays.add(startMarker)

                    val endMarker = Marker(map).apply {
                        position = GeoPoint(endPt.latitude, endPt.longitude)
                        title = "Trail End"
                        snippet = "Latest logged waypoint"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = trailEndDrawable
                    }
                    map.overlays.add(endMarker)
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

                    // Custom Rotated Directional Chevron (Cyan = GNSS/Fused, Amber = Cellular fallback)
                    val isCellularFix = currentLocation.provider == "cellular"
                    val userMarker = Marker(map).apply {
                        position = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                        rotation = currentLocation.bearing
                        title = if (isCellularFix) "Current Position (Cellular Fix)" else "Current Position (GNSS)"
                        val convertedSpeed = currentLocation.speed * settings.speedUnit.multiplier
                        snippet = "Speed: ${String.format("%.1f", convertedSpeed)} ${settings.speedUnit.label} (${currentLocation.gaitLabel}) | Acc: ±${currentLocation.accuracy.toInt()}m"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = if (isCellularFix) cellChevronDrawable else gpsChevronDrawable
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
                                title = "Idle/Neighbor Tower: ${tower.operatorName}"
                                snippet = "CID: ${tower.cellId} | Standby Sector (Not In Use)"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = neighborTowerDrawable
                            }
                            map.overlays.add(neighborMarker)
                        }
                    }

                    // Second pass: Draw Active Serving Tower (Signal-strength coded ray + Gold Mast Icon)
                    if (servingTower != null) {
                        val towerGeoPoint = GeoPoint(servingTower.latitude, servingTower.longitude)
                        val dbm = servingTower.signalDbm
                        val signalColor = when {
                            dbm == null -> android.graphics.Color.rgb(217, 119, 6) // Amber default
                            dbm >= -85 -> android.graphics.Color.rgb(5, 150, 105)  // Strong Emerald
                            dbm >= -102 -> android.graphics.Color.rgb(217, 119, 6) // Moderate Amber
                            else -> android.graphics.Color.rgb(220, 38, 38)        // Weak Red
                        }
                        val signalLabel = when {
                            dbm == null -> "Signal N/A"
                            dbm >= -85 -> "$dbm dBm (Strong)"
                            dbm >= -102 -> "$dbm dBm (Moderate)"
                            else -> "$dbm dBm (Weak)"
                        }

                        // Serving Cell Tower Coverage / Uncertainty Radius Circle
                        val towerCirclePoints = computeCircleGeoPoints(towerGeoPoint, servingTower.rangeMeters)
                        val towerCoveragePolygon = Polygon(map).apply {
                            points = towerCirclePoints
                            fillPaint.color = android.graphics.Color.argb(38, android.graphics.Color.red(signalColor), android.graphics.Color.green(signalColor), android.graphics.Color.blue(signalColor))
                            outlinePaint.color = android.graphics.Color.argb(220, android.graphics.Color.red(signalColor), android.graphics.Color.green(signalColor), android.graphics.Color.blue(signalColor))
                            outlinePaint.strokeWidth = 2.8f
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                            title = "ACTIVE SERVING BASE STATION: ${servingTower.operatorName}"
                            snippet = "CID: ${servingTower.cellId} | LAC: ${servingTower.lac} | $signalLabel | ±${servingTower.rangeMeters.toInt()}m"
                        }
                        map.overlays.add(towerCoveragePolygon)

                        // Connecting Vector Ray (color-coded by live dBm RF signal strength)
                        if (currentLocation != null) {
                            val vectorRay = Polyline(map).apply {
                                outlinePaint.color = android.graphics.Color.argb(235, android.graphics.Color.red(signalColor), android.graphics.Color.green(signalColor), android.graphics.Color.blue(signalColor))
                                outlinePaint.strokeWidth = 4.0f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                                title = "Radio Link • $signalLabel"
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
                            title = "ACTIVE SERVING MAST: ${servingTower.operatorName}"
                            snippet = "${servingTower.addressEstimate ?: "Active Sector"} | CID: ${servingTower.cellId} | $signalLabel | ±${servingTower.rangeMeters.toInt()}m"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = servingTowerDrawable
                        }
                        map.overlays.add(towerMarker)
                    }
                }

                // 5. Draw Tracking Group Devices (excluding self device to avoid overlapping User pin)
                for (member in groupMembers) {
                    if (currentDeviceId.isNotBlank() && member.deviceId == currentDeviceId) continue
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

                // 5b. Draw Personal Multi-Device Fleet (other phones/tablets on same account)
                val violetColor = android.graphics.Color.parseColor("#7C3AED")
                for (device in personalDevices) {
                    if (device.deviceId == currentDeviceId || device.id == currentDeviceId) continue
                    if (device.latitude == 0.0 && device.longitude == 0.0) continue
                    val devPoint = GeoPoint(device.latitude, device.longitude)
                    val devMarker = Marker(map).apply {
                        position = devPoint
                        title = "📱 My Device: ${device.deviceName}"
                        snippet = "${device.deviceModel} | Battery: ${device.batteryPercent}% (${device.trackingMode})"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createMemberAvatarDrawable(context, device.deviceName, violetColor)
                    }
                    map.overlays.add(devMarker)
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
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .heightIn(max = 380.dp)
                        .testTag("layer_selection_popup")
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                                            text = "OpenCellID Mast Pin & Signal Ray",
                                            fontSize = 9.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (onOpenOfflineMaps != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.tertiary.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clickable {
                                        showLayerMenu = false
                                        onOpenOfflineMaps()
                                    }
                                    .testTag("open_offline_maps_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Offline Maps Manager",
                                        tint = colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (offlineRegionsCount > 0) "Offline Maps ($offlineRegionsCount saved)" else "Download Offline Maps",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.tertiary
                                        )
                                        Text(
                                            text = "Cache Satellite, Hybrid or Topo areas",
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

