package com.geotrace.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineDownloadProgress
import com.geotrace.model.OfflineMapRegion
import com.geotrace.ui.components.MapTileSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.BoundingBox
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * Google Maps-style offline region downloader backed by osmdroid's CacheManager and SqlTileWriter.
 * Downloads map tiles into persistent internal app storage (`files/osmdroid/tiles`) so Satellite,
 * Hybrid, Humanitarian, and Topographic basemaps work seamlessly with zero mobile data.
 */
class OfflineMapManager(private val context: Context) {

    data class CorridorTileBox(
        val north: Double,
        val south: Double,
        val east: Double,
        val west: Double
    )

    companion object {
        private const val TAG = "OfflineMapManager"
        const val MAX_TILES_PER_DOWNLOAD = 2500
        const val MAX_CACHE_BYTES_MB = 512
        private const val NOTIFICATION_CHANNEL_ID = "offline_maps_channel"
        private const val NOTIFICATION_ID = 4102

        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
            return r * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        }

        private fun latToTileYStatic(latDeg: Double, n: Long): Long {
            val latRad = Math.toRadians(latDeg)
            val merc = ln(tan(latRad) + 1.0 / cos(latRad))
            val y = floor((1.0 - merc / PI) / 2.0 * n).toLong()
            return y.coerceIn(0L, n - 1L)
        }

        fun normalizeLongitude(lon: Double): Double {
            if (!lon.isFinite()) return 0.0
            var wrapped = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            if (wrapped == -180.0 && lon > 0.0) wrapped = 180.0
            return wrapped
        }

        fun splitBoxAtAntimeridian(box: CorridorTileBox): List<CorridorTileBox> {
            return if (box.west <= box.east) {
                listOf(box)
            } else {
                listOf(
                    CorridorTileBox(north = box.north, south = box.south, east = 180.0, west = box.west),
                    CorridorTileBox(north = box.north, south = box.south, east = box.east, west = -180.0)
                )
            }
        }

        /**
         * Samples a polyline at `spacingMeters` intervals (plus start & end) and builds
         * overlapping ±halfWidthKm square corridor boxes along the route.
         * When `spacingMeters <= 0`, automatically scales spacing to `max(1000m, halfWidth * 1.4)`
         * to minimize tile overlap while guaranteeing corner-free coverage.
         */
        fun computeCorridorBoxes(
            points: List<LocationPoint>,
            halfWidthKm: Double = 1.5,
            spacingMeters: Double = -1.0
        ): List<CorridorTileBox> {
            if (points.isEmpty()) return emptyList()
            val safeHalfKm = halfWidthKm.coerceIn(0.3, 15.0)
            val resolvedSpacing = if (spacingMeters > 0.0) {
                spacingMeters.coerceAtLeast(250.0)
            } else {
                maxOf(1000.0, safeHalfKm * 1000.0 * 1.4)
            }
            val sampled = mutableListOf<LocationPoint>()
            var lastKept: LocationPoint? = null
            for (pt in points) {
                if (lastKept == null || haversineMeters(lastKept.latitude, lastKept.longitude, pt.latitude, pt.longitude) >= resolvedSpacing) {
                    sampled.add(pt)
                    lastKept = pt
                }
            }
            val endPt = points.last()
            if (sampled.lastOrNull() !== endPt) {
                sampled.add(endPt)
            }

            return sampled.map { pt ->
                val latDelta = safeHalfKm / 111.32
                val cosLat = cos(Math.toRadians(pt.latitude)).coerceAtLeast(0.15)
                val lngDelta = (safeHalfKm / (111.32 * cosLat)).coerceAtMost(179.0)
                CorridorTileBox(
                    north = (pt.latitude + latDelta).coerceAtMost(85.0),
                    south = (pt.latitude - latDelta).coerceAtLeast(-85.0),
                    east = normalizeLongitude(pt.longitude + lngDelta),
                    west = normalizeLongitude(pt.longitude - lngDelta)
                )
            }
        }

        fun unionBoundingBox(boxes: List<CorridorTileBox>): CorridorTileBox? {
            if (boxes.isEmpty()) return null
            var n = -90.0
            var s = 90.0
            for (b in boxes) {
                if (b.north > n) n = b.north
                if (b.south < s) s = b.south
            }
            val crossesAntimeridian = boxes.any { it.west > it.east }
            if (!crossesAntimeridian) {
                var e = -180.0
                var w = 180.0
                for (b in boxes) {
                    if (b.east > e) e = b.east
                    if (b.west < w) w = b.west
                }
                return CorridorTileBox(north = n, south = s, east = e, west = w)
            }
            // Shift longitudes onto [0, 360) for antimeridian-crossing unions
            var minShifted = 360.0
            var maxShifted = 0.0
            for (b in boxes) {
                val wS = if (b.west < 0.0) b.west + 360.0 else b.west
                val eS = if (b.east < 0.0) b.east + 360.0 else b.east
                if (wS < minShifted) minShifted = wS
                if (eS > maxShifted) maxShifted = eS
            }
            return CorridorTileBox(
                north = n,
                south = s,
                east = normalizeLongitude(maxShifted),
                west = normalizeLongitude(minShifted)
            )
        }

        fun kbPerTileFor(tileType: MapTileType): Float = when (tileType) {
            MapTileType.SATELLITE -> 42f
            MapTileType.HYBRID -> 28f
            MapTileType.TOPO -> 28f
            MapTileType.HUMANITARIAN, MapTileType.STANDARD -> 18f
        }

        /**
         * Estimates exact deduplicated tile count across route corridor boxes using packed 64-bit `(z, x, y)` keys.
         * Supports boxes crossing the ±180° antimeridian.
         * Returns Triple(totalTilesIncludingOverlay, estimatedMb, corridorBoxes).
         */
        fun estimateCorridorTilesAndMb(
            points: List<LocationPoint>,
            halfWidthKm: Double,
            tileType: MapTileType,
            minZoom: Int,
            maxZoom: Int
        ): Triple<Int, Float, List<CorridorTileBox>> {
            val boxes = computeCorridorBoxes(points, halfWidthKm = halfWidthKm)
            if (boxes.isEmpty()) return Triple(0, 0f, emptyList())

            val safeMin = minZoom.coerceIn(4, 17)
            val safeMax = maxZoom.coerceIn(safeMin, 18)
            val seenKeys = HashSet<Long>()
            val cap = 100_000

            outer@ for (box in boxes) {
                val north = box.north.coerceIn(-85.0511, 85.0511)
                val south = box.south.coerceIn(-85.0511, 85.0511)
                val west = box.west.coerceIn(-180.0, 180.0)
                val east = box.east.coerceIn(-180.0, 180.0)
                if (north <= south || west == east) continue

                for (z in safeMin..safeMax) {
                    val n = 1L shl z
                    val xMin = floor(((west + 180.0) / 360.0) * n).toLong().coerceIn(0L, n - 1L)
                    val xMax = floor(((east + 180.0) / 360.0) * n).toLong().coerceIn(0L, n - 1L)
                    val yMin = latToTileYStatic(north, n)
                    val yMax = latToTileYStatic(south, n)
                    val yLow = minOf(yMin, yMax)
                    val yHigh = maxOf(yMin, yMax)

                    if (west <= east) {
                        for (x in xMin..xMax) {
                            for (y in yLow..yHigh) {
                                val key = (z.toLong() shl 52) or (x shl 26) or y
                                seenKeys.add(key)
                                if (seenKeys.size >= cap) break@outer
                            }
                        }
                    } else {
                        for (x in xMin until n) {
                            for (y in yLow..yHigh) {
                                val key = (z.toLong() shl 52) or (x shl 26) or y
                                seenKeys.add(key)
                                if (seenKeys.size >= cap) break@outer
                            }
                        }
                        for (x in 0L..xMax) {
                            for (y in yLow..yHigh) {
                                val key = (z.toLong() shl 52) or (x shl 26) or y
                                seenKeys.add(key)
                                if (seenKeys.size >= cap) break@outer
                            }
                        }
                    }
                }
            }

            val baseCount = seenKeys.size.toLong()
            val totalTiles = if (tileType == MapTileType.HYBRID) {
                (baseCount * 2L).coerceAtMost(1_000_000L).toInt()
            } else {
                baseCount.coerceAtMost(1_000_000L).toInt()
            }
            val estimatedMb = (totalTiles * kbPerTileFor(tileType)) / 1024f
            return Triple(totalTiles, estimatedMb, boxes)
        }
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _downloadProgress = MutableStateFlow(OfflineDownloadProgress())
    val downloadProgress: StateFlow<OfflineDownloadProgress> = _downloadProgress.asStateFlow()

    private val _cacheSizeMb = MutableStateFlow(0f)
    val cacheSizeMb: StateFlow<Float> = _cacheSizeMb.asStateFlow()

    @Volatile
    private var activeCacheManager: CacheManager? = null

    @Volatile
    private var activeTileWriter: SqlTileWriter? = null

    @Volatile
    private var isCancelled = false

    private val completionDispatched = AtomicBoolean(false)

    @Volatile
    private var downloadWakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var lastNotificationTimeMs = 0L

    @Volatile
    private var lastNotifiedPercent = -1

    fun computeBoundingBoxAround(lat: Double, lng: Double, radiusKm: Double): BoundingBox {
        val latDelta = radiusKm / 111.32
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.15)
        val lngDelta = radiusKm / (111.32 * cosLat)
        return BoundingBox(
            (lat + latDelta).coerceAtMost(85.0),
            (lng + lngDelta).coerceAtMost(180.0),
            (lat - latDelta).coerceAtLeast(-85.0),
            (lng - lngDelta).coerceAtLeast(-180.0)
        )
    }

    /**
     * Computes exact Web Mercator tile count in O(zoomLevels) arithmetic without allocating
     * millions of tile indices (preventing OOM crashes when zoomed out to continental scale).
     */
    fun estimateRegionTilesAndMb(
        box: BoundingBox,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int
    ): Pair<Int, Float> {
        if (box.latNorth <= box.latSouth || box.lonEast == box.lonWest) {
            return 0 to 0f
        }
        val safeMin = minZoom.coerceIn(4, 17)
        val safeMax = maxZoom.coerceIn(safeMin, 18)
        val north = box.latNorth.coerceIn(-85.0511, 85.0511)
        val south = box.latSouth.coerceIn(-85.0511, 85.0511)
        val west = box.lonWest.coerceIn(-180.0, 180.0)
        val east = box.lonEast.coerceIn(-180.0, 180.0)

        var baseTilesLong = 0L
        for (z in safeMin..safeMax) {
            val n = 1L shl z
            val xMin = floor(((west + 180.0) / 360.0) * n).toLong().coerceIn(0L, n - 1L)
            val xMax = floor(((east + 180.0) / 360.0) * n).toLong().coerceIn(0L, n - 1L)
            val dx = if (west <= east) {
                (xMax - xMin + 1L)
            } else {
                (n - xMin) + (xMax + 1L)
            }

            val yMin = latToTileY(north, n)
            val yMax = latToTileY(south, n)
            val dy = (yMax - yMin + 1L).coerceAtLeast(1L)

            baseTilesLong = (baseTilesLong + (dx * dy)).coerceAtMost(1_000_000L)
            if (baseTilesLong >= 1_000_000L) break
        }

        val totalTiles = if (tileType == MapTileType.HYBRID) {
            (baseTilesLong * 2L).coerceAtMost(1_000_000L).toInt()
        } else {
            baseTilesLong.toInt()
        }
        val kbPerTile = when (tileType) {
            MapTileType.SATELLITE -> 42f
            MapTileType.HYBRID -> 28f // average across imagery (~42KB) + labels (~14KB)
            MapTileType.TOPO -> 28f
            MapTileType.HUMANITARIAN, MapTileType.STANDARD -> 18f
        }
        val estimatedMb = (totalTiles * kbPerTile) / 1024f
        return totalTiles to estimatedMb
    }

    private fun latToTileY(latDeg: Double, n: Long): Long {
        val latRad = Math.toRadians(latDeg)
        val merc = ln(tan(latRad) + 1.0 / cos(latRad))
        val y = floor((1.0 - merc / PI) / 2.0 * n).toLong()
        return y.coerceIn(0L, n - 1L)
    }

    fun startRegionDownload(
        name: String,
        box: BoundingBox,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int,
        onCompleted: (OfflineMapRegion) -> Unit
    ) {
        if (_downloadProgress.value.isDownloading) return

        val effectiveType = if (tileType.supportsOfflineBulkDownload) tileType else MapTileType.HUMANITARIAN
        val safeMin = minZoom.coerceIn(4, 17)
        val safeMax = maxZoom.coerceIn(safeMin, 18)
        val (estTiles, estMb) = estimateRegionTilesAndMb(box, effectiveType, safeMin, safeMax)

        if (estTiles <= 0) {
            _downloadProgress.value = OfflineDownloadProgress(
                isDownloading = false,
                errorMessage = "Selected area contains no valid tiles."
            )
            return
        }

        if (estTiles > MAX_TILES_PER_DOWNLOAD) {
            _downloadProgress.value = OfflineDownloadProgress(
                isDownloading = false,
                errorMessage = "Region too large ($estTiles tiles). Zoom in closer or reduce detail (max $MAX_TILES_PER_DOWNLOAD tiles)."
            )
            return
        }

        isCancelled = false
        completionDispatched.set(false)
        lastNotificationTimeMs = 0L
        lastNotifiedPercent = -1
        acquireDownloadWakeLock()
        val cleanName = name.trim().ifBlank { "${effectiveType.label} Area" }

        val tileWriter = SqlTileWriter()
        activeTileWriter = tileWriter
        val baseSource = MapTileSources.baseTileSourceFor(effectiveType)

        _downloadProgress.value = OfflineDownloadProgress(
            isDownloading = true,
            regionName = cleanName,
            completedTiles = 0,
            totalTiles = estTiles,
            currentZoom = safeMin,
            errorMessage = null
        )
        postProgressNotification(cleanName, 0, estTiles, force = true)

        try {
            val baseCount = if (effectiveType == MapTileType.HYBRID) estTiles / 2 else estTiles
            val manager = CacheManager(baseSource, tileWriter, safeMin, safeMax)
            activeCacheManager = manager

            manager.downloadAreaAsyncNoUI(
                context,
                box,
                safeMin,
                safeMax,
                object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        if (isCancelled) {
                            finishWithError("Download cancelled.")
                            return
                        }
                        val overlaySource = MapTileSources.overlayTileSourceFor(effectiveType)
                        if (overlaySource != null) {
                            startOverlayPass(
                                overlaySource = overlaySource,
                                tileWriter = tileWriter,
                                box = box,
                                minZoom = safeMin,
                                maxZoom = safeMax,
                                baseCount = baseCount,
                                totalTiles = estTiles,
                                cleanName = cleanName,
                                effectiveType = effectiveType,
                                estMb = estMb,
                                onCompleted = onCompleted
                            )
                        } else {
                            completeSuccess(cleanName, box, effectiveType, safeMin, safeMax, estTiles, estMb, onCompleted)
                        }
                    }

                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                        val clamped = progress.coerceIn(0, estTiles)
                        _downloadProgress.value = _downloadProgress.value.copy(
                            completedTiles = clamped,
                            currentZoom = currentZoomLevel
                        )
                        postProgressNotification(cleanName, clamped, estTiles)
                    }

                    override fun downloadStarted() {}

                    override fun setPossibleTilesInArea(total: Int) {}

                    override fun onTaskFailed(errors: Int) {
                        if (isCancelled) {
                            finishWithError("Download cancelled.")
                        } else {
                            // Fail when more than ~12.5% of base tiles fail to download
                            if (errors <= max(2, baseCount / 8)) {
                                completeSuccess(cleanName, box, effectiveType, safeMin, safeMax, estTiles, estMb, onCompleted)
                            } else {
                                finishWithError("Download failed ($errors tiles could not be fetched). Check network connection.")
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CacheManager: ${e.message}", e)
            finishWithError(e.localizedMessage ?: "Offline map download failed.")
        }
    }

    /**
     * Downloads tiles strictly along a trail/route corridor strip (±halfWidthKm buffer) to save
     * significant storage vs a wide rectangular bounding box. Supports Hybrid two-pass labels automatically.
     */
    fun startCorridorDownload(
        name: String,
        points: List<LocationPoint>,
        halfWidthKm: Double = 1.5,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int,
        onCompleted: (OfflineMapRegion) -> Unit
    ) {
        if (_downloadProgress.value.isDownloading) return

        val effectiveType = if (tileType.supportsOfflineBulkDownload) tileType else MapTileType.HUMANITARIAN
        val safeMin = minZoom.coerceIn(4, 17)
        val safeMax = maxZoom.coerceIn(safeMin, 18)
        val (estTiles, estMb, rawBoxes) = estimateCorridorTilesAndMb(
            points = points,
            halfWidthKm = halfWidthKm,
            tileType = effectiveType,
            minZoom = safeMin,
            maxZoom = safeMax
        )

        if (estTiles <= 0 || rawBoxes.isEmpty()) {
            _downloadProgress.value = OfflineDownloadProgress(
                isDownloading = false,
                errorMessage = "Route corridor contains no valid tiles."
            )
            return
        }

        if (estTiles > MAX_TILES_PER_DOWNLOAD) {
            _downloadProgress.value = OfflineDownloadProgress(
                isDownloading = false,
                errorMessage = "Corridor too detailed ($estTiles tiles). Lower max zoom (max $MAX_TILES_PER_DOWNLOAD tiles)."
            )
            return
        }

        val overallUnion = unionBoundingBox(rawBoxes) ?: return
        val overallBox = BoundingBox(overallUnion.north, overallUnion.east, overallUnion.south, overallUnion.west)

        isCancelled = false
        completionDispatched.set(false)
        lastNotificationTimeMs = 0L
        lastNotifiedPercent = -1
        acquireDownloadWakeLock()
        val cleanName = name.trim().ifBlank { "Route Corridor (${effectiveType.label})" }

        val tileWriter = SqlTileWriter()
        activeTileWriter = tileWriter
        val baseSource = MapTileSources.baseTileSourceFor(effectiveType)
        val overlaySource = MapTileSources.overlayTileSourceFor(effectiveType)

        data class DownloadPassJob(
            val source: org.osmdroid.tileprovider.tilesource.ITileSource,
            val box: BoundingBox,
            val boxWeightTiles: Int,
            val estimatedBoxTiles: Int
        )

        val splitBoxes = rawBoxes.flatMap { splitBoxAtAntimeridian(it) }
        val jobs = mutableListOf<DownloadPassJob>()
        val perBoxBaseWeights = splitBoxes.map { b ->
            val bb = BoundingBox(b.north, b.east, b.south, b.west)
            estimateRegionTilesAndMb(bb, MapTileType.HUMANITARIAN, safeMin, safeMax).first.coerceAtLeast(1)
        }
        val rawSum = perBoxBaseWeights.sum().coerceAtLeast(1)
        val baseTarget = if (overlaySource != null) estTiles / 2 else estTiles

        splitBoxes.forEachIndexed { idx, b ->
            val bb = BoundingBox(b.north, b.east, b.south, b.west)
            val estBoxTiles = perBoxBaseWeights[idx]
            val scaledWeight = ((estBoxTiles.toDouble() / rawSum) * baseTarget).toInt().coerceAtLeast(1)
            jobs.add(DownloadPassJob(baseSource, bb, scaledWeight, estBoxTiles))
        }
        if (overlaySource != null) {
            splitBoxes.forEachIndexed { idx, b ->
                val bb = BoundingBox(b.north, b.east, b.south, b.west)
                val estBoxTiles = perBoxBaseWeights[idx]
                val scaledWeight = ((estBoxTiles.toDouble() / rawSum) * (estTiles - baseTarget)).toInt().coerceAtLeast(1)
                jobs.add(DownloadPassJob(overlaySource, bb, scaledWeight, estBoxTiles))
            }
        }

        _downloadProgress.value = OfflineDownloadProgress(
            isDownloading = true,
            regionName = cleanName,
            completedTiles = 0,
            totalTiles = estTiles,
            currentZoom = safeMin,
            errorMessage = null
        )
        postProgressNotification(cleanName, 0, estTiles, force = true)

        fun runJob(jobIndex: Int, accumulatedProgress: Int, cumulativeErrors: Int) {
            if (isCancelled) {
                finishWithError("Download cancelled.")
                return
            }
            if (jobIndex >= jobs.size) {
                completeSuccess(cleanName, overallBox, effectiveType, safeMin, safeMax, estTiles, estMb, onCompleted)
                return
            }
            val job = jobs[jobIndex]
            var activeBoxTotal = job.estimatedBoxTiles.coerceAtLeast(1)
            try {
                val mgr = CacheManager(job.source, tileWriter, safeMin, safeMax)
                activeCacheManager = mgr
                mgr.downloadAreaAsyncNoUI(
                    context,
                    job.box,
                    safeMin,
                    safeMax,
                    object : CacheManager.CacheManagerCallback {
                        override fun onTaskComplete() {
                            val nextAccum = (accumulatedProgress + job.boxWeightTiles).coerceIn(0, estTiles)
                            _downloadProgress.value = _downloadProgress.value.copy(completedTiles = nextAccum)
                            postProgressNotification(cleanName, nextAccum, estTiles)
                            runJob(jobIndex + 1, nextAccum, cumulativeErrors)
                        }

                        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                            val scaledBoxProgress = ((progress.toDouble() / activeBoxTotal.toDouble()) * job.boxWeightTiles)
                                .toInt()
                                .coerceIn(0, job.boxWeightTiles)
                            val approx = (accumulatedProgress + scaledBoxProgress).coerceIn(0, estTiles)
                            _downloadProgress.value = _downloadProgress.value.copy(
                                completedTiles = approx,
                                currentZoom = currentZoomLevel
                            )
                            postProgressNotification(cleanName, approx, estTiles)
                        }

                        override fun downloadStarted() {}
                        override fun setPossibleTilesInArea(total: Int) {
                            if (total > 0) activeBoxTotal = total
                        }

                        override fun onTaskFailed(errors: Int) {
                            if (isCancelled) {
                                finishWithError("Download cancelled.")
                                return
                            }
                            val nextErr = cumulativeErrors + errors
                            if (nextErr <= max(4, estTiles / 8)) {
                                val nextAccum = (accumulatedProgress + job.boxWeightTiles).coerceIn(0, estTiles)
                                runJob(jobIndex + 1, nextAccum, nextErr)
                            } else {
                                finishWithError("Corridor download failed ($nextErr tiles could not be fetched). Check network.")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Corridor job $jobIndex error: ${e.message}", e)
                finishWithError(e.localizedMessage ?: "Corridor download failed.")
            }
        }

        runJob(0, 0, 0)
    }

    private fun startOverlayPass(
        overlaySource: org.osmdroid.tileprovider.tilesource.ITileSource,
        tileWriter: SqlTileWriter,
        box: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        baseCount: Int,
        totalTiles: Int,
        cleanName: String,
        effectiveType: MapTileType,
        estMb: Float,
        onCompleted: (OfflineMapRegion) -> Unit
    ) {
        try {
            val overlayMgr = CacheManager(overlaySource, tileWriter, minZoom, maxZoom)
            activeCacheManager = overlayMgr
            overlayMgr.downloadAreaAsyncNoUI(
                context,
                box,
                minZoom,
                maxZoom,
                object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        if (isCancelled) {
                            finishWithError("Download cancelled.")
                            return
                        }
                        completeSuccess(cleanName, box, effectiveType, minZoom, maxZoom, totalTiles, estMb, onCompleted)
                    }

                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                        val clamped = (baseCount + progress).coerceIn(0, totalTiles)
                        _downloadProgress.value = _downloadProgress.value.copy(
                            completedTiles = clamped,
                            currentZoom = currentZoomLevel
                        )
                        postProgressNotification(cleanName, clamped, totalTiles)
                    }

                    override fun downloadStarted() {}
                    override fun setPossibleTilesInArea(total: Int) {}
                    override fun onTaskFailed(errors: Int) {
                        if (isCancelled) {
                            finishWithError("Download cancelled.")
                        } else {
                            val overlayCount = max(1, totalTiles - baseCount)
                            if (errors <= max(2, overlayCount / 5)) {
                                completeSuccess(cleanName, box, effectiveType, minZoom, maxZoom, totalTiles, estMb, onCompleted)
                            } else {
                                finishWithError("Reference labels failed ($errors tiles). Satellite imagery cached; check network to retry.")
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            finishWithError(e.localizedMessage ?: "Overlay pass failed.")
        }
    }

    private fun completeSuccess(
        cleanName: String,
        box: BoundingBox,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int,
        tileCount: Int,
        estMb: Float,
        onCompleted: (OfflineMapRegion) -> Unit
    ) {
        if (!completionDispatched.compareAndSet(false, true)) return
        cleanupActiveHandles(detachWriter = true)
        ioScope.launch { refreshCacheSizeAsync() }
        _downloadProgress.value = OfflineDownloadProgress(isDownloading = false)
        postResultNotification(
            title = "Offline map ready",
            message = "$cleanName ($tileCount tiles) cached for offline use"
        )
        onCompleted(
            OfflineMapRegion(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                northLat = box.latNorth,
                southLat = box.latSouth,
                eastLng = box.lonEast,
                westLng = box.lonWest,
                tileType = tileType,
                minZoom = minZoom,
                maxZoom = maxZoom,
                tileCount = tileCount,
                estimatedSizeMb = estMb,
                downloadedAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun finishWithError(msg: String, detachWriter: Boolean = true) {
        if (!completionDispatched.compareAndSet(false, true)) return
        cleanupActiveHandles(detachWriter = detachWriter)
        ioScope.launch { refreshCacheSizeAsync() }
        _downloadProgress.value = OfflineDownloadProgress(
            isDownloading = false,
            errorMessage = msg
        )
        if (isCancelled) {
            clearDownloadNotification()
        } else {
            postResultNotification("Offline map download paused", msg)
        }
    }

    fun cancelActiveDownload() {
        isCancelled = true
        val pendingWriter = activeTileWriter
        try {
            activeCacheManager?.cancelAllJobs()
        } catch (_: Exception) {}
        finishWithError("Download cancelled.", detachWriter = false)
        if (pendingWriter != null) {
            ioScope.launch {
                kotlinx.coroutines.delay(2500L)
                try {
                    pendingWriter.onDetach()
                } catch (_: Exception) {}
            }
        }
    }

    fun clearDownloadError() {
        if (!_downloadProgress.value.isDownloading) {
            _downloadProgress.value = _downloadProgress.value.copy(errorMessage = null)
        }
    }

    fun clearAllTileCache(): Boolean {
        val writer = SqlTileWriter()
        return try {
            val purged = writer.purgeCache()
            refreshCacheSize()
            purged
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge tile cache: ${e.message}")
            false
        } finally {
            try {
                writer.onDetach()
            } catch (_: Exception) {}
        }
    }

    suspend fun refreshCacheSizeAsync() = withContext(Dispatchers.IO) {
        _cacheSizeMb.value = computeCacheSizeMb()
    }

    fun refreshCacheSize() {
        _cacheSizeMb.value = computeCacheSizeMb()
    }

    private fun computeCacheSizeMb(): Float {
        return try {
            val tileDir = org.osmdroid.config.Configuration.getInstance().osmdroidTileCache
            if (tileDir != null && tileDir.exists()) {
                val bytes = tileDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                bytes / (1024f * 1024f)
            } else {
                0f
            }
        } catch (_: Exception) {
            0f
        }
    }

    private fun cleanupActiveHandles(detachWriter: Boolean = true) {
        activeCacheManager = null
        if (detachWriter) {
            try {
                activeTileWriter?.onDetach()
            } catch (_: Exception) {}
        }
        activeTileWriter = null
        releaseDownloadWakeLock()
    }

    private fun acquireDownloadWakeLock() {
        try {
            if (downloadWakeLock?.isHeld == true) return
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            downloadWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoTrace::OfflineTileDownload").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10-minute safety cap
            }
        } catch (_: Exception) {}
    }

    private fun releaseDownloadWakeLock() {
        try {
            if (downloadWakeLock?.isHeld == true) {
                downloadWakeLock?.release()
            }
        } catch (_: Exception) {}
        downloadWakeLock = null
    }

    private fun ensureNotificationChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Offline Map Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress and completion status for offline map caching"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun postProgressNotification(regionName: String, completed: Int, total: Int, force: Boolean = false) {
        if (!canPostNotifications()) return
        val pct = if (total > 0) ((completed * 100) / total).coerceIn(0, 100) else 0
        val now = System.currentTimeMillis()
        if (!force && (now - lastNotificationTimeMs < 1500L) && (pct - lastNotifiedPercent < 5)) return
        lastNotificationTimeMs = now
        lastNotifiedPercent = pct

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureNotificationChannel(nm)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading offline map ($pct%)")
            .setContentText("$regionName • $completed / $total tiles")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), completed.coerceAtLeast(0), total <= 0)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun postResultNotification(title: String, message: String) {
        if (!canPostNotifications()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureNotificationChannel(nm)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun clearDownloadNotification() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
