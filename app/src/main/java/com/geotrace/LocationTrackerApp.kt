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
        routingProvider = com.geotrace.navigation.HybridPhase1RoutingProvider(this)
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
        maxZoom: Int
    ) {
        _offlinePoiStatus.value = null
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
        maxZoom: Int
    ) {
        _offlinePoiStatus.value = null
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
}
