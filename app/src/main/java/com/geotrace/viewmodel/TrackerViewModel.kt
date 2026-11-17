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
import com.geotrace.model.SearchPlaceResult
import com.geotrace.model.SearchResultSource
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import com.geotrace.service.LocationForegroundService
import com.geotrace.service.PlaceSearchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val waypointCount: Int,
        val lineCount: Int,
        val byteSize: Int
    )

    private val _exportingSessionId = MutableStateFlow<String?>(null)
    val exportingSessionId: StateFlow<String?> = _exportingSessionId.asStateFlow()

    private val _inspectedTrail = MutableStateFlow<InspectedTrail?>(null)
    val inspectedTrail: StateFlow<InspectedTrail?> = _inspectedTrail.asStateFlow()

    private val _cellTowerLocation = MutableStateFlow<com.geotrace.model.CellTowerLocation?>(null)
    val cellTowerLocation: StateFlow<com.geotrace.model.CellTowerLocation?> = _cellTowerLocation.asStateFlow()

    private val _allCellTowerLocations = MutableStateFlow<List<com.geotrace.model.CellTowerLocation>>(emptyList())
    val allCellTowerLocations: StateFlow<List<com.geotrace.model.CellTowerLocation>> = _allCellTowerLocations.asStateFlow()

    val openCellIdStatus: StateFlow<com.geotrace.service.OpenCellIdStatus> =
        com.geotrace.service.OpenCellIdClient.lookupStatus

    private val _isRefreshingCells = MutableStateFlow(false)
    val isRefreshingCells: StateFlow<Boolean> = _isRefreshingCells.asStateFlow()

    private val cellLookupRefreshTrigger = MutableStateFlow(0)
    private var cellRefreshJob: Job? = null

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
    val offlinePlacesCount: StateFlow<Int> = repository.offlinePlacesCount

    val placeSearchManager = PlaceSearchManager(application)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<SearchPlaceResult>>(emptyList())
    val searchResults: StateFlow<List<SearchPlaceResult>> = _searchResults.asStateFlow()
    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()
    private val _isNetworkOnline = MutableStateFlow(placeSearchManager.isNetworkConnected())
    val isNetworkOnline: StateFlow<Boolean> = _isNetworkOnline.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    private val _selectedSearchPin = MutableStateFlow<SearchPlaceResult?>(null)
    val selectedSearchPin: StateFlow<SearchPlaceResult?> = _selectedSearchPin.asStateFlow()
    private var searchJob: Job? = null

    @Volatile
    var lastHandledFocusRequestMs: Long = 0L
    @Volatile
    var lastHandledSearchRequestMs: Long = 0L
    @Volatile
    var lastHandledInspectedTrailId: String? = null

    private val _currentMapBoundingBox = MutableStateFlow<org.osmdroid.util.BoundingBox?>(null)
    val currentMapBoundingBox: StateFlow<org.osmdroid.util.BoundingBox?> = _currentMapBoundingBox.asStateFlow()

    @Volatile
    var savedMapCenter: org.osmdroid.util.GeoPoint? = null
    @Volatile
    var savedMapZoom: Double? = null

    private val _isMapFollowPaused = MutableStateFlow(false)
    val isMapFollowPaused: StateFlow<Boolean> = _isMapFollowPaused.asStateFlow()

    private val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(application)
    private val systemLocationManager = application.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
    private var standbyCallback: com.google.android.gms.location.LocationCallback? = null
    private var standbyNetworkListener: android.location.LocationListener? = null
    private var quickFixCancellation: com.google.android.gms.tasks.CancellationTokenSource? = null
    private var osQuickFixCancellation: android.os.CancellationSignal? = null
    private var standbyCellJob: Job? = null
    private var lastCellularFallbackKey: String? = null
    private var lastCellularFallbackTimeMs: Long = 0L
    @Volatile
    private var lastModeSwitchMs: Long = 0L
    @Volatile
    private var lastModeSwitchElapsedNs: Long = 0L

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

    private fun startStandbyCellPolling(forceFreshOnFirstTick: Boolean = false) {
        standbyCellJob?.cancel()
        standbyCellJob = viewModelScope.launch {
            var useFreshRead = forceFreshOnFirstTick
            while (isActive) {
                val serviceActivelyDrivesCells = LocationForegroundService.isServiceActive &&
                    LocationForegroundService.isCellDiagnosticsActive &&
                    !LocationForegroundService.isProtectionPausedFlow.value
                if (activeSession.value == null && !serviceActivelyDrivesCells) {
                    val towers = if (useFreshRead) {
                        useFreshRead = false
                        cellManager.requestFreshCellTowers()
                    } else {
                        cellManager.readCurrentCellTowers()
                    }
                    repository.updateCellTowers(towers)
                }
                val delayMs = if (trackingMode.value == TrackingMode.POWER_SAVER) 10_000L else 25_000L
                delay(delayMs)
            }
        }
    }

    private fun observeCellTowerLocation() {
        viewModelScope.launch {
            combine(
                cellTowers,
                trackingMode,
                settings.map { it.openCellIdApiKey }.distinctUntilChanged(),
                cellLookupRefreshTrigger
            ) { towers, mode, apiKey, _ ->
                Triple(towers, mode, apiKey)
            }.collect { (towers, mode, apiKey) ->
                val loc = currentLocation.value
                val servingCell = towers.firstOrNull { it.isRegistered } ?: towers.firstOrNull()

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

                // In Cell-Only mode: fall back to OpenCellID-resolved serving tower immediately on switch, or on staleness when service diagnostics isn't driving it
                if (mode == TrackingMode.POWER_SAVER) {
                    _cellTowerLocation.value?.let { towerLoc ->
                        val now = System.currentTimeMillis()
                        val towerKey = "${towerLoc.cellId}_${towerLoc.lac}"
                        val isCurrentNonCellular = loc != null && loc.provider != "cellular"
                        val isCurrentStale = loc == null || (now - loc.timestamp > 25_000L)
                        val changedTower = lastCellularFallbackKey != towerKey
                        val serviceDrivesCell = LocationForegroundService.isServiceActive &&
                            LocationForegroundService.isCellDiagnosticsActive &&
                            !LocationForegroundService.isProtectionPausedFlow.value
                        if (isCurrentNonCellular || (!serviceDrivesCell && (isCurrentStale || (changedTower && (now - lastCellularFallbackTimeMs >= 10_000L))))) {
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
    fun startStandbyLocationUpdates(
        targetMode: TrackingMode = trackingMode.value,
        forceImmediateFix: Boolean = false
    ) {
        stopStandbyLocationUpdates()
        if (activeSession.value != null) return
        if (!LocationForegroundService.hasLocationPermission(getApplication())) return
        if (targetMode == TrackingMode.HIGH_ACCURACY || targetMode == TrackingMode.BALANCED) {
            requestLastKnownLocation(
                forceIfCellular = true,
                targetMode = targetMode,
                forceImmediateFix = forceImmediateFix
            )
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
                    standbyCallback = null
                } catch (_: Exception) {
                    standbyCallback = null
                }
            }

            TrackingMode.BALANCED -> {
                val request = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
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
                    standbyCallback = null
                } catch (_: Exception) {
                    standbyCallback = null
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
                            standbyNetworkListener = null
                        } catch (_: Exception) {
                            standbyNetworkListener = null
                        }
                    }
                }
            }

            TrackingMode.POWER_SAVER -> {
                // When active unpaused foreground protection is running, it drives cell diagnostics + coarse fallback
                val serviceActivelyRunsProtection = LocationForegroundService.isServiceActive &&
                    !LocationForegroundService.isProtectionPausedFlow.value
                if (serviceActivelyRunsProtection) {
                    return
                }
                if (forceImmediateFix && _cellTowerLocation.value == null) {
                    requestImmediateQuickFix(targetMode, forceReplace = true)
                }
                // Register coarse NETWORK_PROVIDER / LOW_POWER fallback if OpenCellID returns null or protection is paused
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
                        standbyNetworkListener = null
                    } catch (_: Exception) {
                        standbyNetworkListener = null
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
                        standbyCallback = null
                    } catch (_: Exception) {
                        standbyCallback = null
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
        val switchElapsedNs = maxOf(lastModeSwitchElapsedNs, repository.lastModeSwitchElapsedNs)
        if (switchElapsedNs > 0L && loc.elapsedRealtimeNanos > 0L &&
            loc.elapsedRealtimeNanos < switchElapsedNs - 2_000_000_000L
        ) return

        // In Balanced mode, block GPS hardware updates so it stays strictly Network/Fused
        if (currentMode == TrackingMode.BALANCED && loc.provider == android.location.LocationManager.GPS_PROVIDER) {
            return
        }

        // In Cell-Only mode, block GPS updates and defer to OpenCellID mast coordinates whenever resolved
        if (currentMode == TrackingMode.POWER_SAVER) {
            if (loc.provider == android.location.LocationManager.GPS_PROVIDER) return
            if (_cellTowerLocation.value != null) return
        }

        if (activeSession.value == null) {
            val normalizedTime = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            val p = LocationPoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                speed = if (loc.hasSpeed()) loc.speed else 0f,
                altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                provider = loc.provider ?: "fused",
                timestamp = normalizedTime,
                sessionId = "standby"
            )
            viewModelScope.launch {
                repository.recordLocation(p)
            }
        }
    }

    fun stopStandbyLocationUpdates() {
        quickFixCancellation?.cancel()
        quickFixCancellation = null
        osQuickFixCancellation?.cancel()
        osQuickFixCancellation = null
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
    fun requestLastKnownLocation(
        forceIfCellular: Boolean = false,
        targetMode: TrackingMode = trackingMode.value,
        forceImmediateFix: Boolean = false
    ) {
        if (targetMode == TrackingMode.POWER_SAVER) return
        if (!LocationForegroundService.hasLocationPermission(getApplication())) return
        if (forceImmediateFix) {
            requestImmediateQuickFix(mode = targetMode, forceReplace = true)
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (trackingMode.value != targetMode) return@addOnSuccessListener
                val cur = currentLocation.value
                val switchElapsedNs = maxOf(lastModeSwitchElapsedNs, repository.lastModeSwitchElapsedNs)
                val isFreshLastKnown = loc != null && loc.time > 0L &&
                    (System.currentTimeMillis() - loc.time) < 20_000L &&
                    (switchElapsedNs <= 0L || loc.elapsedRealtimeNanos <= 0L ||
                        loc.elapsedRealtimeNanos >= switchElapsedNs - 5_000_000_000L)
                val isBalancedBlockedGps = targetMode == TrackingMode.BALANCED &&
                    loc?.provider == android.location.LocationManager.GPS_PROVIDER
                val shouldApply = cur == null ||
                    (forceIfCellular && cur.provider == "cellular" && isFreshLastKnown)
                if (loc != null && shouldApply && !isBalancedBlockedGps) {
                    val normalizedTime = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    val p = LocationPoint(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        speed = if (loc.hasSpeed()) loc.speed else 0f,
                        altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        provider = loc.provider ?: "fused",
                        timestamp = normalizedTime,
                        sessionId = "real_initial"
                    )
                    viewModelScope.launch {
                        repository.recordLocation(p)
                    }
                } else if (cur == null || (forceIfCellular && cur.provider == "cellular")) {
                    requestImmediateQuickFix(
                        mode = targetMode,
                        forceReplace = (cur?.provider == "cellular")
                    )
                }
            }.addOnFailureListener {
                if (currentLocation.value == null || currentLocation.value?.provider == "cellular") {
                    requestImmediateQuickFix(
                        mode = targetMode,
                        forceReplace = (currentLocation.value?.provider == "cellular")
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission flow handled in UI
        } catch (e: Exception) {
            // Hardware unavailable
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun requestImmediateQuickFix(
        mode: TrackingMode,
        forceReplace: Boolean = false,
        allowBalancedFallback: Boolean = true
    ) {
        if (!LocationForegroundService.hasLocationPermission(getApplication())) return
        if (mode == TrackingMode.POWER_SAVER) {
            if (_cellTowerLocation.value != null) return
            val lm = systemLocationManager
            val networkEnabled = try {
                lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
            } catch (_: Exception) {
                false
            }
            if (networkEnabled && lm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    quickFixCancellation?.cancel()
                    quickFixCancellation = null
                    osQuickFixCancellation?.cancel()
                    val osCancel = android.os.CancellationSignal()
                    osQuickFixCancellation = osCancel
                    lm.getCurrentLocation(
                        android.location.LocationManager.NETWORK_PROVIDER,
                        osCancel,
                        getApplication<Application>().mainExecutor
                    ) { netLoc ->
                        if (trackingMode.value != TrackingMode.POWER_SAVER || _cellTowerLocation.value != null) return@getCurrentLocation
                        if (netLoc != null) {
                            val cur = currentLocation.value
                            if (cur == null || forceReplace || cur.provider == "cellular") {
                                val normalizedTime = netLoc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                                val p = LocationPoint(
                                    latitude = netLoc.latitude,
                                    longitude = netLoc.longitude,
                                    accuracy = netLoc.accuracy,
                                    speed = if (netLoc.hasSpeed()) netLoc.speed else 0f,
                                    altitude = if (netLoc.hasAltitude()) netLoc.altitude else 0.0,
                                    bearing = if (netLoc.hasBearing()) netLoc.bearing else 0f,
                                    provider = netLoc.provider ?: "network",
                                    timestamp = normalizedTime,
                                    sessionId = activeSession.value?.id ?: "real_initial"
                                )
                                viewModelScope.launch { repository.recordLocation(p) }
                            }
                        } else {
                            requestNetworkLastKnownFallback(mode, forceReplace)
                        }
                    }
                    return
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }
        }

        val priority = when {
            mode == TrackingMode.HIGH_ACCURACY && allowBalancedFallback ->
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
            mode == TrackingMode.HIGH_ACCURACY ->
                com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
            mode == TrackingMode.BALANCED ->
                com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else ->
                com.google.android.gms.location.Priority.PRIORITY_LOW_POWER
        }
        try {
            quickFixCancellation?.cancel()
            val cancellation = com.google.android.gms.tasks.CancellationTokenSource()
            quickFixCancellation = cancellation
            val currentRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setMaxUpdateAgeMillis(if (forceReplace) 0L else 10_000L)
                .setDurationMillis(if (mode == TrackingMode.HIGH_ACCURACY) 6_000L else 8_000L)
                .build()
            fusedLocationClient.getCurrentLocation(currentRequest, cancellation.token)
                .addOnSuccessListener { loc ->
                    if (trackingMode.value != mode) return@addOnSuccessListener
                    if (loc == null) {
                        if (mode == TrackingMode.HIGH_ACCURACY && allowBalancedFallback &&
                            (forceReplace || currentLocation.value == null || currentLocation.value?.provider == "cellular")
                        ) {
                            requestImmediateQuickFix(
                                mode = TrackingMode.HIGH_ACCURACY,
                                forceReplace = forceReplace,
                                allowBalancedFallback = false
                            )
                        } else {
                            requestNetworkLastKnownFallback(mode, forceReplace)
                        }
                        return@addOnSuccessListener
                    }
                    if (mode == TrackingMode.BALANCED && loc.provider == android.location.LocationManager.GPS_PROVIDER) {
                        requestNetworkLastKnownFallback(mode, forceReplace)
                        return@addOnSuccessListener
                    }
                    if (mode == TrackingMode.POWER_SAVER && (loc.provider == android.location.LocationManager.GPS_PROVIDER || _cellTowerLocation.value != null)) {
                        if (loc.provider == android.location.LocationManager.GPS_PROVIDER) {
                            requestNetworkLastKnownFallback(mode, forceReplace)
                        }
                        return@addOnSuccessListener
                    }
                    val cur = currentLocation.value
                    val shouldApply = cur == null || forceReplace || cur.provider == "cellular"
                    if (shouldApply) {
                        val normalizedTime = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                        val p = LocationPoint(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            speed = if (loc.hasSpeed()) loc.speed else 0f,
                            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                            bearing = if (loc.hasBearing()) loc.bearing else 0f,
                            provider = loc.provider ?: "fused",
                            timestamp = normalizedTime,
                            sessionId = activeSession.value?.id ?: "real_initial"
                        )
                        viewModelScope.launch {
                            repository.recordLocation(p)
                        }
                    }
                }
                .addOnFailureListener {
                    if (trackingMode.value == mode && (mode != TrackingMode.POWER_SAVER || _cellTowerLocation.value == null)) {
                        requestNetworkLastKnownFallback(mode, forceReplace)
                    }
                }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun requestNetworkLastKnownFallback(mode: TrackingMode, forceReplace: Boolean) {
        if (!LocationForegroundService.hasLocationPermission(getApplication())) return
        if (mode == TrackingMode.POWER_SAVER && _cellTowerLocation.value != null) return
        try {
            val lm = systemLocationManager ?: return
            val netLoc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (netLoc != null && trackingMode.value == mode) {
                val cur = currentLocation.value
                if (cur == null || forceReplace || cur.provider == "cellular") {
                    val normalizedTime = netLoc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    val p = LocationPoint(
                        latitude = netLoc.latitude,
                        longitude = netLoc.longitude,
                        accuracy = netLoc.accuracy,
                        speed = if (netLoc.hasSpeed()) netLoc.speed else 0f,
                        altitude = if (netLoc.hasAltitude()) netLoc.altitude else 0.0,
                        bearing = if (netLoc.hasBearing()) netLoc.bearing else 0f,
                        provider = netLoc.provider ?: "network",
                        timestamp = normalizedTime,
                        sessionId = activeSession.value?.id ?: "real_initial"
                    )
                    viewModelScope.launch {
                        repository.recordLocation(p)
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    suspend fun awaitInitialRestore() {
        repository.awaitInitialRestore()
    }

    fun startTracking(sessionName: String, mode: TrackingMode): Boolean {
        if (!LocationForegroundService.hasLocationPermission(getApplication())) {
            return false
        }
        viewModelScope.launch {
            _inspectedTrail.value = null
            stopStandbyLocationUpdates()
            repository.setTrackingMode(mode)
            repository.startNewSession(sessionName, mode)
            LocationForegroundService.startService(getApplication(), mode)
        }
        return true
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
        lastModeSwitchElapsedNs = android.os.SystemClock.elapsedRealtimeNanos()
        repository.setTrackingModeSync(mode)
        stopStandbyLocationUpdates()
        cellRefreshJob?.cancel()

        if (mode == TrackingMode.POWER_SAVER) {
            lastCellularFallbackKey = null
            lastCellularFallbackTimeMs = 0L
        }

        viewModelScope.launch {
            repository.setTrackingMode(mode)
            startStandbyCellPolling(forceFreshOnFirstTick = (mode == TrackingMode.POWER_SAVER))
            if (mode == TrackingMode.POWER_SAVER) {
                // Force immediate cellular fix check if cached mast exists, then refresh fresh modem towers
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
                refreshCellTowers()
            }
            val currentSession = activeSession.value
            if (currentSession != null) {
                if (LocationForegroundService.isServiceActive) {
                    LocationForegroundService.updateMode(getApplication(), mode)
                } else if (currentSession.isPaused) {
                    LocationForegroundService.startServicePaused(getApplication(), mode)
                } else {
                    LocationForegroundService.startService(getApplication(), mode)
                }
            } else {
                // Keep foreground protection service synchronized with newly selected mode first,
                // then align client standby updates so both use the same active mode immediately.
                LocationForegroundService.updateMode(getApplication(), mode)
                startStandbyLocationUpdates(mode, forceImmediateFix = true)
            }
        }
    }

    fun addGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radius: Float,
        category: GeofenceCategory,
        colorHex: String,
        alertOnEntry: Boolean = true,
        alertOnExit: Boolean = true
    ) {
        viewModelScope.launch {
            val zone = GeofenceZone(
                id = UUID.randomUUID().toString(),
                name = name,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radius,
                category = category,
                colorHex = colorHex,
                alertOnEntry = alertOnEntry,
                alertOnExit = alertOnExit
            )
            repository.addGeofence(zone)
            syncGeofencesIfEnabled()
        }
    }

    fun updateGeofence(zone: GeofenceZone) {
        viewModelScope.launch {
            repository.updateGeofence(zone)
            syncGeofencesIfEnabled()
        }
    }

    fun deleteGeofence(id: String) {
        viewModelScope.launch {
            repository.deleteGeofence(id)
            syncGeofencesIfEnabled()
        }
    }

    fun resetSampleGeofences() {
        viewModelScope.launch {
            repository.resetSampleGeofences()
            syncGeofencesIfEnabled()
        }
    }

    fun clearGeofenceAlerts() {
        viewModelScope.launch {
            repository.clearGeofenceAlerts()
        }
    }

    private fun syncGeofencesIfEnabled() {
        if (_settings.value.geofenceNotificationEnabled) {
            geofenceManager.syncGeofences(
                repository.geofenceZones.value,
                true,
                _settings.value.geofenceHysteresisMeters
            )
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
                lastHandledInspectedTrailId = null
            }
            val wasActive = repository.deleteSession(sessionId)
            if (wasActive) {
                LocationForegroundService.stopTrackingOrDowngrade(getApplication())
                startStandbyLocationUpdates()
            }
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

    fun loadWaypointsForSession(sessionId: String, onResult: (List<TrailWaypoint>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getWaypointsForSession(sessionId))
        }
    }

    fun deleteWaypoint(
        waypointId: String,
        sessionId: String,
        onUpdated: ((List<TrailWaypoint>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val remaining = repository.deleteWaypoint(waypointId, sessionId)
            val currentInspected = _inspectedTrail.value
            if (currentInspected != null && currentInspected.session.id == sessionId) {
                _inspectedTrail.value = currentInspected.copy(
                    session = currentInspected.session.copy(waypointCount = remaining.size),
                    waypoints = remaining
                )
            }
            onUpdated?.invoke(remaining)
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
            _isMapFollowPaused.value = true
            lastHandledInspectedTrailId = null
            _inspectedTrail.value = InspectedTrail(session, points, waypoints)
            onResult?.invoke(true)
        }
    }

    fun clearInspectedTrail() {
        _inspectedTrail.value = null
        lastHandledInspectedTrailId = null
    }

    fun exportSession(
        session: TrackingSession,
        format: ExportFormat,
        onReady: (ExportPayload?) -> Unit
    ) {
        if (_exportingSessionId.value != null) return
        _exportingSessionId.value = session.id
        viewModelScope.launch {
            try {
                val points = repository.getPointsForSession(session.id)
                val waypoints = repository.getWaypointsForSession(session.id)
                if (points.isEmpty() && waypoints.isEmpty()) {
                    onReady(null)
                    return@launch
                }
                val payload = withContext(Dispatchers.Default) {
                    val content = TrackExporter.export(session, points, waypoints, format)
                    val fileName = TrackExporter.buildSafeFileName(session.name, format)
                    val lineCount = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
                    val byteSize = content.toByteArray(Charsets.UTF_8).size
                    ExportPayload(
                        sessionName = session.name,
                        format = format,
                        content = content,
                        suggestedFileName = fileName,
                        trackpointCount = points.size,
                        waypointCount = waypoints.size,
                        lineCount = lineCount,
                        byteSize = byteSize
                    )
                }
                onReady(payload)
            } finally {
                _exportingSessionId.value = null
            }
        }
    }

    fun hasCellLocationPermission(): Boolean = cellManager.hasFineLocationPermission()

    fun refreshCellTowers() {
        cellRefreshJob?.cancel()
        cellRefreshJob = viewModelScope.launch {
            _isRefreshingCells.value = true
            try {
                val towers = cellManager.requestFreshCellTowers()
                repository.updateCellTowers(towers)
                cellLookupRefreshTrigger.value += 1
            } finally {
                _isRefreshingCells.value = false
            }
        }
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
                    // Best-effort offline POI & street index pre-cache inside downloaded bounding box
                    launch(Dispatchers.IO) {
                        val pois = placeSearchManager.fetchRegionPoisForOfflineCache(region)
                        if (pois.isNotEmpty()) {
                            repository.saveOfflinePlaces(region.id, pois)
                        }
                    }
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
        if (follow) {
            _isMapFollowPaused.value = false
        }
    }

    fun setMapFollowPaused(paused: Boolean) {
        _isMapFollowPaused.value = paused
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
        val cleaned = apiKey.trim()
        repository.saveOpenCellIdApiKey(cleaned)
        _settings.value = _settings.value.copy(openCellIdApiKey = cleaned)
        com.geotrace.service.OpenCellIdClient.clearCache()
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

    fun sendTestAcademicNotification(): Boolean {
        if (!_settings.value.geofenceNotificationEnabled) return false
        val academicZone = geofenceZones.value.firstOrNull { it.category.isAcademicZone }
            ?: geofenceZones.value.firstOrNull()
        val zoneName = academicZone?.name ?: "MUST Kihumuro Campus (FAST)"
        val emoji = academicZone?.category?.iconEmoji ?: "🎓"
        val label = academicZone?.category?.label ?: "University Campus"
        return com.geotrace.service.GeofenceBroadcastReceiver.sendNotification(
            context = getApplication(),
            title = "$emoji $label: Entered (Test Alert)",
            content = "Geofencing API alert active for $zoneName",
            notificationId = 2999
        )
    }

    fun refreshSearchNetworkStatus() {
        _isNetworkOnline.value = placeSearchManager.isNetworkConnected()
    }

    fun updateSearchQuery(
        query: String,
        deviceAndPeerTargets: List<SearchPlaceResult> = emptyList()
    ) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        val onlineNow = placeSearchManager.isNetworkConnected()
        _isNetworkOnline.value = onlineNow
        _searchError.value = null

        searchJob = viewModelScope.launch {
            val curLoc = currentLocation.value
            fun withDist(item: SearchPlaceResult): SearchPlaceResult {
                val d = curLoc?.let { repository.calculateDistanceMeters(it.latitude, it.longitude, item.latitude, item.longitude) }
                return item.copy(distanceMeters = d)
            }

            val localMatches = mutableListOf<SearchPlaceResult>()

            // 1. Direct coordinate / DMS / geo URI / Maps link parser (0ms offline)
            val parsedCoord = placeSearchManager.parseDirectCoordinates(trimmed)
            if (parsedCoord != null) {
                localMatches.add(withDist(parsedCoord))
            }

            // 2. Recent searches (shown when blank or matching query)
            val recents = placeSearchManager.loadRecentSearches().filter {
                trimmed.isBlank() ||
                    it.name.contains(trimmed, ignoreCase = true) ||
                    it.subtitle.contains(trimmed, ignoreCase = true)
            }.map { withDist(it) }

            // 3. Local Geofence Safe Zones
            val safeZoneMatches = geofenceZones.value.filter {
                trimmed.isBlank() ||
                    it.name.contains(trimmed, ignoreCase = true) ||
                    it.category.label.contains(trimmed, ignoreCase = true)
            }.take(if (trimmed.isBlank()) 4 else 8).map { z ->
                withDist(
                    SearchPlaceResult(
                        id = "zone_${z.id}",
                        name = z.name,
                        subtitle = "${z.category.label} • ${z.radiusMeters.toInt()}m radius",
                        latitude = z.latitude,
                        longitude = z.longitude,
                        source = SearchResultSource.SAFE_ZONE
                    )
                )
            }

            // 4. Saved Offline Map Regions
            val regionMatches = offlineMapRegions.value.filter {
                trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true)
            }.take(4).map { reg ->
                withDist(
                    SearchPlaceResult(
                        id = "reg_${reg.id}",
                        name = reg.name,
                        subtitle = "${reg.tileType.label} • Offline cached area",
                        latitude = reg.centerLat,
                        longitude = reg.centerLng,
                        source = SearchResultSource.OFFLINE_REGION,
                        bboxNorth = reg.northLat,
                        bboxSouth = reg.southLat,
                        bboxEast = reg.eastLng,
                        bboxWest = reg.westLng
                    )
                )
            }

            // 5. Active, Inspected & Historical Saved Waypoints across all trails
            val liveAndInspectedWpts = (activeWaypoints.value + (inspectedTrail.value?.waypoints ?: emptyList()))
            val dbSavedWpts = if (trimmed.isNotBlank()) repository.searchSavedWaypoints(trimmed, limit = 12) else emptyList()
            val wpts = (liveAndInspectedWpts + dbSavedWpts)
                .distinctBy { it.id }
                .filter {
                    trimmed.isNotBlank() && (
                        it.name.contains(trimmed, ignoreCase = true) ||
                            it.note.contains(trimmed, ignoreCase = true)
                        )
                }
                .take(8)
                .map { wp ->
                    withDist(
                        SearchPlaceResult(
                            id = "wpt_${wp.id}",
                            name = wp.name,
                            subtitle = wp.note.ifBlank { "Manual Trail Waypoint" },
                            latitude = wp.latitude,
                            longitude = wp.longitude,
                            source = SearchResultSource.WAYPOINT
                        )
                    )
                }

            // 6. Active Group Members, Personal Fleet Devices & P2P Peers
            val deviceMatches = deviceAndPeerTargets.filter {
                trimmed.isNotBlank() && (
                    it.name.contains(trimmed, ignoreCase = true) ||
                        it.subtitle.contains(trimmed, ignoreCase = true)
                    )
            }.take(6).map { withDist(it) }

            // 7. Cached Offline POIs from downloaded regions (SQLite LIKE query)
            val offlinePoiMatches = if (trimmed.isNotBlank()) {
                repository.searchOfflinePlaces(trimmed, limit = 15).map { poi ->
                    withDist(
                        SearchPlaceResult(
                            id = "poi_${poi.id}",
                            name = poi.name,
                            subtitle = poi.subtitle,
                            latitude = poi.latitude,
                            longitude = poi.longitude,
                            source = SearchResultSource.OFFLINE_POI
                        )
                    )
                }
            } else {
                emptyList()
            }

            if (trimmed.isBlank()) {
                // On blank query show Recents first, then Safe Zones & Offline Regions
                localMatches.addAll(recents)
                localMatches.addAll(safeZoneMatches)
                localMatches.addAll(regionMatches)
            } else {
                // On active search prefer authoritative typed local sources before Recents
                localMatches.addAll(safeZoneMatches)
                localMatches.addAll(wpts)
                localMatches.addAll(deviceMatches)
                localMatches.addAll(regionMatches)
                localMatches.addAll(offlinePoiMatches)
                localMatches.addAll(recents)
            }

            val dedupLocal = deduplicateSearchResults(localMatches)
            _searchResults.value = dedupLocal

            // 8. Online Geocoding (debounced) when query >= 2 chars, not a direct coordinate, and device is online
            if (trimmed.length >= 2 && parsedCoord == null && onlineNow) {
                _isSearchingOnline.value = true
                delay(350L)
                if (!isActive) return@launch
                val viewBox = _currentMapBoundingBox.value
                val biasLat = curLoc?.latitude ?: viewBox?.centerLatitude
                val biasLon = curLoc?.longitude ?: viewBox?.centerLongitude
                val outcome = placeSearchManager.searchOnline(trimmed, biasLat, biasLon, viewBox)
                if (isActive) {
                    val onlineWithDist = outcome.results.map { withDist(it) }
                    _searchError.value = outcome.errorMessage
                    _searchResults.value = deduplicateSearchResults(dedupLocal + onlineWithDist)
                    _isSearchingOnline.value = false
                }
            } else {
                _isSearchingOnline.value = false
            }
        }
    }

    private fun deduplicateSearchResults(items: List<SearchPlaceResult>): List<SearchPlaceResult> {
        val seen = HashSet<String>()
        val out = mutableListOf<SearchPlaceResult>()
        for (item in items) {
            // Omit source from key so a recently selected Safe Zone / Offline POI / OSM place never renders twice
            val key = "${item.name.trim().lowercase(java.util.Locale.US)}:${
                String.format(java.util.Locale.US, "%.4f,%.4f", item.latitude, item.longitude)
            }"
            if (seen.add(key)) {
                out.add(item)
            }
        }
        return out
    }

    fun selectSearchResult(place: SearchPlaceResult) {
        if (place.source != SearchResultSource.DEVICE_OR_PEER) {
            placeSearchManager.saveRecentSearch(place)
        }
        _isMapFollowPaused.value = true
        _selectedSearchPin.value = place.copy(requestedAtMs = System.currentTimeMillis())
    }

    fun clearSelectedSearchPin() {
        _selectedSearchPin.value = null
    }

    fun clearRecentSearches(deviceAndPeerTargets: List<SearchPlaceResult> = emptyList()) {
        placeSearchManager.clearRecentSearches()
        updateSearchQuery(_searchQuery.value, deviceAndPeerTargets)
    }
}
