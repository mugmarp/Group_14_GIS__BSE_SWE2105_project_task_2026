package com.geotrace.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.GeofenceAlert
import com.geotrace.model.GeofenceCategory
import com.geotrace.model.GeofenceZone
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineMapRegion
import com.geotrace.model.OfflinePlacePoi
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationRepository(private val context: Context) {

    private val dbHelper = LocationDbHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val recordingMutex = Mutex()

    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    private val _activeSession = MutableStateFlow<TrackingSession?>(null)
    val activeSession: StateFlow<TrackingSession?> = _activeSession.asStateFlow()

    private val _activeTrailPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val activeTrailPoints: StateFlow<List<LocationPoint>> = _activeTrailPoints.asStateFlow()

    private val _activeWaypoints = MutableStateFlow<List<TrailWaypoint>>(emptyList())
    val activeWaypoints: StateFlow<List<TrailWaypoint>> = _activeWaypoints.asStateFlow()

    private val _allSessions = MutableStateFlow<List<TrackingSession>>(emptyList())
    val allSessions: StateFlow<List<TrackingSession>> = _allSessions.asStateFlow()

    private val _geofenceZones = MutableStateFlow<List<GeofenceZone>>(emptyList())
    val geofenceZones: StateFlow<List<GeofenceZone>> = _geofenceZones.asStateFlow()

    private val _recentAlerts = MutableStateFlow<List<GeofenceAlert>>(emptyList())
    val recentAlerts: StateFlow<List<GeofenceAlert>> = _recentAlerts.asStateFlow()

    private val _cellTowers = MutableStateFlow<List<CellTowerInfo>>(emptyList())
    val cellTowers: StateFlow<List<CellTowerInfo>> = _cellTowers.asStateFlow()

    private val _offlineMapRegions = MutableStateFlow<List<OfflineMapRegion>>(emptyList())
    val offlineMapRegions: StateFlow<List<OfflineMapRegion>> = _offlineMapRegions.asStateFlow()

    private val _offlinePlacesCount = MutableStateFlow(0)
    val offlinePlacesCount: StateFlow<Int> = _offlinePlacesCount.asStateFlow()

    private val prefs = context.getSharedPreferences("geotrace_prefs", Context.MODE_PRIVATE)

    private val _trackingMode = MutableStateFlow(loadInitialTrackingMode())
    val trackingMode: StateFlow<TrackingMode> = _trackingMode.asStateFlow()

    private val _filteredJitterMeters = MutableStateFlow(0.0)
    val filteredJitterMeters: StateFlow<Double> = _filteredJitterMeters.asStateFlow()

    private val _filteredJitterCount = MutableStateFlow(0)
    val filteredJitterCount: StateFlow<Int> = _filteredJitterCount.asStateFlow()

    @Volatile
    var geofenceNotificationsEnabled: Boolean = prefs.getBoolean("geofence_notifications_enabled", false)
        private set

    fun setGeofenceNotificationsEnabled(enabled: Boolean) {
        geofenceNotificationsEnabled = enabled
        prefs.edit().putBoolean("geofence_notifications_enabled", enabled).apply()
    }

    fun getSavedSpeedUnit(): SpeedUnit {
        val raw = prefs.getString("speed_unit", null) ?: return SpeedUnit.KMH
        return try { SpeedUnit.valueOf(raw) } catch (_: Exception) { SpeedUnit.KMH }
    }

    fun saveSpeedUnit(unit: SpeedUnit) {
        prefs.edit().putString("speed_unit", unit.name).apply()
    }

    fun getSavedCoordinateFormat(): CoordinateFormat {
        val raw = prefs.getString("coordinate_format", null) ?: return CoordinateFormat.DECIMAL
        return try { CoordinateFormat.valueOf(raw) } catch (_: Exception) { CoordinateFormat.DECIMAL }
    }

    fun saveCoordinateFormat(format: CoordinateFormat) {
        prefs.edit().putString("coordinate_format", format.name).apply()
    }

    fun getSavedShowAccuracyRing(): Boolean = prefs.getBoolean("show_accuracy_ring", true)

    fun saveShowAccuracyRing(show: Boolean) {
        prefs.edit().putBoolean("show_accuracy_ring", show).apply()
    }

    fun getSavedFollowUserLocation(): Boolean = prefs.getBoolean("follow_user_location", true)

    fun saveFollowUserLocation(follow: Boolean) {
        prefs.edit().putBoolean("follow_user_location", follow).apply()
    }

    fun getSavedKeepScreenOn(): Boolean = prefs.getBoolean("keep_screen_on", true)

    fun saveKeepScreenOn(keepOn: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", keepOn).apply()
    }

    fun getSavedShowCellTowerPin(): Boolean = prefs.getBoolean("show_cell_tower_pin", true)

    fun saveShowCellTowerPin(show: Boolean) {
        prefs.edit().putBoolean("show_cell_tower_pin", show).apply()
    }

    fun getSavedJitterThreshold(): Float = prefs.getFloat("jitter_threshold_meters", 1.5f)

    fun saveJitterThreshold(thresholdMeters: Float) {
        jitterThresholdMeters = thresholdMeters
        prefs.edit().putFloat("jitter_threshold_meters", thresholdMeters).apply()
    }

    fun getSavedOpenCellIdApiKey(): String = prefs.getString("opencellid_api_key", "") ?: ""

    fun saveOpenCellIdApiKey(apiKey: String) {
        prefs.edit().putString("opencellid_api_key", apiKey.trim()).apply()
    }

    fun getSavedMapTileType(): MapTileType {
        val raw = prefs.getString("map_tile_type", null) ?: return MapTileType.STANDARD
        return try {
            MapTileType.valueOf(raw)
        } catch (_: Exception) {
            MapTileType.STANDARD
        }
    }

    fun saveMapTileType(tileType: MapTileType) {
        prefs.edit().putString("map_tile_type", tileType.name).apply()
    }

    fun getSavedGeofenceHysteresis(): Float = prefs.getFloat("geofence_hysteresis_meters", 10f)

    fun saveGeofenceHysteresis(meters: Float) {
        geofenceHysteresisMeters = meters
        prefs.edit().putFloat("geofence_hysteresis_meters", meters).apply()
    }

    fun getSavedGeofenceAccuracyGate(): Boolean = prefs.getBoolean("geofence_accuracy_gate", true)

    fun saveGeofenceAccuracyGate(enabled: Boolean) {
        geofenceAccuracyGateEnabled = enabled
        prefs.edit().putBoolean("geofence_accuracy_gate", enabled).apply()
    }

    private fun loadInitialTrackingMode(): TrackingMode {
        val saved = prefs.getString("selected_tracking_mode", null)
        if (!saved.isNullOrBlank()) {
            try {
                return TrackingMode.valueOf(saved)
            } catch (_: Exception) {}
        }
        return TrackingMode.HIGH_ACCURACY
    }

    @Volatile
    var jitterThresholdMeters: Float = getSavedJitterThreshold()

    @Volatile
    var geofenceHysteresisMeters: Float = getSavedGeofenceHysteresis()

    @Volatile
    var geofenceAccuracyGateEnabled: Boolean = getSavedGeofenceAccuracyGate()

    @Volatile
    private var smoothedSpeedMps: Float = 0f

    @Volatile
    private var stationaryAnchorPoint: LocationPoint? = null

    @Volatile
    private var consecutiveSlowSamples: Int = 0

    private val transitionCooldownLock = Any()
    private val lastNotifiedTransitionMs = mutableMapOf<String, Pair<Boolean, Long>>()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        scope.launch {
            loadGeofences()
            restoreActiveSessionIfAny()
            loadSessions()
            loadAlerts()
            loadOfflineMapRegions()
        }
    }

    private suspend fun restoreActiveSessionIfAny() = recordingMutex.withLock {
        val db = dbHelper.writableDatabase
        val openSessions = mutableListOf<TrackingSession>()
        val cursor = db.query(
            LocationDbHelper.TABLE_SESSIONS,
            null,
            "is_recording = 1",
            null,
            null,
            null,
            "start_time DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                openSessions.add(readSessionFromCursor(c))
            }
        }
        if (openSessions.isEmpty()) return@withLock

        val latest = openSessions.first()
        // Any older stale orphaned sessions get finalized cleanly to their last recorded point timestamp
        if (openSessions.size > 1) {
            for (orphan in openSessions.drop(1)) {
                finalizeOrphanSession(db, orphan)
            }
        }

        // Restore active session points and waypoints into StateFlows
        val restoredPoints = queryPointsSync(db, latest.id)
        val restoredWaypoints = queryWaypointsSync(db, latest.id)
        // If active session was dead for > 20 minutes (e.g. crashed long ago with 0 foreground service), auto-finalize instead of running forever
        val lastActivityMs = restoredPoints.lastOrNull()?.timestamp ?: latest.startTime
        if (System.currentTimeMillis() - lastActivityMs > 20L * 60L * 1000L && !latest.isPaused) {
            finalizeOrphanSession(db, latest, lastPointMs = lastActivityMs)
        } else {
            val synced = latest.copy(
                pointCount = restoredPoints.size.coerceAtLeast(latest.pointCount),
                waypointCount = restoredWaypoints.size.coerceAtLeast(latest.waypointCount)
            )
            _activeSession.value = synced
            _activeTrailPoints.value = restoredPoints
            _activeWaypoints.value = restoredWaypoints
            stationaryAnchorPoint = restoredPoints.lastOrNull()
        }
    }

    private fun finalizeOrphanSession(
        db: android.database.sqlite.SQLiteDatabase,
        session: TrackingSession,
        lastPointMs: Long? = null
    ) {
        val points = queryPointsSync(db, session.id)
        val endMs = lastPointMs ?: points.lastOrNull()?.timestamp ?: session.startTime
        val cv = ContentValues().apply {
            put("is_recording", 0)
            put("is_paused", 0)
            put("pause_started_ms", null as Long?)
            put("end_time", endMs.coerceAtLeast(session.startTime))
            put("point_count", points.size.coerceAtLeast(session.pointCount))
        }
        db.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(session.id))
    }

    suspend fun setTrackingMode(mode: TrackingMode) = withContext(Dispatchers.IO) {
        _trackingMode.value = mode
        prefs.edit().putString("selected_tracking_mode", mode.name).apply()
        val session = _activeSession.value
        if (session != null && session.isRecording) {
            val updated = session.copy(trackingMode = mode)
            _activeSession.value = updated
            val cv = ContentValues().apply {
                put("tracking_mode", mode.name)
            }
            dbHelper.writableDatabase.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(session.id))
            loadSessions()
        }
    }

    suspend fun startNewSession(name: String = "Track ${System.currentTimeMillis() % 10000}", mode: TrackingMode = _trackingMode.value): TrackingSession = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val existing = _activeSession.value
            if (existing != null) {
                stopCurrentSessionInternal(existing)
            }
            val id = UUID.randomUUID().toString()
            val session = TrackingSession(
                id = id,
                name = name.trim().ifBlank { "Field Trail" },
                startTime = System.currentTimeMillis(),
                trackingMode = mode,
                isRecording = true,
                isPaused = false,
                pausedAccumulatedMs = 0L,
                pauseStartedMs = null,
                waypointCount = 0
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
                put("waypoint_count", 0)
                put("tracking_mode", session.trackingMode.name)
                put("is_recording", 1)
                put("is_paused", 0)
                put("paused_accumulated_ms", 0L)
                putNull("pause_started_ms")
            }
            db.insert(LocationDbHelper.TABLE_SESSIONS, null, cv)

            _activeSession.value = session
            _activeTrailPoints.value = emptyList()
            _activeWaypoints.value = emptyList()
            smoothedSpeedMps = 0f
            stationaryAnchorPoint = null
            consecutiveSlowSamples = 0
            loadSessionsInternal()
            session
        }
    }

    suspend fun pauseCurrentSession(): TrackingSession? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val current = _activeSession.value ?: return@withLock null
            if (!current.isRecording || current.isPaused) return@withLock current
            val now = System.currentTimeMillis()
            val updated = current.copy(isPaused = true, pauseStartedMs = now)
            val cv = ContentValues().apply {
                put("is_paused", 1)
                put("pause_started_ms", now)
            }
            dbHelper.writableDatabase.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(current.id))
            _activeSession.value = updated
            loadSessionsInternal()
            updated
        }
    }

    suspend fun resumeCurrentSession(): TrackingSession? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val current = _activeSession.value ?: return@withLock null
            if (!current.isRecording || !current.isPaused) return@withLock current
            val now = System.currentTimeMillis()
            val extraPaused = current.pauseStartedMs?.let { (now - it).coerceAtLeast(0L) } ?: 0L
            val newAccumulated = current.pausedAccumulatedMs + extraPaused
            val updated = current.copy(
                isPaused = false,
                pausedAccumulatedMs = newAccumulated,
                pauseStartedMs = null
            )
            val cv = ContentValues().apply {
                put("is_paused", 0)
                put("paused_accumulated_ms", newAccumulated)
                putNull("pause_started_ms")
            }
            dbHelper.writableDatabase.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(current.id))
            _activeSession.value = updated
            stationaryAnchorPoint = _currentLocation.value
            consecutiveSlowSamples = 0
            loadSessionsInternal()
            updated
        }
    }

    suspend fun stopCurrentSession(): TrackingSession? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val current = _activeSession.value ?: return@withLock null
            stopCurrentSessionInternal(current)
        }
    }
    private suspend fun stopCurrentSessionInternal(current: TrackingSession): TrackingSession {
        val endTime = System.currentTimeMillis()
        val finalPausedAcc = current.pausedAccumulatedMs +
            (current.pauseStartedMs?.let { (endTime - it).coerceAtLeast(0L) } ?: 0L)
        val db = dbHelper.writableDatabase

        // Audit points in SQLite database using anchor-based accumulation so walking steps < 1.5m sum accurately
        var dbCount = 0
        var dbDistance = 0.0
        val effectiveDeadband = jitterThresholdMeters.toDouble().coerceAtLeast(0.0)
        val cursor = db.query(
            LocationDbHelper.TABLE_POINTS,
            arrayOf("latitude", "longitude", "provider"),
            "session_id = ?",
            arrayOf(current.id),
            null, null, "timestamp ASC"
        )
        cursor.use { c ->
            var anchorLat: Double? = null
            var anchorLon: Double? = null
            var lastProvider: String? = null
            while (c.moveToNext()) {
                dbCount++
                val lat = c.getDouble(0)
                val lon = c.getDouble(1)
                val provider = c.getString(2)
                if (anchorLat != null && anchorLon != null) {
                    val isProviderSwitch = (lastProvider == "cellular") != (provider == "cellular")
                    if (isProviderSwitch) {
                        anchorLat = lat
                        anchorLon = lon
                    } else {
                        val stepDist = calculateDistanceMeters(anchorLat, anchorLon, lat, lon)
                        if (stepDist in effectiveDeadband..5000.0) {
                            dbDistance += stepDist
                            anchorLat = lat
                            anchorLon = lon
                        }
                    }
                } else {
                    anchorLat = lat
                    anchorLon = lon
                }
                lastProvider = provider
            }
        }

        val finalDistance = if (current.totalDistanceMeters > 0.0) current.totalDistanceMeters else dbDistance
        val finalCount = if (dbCount > 0) dbCount else current.pointCount
        val activeDurationSec = ((endTime - current.startTime - finalPausedAcc) / 1000.0).coerceAtLeast(1.0)
        val finalAvgSpeed = (finalDistance / activeDurationSec) * 3.6
        val finalWaypointCount = queryWaypointsSync(db, current.id).size.coerceAtLeast(current.waypointCount)

        val updated = current.copy(
            endTime = endTime,
            totalDistanceMeters = finalDistance,
            pointCount = finalCount,
            waypointCount = finalWaypointCount,
            avgSpeedKmh = finalAvgSpeed,
            isRecording = false,
            isPaused = false,
            pausedAccumulatedMs = finalPausedAcc,
            pauseStartedMs = null
        )

        val cv = ContentValues().apply {
            put("end_time", endTime)
            put("is_recording", 0)
            put("is_paused", 0)
            put("paused_accumulated_ms", finalPausedAcc)
            putNull("pause_started_ms")
            put("distance", finalDistance)
            put("avg_speed", finalAvgSpeed)
            put("max_speed", updated.maxSpeedKmh)
            put("point_count", finalCount)
            put("waypoint_count", finalWaypointCount)
        }
        db.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(current.id))

        _activeSession.value = null
        _activeTrailPoints.value = emptyList()
        _activeWaypoints.value = emptyList()
        smoothedSpeedMps = 0f
        stationaryAnchorPoint = null
        consecutiveSlowSamples = 0
        loadSessionsInternal()
        return updated
    }

    suspend fun recordLocation(rawPoint: LocationPoint) = withContext(Dispatchers.IO) {
        // Discard null island and NaN glitch coordinates
        if ((rawPoint.latitude == 0.0 && rawPoint.longitude == 0.0) || rawPoint.latitude.isNaN() || rawPoint.longitude.isNaN()) {
            return@withContext
        }

        val pointForGeofence: LocationPoint = recordingMutex.withLock {
            val session = _activeSession.value
            val isActivelyRecording = session != null && session.isRecording && !session.isPaused
            val prevPoint = if (isActivelyRecording) {
                _activeTrailPoints.value.lastOrNull() ?: _currentLocation.value
            } else {
                _currentLocation.value
            }

        var additionalDistance = 0.0
        var candidateSpeed = rawPoint.speed
        var isProviderSwitch = false

        if (prevPoint != null) {
            isProviderSwitch = (prevPoint.provider == "cellular") != (rawPoint.provider == "cellular")
            if (isProviderSwitch) {
                // Switching between Cell Tower mast coords and GNSS/Fused coords must not inflate distance or speed
                additionalDistance = 0.0
                stationaryAnchorPoint = rawPoint
                consecutiveSlowSamples = 0
                if (candidateSpeed < 0.45f) {
                    candidateSpeed = 0f
                }
                smoothedSpeedMps = candidateSpeed
            } else {
                val anchor = stationaryAnchorPoint ?: prevPoint
                val distFromPrev = calculateDistanceMeters(prevPoint.latitude, prevPoint.longitude, rawPoint.latitude, rawPoint.longitude)
                val distFromAnchor = calculateDistanceMeters(anchor.latitude, anchor.longitude, rawPoint.latitude, rawPoint.longitude)
                val dtPrevSec = ((rawPoint.timestamp - prevPoint.timestamp) / 1000.0).coerceAtLeast(0.1)
                val dtAnchorSec = ((rawPoint.timestamp - anchor.timestamp) / 1000.0).coerceAtLeast(0.1)
                val impliedSpeedFromPrev = (distFromPrev / dtPrevSec).toFloat()
                val impliedSpeedFromAnchor = (distFromAnchor / dtAnchorSec).toFloat()

                // Discard sudden GPS teleportation/multipath spike (> 5km or > 200 km/h)
                if (distFromPrev > 5000.0 || impliedSpeedFromPrev > 55.0f) {
                    additionalDistance = 0.0
                    candidateSpeed = smoothedSpeedMps
                } else {
                    val deadband = jitterThresholdMeters
                    val movedPastDeadband = deadband <= 0f || distFromAnchor >= deadband
                    val dopplerIndicatesMovement = rawPoint.speed >= 0.55f // >= ~2.0 km/h GNSS Doppler

                    if (movedPastDeadband) {
                        // Advance anchor and credit full accumulated displacement (no lost 1.1-1.4m walking strides)
                        additionalDistance = distFromAnchor
                        stationaryAnchorPoint = rawPoint
                        consecutiveSlowSamples = 0

                        // Recover displacement speed when chip Doppler is missing (Balanced/Cell) or under-reads in pocket
                        candidateSpeed = when {
                            rawPoint.provider == "cellular" -> impliedSpeedFromAnchor.coerceIn(0f, 35f)
                            rawPoint.speed <= 0.1f && dtAnchorSec >= 0.3 -> impliedSpeedFromAnchor.coerceIn(0f, 45f)
                            rawPoint.speed < 0.6f && impliedSpeedFromAnchor >= 0.6f && rawPoint.accuracy <= 25f ->
                                impliedSpeedFromAnchor.coerceIn(0f, 45f)
                            rawPoint.speed < 0.45f && impliedSpeedFromAnchor < 0.45f -> 0f
                            else -> rawPoint.speed
                        }
                    } else if (dopplerIndicatesMovement) {
                        // Moving slowly on a 1s tick (< 1.5m step) with clear GNSS Doppler: preserve velocity & hold anchor
                        // so the next tick accumulates >= deadband without losing distance or crashing EMA to 0.
                        additionalDistance = 0.0
                        consecutiveSlowSamples = 0
                        candidateSpeed = rawPoint.speed
                    } else {
                        // Genuine sub-deadband drift while stationary or very slow start
                        if (distFromPrev > 0.05 && (dtAnchorSec >= 2.0 || rawPoint.speed < 0.25f)) {
                            _filteredJitterMeters.value += distFromPrev
                            _filteredJitterCount.value += 1
                        }
                        additionalDistance = 0.0
                        consecutiveSlowSamples += 1
                        candidateSpeed = 0f
                    }
                }
            }
        } else {
            stationaryAnchorPoint = rawPoint
            consecutiveSlowSamples = 0
            if (candidateSpeed < 0.45f) {
                candidateSpeed = 0f
            }
        }

        // Smooth EMA velocity display without instant-zero dropouts on a single 1s sub-deadband sample
        smoothedSpeedMps = when {
            isProviderSwitch -> candidateSpeed
            candidateSpeed <= 0f -> {
                if (consecutiveSlowSamples >= 3 || smoothedSpeedMps < 0.35f) 0f
                else 0.55f * smoothedSpeedMps
            }
            smoothedSpeedMps <= 0f -> candidateSpeed
            else -> (0.45f * candidateSpeed) + (0.55f * smoothedSpeedMps)
        }

            val point = rawPoint.copy(speed = smoothedSpeedMps)
            _currentLocation.value = point

            if (isActivelyRecording && session != null) {
                val lastSaved = _activeTrailPoints.value.lastOrNull()
                val isFirstPoint = lastSaved == null
                val elapsedSinceLastSavedMs = if (lastSaved != null) (point.timestamp - lastSaved.timestamp) else Long.MAX_VALUE
                val accuracyImproved = lastSaved != null && (lastSaved.accuracy - point.accuracy >= 10f)
                // Deduplicate stationary 1s bloat: persist immediately on movement/switch/first-fix/accuracy jump, else 15s stationary heartbeat
                val shouldPersistTrackpoint = isFirstPoint ||
                    additionalDistance > 0.0 ||
                    isProviderSwitch ||
                    accuracyImproved ||
                    elapsedSinceLastSavedMs >= 15_000L

                if (shouldPersistTrackpoint) {
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

                    _activeTrailPoints.value = _activeTrailPoints.value + point

                    val newDistance = session.totalDistanceMeters + additionalDistance
                    val newPointCount = session.pointCount + 1
                    val currentSpeedKmh = point.speedKmh.toDouble()
                    val newMaxSpeed = if (additionalDistance > 0.0) maxOf(session.maxSpeedKmh, currentSpeedKmh) else session.maxSpeedKmh

                    val elapsedSec = session.currentDurationSeconds(point.timestamp).toDouble().coerceAtLeast(1.0)
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
            }
            point
        }

        // Check geofences with dual-radius hysteresis & accuracy gating
        checkGeofences(pointForGeofence.latitude, pointForGeofence.longitude, pointForGeofence.accuracy, pointForGeofence.provider)
    }

    suspend fun dropWaypoint(name: String, note: String = ""): TrailWaypoint? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val session = _activeSession.value ?: return@withLock null
            val loc = _currentLocation.value ?: return@withLock null
            val wp = TrailWaypoint(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                name = name.trim().ifBlank { "Waypoint #${_activeWaypoints.value.size + 1}" },
                note = note.trim(),
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                accuracy = loc.accuracy,
                timestamp = System.currentTimeMillis()
            )
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("id", wp.id)
                put("session_id", wp.sessionId)
                put("name", wp.name)
                put("note", wp.note)
                put("latitude", wp.latitude)
                put("longitude", wp.longitude)
                put("altitude", wp.altitude)
                put("accuracy", wp.accuracy)
                put("timestamp", wp.timestamp)
            }
            db.insert(LocationDbHelper.TABLE_WAYPOINTS, null, cv)

            val newList = _activeWaypoints.value + wp
            _activeWaypoints.value = newList
            val updatedSession = session.copy(waypointCount = newList.size)
            _activeSession.value = updatedSession
            db.update(
                LocationDbHelper.TABLE_SESSIONS,
                ContentValues().apply { put("waypoint_count", newList.size) },
                "id = ?",
                arrayOf(session.id)
            )
            loadSessionsInternal()
            wp
        }
    }

    suspend fun renameSession(sessionId: String, newName: String) = withContext(Dispatchers.IO) {
        val cleaned = newName.trim()
        if (cleaned.isBlank()) return@withContext
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            db.update(
                LocationDbHelper.TABLE_SESSIONS,
                ContentValues().apply { put("name", cleaned) },
                "id = ?",
                arrayOf(sessionId)
            )
            val current = _activeSession.value
            if (current != null && current.id == sessionId) {
                _activeSession.value = current.copy(name = cleaned)
            }
            loadSessionsInternal()
        }
    }

    fun updateCellTowers(towers: List<CellTowerInfo>) {
        _cellTowers.value = towers
    }

    suspend fun loadSessions() = withContext(Dispatchers.IO) {
        loadSessionsInternal()
    }

    private fun loadSessionsInternal() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<TrackingSession>()
        val cursor: Cursor = db.query(
            LocationDbHelper.TABLE_SESSIONS,
            null, null, null, null, null,
            "start_time DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(readSessionFromCursor(c))
            }
        }
        _allSessions.value = list
    }

    private fun readSessionFromCursor(c: Cursor): TrackingSession {
        val modeStr = c.getString(c.getColumnIndexOrThrow("tracking_mode"))
        val mode = try {
            TrackingMode.valueOf(modeStr)
        } catch (_: Exception) {
            TrackingMode.HIGH_ACCURACY
        }
        val wpIdx = c.getColumnIndex("waypoint_count")
        val pausedIdx = c.getColumnIndex("is_paused")
        val pausedAccIdx = c.getColumnIndex("paused_accumulated_ms")
        val pauseStartIdx = c.getColumnIndex("pause_started_ms")
        return TrackingSession(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            startTime = c.getLong(c.getColumnIndexOrThrow("start_time")),
            endTime = if (c.isNull(c.getColumnIndexOrThrow("end_time"))) null else c.getLong(c.getColumnIndexOrThrow("end_time")),
            totalDistanceMeters = c.getDouble(c.getColumnIndexOrThrow("distance")),
            avgSpeedKmh = c.getDouble(c.getColumnIndexOrThrow("avg_speed")),
            maxSpeedKmh = c.getDouble(c.getColumnIndexOrThrow("max_speed")),
            pointCount = c.getInt(c.getColumnIndexOrThrow("point_count")),
            waypointCount = if (wpIdx >= 0) c.getInt(wpIdx) else 0,
            trackingMode = mode,
            isRecording = c.getInt(c.getColumnIndexOrThrow("is_recording")) == 1,
            isPaused = pausedIdx >= 0 && c.getInt(pausedIdx) == 1,
            pausedAccumulatedMs = if (pausedAccIdx >= 0) c.getLong(pausedAccIdx) else 0L,
            pauseStartedMs = if (pauseStartIdx >= 0 && !c.isNull(pauseStartIdx)) c.getLong(pauseStartIdx) else null
        )
    }

    suspend fun getPointsForSession(sessionId: String): List<LocationPoint> = withContext(Dispatchers.IO) {
        queryPointsSync(dbHelper.readableDatabase, sessionId)
    }

    suspend fun getWaypointsForSession(sessionId: String): List<TrailWaypoint> = withContext(Dispatchers.IO) {
        queryWaypointsSync(dbHelper.readableDatabase, sessionId)
    }

    private fun queryPointsSync(db: android.database.sqlite.SQLiteDatabase, sessionId: String): List<LocationPoint> {
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
        return list
    }

    private fun queryWaypointsSync(db: android.database.sqlite.SQLiteDatabase, sessionId: String): List<TrailWaypoint> {
        val list = mutableListOf<TrailWaypoint>()
        val cursor = db.query(
            LocationDbHelper.TABLE_WAYPOINTS,
            null,
            "session_id = ?",
            arrayOf(sessionId),
            null, null,
            "timestamp ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    TrailWaypoint(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        note = c.getString(c.getColumnIndexOrThrow("note")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        altitude = c.getDouble(c.getColumnIndexOrThrow("altitude")),
                        accuracy = c.getFloat(c.getColumnIndexOrThrow("accuracy")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        return list
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(LocationDbHelper.TABLE_WAYPOINTS, "session_id = ?", arrayOf(sessionId))
                db.delete(LocationDbHelper.TABLE_POINTS, "session_id = ?", arrayOf(sessionId))
                db.delete(LocationDbHelper.TABLE_SESSIONS, "id = ?", arrayOf(sessionId))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (_activeSession.value?.id == sessionId) {
                _activeSession.value = null
                _activeTrailPoints.value = emptyList()
                _activeWaypoints.value = emptyList()
            }
            loadSessionsInternal()
        }
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
            val defaultZones = buildDefaultSampleZones()
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

    private fun buildDefaultSampleZones(): List<GeofenceZone> = listOf(
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
            latitude = -0.6075,
            longitude = 30.6727,
            radiusMeters = 300f,
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

    suspend fun resetSampleGeofences() = withContext(Dispatchers.IO) {
        val defaults = buildDefaultSampleZones()
        val writeDb = dbHelper.writableDatabase
        for (z in defaults) {
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
        loadGeofences()
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

    suspend fun handleApiGeofenceTransition(zoneId: String, isNowInside: Boolean) = withContext(Dispatchers.IO) {
        if (!geofenceNotificationsEnabled) return@withContext
        if (_geofenceZones.value.isEmpty()) {
            loadGeofences()
        }
        val zone = _geofenceZones.value.firstOrNull { it.id == zoneId || it.name == zoneId } ?: return@withContext
        val now = System.currentTimeMillis()
        if (shouldSuppressDuplicateTransition(zone.id, isNowInside, now)) return@withContext

        val db = dbHelper.writableDatabase
        val eventType = if (isNowInside) "ENTERED" else "EXITED"
        val shouldAlert = (isNowInside && zone.alertOnEntry) || (!isNowInside && zone.alertOnExit)

        val updatedZones = _geofenceZones.value.map { current ->
            if (current.id == zone.id) {
                current.copy(isInside = isNowInside, lastTransitionTime = now)
            } else {
                current
            }
        }
        val zoneCv = ContentValues().apply {
            put("is_inside", if (isNowInside) 1 else 0)
            put("last_transition", now)
        }
        db.update(LocationDbHelper.TABLE_GEOFENCES, zoneCv, "id = ?", arrayOf(zone.id))
        _geofenceZones.value = updatedZones

        if (shouldAlert) {
            val alert = GeofenceAlert(
                id = UUID.randomUUID().toString(),
                zoneName = zone.name,
                eventType = eventType,
                timestamp = now
            )
            val alertCv = ContentValues().apply {
                put("id", alert.id)
                put("zone_name", alert.zoneName)
                put("event_type", alert.eventType)
                put("timestamp", alert.timestamp)
            }
            db.insert(LocationDbHelper.TABLE_ALERTS, null, alertCv)
            loadAlerts()

            val verb = if (isNowInside) "Entered" else "Exited"
            val notifTitle = "${zone.category.iconEmoji} ${zone.category.label}: $verb"
            val notifContent = "You have $verb ${zone.name}"
            com.geotrace.service.GeofenceBroadcastReceiver.sendNotification(
                context = context,
                title = notifTitle,
                content = notifContent,
                notificationId = zone.id.hashCode() + (if (isNowInside) 1 else 2)
            )
        }
    }

    private fun shouldSuppressDuplicateTransition(zoneId: String, isNowInside: Boolean, nowMs: Long): Boolean {
        synchronized(transitionCooldownLock) {
            val prev = lastNotifiedTransitionMs[zoneId]
            if (prev != null && prev.first == isNowInside && (nowMs - prev.second) < 15_000L) {
                return true
            }
            lastNotifiedTransitionMs[zoneId] = Pair(isNowInside, nowMs)
            return false
        }
    }

    private suspend fun checkGeofences(
        lat: Double,
        lon: Double,
        accuracyMeters: Float = 10f,
        provider: String = "fused"
    ) = withContext(Dispatchers.IO) {
        val zones = _geofenceZones.value
        val updatedZones = mutableListOf<GeofenceZone>()
        val newAlerts = mutableListOf<GeofenceAlert>()
        var hasTransitions = false
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()

        // Dual-radius Schmitt-trigger hysteresis: smaller radius for entry, larger radius for exit
        val delta = geofenceHysteresisMeters.toDouble().coerceAtLeast(0.0)
        val useAccuracyGate = geofenceAccuracyGateEnabled

        for (z in zones) {
            if (useAccuracyGate) {
                val maxAllowedAcc = maxOf(35.0, z.radiusMeters * 0.75)
                if (provider == "cellular" || accuracyMeters > maxAllowedAcc) {
                    updatedZones.add(z)
                    continue
                }
            }

            val dist = calculateDistanceMeters(lat, lon, z.latitude, z.longitude)
            val entryRadius = (z.radiusMeters - delta).coerceAtLeast(minOf(10.0, z.radiusMeters * 0.5))
            val exitRadius = z.radiusMeters + delta

            val isNowInside = if (z.isInside) {
                dist <= exitRadius
            } else {
                dist <= entryRadius
            }

            if (isNowInside != z.isInside) {
                hasTransitions = true
                val eventType = if (isNowInside) "ENTERED" else "EXITED"
                val shouldAlert = (isNowInside && z.alertOnEntry) || (!isNowInside && z.alertOnExit)
                val notDuplicate = !shouldSuppressDuplicateTransition(z.id, isNowInside, now)

                if (shouldAlert && notDuplicate) {
                    val alert = GeofenceAlert(
                        id = UUID.randomUUID().toString(),
                        zoneName = z.name,
                        eventType = eventType,
                        timestamp = now
                    )
                    newAlerts.add(alert)
                    val alertCv = ContentValues().apply {
                        put("id", alert.id)
                        put("zone_name", alert.zoneName)
                        put("event_type", alert.eventType)
                        put("timestamp", alert.timestamp)
                    }
                    db.insert(LocationDbHelper.TABLE_ALERTS, null, alertCv)

                    if (geofenceNotificationsEnabled) {
                        val verb = if (eventType == "ENTERED") "Entered" else "Exited"
                        val notifTitle = "${z.category.iconEmoji} ${z.category.label}: $verb"
                        val notifContent = "You have $verb ${z.name}"
                        com.geotrace.service.GeofenceBroadcastReceiver.sendNotification(
                            context = context,
                            title = notifTitle,
                            content = notifContent,
                            notificationId = z.id.hashCode() + (if (isNowInside) 1 else 2)
                        )
                    }
                }

                val updatedZone = z.copy(isInside = isNowInside, lastTransitionTime = now)
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

        if (hasTransitions) {
            _geofenceZones.value = updatedZones
        }
        if (newAlerts.isNotEmpty()) {
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

    suspend fun loadOfflineMapRegions() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<OfflineMapRegion>()
        val cursor = db.query(
            LocationDbHelper.TABLE_OFFLINE_MAPS,
            null,
            null,
            null,
            null,
            null,
            "downloaded_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val tileTypeStr = c.getString(c.getColumnIndexOrThrow("tile_type"))
                val tileType = try {
                    MapTileType.valueOf(tileTypeStr)
                } catch (_: Exception) {
                    MapTileType.SATELLITE
                }
                list.add(
                    OfflineMapRegion(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        northLat = c.getDouble(c.getColumnIndexOrThrow("north_lat")),
                        southLat = c.getDouble(c.getColumnIndexOrThrow("south_lat")),
                        eastLng = c.getDouble(c.getColumnIndexOrThrow("east_lng")),
                        westLng = c.getDouble(c.getColumnIndexOrThrow("west_lng")),
                        tileType = tileType,
                        minZoom = c.getInt(c.getColumnIndexOrThrow("min_zoom")),
                        maxZoom = c.getInt(c.getColumnIndexOrThrow("max_zoom")),
                        tileCount = c.getInt(c.getColumnIndexOrThrow("tile_count")),
                        estimatedSizeMb = c.getFloat(c.getColumnIndexOrThrow("estimated_size_mb")),
                        downloadedAtMs = c.getLong(c.getColumnIndexOrThrow("downloaded_at"))
                    )
                )
            }
        }
        _offlineMapRegions.value = list
        refreshOfflinePlacesCountInternal()
    }

    private fun refreshOfflinePlacesCountInternal() {
        try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM ${LocationDbHelper.TABLE_OFFLINE_PLACES}", null).use { c ->
                if (c.moveToFirst()) {
                    _offlinePlacesCount.value = c.getInt(0)
                }
            }
        } catch (_: Exception) {
            _offlinePlacesCount.value = 0
        }
    }

    suspend fun saveOfflinePlaces(regionId: String, places: List<OfflinePlacePoi>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(LocationDbHelper.TABLE_OFFLINE_PLACES, "region_id = ?", arrayOf(regionId))
            for (poi in places) {
                val cv = ContentValues().apply {
                    put("id", poi.id)
                    put("region_id", poi.regionId)
                    put("name", poi.name)
                    put("category", poi.category)
                    put("subtitle", poi.subtitle)
                    put("latitude", poi.latitude)
                    put("longitude", poi.longitude)
                }
                db.insertWithOnConflict(
                    LocationDbHelper.TABLE_OFFLINE_PLACES,
                    null,
                    cv,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshOfflinePlacesCountInternal()
    }

    suspend fun searchOfflinePlaces(query: String, limit: Int = 25): List<OfflinePlacePoi> = withContext(Dispatchers.IO) {
        val list = mutableListOf<OfflinePlacePoi>()
        val trimmed = query.trim()
        val db = dbHelper.readableDatabase
        val cursor = if (trimmed.isBlank()) {
            db.query(
                LocationDbHelper.TABLE_OFFLINE_PLACES,
                null,
                null,
                null,
                null,
                null,
                "name ASC",
                limit.toString()
            )
        } else {
            val likeArg = "%$trimmed%"
            db.query(
                LocationDbHelper.TABLE_OFFLINE_PLACES,
                null,
                "name LIKE ? COLLATE NOCASE OR category LIKE ? COLLATE NOCASE OR subtitle LIKE ? COLLATE NOCASE",
                arrayOf(likeArg, likeArg, likeArg),
                null,
                null,
                "name ASC",
                limit.toString()
            )
        }
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    OfflinePlacePoi(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        regionId = c.getString(c.getColumnIndexOrThrow("region_id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        subtitle = c.getString(c.getColumnIndexOrThrow("subtitle")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude"))
                    )
                )
            }
        }
        list
    }

    suspend fun saveOfflineMapRegion(region: OfflineMapRegion) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", region.id)
            put("name", region.name)
            put("north_lat", region.northLat)
            put("south_lat", region.southLat)
            put("east_lng", region.eastLng)
            put("west_lng", region.westLng)
            put("tile_type", region.tileType.name)
            put("min_zoom", region.minZoom)
            put("max_zoom", region.maxZoom)
            put("tile_count", region.tileCount)
            put("estimated_size_mb", region.estimatedSizeMb)
            put("downloaded_at", region.downloadedAtMs)
        }
        db.insertWithOnConflict(
            LocationDbHelper.TABLE_OFFLINE_MAPS,
            null,
            cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        loadOfflineMapRegions()
    }

    suspend fun deleteOfflineMapRegion(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(LocationDbHelper.TABLE_OFFLINE_PLACES, "region_id = ?", arrayOf(id))
        db.delete(LocationDbHelper.TABLE_OFFLINE_MAPS, "id = ?", arrayOf(id))
        loadOfflineMapRegions()
    }

    suspend fun clearAllOfflineMapRegions() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(LocationDbHelper.TABLE_OFFLINE_PLACES, null, null)
        db.delete(LocationDbHelper.TABLE_OFFLINE_MAPS, null, null)
        loadOfflineMapRegions()
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
