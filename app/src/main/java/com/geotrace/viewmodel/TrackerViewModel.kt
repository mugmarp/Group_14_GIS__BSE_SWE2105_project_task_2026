package com.geotrace.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotrace.LocationTrackerApp
import com.geotrace.data.export.ExportFormat
import com.geotrace.data.export.TrackExporter
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.GeofenceCategory
import com.geotrace.model.GeofenceZone
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineDownloadProgress
import com.geotrace.model.OfflineMapRegion
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import com.geotrace.service.LocationForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class TrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as LocationTrackerApp).repository
    private val cellManager = (application as LocationTrackerApp).cellManager

    val currentLocation: StateFlow<LocationPoint?> = repository.currentLocation
    val activeSession: StateFlow<TrackingSession?> = repository.activeSession
    val activeTrailPoints: StateFlow<List<LocationPoint>> = repository.activeTrailPoints
    val activeWaypoints: StateFlow<List<TrailWaypoint>> = repository.activeWaypoints
    val allSessions: StateFlow<List<TrackingSession>> = repository.allSessions
    val geofenceZones: StateFlow<List<GeofenceZone>> = repository.geofenceZones
    val recentAlerts = repository.recentAlerts
    val cellTowers: StateFlow<List<CellTowerInfo>> = repository.cellTowers
    val trackingMode: StateFlow<TrackingMode> = repository.trackingMode
    val filteredJitterMeters: StateFlow<Double> = repository.filteredJitterMeters
    val filteredJitterCount: StateFlow<Int> = repository.filteredJitterCount

    data class InspectedTrail(
        val session: TrackingSession,
        val points: List<LocationPoint>,
        val waypoints: List<TrailWaypoint>
    )

    data class ExportPayload(
        val sessionName: String,
        val format: ExportFormat,
        val content: String,
        val suggestedFileName: String,
        val trackpointCount: Int,
        val waypointCount: Int
    )

    private val _inspectedTrail = MutableStateFlow<InspectedTrail?>(null)
    val inspectedTrail: StateFlow<InspectedTrail?> = _inspectedTrail.asStateFlow()

    private val _cellTowerLocation = MutableStateFlow<com.geotrace.model.CellTowerLocation?>(null)
    val cellTowerLocation: StateFlow<com.geotrace.model.CellTowerLocation?> = _cellTowerLocation.asStateFlow()

    private val _allCellTowerLocations = MutableStateFlow<List<com.geotrace.model.CellTowerLocation>>(emptyList())
    val allCellTowerLocations: StateFlow<List<com.geotrace.model.CellTowerLocation>> = _allCellTowerLocations.asStateFlow()

    private val _settings = MutableStateFlow(
        com.geotrace.model.TrackerSettings(
            speedUnit = repository.getSavedSpeedUnit(),
            coordinateFormat = repository.getSavedCoordinateFormat(),
            mapTileType = repository.getSavedMapTileType(),
            showAccuracyRing = repository.getSavedShowAccuracyRing(),
            followUserLocation = repository.getSavedFollowUserLocation(),
            stationaryJitterThresholdMeters = repository.getSavedJitterThreshold(),
            keepScreenOn = repository.getSavedKeepScreenOn(),
            showCellTowerPin = repository.getSavedShowCellTowerPin(),
            openCellIdApiKey = repository.getSavedOpenCellIdApiKey(),
            geofenceNotificationEnabled = repository.geofenceNotificationsEnabled,
            geofenceHysteresisMeters = repository.getSavedGeofenceHysteresis(),
            geofenceAccuracyGateEnabled = repository.getSavedGeofenceAccuracyGate()
        )
    )
    val settings: StateFlow<com.geotrace.model.TrackerSettings> = _settings.asStateFlow()

    data class GeofencePickDraft(
        val isNew: Boolean,
        val zoneId: String? = null,
        val name: String,
        val radiusMeters: Float,
        val category: GeofenceCategory,
        val colorHex: String,
        val alertOnEntry: Boolean,
        val alertOnExit: Boolean,
        val latitude: Double,
        val longitude: Double
    )

    private val _geofencePickDraft = MutableStateFlow<GeofencePickDraft?>(null)
    val geofencePickDraft: StateFlow<GeofencePickDraft?> = _geofencePickDraft.asStateFlow()

    private val _isPickingGeofenceCenter = MutableStateFlow(false)
    val isPickingGeofenceCenter: StateFlow<Boolean> = _isPickingGeofenceCenter.asStateFlow()

    val geofenceManager = com.geotrace.service.GeofenceManager(application)
    val geofenceApiStatus: StateFlow<com.geotrace.service.GeofenceApiStatus> = geofenceManager.apiStatus

    val appUpdateManager = com.geotrace.service.AppUpdateManager(application)
    val updateStatus = appUpdateManager.updateStatus

    val offlineMapManager = com.geotrace.service.OfflineMapManager(application)
    val offlineMapRegions: StateFlow<List<OfflineMapRegion>> = repository.offlineMapRegions
    val offlineDownloadProgress: StateFlow<OfflineDownloadProgress> = offlineMapManager.downloadProgress
    val offlineCacheSizeMb: StateFlow<Float> = offlineMapManager.cacheSizeMb

    private val _currentMapBoundingBox = MutableStateFlow<org.osmdroid.util.BoundingBox?>(null)
    val currentMapBoundingBox: StateFlow<org.osmdroid.util.BoundingBox?> = _currentMapBoundingBox.asStateFlow()

    @Volatile
    var savedMapCenter: org.osmdroid.util.GeoPoint? = null
    @Volatile
    var savedMapZoom: Double? = null

    private val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(application)
    private val systemLocationManager = application.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
    private var standbyCallback: com.google.android.gms.location.LocationCallback? = null
    private var standbyNetworkListener: android.location.LocationListener? = null
    private var standbyCellJob: Job? = null
    private var lastCellularFallbackKey: String? = null
    private var lastCellularFallbackTimeMs: Long = 0L
    @Volatile
    private var lastModeSwitchMs: Long = 0L

    init {
        startStandbyLocationUpdates()
        startStandbyCellPolling()
        observeCellTowerLocation()
        observeGeofencingSync()
    }

    private fun observeGeofencingSync() {
        viewModelScope.launch {
            combine(geofenceZones, settings) { zones, currentSettings ->
                Triple(zones, currentSettings.geofenceNotificationEnabled, currentSettings.geofenceHysteresisMeters)
            }.collect { (zones, enabled, hysteresis) ->
                if (enabled) {
                    geofenceManager.syncGeofences(zones, true, hysteresis)
                } else {
                    geofenceManager.syncGeofences(emptyList(), false, hysteresis)
                }
            }
        }
    }

    private fun startStandbyCellPolling() {
        standbyCellJob?.cancel()
        standbyCellJob = viewModelScope.launch {
            while (isActive) {
                if (activeSession.value == null) {
                    val towers = cellManager.readCurrentCellTowers()
                    repository.updateCellTowers(towers)
                }
                val delayMs = if (trackingMode.value == TrackingMode.POWER_SAVER) 10_000L else 25_000L
                delay(delayMs)
            }
        }
    }

    private fun observeCellTowerLocation() {
        viewModelScope.launch {
            combine(cellTowers, trackingMode) { towers, mode ->
                Pair(towers, mode)
            }.collect { (towers, mode) ->
                val loc = currentLocation.value
                val servingCell = towers.firstOrNull { it.isRegistered } ?: towers.firstOrNull()
                val apiKey = _settings.value.openCellIdApiKey

                if (servingCell != null) {
                    val location = com.geotrace.service.OpenCellIdClient.resolveCellLocation(
                        cellInfo = servingCell,
                        apiKey = apiKey,
                        userLatitude = loc?.latitude,
                        userLongitude = loc?.longitude
                    )
                    _cellTowerLocation.value = location
                } else {
                    _cellTowerLocation.value = null
                }

                // Resolve all visible towers detected by TelephonyManager
                val allResolved = com.geotrace.service.OpenCellIdClient.resolveAllCellLocations(
                    towers = towers,
                    apiKey = apiKey,
                    userLatitude = loc?.latitude,
                    userLongitude = loc?.longitude
                )
                _allCellTowerLocations.value = allResolved

                // In Cell-Only mode: fall back to OpenCellID-resolved serving tower immediately on switch or staleness
                if (mode == TrackingMode.POWER_SAVER) {
                    _cellTowerLocation.value?.let { towerLoc ->
                        val now = System.currentTimeMillis()
                        val towerKey = "${towerLoc.cellId}_${towerLoc.lac}"
                        val isCurrentNonCellular = loc != null && loc.provider != "cellular"
                        val isCurrentStale = loc == null || (now - loc.timestamp > 25_000L)
                        val changedTower = lastCellularFallbackKey != towerKey
                        if (isCurrentNonCellular || isCurrentStale || (changedTower && (now - lastCellularFallbackTimeMs >= 10_000L))) {
                            lastCellularFallbackKey = towerKey
                            lastCellularFallbackTimeMs = now
                            val fallbackPoint = LocationPoint(
                                latitude = towerLoc.latitude,
                                longitude = towerLoc.longitude,
                                accuracy = towerLoc.rangeMeters.toFloat(),
                                speed = 0f,
                                altitude = 0.0,
                                bearing = 0f,
                                provider = "cellular",
                                timestamp = now,
                                sessionId = activeSession.value?.id ?: "cellular_standby"
                            )
                            repository.recordLocation(fallbackPoint)
                        }
                    }
                }
            }
        }
    }

    fun hasActiveStandbyUpdates(): Boolean = standbyCallback != null || standbyNetworkListener != null

    @android.annotation.SuppressLint("MissingPermission")
    fun startStandbyLocationUpdates(targetMode: TrackingMode = trackingMode.value) {
        stopStandbyLocationUpdates()
        if (targetMode == TrackingMode.HIGH_ACCURACY) {
            requestLastKnownLocation(forceIfCellular = true)
        }

        when (targetMode) {
            TrackingMode.HIGH_ACCURACY -> {
                val request = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    targetMode.intervalMs
                )
                    .setMinUpdateDistanceMeters(targetMode.minDistanceMeters)
                    .setMinUpdateIntervalMillis(targetMode.intervalMs / 2)
                    .build()

                standbyCallback = createStandbyFusedCallback(targetMode)
                try {
                    fusedLocationClient.requestLocationUpdates(
                        request,
                        standbyCallback!!,
                        android.os.Looper.getMainLooper()
                    )
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }

            TrackingMode.BALANCED -> {
                val lm = systemLocationManager
                val networkEnabled = try {
                    lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                } catch (_: Exception) {
                    false
                }

                if (networkEnabled && lm != null) {
                    val listener = createStandbyNetworkListener(targetMode)
                    standbyNetworkListener = listener
                    try {
                        lm.requestLocationUpdates(
                            android.location.LocationManager.NETWORK_PROVIDER,
                            targetMode.intervalMs,
                            targetMode.minDistanceMeters,
                            listener,
                            android.os.Looper.getMainLooper()
                        )
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {
                    }
                } else {
                    val request = com.google.android.gms.location.LocationRequest.Builder(
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        targetMode.intervalMs
                    )
                        .setMinUpdateDistanceMeters(targetMode.minDistanceMeters)
                        .setMinUpdateIntervalMillis(targetMode.intervalMs)
                        .build()
                    standbyCallback = createStandbyFusedCallback(targetMode)
                    try {
                        fusedLocationClient.requestLocationUpdates(
                            request,
                            standbyCallback!!,
                            android.os.Looper.getMainLooper()
                        )
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {
                    }
                }
            }

            TrackingMode.POWER_SAVER -> {
                // In Cell-Only mode, cell polling + OpenCellID leads; coarse NETWORK_PROVIDER only acts as passive fallback if OpenCellID returns null
                val lm = systemLocationManager
                val networkEnabled = try {
                    lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                } catch (_: Exception) {
                    false
                }
                if (networkEnabled && lm != null) {
                    val listener = createStandbyNetworkListener(targetMode)
                    standbyNetworkListener = listener
                    try {
                        lm.requestLocationUpdates(
                            android.location.LocationManager.NETWORK_PROVIDER,
                            targetMode.intervalMs,
                            targetMode.minDistanceMeters,
                            listener,
                            android.os.Looper.getMainLooper()
                        )
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {
                    }
                } else {
                    val request = com.google.android.gms.location.LocationRequest.Builder(
                        com.google.android.gms.location.Priority.PRIORITY_LOW_POWER,
                        targetMode.intervalMs
                    )
                        .setMinUpdateDistanceMeters(targetMode.minDistanceMeters)
                        .setMinUpdateIntervalMillis(targetMode.intervalMs)
                        .build()
                    standbyCallback = createStandbyFusedCallback(targetMode)
                    try {
                        fusedLocationClient.requestLocationUpdates(
                            request,
                            standbyCallback!!,
                            android.os.Looper.getMainLooper()
                        )
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun createStandbyFusedCallback(registeredMode: TrackingMode): com.google.android.gms.location.LocationCallback {
        return object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: return
                handleStandbyPlatformLocation(loc, registeredMode)
            }
        }
    }

    private fun createStandbyNetworkListener(registeredMode: TrackingMode): android.location.LocationListener {
        return object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                handleStandbyPlatformLocation(location, registeredMode)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
    }

    private fun handleStandbyPlatformLocation(loc: android.location.Location, registeredMode: TrackingMode) {
        val currentMode = trackingMode.value
        if (currentMode != registeredMode) return
        if (lastModeSwitchMs > 0L && loc.time > 0L && loc.time < lastModeSwitchMs - 2_000L) return

        // In Balanced mode, block GPS hardware updates so it stays strictly Network/Fused
        if (currentMode == TrackingMode.BALANCED && loc.provider == android.location.LocationManager.GPS_PROVIDER) {
            return
        }

        // In Cell-Only mode, block warm GNSS/high-precision Wi-Fi pings and defer whenever OpenCellID resolved a tower
        if (currentMode == TrackingMode.POWER_SAVER) {
            if (_cellTowerLocation.value != null) return
            if (loc.provider == android.location.LocationManager.GPS_PROVIDER || loc.accuracy < 35f) return
        }

        if (activeSession.value == null) {
            val p = LocationPoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                speed = if (loc.hasSpeed()) loc.speed else 0f,
                altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                provider = loc.provider ?: "fused",
                timestamp = loc.time,
                sessionId = "standby"
            )
            viewModelScope.launch {
                repository.recordLocation(p)
            }
        }
    }

    fun stopStandbyLocationUpdates() {
        standbyCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            standbyCallback = null
        }
        standbyNetworkListener?.let { listener ->
            try {
                systemLocationManager?.removeUpdates(listener)
            } catch (_: Exception) {
            }
            standbyNetworkListener = null
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun requestLastKnownLocation(forceIfCellular: Boolean = false) {
        if (trackingMode.value != TrackingMode.HIGH_ACCURACY) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                val cur = currentLocation.value
                val isFreshLastKnown = loc != null && (System.currentTimeMillis() - loc.time) < 20_000L
                val shouldApply = cur == null || (forceIfCellular && cur.provider == "cellular" && isFreshLastKnown)
                if (loc != null && shouldApply) {
                    val p = LocationPoint(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        speed = if (loc.hasSpeed()) loc.speed else 0f,
                        altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        provider = loc.provider ?: "fused",
                        timestamp = loc.time,
                        sessionId = "real_initial"
                    )
                    viewModelScope.launch {
                        repository.recordLocation(p)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission flow handled in UI
        } catch (e: Exception) {
            // Hardware unavailable
        }
    }

    fun startTracking(sessionName: String, mode: TrackingMode) {
        viewModelScope.launch {
            _inspectedTrail.value = null
            stopStandbyLocationUpdates()
            repository.setTrackingMode(mode)
            repository.startNewSession(sessionName, mode)
            LocationForegroundService.startService(getApplication(), mode)
        }
    }

    fun pauseTracking() {
        viewModelScope.launch {
            repository.pauseCurrentSession()
            LocationForegroundService.pauseTracking(getApplication())
        }
    }

    fun resumeTracking() {
        viewModelScope.launch {
            repository.resumeCurrentSession()
            LocationForegroundService.resumeTracking(getApplication())
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            repository.stopCurrentSession()
            LocationForegroundService.stopTrackingOrDowngrade(getApplication())
            startStandbyLocationUpdates()
        }
    }

    fun dropWaypoint(name: String, note: String = "", onResult: ((TrailWaypoint?) -> Unit)? = null) {
        viewModelScope.launch {
            val wp = repository.dropWaypoint(name, note)
            onResult?.invoke(wp)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStandbyLocationUpdates()
        standbyCellJob?.cancel()
    }

    fun setTrackingMode(mode: TrackingMode) {
        lastModeSwitchMs = System.currentTimeMillis()
        stopStandbyLocationUpdates()
        viewModelScope.launch {
            repository.setTrackingMode(mode)
            startStandbyCellPolling()
            if (mode == TrackingMode.POWER_SAVER) {
                refreshCellTowers()
                // Force immediate cellular fix check if currentLocation is GNSS/Fused
                val curLoc = currentLocation.value
                _cellTowerLocation.value?.let { towerLoc ->
                    if (curLoc == null || curLoc.provider != "cellular") {
                        lastCellularFallbackKey = "${towerLoc.cellId}_${towerLoc.lac}"
                        lastCellularFallbackTimeMs = System.currentTimeMillis()
                        repository.recordLocation(
                            LocationPoint(
                                latitude = towerLoc.latitude,
                                longitude = towerLoc.longitude,
                                accuracy = towerLoc.rangeMeters.toFloat(),
                                speed = 0f,
                                altitude = 0.0,
                                bearing = 0f,
                                provider = "cellular",
                                timestamp = lastCellularFallbackTimeMs,
                                sessionId = activeSession.value?.id ?: "cellular_standby"
                            )
                        )
                    }
                }
            }
            if (activeSession.value != null) {
                LocationForegroundService.updateMode(getApplication(), mode)
            } else {
                startStandbyLocationUpdates(mode)
                // Keep foreground protection service synchronized with newly selected mode too
                LocationForegroundService.updateMode(getApplication(), mode)
            }
        }
    }

    fun addGeofence(name: String, latitude: Double, longitude: Double, radius: Float, category: GeofenceCategory, colorHex: String) {
        viewModelScope.launch {
            val zone = GeofenceZone(
                id = UUID.randomUUID().toString(),
                name = name,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radius,
                category = category,
                colorHex = colorHex
            )
            repository.addGeofence(zone)
        }
    }

    fun updateGeofence(zone: GeofenceZone) {
        viewModelScope.launch {
            repository.updateGeofence(zone)
        }
    }

    fun deleteGeofence(id: String) {
        viewModelScope.launch {
            repository.deleteGeofence(id)
        }
    }

    fun resetSampleGeofences() {
        viewModelScope.launch {
            repository.resetSampleGeofences()
        }
    }

    fun beginMapPickForGeofence(draft: GeofencePickDraft) {
        _geofencePickDraft.value = draft
        _isPickingGeofenceCenter.value = true
    }

    fun completeMapPick(lat: Double, lon: Double) {
        val current = _geofencePickDraft.value
        if (current != null) {
            _geofencePickDraft.value = current.copy(latitude = lat, longitude = lon)
        }
        _isPickingGeofenceCenter.value = false
    }

    fun cancelMapPick() {
        _isPickingGeofenceCenter.value = false
        _geofencePickDraft.value = null
    }

    fun clearGeofencePickDraft() {
        _geofencePickDraft.value = null
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_inspectedTrail.value?.session?.id == sessionId) {
                _inspectedTrail.value = null
            }
            repository.deleteSession(sessionId)
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, newName)
            val currentInspected = _inspectedTrail.value
            if (currentInspected != null && currentInspected.session.id == sessionId) {
                _inspectedTrail.value = currentInspected.copy(
                    session = currentInspected.session.copy(name = newName.trim())
                )
            }
        }
    }

    fun inspectSessionOnMap(session: TrackingSession, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val points = repository.getPointsForSession(session.id)
            val waypoints = repository.getWaypointsForSession(session.id)
            if (points.isEmpty() && waypoints.isEmpty()) {
                onResult?.invoke(false)
                return@launch
            }
            _inspectedTrail.value = InspectedTrail(session, points, waypoints)
            onResult?.invoke(true)
        }
    }

    fun clearInspectedTrail() {
        _inspectedTrail.value = null
    }

    fun exportSession(
        session: TrackingSession,
        format: ExportFormat,
        onReady: (ExportPayload?) -> Unit
    ) {
        viewModelScope.launch {
            val points = repository.getPointsForSession(session.id)
            val waypoints = repository.getWaypointsForSession(session.id)
            if (points.isEmpty() && waypoints.isEmpty()) {
                onReady(null)
                return@launch
            }
            val content = TrackExporter.export(session, points, waypoints, format)
            val fileName = TrackExporter.buildSafeFileName(session.name, format)
            onReady(
                ExportPayload(
                    sessionName = session.name,
                    format = format,
                    content = content,
                    suggestedFileName = fileName,
                    trackpointCount = points.size,
                    waypointCount = waypoints.size
                )
            )
        }
    }

    fun refreshCellTowers() {
        val towers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(towers)
    }

    fun updateSpeedUnit(unit: com.geotrace.model.SpeedUnit) {
        repository.saveSpeedUnit(unit)
        _settings.value = _settings.value.copy(speedUnit = unit)
    }

    fun updateCoordinateFormat(format: com.geotrace.model.CoordinateFormat) {
        repository.saveCoordinateFormat(format)
        _settings.value = _settings.value.copy(coordinateFormat = format)
    }

    fun updateMapTileType(tileType: com.geotrace.model.MapTileType) {
        repository.saveMapTileType(tileType)
        _settings.value = _settings.value.copy(mapTileType = tileType)
    }

    fun updateMapViewportBounds(box: org.osmdroid.util.BoundingBox) {
        _currentMapBoundingBox.value = box
    }

    fun computeBoundingBoxAround(lat: Double, lng: Double, radiusKm: Double): org.osmdroid.util.BoundingBox {
        return offlineMapManager.computeBoundingBoxAround(lat, lng, radiusKm)
    }

    fun estimateOfflineRegion(
        box: org.osmdroid.util.BoundingBox,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int
    ): Pair<Int, Float> {
        return offlineMapManager.estimateRegionTilesAndMb(box, tileType, minZoom, maxZoom)
    }

    fun startOfflineRegionDownload(
        name: String,
        box: org.osmdroid.util.BoundingBox,
        tileType: MapTileType,
        minZoom: Int,
        maxZoom: Int
    ) {
        offlineMapManager.startRegionDownload(
            name = name,
            box = box,
            tileType = tileType,
            minZoom = minZoom,
            maxZoom = maxZoom,
            onCompleted = { region ->
                viewModelScope.launch {
                    repository.saveOfflineMapRegion(region)
                }
            }
        )
    }

    fun cancelOfflineRegionDownload() {
        offlineMapManager.cancelActiveDownload()
    }

    fun clearOfflineDownloadError() {
        offlineMapManager.clearDownloadError()
    }

    fun deleteOfflineMapRegion(id: String) {
        viewModelScope.launch {
            repository.deleteOfflineMapRegion(id)
        }
    }

    fun clearAllTileCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            offlineMapManager.clearAllTileCache()
            repository.clearAllOfflineMapRegions()
        }
    }

    fun refreshTileCacheSize() {
        viewModelScope.launch {
            offlineMapManager.refreshCacheSizeAsync()
        }
    }

    fun toggleAccuracyRing(show: Boolean) {
        repository.saveShowAccuracyRing(show)
        _settings.value = _settings.value.copy(showAccuracyRing = show)
    }

    fun toggleFollowUser(follow: Boolean) {
        repository.saveFollowUserLocation(follow)
        _settings.value = _settings.value.copy(followUserLocation = follow)
    }

    fun updateJitterThreshold(thresholdMeters: Float) {
        repository.saveJitterThreshold(thresholdMeters)
        _settings.value = _settings.value.copy(stationaryJitterThresholdMeters = thresholdMeters)
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return repository.calculateDistanceMeters(lat1, lon1, lat2, lon2)
    }

    fun toggleKeepScreenOn(keepOn: Boolean) {
        repository.saveKeepScreenOn(keepOn)
        _settings.value = _settings.value.copy(keepScreenOn = keepOn)
    }

    fun toggleCellTowerPin(show: Boolean) {
        repository.saveShowCellTowerPin(show)
        _settings.value = _settings.value.copy(showCellTowerPin = show)
    }

    fun updateOpenCellIdApiKey(apiKey: String) {
        repository.saveOpenCellIdApiKey(apiKey)
        _settings.value = _settings.value.copy(openCellIdApiKey = apiKey)
        refreshCellTowers()
    }

    fun toggleGeofenceNotifications(enabled: Boolean) {
        repository.setGeofenceNotificationsEnabled(enabled)
        _settings.value = _settings.value.copy(geofenceNotificationEnabled = enabled)
        geofenceManager.syncGeofences(geofenceZones.value, enabled, _settings.value.geofenceHysteresisMeters)
    }

    fun updateGeofenceHysteresis(meters: Float) {
        repository.saveGeofenceHysteresis(meters)
        _settings.value = _settings.value.copy(geofenceHysteresisMeters = meters)
        if (_settings.value.geofenceNotificationEnabled) {
            geofenceManager.syncGeofences(geofenceZones.value, true, meters)
        }
    }

    fun toggleGeofenceAccuracyGate(enabled: Boolean) {
        repository.saveGeofenceAccuracyGate(enabled)
        _settings.value = _settings.value.copy(geofenceAccuracyGateEnabled = enabled)
    }

    fun resyncGeofencingApi() {
        val enabled = _settings.value.geofenceNotificationEnabled
        geofenceManager.syncGeofences(geofenceZones.value, enabled, _settings.value.geofenceHysteresisMeters)
    }

    fun sendTestAcademicNotification() {
        if (!_settings.value.geofenceNotificationEnabled) return
        val academicZone = geofenceZones.value.firstOrNull { it.category.isAcademicZone }
            ?: geofenceZones.value.firstOrNull()
        val zoneName = academicZone?.name ?: "MUST Kihumuro Campus (FAST)"
        val emoji = academicZone?.category?.iconEmoji ?: "🎓"
        val label = academicZone?.category?.label ?: "University Campus"
        com.geotrace.service.GeofenceBroadcastReceiver.sendNotification(
            context = getApplication(),
            title = "$emoji $label: Entered (Test Alert)",
            content = "Geofencing API alert active for $zoneName",
            notificationId = 2999
        )
    }
}
