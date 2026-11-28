package com.geotrace.navigation

import android.content.Context
import android.util.Log
import com.geotrace.BuildConfig
import com.geotrace.data.cache.OfflineCacheRepository
import com.geotrace.data.cache.OfflineRoutingPackEntity
import com.geotrace.model.OfflineMapRegion
import com.geotrace.service.OfflineMapManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class OfflineRoutingPackManager(
    context: Context,
    private val cacheRepository: OfflineCacheRepository
) {

    companion object {
        private const val TAG = "OfflineRoutingPackMgr"
        private const val PACK_DIR_NAME = "offline_routing"
        private const val USER_AGENT = "GeoTrace-GIS-FieldApp/${BuildConfig.VERSION_NAME} (Android)"
        private const val TILE_SPAN_DEG = 0.11
        private const val MAX_REGION_HALF_SPAN_DEG = 0.18 // ~40km x 40km road pack cap per region
        private const val PACK_MATCH_BUFFER_DEG = 0.025  // ~2.7km snap margin outside pack border

        private val HIGHWAY_REGEX =
            "^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|track|path|cycleway|footway|pedestrian|steps)$"
    }

    private val appContext = context.applicationContext
    private val packDir: File = File(appContext.filesDir, PACK_DIR_NAME).apply { mkdirs() }
    private val ioMutex = Mutex()
    private val graphCacheMutex = Mutex()

    @Volatile
    private var cachedGraphKey: String? = null
    @Volatile
    private var cachedGraph: OsmRoadGraphData? = null

    val packsFlow = cacheRepository.observeOfflineRoutingPacks()

    private val _indexingRegionIds = MutableStateFlow<Set<String>>(emptySet())
    val indexingRegionIds: StateFlow<Set<String>> = _indexingRegionIds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _totalPacksSizeMb = MutableStateFlow(computeTotalSizeMbSync())
    val totalPacksSizeMb: StateFlow<Float> = _totalPacksSizeMb.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    suspend fun refreshTotalSizeAsync() = withContext(Dispatchers.IO) {
        _totalPacksSizeMb.value = computeTotalSizeMbSync()
    }

    suspend fun buildOrRefreshPackForRegion(region: OfflineMapRegion): Boolean = withContext(Dispatchers.IO) {
        if (region.id in _indexingRegionIds.value) return@withContext false
        _indexingRegionIds.update { it + region.id }
        try {
            _statusMessage.value = "Indexing offline roads for '${region.name}'..."
            val (queryWindows, effectiveBounds) = buildQueryWindows(region)
            if (queryWindows.isEmpty()) {
                _statusMessage.value = "Could not determine valid bounds for '${region.name}' road pack"
                return@withContext false
            }

            val subGraphs = mutableListOf<OsmRoadGraphData>()
            var anySuccess = false
            for (win in queryWindows) {
                val antimeridianParts = OfflineMapManager.splitBoxAtAntimeridian(win)
                for (box in antimeridianParts) {
                    val bboxStr = String.format(
                        Locale.US,
                        "%.5f,%.5f,%.5f,%.5f",
                        box.south,
                        box.west,
                        box.north,
                        box.east
                    )
                    val query = """
                        [out:json][timeout:30];
                        (
                          way["highway"~"$HIGHWAY_REGEX"]($bboxStr);
                        );
                        out body;
                        >;
                        out skel qt;
                    """.trimIndent()
                    val raw = postOverpassQuery(query)
                    if (raw != null) {
                        anySuccess = true
                        val parsed = OsmRoadGraphCodec.parseOverpassHighwayJson(raw)
                        if (!parsed.isEmpty) {
                            subGraphs.add(parsed)
                        }
                    }
                }
            }

            if (!anySuccess) {
                _statusMessage.value = "Road download failed for '${region.name}' (Overpass unavailable or offline)"
                return@withContext false
            }

            val merged = OsmRoadGraphCodec.mergeGraphs(subGraphs)
            if (merged.isEmpty) {
                _statusMessage.value = "No routable OSM roads found inside '${region.name}'"
                return@withContext false
            }

            ioMutex.withLock {
                packDir.mkdirs()
                val outFile = File(packDir, "${region.id}.gtroad")
                val tmpFile = File(packDir, "${region.id}.gtroad.tmp")
                FileOutputStream(tmpFile).use { fos ->
                    OsmRoadGraphCodec.writeBinary(merged, fos)
                }
                if (outFile.exists()) outFile.delete()
                if (!tmpFile.renameTo(outFile)) {
                    tmpFile.copyTo(outFile, overwrite = true)
                    tmpFile.delete()
                }

                val nowMs = System.currentTimeMillis()
                cacheRepository.saveOfflineRoutingPack(
                    OfflineRoutingPackEntity(
                        regionId = region.id,
                        regionName = region.name,
                        northLat = effectiveBounds.latNorth,
                        southLat = effectiveBounds.latSouth,
                        eastLon = effectiveBounds.lonEast,
                        westLon = effectiveBounds.lonWest,
                        vertexCount = merged.vertices.size,
                        edgeCount = merged.edges.size,
                        fileSizeBytes = outFile.length(),
                        filePath = outFile.absolutePath,
                        updatedAtMs = nowMs
                    )
                )
            }
            invalidateGraphCache()
            _totalPacksSizeMb.value = computeTotalSizeMbSync()
            _statusMessage.value = "Offline road pack ready for '${region.name}' (${merged.edges.size} road segments)"
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build road pack for ${region.name}: ${e.message}", e)
            _statusMessage.value = "Road pack build failed for '${region.name}': ${e.message?.take(60)}"
            false
        } finally {
            _indexingRegionIds.update { it - region.id }
        }
    }

    suspend fun deletePack(regionId: String) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            try {
                File(packDir, "$regionId.gtroad").delete()
            } catch (_: Exception) {}
            cacheRepository.deleteOfflineRoutingPack(regionId)
        }
        invalidateGraphCache()
        _totalPacksSizeMb.value = computeTotalSizeMbSync()
    }

    suspend fun clearAllPacks() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            try {
                packDir.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            } catch (_: Exception) {}
            cacheRepository.clearAllOfflineRoutingPacks()
        }
        invalidateGraphCache()
        _totalPacksSizeMb.value = computeTotalSizeMbSync()
    }

    suspend fun hasPackCovering(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Boolean = withContext(Dispatchers.IO) {
        val packs = cacheRepository.getOfflineRoutingPacksSnapshot().filter { File(it.filePath).exists() }
        if (packs.isEmpty()) return@withContext false
        packs.any { pointNearPack(lat1, lon1, it) } && packs.any { pointNearPack(lat2, lon2, it) }
    }

    suspend fun computeOfflineRoutes(
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        includeAlternatives: Boolean = true
    ): Result<List<NavRoute>> = withContext(Dispatchers.IO) {
        val allPacks = cacheRepository.getOfflineRoutingPacksSnapshot()
        if (allPacks.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No offline road graph packs downloaded yet"))
        }

        val reqNorth = max(originLat, destinationLat) + PACK_MATCH_BUFFER_DEG
        val reqSouth = min(originLat, destinationLat) - PACK_MATCH_BUFFER_DEG
        val reqEast = max(originLon, destinationLon) + PACK_MATCH_BUFFER_DEG
        val reqWest = min(originLon, destinationLon) - PACK_MATCH_BUFFER_DEG

        val matchingPacks = allPacks.filter { pack ->
            val fileExists = File(pack.filePath).exists()
            if (!fileExists) return@filter false
            val intersectsTrip = !(pack.southLat > reqNorth ||
                pack.northLat < reqSouth ||
                pack.westLon > reqEast ||
                pack.eastLon < reqWest)
            intersectsTrip || pointNearPack(originLat, originLon, pack) || pointNearPack(destinationLat, destinationLon, pack)
        }

        if (matchingPacks.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No downloaded offline road pack covers this area. Open Offline Maps to download roads.")
            )
        }

        val cacheKey = matchingPacks.sortedBy { it.regionId }.joinToString("|") { "${it.regionId}:${it.updatedAtMs}" }
        val graph = graphCacheMutex.withLock {
            if (cachedGraphKey == cacheKey && cachedGraph != null) {
                cachedGraph!!
            } else {
                val loaded = matchingPacks.mapNotNull { entity ->
                    try {
                        FileInputStream(File(entity.filePath)).use { fis ->
                            OsmRoadGraphCodec.readBinary(fis)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Corrupt .gtroad file for ${entity.regionId}: ${e.message}")
                        null
                    }
                }
                val merged = OsmRoadGraphCodec.mergeGraphs(loaded)
                cachedGraphKey = cacheKey
                cachedGraph = merged
                merged
            }
        }

        if (graph.isEmpty) {
            return@withContext Result.failure(IllegalStateException("Loaded offline road pack contains no routable segments"))
        }

        LocalOsmGraphRouter.route(
            graph = graph,
            originLat = originLat,
            originLon = originLon,
            destinationName = destinationName,
            destinationLat = destinationLat,
            destinationLon = destinationLon,
            profile = profile,
            includeAlternatives = includeAlternatives
        )
    }

    private fun invalidateGraphCache() {
        cachedGraphKey = null
        cachedGraph = null
    }

    private fun pointNearPack(lat: Double, lon: Double, pack: OfflineRoutingPackEntity): Boolean {
        return lat in (pack.southLat - PACK_MATCH_BUFFER_DEG)..(pack.northLat + PACK_MATCH_BUFFER_DEG) &&
            lon in (pack.westLon - PACK_MATCH_BUFFER_DEG)..(pack.eastLon + PACK_MATCH_BUFFER_DEG)
    }

    private fun buildQueryWindows(region: OfflineMapRegion): Pair<List<OfflineMapManager.CorridorTileBox>, BoundingBox> {
        if (region.isCorridor && region.corridorBoxes.isNotEmpty()) {
            val rawBoxes = region.corridorBoxes.map {
                OfflineMapManager.CorridorTileBox(
                    north = (it.north + 0.004).coerceAtMost(85.0),
                    south = (it.south - 0.004).coerceAtLeast(-85.0),
                    east = it.east + 0.004,
                    west = it.west - 0.004
                )
            }
            val stride = ((rawBoxes.size + 5) / 6).coerceAtLeast(1)
            val windows = rawBoxes.chunked(stride).mapNotNull { OfflineMapManager.unionBoundingBox(it) }
            val overall = BoundingBox(region.northLat, region.eastLng, region.southLat, region.westLng)
            return windows to overall
        }

        val cLat = (region.northLat + region.southLat) * 0.5
        val cLon = (region.eastLng + region.westLng) * 0.5
        val clampedNorth = min(region.northLat, cLat + MAX_REGION_HALF_SPAN_DEG).coerceAtMost(85.0)
        val clampedSouth = max(region.southLat, cLat - MAX_REGION_HALF_SPAN_DEG).coerceAtLeast(-85.0)
        val clampedEast = min(region.eastLng, cLon + MAX_REGION_HALF_SPAN_DEG)
        val clampedWest = max(region.westLng, cLon - MAX_REGION_HALF_SPAN_DEG)

        val latSpan = (clampedNorth - clampedSouth).coerceAtLeast(0.005)
        val lonSpan = (clampedEast - clampedWest).coerceAtLeast(0.005)
        val latSteps = ceil(latSpan / TILE_SPAN_DEG).toInt().coerceIn(1, 3)
        val lonSteps = ceil(lonSpan / TILE_SPAN_DEG).toInt().coerceIn(1, 3)

        val windows = mutableListOf<OfflineMapManager.CorridorTileBox>()
        val dLat = latSpan / latSteps
        val dLon = lonSpan / lonSteps
        for (i in 0 until latSteps) {
            val s = clampedSouth + i * dLat
            val n = if (i == latSteps - 1) clampedNorth else s + dLat
            for (j in 0 until lonSteps) {
                val w = clampedWest + j * dLon
                val e = if (j == lonSteps - 1) clampedEast else w + dLon
                windows.add(OfflineMapManager.CorridorTileBox(north = n, south = s, east = e, west = w))
            }
        }
        return windows to BoundingBox(clampedNorth, clampedEast, clampedSouth, clampedWest)
    }

    private suspend fun postOverpassQuery(overpassQuery: String): String? {
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
        )
        var shouldBackoff = false
        for (endpoint in endpoints) {
            if (shouldBackoff) {
                delay(900L)
                shouldBackoff = false
            }
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 32_000
                    doOutput = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write("data=" + URLEncoder.encode(overpassQuery, "UTF-8"))
                    writer.flush()
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                } else {
                    Log.w(TAG, "Overpass road mirror $endpoint returned HTTP $code")
                    if (code == 429 || code == 503 || code == 504) {
                        shouldBackoff = true
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Overpass road mirror $endpoint failed: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun computeTotalSizeMbSync(): Float {
        return try {
            if (!packDir.exists()) return 0f
            val totalBytes = packDir.listFiles()?.filter { it.isFile && it.name.endsWith(".gtroad") }
                ?.sumOf { it.length() } ?: 0L
            totalBytes / (1024f * 1024f)
        } catch (_: Exception) {
            0f
        }
    }
}
