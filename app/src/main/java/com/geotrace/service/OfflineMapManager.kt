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

    companion object {
        private const val TAG = "OfflineMapManager"
        const val MAX_TILES_PER_DOWNLOAD = 2500
        const val MAX_CACHE_BYTES_MB = 512
        private const val NOTIFICATION_CHANNEL_ID = "offline_maps_channel"
        private const val NOTIFICATION_ID = 4102
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
                            // Even if a few edge tiles 404/timeout, save partial region if most succeeded
                            if (errors < max(1, baseCount / 3)) {
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
                            if (errors < max(1, overlayCount / 2)) {
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
        try {
            activeCacheManager?.cancelAllJobs()
        } catch (_: Exception) {}
        // Avoid detaching SqlTileWriter immediately while osmdroid workers wind down; let GC/callback finalize safely
        finishWithError("Download cancelled.", detachWriter = false)
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
