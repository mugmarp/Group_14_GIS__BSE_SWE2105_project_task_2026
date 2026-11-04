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
}
