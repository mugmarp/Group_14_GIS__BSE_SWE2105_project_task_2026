package com.geotrace.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.GeofenceAlert
import com.geotrace.model.GeofenceCategory
import com.geotrace.model.GeofenceZone
import com.geotrace.model.LocationPoint
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationRepository(private val context: Context) {

    private val dbHelper = LocationDbHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    private val _activeSession = MutableStateFlow<TrackingSession?>(null)
    val activeSession: StateFlow<TrackingSession?> = _activeSession.asStateFlow()

    private val _activeTrailPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val activeTrailPoints: StateFlow<List<LocationPoint>> = _activeTrailPoints.asStateFlow()

    private val _allSessions = MutableStateFlow<List<TrackingSession>>(emptyList())
    val allSessions: StateFlow<List<TrackingSession>> = _allSessions.asStateFlow()

    private val _geofenceZones = MutableStateFlow<List<GeofenceZone>>(emptyList())
    val geofenceZones: StateFlow<List<GeofenceZone>> = _geofenceZones.asStateFlow()

    private val _recentAlerts = MutableStateFlow<List<GeofenceAlert>>(emptyList())
    val recentAlerts: StateFlow<List<GeofenceAlert>> = _recentAlerts.asStateFlow()

    private val _cellTowers = MutableStateFlow<List<CellTowerInfo>>(emptyList())
    val cellTowers: StateFlow<List<CellTowerInfo>> = _cellTowers.asStateFlow()

    private val _trackingMode = MutableStateFlow(TrackingMode.HIGH_ACCURACY)
    val trackingMode: StateFlow<TrackingMode> = _trackingMode.asStateFlow()

    private val _filteredJitterMeters = MutableStateFlow(0.0)
    val filteredJitterMeters: StateFlow<Double> = _filteredJitterMeters.asStateFlow()

    private val _filteredJitterCount = MutableStateFlow(0)
    val filteredJitterCount: StateFlow<Int> = _filteredJitterCount.asStateFlow()

    @Volatile
    var jitterThresholdMeters: Float = 1.5f

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        scope.launch {
            loadGeofences()
            loadSessions()
            loadAlerts()
        }
    }

    suspend fun setTrackingMode(mode: TrackingMode) {
        _trackingMode.value = mode
    }

    suspend fun startNewSession(name: String = "Track ${System.currentTimeMillis() % 10000}", mode: TrackingMode = _trackingMode.value): TrackingSession = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val session = TrackingSession(
            id = id,
            name = name,
            startTime = System.currentTimeMillis(),
            trackingMode = mode,
            isRecording = true
        )

        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", session.id)
            put("name", session.name)
            put("start_time", session.startTime)
            put("distance", 0.0)
            put("avg_speed", 0.0)
            put("max_speed", 0.0)
            put("point_count", 0)
            put("tracking_mode", session.trackingMode.name)
            put("is_recording", 1)
        }
        db.insert(LocationDbHelper.TABLE_SESSIONS, null, cv)

        _activeSession.value = session
        _activeTrailPoints.value = emptyList()
        loadSessions()
        session
    }

    suspend fun stopCurrentSession(): TrackingSession? = withContext(Dispatchers.IO) {
        val current = _activeSession.value ?: return@withContext null
        val endTime = System.currentTimeMillis()
        val db = dbHelper.writableDatabase

        // Audit points in SQLite database to ensure the saved record matches actual persisted points
        var dbCount = 0
        var dbDistance = 0.0
        val cursor = db.query(
            LocationDbHelper.TABLE_POINTS,
            arrayOf("latitude", "longitude"),
            "session_id = ?",
            arrayOf(current.id),
            null, null, "timestamp ASC"
        )
        cursor.use { c ->
            var lastLat: Double? = null
            var lastLon: Double? = null
            while (c.moveToNext()) {
                dbCount++
                val lat = c.getDouble(0)
                val lon = c.getDouble(1)
                if (lastLat != null && lastLon != null) {
                    val stepDist = calculateDistanceMeters(lastLat, lastLon, lat, lon)
                    if (stepDist in 1.5..5000.0) {
                        dbDistance += stepDist
                    }
                }
                lastLat = lat
                lastLon = lon
            }
        }

        val finalDistance = if (current.totalDistanceMeters > 0.0) current.totalDistanceMeters else dbDistance
        val finalCount = if (current.pointCount > 0) current.pointCount else dbCount
        val elapsedSec = ((endTime - current.startTime) / 1000.0).coerceAtLeast(1.0)
        val finalAvgSpeed = (finalDistance / elapsedSec) * 3.6

        val updated = current.copy(
            endTime = endTime,
            totalDistanceMeters = finalDistance,
            pointCount = finalCount,
            avgSpeedKmh = finalAvgSpeed,
            isRecording = false
        )

        val cv = ContentValues().apply {
            put("end_time", endTime)
            put("is_recording", 0)
            put("distance", finalDistance)
            put("avg_speed", finalAvgSpeed)
            put("max_speed", updated.maxSpeedKmh)
            put("point_count", finalCount)
        }
        db.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(current.id))

        _activeSession.value = null
        _activeTrailPoints.value = emptyList()
        loadSessions()
        updated
    }

    suspend fun recordLocation(point: LocationPoint) = withContext(Dispatchers.IO) {
        // Discard null island and NaN glitch coordinates
        if ((point.latitude == 0.0 && point.longitude == 0.0) || point.latitude.isNaN() || point.longitude.isNaN()) {
            return@withContext
        }


        _currentLocation.value = point

        val session = _activeSession.value
        if (session != null && session.isRecording) {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("session_id", session.id)
                put("latitude", point.latitude)
                put("longitude", point.longitude)
                put("accuracy", point.accuracy)
                put("speed", point.speed)
                put("altitude", point.altitude)
                put("bearing", point.bearing)
                put("provider", point.provider)
                put("timestamp", point.timestamp)
            }
            db.insert(LocationDbHelper.TABLE_POINTS, null, cv)

            val prevPoint = _activeTrailPoints.value.lastOrNull()
            _activeTrailPoints.value = _activeTrailPoints.value + point

            // Calculate incremental distance with multipath & teleportation jump rejection
            val additionalDistance: Double = if (prevPoint != null) {
                val dist = calculateDistanceMeters(prevPoint.latitude, prevPoint.longitude, point.latitude, point.longitude)
                val timeDiffSec = ((point.timestamp - prevPoint.timestamp) / 1000.0).coerceAtLeast(0.1)
                val impliedSpeedMps = dist / timeDiffSec
                // Discard sudden GPS teleportation/multipath jump (> 5km or implied speed > 200 km/h)
                if (dist > 5000.0 || impliedSpeedMps > 55.0) {
                    0.0
                } else if (jitterThresholdMeters > 0f && dist < jitterThresholdMeters) {
                    _filteredJitterMeters.value += dist
                    _filteredJitterCount.value += 1
                    0.0 // Discard stationary jitter according to configurable sensitivity
                } else {
                    dist
                }
            } else {
                0.0
            }

            val newDistance = session.totalDistanceMeters + additionalDistance
            val newPointCount = session.pointCount + 1
            val currentSpeedKmh = point.speedKmh.toDouble()
            val newMaxSpeed = maxOf(session.maxSpeedKmh, currentSpeedKmh)

            val elapsedSec = ((point.timestamp - session.startTime) / 1000.0).coerceAtLeast(1.0)
            val newAvgSpeed = (newDistance / elapsedSec) * 3.6

            val updatedSession = session.copy(
                totalDistanceMeters = newDistance,
                pointCount = newPointCount,
                maxSpeedKmh = newMaxSpeed,
                avgSpeedKmh = newAvgSpeed
            )
            _activeSession.value = updatedSession

            val updateCv = ContentValues().apply {
                put("distance", newDistance)
                put("avg_speed", newAvgSpeed)
                put("max_speed", newMaxSpeed)
                put("point_count", newPointCount)
            }
            db.update(LocationDbHelper.TABLE_SESSIONS, updateCv, "id = ?", arrayOf(session.id))
        }

        // Check geofences
        checkGeofences(point.latitude, point.longitude)
    }

    fun updateCellTowers(towers: List<CellTowerInfo>) {
        _cellTowers.value = towers
    }

    suspend fun loadSessions() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<TrackingSession>()
        val cursor: Cursor = db.query(
            LocationDbHelper.TABLE_SESSIONS,
            null, null, null, null, null,
            "start_time DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val modeStr = c.getString(c.getColumnIndexOrThrow("tracking_mode"))
                val mode = try {
                    TrackingMode.valueOf(modeStr)
                } catch (e: Exception) {
                    TrackingMode.HIGH_ACCURACY
                }
                list.add(
                    TrackingSession(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        startTime = c.getLong(c.getColumnIndexOrThrow("start_time")),
                        endTime = if (c.isNull(c.getColumnIndexOrThrow("end_time"))) null else c.getLong(c.getColumnIndexOrThrow("end_time")),
                        totalDistanceMeters = c.getDouble(c.getColumnIndexOrThrow("distance")),
                        avgSpeedKmh = c.getDouble(c.getColumnIndexOrThrow("avg_speed")),
                        maxSpeedKmh = c.getDouble(c.getColumnIndexOrThrow("max_speed")),
                        pointCount = c.getInt(c.getColumnIndexOrThrow("point_count")),
                        trackingMode = mode,
                        isRecording = c.getInt(c.getColumnIndexOrThrow("is_recording")) == 1
                    )
                )
            }
        }
        _allSessions.value = list
    }

    suspend fun getPointsForSession(sessionId: String): List<LocationPoint> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<LocationPoint>()
        val cursor = db.query(
            LocationDbHelper.TABLE_POINTS,
            null,
            "session_id = ?",
            arrayOf(sessionId),
            null, null,
            "timestamp ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    LocationPoint(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        accuracy = c.getFloat(c.getColumnIndexOrThrow("accuracy")),
                        speed = c.getFloat(c.getColumnIndexOrThrow("speed")),
                        altitude = c.getDouble(c.getColumnIndexOrThrow("altitude")),
                        bearing = c.getFloat(c.getColumnIndexOrThrow("bearing")),
                        provider = c.getString(c.getColumnIndexOrThrow("provider")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        list
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(LocationDbHelper.TABLE_POINTS, "session_id = ?", arrayOf(sessionId))
        db.delete(LocationDbHelper.TABLE_SESSIONS, "id = ?", arrayOf(sessionId))
        if (_activeSession.value?.id == sessionId) {
            _activeSession.value = null
            _activeTrailPoints.value = emptyList()
        }
        loadSessions()
    }

    suspend fun loadGeofences() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<GeofenceZone>()
        val cursor = db.query(LocationDbHelper.TABLE_GEOFENCES, null, null, null, null, null, "name ASC")
        cursor.use { c ->
            while (c.moveToNext()) {
                val catStr = c.getString(c.getColumnIndexOrThrow("category"))
                val category = try {
                    GeofenceCategory.valueOf(catStr)
                } catch (e: Exception) {
                    GeofenceCategory.SAFE_ZONE
                }
                list.add(
                    GeofenceZone(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        radiusMeters = c.getFloat(c.getColumnIndexOrThrow("radius")),
                        category = category,
                        colorHex = c.getString(c.getColumnIndexOrThrow("color_hex")),
                        alertOnEntry = c.getInt(c.getColumnIndexOrThrow("alert_on_entry")) == 1,
                        alertOnExit = c.getInt(c.getColumnIndexOrThrow("alert_on_exit")) == 1,
                        isInside = c.getInt(c.getColumnIndexOrThrow("is_inside")) == 1,
                        lastTransitionTime = if (c.isNull(c.getColumnIndexOrThrow("last_transition"))) null else c.getLong(c.getColumnIndexOrThrow("last_transition"))
                    )
                )
            }
        }
        if (list.isEmpty()) {
            // Seed comprehensive MUST (Mbarara University) & Safe Zones demonstration perimeters
            val defaultZones = listOf(
                GeofenceZone(
                    id = "must_kihumuro_campus",
                    name = "MUST Kihumuro Campus (FAST)",
                    latitude = -0.5982,
                    longitude = 30.6178,
                    radiusMeters = 350f,
                    category = GeofenceCategory.CAMPUS,
                    colorHex = "#006948",
                    alertOnEntry = true,
                    alertOnExit = true
                ),
                GeofenceZone(
                    id = "must_kihumuro_library",
                    name = "MUST Kihumuro Main Library",
                    latitude = -0.5976,
                    longitude = 30.6185,
                    radiusMeters = 100f,
                    category = GeofenceCategory.LIBRARY,
                    colorHex = "#0051D5",
                    alertOnEntry = true,
                    alertOnExit = true
                ),
                GeofenceZone(
                    id = "must_kihumuro_hostels",
                    name = "MUST Kihumuro Hostels Zone",
                    latitude = -0.5995,
                    longitude = 30.6155,
                    radiusMeters = 250f,
                    category = GeofenceCategory.HOSTEL,
                    colorHex = "#7C3AED",
                    alertOnEntry = true,
                    alertOnExit = true
                ),
                GeofenceZone(
                    id = "must_main_town",
                    name = "MUST Main Town Campus",
                    latitude = -0.6163,
                    longitude = 30.6568,
                    radiusMeters = 300f,
                    category = GeofenceCategory.CAMPUS,
                    colorHex = "#006948",
                    alertOnEntry = true,
                    alertOnExit = true
                ),
                GeofenceZone(
                    id = "must_town_library",
                    name = "MUST Town Campus Library",
                    latitude = -0.6158,
                    longitude = 30.6575,
                    radiusMeters = 90f,
                    category = GeofenceCategory.LIBRARY,
                    colorHex = "#0051D5",
                    alertOnEntry = true,
                    alertOnExit = true
                ),
                GeofenceZone(
                    id = "must_kakoba_hostels",
                    name = "MUST Kakoba Student Hostels",
                    latitude = -0.6120,
                    longitude = 30.6620,
                    radiusMeters = 250f,
                    category = GeofenceCategory.HOSTEL,
                    colorHex = "#7C3AED",
                    alertOnEntry = true,
                    alertOnExit = true
                ),
                GeofenceZone(
                    id = "wandegeya_boda",
                    name = "Wandegeya Boda Boda Stage",
                    latitude = 0.3308,
                    longitude = 32.5705,
                    radiusMeters = 160f,
                    category = GeofenceCategory.BODA_HOTSPOT,
                    colorHex = "#D97706",
                    alertOnEntry = true,
                    alertOnExit = true
                )
            )
            val writeDb = dbHelper.writableDatabase
            for (z in defaultZones) {
                val cv = ContentValues().apply {
                    put("id", z.id)
                    put("name", z.name)
                    put("latitude", z.latitude)
                    put("longitude", z.longitude)
                    put("radius", z.radiusMeters)
                    put("category", z.category.name)
                    put("color_hex", z.colorHex)
                    put("alert_on_entry", if (z.alertOnEntry) 1 else 0)
                    put("alert_on_exit", if (z.alertOnExit) 1 else 0)
                    put("is_inside", 0)
                }
                writeDb.insertWithOnConflict(LocationDbHelper.TABLE_GEOFENCES, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            _geofenceZones.value = defaultZones
        } else {
            _geofenceZones.value = list
        }
    }

    suspend fun addGeofence(zone: GeofenceZone) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", zone.id)
            put("name", zone.name)
            put("latitude", zone.latitude)
            put("longitude", zone.longitude)
            put("radius", zone.radiusMeters)
            put("category", zone.category.name)
            put("color_hex", zone.colorHex)
            put("alert_on_entry", if (zone.alertOnEntry) 1 else 0)
            put("alert_on_exit", if (zone.alertOnExit) 1 else 0)
            put("is_inside", if (zone.isInside) 1 else 0)
            put("last_transition", zone.lastTransitionTime)
        }
        db.insertWithOnConflict(LocationDbHelper.TABLE_GEOFENCES, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        loadGeofences()
    }

    suspend fun updateGeofence(zone: GeofenceZone) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("name", zone.name)
            put("latitude", zone.latitude)
            put("longitude", zone.longitude)
            put("radius", zone.radiusMeters)
            put("category", zone.category.name)
            put("color_hex", zone.colorHex)
            put("alert_on_entry", if (zone.alertOnEntry) 1 else 0)
            put("alert_on_exit", if (zone.alertOnExit) 1 else 0)
        }
        db.update(LocationDbHelper.TABLE_GEOFENCES, cv, "id = ?", arrayOf(zone.id))
        loadGeofences()
    }

    suspend fun deleteGeofence(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(LocationDbHelper.TABLE_GEOFENCES, "id = ?", arrayOf(id))
        loadGeofences()
    }

    private suspend fun checkGeofences(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        val zones = _geofenceZones.value
        val updatedZones = mutableListOf<GeofenceZone>()
        val newAlerts = mutableListOf<GeofenceAlert>()
        val db = dbHelper.writableDatabase

        // 15m exit hysteresis margin to eliminate boundary flapping caused by GPS multipath noise
        val hysteresisBufferMeters = 15.0

        for (z in zones) {
            val dist = calculateDistanceMeters(lat, lon, z.latitude, z.longitude)
            val isNowInside = if (z.isInside) {
                dist <= (z.radiusMeters + hysteresisBufferMeters)
            } else {
                dist <= z.radiusMeters
            }

            if (isNowInside != z.isInside) {
                val eventType = if (isNowInside) "ENTERED" else "EXITED"
                val shouldAlert = (isNowInside && z.alertOnEntry) || (!isNowInside && z.alertOnExit)
                val alert = if (shouldAlert) {
                    GeofenceAlert(
                        id = UUID.randomUUID().toString(),
                        zoneName = z.name,
                        eventType = eventType
                    )
                } else null

                if (alert != null) {
                    newAlerts.add(alert)
                    val alertCv = ContentValues().apply {
                        put("id", alert.id)
                        put("zone_name", alert.zoneName)
                        put("event_type", alert.eventType)
                        put("timestamp", alert.timestamp)
                    }
                    db.insert(LocationDbHelper.TABLE_ALERTS, null, alertCv)
                }

                val updatedZone = z.copy(isInside = isNowInside, lastTransitionTime = System.currentTimeMillis())
                updatedZones.add(updatedZone)

                val zoneCv = ContentValues().apply {
                    put("is_inside", if (isNowInside) 1 else 0)
                    put("last_transition", updatedZone.lastTransitionTime)
                }
                db.update(LocationDbHelper.TABLE_GEOFENCES, zoneCv, "id = ?", arrayOf(z.id))
            } else {
                updatedZones.add(z)
            }
        }

        if (newAlerts.isNotEmpty()) {
            _geofenceZones.value = updatedZones
            loadAlerts()
        }
    }

    private suspend fun loadAlerts() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<GeofenceAlert>()
        val cursor = db.query(LocationDbHelper.TABLE_ALERTS, null, null, null, null, null, "timestamp DESC", "20")
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    GeofenceAlert(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        zoneName = c.getString(c.getColumnIndexOrThrow("zone_name")),
                        eventType = c.getString(c.getColumnIndexOrThrow("event_type")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        _recentAlerts.value = list
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
