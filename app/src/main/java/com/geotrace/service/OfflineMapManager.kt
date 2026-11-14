package com.geotrace.service

import android.content.Context
import android.util.Log
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineDownloadProgress
import com.geotrace.model.OfflineMapRegion
import com.geotrace.ui.components.MapTileSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.BoundingBox
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max

/**
 * Google Maps-style offline region downloader backed by osmdroid's CacheManager and SqlTileWriter.
 * Downloads map tiles into persistent internal app storage (`files/osmdroid/tiles`) so Satellite,
 * Hybrid, Humanitarian, and Topographic basemaps work seamlessly with zero mobile data.
 */
class OfflineMapManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineMapManager"
        const val MAX_TILES_PER_DOWNLOAD = 2500
    }

    private val _downloadProgress = MutableStateFlow(OfflineDownloadProgress())
    val downloadProgress: StateFlow<OfflineDownloadProgress> = _downloadProgress.asStateFlow()

    private val _cacheSizeMb = MutableStateFlow(computeCacheSizeMb())
    val cacheSizeMb: StateFlow<Float> = _cacheSizeMb.asStateFlow()

    @Volatile
    private var activeCacheManager: CacheManager? = null

    @Volatile
    private var activeTileWriter: SqlTileWriter? = null

    @Volatile
    private var isCancelled = false

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

    fun estimateRegionTilesAndMb(
        box: BoundingBox,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int
    ): Pair<Int, Float> {
        val safeMin = minZoom.coerceIn(4, 17)
        val safeMax = maxZoom.coerceIn(safeMin, 18)
        val baseTiles = CacheManager.getTilesCoverage(box, safeMin, safeMax).size
        val totalTiles = if (tileType == MapTileType.HYBRID) baseTiles * 2 else baseTiles
        val kbPerTile = when (tileType) {
            MapTileType.SATELLITE -> 42f
            MapTileType.HYBRID -> 28f // average across imagery (~42KB) + labels (~14KB)
            MapTileType.TOPO -> 28f
            MapTileType.HUMANITARIAN, MapTileType.STANDARD -> 18f
        }
        val estimatedMb = (totalTiles * kbPerTile) / 1024f
        return totalTiles to estimatedMb
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
                        _downloadProgress.value = _downloadProgress.value.copy(
                            completedTiles = progress.coerceIn(0, estTiles),
                            currentZoom = currentZoomLevel
                        )
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
                        _downloadProgress.value = _downloadProgress.value.copy(
                            completedTiles = (baseCount + progress).coerceIn(0, totalTiles),
                            currentZoom = currentZoomLevel
                        )
                    }

                    override fun downloadStarted() {}
                    override fun setPossibleTilesInArea(total: Int) {}
                    override fun onTaskFailed(errors: Int) {
                        if (isCancelled) {
                            finishWithError("Download cancelled.")
                        } else {
                            completeSuccess(cleanName, box, effectiveType, minZoom, maxZoom, totalTiles, estMb, onCompleted)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            completeSuccess(cleanName, box, effectiveType, minZoom, maxZoom, totalTiles, estMb, onCompleted)
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
        cleanupActiveHandles()
        refreshCacheSize()
        _downloadProgress.value = OfflineDownloadProgress(isDownloading = false)
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

    private fun finishWithError(msg: String) {
        cleanupActiveHandles()
        refreshCacheSize()
        _downloadProgress.value = OfflineDownloadProgress(
            isDownloading = false,
            errorMessage = msg
        )
    }

    fun cancelActiveDownload() {
        isCancelled = true
        try {
            activeCacheManager?.cancelAllJobs()
        } catch (_: Exception) {}
        finishWithError("Download cancelled.")
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

    private fun cleanupActiveHandles() {
        activeCacheManager = null
        try {
            activeTileWriter?.onDetach()
        } catch (_: Exception) {}
        activeTileWriter = null
    }
}
