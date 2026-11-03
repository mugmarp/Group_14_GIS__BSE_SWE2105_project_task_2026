package com.example.gistracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gistracker.model.CellTowerInfo
import com.example.gistracker.model.GeofenceCategory
import com.example.gistracker.model.GeofenceZone
import com.example.gistracker.model.LocationPoint
import com.example.gistracker.ui.theme.LocalCustomColors
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
    // Theme colors
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val canvasBg = colorScheme.background
    val gridLineColor = colorScheme.outline.copy(alpha = 0.35f)
    val gridTextColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val trailColor = colorScheme.secondary
    val userPinColor = colorScheme.primary
    val safeColor = colorScheme.tertiary
    val hazardColor = colorScheme.error
    val warningColor = customColors.warning

    // Center coordinate of map
    var centerLat by remember { mutableStateOf(currentLocation?.latitude ?: 0.3345) }
    var centerLon by remember { mutableStateOf(currentLocation?.longitude ?: 32.5712) }
    var zoomScale by remember { mutableFloatStateOf(45000f) }
    var showCellTowers by remember { mutableStateOf(true) }
    var showGeofences by remember { mutableStateOf(true) }

    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            centerLat = currentLocation.latitude
            centerLon = currentLocation.longitude
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
            .testTag("map_canvas_container")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(8000f, 350000f)
                        val dLat = (pan.y / zoomScale).toDouble()
                        val dLon = (-pan.x / (zoomScale * cos(Math.toRadians(centerLat)))).toDouble()
                        centerLat += dLat
                        centerLon += dLon
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            fun project(lat: Double, lon: Double): Offset {
                val cosLat = cos(Math.toRadians(centerLat)).toFloat().coerceAtLeast(0.1f)
                val x = centerX + ((lon - centerLon).toFloat() * zoomScale * cosLat)
                val y = centerY - ((lat - centerLat).toFloat() * zoomScale)
                return Offset(x, y)
            }

            // Draw coordinate grid lines
            drawCoordinateGrid(centerLat, centerLon, zoomScale, gridLineColor, gridTextColor, textMeasurer)

            // Draw Geofences
            if (showGeofences) {
                for (zone in geofences) {
                    val zoneOffset = project(zone.latitude, zone.longitude)
                    val radiusPixels = (zone.radiusMeters / 111000f) * zoomScale

                    val zoneColor = when (zone.category) {
                        GeofenceCategory.SAFE_ZONE, GeofenceCategory.HOME -> safeColor
                        GeofenceCategory.BODA_HOTSPOT -> hazardColor
                        GeofenceCategory.SCHOOL, GeofenceCategory.WORK -> trailColor
                        GeofenceCategory.CUSTOM -> warningColor
                    }

                    // Translucent fill
                    drawCircle(
                        color = zoneColor.copy(alpha = if (zone.isInside) 0.30f else 0.12f),
                        radius = radiusPixels,
                        center = zoneOffset
                    )

                    // Dashed perimeter
                    drawCircle(
                        color = zoneColor,
                        radius = radiusPixels,
                        center = zoneOffset,
                        style = Stroke(
                            width = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    )

                    // Zone label
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = "🛡️ ${zone.name}",
                        topLeft = Offset(zoneOffset.x - 60f, zoneOffset.y - radiusPixels - 25f),
                        style = TextStyle(
                            color = zoneColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Draw Cell Towers
            if (showCellTowers) {
                for (tower in cellTowers) {
                    val towerLat = tower.latitude
                    val towerLon = tower.longitude
                    if (towerLat != null && towerLon != null) {
                        val towerOffset = project(towerLat, towerLon)

                        // Coverage arc
                        val rangePixels = ((tower.estimatedDistanceKm ?: 1.0) * 1000f / 111000f).toFloat() * zoomScale
                        drawCircle(
                            color = trailColor.copy(alpha = 0.08f),
                            radius = rangePixels,
                            center = towerOffset
                        )

                        // Antenna icon marker
                        drawCircle(
                            color = if (tower.isRegistered) warningColor else trailColor,
                            radius = 11f,
                            center = towerOffset
                        )
                        drawCircle(
                            color = canvasBg,
                            radius = 4.5f,
                            center = towerOffset
                        )

                        val towerText = "${tower.operatorDisplayName} (CID: ${tower.cid ?: "?"})"
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = "📡 $towerText",
                            topLeft = Offset(towerOffset.x + 15f, towerOffset.y - 10f),
                            style = TextStyle(
                                color = if (tower.isRegistered) warningColor else trailColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Draw GPS Trail
            if (trailPoints.size >= 2) {
                val path = Path()
                val first = project(trailPoints.first().latitude, trailPoints.first().longitude)
                path.moveTo(first.x, first.y)

                for (i in 1 until trailPoints.size) {
                    val p = trailPoints[i]
                    val pt = project(p.latitude, p.longitude)
                    path.lineTo(pt.x, pt.y)
                }

                // Outer trail glow
                drawPath(
                    path = path,
                    color = trailColor.copy(alpha = 0.28f),
                    style = Stroke(
                        width = 10f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Main trail line
                drawPath(
                    path = path,
                    color = trailColor,
                    style = Stroke(
                        width = 4.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Vertices
                for (p in trailPoints) {
                    val pt = project(p.latitude, p.longitude)
                    drawCircle(
                        color = colorScheme.onSurface,
                        radius = 3f,
                        center = pt
                    )
                }
            }

            // Draw Current Location Marker
            if (currentLocation != null) {
                val currentOffset = project(currentLocation.latitude, currentLocation.longitude)
                val accuracyPixels = (currentLocation.accuracy / 111000f) * zoomScale

                // Accuracy circle
                drawCircle(
                    color = userPinColor.copy(alpha = 0.18f),
                    radius = accuracyPixels.coerceAtLeast(18f),
                    center = currentOffset
                )
                drawCircle(
                    color = userPinColor.copy(alpha = 0.65f),
                    radius = accuracyPixels.coerceAtLeast(18f),
                    center = currentOffset,
                    style = Stroke(width = 2f)
                )

                // Direction / Bearing beam
                val bearingRad = Math.toRadians((currentLocation.bearing - 90.0))
                val arrowLen = 28f
                val arrowX = currentOffset.x + (arrowLen * cos(bearingRad)).toFloat()
                val arrowY = currentOffset.y + (arrowLen * sin(bearingRad)).toFloat()

                drawLine(
                    color = userPinColor,
                    start = currentOffset,
                    end = Offset(arrowX, arrowY),
                    strokeWidth = 5.5f,
                    cap = StrokeCap.Round
                )

                // Center outer ring
                drawCircle(
                    color = colorScheme.surface,
                    radius = 11f,
                    center = currentOffset
                )

                // Center solid pulse
                drawCircle(
                    color = userPinColor,
                    radius = 8f,
                    center = currentOffset
                )
            }
        }

        // Overlay Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
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
                        onClick = { zoomScale = (zoomScale * 1.35f).coerceAtMost(350000f) },
                        modifier = Modifier.testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = { zoomScale = (zoomScale / 1.35f).coerceAtLeast(8000f) },
                        modifier = Modifier.testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = colorScheme.onSurface)
                    }
                }
            }

            Box(modifier = Modifier.size(8.dp))

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

            Box(modifier = Modifier.size(8.dp))

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

            Box(modifier = Modifier.size(8.dp))

            FloatingActionButton(
                onClick = {
                    if (currentLocation != null) {
                        centerLat = currentLocation.latitude
                        centerLon = currentLocation.longitude
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

        // Map Scale & Coordinates Status Badge (Bottom-Left)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
        ) {
            val scaleMeters = ((100f / zoomScale) * 111000f).toInt()
            val scaleText = if (scaleMeters >= 1000) "${scaleMeters / 1000} km" else "$scaleMeters m"
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = "GPS: ${String.format("%.5f", centerLat)}°N, ${String.format("%.5f", centerLon)}°E",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Scale: 100px ≈ $scaleText | Points: ${trailPoints.size}",
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun DrawScope.drawCoordinateGrid(
    centerLat: Double,
    centerLon: Double,
    zoomScale: Float,
    gridLineColor: Color,
    gridTextColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val width = size.width
    val height = size.height
    val centerX = width / 2f
    val centerY = height / 2f
    val cosLat = cos(Math.toRadians(centerLat)).toFloat().coerceAtLeast(0.1f)

    val step = when {
        zoomScale > 150000f -> 0.002
        zoomScale > 60000f -> 0.005
        zoomScale > 25000f -> 0.01
        zoomScale > 12000f -> 0.02
        else -> 0.05
    }

    val minLat = centerLat - (height / (2f * zoomScale))
    val maxLat = centerLat + (height / (2f * zoomScale))
    val minLon = centerLon - (width / (2f * zoomScale * cosLat))
    val maxLon = centerLon + (width / (2f * zoomScale * cosLat))

    var curLat = (minLat / step).toInt() * step
    while (curLat <= maxLat) {
        val y = centerY - ((curLat - centerLat).toFloat() * zoomScale)
        if (y in 0f..height) {
            drawLine(
                color = gridLineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            drawSafeText(
                textMeasurer = textMeasurer,
                text = "${String.format("%.3f", curLat)}°",
                topLeft = Offset(8f, (y - 14f).coerceIn(4f, (height - 20f).coerceAtLeast(4f))),
                style = TextStyle(color = gridTextColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            )
        }
        curLat += step
    }

    var curLon = (minLon / step).toInt() * step
    while (curLon <= maxLon) {
        val x = centerX + ((curLon - centerLon).toFloat() * zoomScale * cosLat)
        if (x in 0f..width) {
            drawLine(
                color = gridLineColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            drawSafeText(
                textMeasurer = textMeasurer,
                text = "${String.format("%.3f", curLon)}°",
                topLeft = Offset((x + 4f).coerceIn(4f, (width - 50f).coerceAtLeast(4f)), (height - 20f).coerceAtLeast(4f)),
                style = TextStyle(color = gridTextColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            )
        }
        curLon += step
    }
}

private fun DrawScope.drawSafeText(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    topLeft: Offset,
    style: TextStyle
) {
    val x = topLeft.x
    val y = topLeft.y
    val w = size.width
    val h = size.height

    if (x in 0f..(w - 10f) && y in 0f..(h - 10f)) {
        try {
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                topLeft = Offset(x, y),
                style = style
            )
        } catch (e: Exception) {
            // Guard against edge-case layout constraint violations
        }
    }
}
