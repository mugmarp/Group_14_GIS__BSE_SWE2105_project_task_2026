package com.geotrace.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotrace.LocationTrackerApp
import com.geotrace.data.export.TrackExporter
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.GeofenceCategory
import com.geotrace.model.GeofenceZone
import com.geotrace.model.LocationPoint
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
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
    val allSessions: StateFlow<List<TrackingSession>> = repository.allSessions
    val geofenceZones: StateFlow<List<GeofenceZone>> = repository.geofenceZones
    val recentAlerts = repository.recentAlerts
    val cellTowers: StateFlow<List<CellTowerInfo>> = repository.cellTowers
    val trackingMode: StateFlow<TrackingMode> = repository.trackingMode
    val filteredJitterMeters: StateFlow<Double> = repository.filteredJitterMeters
    val filteredJitterCount: StateFlow<Int> = repository.filteredJitterCount

    private val _cellTowerLocation = MutableStateFlow<com.geotrace.model.CellTowerLocation?>(null)
    val cellTowerLocation: StateFlow<com.geotrace.model.CellTowerLocation?> = _cellTowerLocation.asStateFlow()

    private val _allCellTowerLocations = MutableStateFlow<List<com.geotrace.model.CellTowerLocation>>(emptyList())
    val allCellTowerLocations: StateFlow<List<com.geotrace.model.CellTowerLocation>> = _allCellTowerLocations.asStateFlow()

    private val _settings = MutableStateFlow(
        com.geotrace.model.TrackerSettings(
            openCellIdApiKey = repository.getSavedOpenCellIdApiKey(),
            autoBalancedTestMode = repository.trackingMode.value == TrackingMode.BALANCED,
            geofenceNotificationEnabled = repository.geofenceNotificationsEnabled
        )
    )
    val settings: StateFlow<com.geotrace.model.TrackerSettings> = _settings.asStateFlow()

    val geofenceManager = com.geotrace.service.GeofenceManager(application)
    val geofenceApiStatus: StateFlow<com.geotrace.service.GeofenceApiStatus> = geofenceManager.apiStatus

    val appUpdateManager = com.geotrace.service.AppUpdateManager(application)
    val updateStatus = appUpdateManager.updateStatus

    private val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(application)
    private var standbyCallback: com.google.android.gms.location.LocationCallback? = null
    private var standbyCellJob: Job? = null
    private var lastCellularFallbackKey: String? = null
    private var lastCellularFallbackTimeMs: Long = 0L

    init {
        startStandbyLocationUpdates()
        startStandbyCellPolling()
        observeCellTowerLocation()
        observeGeofencingSync()
    }

    private fun observeGeofencingSync() {
        viewModelScope.launch {
            combine(geofenceZones, settings) { zones, currentSettings ->
                Pair(zones, currentSettings.geofenceNotificationEnabled)
            }.collect { (zones, enabled) ->
                if (enabled) {
                    geofenceManager.syncGeofences(zones, true)
                } else {
                    geofenceManager.syncGeofences(emptyList(), false)
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

                // In Cell-Only mode: fall back to OpenCellID-resolved serving tower only when needed
                if (mode == TrackingMode.POWER_SAVER) {
                    _cellTowerLocation.value?.let { towerLoc ->
                        val now = System.currentTimeMillis()
                        val towerKey = "${towerLoc.cellId}_${towerLoc.lac}"
                        val isCurrentStale = loc == null || (now - loc.timestamp > 25_000L)
                        val changedTower = lastCellularFallbackKey != towerKey
                        if ((isCurrentStale || changedTower) && (now - lastCellularFallbackTimeMs >= 10_000L || changedTower)) {
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

    @android.annotation.SuppressLint("MissingPermission")
    fun startStandbyLocationUpdates(targetMode: TrackingMode = trackingMode.value) {
        requestLastKnownLocation()
        stopStandbyLocationUpdates()

        val (priority, interval, minDistance) = when (targetMode) {
            TrackingMode.HIGH_ACCURACY -> Triple(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2500L, 1.0f)
            TrackingMode.BALANCED -> Triple(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L, 10.0f)
            TrackingMode.POWER_SAVER -> Triple(com.google.android.gms.location.Priority.PRIORITY_LOW_POWER, 20000L, 50.0f)
        }

        val request = com.google.android.gms.location.LocationRequest.Builder(
            priority,
            interval
        )
            .setMinUpdateDistanceMeters(minDistance)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()

        standbyCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: return
                // Only post from standby callback if no active foreground recording service is managing updates
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
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                standbyCallback!!,
                android.os.Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Handled via UI permission request flow
        } catch (e: Exception) {
            // Hardware/service unavailable
        }
    }

    fun stopStandbyLocationUpdates() {
        standbyCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            standbyCallback = null
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun requestLastKnownLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && currentLocation.value == null) {
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
            stopStandbyLocationUpdates()
            repository.setTrackingMode(mode)
            repository.startNewSession(sessionName, mode)
            LocationForegroundService.startService(getApplication(), mode)
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            repository.stopCurrentSession()
            LocationForegroundService.stopService(getApplication())
            startStandbyLocationUpdates()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStandbyLocationUpdates()
        standbyCellJob?.cancel()
    }

    fun setTrackingMode(mode: TrackingMode) {
        viewModelScope.launch {
            repository.setTrackingMode(mode)
            _settings.value = _settings.value.copy(autoBalancedTestMode = (mode == TrackingMode.BALANCED))
            if (activeSession.value != null) {
                LocationForegroundService.updateMode(getApplication(), mode)
            } else {
                startStandbyLocationUpdates(mode)
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

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun exportGpx(session: TrackingSession, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val points = repository.getPointsForSession(session.id)
            val gpx = TrackExporter.toGpx(session, points)
            onReady(gpx)
        }
    }

    fun exportKml(session: TrackingSession, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val points = repository.getPointsForSession(session.id)
            val kml = TrackExporter.toKml(session, points)
            onReady(kml)
        }
    }

    fun exportCsv(session: TrackingSession, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val points = repository.getPointsForSession(session.id)
            val csv = TrackExporter.toCsv(session, points)
            onReady(csv)
        }
    }

    fun refreshCellTowers() {
        val towers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(towers)
    }

    fun updateSpeedUnit(unit: com.geotrace.model.SpeedUnit) {
        _settings.value = _settings.value.copy(speedUnit = unit)
    }

    fun updateCoordinateFormat(format: com.geotrace.model.CoordinateFormat) {
        _settings.value = _settings.value.copy(coordinateFormat = format)
    }

    fun updateMapTileType(tileType: com.geotrace.model.MapTileType) {
        _settings.value = _settings.value.copy(mapTileType = tileType)
    }

    fun toggleAccuracyRing(show: Boolean) {
        _settings.value = _settings.value.copy(showAccuracyRing = show)
    }

    fun toggleFollowUser(follow: Boolean) {
        _settings.value = _settings.value.copy(followUserLocation = follow)
    }

    fun updateJitterThreshold(thresholdMeters: Float) {
        _settings.value = _settings.value.copy(stationaryJitterThresholdMeters = thresholdMeters)
        repository.jitterThresholdMeters = thresholdMeters
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return repository.calculateDistanceMeters(lat1, lon1, lat2, lon2)
    }

    fun toggleKeepScreenOn(keepOn: Boolean) {
        _settings.value = _settings.value.copy(keepScreenOn = keepOn)
    }

    fun toggleCellTowerPin(show: Boolean) {
        _settings.value = _settings.value.copy(showCellTowerPin = show)
    }

    fun updateOpenCellIdApiKey(apiKey: String) {
        repository.saveOpenCellIdApiKey(apiKey)
        _settings.value = _settings.value.copy(openCellIdApiKey = apiKey)
        refreshCellTowers()
    }

    fun toggleAutoBalancedTestMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoBalancedTestMode = enabled)
        setTrackingMode(if (enabled) TrackingMode.BALANCED else TrackingMode.HIGH_ACCURACY)
    }

    fun toggleGeofenceNotifications(enabled: Boolean) {
        repository.setGeofenceNotificationsEnabled(enabled)
        _settings.value = _settings.value.copy(geofenceNotificationEnabled = enabled)
        geofenceManager.syncGeofences(geofenceZones.value, enabled)
    }

    fun resyncGeofencingApi() {
        val enabled = _settings.value.geofenceNotificationEnabled
        geofenceManager.syncGeofences(geofenceZones.value, enabled)
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
