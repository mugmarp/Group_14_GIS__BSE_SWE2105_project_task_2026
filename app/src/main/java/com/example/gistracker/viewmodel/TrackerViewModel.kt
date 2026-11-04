package com.example.gistracker.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gistracker.LocationTrackerApp
import com.example.gistracker.data.export.TrackExporter
import com.example.gistracker.model.CellTowerInfo
import com.example.gistracker.model.GeofenceCategory
import com.example.gistracker.model.GeofenceZone
import com.example.gistracker.model.LocationPoint
import com.example.gistracker.model.TrackingMode
import com.example.gistracker.model.TrackingSession
import com.example.gistracker.service.LocationForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _isSimulatingMovement = MutableStateFlow(false)
    val isSimulatingMovement: StateFlow<Boolean> = _isSimulatingMovement.asStateFlow()

    private var simulationJob: Job? = null

    // Realistic demonstration path along Kampala Road -> Kololo -> Mulago (or Mbarara MUST)
    private val simulationWaypoints = listOf(
        Pair(0.3136, 32.5811), // Kampala Road Central
        Pair(0.3160, 32.5830),
        Pair(0.3200, 32.5855), // Jinja Road junction
        Pair(0.3250, 32.5820), // Wampewo Ave
        Pair(0.3310, 32.5780), // Kololo Hill base
        Pair(0.3340, 32.5710), // Wandegeya Boda Hotspot
        Pair(0.3385, 32.5750), // Towards Mulago
        Pair(0.3420, 32.5775), // Mulago Hospital Entrance
        Pair(0.3478, 32.5822)  // Mulago Hill Top
    )

    private val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(application)

    init {
        requestLastKnownLocation()
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
            repository.setTrackingMode(mode)
            repository.startNewSession(sessionName, mode)
            LocationForegroundService.startService(getApplication(), mode)
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            repository.stopCurrentSession()
            LocationForegroundService.stopService(getApplication())
            if (_isSimulatingMovement.value) {
                toggleSimulation(false)
            }
        }
    }

    fun setTrackingMode(mode: TrackingMode) {
        viewModelScope.launch {
            repository.setTrackingMode(mode)
            if (activeSession.value != null) {
                LocationForegroundService.startService(getApplication(), mode)
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

    fun toggleSimulation(enable: Boolean) {
        _isSimulatingMovement.value = enable
        repository.setSimulationActive(enable)
        if (enable) {
            simulationJob?.cancel()
            simulationJob = viewModelScope.launch {
                if (activeSession.value == null) {
                    startTracking("Simulation Demo Route", trackingMode.value)
                    delay(300L) // Allow repository to initialize active session
                }

                var step = 0
                var bearing = 45f
                while (isActive && _isSimulatingMovement.value) {
                    val waypoint = simulationWaypoints[step % simulationWaypoints.size]
                    // Add subtle GPS jitter to simulate real satellite multipath
                    val jitterLat = (Math.random() - 0.5) * 0.0001
                    val jitterLon = (Math.random() - 0.5) * 0.0001

                    val simulatedSpeed = 8.5f + (Math.random() * 4.0).toFloat() // ~30-45 km/h boda speed
                    bearing = (bearing + (Math.random() * 10 - 5).toFloat()).coerceIn(0f, 360f)

                    val currentSessionId = activeSession.value?.id ?: "sim"
                    val p = LocationPoint(
                        latitude = waypoint.first + jitterLat,
                        longitude = waypoint.second + jitterLon,
                        accuracy = 3.5f + (Math.random() * 2.5).toFloat(),
                        speed = simulatedSpeed,
                        altitude = 1190.0 + (Math.random() * 15.0),
                        bearing = bearing,
                        provider = "simulation",
                        sessionId = currentSessionId
                    )

                    repository.recordLocation(p)

                    step++
                    delay(2000L)
                }
            }
        } else {
            simulationJob?.cancel()
            simulationJob = null
            // If the active session was the demo route, stop it cleanly
            if (activeSession.value?.name?.contains("Simulation", ignoreCase = true) == true) {
                stopTracking()
            }
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

    fun shareEmergencyAlert(context: Context) {
        val curr = currentLocation.value
        val topCell = cellTowers.value.firstOrNull()
        val text = TrackExporter.buildEmergencyShareMessage(curr, topCell)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Emergency Broadcast: Share Current Coordinates")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun refreshCellTowers() {
        val towers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(towers)
    }

    fun loadAcademicReferenceTowers() {
        val demoTowers = cellManager.createDemonstrationTowers()
        repository.updateCellTowers(demoTowers)
    }
}
