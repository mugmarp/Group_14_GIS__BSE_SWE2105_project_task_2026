package com.geotrace

import android.app.Application
import com.geotrace.data.LocationRepository
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineMapRegion
import com.geotrace.service.CellularDiagnosticManager
import com.geotrace.service.OfflineMapManager
import com.geotrace.service.PlaceSearchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox

class LocationTrackerApp : Application() {

    lateinit var offlineCacheRepository: com.geotrace.data.cache.OfflineCacheRepository
        private set

    lateinit var repository: LocationRepository
        private set

    lateinit var cellManager: CellularDiagnosticManager
        private set

    lateinit var groupRepository: com.geotrace.data.GroupRepository
        private set

    lateinit var offlineMapManager: OfflineMapManager
        private set

    lateinit var placeSearchManager: PlaceSearchManager
        private set

    lateinit var routingProvider: com.geotrace.navigation.RoutingProvider
        private set

    lateinit var offlineRoutingPackManager: com.geotrace.navigation.OfflineRoutingPackManager
        private set

    lateinit var voiceGuidanceManager: com.geotrace.navigation.VoiceGuidanceManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _offlinePoiStatus = MutableStateFlow<String?>(null)
    val offlinePoiStatus: StateFlow<String?> = _offlinePoiStatus.asStateFlow()

    private val _poiIndexingRegionIds = MutableStateFlow<Set<String>>(emptySet())
    val poiIndexingRegionIds: StateFlow<Set<String>> = _poiIndexingRegionIds.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        // Initialize OpenStreetMap (osmdroid) configuration with persistent internal tile storage
        val osmCfg = Configuration.getInstance()
        osmCfg.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        val osmBasePath = java.io.File(filesDir, "osmdroid")
        val osmTileCache = java.io.File(osmBasePath, "tiles")
        osmBasePath.mkdirs()
        osmTileCache.mkdirs()
        osmCfg.osmdroidBasePath = osmBasePath
        osmCfg.osmdroidTileCache = osmTileCache
        osmCfg.userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
        osmCfg.tileFileSystemCacheMaxBytes = OfflineMapManager.MAX_CACHE_BYTES_MB * 1024L * 1024L
        osmCfg.tileFileSystemCacheTrimBytes = OfflineMapManager.TRIM_CACHE_BYTES_MB * 1024L * 1024L
        osmCfg.expirationOverrideDuration = 30L * 24L * 60L * 60L * 1000L
        osmCfg.tileDownloadThreads = 2
        osmCfg.tileDownloadMaxQueueSize = 40

        val offlineCacheDb = com.geotrace.data.cache.OfflineCacheDatabase.getInstance(this)
        offlineCacheRepository = com.geotrace.data.cache.OfflineCacheRepository(offlineCacheDb)
        com.geotrace.service.OpenCellIdClient.attachPersistentStore(offlineCacheRepository)

        repository = LocationRepository(this, offlineCacheRepository)
        cellManager = CellularDiagnosticManager(this)
        groupRepository = com.geotrace.data.GroupRepository(this)
        offlineMapManager = OfflineMapManager(this)
        placeSearchManager = PlaceSearchManager(this)
        offlineRoutingPackManager = com.geotrace.navigation.OfflineRoutingPackManager(this, offlineCacheRepository)
        routingProvider = com.geotrace.navigation.HybridPhase2RoutingProvider(this, offlineRoutingPackManager)
        voiceGuidanceManager = com.geotrace.navigation.VoiceGuidanceManager(this)

        // Read real hardware cell towers if present
        val initialTowers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(initialTowers)
    }

    fun clearOfflinePoiStatus() {
        _offlinePoiStatus.value = null
    }

    fun startOfflineRegionDownload(
        name: String,
        box: BoundingBox,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int,
        includeRoads: Boolean = true
    ) {
        _offlinePoiStatus.value = null
        offlineRoutingPackManager.clearStatusMessage()
        offlineMapManager.startRegionDownload(
            name = name,
            box = box,
            tileType = tileType,
            minZoom = minZoom,
            maxZoom = maxZoom,
            onCompleted = { region ->
                _poiIndexingRegionIds.update { it + region.id }
                appScope.launch {
                    try {
                        repository.saveOfflineMapRegion(region)
                        val outcome = placeSearchManager.fetchRegionPoisForOfflineCacheWithStatus(region)
                        if (outcome.pois.isNotEmpty()) {
                            repository.saveOfflinePlaces(region.id, outcome.pois)
                            _offlinePoiStatus.value = "Cached ${outcome.pois.size} offline POIs for '${region.name}'"
                        } else if (!outcome.succeeded) {
                            _offlinePoiStatus.value = "Tiles saved for '${region.name}'; Overpass POI index unavailable offline"
                        } else {
                            _offlinePoiStatus.value = "Tiles saved for '${region.name}' (no mapped OSM POIs in bounds)"
                        }
                        if (includeRoads) {
                            offlineRoutingPackManager.buildOrRefreshPackForRegion(region)
                        }
                    } finally {
                        _poiIndexingRegionIds.update { it - region.id }
                    }
                }
            }
        )
    }

    fun startCorridorOfflineDownload(
        name: String,
        points: List<LocationPoint>,
        halfWidthKm: Double = 1.5,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int,
        includeRoads: Boolean = true
    ) {
        _offlinePoiStatus.value = null
        offlineRoutingPackManager.clearStatusMessage()
        offlineMapManager.startCorridorDownload(
            name = name,
            points = points,
            halfWidthKm = halfWidthKm,
            tileType = tileType,
            minZoom = minZoom,
            maxZoom = maxZoom,
            onCompleted = { region ->
                _poiIndexingRegionIds.update { it + region.id }
                appScope.launch {
                    try {
                        repository.saveOfflineMapRegion(region)
                        val outcome = placeSearchManager.fetchCorridorPoisForOfflineCacheWithStatus(
                            points = points,
                            regionId = region.id,
                            regionName = region.name,
                            halfWidthKm = halfWidthKm
                        )
                        if (outcome.pois.isNotEmpty()) {
                            repository.saveOfflinePlaces(region.id, outcome.pois)
                            _offlinePoiStatus.value = "Cached ${outcome.pois.size} trail corridor POIs for '${region.name}'"
                        } else if (!outcome.succeeded) {
                            _offlinePoiStatus.value = "Corridor tiles saved for '${region.name}'; Overpass POI index unavailable"
                        } else {
                            _offlinePoiStatus.value = "Corridor tiles saved for '${region.name}' (no mapped POIs in strip)"
                        }
                        if (includeRoads) {
                            offlineRoutingPackManager.buildOrRefreshPackForRegion(region)
                        }
                    } finally {
                        _poiIndexingRegionIds.update { it - region.id }
                    }
                }
            }
        )
    }

    fun refreshOfflineRegionPois(region: OfflineMapRegion) {
        if (region.id in _poiIndexingRegionIds.value) return
        _poiIndexingRegionIds.update { it + region.id }
        com.geotrace.service.OfflineDownloadService.start(this)
        appScope.launch {
            try {
                _offlinePoiStatus.value = "Refreshing offline POIs for '${region.name}'..."
                val outcome = if (region.isCorridor && region.corridorBoxes.isNotEmpty()) {
                    placeSearchManager.fetchCorridorBoxesPoisForOfflineCacheWithStatus(region)
                } else {
                    placeSearchManager.fetchRegionPoisForOfflineCacheWithStatus(region)
                }
                if (outcome.pois.isNotEmpty()) {
                    repository.saveOfflinePlaces(region.id, outcome.pois)
                    _offlinePoiStatus.value = "Updated ${outcome.pois.size} offline POIs for '${region.name}'"
                } else if (!outcome.succeeded) {
                    _offlinePoiStatus.value = "Overpass POI refresh failed for '${region.name}' (check internet)"
                } else {
                    _offlinePoiStatus.value = "No mapped OSM POIs found in '${region.name}'"
                }
            } finally {
                _poiIndexingRegionIds.update { it - region.id }
            }
        }
    }

    fun refreshOfflineRegionRoads(region: OfflineMapRegion) {
        if (region.id in offlineRoutingPackManager.indexingRegionIds.value) return
        com.geotrace.service.OfflineDownloadService.start(this)
        appScope.launch {
            offlineRoutingPackManager.buildOrRefreshPackForRegion(region)
        }
    }

    fun cancelOfflineRegionRoads(regionId: String) {
        offlineRoutingPackManager.cancelIndexing(regionId)
    }

    fun startRoadOnlyRegionDownload(
        name: String,
        box: BoundingBox?,
        corridorPoints: List<LocationPoint>?,
        halfWidthKm: Double = 1.5,
        tileType: MapTileType
    ) {
        val isCorridor = corridorPoints != null && corridorPoints.size >= 2
        val rawBoxes = if (isCorridor) {
            OfflineMapManager.computeCorridorBoxes(corridorPoints!!, halfWidthKm = halfWidthKm)
        } else {
            emptyList()
        }
        val overallUnion = if (isCorridor && rawBoxes.isNotEmpty()) {
            OfflineMapManager.unionBoundingBox(rawBoxes)
        } else {
            null
        }
        val finalNorth = overallUnion?.north ?: box?.latNorth ?: return
        val finalSouth = overallUnion?.south ?: box?.latSouth ?: return
        val finalEast = overallUnion?.east ?: box?.lonEast ?: return
        val finalWest = overallUnion?.west ?: box?.lonWest ?: return

        val region = OfflineMapRegion(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { if (isCorridor) "Offline Road Corridor" else "Offline Road Pack" },
            northLat = finalNorth,
            southLat = finalSouth,
            eastLng = finalEast,
            westLng = finalWest,
            tileType = tileType,
            minZoom = 12,
            maxZoom = 14,
            tileCount = 0,
            estimatedSizeMb = 0f,
            downloadedAtMs = System.currentTimeMillis(),
            isCorridor = isCorridor,
            corridorBoxes = rawBoxes.map {
                com.geotrace.model.OfflineCorridorBox(it.north, it.south, it.east, it.west)
            }
        )
        com.geotrace.service.OfflineDownloadService.start(this)
        _poiIndexingRegionIds.update { it + region.id }
        appScope.launch {
            try {
                repository.saveOfflineMapRegion(region)
                offlineRoutingPackManager.buildOrRefreshPackForRegion(region)
                val outcome = if (isCorridor) {
                    placeSearchManager.fetchCorridorBoxesPoisForOfflineCacheWithStatus(region)
                } else {
                    placeSearchManager.fetchRegionPoisForOfflineCacheWithStatus(region)
                }
                if (outcome.pois.isNotEmpty()) {
                    repository.saveOfflinePlaces(region.id, outcome.pois)
                }
            } finally {
                _poiIndexingRegionIds.update { it - region.id }
            }
        }
    }

    fun cacheNavRouteCorridor(
        route: com.geotrace.navigation.NavRoute,
        preferredTileType: MapTileType
    ): String {
        if (route.points.size < 2) return "Route has too few points to cache."
        val safeName = "Route: ${route.destinationName.take(26)}"

        if (preferredTileType.supportsOfflineBulkDownload && !offlineMapManager.downloadProgress.value.isDownloading) {
            val usedMb = offlineMapManager.cacheSizeMb.value
            for (maxZ in listOf(16, 15, 14)) {
                val (tiles, mb, _) = OfflineMapManager.estimateCorridorTilesAndMb(
                    points = route.points,
                    halfWidthKm = 1.0,
                    tileType = preferredTileType,
                    minZoom = 12,
                    maxZoom = maxZ
                )
                if (tiles in 1..OfflineMapManager.MAX_TILES_PER_DOWNLOAD &&
                    (usedMb + mb) <= OfflineMapManager.MAX_CACHE_BYTES_MB
                ) {
                    startCorridorOfflineDownload(
                        name = safeName,
                        points = route.points,
                        halfWidthKm = 1.0,
                        tileType = preferredTileType,
                        minZoom = 12,
                        maxZoom = maxZ,
                        includeRoads = true
                    )
                    return "Downloading corridor tiles (Z12–$maxZ) & offline roads..."
                }
            }
        }

        // Decoupled fallback: >2,500-tile routes, OSM Streets, or busy tile queue still get full offline road graph
        startRoadOnlyRegionDownload(
            name = safeName,
            box = null,
            corridorPoints = route.points,
            halfWidthKm = 1.5,
            tileType = preferredTileType
        )
        return if (!preferredTileType.supportsOfflineBulkDownload) {
            "Downloading offline road network (OSM Streets tiles auto-cache)..."
        } else {
            "Long route (>2,500 tiles): downloading full offline road network..."
        }
    }
}
